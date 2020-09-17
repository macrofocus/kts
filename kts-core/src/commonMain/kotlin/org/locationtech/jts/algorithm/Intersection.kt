/*
 * Copyright (c) 2019 martin Davis
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
import org.locationtech.jts.legacy.Math
import kotlin.jvm.JvmStatic

/**
 * Contains functions to compute intersections between lines.
 *
 * @author Martin Davis
 * @author Luc Girardin
 */
object Intersection {
    /**
     * Computes the intersection point of two lines.
     * If the lines are parallel or collinear this case is detected
     * and `null` is returned.
     *
     * In general it is not possible to accurately compute
     * the intersection point of two lines, due to
     * numerical roundoff.
     * This is particularly true when the input lines are nearly parallel.
     * This routine uses numerical conditioning on the input values
     * to ensure that the computed value should be very close to the correct value.
     *
     * @param p1 an endpoint of line 1
     * @param p2 an endpoint of line 1
     * @param q1 an endpoint of line 2
     * @param q2 an endpoint of line 2
     * @return the intersection point between the lines, if there is one,
     * or null if the lines are parallel or collinear
     *
     * @see CGAlgorithmsDD.intersection
     */
    @JvmStatic
    fun intersection(p1: Coordinate, p2: Coordinate, q1: Coordinate, q2: Coordinate): Coordinate? {
        // compute midpoint of "kernel envelope"
        val minX0 = if (p1.x < p2.x) p1.x else p2.x
        val minY0 = if (p1.y < p2.y) p1.y else p2.y
        val maxX0 = if (p1.x > p2.x) p1.x else p2.x
        val maxY0 = if (p1.y > p2.y) p1.y else p2.y
        val minX1 = if (q1.x < q2.x) q1.x else q2.x
        val minY1 = if (q1.y < q2.y) q1.y else q2.y
        val maxX1 = if (q1.x > q2.x) q1.x else q2.x
        val maxY1 = if (q1.y > q2.y) q1.y else q2.y
        val intMinX = if (minX0 > minX1) minX0 else minX1
        val intMaxX = if (maxX0 < maxX1) maxX0 else maxX1
        val intMinY = if (minY0 > minY1) minY0 else minY1
        val intMaxY = if (maxY0 < maxY1) maxY0 else maxY1
        val midx = (intMinX + intMaxX) / 2.0
        val midy = (intMinY + intMaxY) / 2.0

        // condition ordinate values by subtracting midpoint
        val p1x = p1.x - midx
        val p1y = p1.y - midy
        val p2x = p2.x - midx
        val p2y = p2.y - midy
        val q1x = q1.x - midx
        val q1y = q1.y - midy
        val q2x = q2.x - midx
        val q2y = q2.y - midy

        // unrolled computation using homogeneous coordinates eqn
        val px = p1y - p2y
        val py = p2x - p1x
        val pw = p1x * p2y - p2x * p1y
        val qx = q1y - q2y
        val qy = q2x - q1x
        val qw = q1x * q2y - q2x * q1y
        val x = py * qw - qy * pw
        val y = qx * pw - px * qw
        val w = px * qy - qx * py
        val xInt = x / w
        val yInt = y / w

        // check for parallel lines
        return if (Math.isNaN(xInt) || (Math.isInfinite(xInt)
                    || Math.isNaN(yInt)) || Math.isInfinite(yInt)
        ) {
            null
        } else Coordinate(xInt + midx, yInt + midy)
        // de-condition intersection point
    }
}