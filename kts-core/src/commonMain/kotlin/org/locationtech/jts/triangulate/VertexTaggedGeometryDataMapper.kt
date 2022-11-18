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
package org.locationtech.jts.triangulate

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.legacy.map.TreeMap

/**
 * Creates a map between the vertex [Coordinate]s of a
 * set of [Geometry]s,
 * and the parent geometry, and transfers the source geometry
 * data objects to geometry components tagged with the coordinates.
 *
 *
 * This class can be used in conjunction with [VoronoiDiagramBuilder]
 * to transfer data objects from the input site geometries
 * to the constructed Voronoi polygons.
 *
 * @author Martin Davis
 * @see VoronoiDiagramBuilder
 */
class VertexTaggedGeometryDataMapper {
    private val coordDataMap: MutableMap<Coordinate, Any?> = TreeMap()
    fun loadSourceGeometries(geoms: Collection<*>) {
        val i = geoms.iterator()
        while (i.hasNext()) {
            val geom = i.next() as Geometry
            loadVertices(geom.coordinates!!, geom.getUserData())
        }
    }

    fun loadSourceGeometries(geomColl: Geometry) {
        for (i in 0 until geomColl.numGeometries) {
            val geom = geomColl.getGeometryN(i)
            loadVertices(geom.coordinates!!, geom.getUserData())
        }
    }

    private fun loadVertices(pts: Array<Coordinate>, data: Any?) {
        for (i in pts.indices) {
            coordDataMap[pts[i]] = data
        }
    }

    fun getCoordinates(): MutableList<Coordinate> {
        return ArrayList(coordDataMap.keys)
    }

    /**
     * Input is assumed to be a multiGeometry
     * in which every component has its userData
     * set to be a Coordinate which is the key to the output data.
     * The Coordinate is used to determine
     * the output data object to be written back into the component.
     *
     * @param targetGeom
     */
    fun transferData(targetGeom: Geometry) {
        for (i in 0 until targetGeom.numGeometries) {
            val geom = targetGeom.getGeometryN(i)
            val vertexKey = geom.getUserData() as Coordinate? ?: continue
            geom.setUserData(coordDataMap[vertexKey])
        }
    }
}