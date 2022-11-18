/*
 * Copyright (c) 2016 Martin Davis.
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

import org.locationtech.jts.algorithm.Angle.angle
import org.locationtech.jts.algorithm.Angle.angleBetweenOriented
import org.locationtech.jts.algorithm.Angle.normalize
import org.locationtech.jts.algorithm.Distance.pointToSegment
import org.locationtech.jts.algorithm.Intersection.intersection
import org.locationtech.jts.algorithm.Intersection.lineSegment
import org.locationtech.jts.algorithm.LineIntersector
import org.locationtech.jts.algorithm.Orientation
import org.locationtech.jts.algorithm.Orientation.index
import org.locationtech.jts.algorithm.RobustLineIntersector
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.LineSegment
import org.locationtech.jts.geom.Position
import org.locationtech.jts.geom.PrecisionModel
import org.locationtech.jts.legacy.Math.abs
import org.locationtech.jts.legacy.Math.atan2
import org.locationtech.jts.legacy.Math.cos
import org.locationtech.jts.legacy.Math.sin
import org.locationtech.jts.legacy.Math.sqrt
import kotlin.math.PI

/**
 * Generates segments which form an offset curve.
 * Supports all end cap and join options
 * provided for buffering.
 * This algorithm implements various heuristics to
 * produce smoother, simpler curves which are
 * still within a reasonable tolerance of the
 * true curve.
 *
 * @author Martin Davis
 */
internal class OffsetSegmentGenerator(
    private val precisionModel: PrecisionModel,
    bufParams: BufferParameters, distance: Double
) {
    /**
     * the max error of approximation (distance) between a quad segment and the true fillet curve
     */
    private var maxCurveSegmentError = 0.0

    /**
     * The angle quantum with which to approximate a fillet curve
     * (based on the input # of quadrant segments)
     */
    private val filletAngleQuantum: Double

    /**
     * The Closing Segment Length Factor controls how long
     * "closing segments" are.  Closing segments are added
     * at the middle of inside corners to ensure a smoother
     * boundary for the buffer offset curve.
     * In some cases (particularly for round joins with default-or-better
     * quantization) the closing segments can be made quite short.
     * This substantially improves performance (due to fewer intersections being created).
     *
     * A closingSegFactor of 0 results in lines to the corner vertex
     * A closingSegFactor of 1 results in lines halfway to the corner vertex
     * A closingSegFactor of 80 results in lines 1/81 of the way to the corner vertex
     * (this option is reasonable for the very common default situation of round joins
     * and quadrantSegs >= 8)
     */
    private var closingSegLengthFactor = 1
    private var segList: OffsetSegmentString? = null
    private var distance = 0.0
    private val bufParams: BufferParameters
    private val li: LineIntersector
    private var s0: Coordinate? = null
    private var s1: Coordinate? = null
    private var s2: Coordinate? = null
    private val seg0 = LineSegment()
    private val seg1 = LineSegment()
    private val offset0 = LineSegment()
    private val offset1 = LineSegment()
    private var side = 0
    private var hasNarrowConcaveAngle = false

    init {
        this.bufParams = bufParams

        // compute intersections in full precision, to provide accuracy
        // the points are rounded as they are inserted into the curve line
        li = RobustLineIntersector()
        var quadSegs: Int = bufParams.getQuadrantSegments()
        if (quadSegs < 1) quadSegs = 1
        filletAngleQuantum = PI / 2.0 / quadSegs
        /**
         * Non-round joins cause issues with short closing segments, so don't use
         * them. In any case, non-round joins only really make sense for relatively
         * small buffer distances.
         */
        if (bufParams.getQuadrantSegments() >= 8
            && bufParams.getJoinStyle() == BufferParameters.JOIN_ROUND
        ) closingSegLengthFactor = MAX_CLOSING_SEG_LEN_FACTOR
        init(distance)
    }

    /**
     * Tests whether the input has a narrow concave angle
     * (relative to the offset distance).
     * In this case the generated offset curve will contain self-intersections
     * and heuristic closing segments.
     * This is expected behaviour in the case of Buffer curves.
     * For pure Offset Curves,
     * the output needs to be further treated
     * before it can be used.
     *
     * @return true if the input has a narrow concave angle
     */
    fun hasNarrowConcaveAngle(): Boolean {
        return hasNarrowConcaveAngle
    }

    private fun init(distance: Double) {
        this.distance = distance
        maxCurveSegmentError = distance * (1 - cos(filletAngleQuantum / 2.0))
        segList = OffsetSegmentString()
        segList!!.setPrecisionModel(precisionModel)
        /**
         * Choose the min vertex separation as a small fraction of the offset distance.
         */
        segList!!.setMinimumVertexDistance(distance * CURVE_VERTEX_SNAP_DISTANCE_FACTOR)
    }

    fun initSideSegments(s1: Coordinate?, s2: Coordinate?, side: Int) {
        this.s1 = s1
        this.s2 = s2
        this.side = side
        seg1.setCoordinates(s1!!, s2!!)
        computeOffsetSegment(seg1, side, distance, offset1)
    }

    val coordinates: Array<Coordinate>
        get() = segList!!.coordinates

    fun closeRing() {
        segList!!.closeRing()
    }

    fun addSegments(pt: Array<Coordinate>, isForward: Boolean) {
        segList!!.addPts(pt, isForward)
    }

    fun addFirstSegment() {
        segList!!.addPt(offset1.p0)
    }

    /**
     * Add last offset point
     */
    fun addLastSegment() {
        segList!!.addPt(offset1.p1)
    }

    //private static double MAX_CLOSING_SEG_LEN = 3.0;
    fun addNextSegment(p: Coordinate?, addStartPoint: Boolean) {
        // s0-s1-s2 are the coordinates of the previous segment and the current one
        s0 = s1
        s1 = s2
        s2 = p
        seg0.setCoordinates(s0!!, s1!!)
        computeOffsetSegment(seg0, side, distance, offset0)
        seg1.setCoordinates(s1!!, s2!!)
        computeOffsetSegment(seg1, side, distance, offset1)

        // do nothing if points are equal
        if (s1!!.equals(s2)) return
        val orientation = index(s0, s1, s2)
        val outsideTurn =
            orientation == Orientation.CLOCKWISE && side == Position.LEFT || orientation == Orientation.COUNTERCLOCKWISE && side == Position.RIGHT
        if (orientation == 0) { // lines are collinear
            addCollinear(addStartPoint)
        } else if (outsideTurn) {
            addOutsideTurn(orientation, addStartPoint)
        } else { // inside turn
            addInsideTurn(orientation, addStartPoint)
        }
    }

    private fun addCollinear(addStartPoint: Boolean) {
        /**
         * This test could probably be done more efficiently,
         * but the situation of exact collinearity should be fairly rare.
         */
        li.computeIntersection(s0, s1, s1, s2)
        val numInt = li.intersectionNum
        /**
         * if numInt is < 2, the lines are parallel and in the same direction. In
         * this case the point can be ignored, since the offset lines will also be
         * parallel.
         */
        if (numInt >= 2) {
            /**
             * segments are collinear but reversing.
             * Add an "end-cap" fillet
             * all the way around to other direction This case should ONLY happen
             * for LineStrings, so the orientation is always CW. (Polygons can never
             * have two consecutive segments which are parallel but reversed,
             * because that would be a self intersection.
             *
             */
            if (bufParams.getJoinStyle() == BufferParameters.JOIN_BEVEL
                || bufParams.getJoinStyle() == BufferParameters.JOIN_MITRE
            ) {
                if (addStartPoint) segList!!.addPt(offset0.p1)
                segList!!.addPt(offset1.p0)
            } else {
                addCornerFillet(s1, offset0.p1, offset1.p0, Orientation.CLOCKWISE, distance)
            }
        }
    }

    /**
     * Adds the offset points for an outside (convex) turn
     *
     * @param orientation
     * @param addStartPoint
     */
    private fun addOutsideTurn(orientation: Int, addStartPoint: Boolean) {
        /**
         * Heuristic: If offset endpoints are very close together,
         * just use one of them as the corner vertex.
         * This avoids problems with computing mitre corners in the case
         * where the two segments are almost parallel
         * (which is hard to compute a robust intersection for).
         */
        if (offset0.p1.distance(offset1.p0) < distance * OFFSET_SEGMENT_SEPARATION_FACTOR) {
            segList!!.addPt(offset0.p1)
            return
        }
        if (bufParams.getJoinStyle() == BufferParameters.JOIN_MITRE) {
            addMitreJoin(s1, offset0, offset1, distance)
        } else if (bufParams.getJoinStyle() == BufferParameters.JOIN_BEVEL) {
            addBevelJoin(offset0, offset1)
        } else {
            // add a circular fillet connecting the endpoints of the offset segments
            if (addStartPoint) segList!!.addPt(offset0.p1)
            // TESTING - comment out to produce beveled joins
            addCornerFillet(s1, offset0.p1, offset1.p0, orientation, distance)
            segList!!.addPt(offset1.p0)
        }
    }

    /**
     * Adds the offset points for an inside (concave) turn.
     *
     * @param orientation
     * @param addStartPoint
     */
    private fun addInsideTurn(orientation: Int, addStartPoint: Boolean) {
        /**
         * add intersection point of offset segments (if any)
         */
        li.computeIntersection(offset0.p0, offset0.p1, offset1.p0, offset1.p1)
        if (li.hasIntersection()) {
            segList!!.addPt(li.getIntersection(0))
        } else {
            /**
             * If no intersection is detected,
             * it means the angle is so small and/or the offset so
             * large that the offsets segments don't intersect.
             * In this case we must
             * add a "closing segment" to make sure the buffer curve is continuous,
             * fairly smooth (e.g. no sharp reversals in direction)
             * and tracks the buffer correctly around the corner. The curve connects
             * the endpoints of the segment offsets to points
             * which lie toward the centre point of the corner.
             * The joining curve will not appear in the final buffer outline, since it
             * is completely internal to the buffer polygon.
             *
             * In complex buffer cases the closing segment may cut across many other
             * segments in the generated offset curve.  In order to improve the
             * performance of the noding, the closing segment should be kept as short as possible.
             * (But not too short, since that would defeat its purpose).
             * This is the purpose of the closingSegFactor heuristic value.
             */
            /**
             * The intersection test above is vulnerable to robustness errors; i.e. it
             * may be that the offsets should intersect very close to their endpoints,
             * but aren't reported as such due to rounding. To handle this situation
             * appropriately, we use the following test: If the offset points are very
             * close, don't add closing segments but simply use one of the offset
             * points
             */
            hasNarrowConcaveAngle = true
            //System.out.println("NARROW ANGLE - distance = " + distance);
            if (offset0.p1.distance(offset1.p0) < distance
                * INSIDE_TURN_VERTEX_SNAP_DISTANCE_FACTOR
            ) {
                segList!!.addPt(offset0.p1)
            } else {
                // add endpoint of this segment offset
                segList!!.addPt(offset0.p1)
                /**
                 * Add "closing segment" of required length.
                 */
                if (closingSegLengthFactor > 0) {
                    val mid0 = Coordinate(
                        (closingSegLengthFactor * offset0.p1.x + s1!!.x) / (closingSegLengthFactor + 1),
                        (closingSegLengthFactor * offset0.p1.y + s1!!.y) / (closingSegLengthFactor + 1)
                    )
                    segList!!.addPt(mid0)
                    val mid1 = Coordinate(
                        (closingSegLengthFactor * offset1.p0.x + s1!!.x) / (closingSegLengthFactor + 1),
                        (closingSegLengthFactor * offset1.p0.y + s1!!.y) / (closingSegLengthFactor + 1)
                    )
                    segList!!.addPt(mid1)
                } else {
                    /**
                     * This branch is not expected to be used except for testing purposes.
                     * It is equivalent to the JTS 1.9 logic for closing segments
                     * (which results in very poor performance for large buffer distances)
                     */
                    segList!!.addPt(s1)
                }

                //*/  
                // add start point of next segment offset
                segList!!.addPt(offset1.p0)
            }
        }
    }

    /**
     * Add an end cap around point p1, terminating a line segment coming from p0
     */
    fun addLineEndCap(p0: Coordinate, p1: Coordinate) {
        val seg = LineSegment(p0, p1)
        val offsetL = LineSegment()
        computeOffsetSegment(seg, Position.LEFT, distance, offsetL)
        val offsetR = LineSegment()
        computeOffsetSegment(seg, Position.RIGHT, distance, offsetR)
        val dx = p1.x - p0.x
        val dy = p1.y - p0.y
        val angle: Double = atan2(dy, dx)
        when (bufParams.getEndCapStyle()) {
            BufferParameters.CAP_ROUND -> {
                // add offset seg points with a fillet between them
                segList!!.addPt(offsetL.p1)
                addDirectedFillet(
                    p1,
                    angle + PI / 2,
                    angle - PI / 2,
                    Orientation.CLOCKWISE,
                    distance
                )
                segList!!.addPt(offsetR.p1)
            }

            BufferParameters.CAP_FLAT -> {
                // only offset segment points are added
                segList!!.addPt(offsetL.p1)
                segList!!.addPt(offsetR.p1)
            }

            BufferParameters.CAP_SQUARE -> {
                // add a square defined by extensions of the offset segment endpoints
                val squareCapSideOffset = Coordinate()
                squareCapSideOffset.x = abs(distance) * cos(angle)
                squareCapSideOffset.y = abs(distance) * sin(angle)
                val squareCapLOffset = Coordinate(
                    offsetL.p1.x + squareCapSideOffset.x,
                    offsetL.p1.y + squareCapSideOffset.y
                )
                val squareCapROffset = Coordinate(
                    offsetR.p1.x + squareCapSideOffset.x,
                    offsetR.p1.y + squareCapSideOffset.y
                )
                segList!!.addPt(squareCapLOffset)
                segList!!.addPt(squareCapROffset)
            }
        }
    }

    /**
     * Adds a mitre join connecting two convex offset segments.
     * The mitre is beveled if it exceeds the mitre limit factor.
     * The mitre limit is intended to prevent extremely long corners occurring.
     * If the mitre limit is very small it can cause unwanted artifacts around fairly flat corners.
     * This is prevented by using a simple bevel join in this case.
     * In other words, the limit prevents the corner from getting too long,
     * but it won't force it to be very short/flat.
     *
     * @param offset0 the first offset segment
     * @param offset1 the second offset segment
     * @param distance the offset distance
     */
    private fun addMitreJoin(
        cornerPt: Coordinate?,
        offset0: LineSegment,
        offset1: LineSegment,
        distance: Double
    ) {
        val mitreLimitDistance: Double = bufParams.getMitreLimit() * distance

        /**
         * First try a non-beveled join.
         * Compute the intersection point of the lines determined by the offsets.
         * Parallel or collinear lines will return a null point ==> need to be beveled
         *
         * Note: This computation is unstable if the offset segments are nearly collinear.
         * However, this situation should have been eliminated earlier by the check
         * for whether the offset segment endpoints are almost coincident
         */
        val intPt = intersection(offset0.p0, offset0.p1, offset1.p0, offset1.p1)
        if (intPt != null && intPt.distance(cornerPt!!) <= mitreLimitDistance) {
            segList!!.addPt(intPt)
            return
        }
        /**
         * In case the mitre limit is very small, try a plain bevel.
         * Use it if it's further than the limit.
         */
        val bevelDist = pointToSegment(cornerPt!!, offset0.p1, offset1.p0)
        if (bevelDist >= mitreLimitDistance) {
            addBevelJoin(offset0, offset1)
            return
        }
        /**
         * Have to construct a limited mitre bevel.
         */
        addLimitedMitreJoin(offset0, offset1, distance, mitreLimitDistance)
    }

    /**
     * Adds a limited mitre join connecting two convex offset segments.
     * A limited mitre join is beveled at the distance
     * determined by the mitre limit factor,
     * or as a standard bevel join, whichever is further.
     *
     * @param offset0 the first offset segment
     * @param offset1 the second offset segment
     * @param distance the offset distance
     * @param mitreLimitDistance the mitre limit distance
     */
    private fun addLimitedMitreJoin(
        offset0: LineSegment,
        offset1: LineSegment,
        distance: Double,
        mitreLimitDistance: Double
    ) {
        val cornerPt = seg0.p1
        // oriented angle of the corner formed by segments
        val angInterior = angleBetweenOriented(seg0.p0, cornerPt, seg1.p1)
        // half of the interior angle
        val angInterior2 = angInterior / 2

        // direction of bisector of the interior angle between the segments
        val dir0 = angle(cornerPt, seg0.p0)
        val dirBisector = normalize(dir0 + angInterior2)

        // midpoint of the bevel segment
        val bevelMidPt = project(cornerPt, -mitreLimitDistance, dirBisector)

        // direction of bevel segment (at right angle to corner bisector)
        val dirBevel = normalize(dirBisector + PI / 2.0)

        // compute the candidate bevel segment by projecting both sides of the midpoint
        val bevel0 = project(bevelMidPt, distance, dirBevel)
        val bevel1 = project(bevelMidPt, distance, dirBevel + PI)

        // compute actual bevel segment between the offset lines
        val bevelInt0 = lineSegment(offset0.p0, offset0.p1, bevel0, bevel1)
        val bevelInt1 = lineSegment(offset1.p0, offset1.p1, bevel0, bevel1)

        //-- add the limited bevel, if it intersects the offsets
        if (bevelInt0 != null && bevelInt1 != null) {
            segList!!.addPt(bevelInt0)
            segList!!.addPt(bevelInt1)
            return
        }
        /**
         * If the corner is very flat or the mitre limit is very small
         * the limited bevel segment may not intersect the offsets.
         * In this case just bevel the join.
         */
        addBevelJoin(offset0, offset1)
    }

    /**
     * Adds a bevel join connecting two offset segments
     * around a convex corner.
     *
     * @param offset0 the first offset segment
     * @param offset1 the second offset segment
     */
    private fun addBevelJoin(
        offset0: LineSegment,
        offset1: LineSegment
    ) {
        segList!!.addPt(offset0.p1)
        segList!!.addPt(offset1.p0)
    }

    /**
     * Add points for a circular fillet around a convex corner.
     * Adds the start and end points
     *
     * @param p base point of curve
     * @param p0 start point of fillet curve
     * @param p1 endpoint of fillet curve
     * @param direction the orientation of the fillet
     * @param radius the radius of the fillet
     */
    private fun addCornerFillet(p: Coordinate?, p0: Coordinate, p1: Coordinate, direction: Int, radius: Double) {
        val dx0 = p0.x - p!!.x
        val dy0 = p0.y - p.y
        var startAngle: Double = atan2(dy0, dx0)
        val dx1 = p1.x - p.x
        val dy1 = p1.y - p.y
        val endAngle: Double = atan2(dy1, dx1)
        if (direction == Orientation.CLOCKWISE) {
            if (startAngle <= endAngle) startAngle += 2.0 * PI
        } else {    // direction == COUNTERCLOCKWISE
            if (startAngle >= endAngle) startAngle -= 2.0 * PI
        }
        segList!!.addPt(p0)
        addDirectedFillet(p, startAngle, endAngle, direction, radius)
        segList!!.addPt(p1)
    }

    /**
     * Adds points for a circular fillet arc
     * between two specified angles.
     * The start and end point for the fillet are not added -
     * the caller must add them if required.
     *
     * @param direction is -1 for a CW angle, 1 for a CCW angle
     * @param radius the radius of the fillet
     */
    private fun addDirectedFillet(
        p: Coordinate?,
        startAngle: Double,
        endAngle: Double,
        direction: Int,
        radius: Double
    ) {
        val directionFactor = if (direction == Orientation.CLOCKWISE) -1 else 1
        val totalAngle: Double = abs(startAngle - endAngle)
        val nSegs = (totalAngle / filletAngleQuantum + 0.5).toInt()
        if (nSegs < 1) return  // no segments because angle is less than increment - nothing to do!

        // choose angle increment so that each segment has equal length
        val angleInc = totalAngle / nSegs
        val pt = Coordinate()
        for (i in 0 until nSegs) {
            val angle = startAngle + directionFactor * i * angleInc
            pt.x = p!!.x + radius * cos(angle)
            pt.y = p.y + radius * sin(angle)
            segList!!.addPt(pt)
        }
    }

    /**
     * Creates a CW circle around a point
     */
    fun createCircle(p: Coordinate?) {
        // add start point
        val pt = Coordinate(p!!.x + distance, p.y)
        segList!!.addPt(pt)
        addDirectedFillet(p, 0.0, 2.0 * PI, -1, distance)
        segList!!.closeRing()
    }

    /**
     * Creates a CW square around a point
     */
    fun createSquare(p: Coordinate?) {
        segList!!.addPt(Coordinate(p!!.x + distance, p.y + distance))
        segList!!.addPt(Coordinate(p.x + distance, p.y - distance))
        segList!!.addPt(Coordinate(p.x - distance, p.y - distance))
        segList!!.addPt(Coordinate(p.x - distance, p.y + distance))
        segList!!.closeRing()
    }

    companion object {
        /**
         * Factor which controls how close offset segments can be to
         * skip adding a filler or mitre.
         */
        private const val OFFSET_SEGMENT_SEPARATION_FACTOR = 1.0E-3

        /**
         * Factor which controls how close curve vertices on inside turns can be to be snapped
         */
        private const val INSIDE_TURN_VERTEX_SNAP_DISTANCE_FACTOR = 1.0E-3

        /**
         * Factor which controls how close curve vertices can be to be snapped
         */
        private const val CURVE_VERTEX_SNAP_DISTANCE_FACTOR = 1.0E-6

        /**
         * Factor which determines how short closing segs can be for round buffers
         */
        private const val MAX_CLOSING_SEG_LEN_FACTOR = 80

        /**
         * Compute an offset segment for an input segment on a given side and at a given distance.
         * The offset points are computed in full double precision, for accuracy.
         *
         * @param seg the segment to offset
         * @param side the side of the segment ([Position]) the offset lies on
         * @param distance the offset distance
         * @param offset the points computed for the offset segment
         */
        fun computeOffsetSegment(seg: LineSegment, side: Int, distance: Double, offset: LineSegment) {
            val sideSign = if (side == Position.LEFT) 1 else -1
            val dx = seg.p1.x - seg.p0.x
            val dy = seg.p1.y - seg.p0.y
            val len: Double = sqrt(dx * dx + dy * dy)
            // u is the vector that is the length of the offset, in the direction of the segment
            val ux = sideSign * distance * dx / len
            val uy = sideSign * distance * dy / len
            offset.p0.x = seg.p0.x - uy
            offset.p0.y = seg.p0.y + ux
            offset.p1.x = seg.p1.x - uy
            offset.p1.y = seg.p1.y + ux
        }

        /**
         * Projects a point to a given distance in a given direction angle.
         *
         * @param pt the point to project
         * @param d the projection distance
         * @param dir the direction angle (in radians)
         * @return the projected point
         */
        private fun project(pt: Coordinate, d: Double, dir: Double): Coordinate {
            val x: Double = pt.x + d * cos(dir)
            val y: Double = pt.y + d * sin(dir)
            return Coordinate(x, y)
        }
    }
}