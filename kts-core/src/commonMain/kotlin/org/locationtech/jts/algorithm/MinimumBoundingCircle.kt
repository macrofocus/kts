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
package org.locationtech.jts.algorithm

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.CoordinateArrays
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.Triangle
import org.locationtech.jts.legacy.Math.sqrt
import org.locationtech.jts.util.Assert

/**
 * Computes the **Minimum Bounding Circle** (MBC)
 * for the points in a [Geometry].
 * The MBC is the smallest circle which <tt>cover</tt>s
 * all the input points
 * (this is also known as the **Smallest Enclosing Circle**).
 * This is equivalent to computing the Maximum Diameter
 * of the input point set.
 *
 * The computed circle can be specified in two equivalent ways,
 * both of which are provide as output by this class:
 *
 *  * As a centre point and a radius
 *  * By the set of points defining the circle.
 * Depending on the number of points in the input
 * and their relative positions, this set
 * contains from 0 to 3 points.
 *
 *  * 0 or 1 points indicate an empty or trivial input point arrangement.
 *  * 2 points define the diameter of the minimum bounding circle.
 *  * 3 points define an inscribed triangle of the minimum bounding circle.
 *
 * The class can also output a [Geometry] which approximates the
 * shape of the Minimum Bounding Circle (although as an approximation
 * it is **not** guaranteed to <tt>cover</tt> all the input points.)
 *
 * The Maximum Diameter of the input point set can
 * be computed as well.  The Maximum Diameter is
 * defined by the pair of input points with maximum distance between them.
 * The points of the maximum diameter are two of the extremal points of the Minimum Bounding Circle.
 * They lie on the convex hull of the input.
 * However, that the maximum diameter is not a diameter
 * of the Minimum Bounding Circle in the case where the MBC is
 * defined by an inscribed triangle.
 *
 * @author Martin Davis
 *
 * @see MinimumDiameter
 */
class MinimumBoundingCircle
/**
 * Creates a new object for computing the minimum bounding circle for the
 * point set defined by the vertices of the given geometry.
 *
 * @param geom the geometry to use to obtain the point set
 *///TODO: ensure the output circle contains the extermal points.
    ( /*
   * The algorithm used is based on the one by Jon Rokne in 
   * the article "An Easy Bounding Circle" in <i>Graphic Gems II</i>.
   */
      private val input: Geometry
) {
    private var extremalPts: Array<Coordinate>? = null
    private var centre: Coordinate? = null
    private var radius = 0.0
    //TODO: or maybe even ensure that the returned geometry contains ALL the input points?
    /**
     * Gets a geometry which represents the Minimum Bounding Circle.
     * If the input is degenerate (empty or a single unique point),
     * this method will return an empty geometry or a single Point geometry.
     * Otherwise, a Polygon will be returned which approximates the
     * Minimum Bounding Circle.
     * (Note that because the computed polygon is only an approximation,
     * it may not precisely contain all the input points.)
     *
     * @return a Geometry representing the Minimum Bounding Circle.
     */
    val circle: Geometry
        get() {
            //TODO: ensure the output circle contains the extermal points.
            //TODO: or maybe even ensure that the returned geometry contains ALL the input points?
            compute()
            if (centre == null) return input.factory.createPolygon()
            val centrePoint = input.factory.createPoint(centre)
            return if (radius == 0.0) centrePoint else centrePoint.buffer(radius)
        }

    /**
     * Gets a geometry representing the maximum diameter of the
     * input. The maximum diameter is the longest line segment
     * between any two points of the input.
     *
     * The points are two of the extremal points of the Minimum Bounding Circle.
     * They lie on the convex hull of the input.
     *
     * @return a LineString between the two farthest points of the input
     * @return a empty LineString if the input is empty
     * @return a Point if the input is a point
     */
    val maximumDiameter: Geometry
        get() {
            compute()
            return when (extremalPts!!.size) {
                0 -> input.factory.createLineString()
                1 -> input.factory.createPoint(centre)
                2 -> input.factory.createLineString(
                    arrayOf(
                        extremalPts!![0],
                        extremalPts!![1]
                    )
                )

                else -> {
                    val maxDiameter = farthestPoints(extremalPts!!)
                    input.factory.createLineString(maxDiameter)
                }
            }
        }

    /**
     * Gets a geometry representing a line between the two farthest points
     * in the input.
     *
     * The points are two of the extremal points of the Minimum Bounding Circle.
     * They lie on the convex hull of the input.
     *
     * @return a LineString between the two farthest points of the input
     * @return a empty LineString if the input is empty
     * @return a Point if the input is a point
     *
     */
    @get:Deprecated("use #getMaximumDiameter()")
    val farthestPoints: Geometry
        get() = maximumDiameter// TODO: handle case of 3 extremal points, by computing a line from one of
    // them through the centre point with len = 2*radius
    /**
     * Gets a geometry representing the diameter of the computed Minimum Bounding
     * Circle.
     *
     * @return the diameter LineString of the Minimum Bounding Circle
     * @return a empty LineString if the input is empty
     * @return a Point if the input is a point
     */
    val diameter: Geometry
        get() {
            compute()
            when (extremalPts!!.size) {
                0 -> return input.factory.createLineString()
                1 -> return input.factory.createPoint(centre)
            }
            // TODO: handle case of 3 extremal points, by computing a line from one of
            // them through the centre point with len = 2*radius
            val p0 = extremalPts!![0]
            val p1 = extremalPts!![1]
            return input.factory.createLineString(arrayOf(p0, p1))
        }

    /**
     * Gets the extremal points which define the computed Minimum Bounding Circle.
     * There may be zero, one, two or three of these points, depending on the number
     * of points in the input and the geometry of those points.
     *
     *  * 0 or 1 points indicate an empty or trivial input point arrangement.
     *  * 2 points define the diameter of the Minimum Bounding Circle.
     *  * 3 points define an inscribed triangle of which the Minimum Bounding Circle is the circumcircle.
     * The longest chords of the circle are the line segments [0-1] and [1-2]
     *
     * @return the points defining the Minimum Bounding Circle
     */
    val extremalPoints: Array<Coordinate>?
        get() {
            compute()
            return extremalPts
        }

    /**
     * Gets the centre point of the computed Minimum Bounding Circle.
     *
     * @return the centre point of the Minimum Bounding Circle
     * @return null if the input is empty
     */
    fun getCentre(): Coordinate? {
        compute()
        return centre
    }

    /**
     * Gets the radius of the computed Minimum Bounding Circle.
     *
     * @return the radius of the Minimum Bounding Circle
     */
    fun getRadius(): Double {
        compute()
        return radius
    }

    private fun computeCentre() {
        when (extremalPts!!.size) {
            0 -> centre = null
            1 -> centre = extremalPts!![0]
            2 -> centre = Coordinate(
                (extremalPts!![0].x + extremalPts!![1].x) / 2.0,
                (extremalPts!![0].y + extremalPts!![1].y) / 2.0
            )

            3 -> centre = Triangle.circumcentre(extremalPts!![0], extremalPts!![1], extremalPts!![2])
        }
    }

    private fun compute() {
        if (extremalPts != null) return
        computeCirclePoints()
        computeCentre()
        if (centre != null) radius = centre!!.distance(extremalPts!![0])
    }

    private fun computeCirclePoints() {
        // handle degenerate or trivial cases
        if (input.isEmpty) {
            extremalPts = emptyArray()
            return
        }
        if (input.numPoints == 1) {
            val pts = input.coordinates
            extremalPts = arrayOf(Coordinate(pts!![0]))
            return
        }
        /**
         * The problem is simplified by reducing to the convex hull.
         * Computing the convex hull also has the useful effect of eliminating duplicate points
         */
        val convexHull = input.convexHull()
        val hullPts = convexHull!!.coordinates

        // strip duplicate final point, if any
        var pts = hullPts
        if (hullPts!![0].equals2D(hullPts[hullPts.size - 1])) {
            val p = arrayOfNulls<Coordinate>(hullPts.size - 1)
            CoordinateArrays.copyDeep(hullPts, 0, p, 0, hullPts.size - 1)
            pts = p.requireNoNulls()
        }
        /**
         * Optimization for the trivial case where the CH has fewer than 3 points
         */
        if (pts!!.size <= 2) {
            extremalPts = CoordinateArrays.copyDeep(pts)
            return
        }

        // find a point P with minimum Y ordinate
        var P = lowestPoint(pts)

        // find a point Q such that the angle that PQ makes with the x-axis is minimal
        var Q = pointWitMinAngleWithX(pts, P)
        /**
         * Iterate over the remaining points to find
         * a pair or triplet of points which determine the minimal circle.
         * By the design of the algorithm,
         * at most <tt>pts.length</tt> iterations are required to terminate
         * with a correct result.
         */
        for (i in pts.indices) {
            val R = pointWithMinAngleWithSegment(pts, P, Q)
            if (Angle.isObtuse(P, R!!, Q!!)) {
                // if PRQ is obtuse, then MBC is determined by P and Q
                extremalPts = arrayOf(
                    Coordinate(P), Coordinate(
                        Q
                    )
                )
                return
            } else if (Angle.isObtuse(R, P, Q)) {
                // if RPQ is obtuse, update baseline and iterate
                P = R
                continue
            } else if (Angle.isObtuse(R, Q, P)) {
                // if RQP is obtuse, update baseline and iterate
                Q = R
                continue
            } else {
                // otherwise all angles are acute, and the MBC is determined by the triangle PQR
                extremalPts = arrayOf(
                    Coordinate(P), Coordinate(
                        Q
                    ), Coordinate(R)
                )
                return
            }
        }
        Assert.shouldNeverReachHere("Logic failure in Minimum Bounding Circle algorithm!")
    }

    companion object {
        /**
         * Finds the farthest pair out of 3 extremal points
         * @param pts the array of extremal points
         * @return the pair of farthest points
         */
        private fun farthestPoints(pts: Array<Coordinate>): Array<Coordinate> {
            val dist01 = pts[0].distance(pts[1])
            val dist12 = pts[1].distance(pts[2])
            val dist20 = pts[2].distance(pts[0])
            if (dist01 >= dist12 && dist01 >= dist20) {
                return arrayOf(pts[0], pts[1])
            }
            return if (dist12 >= dist01 && dist12 >= dist20) {
                arrayOf(pts[1], pts[2])
            } else arrayOf(pts[2], pts[0])
        }

        private fun lowestPoint(pts: Array<Coordinate>): Coordinate {
            var min = pts[0]
            for (i in 1 until pts.size) {
                if (pts[i].y < min.y) min = pts[i]
            }
            return min
        }

        private fun pointWitMinAngleWithX(pts: Array<Coordinate>, P: Coordinate): Coordinate? {
            var minSin = Double.MAX_VALUE
            var minAngPt: Coordinate? = null
            for (i in pts.indices) {
                val p = pts[i]
                if (p === P) continue
                /**
                 * The sin of the angle is a simpler proxy for the angle itself
                 */
                val dx = p.x - P.x
                var dy = p.y - P.y
                if (dy < 0) dy = -dy
                val len: Double = sqrt(dx * dx + dy * dy)
                val sin = dy / len
                if (sin < minSin) {
                    minSin = sin
                    minAngPt = p
                }
            }
            return minAngPt
        }

        private fun pointWithMinAngleWithSegment(
            pts: Array<Coordinate>?,
            P: Coordinate?,
            Q: Coordinate?
        ): Coordinate? {
            var minAng = Double.MAX_VALUE
            var minAngPt: Coordinate? = null
            for (i in pts!!.indices) {
                val p = pts[i]
                if (p === P) continue
                if (p === Q) continue
                val ang = Angle.angleBetween(P!!, p, Q!!)
                if (ang < minAng) {
                    minAng = ang
                    minAngPt = p
                }
            }
            return minAngPt
        }
    }
}