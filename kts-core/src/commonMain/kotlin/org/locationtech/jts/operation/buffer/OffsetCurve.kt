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
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Computes an offset curve from a geometry.
 * The offset curve is a linear geometry which is offset a specified distance
 * from the input.
 * If the offset distance is positive the curve lies on the left side of the input;
 * if it is negative the curve is on the right side.
 *
 *  * For a [LineString] the offset curve is a line.
 *  * For a [Point] the offset curve is an empty [LineString].
 *  * For a [Polygon] the offset curve is the boundary of the polygon buffer (which
 * may be a [MultiLineString].
 *  * For a collection the output is a [MultiLineString] containing the element offset curves.
 *
 * The offset curve is computed as a single contiguous section of the geometry buffer boundary.
 * In some geometric situations this definition is ill-defined.
 * This algorithm provides a "best-effort" interpretation.
 * In particular:
 *
 *  * For self-intersecting lines, the buffer boundary includes
 * offset lines for both left and right sides of the input line.
 * Only a single contiguous portion on the specified side is returned.
 *  * If the offset corresponds to buffer holes, only the largest hole is used.
 *
 * Offset curves support setting the number of quadrant segments,
 * the join style, and the mitre limit (if applicable) via
 * the [BufferParameters].
 *
 * @author Martin Davis
 */
class OffsetCurve @JvmOverloads constructor(
    private val inputGeom: Geometry,
    private val distance: Double,
    bufParams: BufferParameters? = null
) {
    private val bufferParams: BufferParameters
    private val matchDistance: Double
    private val geomFactory: GeometryFactory
    /**
     * Creates a new instance for computing an offset curve for a geometry at a given distance.
     * allowing the quadrant segments and join style and mitre limit to be set
     * via [BufferParameters].
     *
     * @param inputGeom
     * @param distance
     * @param bufParams
     */
    /**
     * Creates a new instance for computing an offset curve for a geometryat a given distance.
     * with default quadrant segments ([BufferParameters.DEFAULT_QUADRANT_SEGMENTS])
     * and join style ([BufferParameters.JOIN_STYLE]).
     *
     * @param geom the geometry to offset
     * @param distance the offset distance (positive = left, negative = right)
     *
     * @see BufferParameters
     */
    init {
        matchDistance = abs(distance) / NEARNESS_FACTOR
        geomFactory = inputGeom.factory

        //-- make new buffer params since the end cap style must be the default
        bufferParams = BufferParameters()
        if (bufParams != null) {
            bufferParams.setQuadrantSegments(bufParams.getQuadrantSegments())
            bufferParams.setJoinStyle(bufParams.getJoinStyle())
            bufferParams.setMitreLimit(bufParams.getMitreLimit())
        }
    }
    /**
     * Force LinearRings to be LineStrings.
     *
     * @param inputGeom a geometry which may be a LinearRing
     * @return a geometry which will be a LineString or MultiLineString
     */
    /**
     * Gets the computed offset curve.
     *
     * @return the offset curve geometry
     */
    val curve: Geometry?
        get() = flatMap(inputGeom, 1, object : GeometryMapper.MapOp {
            override fun map(geom: Geometry): Geometry? {
                if (geom is Point) return null
                return if (geom is Polygon) {
                    toLineString(geom.buffer(distance).boundary)
                } else computeCurve(geom as LineString?, distance)
            }
            /**
             * Force LinearRings to be LineStrings.
             *
             * @param geom a geometry which may be a LinearRing
             * @return a geometry which will be a LineString or MultiLineString
             */
            /**
             * Force LinearRings to be LineStrings.
             *
             * @param geom a geometry which may be a LinearRing
             * @return a geometry which will be a LineString or MultiLineString
             */
            /**
             * Force LinearRings to be LineStrings.
             *
             * @param geom a geometry which may be a LinearRing
             * @return a geometry which will be a LineString or MultiLineString
             */
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

    private fun computeCurve(lineGeom: LineString?, distance: Double): LineString {
        //-- first handle special/simple cases
        if (lineGeom!!.numPoints < 2 || lineGeom.length == 0.0) {
            return geomFactory.createLineString()
        }
        if (lineGeom.numPoints == 2) {
            return offsetSegment(lineGeom.coordinates, distance)
        }
        val rawOffset = rawOffset(lineGeom, distance, bufferParams)
        if (rawOffset!!.size == 0) {
            return geomFactory.createLineString()
        }
        /**
         * Note: If the raw offset curve has no
         * narrow concave angles or self-intersections it could be returned as is.
         * However, this is likely to be a less frequent situation,
         * and testing indicates little performance advantage,
         * so not doing this.
         */
        val bufferPoly = getBufferOriented(lineGeom, distance, bufferParams)

        //-- first try matching shell to raw curve
        val shell: Array<Coordinate> = bufferPoly!!.exteriorRing!!.coordinates
        var offsetCurve = computeCurve(shell, rawOffset)
        if (!offsetCurve.isEmpty
            || bufferPoly.getNumInteriorRing() == 0
        ) return offsetCurve

        //-- if shell didn't work, try matching to largest hole 
        val holePts: Array<Coordinate> = extractLongestHole(bufferPoly)!!.coordinates
        offsetCurve = computeCurve(holePts, rawOffset)
        return offsetCurve
    }

    private fun offsetSegment(pts: Array<Coordinate>, distance: Double): LineString {
        val offsetSeg = LineSegment(pts[0], pts[1]).offset(distance)
        return geomFactory.createLineString(arrayOf(offsetSeg.p0, offsetSeg.p1))
    }

    private fun computeCurve(bufferPts: Array<Coordinate>, rawOffset: Array<Coordinate>?): LineString {
        val isInCurve = BooleanArray(bufferPts.size - 1)
        val segIndex: SegmentMCIndex =
            SegmentMCIndex(bufferPts)
        var curveStart = -1
        for (i in 0 until rawOffset!!.size - 1) {
            val index = markMatchingSegments(
                rawOffset[i], rawOffset[i + 1], segIndex, bufferPts, isInCurve
            )
            if (curveStart < 0) {
                curveStart = index
            }
        }
        val curvePts = extractSection(bufferPts, curveStart, isInCurve)
        return geomFactory.createLineString(curvePts)
    }

    private fun markMatchingSegments(
        p0: Coordinate?, p1: Coordinate?,
        segIndex: SegmentMCIndex, bufferPts: Array<Coordinate>,
        isInCurve: BooleanArray
    ): Int {
        val matchEnv = Envelope(p0!!, p1!!)
        matchEnv.expandBy(matchDistance)
        val action = MatchCurveSegmentAction(p0, p1, bufferPts, matchDistance, isInCurve)
        segIndex.query(matchEnv, action)
        return action.minCurveIndex
    }

    /**
     * An action to match a raw offset curve segment
     * to segments in the buffer ring
     * and mark them as being in the offset curve.
     *
     * @author Martin Davis
     */
    private class MatchCurveSegmentAction(
        private val p0: Coordinate?, private val p1: Coordinate?,
        private val bufferPts: Array<Coordinate>, private val matchDistance: Double, private val isInCurve: BooleanArray
    ) : MonotoneChainSelectAction() {
        private var minFrac = -1.0
        var minCurveIndex = -1
            private set

        override fun select(mc: MonotoneChain, segIndex: Int) {
            /**
             * A curveRingPt segment may match all or only a portion of a single raw segment.
             * There may be multiple curve ring segs that match along the raw segment.
             * The one closest to the segment start is recorded as the offset curve start.
             */
            val frac = subsegmentMatchFrac(bufferPts[segIndex], bufferPts[segIndex + 1], p0, p1, matchDistance)
            //-- no match
            if (frac < 0) return
            isInCurve[segIndex] = true

            //-- record lowest index
            if (minFrac < 0 || frac < minFrac) {
                minFrac = frac
                minCurveIndex = segIndex
            }
        }
    }

    companion object {
        /**
         * The nearness tolerance between the raw offset linework and the buffer curve.
         */
        private const val NEARNESS_FACTOR = 10000

        /**
         * Computes the offset curve of a geometry at a given distance.
         *
         * @param geom a geometry
         * @param distance the offset distance (positive = left, negative = right)
         * @return the offset curve
         */
        @JvmStatic
        fun getCurve(geom: Geometry, distance: Double): Geometry? {
            val oc = OffsetCurve(geom, distance)
            return oc.curve
        }

        /**
         * Computes the offset curve of a geometry at a given distance,
         * and for a specified quadrant segments, join style and mitre limit.
         *
         * @param geom a geometry
         * @param distance the offset distance (positive = left, negative = right)
         * @param quadSegs the quadrant segments (-1 for default)
         * @param joinStyle the join style (-1 for default)
         * @param mitreLimit the mitre limit (-1 for default)
         * @return the offset curve
         */
        @JvmStatic
        fun getCurve(geom: Geometry, distance: Double, quadSegs: Int, joinStyle: Int, mitreLimit: Double): Geometry? {
            val bufferParams: BufferParameters =
                BufferParameters()
            if (quadSegs >= 0) bufferParams.setQuadrantSegments(quadSegs)
            if (joinStyle >= 0) bufferParams.setJoinStyle(joinStyle)
            if (mitreLimit >= 0) bufferParams.setMitreLimit(mitreLimit)
            val oc = OffsetCurve(geom, distance, bufferParams)
            return oc.curve
        }
        /**
         * Gets the raw offset line.
         * The quadrant segments and join style and mitre limit to be set
         * via [BufferParameters].
         *
         *
         * The raw offset line may contain loops and other artifacts which are
         * not present in the true offset curve.
         * The raw offset line is matched to the buffer ring (which is clean)
         * to extract the offset curve.
         *
         * @param geom the linestring to offset
         * @param distance the offset distance
         * @param bufParams the buffer parameters to use
         * @return the raw offset line
         */
        /**
         * Gets the raw offset line, with default buffer parameters.
         *
         * @param geom the linestring to offset
         * @param distance the offset distance
         * @return the raw offset line
         */
        @JvmOverloads
        fun rawOffset(
            geom: LineString?,
            distance: Double,
            bufParams: BufferParameters = BufferParameters()
        ): Array<Coordinate>? {
            val ocb: OffsetCurveBuilder =
                OffsetCurveBuilder(
                    geom!!.factory.precisionModel, bufParams
                )
            return ocb.getOffsetCurve(geom!!.coordinates, distance)
        }

        private fun getBufferOriented(
            geom: LineString?,
            distance: Double,
            bufParams: BufferParameters
        ): Polygon? {
            val buffer: Geometry? = BufferOp.bufferOp(
                geom,
                abs(distance),
                bufParams
            )
            var bufferPoly = extractMaxAreaPolygon(buffer)
            //-- for negative distances (Right of input) reverse buffer direction to match offset curve
            if (distance < 0) {
                bufferPoly = bufferPoly!!.reverse()
            }
            return bufferPoly
        }

        /**
         * Extracts the largest polygon by area from a geometry.
         * Used here to avoid issues with non-robust buffer results which have spurious extra polygons.
         *
         * @param geom a geometry
         * @return the polygon element of largest area
         */
        private fun extractMaxAreaPolygon(geom: Geometry?): Polygon? {
            if (geom!!.numGeometries == 1) return geom as Polygon?
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
            return maxPoly
        }

        private fun extractLongestHole(poly: Polygon?): LinearRing? {
            var largestHole: LinearRing? = null
            var maxLen = -1.0
            for (i in 0 until poly!!.getNumInteriorRing()) {
                val hole = poly.getInteriorRingN(i)
                val len: Double = hole.length
                if (len > maxLen) {
                    largestHole = hole
                    maxLen = len
                }
            }
            return largestHole
        }

        /*
  // Slower, non-indexed algorithm.  Left here for future testing.
  
  private Coordinate[] OLDcomputeCurve(Coordinate[] curveRingPts, Coordinate[] rawOffset) {
    boolean[] isInCurve = new boolean[curveRingPts.length - 1];
    int curveStart = -1;
    for (int i = 0; i < rawOffset.length - 1; i++) {
      int index = markMatchingSegments(
                      rawOffset[i], rawOffset[i + 1], curveRingPts, isInCurve);
      if (curveStart < 0) {
        curveStart = index;
      }
    }
    Coordinate[] curvePts = extractSection(curveRingPts, isInCurve, curveStart);
    return curvePts;
  }

  private int markMatchingSegments(Coordinate p0, Coordinate p1, Coordinate[] curveRingPts, boolean[] isInCurve) {
    double minFrac = -1;
    int minCurveIndex = -1;
    for (int i = 0; i < curveRingPts.length - 1; i++) {
       // A curveRingPt seg will only match a portion of a single raw segment.
       // But there may be multiple curve ring segs that match along that segment.
       // The one closest to the segment start is recorded.
      double frac = subsegmentMatchFrac(curveRingPts[i], curveRingPts[i+1], p0, p1, matchDistance);
      //-- no match
      if (frac < 0) continue;
      
      isInCurve[i] = true;
      
      //-- record lowest index
      if (minFrac < 0 || frac < minFrac) {
        minFrac = frac;
        minCurveIndex = i;
      }
    }
    return minCurveIndex;
  }
  */
        private fun subsegmentMatchFrac(
            p0: Coordinate, p1: Coordinate,
            seg0: Coordinate?, seg1: Coordinate?, matchDistance: Double
        ): Double {
            if (matchDistance < pointToSegment(p0, seg0!!, seg1!!)) return (-1).toDouble()
            if (matchDistance < pointToSegment(p1, seg0, seg1)) return (-1).toDouble()
            //-- matched - determine position as fraction
            val seg = LineSegment(seg0, seg1)
            return seg.segmentFraction(p0)
        }

        /**
         * Extracts a section of a ring of coordinates, starting at a given index,
         * and keeping coordinates which are flagged as being required.
         *
         * @param ring the ring of points
         * @param startIndex the index of the start coordinate
         * @param isExtracted flag indicating if coordinate is to be extracted
         * @return
         */
        private fun extractSection(
            ring: Array<Coordinate>,
            startIndex: Int,
            isExtracted: BooleanArray
        ): Array<Coordinate> {
            if (startIndex < 0) return emptyArray()
            val coordList = CoordinateList()
            var i = startIndex
            do {
                coordList.add(ring[i], false)
                if (!isExtracted[i]) {
                    break
                }
                i = next(i, ring.size - 1)
            } while (i != startIndex)
            //-- handle case where every segment is extracted
            if (isExtracted[i]) {
                coordList.add(ring[i], false)
            }

            //-- if only one point found return empty LineString
            return if (coordList.size === 1) emptyArray() else coordList.toCoordinateArray()
        }

        private fun next(i: Int, size: Int): Int {
            var i = i
            i += 1
            return if (i < size) i else 0
        }
    }
}