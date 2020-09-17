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
package org.locationtech.jts.noding

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.legacy.Math
import kotlin.jvm.JvmStatic

/**
 * Methods for computing and working with octants of the Cartesian plane
 * Octants are numbered as follows:
 * <pre>
 * \2|1/
 * 3 \|/ 0
 * ---+--
 * 4 /|\ 7
 * /5|6\
</pre> *
 * If line segments lie along a coordinate axis, the octant is the lower of the two
 * possible values.
 *
 * @version 1.7
 */
object Octant {
    /**
     * Returns the octant of a directed line segment (specified as x and y
     * displacements, which cannot both be 0).
     */
    fun octant(dx: Double, dy: Double): Int {
        require(!(dx == 0.0 && dy == 0.0)) { "Cannot compute the octant for point ( $dx, $dy )" }
        val adx = Math.abs(dx)
        val ady = Math.abs(dy)
        return if (dx >= 0) {
            if (dy >= 0) {
                if (adx >= ady) 0 else 1
            } else { // dy < 0
                if (adx >= ady) 7 else 6
            }
        } else { // dx < 0
            if (dy >= 0) {
                if (adx >= ady) 3 else 2
            } else { // dy < 0
                if (adx >= ady) 4 else 5
            }
        }
    }

    /**
     * Returns the octant of a directed line segment from p0 to p1.
     */
    @JvmStatic
    fun octant(p0: Coordinate, p1: Coordinate): Int {
        val dx = p1.x - p0.x
        val dy = p1.y - p0.y
        require(!(dx == 0.0 && dy == 0.0)) { "Cannot compute the octant for two identical points $p0" }
        return octant(dx, dy)
    }
}