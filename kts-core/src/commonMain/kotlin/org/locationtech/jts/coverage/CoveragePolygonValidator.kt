/*
 * Copyright (c) 2022 Martin Davis.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.coverage

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LineSegment
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.util.PolygonExtracter
import org.locationtech.jts.noding.MCIndexSegmentSetMutualIntersector
import kotlin.jvm.JvmStatic

/**
 * Validates that a polygon forms a valid polygonal coverage
 * with the set of polygons adjacent to it.
 * If the polygon is coverage-valid an empty [LineString] is returned.
 * Otherwise, the result is a linear geometry containing
 * the target polygon boundary linework causing the invalidity.
 *
 * A polygon is coverage-valid if:
 *
 *  1. The polygon interior does not intersect the interior of other polygons.
 *  1. If the polygon boundary intersects another polygon boundary, the vertices
 * and line segments of the intersection match exactly.
 *
 *
 * The algorithm detects the following coverage errors:
 *
 *  1. the polygon is a duplicate of another one
 *  1. a polygon boundary segment equals an adjacent segment (with same orientation).
 *     This determines that the polygons overlap
 *  1. a polygon boundary segment is collinear and overlaps an adjacent segment
 *     but is not equal to it
 *  1. a polygon boundary segment touches an adjacent segment at a non-vertex point
 *  1. a polygon boundary segment crosses into an adjacent polygon
 *  1. a polygon boundary segment is in the interior of an adjacent polygon
 *
 *
 * If any of these errors is present, the target polygon
 * does not form a valid coverage with the adjacent polygons.
 *
 * The validity rules do not preclude properly-noded gaps between coverage polygons.
 * However, this class can detect narrow gaps,
 * by specifying a maximum gap width using [setGapWidth].
 * Note that this will also identify narrow gaps separating disjoint coverage regions,
 * and narrow gores.
 * In some situations it may also produce false positives
 * (i.e. linework identified as part of a gap which is wider than the given width).
 * To fully identify gaps it maybe necessary to use [CoverageUnion] and analyze
 * the holes in the result to see if they are acceptable.
 *
 * A polygon may be coverage-valid with respect to
 * a set of surrounding polygons, but the collection as a whole may not
 * form a clean coverage.  For example, the target polygon boundary may be fully matched
 * by adjacent boundary segments, but the adjacent set contains polygons
 * which are not coverage-valid relative to other ones in the set.
 * A coverage is valid only if every polygon in the coverage is coverage-valid.
 * Use [CoverageValidator] to validate an entire set of polygons.
 *
 * The adjacent set may contain polygons which do not intersect the target polygon.
 * These are effectively ignored during validation (but may decrease performance).
 *
 * @see CoverageValidator
 *
 * @author Martin Davis
 *
 */
class CoveragePolygonValidator(
    private val targetGeom: Geometry,
    private val adjGeoms: Array<Geometry>
) {
    private var gapWidth = 0.0
    private val geomFactory: GeometryFactory = targetGeom.factory
    private var adjCovPolygons: List<CoveragePolygon>? = null

    /**
     * Sets the maximum gap width, if narrow gaps are to be detected.
     *
     * @param gapWidth the maximum width of gaps to detect
     */
    fun setGapWidth(gapWidth: Double) {
        this.gapWidth = gapWidth
    }

    /**
     * Validates the coverage polygon against the set of adjacent polygons
     * in the coverage.
     *
     * @return a linear geometry containing the segments causing invalidity (if any)
     */
    fun validate(): Geometry {
        val adjPolygons = extractPolygons(adjGeoms)
        adjCovPolygons = toCoveragePolygons(adjPolygons)

        val targetRings = CoverageRing.createRings(targetGeom)
        val adjRings = CoverageRing.createRings(adjPolygons)

        /**
         * Mark matching segments first.
         * Matched segments are not considered for further checks.
         * This improves performance substantially for mostly-valid coverages.
         */
        val targetEnv = targetGeom.envelopeInternal.copy()
        targetEnv.expandBy(gapWidth)

        checkTargetRings(targetRings, adjRings, targetEnv)

        return createInvalidLines(targetRings)
    }

    private fun checkTargetRings(targetRings: List<CoverageRing>, adjRings: List<CoverageRing>, targetEnv: Envelope) {
        markMatchedSegments(targetRings, adjRings, targetEnv)

        /**
         * Short-circuit if target is fully known (matched or invalid).
         * This often happens in clean coverages,
         * when the target is surrounded by matching polygons.
         * It can also happen in invalid coverages
         * which have polygons which are duplicates,
         * or perfectly overlap other polygons.
         *
         */
        if (CoverageRing.isKnown(targetRings)) return

        /**
         * Here target has at least one unmatched segment.
         * Do further checks to see if any of them are are invalid.
         */
        markInvalidInteractingSegments(targetRings, adjRings, gapWidth)
        markInvalidInteriorSegments(targetRings, adjCovPolygons!!)
        //OLDmarkInvalidInteriorSegments(targetRings, adjPolygons);
    }

    private fun createEmptyResult(): Geometry {
        return geomFactory.createLineString()
    }

    /**
     * Marks matched segments.
     * This improves the efficiency of validity testing, since in valid coverages
     * all segments (except exterior ones) are matched,
     * and hence do not need to be tested further.
     * Segments which are equal and have same orientation
     * are detected and marked invalid.
     * In fact, the entire target polygon may be matched and valid,
     * which allows avoiding further tests.
     * Segments matched between adjacent polygons are also marked valid,
     * since this prevents them from being detected as misaligned,
     * if this is being done.
     *
     * @param targetRings the target rings
     * @param adjRngs the adjacent rings
     * @param targetEnv the tolerance envelope of the target
     */
    private fun markMatchedSegments(
        targetRings: List<CoverageRing>,
        adjRngs: List<CoverageRing>, targetEnv: Envelope
    ) {
        val segmentMap: MutableMap<CoverageRingSegment, CoverageRingSegment> = HashMap()
        markMatchedSegments(targetRings, targetEnv, segmentMap)
        markMatchedSegments(adjRngs, targetEnv, segmentMap)
    }

    /**
     * Adds ring segments to the segment map,
     * and detects if they match an existing segment.
     * Matched segments are marked.
     *
     * @param rings
     * @param envLimit
     * @param segMap
     */
    private fun markMatchedSegments(
        rings: List<CoverageRing>, envLimit: Envelope,
        segmentMap: MutableMap<CoverageRingSegment, CoverageRingSegment>
    ) {
        for (ring in rings) {
            for (i in 0 until ring.size() - 1) {
                val p0 = ring.getCoordinate(i)
                val p1 = ring.getCoordinate(i + 1)
                //-- skip segments which lie outside the limit envelope
                if (!envLimit.intersects(p0, p1)) {
                    continue
                }
                //-- if segment keys match, mark them as matched (or invalid)
                val seg = CoverageRingSegment.create(ring, i)
                val segMatch = segmentMap[seg]
                if (segMatch != null) {
                    /**
                     * Since inputs should be valid,
                     * the segments are assumed to be in different rings.
                     */
                    seg.match(segMatch)
                } else {
                    //-- store the segment as key and value, to allow retrieving when matched
                    segmentMap[seg] = seg
                }
            }
        }
    }

    /**
     * Models a segment in a CoverageRing.
     * The segment is normalized so it can be compared with segments
     * in any orientation.
     * Records valid matching segments in a coverage,
     * which must have opposite orientations.
     * Also detects equal segments with identical
     * orientation, and marks them as coverage-invalid.
     */
    private class CoverageRingSegment private constructor(
        p0: Coordinate,
        p1: Coordinate,
        ring: CoverageRing,
        index: Int
    ) : LineSegment(p0, p1) {
        private var ringForward: CoverageRing? = null
        private var indexForward = -1
        private var ringOpp: CoverageRing? = null
        private var indexOpp = -1

        init {
            if (p1.compareTo(p0) < 0) {
                reverse()
                ringOpp = ring
                indexOpp = index
            } else {
                ringForward = ring
                indexForward = index
            }
        }

        fun match(seg: CoverageRingSegment) {
            val isInvalid = checkInvalid(seg)
            if (isInvalid) {
                return
            }
            //-- record the match
            if (ringForward == null) {
                ringForward = seg.ringForward
                indexForward = seg.indexForward
            } else {
                ringOpp = seg.ringOpp
                indexOpp = seg.indexOpp
            }
            //-- mark ring segments as matched
            ringForward!!.markMatched(indexForward)
            ringOpp!!.markMatched(indexOpp)
        }

        private fun checkInvalid(seg: CoverageRingSegment): Boolean {
            if (ringForward != null && seg.ringForward != null) {
                ringForward!!.markInvalid(indexForward)
                seg.ringForward!!.markInvalid(seg.indexForward)
                return true
            }
            if (ringOpp != null && seg.ringOpp != null) {
                ringOpp!!.markInvalid(indexOpp)
                seg.ringOpp!!.markInvalid(seg.indexOpp)
                return true
            }
            return false
        }

        companion object {
            fun create(ring: CoverageRing, index: Int): CoverageRingSegment {
                val p0 = ring.getCoordinate(index)
                val p1 = ring.getCoordinate(index + 1)
                //-- orient segment as if ring is in canonical orientation
                return if (ring.isInteriorOnRight) {
                    CoverageRingSegment(p0, p1, ring, index)
                } else {
                    CoverageRingSegment(p1, p0, ring, index)
                }
            }
        }
    }

    //--------------------------------------------------

    /**
     * Marks invalid target segments which cross an adjacent ring segment,
     * lie partially in the interior of an adjacent ring,
     * or are nearly collinear with an adjacent ring segment up to the distance tolerance
     *
     * @param targetRings the rings with segments to test
     * @param adjRings the adjacent rings
     * @param distanceTolerance the gap distance tolerance, if any
     */
    private fun markInvalidInteractingSegments(
        targetRings: List<CoverageRing>, adjRings: List<CoverageRing>,
        distanceTolerance: Double
    ) {
        val detector = InvalidSegmentDetector(distanceTolerance)
        val segSetMutInt = MCIndexSegmentSetMutualIntersector(targetRings, distanceTolerance)
        segSetMutInt.process(adjRings, detector)
    }

    /**
     * Marks invalid target segments which are fully interior
     * to an adjacent polygon.
     *
     * @param targetRings the rings with segments to test
     * @param adjCovPolygons the adjacent polygons
     */
    private fun markInvalidInteriorSegments(targetRings: List<CoverageRing>, adjCovPolygons: List<CoveragePolygon>) {
        for (ring in targetRings) {
            val stride = RING_SECTION_STRIDE
            var i = 0
            while (i < ring.size() - 1) {
                var iEnd = i + stride
                if (iEnd >= ring.size()) iEnd = ring.size() - 1

                markInvalidInteriorSection(ring, i, iEnd, adjCovPolygons)
                i += stride
            }
        }
    }

    /**
     * Marks invalid target segments in a section which are interior
     * to an adjacent polygon.
     * Processing a section at a time dramatically improves efficiency.
     * Due to the coherent organization of polygon rings,
     * sections usually have a high spatial locality.
     * This means that sections typically intersect only a few or often no adjacent polygons.
     * The section envelope can be computed and tested against adjacent polygon envelopes quickly.
     * The section can be skipped entirely if it does not interact with any polygons.
     *
     * @param ring
     * @param iStart
     * @param iEnd
     * @param adjPolygons
     */
    private fun markInvalidInteriorSection(ring: CoverageRing, iStart: Int, iEnd: Int, adjPolygons: List<CoveragePolygon>) {
        val sectionEnv = ring.getEnvelope(iStart, iEnd)
        //TODO: is it worth indexing polygons?
        for (adjPoly in adjPolygons) {
            if (adjPoly.intersectsEnv(sectionEnv)) {
                //-- test vertices in section
                for (i in iStart until iEnd) {
                    markInvalidInteriorSegment(ring, i, adjPoly)
                }
            }
        }
    }

    private fun markInvalidInteriorSegment(ring: CoverageRing, i: Int, adjPoly: CoveragePolygon) {
        //-- skip check for segments with known state.
        if (ring.isKnown(i)) return

        /**
         * Check if vertex is in interior of an adjacent polygon.
         * If so, the segments on either side are in the interior.
         * Mark them invalid, unless they are already matched.
         */
        val p = ring.getCoordinate(i)
        if (adjPoly.contains(p)) {
            ring.markInvalid(i)
            //-- previous segment may be interior (but may also be matched)
            val iPrev = if (i == 0) ring.size() - 2 else i - 1
            if (!ring.isKnown(iPrev)) ring.markInvalid(iPrev)
        }
    }

    private fun createInvalidLines(rings: List<CoverageRing>): Geometry {
        val lines: MutableList<LineString> = ArrayList()
        for (ring in rings) {
            ring.createInvalidLines(geomFactory, lines)
        }

        if (lines.size == 0) {
            return createEmptyResult()
        } else if (lines.size == 1) {
            return lines[0]
        }
        return geomFactory.createMultiLineString(lines.toTypedArray())
    }

    companion object {
        /**
         * Validates that a polygon is coverage-valid  against the
         * surrounding polygons in a polygonal coverage.
         *
         * @param targetPolygon the polygon to validate
         * @param adjPolygons the adjacent polygons
         * @return a linear geometry containing the segments causing invalidity (if any)
         */
        @JvmStatic
        fun validate(targetPolygon: Geometry, adjPolygons: Array<Geometry>): Geometry {
            val v = CoveragePolygonValidator(targetPolygon, adjPolygons)
            return v.validate()
        }

        /**
         * Validates that a polygon is coverage-valid against the
         * surrounding polygons in a polygonal coverage,
         * and forms no gaps narrower than a specified width.
         *
         * The set of surrounding polygons should include all polygons which
         * are within the gap width distance of the target polygon.
         *
         * @param targetPolygon the polygon to validate
         * @param adjPolygons a collection of the adjacent polygons
         * @param gapWidth the maximum width of invalid gaps
         * @return a linear geometry containing the segments causing invalidity (if any)
         */
        @JvmStatic
        fun validate(targetPolygon: Geometry, adjPolygons: Array<Geometry>, gapWidth: Double): Geometry {
            val v = CoveragePolygonValidator(targetPolygon, adjPolygons)
            v.setGapWidth(gapWidth)
            return v.validate()
        }

        /**
         * Stride is chosen experimentally to provide good performance
         */
        private const val RING_SECTION_STRIDE = 1000

        private fun toCoveragePolygons(polygons: List<Polygon>): List<CoveragePolygon> {
            val covPolys: MutableList<CoveragePolygon> = ArrayList()
            for (poly in polygons) {
                covPolys.add(CoveragePolygon(poly))
            }
            return covPolys
        }

        private fun extractPolygons(geoms: Array<Geometry>): List<Polygon> {
            val polygons: MutableList<Polygon> = ArrayList()
            for (geom in geoms) {
                PolygonExtracter.getPolygons(geom, polygons)
            }
            return polygons
        }
    }
}
