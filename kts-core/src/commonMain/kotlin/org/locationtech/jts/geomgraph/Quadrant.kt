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
package org.locationtech.jts.geomgraph

import org.locationtech.jts.geom.Coordinate
import kotlin.jvm.JvmStatic

/**
 * @version 1.7
 */
/**
 * Utility functions for working with quadrants, which are numbered as follows:
 * <pre>
 * 1 | 0
 * --+--
 * 2 | 3
</pre> *
 *
 * @version 1.7
 */
object Quadrant {
    const val NE = 0
    const val NW = 1
    const val SW = 2
    const val SE = 3

    /**
     * Returns the quadrant of a directed line segment (specified as x and y
     * displacements, which cannot both be 0).
     *
     * @throws IllegalArgumentException if the displacements are both 0
     */
    @JvmStatic
    fun quadrant(dx: Double, dy: Double): Int {
        require(!(dx == 0.0 && dy == 0.0)) { "Cannot compute the quadrant for point ( $dx, $dy )" }
        return if (dx >= 0.0) {
            if (dy >= 0.0) NE else SE
        } else {
            if (dy >= 0.0) NW else SW
        }
    }

    /**
     * Returns the quadrant of a directed line segment from p0 to p1.
     *
     * @throws IllegalArgumentException if the points are equal
     */
    @JvmStatic
    fun quadrant(p0: Coordinate, p1: Coordinate): Int {
        require(!(p1.x == p0.x && p1.y == p0.y)) { "Cannot compute the quadrant for two identical points $p0" }
        return if (p1.x >= p0.x) {
            if (p1.y >= p0.y) NE else SE
        } else {
            if (p1.y >= p0.y) NW else SW
        }
    }

    /**
     * Returns true if the quadrants are 1 and 3, or 2 and 4
     */
    fun isOpposite(quad1: Int, quad2: Int): Boolean {
        if (quad1 == quad2) return false
        val diff = (quad1 - quad2 + 4) % 4
        // if quadrants are not adjacent, they are opposite
        return diff == 2
    }

    /**
     * Returns the right-hand quadrant of the halfplane defined by the two quadrants,
     * or -1 if the quadrants are opposite, or the quadrant if they are identical.
     */
    fun commonHalfPlane(quad1: Int, quad2: Int): Int {
        // if quadrants are the same they do not determine a unique common halfplane.
        // Simply return one of the two possibilities
        if (quad1 == quad2) return quad1
        val diff = (quad1 - quad2 + 4) % 4
        // if quadrants are not adjacent, they do not share a common halfplane
        if (diff == 2) return -1
        //
        val min = if (quad1 < quad2) quad1 else quad2
        val max = if (quad1 > quad2) quad1 else quad2
        // for this one case, the righthand plane is NOT the minimum index;
        return if (min == 0 && max == 3) 3 else min
        // in general, the halfplane index is the minimum of the two adjacent quadrants
    }

    /**
     * Returns whether the given quadrant lies within the given halfplane (specified
     * by its right-hand quadrant).
     */
    fun isInHalfPlane(quad: Int, halfPlane: Int): Boolean {
        return if (halfPlane == SE) {
            quad == SE || quad == SW
        } else quad == halfPlane || quad == halfPlane + 1
    }

    /**
     * Returns true if the given quadrant is 0 or 1.
     */
    @JvmStatic
    fun isNorthern(quad: Int): Boolean {
        return quad == NE || quad == NW
    }
}