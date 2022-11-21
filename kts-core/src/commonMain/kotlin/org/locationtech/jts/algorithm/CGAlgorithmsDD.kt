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
package org.locationtech.jts.algorithm

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.legacy.Math.isInfinite
import org.locationtech.jts.legacy.Math.isNaN
import org.locationtech.jts.math.DD
import kotlin.jvm.JvmStatic

/**
 * Implements basic computational geometry algorithms using [DD] arithmetic.
 *
 * @author Martin Davis
 */
object CGAlgorithmsDD {
    /**
     * Returns the index of the direction of the point `q` relative to
     * a vector specified by `p1-p2`.
     *
     * @param p1 the origin point of the vector
     * @param p2 the final point of the vector
     * @param q the point to compute the direction to
     *
     * @return `1` if q is counter-clockwise (left) from p1-p2
     * `-1` if q is clockwise (right) from p1-p2
     * `0` if q is collinear with p1-p2
     */
    @JvmStatic
    fun orientationIndex(p1: Coordinate, p2: Coordinate, q: Coordinate): Int {
        return orientationIndex(p1.x, p1.y, p2.x, p2.y, q.x, q.y)
    }

    /**
     * Returns the index of the direction of the point `q` relative to
     * a vector specified by `p1-p2`.
     *
     * @param p1x the x ordinate of the vector origin point
     * @param p1y the y ordinate of the vector origin point
     * @param p2x the x ordinate of the vector final point
     * @param p2y the y ordinate of the vector final point
     * @param qx the x ordinate of the query point
     * @param qy the y ordinate of the query point
     *
     * @return 1 if q is counter-clockwise (left) from p1-p2
     * -1 if q is clockwise (right) from p1-p2
     * 0 if q is collinear with p1-p2
     */
    fun orientationIndex(
        p1x: Double, p1y: Double,
        p2x: Double, p2y: Double,
        qx: Double, qy: Double
    ): Int {
        // fast filter for orientation index
        // avoids use of slow extended-precision arithmetic in many cases
        val index = orientationIndexFilter(p1x, p1y, p2x, p2y, qx, qy)
        if (index <= 1) return index

        // normalize coordinates
        val dx1 = DD.valueOf(p2x).selfAdd(-p1x)
        val dy1 = DD.valueOf(p2y).selfAdd(-p1y)
        val dx2 = DD.valueOf(qx).selfAdd(-p2x)
        val dy2 = DD.valueOf(qy).selfAdd(-p2y)

        // sign of determinant - unrolled for performance
        return dx1.selfMultiply(dy2).selfSubtract(dy1.selfMultiply(dx2)).signum()
    }

    /**
     * Computes the sign of the determinant of the 2x2 matrix
     * with the given entries.
     *
     * @return -1 if the determinant is negative,
     * 1 if the determinant is positive,
     * 0 if the determinant is 0.
     */
    fun signOfDet2x2(x1: DD, y1: DD, x2: DD?, y2: DD?): Int {
        val det = x1.multiply(y2!!).selfSubtract(y1.multiply(x2!!))
        return det.signum()
    }

    /**
     * Computes the sign of the determinant of the 2x2 matrix
     * with the given entries.
     *
     * @return -1 if the determinant is negative,
     * 1 if the determinant is positive,
     * 0 if the determinant is 0.
     */
    @JvmStatic
    fun signOfDet2x2(dx1: Double, dy1: Double, dx2: Double, dy2: Double): Int {
        val x1 = DD.valueOf(dx1)
        val y1 = DD.valueOf(dy1)
        val x2 = DD.valueOf(dx2)
        val y2 = DD.valueOf(dy2)
        val det = x1.multiply(y2).selfSubtract(y1.multiply(x2))
        return det.signum()
    }

    /**
     * A value which is safely greater than the
     * relative round-off error in double-precision numbers
     */
    private const val DP_SAFE_EPSILON = 1e-15

    /**
     * A filter for computing the orientation index of three coordinates.
     *
     * If the orientation can be computed safely using standard DP
     * arithmetic, this routine returns the orientation index.
     * Otherwise, a value i > 1 is returned.
     * In this case the orientation index must
     * be computed using some other more robust method.
     * The filter is fast to compute, so can be used to
     * avoid the use of slower robust methods except when they are really needed,
     * thus providing better average performance.
     *
     * Uses an approach due to Jonathan Shewchuk, which is in the public domain.
     *
     * @param pax A coordinate
     * @param pay A coordinate
     * @param pbx B coordinate
     * @param pby B coordinate
     * @param pcx C coordinate
     * @param pcy C coordinate
     * @return the orientation index if it can be computed safely
     * @return i > 1 if the orientation index cannot be computed safely
     */
    private fun orientationIndexFilter(
        pax: Double, pay: Double,
        pbx: Double, pby: Double, pcx: Double, pcy: Double
    ): Int {
        val detsum: Double
        val detleft = (pax - pcx) * (pby - pcy)
        val detright = (pay - pcy) * (pbx - pcx)
        val det = detleft - detright
        detsum = if (detleft > 0.0) {
            if (detright <= 0.0) {
                return signum(det)
            } else {
                detleft + detright
            }
        } else if (detleft < 0.0) {
            if (detright >= 0.0) {
                return signum(det)
            } else {
                -detleft - detright
            }
        } else {
            return signum(det)
        }
        val errbound = DP_SAFE_EPSILON * detsum
        return if (det >= errbound || -det >= errbound) {
            signum(det)
        } else 2
    }

    private fun signum(x: Double): Int {
        if (x > 0) return 1
        return if (x < 0) -1 else 0
    }

    /**
     * Computes an intersection point between two lines
     * using DD arithmetic.
     * If the lines are parallel (either identical
     * or separate) a null value is returned.
     *
     * @param p1 an endpoint of line segment 1
     * @param p2 an endpoint of line segment 1
     * @param q1 an endpoint of line segment 2
     * @param q2 an endpoint of line segment 2
     * @return an intersection point if one exists, or null if the lines are parallel
     */
    @JvmStatic
    fun intersection(
        p1: Coordinate, p2: Coordinate,
        q1: Coordinate, q2: Coordinate
    ): Coordinate? {
        val px = DD(p1.y).selfSubtract(p2.y)
        val py = DD(p2.x).selfSubtract(p1.x)
        val pw = DD(p1.x).selfMultiply(p2.y).selfSubtract(DD(p2.x).selfMultiply(p1.y))
        val qx = DD(q1.y).selfSubtract(q2.y)
        val qy = DD(q2.x).selfSubtract(q1.x)
        val qw = DD(q1.x).selfMultiply(q2.y).selfSubtract(DD(q2.x).selfMultiply(q1.y))
        val x = py.multiply(qw).selfSubtract(qy.multiply(pw))
        val y = qx.multiply(pw).selfSubtract(px.multiply(qw))
        val w = px.multiply(qy).selfSubtract(qx.multiply(py))
        val xInt = x.selfDivide(w).doubleValue()
        val yInt = y.selfDivide(w).doubleValue()
        return if (isNaN(xInt) || isInfinite(xInt) || isNaN(yInt) || isInfinite(
                yInt
            )
        ) {
            null
        } else Coordinate(xInt, yInt)
    }
}