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
package org.locationtech.jts.operation.distance

import org.locationtech.jts.geom.*

/**
 * A ConnectedElementPointFilter extracts a single point
 * from each connected element in a Geometry
 * (e.g. a polygon, linestring or point)
 * and returns them in a list. The elements of the list are
 * [org.locationtech.jts.operation.distance.GeometryLocation]s.
 * Empty geometries do not provide a location item.
 *
 * @version 1.7
 */
class ConnectedElementLocationFilter internal constructor(locations: MutableList<GeometryLocation>) : GeometryFilter {
    private val locations: MutableList<GeometryLocation>

    init {
        this.locations = locations
    }

    override fun filter(geom: Geometry?) {
        // empty geometries do not provide a location
        if (geom!!.isEmpty) return
        if (geom is Point
            || geom is LineString
            || geom is Polygon
        ) locations.add(GeometryLocation(geom, 0, geom.coordinate))
    }

    companion object {
        /**
         * Returns a list containing a point from each Polygon, LineString, and Point
         * found inside the specified geometry. Thus, if the specified geometry is
         * not a GeometryCollection, an empty list will be returned. The elements of the list
         * are [org.locationtech.jts.operation.distance.GeometryLocation]s.
         */
        fun getLocations(geom: Geometry): MutableList<GeometryLocation> {
            val locations: MutableList<GeometryLocation> = ArrayList()
            geom.apply(ConnectedElementLocationFilter(locations))
            return locations
        }
    }
}