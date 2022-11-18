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

import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.CoordinateArrays.copyDeep
import org.locationtech.jts.geom.CoordinateArrays.toCoordinateArray
import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision
import org.locationtech.jts.triangulate.quadedge.Vertex

/**
 * A utility class which creates Delaunay Triangulations
 * from collections of points and extract the resulting
 * triangulation edges or triangles as geometries.
 *
 * @author Martin Davis
 */
class DelaunayTriangulationBuilder
/**
 * Creates a new triangulation builder.
 *
 */
{
    private var siteCoords: Collection<*>? = null
    private var tolerance = 0.0
    private var subdiv: QuadEdgeSubdivision? = null

    /**
     * Sets the sites (vertices) which will be triangulated.
     * All vertices of the given geometry will be used as sites.
     *
     * @param geom the geometry from which the sites will be extracted.
     */
    fun setSites(geom: Geometry?) {
        // remove any duplicate points (they will cause the triangulation to fail)
        siteCoords = extractUniqueCoordinates(geom)
    }

    /**
     * Sets the sites (vertices) which will be triangulated
     * from a collection of [Coordinate]s.
     *
     * @param coords a collection of Coordinates.
     */
    fun setSites(coords: Collection<*>?) {
        // remove any duplicate points (they will cause the triangulation to fail)
        siteCoords = unique(
            toCoordinateArray(
                coords!!
            )
        )
    }

    /**
     * Sets the snapping tolerance which will be used
     * to improved the robustness of the triangulation computation.
     * A tolerance of 0.0 specifies that no snapping will take place.
     *
     * @param tolerance the tolerance distance to use
     */
    fun setTolerance(tolerance: Double) {
        this.tolerance = tolerance
    }

    private fun create() {
        if (subdiv != null) return
        val siteEnv = envelope(siteCoords)
        val vertices: MutableList<Vertex> = toVertices(siteCoords)
        subdiv = QuadEdgeSubdivision(siteEnv, tolerance)
        val triangulator: org.locationtech.jts.triangulate.IncrementalDelaunayTriangulator =
            org.locationtech.jts.triangulate.IncrementalDelaunayTriangulator(
                subdiv!!
            )
        triangulator.insertSites(vertices)
    }

    /**
     * Gets the [QuadEdgeSubdivision] which models the computed triangulation.
     *
     * @return the subdivision containing the triangulation
     */
    val subdivision: QuadEdgeSubdivision?
        get() {
            create()
            return subdiv
        }

    /**
     * Gets the edges of the computed triangulation as a [MultiLineString].
     *
     * @param geomFact the geometry factory to use to create the output
     * @return the edges of the triangulation
     */
    fun getEdges(geomFact: GeometryFactory?): Geometry {
        create()
        return subdiv!!.getEdges(geomFact!!)
    }

    /**
     * Gets the faces of the computed triangulation as a [GeometryCollection]
     * of [Polygon].
     *
     * @param geomFact the geometry factory to use to create the output
     * @return the faces of the triangulation
     */
    fun getTriangles(geomFact: GeometryFactory?): Geometry {
        create()
        return subdiv!!.getTriangles(geomFact!!)
    }

    companion object {
        /**
         * Extracts the unique [Coordinate]s from the given [Geometry].
         * @param geom the geometry to extract from
         * @return a List of the unique Coordinates
         */
        fun extractUniqueCoordinates(geom: Geometry?): CoordinateList {
            if (geom == null) return CoordinateList()
            val coords: Array<Coordinate> = geom.coordinates!!
            return unique(coords)
        }

        fun unique(coords: Array<Coordinate>): CoordinateList {
            val coordsCopy =
                copyDeep(coords)
            coordsCopy.sort()
            return CoordinateList(coordsCopy, false)
        }

        /**
         * Converts all [Coordinate]s in a collection to [Vertex]es.
         * @param coords the coordinates to convert
         * @return a List of Vertex objects
         */
        fun toVertices(coords: Collection<*>?): MutableList<Vertex> {
            val verts: MutableList<Vertex> = ArrayList()
            val i = coords!!.iterator()
            while (i.hasNext()) {
                val coord = i.next() as Coordinate
                verts.add(Vertex(coord))
            }
            return verts
        }

        /**
         * Computes the [Envelope] of a collection of [Coordinate]s.
         *
         * @param coords a List of Coordinates
         * @return the envelope of the set of coordinates
         */
        fun envelope(coords: Collection<*>?): Envelope {
            val env = Envelope()
            val i = coords!!.iterator()
            while (i.hasNext()) {
                val coord = i.next() as Coordinate
                env.expandToInclude(coord)
            }
            return env
        }
    }
}