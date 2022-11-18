/*
 * Copyright (c) 2021 Martin Davis.
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.shape

import org.locationtech.jts.algorithm.Angle
import org.locationtech.jts.algorithm.Angle.angle
import org.locationtech.jts.algorithm.Angle.angleBetweenOriented
import org.locationtech.jts.algorithm.Angle.bisector
import org.locationtech.jts.algorithm.Angle.project
import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.util.GeometryMapper
import org.locationtech.jts.geom.util.GeometryMapper.flatMap
import org.locationtech.jts.legacy.Math
import org.locationtech.jts.legacy.Math.abs
import org.locationtech.jts.legacy.Math.max
import org.locationtech.jts.legacy.Math.min

/**
 * Creates a curved geometry by replacing the segments
 * of the input with Cubic Bezier Curves.
 * The Bezier control points are determined from the segments of the geometry
 * and the alpha control parameter controlling curvedness, and
 * the optional skew parameter controlling the shape of the curve at vertices.
 * The Bezier Curves are created to be C2-continuous (smooth)
 * at each input vertex.
 *
 * Alternatively, the Bezier control points can be supplied explicitly.
 *
 * The result is not guaranteed to be valid, since large alpha values
 * may cause self-intersections.
 */
class CubicBezierCurve {
    private val minSegmentLength = 0.0
    private val numVerticesPerSegment = 16
    private var inputGeom: Geometry
    private var alpha = -1.0
    private var skew = 0.0
    private var controlPoints: Geometry? = null
    private val geomFactory: GeometryFactory
    private lateinit var bezierCurvePts: Array<Coordinate?>
    private lateinit var interpolationParam: Array<DoubleArray>
    private var controlPointIndex = 0

    /**
     * Creates a new instance producing a Bezier curve defined by a geometry
     * and an alpha curvedness value.
     *
     * @param geom geometry defining curve
     * @param alpha curvedness parameter (0 = linear, 1 = round, 2 = distorted)
     */
    internal constructor(geom: Geometry, alpha: Double) {
        var alpha = alpha
        inputGeom = geom
        geomFactory = geom.factory
        if (alpha < 0.0) alpha = 0.0
        this.alpha = alpha
    }

    /**
     * Creates a new instance producing a Bezier curve defined by a geometry,
     * an alpha curvedness value, and a skew factor.
     *
     * @param geom geometry defining curve
     * @param alpha curvedness parameter (0 is linear, 1 is round, >1 is increasingly curved)
     * @param skew the skew parameter (0 is none, positive skews towards longer side, negative towards shorter
     */
    internal constructor(geom: Geometry, alpha: Double, skew: Double) {
        var alpha = alpha
        inputGeom = geom
        geomFactory = geom.factory
        if (alpha < 0.0) alpha = 0.0
        this.alpha = alpha
        this.skew = skew
    }

    /**
     * Creates a new instance producing a Bezier curve defined by a geometry,
     * and a list (or lists) of control points.
     *
     *
     * Typically the control point geometry
     * is a [LineString] or [MultiLineString]
     * containing an element for each line or ring in the input geometry.
     * The list of control points for each linear element must contain two
     * vertices for each segment (and thus `2 * npts - 2`).
     *
     * @param geom geometry defining curve
     * @param controlPoints the geometry containing the control points
     */
    internal constructor(geom: Geometry, controlPoints: Geometry?) {
        inputGeom = geom
        geomFactory = geom.factory
        this.controlPoints = controlPoints
    }//-- Points

    /**
     * Gets the computed linearized Bezier curve geometry.
     *
     * @return a linearized curved geometry
     */
    val result: Geometry?
        get() {
            bezierCurvePts = arrayOfNulls(numVerticesPerSegment)
            interpolationParam = computeIterpolationParameters(numVerticesPerSegment)
            return flatMap(inputGeom, 1, object : GeometryMapper.MapOp {
                override fun map(geom: Geometry): Geometry? {
                    if (geom is LineString) {
                        return bezierLine(geom)
                    }
                    return if (geom is Polygon) {
                        bezierPolygon(geom)
                    } else geom!!.copy()
                    //-- Points
                }
            })
        }

    private fun bezierLine(ls: LineString): LineString {
        val coords = ls.coordinates
        val curvePts = bezierCurve(coords, false)
        curvePts.add(coords[coords.size - 1].copy(), false)
        return geomFactory.createLineString(curvePts.toCoordinateArray())
    }

    private fun bezierRing(ring: LinearRing?): LinearRing {
        val coords: Array<Coordinate> = ring!!.coordinates
        val curvePts = bezierCurve(coords, true)
        curvePts.closeRing()
        return geomFactory.createLinearRing(curvePts.toCoordinateArray())
    }

    private fun bezierPolygon(poly: Polygon): Polygon {
        val shell = bezierRing(poly.exteriorRing)
        var holes: Array<LinearRing?>? = null
        if (poly.getNumInteriorRing() > 0) {
            holes = arrayOfNulls(poly.getNumInteriorRing())
            for (i in 0 until poly.getNumInteriorRing()) {
                holes[i] = bezierRing(poly.getInteriorRingN(i))
            }
        }
        return geomFactory.createPolygon(shell, holes!!.requireNoNulls())
    }

    private fun bezierCurve(coords: Array<Coordinate>, isRing: Boolean): CoordinateList {
        val control = controlPoints(coords, isRing)
        val curvePts = CoordinateList()
        for (i in 0 until coords.size - 1) {
            val ctrlIndex = 2 * i
            addCurve(coords[i], coords[i + 1], control!![ctrlIndex], control[ctrlIndex + 1], curvePts)
        }
        return curvePts
    }

    private fun controlPoints(coords: Array<Coordinate>, isRing: Boolean): Array<Coordinate>? {
        if (controlPoints != null) {
            if (controlPointIndex >= controlPoints!!.numGeometries) {
                throw IllegalArgumentException("Too few control point elements")
            }
            val ctrlPtsGeom = controlPoints!!.getGeometryN(controlPointIndex++)
            val ctrlPts = ctrlPtsGeom.coordinates
            val expectedNum1 = 2 * coords.size - 2
            val expectedNum2 = if (isRing) coords.size - 1 else coords.size
            if (expectedNum1 != ctrlPts!!.size && expectedNum2 != ctrlPts.size) {
                throw IllegalArgumentException(
                        "Wrong number of control points for element ${controlPointIndex - 1} - expected ${expectedNum1} or ${expectedNum2}, found ${ctrlPts.size}",
                )
            }
            return ctrlPts
        }
        return controlPoints(coords, isRing, alpha, skew)
    }

    private fun addCurve(
        p0: Coordinate, p1: Coordinate,
        ctrl0: Coordinate?, crtl1: Coordinate?,
        curvePts: CoordinateList
    ) {
        val len = p0.distance(p1)
        if (len < minSegmentLength) {
            // segment too short - copy input coordinate
            curvePts.add(Coordinate(p0))
        } else {
            cubicBezier(
                p0, p1, ctrl0, crtl1,
                interpolationParam, bezierCurvePts
            )
            for (i in 0 until bezierCurvePts.size - 1) {
                curvePts.add(bezierCurvePts[i], false)
            }
        }
    }

    /**
     * Creates control points for each vertex of curve.
     * The control points are collinear with each vertex,
     * thus providing C1-continuity.
     * By default the control vectors are the same length,
     * which provides C2-continuity (same curvature on each
     * side of vertex.
     * The alpha parameter controls the length of the control vectors.
     * Alpha = 0 makes the vectors zero-length, and hence flattens the curves.
     * Alpha = 1 makes the curve at right angles roughly circular.
     * Alpha > 1 starts to distort the curve and may introduce self-intersections.
     *
     *
     * The control point array contains a pair of coordinates for each input segment.
     *
     * @param coords
     * @param isRing
     * @param alpha determines the curviness
     * @return the control point array
     */
    private fun controlPoints(
        coords: Array<Coordinate>,
        isRing: Boolean,
        alpha: Double,
        skew: Double
    ): Array<Coordinate> {
        var N = coords.size
        var start = 1
        var end = N - 1
        if (isRing) {
            N = coords.size - 1
            start = 0
            end = N
        }
        val nControl = 2 * coords.size - 2
        val ctrl = arrayOfNulls<Coordinate>(nControl)
        for (i in start until end) {
            val iprev = if (i == 0) N - 1 else i - 1
            val v0 = coords[iprev]
            val v1 = coords[i]
            val v2 = coords[i + 1]
            val interiorAng = angleBetweenOriented(v0, v1, v2)
            val orient: Double = Math.signum(interiorAng)
            val angBisect = bisector(v0, v1, v2)
            val ang0 = angBisect - orient * Angle.PI_OVER_2
            val ang1 = angBisect + orient * Angle.PI_OVER_2
            val dist0 = v1.distance(v0)
            val dist1 = v1.distance(v2)
            val lenBase: Double = min(dist0, dist1)
            val intAngAbs: Double = abs(interiorAng)

            //-- make acute corners sharper by shortening tangent vectors
            val sharpnessFactor: Double = if (intAngAbs >= Angle.PI_OVER_2) 1.0 else intAngAbs / Angle.PI_OVER_2
            val len = alpha * CIRCLE_LEN_FACTOR * sharpnessFactor * lenBase
            var stretch0 = 1.0
            var stretch1 = 1.0
            if (skew != 0.0) {
                val stretch: Double = abs(dist0 - dist1) / max(dist0, dist1)
                var skewIndex = if (dist0 > dist1) 0 else 1
                if (skew < 0) skewIndex = 1 - skewIndex
                if (skewIndex == 0) {
                    stretch0 += abs(skew) * stretch
                } else {
                    stretch1 += abs(skew) * stretch
                }
            }
            val ctl0 = project(v1, ang0, stretch0 * len)
            val ctl1 = project(v1, ang1, stretch1 * len)
            val index = 2 * i - 1
            // for a ring case the first control point is for last segment
            val i0 = if (index < 0) nControl - 1 else index
            ctrl[i0] = ctl0
            ctrl[index + 1] = ctl1

            //System.out.println(WKTWriter.toLineString(v1, ctl0));
            //System.out.println(WKTWriter.toLineString(v1, ctl1));
        }
        if (!isRing) {
            setLineEndControlPoints(coords, ctrl)
        }
        return ctrl.requireNoNulls()
    }

    /**
     * Sets the end control points for a line.
     * Produce a symmetric curve for the first and last segments
     * by using mirrored control points for start and end vertex.
     *
     * @param coords
     * @param ctrl
     */
    private fun setLineEndControlPoints(coords: Array<Coordinate>, ctrl: Array<Coordinate?>) {
        val N = ctrl.size
        ctrl[0] = mirrorControlPoint(ctrl[1], coords[1], coords[0])
        ctrl[N - 1] = mirrorControlPoint(
            ctrl[N - 2],
            coords[coords.size - 1], coords[coords.size - 2]
        )
    }

    /**
     * Calculates vertices along a cubic Bezier curve.
     *
     * @param p0 start point
     * @param p1   end point
     * @param ctrl1 first control point
     * @param ctrl2 second control point
     * @param param interpolation parameters
     * @param curve array to hold generated points
     */
    private fun cubicBezier(
        p0: Coordinate,
        p1: Coordinate, ctrl1: Coordinate?,
        ctrl2: Coordinate?, param: Array<DoubleArray>,
        curve: Array<Coordinate?>
    ) {
        val n = curve.size
        curve[0] = Coordinate(p0)
        curve[n - 1] = Coordinate(p1)
        for (i in 1 until n - 1) {
            val c = Coordinate()
            val sum = param[i][0] + param[i][1] + param[i][2] + param[i][3]
            c.x = param[i][0] * p0.x + param[i][1] * ctrl1!!.x + param[i][2] * ctrl2!!.x + param[i][3] * p1.x
            c.x /= sum
            c.y = param[i][0] * p0.y + param[i][1] * ctrl1.y + param[i][2] * ctrl2.y + param[i][3] * p1.y
            c.y /= sum
            curve[i] = c
        }
    }

    companion object {
        /**
         * Creates a geometry of linearized Cubic Bezier Curves
         * defined by the segments of the input and a parameter
         * controlling how curved the result should be.
         *
         * @param geom the geometry defining the curve
         * @param alpha curvedness parameter (0 is linear, 1 is round, >1 is increasingly curved)
         * @return the linearized curved geometry
         */
        fun bezierCurve(geom: Geometry, alpha: Double): Geometry? {
            val curve = CubicBezierCurve(geom, alpha)
            return curve.result
        }

        /**
         * Creates a geometry of linearized Cubic Bezier Curves
         * defined by the segments of the input and a parameter
         * controlling how curved the result should be, with a skew factor
         * affecting the curve shape at each vertex.
         *
         * @param geom the geometry defining the curve
         * @param alpha curvedness parameter (0 is linear, 1 is round, >1 is increasingly curved)
         * @param skew the skew parameter (0 is none, positive skews towards longer side, negative towards shorter
         * @return the linearized curved geometry
         */
        fun bezierCurve(geom: Geometry, alpha: Double, skew: Double): Geometry? {
            val curve = CubicBezierCurve(geom, alpha, skew)
            return curve.result
        }

        /**
         * Creates a geometry of linearized Cubic Bezier Curves
         * defined by the segments of the input
         * and a list (or lists) of control points.
         *
         *
         * Typically the control point geometry
         * is a [LineString] or [MultiLineString]
         * containing an element for each line or ring in the input geometry.
         * The list of control points for each linear element must contain two
         * vertices for each segment (and thus `2 * npts - 2`).
         *
         * @param geom the geometry defining the curve
         * @param controlPoints a geometry containing the control point elements.
         * @return the linearized curved geometry
         */
        fun bezierCurve(geom: Geometry, controlPoints: Geometry?): Geometry? {
            val curve = CubicBezierCurve(geom, controlPoints)
            return curve.result
        }

        //-- chosen to make curve at right-angle corners roughly circular
        private const val CIRCLE_LEN_FACTOR = 3.0 / 8.0

        /**
         * Creates a control point aimed at the control point at the opposite end of the segment.
         *
         * Produces overly flat results, so not used currently.
         *
         * @param c
         * @param p1
         * @param p0
         * @return
         */
        private fun aimedControlPoint(c: Coordinate, p1: Coordinate, p0: Coordinate): Coordinate {
            val len = p1.distance(c)
            val ang = angle(p0, p1)
            return project(p0, ang, len)
        }

        private fun mirrorControlPoint(c: Coordinate?, p0: Coordinate, p1: Coordinate): Coordinate {
            val vlinex = p1.x - p0.x
            val vliney = p1.y - p0.y
            // rotate line vector by 90
            val vrotx = -vliney
            val midx = (p0.x + p1.x) / 2
            val midy = (p0.y + p1.y) / 2
            return reflectPointInLine(
                c, Coordinate(midx, midy), Coordinate(
                    midx + vrotx,
                    midy + vlinex
                )
            )
        }

        private fun reflectPointInLine(p: Coordinate?, p0: Coordinate, p1: Coordinate): Coordinate {
            val vx = p1.x - p0.x
            val vy = p1.y - p0.y
            val x = p0.x - p!!.x
            val y = p0.y - p.y
            val r = 1 / (vx * vx + vy * vy)
            val rx = p.x + 2 * (x - x * vx * vx * r - y * vx * vy * r)
            val ry = p.y + 2 * (y - y * vy * vy * r - x * vx * vy * r)
            return Coordinate(rx, ry)
        }

        /**
         * Gets the interpolation parameters for a Bezier curve approximated by a
         * given number of vertices.
         *
         * @param n number of vertices
         * @return array of double[4] holding the parameter values
         */
        private fun computeIterpolationParameters(n: Int): Array<DoubleArray> {
            val param = Array(n) { DoubleArray(4) }
            for (i in 0 until n) {
                val t = i.toDouble() / (n - 1)
                val tc = 1.0 - t
                param[i][0] = tc * tc * tc
                param[i][1] = 3.0 * tc * tc * t
                param[i][2] = 3.0 * tc * t * t
                param[i][3] = t * t * t
            }
            return param
        }
    }
}