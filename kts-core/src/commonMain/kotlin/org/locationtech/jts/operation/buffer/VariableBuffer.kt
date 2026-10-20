/*
 * Copyright (c) 2019 Martin Davis.
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
import org.locationtech.jts.algorithm.Orientation
import org.locationtech.jts.geom.*
import org.locationtech.jts.legacy.Math.abs
import org.locationtech.jts.legacy.Math.atan2
import org.locationtech.jts.legacy.Math.cos
import org.locationtech.jts.legacy.Math.isNaN
import org.locationtech.jts.legacy.Math.sin
import org.locationtech.jts.legacy.Math.sqrt
import kotlin.jvm.JvmStatic
import kotlin.math.PI
import kotlin.math.asin

/**
 * Creates a buffer polygon with a varying buffer distance
 * at each vertex along a line.
 * Vertex distances may be zero.
 *
 * Only single linestrings are supported as input, since buffer widths
 * are typically specified individually for each line.
 *
 * @author Martin Davis
 */
class VariableBuffer(line: Geometry, distance: DoubleArray) {
    private val line: LineString
    private val distance: DoubleArray
    private val geomFactory: GeometryFactory
    private val quadrantSegs: Int =
        BufferParameters.DEFAULT_QUADRANT_SEGMENTS// construct segment buffers

    // ensure an empty polygon is returned if needed
    /**
     * Computes the variable buffer polygon.
     *
     * @return a buffer polygon
     */
    val result: Geometry
        get() {
            val parts: MutableList<Geometry> = ArrayList()
            val pts = line.coordinates
            // construct segment buffers
            for (i in 1 until pts.size) {
                val dist0 = distance[i - 1]
                val dist1 = distance[i]
                if (dist0 > 0 || dist1 > 0) {
                    val poly = segmentBuffer(pts[i - 1], pts[i], dist0, dist1)
                    if (poly != null) parts.add(poly)
                }
            }
            val partsGeom = geomFactory
                .createGeometryCollection(GeometryFactory.toGeometryArray(parts))
            val buffer = partsGeom.union()

            // ensure an empty polygon is returned if needed
            return if (buffer!!.isEmpty) {
                geomFactory.createPolygon()
            } else buffer
        }

    /**
     * Computes a variable buffer polygon for a single segment,
     * with the given endpoints and buffer distances.
     * The individual segment buffers are unioned
     * to form the final buffer.
     * If one distance is zero, the end cap at that
     * segment end is the endpoint of the segment.
     * If both distances are zero, no polygon is returned.
     *
     * @param p0 the segment start point
     * @param p1 the segment end point
     * @param dist0 the buffer distance at the start point
     * @param dist1 the buffer distance at the end point
     * @return the segment buffer, or null if void
     */
    private fun segmentBuffer(
        p0: Coordinate, p1: Coordinate,
        dist0: Double, dist1: Double
    ): Polygon? {
        /**
         * Skip buffer polygon if both distances are zero
         */
        if (dist0 <= 0 && dist1 <= 0) return null

        /**
         * Generation algorithm requires increasing distance, so flip if needed
         */
        return if (dist0 > dist1) {
            segmentBufferOriented(p1, p0, dist1, dist0)
        } else segmentBufferOriented(p0, p1, dist0, dist1)
    }

    private fun segmentBufferOriented(
        p0: Coordinate, p1: Coordinate,
        dist0: Double, dist1: Double
    ): Polygon? {
        //-- Assert: dist0 <= dist1

        //-- forward tangent line
        val tangent = outerTangent(p0, dist0, p1, dist1)

        //-- if tangent is null then compute a buffer for largest circle
        if (tangent == null) {
            var center = p0
            var dist = dist0
            if (dist1 > dist0) {
                center = p1
                dist = dist1
            }
            return circle(center, dist)
        }

        //-- reverse tangent line on other side of segment
        val tangentReflect = reflect(tangent, p0, p1, dist0)
        val coords = CoordinateList()
        //-- end cap
        addCap(p1, dist1, tangent.p1, tangentReflect.p1, coords)
        //-- start cap
        addCap(p0, dist0, tangentReflect.p0, tangent.p0, coords)
        coords.closeRing()
        val pts = coords.toCoordinateArray()
        return geomFactory.createPolygon(pts)
    }

    private fun reflect(seg: LineSegment, p0: Coordinate, p1: Coordinate, dist0: Double): LineSegment {
        val line = LineSegment(p0, p1)
        var r0 = line.reflect(seg.p0)
        val r1 = line.reflect(seg.p1)
        //-- avoid numeric jitter if first distance is zero (second dist must be > 0)
        if (dist0 == 0.0) r0 = p0.copy()
        return LineSegment(r0, r1)
    }

    /**
     * Returns a circular polygon.
     *
     * @param center the circle center point
     * @param radius the radius
     * @return a polygon, or null if the radius is 0
     */
    private fun circle(center: Coordinate, radius: Double): Polygon? {
        if (radius <= 0) return null
        val nPts = 4 * quadrantSegs
        val pts = arrayOfNulls<Coordinate>(nPts + 1)
        val angInc: Double = PI / 2 / quadrantSegs
        for (i in 0 until nPts) {
            pts[i] = projectPolar(center, radius, i * angInc)
        }
        pts[pts.size - 1] = pts[0]!!.copy()
        return geomFactory.createPolygon(pts.requireNoNulls())
    }

    /**
     * Adds a semi-circular cap CCW around the point p.
     *
     * The vertices in caps are generated at fixed angles around a point.
     * This allows caps at the same point to share vertices,
     * which reduces artifacts when the segment buffers are merged.
     *
     * @param p the centre point of the cap
     * @param r the cap radius
     * @param t1 the starting point of the cap
     * @param t2 the ending point of the cap
     * @param coords the coordinate list to add to
     */
    private fun addCap(p: Coordinate, r: Double, t1: Coordinate, t2: Coordinate, coords: CoordinateList) {
        //-- if radius is zero just copy the vertex
        if (r == 0.0) {
            coords.add(p.copy(), false)
            return
        }
        coords.add(t1, false)
        var angStart = angle(p, t1)
        val angEnd = angle(p, t2)
        if (angStart < angEnd) angStart += 2 * PI
        val indexStart = capAngleIndex(angStart)
        val indexEnd = capAngleIndex(angEnd)
        val capSegLen = r * 2 * sin(PI / 4 / quadrantSegs)
        val minSegLen = capSegLen / MIN_CAP_SEG_LEN_FACTOR
        for (i in indexStart downTo indexEnd) {
            //-- use negative increment to create points CW
            val ang = capAngle(i)
            val capPt = projectPolar(p, r, ang)
            var isCapPointHighQuality = true
            /**
             * Due to the fixed locations of the cap points,
             * a start or end cap point might create
             * a "reversed" segment to the next tangent point.
             * This causes an unwanted narrow spike in the buffer curve,
             * which can cause holes in the final buffer polygon.
             * These checks remove these points.
             */
            if (i == indexStart
                && Orientation.CLOCKWISE != Orientation.index(p, t1, capPt)
            ) {
                isCapPointHighQuality = false
            } else if (i == indexEnd
                && Orientation.COUNTERCLOCKWISE != Orientation.index(p, t2, capPt)
            ) {
                isCapPointHighQuality = false
            }

            /**
             * Remove short segments between the cap and the tangent segments.
             */
            if (capPt.distance(t1) < minSegLen) {
                isCapPointHighQuality = false
            } else if (capPt.distance(t2) < minSegLen) {
                isCapPointHighQuality = false
            }
            if (isCapPointHighQuality) {
                coords.add(capPt, false)
            }
        }
        coords.add(t2, false)
    }

    /**
     * Computes the actual angle for a cap angle index.
     *
     * @param index the cap angle index
     * @return the angle
     */
    private fun capAngle(index: Int): Double {
        val capSegAng: Double = PI / 2 / quadrantSegs
        return index * capSegAng
    }

    /**
     * Computes the canonical cap point index for a given angle.
     * The angle is rounded down to the next lower
     * index.
     *
     * In order to reduce the number of points created by overlapping end caps,
     * cap points are generated at the same locations around a circle.
     * The index is the index of the points around the circle,
     * with 0 being the point at (1,0).
     * The total number of points around the circle is
     * `4 * quadrantSegs`.
     *
     * @param ang the angle
     * @return the index for the angle.
     */
    private fun capAngleIndex(ang: Double): Int {
        val capSegAng: Double = PI / 2 / quadrantSegs
        return (ang / capSegAng).toInt()
    }

    /**
     * Creates a generator for a variable-distance line buffer.
     *
     * @param line the linestring to buffer
     * @param distance the buffer distance for each vertex of the line
     */
    init {
        this.line = line as LineString
        this.distance = distance
        geomFactory = line.factory
        if (distance.size != this.line.numPoints) {
            throw IllegalArgumentException("Number of distances is not equal to number of vertices")
        }
    }

    companion object {
        private const val MIN_CAP_SEG_LEN_FACTOR = 4

        /**
         * Creates a buffer polygon along a line with the buffer distance interpolated
         * between a start distance and an end distance.
         *
         * @param line the line to buffer
         * @param startDistance the buffer width at the start of the line
         * @param endDistance the buffer width at the end of the line
         * @return the variable-distance buffer polygon
         */
        @JvmStatic
        fun buffer(
            line: Geometry, startDistance: Double,
            endDistance: Double
        ): Geometry {
            val distance = interpolate(
                line as LineString,
                startDistance, endDistance
            )
            val vb = VariableBuffer(line, distance)
            return vb.result
        }

        /**
         * Creates a buffer polygon along a line with the buffer distance interpolated
         * between a start distance, a middle distance and an end distance.
         * The middle distance is attained at
         * the vertex at or just past the half-length of the line.
         * For smooth buffering of a [LinearRing] (or the rings of a [Polygon])
         * the start distance and end distance should be equal.
         *
         * @param line the line to buffer
         * @param startDistance the buffer width at the start of the line
         * @param midDistance the buffer width at the middle vertex of the line
         * @param endDistance the buffer width at the end of the line
         * @return the variable-distance buffer polygon
         */
        @JvmStatic
        fun buffer(
            line: Geometry, startDistance: Double,
            midDistance: Double,
            endDistance: Double
        ): Geometry {
            val distance = interpolate(
                line as LineString,
                startDistance, midDistance, endDistance
            )
            val vb = VariableBuffer(line, distance)
            return vb.result
        }

        /**
         * Creates a buffer polygon along a line with the distance specified
         * at each vertex.
         *
         * @param line the line to buffer
         * @param distance the buffer distance for each vertex of the line
         * @return the variable-distance buffer polygon
         */
        @JvmStatic
        fun buffer(line: Geometry, distance: DoubleArray): Geometry {
            val vb = VariableBuffer(line, distance)
            return vb.result
        }

        /**
         * Computes a list of values for the points along a line by
         * interpolating between values for the start and end point.
         * The interpolation is
         * based on the distance of each point along the line
         * relative to the total line length.
         *
         * @param line the line to interpolate along
         * @param startValue the start value
         * @param endValue the end value
         * @return the array of interpolated values
         */
        private fun interpolate(
            line: LineString,
            startValue: Double,
            endValue: Double
        ): DoubleArray {
            var startValue = startValue
            var endValue = endValue
            startValue = abs(startValue)
            endValue = abs(endValue)
            val values = DoubleArray(line.numPoints)
            values[0] = startValue
            values[values.size - 1] = endValue
            val totalLen = line.length
            val pts = line.coordinates
            var currLen = 0.0
            for (i in 1 until values.size - 1) {
                val segLen = pts[i].distance(pts[i - 1])
                currLen += segLen
                val lenFrac = currLen / totalLen
                val delta = lenFrac * (endValue - startValue)
                values[i] = startValue + delta
            }
            return values
        }

        /**
         * Computes a list of values for the points along a line by
         * interpolating between values for the start, middle and end points.
         * The interpolation is
         * based on the distance of each point along the line
         * relative to the total line length.
         * The middle distance is attained at
         * the vertex at or just past the half-length of the line.
         *
         * @param line the line to interpolate along
         * @param startValue the start value
         * @param midValue the start value
         * @param endValue the end value
         * @return the array of interpolated values
         */
        private fun interpolate(
            line: LineString,
            startValue: Double,
            midValue: Double,
            endValue: Double
        ): DoubleArray {
            var startValue = startValue
            var midValue = midValue
            var endValue = endValue
            startValue = abs(startValue)
            midValue = abs(midValue)
            endValue = abs(endValue)
            val values = DoubleArray(line.numPoints)
            values[0] = startValue
            values[values.size - 1] = endValue
            val pts = line.coordinates
            val lineLen = line.length
            val midIndex = indexAtLength(pts, lineLen / 2)
            val delMidStart = midValue - startValue
            val delEndMid = endValue - midValue
            val lenSM = length(pts, 0, midIndex)
            var currLen = 0.0
            for (i in 1..midIndex) {
                val segLen = pts[i].distance(pts[i - 1])
                currLen += segLen
                val lenFrac = currLen / lenSM
                val `val` = startValue + lenFrac * delMidStart
                values[i] = `val`
            }
            val lenME = length(pts, midIndex, pts.size - 1)
            currLen = 0.0
            for (i in midIndex + 1 until values.size - 1) {
                val segLen = pts[i].distance(pts[i - 1])
                currLen += segLen
                val lenFrac = currLen / lenME
                val `val` = midValue + lenFrac * delEndMid
                values[i] = `val`
            }
            return values
        }

        private fun indexAtLength(pts: Array<Coordinate>, targetLen: Double): Int {
            var len = 0.0
            for (i in 1 until pts.size) {
                len += pts[i].distance(pts[i - 1])
                if (len > targetLen) return i
            }
            return pts.size - 1
        }

        private fun length(pts: Array<Coordinate>, i1: Int, i2: Int): Double {
            var len = 0.0
            for (i in i1 + 1..i2) {
                len += pts[i].distance(pts[i - 1])
            }
            return len
        }

        /**
         * Computes the two circumference points defining the outer tangent line
         * between two circles.
     * The tangent line may be null if one circle mostly overlaps the other.
         *
         *
         * For the algorithm see [Wikipedia](https://en.wikipedia.org/wiki/Tangent_lines_to_circles#Outer_tangent).
         *
         * @param c1 the centre of circle 1
         * @param r1 the radius of circle 1
         * @param c2 the centre of circle 2
         * @param r2 the center of circle 2
         * @return the outer tangent line segment, or null if none exists
         */
        private fun outerTangent(c1: Coordinate, r1: Double, c2: Coordinate, r2: Double): LineSegment? {
            /**
             * If distances are inverted then flip to compute and flip result back.
             */
            if (r1 > r2) {
                val seg = outerTangent(c2, r2, c1, r1)
                return LineSegment(seg!!.p1, seg.p0)
            }
            val x1 = c1.x
            val y1 = c1.y
            val x2 = c2.x
            val y2 = c2.y
            // TODO: handle r1 == r2?
            val a3: Double = -atan2(y2 - y1, x2 - x1)
            val dr = r2 - r1
            val d: Double = sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1))
            val a2: Double = asin(dr / d)
            // check if no tangent exists
            if (isNaN(a2)) return null
            val a1 = a3 - a2
            val aa: Double = PI / 2 - a1
            val x3: Double = x1 + r1 * cos(aa)
            val y3: Double = y1 + r1 * sin(aa)
            val x4: Double = x2 + r2 * cos(aa)
            val y4: Double = y2 + r2 * sin(aa)
            return LineSegment(x3, y3, x4, y4)
        }

        private fun projectPolar(p: Coordinate, r: Double, ang: Double): Coordinate {
            val x = p.x + r * snapTrig(cos(ang))
            val y = p.y + r * snapTrig(sin(ang))
            return Coordinate(x, y)
        }

        private const val SNAP_TRIG_TOL = 1e-6

        /**
         * Snap trig values to integer values for better consistency.
         *
         * @param x the result of a trigonometric function
         * @return x snapped to the integer interval
         */
        private fun snapTrig(x: Double): Double {
            if (x > 1 - SNAP_TRIG_TOL) return 1.0
            if (x < -1 + SNAP_TRIG_TOL) return (-1).toDouble()
            return if (abs(x) < SNAP_TRIG_TOL) 0.0 else x
        }
    }
}