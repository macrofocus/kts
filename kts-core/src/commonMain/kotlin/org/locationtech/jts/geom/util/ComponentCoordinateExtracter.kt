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
package org.locationtech.jts.geom.util

import org.locationtech.jts.geom.*

/**
 * Extracts a representative [Coordinate]
 * from each connected component of a [Geometry].
 *
 * @version 1.9
 */
class ComponentCoordinateExtracter(coords: MutableList<Coordinate>) : GeometryComponentFilter {
    private val coords: MutableList<Coordinate>

    /**
     * Constructs a LineExtracterFilter with a list in which to store LineStrings found.
     */
    init {
        this.coords = coords
    }

    override fun filter(geom: Geometry) {
        // add coordinates from connected components
        if (geom is LineString
            || geom is Point
        ) coords.add(geom.coordinate!!)
    }

    companion object {
        /**
         * Extracts a representative [Coordinate]
         * from each connected component in a geometry.
         *
         *
         * If more than one geometry is to be processed, it is more
         * efficient to create a single [ComponentCoordinateExtracter] instance
         * and pass it to each geometry.
         *
         * @param geom the Geometry from which to extract
         * @return a list of representative Coordinates
         */
        fun getCoordinates(geom: Geometry): MutableList<Coordinate> {
            val coords: MutableList<Coordinate> = ArrayList()
            geom.apply(ComponentCoordinateExtracter(coords))
            return coords
        }
    }
}