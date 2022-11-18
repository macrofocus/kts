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
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryCollection
import org.locationtech.jts.geom.GeometryFilter

/**
 * Computes an interior point of a `[Geometry]`.
 * An interior point is guaranteed to lie in the interior of the Geometry,
 * if it possible to calculate such a point exactly.
 * Otherwise, the point may lie on the boundary of the geometry.
 * For collections the interior point is computed for the collection of
 * non-empty elements of highest dimension.
 * The interior point of an empty geometry is `null`.
 *
 * <h2>Algorithm</h2>
 * The point is chosen to be "close to the center" of the geometry.
 * The location depends on the dimension of the input:
 *
 *  * **Dimension 2** - the interior point is constructed in the middle of the longest interior segment
 * of a line bisecting the area.
 *
 *  * **Dimension 1** - the interior point is the interior or boundary vertex closest to the centroid.
 *
 *  * **Dimension 0** - the point is the point closest to the centroid.
 *
 * @see Centroid
 *
 * @see MaximumInscribedCircle
 *
 * @see LargestEmptyCircle
 */
object InteriorPoint {
    /**
     * Computes a location of an interior point in a [Geometry].
     * Handles all geometry types.
     *
     * @param geom a geometry in which to find an interior point
     * @return the location of an interior point,
     * or `null` if the input is empty
     */
    fun getInteriorPoint(geom: Geometry): Coordinate? {
        if (geom.isEmpty) return null
        var interiorPt: Coordinate? = null
        //int dim = geom.getDimension();
        val dim = effectiveDimension(geom)
        // this should not happen, but just in case...
        if (dim < 0) {
            return null
        }
        interiorPt = if (dim == 0) {
            InteriorPointPoint.getInteriorPoint(geom)
        } else if (dim == 1) {
            InteriorPointLine.getInteriorPoint(geom)
        } else {
            InteriorPointArea.getInteriorPoint(geom)
        }
        return interiorPt
    }

    private fun effectiveDimension(geom: Geometry): Int {
        val dimFilter = EffectiveDimensionFilter()
        geom.apply(dimFilter)
        return dimFilter.dimension
    }

    private class EffectiveDimensionFilter : GeometryFilter {
        var dimension = -1
            private set

        override fun filter(elem: Geometry?) {
            if (elem is GeometryCollection) return
            if (!elem!!.isEmpty) {
                val elemDim = elem.dimension
                if (elemDim > dimension) dimension = elemDim
            }
        }
    }
}