/*
 * Copyright (c) 2016 Vivid Solutions.
 * Copyright (c) 2020 Macrofocus GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.operation.buffer

import org.locationtech.jts.algorithm.Distance.pointToSegment
import org.locationtech.jts.algorithm.Orientation.isCCW
import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.CoordinateArrays.isRing
import org.locationtech.jts.geom.CoordinateArrays.removeRepeatedPoints
import org.locationtech.jts.geomgraph.Label
import org.locationtech.jts.geomgraph.Position
import org.locationtech.jts.legacy.Math
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
class OffsetCurveSetBuilder(
    private val inputGeom: Geometry,
    private val distance: Double,
    private val curveBuilder: OffsetCurveBuilder
) {
    private val curveList: MutableList<Any?> = ArrayList()

    /**
     * Computes the set of raw offset curves for the buffer.
     * Each offset curve has an attached [Label] indicating
     * its left and right location.
     *
     * @return a Collection of SegmentStrings representing the raw buffer curves
     */
    val curves: List<*>
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

    private fun add(g: Geometry) {
        if (g.isEmpty) return
        when (g) {
            is Polygon -> addPolygon(g)
            is LineString -> addLineString(g)
            is Point -> addPoint(
                g
            )
            is MultiPoint -> addCollection(g)
            is MultiLineString -> addCollection(g)
            is MultiPolygon -> addCollection(
                g
            )
            is GeometryCollection -> addCollection(g)
            else -> throw UnsupportedOperationException(g::class.simpleName)
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
        val coord = p.coordinates
        val curve = curveBuilder.getLineCurve(coord!!, distance)
        addCurve(curve, Location.EXTERIOR, Location.INTERIOR)
    }

    private fun addLineString(line: LineString) {
        if (curveBuilder.isLineOffsetEmpty(distance)) return
        val coord = removeRepeatedPoints(line.coordinates)
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
            val curve = curveBuilder.getLineCurve(coord, distance)
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
        val shellCoord = removeRepeatedPoints(shell!!.coordinates)
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
            val holeCoord = removeRepeatedPoints(hole!!.coordinates)

            // optimization - don't bother computing buffer for this hole
            // if the hole would be completely covered
            if (distance > 0.0 && isErodedCompletely(hole, -distance)) continue

            // Holes are topologically labelled opposite to the shell, since
            // the interior of the polygon lies on their opposite side
            // (on the left, if the hole is oriented CCW)
            addRingSide(
                holeCoord,
                offsetDistance,
                Position.opposite(offsetSide),
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
        var s = side
        if (offsetDistance == 0.0 && coord.size < LinearRing.MINIMUM_VALID_SIZE) return
        var leftLoc = cwLeftLoc
        var rightLoc = cwRightLoc
        if (coord.size >= LinearRing.MINIMUM_VALID_SIZE
            && isCCW(coord)
        ) {
            leftLoc = cwRightLoc
            rightLoc = cwLeftLoc
            s = Position.opposite(s)
        }
        val curve = curveBuilder.getRingCurve(coord, s, offsetDistance)
        addCurve(curve, leftLoc, rightLoc)
    }

    /**
     * The ringCoord is assumed to contain no repeated points.
     * It may be degenerate (i.e. contain only 1, 2, or 3 points).
     * In this case it has no area, and hence has a minimum diameter of 0.
     *
     * @param ringCoord
     * @param offsetDistance
     * @return
     */
    private fun isErodedCompletely(ring: LinearRing?, bufferDistance: Double): Boolean {
        val ringCoord = ring!!.coordinates
        // degenerate ring has no area
        if (ringCoord.size < 4) return bufferDistance < 0

        // important test to eliminate inverted triangle bug
        // also optimizes erosion test for triangles
        if (ringCoord.size == 4) return isTriangleErodedCompletely(ringCoord, bufferDistance)

        // if envelope is narrower than twice the buffer distance, ring is eroded
        val env = ring.envelopeInternal
        val envMinDimension = Math.min(env.height, env.width)
        return (bufferDistance < 0.0
                && 2 * Math.abs(bufferDistance) > envMinDimension)
        /**
         * The following is a heuristic test to determine whether an
         * inside buffer will be eroded completely.
         * It is based on the fact that the minimum diameter of the ring pointset
         * provides an upper bound on the buffer distance which would erode the
         * ring.
         * If the buffer distance is less than the minimum diameter, the ring
         * may still be eroded, but this will be determined by
         * a full topological computation.
         *
         */
//System.out.println(ring);
/* MD  7 Feb 2005 - there's an unknown bug in the MD code, so disable this for now
    MinimumDiameter md = new MinimumDiameter(ring);
    minDiam = md.getLength();
    //System.out.println(md.getDiameter());
    return minDiam < 2 * Math.abs(bufferDistance);
    */
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
        triangleCoord: Array<Coordinate>?,
        bufferDistance: Double
    ): Boolean {
        val tri = Triangle(triangleCoord!![0], triangleCoord[1], triangleCoord[2])
        val inCentre: Coordinate = tri.inCentre()
        val distToCentre = pointToSegment(inCentre, tri.p0, tri.p1)
        return distToCentre < Math.abs(bufferDistance)
    }
}