/*
 * Copyright (c) 2021 Martin Davis.
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.operation.buffer

import org.locationtech.jts.algorithm.Distance.pointToSegment
import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.util.GeometryMapper
import org.locationtech.jts.geom.util.GeometryMapper.flatMap
import org.locationtech.jts.index.chain.MonotoneChain
import org.locationtech.jts.index.chain.MonotoneChainSelectAction
import org.locationtech.jts.legacy.Math.abs
import org.locationtech.jts.util.Assert
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Computes an offset curve from a geometry.
 * An offset curve is a linear geometry which is offset a given distance
 * from the input.
 * If the offset distance is positive the curve lies on the left side of the input;
 * if it is negative the curve is on the right side.
 * The curve(s) have the same direction as the input line(s).
 * The result for a zero offset distance is a copy of the input linework.
 *
 * The offset curve is based on the boundary of the buffer for the geometry
 * at the offset distance (see [BufferOp].
 * The normal mode of operation is to return the sections of the buffer boundary
 * which lie on the raw offset curve
 * (obtained via [rawOffset].
 * The offset curve will contain multiple sections
 * if the input self-intersects or has close approaches.
 * The computed sections are ordered along the raw offset curve.
 * Sections are disjoint.  They never self-intersect, but may be rings.
 *
 *  * For a [LineString] the offset curve is a linear geometry
 * ([LineString] or [MultiLineString]).
 *  * For a [Point] or [MultiPoint] the offset curve is an empty [LineString].
 *  * For a [Polygon] the offset curve is the boundary of the polygon buffer (which
 * may be a [MultiLineString].
 *  * For a collection the output is a [MultiLineString] containing the offset curves of the elements.
 *
 * In "joined" mode (see [setJoined]
 * the sections computed for each input line are joined into a single offset curve line.
 * The joined curve may self-intersect.
 * At larger offset distances the curve may contain "flat-line" artifacts
 * in places where the input self-intersects.
 *
 * Offset curves support setting the number of quadrant segments,
 * the join style, and the mitre limit (if applicable) via
 * the [BufferParameters].
 *
 * @author Martin Davis
 */
class OffsetCurve
/**
 * Creates a new instance for computing an offset curve for a geometry at a given distance.
 * setting the quadrant segments and join style and mitre limit
 * via [BufferParameters].
 *
 * @param geom the geometry to offset
 * @param distance the offset distance (positive for left, negative for right)
 * @param bufParams the buffer parameters to use
 */
@JvmOverloads constructor(
    private val inputGeom: Geometry,
    private val distance: Double,
    bufParams: BufferParameters? = null
) {
    private var isJoined = false
    private val bufferParams: BufferParameters
    private val matchDistance: Double = abs(distance) / MATCH_DISTANCE_FACTOR
    private val geomFactory: GeometryFactory = inputGeom.factory

    init {
        //-- make new buffer params since the end cap style must be the default
        bufferParams = BufferParameters()
        if (bufParams != null) {
            /**
             * Prevent using a very small QuadSegs value, to avoid
             * offset curve artifacts near the end caps.
             */
            var quadSegs = bufParams.getQuadrantSegments()
            if (quadSegs < MIN_QUADRANT_SEGMENTS) {
                quadSegs = MIN_QUADRANT_SEGMENTS
            }
            bufferParams.setQuadrantSegments(quadSegs)
            bufferParams.setJoinStyle(bufParams.getJoinStyle())
            bufferParams.setMitreLimit(bufParams.getMitreLimit())
        }
    }

    /**
     * Computes a single curve line for each input linear component,
     * by joining curve sections in order along the raw offset curve.
     * The default mode is to compute separate curve sections.
     *
     * @param isJoined true if joined mode should be used.
     */
    fun setJoined(isJoined: Boolean) {
        this.isJoined = isJoined
    }

    /**
     * Gets the computed offset curve lines.
     *
     * @return the offset curve geometry
     */
    val curve: Geometry
        get() = flatMap(inputGeom, 1, object : GeometryMapper.MapOp {
            override fun map(geom: Geometry): Geometry? {
                if (geom is Point) return null
                return if (geom is Polygon) {
                    toLineString(geom.buffer(distance).boundary)
                } else computeCurve(geom as LineString, distance)
            }

            /**
             * Force LinearRings to be LineStrings.
             *
             * @param geom a geometry which may be a LinearRing
             * @return a geometry which will be a LineString or MultiLineString
             */
            private fun toLineString(geom: Geometry?): Geometry? {
                if (geom is LinearRing) {
                    return geom.factory.createLineString(geom.coordinateSequence)
                }
                return geom
            }
        })

    private fun computeCurve(lineGeom: LineString, distance: Double): Geometry {
        //-- first handle simple cases
        //-- empty or single-point line
        if (lineGeom.numPoints < 2 || lineGeom.length == 0.0) {
            return geomFactory.createLineString()
        }
        //-- zero offset distance
        if (distance == 0.0) {
            return lineGeom.copy()
        }
        //-- two-point line
        if (lineGeom.numPoints == 2) {
            return offsetSegment(lineGeom.coordinates, distance)
        }
        val sections = computeSections(lineGeom, distance)
        val offsetCurve: Geometry = if (isJoined) {
            OffsetCurveSection.toLine(sections, geomFactory)
        } else {
            OffsetCurveSection.toGeometry(sections, geomFactory)
        }
        return offsetCurve
    }

    private fun computeSections(lineGeom: LineString, distance: Double): MutableList<OffsetCurveSection> {
        val rawCurve = rawOffset(lineGeom, distance, bufferParams)
        val sections: MutableList<OffsetCurveSection> = ArrayList()
        if (rawCurve.isEmpty()) {
            return sections
        }
        /**
         * Note: If the raw offset curve has no
         * narrow concave angles or self-intersections it could be returned as is.
         * However, this is likely to be a less frequent situation,
         * and testing indicates little performance advantage,
         * so not doing this.
         */
        val bufferPoly = getBufferOriented(lineGeom, distance, bufferParams)

        //-- first extract offset curve sections from shell
        val shell: Array<Coordinate> = bufferPoly.exteriorRing!!.coordinates
        computeCurveSections(shell, rawCurve, sections)

        //-- extract offset curve sections from holes
        for (i in 0 until bufferPoly.getNumInteriorRing()) {
            val hole: Array<Coordinate> = bufferPoly.getInteriorRingN(i).coordinates
            computeCurveSections(hole, rawCurve, sections)
        }
        return sections
    }

    private fun offsetSegment(pts: Array<Coordinate>, distance: Double): LineString {
        val offsetSeg = LineSegment(pts[0], pts[1]).offset(distance)
        return geomFactory.createLineString(arrayOf(offsetSeg.p0, offsetSeg.p1))
    }

    private fun computeCurveSections(
        bufferRingPts: Array<Coordinate>,
        rawCurve: Array<Coordinate>, sections: MutableList<OffsetCurveSection>
    ) {
        val rawPosition = DoubleArray(bufferRingPts.size - 1) { NOT_IN_CURVE }
        val bufferSegIndex = SegmentMCIndex(bufferRingPts)
        var bufferFirstIndex = -1
        var minRawPosition = -1.0
        for (i in 0 until rawCurve.size - 1) {
            val minBufferIndexForSeg = matchSegments(
                rawCurve[i], rawCurve[i + 1], i, bufferSegIndex, bufferRingPts, rawPosition
            )
            if (minBufferIndexForSeg >= 0) {
                val pos = rawPosition[minBufferIndexForSeg]
                if (bufferFirstIndex < 0 || pos < minRawPosition) {
                    minRawPosition = pos
                    bufferFirstIndex = minBufferIndexForSeg
                }
            }
        }
        //-- no matching sections found in this buffer ring
        if (bufferFirstIndex < 0) return
        extractSections(bufferRingPts, rawPosition, bufferFirstIndex, sections)
    }

    /**
     * Matches the segments in a buffer ring to the raw offset curve
     * to obtain their match positions (if any).
     *
     * @param raw0 a raw curve segment start point
     * @param raw1 a raw curve segment end point
     * @param rawCurveIndex the index of the raw curve segment
     * @param bufferSegIndex the spatial index of the buffer ring segments
     * @param bufferPts the points of the buffer ring
     * @param rawCurvePos the raw curve positions of the buffer ring segments
     * @return the index of the minimum matched buffer segment
     */
    private fun matchSegments(
        raw0: Coordinate, raw1: Coordinate, rawCurveIndex: Int,
        bufferSegIndex: SegmentMCIndex, bufferPts: Array<Coordinate>,
        rawCurvePos: DoubleArray
    ): Int {
        val matchEnv = Envelope(raw0, raw1)
        matchEnv.expandBy(matchDistance)
        val matchAction =
            MatchCurveSegmentAction(raw0, raw1, rawCurveIndex, matchDistance, bufferPts, rawCurvePos)
        bufferSegIndex.query(matchEnv, matchAction)
        return matchAction.bufferMinIndex
    }

    /**
     * An action to match a raw offset curve segment
     * to segments in a buffer ring
     * and record the matched segment locations(s) along the raw curve.
     *
     * @author Martin Davis
     */
    private class MatchCurveSegmentAction(
        private val raw0: Coordinate,
        private val raw1: Coordinate,
        private val rawCurveIndex: Int,
        private val matchDistance: Double,
        private val bufferRingPts: Array<Coordinate>,
        private val rawCurveLoc: DoubleArray
    ) : MonotoneChainSelectAction() {
        private val rawLen: Double = raw0.distance(raw1)
        private var minRawLocation = -1.0
        var bufferMinIndex = -1
            private set

        override fun select(mc: MonotoneChain, segIndex: Int) {
            /**
             * Generally buffer segments are no longer than raw curve segments,
             * since the final buffer line likely has node points added.
             * So a buffer segment may match all or only a portion of a single raw segment.
             * There may be multiple buffer ring segs that match along the raw segment.
             *
             * HOWEVER, in some cases the buffer construction may contain
             * a matching buffer segment which is slightly longer than a raw curve segment.
             * Specifically, at the endpoint of a closed line with nearly parallel end segments
             * - the closing fillet line is very short so is heuristically removed in the buffer.
             * In this case, the buffer segment must still be matched.
             * This produces closed offset curves, which is technically
             * an anomaly, but only happens in rare cases.
             */
            val frac = segmentMatchFrac(
                bufferRingPts[segIndex], bufferRingPts[segIndex + 1],
                raw0, raw1, matchDistance
            )
            //-- no match
            if (frac < 0) return

            //-- location is used to sort segments along raw curve
            val location = rawCurveIndex + frac
            rawCurveLoc[segIndex] = location
            //-- buffer seg index at lowest raw location is the curve start
            if (minRawLocation < 0 || location < minRawLocation) {
                minRawLocation = location
                bufferMinIndex = segIndex
            }
        }

        private fun segmentMatchFrac(
            buf0: Coordinate, buf1: Coordinate,
            raw0: Coordinate, raw1: Coordinate, matchDistance: Double
        ): Double {
            if (!isMatch(buf0, buf1, raw0, raw1, matchDistance)) return -1.0

            //-- matched - determine location as fraction along raw segment
            val seg = LineSegment(raw0, raw1)
            return seg.segmentFraction(buf0)
        }

        private fun isMatch(
            buf0: Coordinate,
            buf1: Coordinate,
            raw0: Coordinate,
            raw1: Coordinate,
            matchDistance: Double
        ): Boolean {
            val bufSegLen = buf0.distance(buf1)
            if (rawLen <= bufSegLen) {
                if (matchDistance < pointToSegment(raw0, buf0, buf1)) return false
                if (matchDistance < pointToSegment(raw1, buf0, buf1)) return false
            } else {
                //TODO: only match longer buf segs at raw curve end segs?
                if (matchDistance < pointToSegment(buf0, raw0, raw1)) return false
                if (matchDistance < pointToSegment(buf1, raw0, raw1)) return false
            }
            return true
        }
    }

    /**
     * This is only called when there is at least one ring segment matched
     * (so rawCurvePos has at least one entry != NOT_IN_CURVE).
     * The start index of the first section must be provided.
     * This is intended to be the section with lowest position
     * along the raw curve.
     * @param ringPts the points in a buffer ring
     * @param rawCurveLoc the position of buffer ring segments along the raw curve
     * @param startIndex the index of the start of a section
     * @param sections the list of extracted offset curve sections
     */
    private fun extractSections(
        ringPts: Array<Coordinate>, rawCurveLoc: DoubleArray,
        startIndex: Int, sections: MutableList<OffsetCurveSection>
    ) {
        var sectionStart = startIndex
        var sectionCount = 0
        var sectionEnd: Int
        do {
            sectionEnd = findSectionEnd(rawCurveLoc, sectionStart, startIndex)
            val location = rawCurveLoc[sectionStart]
            val lastIndex = prev(sectionEnd, rawCurveLoc.size)
            val lastLoc = rawCurveLoc[lastIndex]
            val section = OffsetCurveSection.create(ringPts, sectionStart, sectionEnd, location, lastLoc)
            sections.add(section)
            sectionStart = findSectionStart(rawCurveLoc, sectionEnd)

            //-- check for an abnormal state
            if (sectionCount++ > ringPts.size) {
                Assert.shouldNeverReachHere("Too many sections for ring - probable bug")
            }
        } while (sectionStart != startIndex && sectionEnd != startIndex)
    }

    private fun findSectionStart(loc: DoubleArray, end: Int): Int {
        var start = end
        do {
            val nextIndex = next(start, loc.size)
            //-- skip ahead if segment is not in raw curve
            if (loc[start] == NOT_IN_CURVE) {
                start = nextIndex
                continue
            }
            val prevIndex = prev(start, loc.size)
            //-- if prev segment is not in raw curve then have found a start
            if (loc[prevIndex] == NOT_IN_CURVE) {
                return start
            }
            if (isJoined) {
                /**
                 * Start section at next gap in raw curve.
                 * Only needed for joined curve, since otherwise
                 * contiguous buffer segments can be in same curve section.
                 */
                val locDelta = abs(loc[start] - loc[prevIndex])
                if (locDelta > 1) return start
            }
            start = nextIndex
        } while (start != end)
        return start
    }

    private fun findSectionEnd(loc: DoubleArray, start: Int, firstStartIndex: Int): Int {
        // assert: pos[start] is IN CURVE
        var end = start
        var nextIndex: Int
        do {
            nextIndex = next(end, loc.size)
            if (loc[nextIndex] == NOT_IN_CURVE) return nextIndex
            if (isJoined) {
                /**
                 * End section at gap in raw curve.
                 * Only needed for joined curve, since otherwise
                 * contigous buffer segments can be in same section
                 */
                val locDelta = abs(loc[nextIndex] - loc[end])
                if (locDelta > 1) return nextIndex
            }
            end = nextIndex
        } while (end != start && end != firstStartIndex)
        return end
    }

    companion object {
        /**
         * The nearness tolerance for matching the the raw offset linework and the buffer curve.
         */
        private const val MATCH_DISTANCE_FACTOR = 10000

        /**
         * A QuadSegs minimum value that will prevent generating
         * unwanted offset curve artifacts near end caps.
         */
        private const val MIN_QUADRANT_SEGMENTS = 8

        private const val NOT_IN_CURVE = -1.0

        /**
         * Computes the offset curve of a geometry at a given distance.
         *
         * @param geom a geometry
         * @param distance the offset distance (positive for left, negative for right)
         * @return the offset curve
         */
        @JvmStatic
        fun getCurve(geom: Geometry, distance: Double): Geometry {
            val oc = OffsetCurve(geom, distance)
            return oc.curve
        }

        /**
         * Computes the offset curve of a geometry at a given distance,
         * with specified quadrant segments, join style and mitre limit.
         *
         * @param geom a geometry
         * @param distance the offset distance (positive for left, negative for right)
         * @param quadSegs the quadrant segments (-1 for default)
         * @param joinStyle the join style (-1 for default)
         * @param mitreLimit the mitre limit (-1 for default)
         * @return the offset curve
         */
        @JvmStatic
        fun getCurve(geom: Geometry, distance: Double, quadSegs: Int, joinStyle: Int, mitreLimit: Double): Geometry {
            val bufferParams = BufferParameters()
            if (quadSegs >= 0) bufferParams.setQuadrantSegments(quadSegs)
            if (joinStyle >= 0) bufferParams.setJoinStyle(joinStyle)
            if (mitreLimit >= 0) bufferParams.setMitreLimit(mitreLimit)
            val oc = OffsetCurve(geom, distance, bufferParams)
            return oc.curve
        }

        /**
         * Computes the offset curve of a geometry at a given distance,
         * joining curve sections into a single line for each input line.
         *
         * @param geom a geometry
         * @param distance the offset distance (positive for left, negative for right)
         * @return the joined offset curve
         */
        @JvmStatic
        fun getCurveJoined(geom: Geometry, distance: Double): Geometry {
            val oc = OffsetCurve(geom, distance)
            oc.setJoined(true)
            return oc.curve
        }

        /**
         * Gets the raw offset curve for a line at a given distance.
         * The quadrant segments, join style and mitre limit can be specified
         * via [BufferParameters].
         *
         * The raw offset line may contain loops and other artifacts which are
         * not present in the true offset curve.
         *
         * @param line the line to offset
         * @param distance the offset distance (positive for left, negative for right)
         * @param bufParams the buffer parameters to use
         * @return the raw offset curve points
         */
        @JvmStatic
        @JvmOverloads
        fun rawOffset(
            line: LineString,
            distance: Double,
            bufParams: BufferParameters = BufferParameters()
        ): Array<Coordinate> {
            val pts = line.coordinates
            val cleanPts = CoordinateArrays.removeRepeatedOrInvalidPoints(pts)
            val ocb = OffsetCurveBuilder(
                line.factory.precisionModel, bufParams
            )
            val rawPts = ocb.getOffsetCurve(cleanPts, distance)
            return rawPts!!
        }

        private fun getBufferOriented(
            geom: LineString,
            distance: Double,
            bufParams: BufferParameters
        ): Polygon {
            val buffer: Geometry = BufferOp.bufferOp(geom, abs(distance), bufParams)
            var bufferPoly = extractMaxAreaPolygon(buffer)
            //-- for negative distances (Right of input) reverse buffer direction to match offset curve
            if (distance < 0) {
                bufferPoly = bufferPoly.reverse()
            }
            return bufferPoly
        }

        /**
         * Extracts the largest polygon by area from a geometry.
         * Used here to avoid issues with non-robust buffer results
         * which have spurious extra polygons.
         *
         * @param geom a geometry
         * @return the polygon element of largest area
         */
        private fun extractMaxAreaPolygon(geom: Geometry): Polygon {
            if (geom.numGeometries == 1) return geom as Polygon
            var maxArea = 0.0
            var maxPoly: Polygon? = null
            for (i in 0 until geom.numGeometries) {
                val poly = geom.getGeometryN(i) as Polygon
                val area = poly.area
                if (maxPoly == null || area > maxArea) {
                    maxPoly = poly
                    maxArea = area
                }
            }
            return maxPoly!!
        }

        private fun next(i: Int, size: Int): Int {
            val n = i + 1
            return if (n < size) n else 0
        }

        private fun prev(i: Int, size: Int): Int {
            val p = i - 1
            return if (p < 0) size - 1 else p
        }
    }
}
