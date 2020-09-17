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
package org.locationtech.jts.algorithm

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.PrecisionModel
import org.locationtech.jts.io.WKTWriter
import org.locationtech.jts.legacy.Math
import org.locationtech.jts.util.Assert.isTrue
import kotlin.jvm.JvmField
import kotlin.math.max

/**
 * @version 1.7
 */
/**
 * A `LineIntersector` is an algorithm that can both test whether
 * two line segments intersect and compute the intersection point(s)
 * if they do.
 *
 * There are three possible outcomes when determining whether two line segments intersect:
 *
 *  * [.NO_INTERSECTION] - the segments do not intersect
 *  * [.POINT_INTERSECTION] - the segments intersect in a single point
 *  * [.COLLINEAR_INTERSECTION] - the segments are collinear and they intersect in a line segment
 *
 * For segments which intersect in a single point, the point may be either an endpoint
 * or in the interior of each segment.
 * If the point lies in the interior of both segments,
 * this is termed a *proper intersection*.
 * The method [.isProper] test for this situation.
 *
 * The intersection point(s) may be computed in a precise or non-precise manner.
 * Computing an intersection point precisely involves rounding it
 * via a supplied [PrecisionModel].
 *
 * LineIntersectors do not perform an initial envelope intersection test
 * to determine if the segments are disjoint.
 * This is because this class is likely to be used in a context where
 * envelope overlap is already known to occur (or be likely).
 *
 * @version 1.7
 */
abstract class LineIntersector {
    /**
     * Returns the number of intersection points found.  This will be either 0, 1 or 2.
     *
     * @return the number of intersection points found (0, 1, or 2)
     */
    var intersectionNum: Int
        protected set

    @JvmField
    protected var inputLines = Array(2) { arrayOfNulls<Coordinate>(2) }

    @JvmField
    protected var intPt = arrayOfNulls<Coordinate>(2)

    /**
     * The indexes of the endpoints of the intersection lines, in order along
     * the corresponding line
     */
    protected var intLineIndex: Array<IntArray>? = null

    /**
     * Tests whether an intersection is proper.
     * <br></br>
     * The intersection between two line segments is considered proper if
     * they intersect in a single point in the interior of both segments
     * (e.g. the intersection is a single point and is not equal to any of the
     * endpoints).
     *
     * The intersection between a point and a line segment is considered proper
     * if the point lies in the interior of the segment (e.g. is not equal to
     * either of the endpoints).
     *
     * @return true if the intersection is proper
     */
    var isProper = false
        get() = hasIntersection() && field
    protected var pa: Coordinate?
    protected var pb: Coordinate?

    /**
     * If makePrecise is true, computed intersection coordinates will be made precise
     * using Coordinate#makePrecise
     */
//    @JvmField
    var precisionModel: PrecisionModel? = null
        /**
         * Force computed intersection to be rounded to a given precision model.
         * No getter is provided, because the precision model is not required to be specified.
         * @param precisionModel
         */
        set(value) {
            field = value
        }

    /**
     * Force computed intersection to be rounded to a given precision model
     * @param precisionModel
     */
    @Deprecated("use <code>setPrecisionModel</code> instead")
    fun setMakePrecise(precisionModel: PrecisionModel?) {
        this.precisionModel = precisionModel
    }

    /**
     * Gets an endpoint of an input segment.
     *
     * @param segmentIndex the index of the input segment (0 or 1)
     * @param ptIndex the index of the endpoint (0 or 1)
     * @return the specified endpoint
     */
    fun getEndpoint(segmentIndex: Int, ptIndex: Int): Coordinate? {
        return inputLines[segmentIndex][ptIndex]
    }

    /**
     * Compute the intersection of a point p and the line p1-p2.
     * This function computes the boolean value of the hasIntersection test.
     * The actual value of the intersection (if there is one)
     * is equal to the value of `p`.
     */
    abstract fun computeIntersection(
        p: Coordinate?,
        p1: Coordinate?, p2: Coordinate?
    )

    protected val isCollinear: Boolean
        protected get() = intersectionNum == COLLINEAR_INTERSECTION

    /**
     * Computes the intersection of the lines p1-p2 and p3-p4.
     * This function computes both the boolean value of the hasIntersection test
     * and the (approximate) value of the intersection point itself (if there is one).
     */
    fun computeIntersection(
        p1: Coordinate?, p2: Coordinate?,
        p3: Coordinate?, p4: Coordinate?
    ) {
        inputLines[0][0] = p1
        inputLines[0][1] = p2
        inputLines[1][0] = p3
        inputLines[1][1] = p4
        intersectionNum = computeIntersect(p1, p2, p3, p4)
        //numIntersects++;
    }

    protected abstract fun computeIntersect(
        p1: Coordinate?, p2: Coordinate?,
        q1: Coordinate?, q2: Coordinate?
    ): Int

    /*
  public String toString() {
    String str = inputLines[0][0] + "-"
         + inputLines[0][1] + " "
         + inputLines[1][0] + "-"
         + inputLines[1][1] + " : "
               + getTopologySummary();
    return str;
  }
*/
    override fun toString(): String {
        return (WKTWriter.toLineString(inputLines[0][0]!!, inputLines[0][1]!!) + " - "
                + WKTWriter.toLineString(inputLines[1][0]!!, inputLines[1][1]!!)
                + topologySummary)
    }

    private val topologySummary: String
        private get() {
            val catBuilder = StringBuilder()
            if (isEndPoint) catBuilder.append(" endpoint")
            if (isProper) catBuilder.append(" proper")
            if (isCollinear) catBuilder.append(" collinear")
            return catBuilder.toString()
        }
    protected val isEndPoint: Boolean
        protected get() = hasIntersection() && !isProper

    /**
     * Tests whether the input geometries intersect.
     *
     * @return true if the input geometries intersect
     */
    fun hasIntersection(): Boolean {
        return intersectionNum != NO_INTERSECTION
    }

    /**
     * Returns the intIndex'th intersection point
     *
     * @param intIndex is 0 or 1
     *
     * @return the intIndex'th intersection point
     */
    fun getIntersection(intIndex: Int): Coordinate {
        return intPt[intIndex]!!
    }

    protected fun computeIntLineIndex() {
        if (intLineIndex == null) {
            intLineIndex = Array(2) { IntArray(2) }
            computeIntLineIndex(0)
            computeIntLineIndex(1)
        }
    }

    /**
     * Test whether a point is a intersection point of two line segments.
     * Note that if the intersection is a line segment, this method only tests for
     * equality with the endpoints of the intersection segment.
     * It does **not** return true if
     * the input point is internal to the intersection segment.
     *
     * @return true if the input point is one of the intersection points.
     */
    fun isIntersection(pt: Coordinate?): Boolean {
        for (i in 0 until intersectionNum) {
            if (intPt[i]!!.equals2D(pt!!)) {
                return true
            }
        }
        return false
    }

    /**
     * Tests whether either intersection point is an interior point of one of the input segments.
     *
     * @return `true` if either intersection point is in the interior of one of the input segments
     */
    val isInteriorIntersection: Boolean
        get() {
            if (isInteriorIntersection(0)) return true
            return isInteriorIntersection(1)
        }

    /**
     * Tests whether either intersection point is an interior point of the specified input segment.
     *
     * @return `true` if either intersection point is in the interior of the input segment
     */
    fun isInteriorIntersection(inputLineIndex: Int): Boolean {
        for (i in 0 until intersectionNum) {
            if (!(intPt[i]!!.equals2D(inputLines[inputLineIndex][0]!!)
                        || intPt[i]!!.equals2D(inputLines[inputLineIndex][1]!!))
            ) {
                return true
            }
        }
        return false
    }

    /**
     * Computes the intIndex'th intersection point in the direction of
     * a specified input line segment
     *
     * @param segmentIndex is 0 or 1
     * @param intIndex is 0 or 1
     *
     * @return the intIndex'th intersection point in the direction of the specified input line segment
     */
    fun getIntersectionAlongSegment(segmentIndex: Int, intIndex: Int): Coordinate? {
        // lazily compute int line array
        computeIntLineIndex()
        return intPt[intLineIndex!![segmentIndex][intIndex]]
    }

    /**
     * Computes the index (order) of the intIndex'th intersection point in the direction of
     * a specified input line segment
     *
     * @param segmentIndex is 0 or 1
     * @param intIndex is 0 or 1
     *
     * @return the index of the intersection point along the input segment (0 or 1)
     */
    fun getIndexAlongSegment(segmentIndex: Int, intIndex: Int): Int {
        computeIntLineIndex()
        return intLineIndex!![segmentIndex][intIndex]
    }

    protected fun computeIntLineIndex(segmentIndex: Int) {
        val dist0 = getEdgeDistance(segmentIndex, 0)
        val dist1 = getEdgeDistance(segmentIndex, 1)
        if (dist0 > dist1) {
            intLineIndex!![segmentIndex][0] = 0
            intLineIndex!![segmentIndex][1] = 1
        } else {
            intLineIndex!![segmentIndex][0] = 1
            intLineIndex!![segmentIndex][1] = 0
        }
    }

    /**
     * Computes the "edge distance" of an intersection point along the specified input line segment.
     *
     * @param segmentIndex is 0 or 1
     * @param intIndex is 0 or 1
     *
     * @return the edge distance of the intersection point
     */
    fun getEdgeDistance(segmentIndex: Int, intIndex: Int): Double {
        return computeEdgeDistance(
            intPt[intIndex], inputLines[segmentIndex][0],
            inputLines[segmentIndex][1]
        )
    }

    companion object {
        /**
         * These are deprecated, due to ambiguous naming
         */
        const val DONT_INTERSECT = 0
        const val DO_INTERSECT = 1
        const val COLLINEAR = 2

        /**
         * Indicates that line segments do not intersect
         */
        const val NO_INTERSECTION = 0

        /**
         * Indicates that line segments intersect in a single point
         */
        const val POINT_INTERSECTION = 1

        /**
         * Indicates that line segments intersect in a line segment
         */
        const val COLLINEAR_INTERSECTION = 2

        /**
         * Computes the "edge distance" of an intersection point p along a segment.
         * The edge distance is a metric of the point along the edge.
         * The metric used is a robust and easy to compute metric function.
         * It is **not** equivalent to the usual Euclidean metric.
         * It relies on the fact that either the x or the y ordinates of the
         * points in the edge are unique, depending on whether the edge is longer in
         * the horizontal or vertical direction.
         *
         * NOTE: This function may produce incorrect distances
         * for inputs where p is not precisely on p1-p2
         * (E.g. p = (139,9) p1 = (139,10), p2 = (280,1) produces distance 0.0, which is incorrect.
         *
         * My hypothesis is that the function is safe to use for points which are the
         * result of **rounding** points which lie on the line,
         * but not safe to use for **truncated** points.
         */
        fun computeEdgeDistance(
            p: Coordinate?,
            p0: Coordinate?,
            p1: Coordinate?
        ): Double {
            val dx = Math.abs(p1!!.x - p0!!.x)
            val dy = Math.abs(p1.y - p0.y)
            var dist = -1.0 // sentinel value
            if (p!! == p0) {
                dist = 0.0
            } else if (p == p1) {
                dist = if (dx > dy) dx else dy
            } else {
                val pdx = Math.abs(p.x - p0.x)
                val pdy = Math.abs(p.y - p0.y)
                dist = if (dx > dy) pdx else pdy
                // <FIX>
                // hack to ensure that non-endpoints always have a non-zero distance
                if (dist == 0.0 && p != p0) {
                    dist = max(pdx, pdy)
                }
            }
            isTrue(!(dist == 0.0 && p != p0), "Bad distance calculation")
            return dist
        }

        /**
         * This function is non-robust, since it may compute the square of large numbers.
         * Currently not sure how to improve this.
         */
        fun nonRobustComputeEdgeDistance(
            p: Coordinate,
            p1: Coordinate,
            p2: Coordinate?
        ): Double {
            val dx = p.x - p1.x
            val dy = p.y - p1.y
            val dist = Math.sqrt(dx * dx + dy * dy) // dummy value
            isTrue(!(dist == 0.0 && p != p1), "Invalid distance calculation")
            return dist
        }
    }

    //public int numIntersects = 0;
    init {
        intPt[0] = Coordinate()
        intPt[1] = Coordinate()
        // alias the intersection points for ease of reference
        pa = intPt[0]
        pb = intPt[1]
        intersectionNum = 0
    }
}