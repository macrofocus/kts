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
package org.locationtech.jts.util

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.CoordinateFilter

/**
 * A [CoordinateFilter] that extracts a unique array of `Coordinate`s.
 * The array of coordinates contains no duplicate points.
 * It preserves the order of the input points.
 *
 * @version 1.7
 */
class UniqueCoordinateArrayFilter : CoordinateFilter {
    private val coordSet: MutableSet<Coordinate?> = HashSet()

    // Use an auxiliary list as well in order to preserve coordinate order
    private val list: MutableList<Coordinate?> = ArrayList()

    /**
     * Returns the gathered `Coordinate`s.
     *
     * @return    the `Coordinate`s collected by this `CoordinateArrayFilter`
     */
    val coordinates: Array<Coordinate>
        get() {
            return list.requireNoNulls().toTypedArray()
        }

    /**
     * @see CoordinateFilter.filter
     */
    override fun filter(coord: Coordinate?) {
        if (coordSet.add(coord)) {
            list.add(coord)
        }
    }

    companion object {
        /**
         * Convenience method which allows running the filter over an array of [Coordinate]s.
         *
         * @param coords an array of coordinates
         * @return an array of the unique coordinates
         */
        fun filterCoordinates(coords: Array<Coordinate>): Array<Coordinate> {
            val filter = UniqueCoordinateArrayFilter()
            for (i in coords.indices) {
                filter.filter(coords[i])
            }
            return filter.coordinates
        }
    }
}