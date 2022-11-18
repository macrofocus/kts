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
package org.locationtech.jts.triangulate.quadedge

import org.locationtech.jts.algorithm.HCoordinate
import org.locationtech.jts.algorithm.NotRepresentableException
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.legacy.Math.pow
import org.locationtech.jts.legacy.Math.sqrt

/**
 * Models a site (node) in a [QuadEdgeSubdivision].
 * The sites can be points on a line string representing a
 * linear site.
 *
 * The vertex can be considered as a vector with a norm, length, inner product, cross
 * product, etc. Additionally, point relations (e.g., is a point to the left of a line, the circle
 * defined by this point and two others, etc.) are also defined in this class.
 *
 * It is common to want to attach user-defined data to
 * the vertices of a subdivision.
 * One way to do this is to subclass <tt>Vertex</tt>
 * to carry any desired information.
 *
 * @author David Skea
 * @author Martin Davis
 */
open class Vertex {
    var coordinate: Coordinate
        private set

    // private int edgeNumber = -1;
    constructor(_x: Double, _y: Double) {
        coordinate = Coordinate(_x, _y)
    }

    constructor(_x: Double, _y: Double, _z: Double) {
        coordinate = Coordinate(_x, _y, _z)
    }

    constructor(_p: Coordinate?) {
        coordinate = Coordinate(_p!!)
    }

    val x: Double
        get() = coordinate.x
    val y: Double
        get() = coordinate.y
    var z: Double
        get() = coordinate.z
        set(_z) {
            coordinate.z = _z
        }

    override fun toString(): String {
        return "POINT (" + coordinate.x + " " + coordinate.y + ")"
    }

    fun equals(_x: Vertex?): Boolean {
        return coordinate.x == _x!!.x && coordinate.y == _x.y
    }

    fun equals(_x: Vertex, tolerance: Double): Boolean {
        return coordinate.distance(_x.coordinate) < tolerance
    }

    fun classify(p0: Vertex, p1: Vertex): Int {
        val p2 = this
        val a = p1.sub(p0)
        val b = p2.sub(p0)
        val sa = a.crossProduct(b)
        if (sa > 0.0) return LEFT
        if (sa < 0.0) return RIGHT
        if (a.x * b.x < 0.0 || a.y * b.y < 0.0) return BEHIND
        if (a.magn() < b.magn()) return BEYOND
        if (p0.equals(p2)) return ORIGIN
        return if (p1.equals(p2)) DESTINATION else BETWEEN
    }

    /**
     * Computes the cross product k = u X v.
     *
     * @param v a vertex
     * @return returns the magnitude of u X v
     */
    fun crossProduct(v: Vertex): Double {
        return coordinate.x * v.y - coordinate.y * v.x
    }

    /**
     * Computes the inner or dot product
     *
     * @param v a vertex
     * @return returns the dot product u.v
     */
    fun dot(v: Vertex): Double {
        return coordinate.x * v.x + coordinate.y * v.y
    }

    /**
     * Computes the scalar product c(v)
     *
     * @param v a vertex
     * @return returns the scaled vector
     */
    operator fun times(c: Double): Vertex {
        return Vertex(c * coordinate.x, c * coordinate.y)
    }

    /* Vector addition */
    fun sum(v: Vertex): Vertex {
        return Vertex(coordinate.x + v.x, coordinate.y + v.y)
    }

    /* and subtraction */
    fun sub(v: Vertex): Vertex {
        return Vertex(coordinate.x - v.x, coordinate.y - v.y)
    }

    /* magnitude of vector */
    fun magn(): Double {
        return sqrt(coordinate.x * coordinate.x + coordinate.y * coordinate.y)
    }

    /* returns k X v (cross product). this is a vector perpendicular to v */
    fun cross(): Vertex {
        return Vertex(coordinate.y, -coordinate.x)
    }
    /** *************************************************************  */
    /***********************************************************************************************
     * Geometric primitives /
     */
    /**
     * Tests if the vertex is inside the circle defined by
     * the triangle with vertices a, b, c (oriented counter-clockwise).
     *
     * @param a a vertex of the triangle
     * @param b a vertex of the triangle
     * @param c a vertex of the triangle
     * @return true if this vertex is in the circumcircle of (a,b,c)
     */
    fun isInCircle(a: Vertex, b: Vertex, c: Vertex): Boolean {
        return TrianglePredicate.isInCircleRobust(
            a.coordinate,
            b.coordinate,
            c.coordinate,
            coordinate
        )
        // non-robust - best to not use
        //return TrianglePredicate.isInCircle(a.p, b.p, c.p, this.p);
    }

    /**
     * Tests whether the triangle formed by this vertex and two
     * other vertices is in CCW orientation.
     *
     * @param b a vertex
     * @param c a vertex
     * @return true if the triangle is oriented CCW
     */
    fun isCCW(b: Vertex, c: Vertex): Boolean {
        /*
      // test code used to check for robustness of triArea 
      boolean isCCW = (b.p.x - p.x) * (c.p.y - p.y) 
      - (b.p.y - p.y) * (c.p.x - p.x) > 0;
     //boolean isCCW = triArea(this, b, c) > 0;
     boolean isCCWRobust = CGAlgorithms.orientationIndex(p, b.p, c.p) == CGAlgorithms.COUNTERCLOCKWISE; 
     if (isCCWRobust != isCCW)
      System.out.println("CCW failure");
     // */

        // is equal to the signed area of the triangle
        return ((b.coordinate.x - coordinate.x) * (c.coordinate.y - coordinate.y)
        -(b.coordinate.y - coordinate.y) * (c.coordinate.x - coordinate.x)) > 0.0

        // original rolled code
        //boolean isCCW = triArea(this, b, c) > 0;
        //return isCCW;
    }

    fun rightOf(e: QuadEdge): Boolean {
        return isCCW(e.dest()!!, e.orig()!!)
    }

    fun leftOf(e: QuadEdge): Boolean {
        return isCCW(e.orig()!!, e.dest()!!)
    }

    private fun bisector(a: Vertex, b: Vertex): HCoordinate {
        // returns the perpendicular bisector of the line segment ab
        val dx = b.x - a.x
        val dy = b.y - a.y
        val l1 = HCoordinate(a.x + dx / 2.0, a.y + dy / 2.0, 1.0)
        val l2 = HCoordinate(a.x - dy + dx / 2.0, a.y + dx + dy / 2.0, 1.0)
        return HCoordinate(l1, l2)
    }

    private fun distance(v1: Vertex?, v2: Vertex): Double {
        return sqrt(
            pow(v2.x - v1!!.x, 2.0)
                    + pow(v2.y - v1.y, 2.0)
        )
    }

    /**
     * Computes the value of the ratio of the circumradius to shortest edge. If smaller than some
     * given tolerance B, the associated triangle is considered skinny. For an equal lateral
     * triangle this value is 0.57735. The ratio is related to the minimum triangle angle theta by:
     * circumRadius/shortestEdge = 1/(2sin(theta)).
     *
     * @param b second vertex of the triangle
     * @param c third vertex of the triangle
     * @return ratio of circumradius to shortest edge.
     */
    fun circumRadiusRatio(b: Vertex, c: Vertex): Double {
        val x = circleCenter(b, c)
        val radius = distance(x, b)
        var edgeLength = distance(this, b)
        var el = distance(b, c)
        if (el < edgeLength) {
            edgeLength = el
        }
        el = distance(c, this)
        if (el < edgeLength) {
            edgeLength = el
        }
        return radius / edgeLength
    }

    /**
     * returns a new vertex that is mid-way between this vertex and another end point.
     *
     * @param a the other end point.
     * @return the point mid-way between this and that.
     */
    fun midPoint(a: Vertex): Vertex {
        val xm = (coordinate.x + a.x) / 2.0
        val ym = (coordinate.y + a.y) / 2.0
        val zm = (coordinate.z + a.z) / 2.0
        return Vertex(xm, ym, zm)
    }

    /**
     * Computes the centre of the circumcircle of this vertex and two others.
     *
     * @param b
     * @param c
     * @return the Coordinate which is the circumcircle of the 3 points.
     */
    fun circleCenter(b: Vertex, c: Vertex): Vertex? {
        val a = Vertex(
            x, y
        )
        // compute the perpendicular bisector of cord ab
        val cab = bisector(a, b)
        // compute the perpendicular bisector of cord bc
        val cbc = bisector(b, c)
        // compute the intersection of the bisectors (circle radii)
        val hcc = HCoordinate(cab, cbc)
        var cc: Vertex? = null
        try {
            cc = Vertex(hcc.x, hcc.y)
        } catch (nre: NotRepresentableException) {
            //Debug.println("a: " + a + "  b: " + b + "  c: " + c);
            //Debug.println(nre);
        }
        return cc
    }

    /**
     * For this vertex enclosed in a triangle defined by three vertices v0, v1 and v2, interpolate
     * a z value from the surrounding vertices.
     */
    fun interpolateZValue(
        v0: Vertex,
        v1: Vertex,
        v2: Vertex
    ): Double {
        val x0 = v0.x
        val y0 = v0.y
        val a = v1.x - x0
        val b = v2.x - x0
        val c = v1.y - y0
        val d = v2.y - y0
        val det = a * d - b * c
        val dx = x - x0
        val dy = y - y0
        val t = (d * dx - b * dy) / det
        val u = (-c * dx + a * dy) / det
        return v0.z + t * (v1.z - v0.z) + u * (v2.z - v0.z)
    }

    companion object {
        const val LEFT = 0
        const val RIGHT = 1
        const val BEYOND = 2
        const val BEHIND = 3
        const val BETWEEN = 4
        const val ORIGIN = 5
        const val DESTINATION = 6

        /**
         * Interpolates the Z-value (height) of a point enclosed in a triangle
         * whose vertices all have Z values.
         * The containing triangle must not be degenerate
         * (in other words, the three vertices must enclose a
         * non-zero area).
         *
         * @param p the point to interpolate the Z value of
         * @param v0 a vertex of a triangle containing the p
         * @param v1 a vertex of a triangle containing the p
         * @param v2 a vertex of a triangle containing the p
         * @return the interpolated Z-value (height) of the point
         */
        fun interpolateZ(
            p: Coordinate,
            v0: Coordinate,
            v1: Coordinate,
            v2: Coordinate
        ): Double {
            val x0 = v0.x
            val y0 = v0.y
            val a = v1.x - x0
            val b = v2.x - x0
            val c = v1.y - y0
            val d = v2.y - y0
            val det = a * d - b * c
            val dx = p.x - x0
            val dy = p.y - y0
            val t = (d * dx - b * dy) / det
            val u = (-c * dx + a * dy) / det
            return v0.z + t * (v1.z - v0.z) + u * (v2.z - v0.z)
        }

        /**
         * Computes the interpolated Z-value for a point p lying on the segment p0-p1
         *
         * @param p
         * @param p0
         * @param p1
         * @return the interpolated Z value
         */
        fun interpolateZ(
            p: Coordinate,
            p0: Coordinate,
            p1: Coordinate
        ): Double {
            val segLen = p0.distance(p1)
            val ptLen = p.distance(p0)
            val dz = p1.z - p0.z
            return p0.z + dz * (ptLen / segLen)
        }
    }
}