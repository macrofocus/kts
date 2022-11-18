/*
 * Copyright (c) 2016 Vivid Solutions.
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.operation.buffer

import org.locationtech.jts.algorithm.Distance.pointToSegment
import org.locationtech.jts.algorithm.Distance.pointToSegmentString
import org.locationtech.jts.algorithm.Orientation.isCCWArea
import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.CoordinateArrays.isRing
import org.locationtech.jts.geom.CoordinateArrays.removeRepeatedOrInvalidPoints
import org.locationtech.jts.geom.Position.opposite
import org.locationtech.jts.geomgraph.Label
import org.locationtech.jts.legacy.Math.abs
import org.locationtech.jts.legacy.Math.min
import org.locationtech.jts.noding.NodedSegmentString
import org.locationtech.jts.noding.SegmentString

/**
 * @version 1.7
 */
/**
 * Creates all the raw offset curves for a buffer of a [Geometry].
 * Raw curves need to be noded together and polygonized to form the final buffer area.
 *
 * @version 1.7
 */
class BufferCurveSetBuilder(
    private val inputGeom: Geometry?,
    private val distance: Double,
    precisionModel: PrecisionModel,
    bufParams: BufferParameters
) {
    private val curveBuilder: OffsetCurveBuilder
    private val curveList: MutableList<SegmentString> = ArrayList()
    private var isInvertOrientation = false

    /**
     * Sets whether the offset curve is generated
     * using the inverted orientation of input rings.
     * This allows generating a buffer(0) polygon from the smaller lobes
     * of self-crossing rings.
     *
     * @param isInvertOrientation true if input ring orientation should be inverted
     */
    fun setInvertOrientation(isInvertOrientation: Boolean) {
        this.isInvertOrientation = isInvertOrientation
    }

    /**
     * Computes orientation of a ring using a signed-area orientation test.
     * For invalid (self-crossing) rings this ensures the largest enclosed area
     * is taken to be the interior of the ring.
     * This produces a more sensible result when
     * used for repairing polygonal geometry via buffer-by-zero.
     * For buffer  use the lower robustness of orientation-by-area
     * doesn't matter, since narrow or flat rings
     * produce an acceptable offset curve for either orientation.
     *
     * @param coord the ring coordinates
     * @return true if the ring is CCW
     */
    private fun isRingCCW(coord: Array<Coordinate>): Boolean {
        val isCCW = isCCWArea(coord)
        //--- invert orientation if required
        return if (isInvertOrientation) !isCCW else isCCW
    }

    /**
     * Computes the set of raw offset curves for the buffer.
     * Each offset curve has an attached [Label] indicating
     * its left and right location.
     *
     * @return a Collection of SegmentStrings representing the raw buffer curves
     */
    val curves: MutableList<SegmentString>
        get() {
            add(inputGeom)
            return curveList
        }

    /**
     * Creates a [SegmentString] for a coordinate list which is a raw offset curve,
     * and adds it to the list of buffer curves.
     * The SegmentString is tagged with a Label giving the topology of the curve.
     * The curve may be oriented in either direction.
     * If the curve is oriented CW, the locations will be:
     * <br></br>Left: Location.EXTERIOR
     * <br></br>Right: Location.INTERIOR
     */
    private fun addCurve(coord: Array<Coordinate>?, leftLoc: Int, rightLoc: Int) {
        // don't add null or trivial curves
        if (coord == null || coord.size < 2) return
        // add the edge for a coordinate list which is a raw offset curve
        val e: SegmentString = NodedSegmentString(
            coord,
            Label(0, Location.BOUNDARY, leftLoc, rightLoc)
        )
        curveList.add(e)
    }

    private fun add(g: Geometry?) {
        if (g!!.isEmpty) return
        when (g) {
            is Polygon -> addPolygon(g)
            is LineString -> addLineString(
                g
            )

            is Point -> addPoint(g)
            is MultiPoint -> addCollection(
                g
            )

            is MultiLineString -> addCollection(g)
            is MultiPolygon -> addCollection(
                g
            )

            is GeometryCollection -> addCollection(g)
            else -> throw UnsupportedOperationException("${g::class::simpleName}")
        }
    }

    private fun addCollection(gc: GeometryCollection) {
        for (i in 0 until gc.numGeometries) {
            val g = gc.getGeometryN(i)
            add(g)
        }
    }

    /**
     * Add a Point to the graph.
     */
    private fun addPoint(p: Point) {
        // a zero or negative width buffer of a point is empty
        if (distance <= 0.0) return
        val coord: Array<Coordinate> = p.coordinates
        // skip if coordinate is invalid
        if (coord.isNotEmpty() && !coord[0].isValid) return
        val curve: Array<Coordinate>? = curveBuilder.getLineCurve(coord, distance)
        addCurve(curve, Location.EXTERIOR, Location.INTERIOR)
    }

    private fun addLineString(line: LineString) {
        if (curveBuilder.isLineOffsetEmpty(distance)) return
        val coord = clean(line.coordinates)
        /**
         * Rings (closed lines) are generated with a continuous curve,
         * with no end arcs. This produces better quality linework,
         * and avoids noding issues with arcs around almost-parallel end segments.
         * See JTS #523 and #518.
         *
         * Singled-sided buffers currently treat rings as if they are lines.
         */
        if (isRing(coord) && !curveBuilder.bufferParameters.isSingleSided) {
            addRingBothSides(coord, distance)
        } else {
            val curve: Array<Coordinate>? = curveBuilder.getLineCurve(coord, distance)
            addCurve(curve, Location.EXTERIOR, Location.INTERIOR)
        }
        // TESTING
        //Coordinate[] curveTrim = BufferCurveLoopPruner.prune(curve); 
        //addCurve(curveTrim, Location.EXTERIOR, Location.INTERIOR);
    }

    private fun addPolygon(p: Polygon) {
        var offsetDistance = distance
        var offsetSide = Position.LEFT
        if (distance < 0.0) {
            offsetDistance = -distance
            offsetSide = Position.RIGHT
        }
        val shell = p.exteriorRing
        val shellCoord = clean(shell!!.coordinates)
        // optimization - don't bother computing buffer
        // if the polygon would be completely eroded
        if (distance < 0.0 && isErodedCompletely(shell, distance)) return
        // don't attempt to buffer a polygon with too few distinct vertices
        if (distance <= 0.0 && shellCoord.size < 3) return
        addRingSide(
            shellCoord,
            offsetDistance,
            offsetSide,
            Location.EXTERIOR,
            Location.INTERIOR
        )
        for (i in 0 until p.getNumInteriorRing()) {
            val hole = p.getInteriorRingN(i)
            val holeCoord = clean(hole.coordinates)

            // optimization - don't bother computing buffer for this hole
            // if the hole would be completely covered
            if (distance > 0.0 && isErodedCompletely(hole, -distance)) continue

            // Holes are topologically labelled opposite to the shell, since
            // the interior of the polygon lies on their opposite side
            // (on the left, if the hole is oriented CCW)
            addRingSide(
                holeCoord,
                offsetDistance,
                opposite(offsetSide),
                Location.INTERIOR,
                Location.EXTERIOR
            )
        }
    }

    private fun addRingBothSides(coord: Array<Coordinate>, distance: Double) {
        addRingSide(
            coord, distance,
            Position.LEFT,
            Location.EXTERIOR, Location.INTERIOR
        )
        /* Add the opposite side of the ring
    */addRingSide(
            coord, distance,
            Position.RIGHT,
            Location.INTERIOR, Location.EXTERIOR
        )
    }

    /**
     * Adds an offset curve for one side of a ring.
     * The side and left and right topological location arguments
     * are provided as if the ring is oriented CW.
     * (If the ring is in the opposite orientation,
     * this is detected and
     * the left and right locations are interchanged and the side is flipped.)
     *
     * @param coord the coordinates of the ring (must not contain repeated points)
     * @param offsetDistance the positive distance at which to create the buffer
     * @param side the side [Position] of the ring on which to construct the buffer line
     * @param cwLeftLoc the location on the L side of the ring (if it is CW)
     * @param cwRightLoc the location on the R side of the ring (if it is CW)
     */
    private fun addRingSide(
        coord: Array<Coordinate>,
        offsetDistance: Double,
        side: Int,
        cwLeftLoc: Int,
        cwRightLoc: Int
    ) {
        // don't bother adding ring if it is "flat" and will disappear in the output
        var side = side
        if (offsetDistance == 0.0 && coord.size < LinearRing.MINIMUM_VALID_SIZE) return
        var leftLoc = cwLeftLoc
        var rightLoc = cwRightLoc
        val isCCW = isRingCCW(coord)
        if (coord.size >= LinearRing.MINIMUM_VALID_SIZE
            && isCCW
        ) {
            leftLoc = cwRightLoc
            rightLoc = cwLeftLoc
            side = opposite(side)
        }
        val curve: Array<Coordinate>? = curveBuilder.getRingCurve(coord, side, offsetDistance)
        /**
         * If the offset curve has inverted completely it will produce
         * an unwanted artifact in the result, so skip it.
         */
        if (isRingCurveInverted(coord, offsetDistance, curve)) {
            return
        }
        addCurve(curve, leftLoc, rightLoc)
    }

    init {
        curveBuilder = OffsetCurveBuilder(precisionModel, bufParams)
    }

    companion object {
        /**
         * Keeps only valid coordinates, and removes repeated points.
         *
         * @param coordinates the coordinates to clean
         * @return an array of clean coordinates
         */
        private fun clean(coords: Array<Coordinate>): Array<Coordinate> {
            return removeRepeatedOrInvalidPoints(coords)
        }

        private const val MAX_INVERTED_RING_SIZE = 9
        private const val INVERTED_CURVE_VERTEX_FACTOR = 4
        private const val NEARNESS_FACTOR = 0.99

        /**
         * Tests whether the offset curve for a ring is fully inverted.
         * An inverted ("inside-out") curve occurs in some specific situations
         * involving a buffer distance which should result in a fully-eroded (empty) buffer.
         * It can happen that the sides of a small, convex polygon
         * produce offset segments which all cross one another to form
         * a curve with inverted orientation.
         * This happens at buffer distances slightly greater than the distance at
         * which the buffer should disappear.
         * The inverted curve will produce an incorrect non-empty buffer (for a shell)
         * or an incorrect hole (for a hole).
         * It must be discarded from the set of offset curves used in the buffer.
         * Heuristics are used to reduce the number of cases which area checked,
         * for efficiency and correctness.
         *
         *
         * See https://github.com/locationtech/jts/issues/472
         *
         * @param inputPts the input ring
         * @param distance the buffer distance
         * @param curvePts the generated offset curve
         * @return true if the offset curve is inverted
         */
        private fun isRingCurveInverted(
            inputPts: Array<Coordinate>,
            distance: Double,
            curvePts: Array<Coordinate>?
        ): Boolean {
            if (distance == 0.0) return false
            /**
             * Only proper rings can invert.
             */
            if (inputPts.size <= 3) return false
            /**
             * Heuristic based on low chance that a ring with many vertices will invert.
             * This low limit ensures this test is fairly efficient.
             */
            if (inputPts.size >= MAX_INVERTED_RING_SIZE) return false
            /**
             * Don't check curves which are much larger than the input.
             * This improves performance by avoiding checking some concave inputs
             * (which can produce fillet arcs with many more vertices)
             */
            if (curvePts!!.size > INVERTED_CURVE_VERTEX_FACTOR * inputPts.size) return false
            /**
             * Check if the curve vertices are all closer to the input ring
             * than the buffer distance.
             * If so, the curve is NOT a valid buffer curve.
             */
            val distTol: Double =
                NEARNESS_FACTOR * abs(
                    distance
                )
            val maxDist =
                maxDistance(curvePts, inputPts)
            return maxDist < distTol
        }

        /**
         * Computes the maximum distance out of a set of points to a linestring.
         *
         * @param pts the points
         * @param line the linestring vertices
         * @return the maximum distance
         */
        private fun maxDistance(pts: Array<Coordinate>?, line: Array<Coordinate>): Double {
            var maxDistance = 0.0
            for (p in pts!!) {
                val dist = pointToSegmentString(p, line)
                if (dist > maxDistance) {
                    maxDistance = dist
                }
            }
            return maxDistance
        }

        /**
         * Tests whether a ring buffer is eroded completely (is empty)
         * based on simple heuristics.
         *
         * The ringCoord is assumed to contain no repeated points.
         * It may be degenerate (i.e. contain only 1, 2, or 3 points).
         * In this case it has no area, and hence has a minimum diameter of 0.
         *
         * @param ringCoord
         * @param offsetDistance
         * @return
         */
        private fun isErodedCompletely(ring: LinearRing, bufferDistance: Double): Boolean {
            val ringCoord: Array<Coordinate> = ring.coordinates
            // degenerate ring has no area
            if (ringCoord.size < 4) return bufferDistance < 0

            // important test to eliminate inverted triangle bug
            // also optimizes erosion test for triangles
            if (ringCoord.size == 4) return isTriangleErodedCompletely(ringCoord, bufferDistance)

            // if envelope is narrower than twice the buffer distance, ring is eroded
            val env = ring.envelopeInternal
            val envMinDimension: Double = min(env.height, env.width)
            return (bufferDistance < 0.0
                    && 2 * abs(bufferDistance) > envMinDimension)
        }

        /**
         * Tests whether a triangular ring would be eroded completely by the given
         * buffer distance.
         * This is a precise test.  It uses the fact that the inner buffer of a
         * triangle converges on the inCentre of the triangle (the point
         * equidistant from all sides).  If the buffer distance is greater than the
         * distance of the inCentre from a side, the triangle will be eroded completely.
         *
         * This test is important, since it removes a problematic case where
         * the buffer distance is slightly larger than the inCentre distance.
         * In this case the triangle buffer curve "inverts" with incorrect topology,
         * producing an incorrect hole in the buffer.
         *
         * @param triangleCoord
         * @param bufferDistance
         * @return
         */
        private fun isTriangleErodedCompletely(
            triangleCoord: Array<Coordinate>,
            bufferDistance: Double
        ): Boolean {
            val tri = Triangle(triangleCoord[0], triangleCoord[1], triangleCoord[2])
            val inCentre: Coordinate = tri.inCentre()
            val distToCentre = pointToSegment(inCentre, tri.p0, tri.p1)
            return distToCentre < abs(bufferDistance)
        }
    }
}