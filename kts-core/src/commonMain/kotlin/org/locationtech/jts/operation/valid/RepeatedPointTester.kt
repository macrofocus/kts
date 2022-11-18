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
package org.locationtech.jts.operation.valid

import org.locationtech.jts.geom.*

/**
 * Implements the appropriate checks for repeated points
 * (consecutive identical coordinates) as defined in the
 * JTS spec.
 *
 * @version 1.7
 */
class RepeatedPointTester {
    // save the repeated coord found (if any)
    var coordinate: Coordinate? = null
        private set

    fun hasRepeatedPoint(g: Geometry): Boolean {
        if (g.isEmpty) return false
        return if (g is Point) false else if (g is MultiPoint) false else if (g is LineString) hasRepeatedPoint(
            g.coordinates
        ) else if (g is Polygon) hasRepeatedPoint(g) else if (g is GeometryCollection) hasRepeatedPoint(
            g
        ) else throw UnsupportedOperationException(
            g::class.simpleName
        )
    }

    fun hasRepeatedPoint(coord: Array<Coordinate>): Boolean {
        for (i in 1 until coord.size) {
            if (coord[i - 1] == coord[i]) {
                coordinate = coord[i]
                return true
            }
        }
        return false
    }

    private fun hasRepeatedPoint(p: Polygon): Boolean {
        if (hasRepeatedPoint(p.exteriorRing!!.coordinates)) return true
        for (i in 0 until p.getNumInteriorRing()) {
            if (hasRepeatedPoint(p.getInteriorRingN(i).coordinates)) return true
        }
        return false
    }

    private fun hasRepeatedPoint(gc: GeometryCollection): Boolean {
        for (i in 0 until gc.numGeometries) {
            val g = gc.getGeometryN(i)
            if (hasRepeatedPoint(g)) return true
        }
        return false
    }
}