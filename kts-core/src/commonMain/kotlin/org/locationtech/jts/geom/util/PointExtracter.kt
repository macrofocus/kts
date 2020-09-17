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
package org.locationtech.jts.geom.util

import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryCollection
import org.locationtech.jts.geom.GeometryFilter
import org.locationtech.jts.geom.Point

/**
 * Extracts all the 0-dimensional ([Point]) components from a [Geometry].
 *
 * @version 1.7
 * @see GeometryExtracter
 */
class PointExtracter
/**
 * Constructs a PointExtracterFilter with a list in which to store Points found.
 */(private val pts: MutableList<Geometry>) : GeometryFilter {
    override fun filter(geom: Geometry) {
        if (geom is Point) pts.add(geom)
    }

    companion object {
        /**
         * Extracts the [Point] elements from a single [Geometry]
         * and adds them to the provided [List].
         *
         * @param geom the geometry from which to extract
         * @param list the list to add the extracted elements to
         */
        fun getPoints(geom: Geometry, list: MutableList<Geometry>): List<Geometry> {
            if (geom is Point) {
                list.add(geom)
            } else (geom as? GeometryCollection)?.apply(PointExtracter(list))
            // skip non-Polygonal elemental geometries
            return list
        }

        /**
         * Extracts the [Point] elements from a single [Geometry]
         * and returns them in a [List].
         *
         * @param geom the geometry from which to extract
         */
        fun getPoints(geom: Geometry): List<Geometry> {
            return (geom as? Point)?.let { listOf(it) }
                ?: getPoints(geom, ArrayList())
        }
    }
}