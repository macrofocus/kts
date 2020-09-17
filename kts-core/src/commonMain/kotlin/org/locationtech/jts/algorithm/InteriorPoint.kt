/*
 * Copyright (c) 2016 Martin Davis.
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
import org.locationtech.jts.geom.Geometry

/**
 * Computes an interior point of a `[Geometry]`.
 * An interior point is guaranteed to lie in the interior of the Geometry,
 * if it possible to calculate such a point exactly.
 * Otherwise, the point may lie on the boundary of the geometry.
 *
 * The interior point of an empty geometry is `null`.
 */
object InteriorPoint {
    /**
     * Compute a location of an interior point in a [Geometry].
     * Handles all geometry types.
     *
     * @param geom a geometry in which to find an interior point
     * @return the location of an interior point,
     * or `null` if the input is empty
     */
    fun getInteriorPoint(geom: Geometry): Coordinate? {
        if (geom.isEmpty) return null
//        var interiorPt: Coordinate? = null
        val dim = geom.dimension
        return when (dim) {
            0 -> {
                InteriorPointPoint.getInteriorPoint(geom)
            }
            1 -> {
                InteriorPointLine.getInteriorPoint(geom)
            }
            else -> {
                InteriorPointArea.getInteriorPoint(geom)
            }
        }
    }
}