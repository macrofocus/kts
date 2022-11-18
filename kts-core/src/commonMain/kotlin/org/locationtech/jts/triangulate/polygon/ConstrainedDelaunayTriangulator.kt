/*
 * Copyright (c) 2021 Martin Davis.
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
package org.locationtech.jts.triangulate.polygon

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.util.PolygonExtracter
import org.locationtech.jts.triangulate.tri.Tri
import org.locationtech.jts.triangulate.tri.TriangulationBuilder

/**
 * Computes the Constrained Delaunay Triangulation of polygons.
 * The Constrained Delaunay Triangulation of a polygon is a set of triangles
 * covering the polygon, with the maximum total interior angle over all
 * possible triangulations.  It provides the "best quality" triangulation
 * of the polygon.
 *
 * Holes are supported.
 */
class ConstrainedDelaunayTriangulator(inputGeom: Geometry) {
    private val geomFact: GeometryFactory
    private val inputGeom: Geometry
    private var triList: MutableList<Tri>? = null

    /**
     * Constructs a new Constrained Delaunay triangulator.
     *
     * @param inputGeom the input geometry
     */
    init {
        geomFact = inputGeom.factory
        this.inputGeom = inputGeom
    }

    /**
     * Gets the triangulation as a [GeometryCollection] of triangular [Polygon]s.
     *
     * @return a collection of the result triangle polygons
     */
    val result: Geometry
        get() {
            compute()
            return Tri.toGeometry(triList!!, geomFact!!)
        }

    /**
     * Gets the triangulation as a list of [Tri]s.
     *
     * @return the list of Tris in the triangulation
     */
    val triangles: List<Tri>?
        get() {
            compute()
            return triList
        }

    private fun compute() {
        if (triList != null) return
        val polys: List<Polygon> = PolygonExtracter.getPolygons(inputGeom)
        triList = ArrayList<Tri>()
        for (poly in polys) {
            val polyTriList: List<Tri> = triangulatePolygon(poly)
            triList!!.addAll(polyTriList)
        }
    }

    /**
     * Computes the triangulation of a single polygon
     * and returns it as a list of [Tri]s.
     *
     * @param poly the input polygon
     * @return list of Tris forming the triangulation
     */
    fun triangulatePolygon(poly: Polygon): List<Tri> {
        /**
         * Normalize to ensure that shell and holes have canonical orientation.
         *
         * TODO: perhaps better to just correct orientation of rings?
         */
        val polyNorm = poly.norm() as Polygon
        val polyShell: Array<Coordinate> =
            PolygonHoleJoiner.join(polyNorm)
        val triList: List<Tri> =
            PolygonEarClipper.triangulate(polyShell)

        //long start = System.currentTimeMillis();
        TriangulationBuilder.build(triList)
        TriDelaunayImprover.improve(triList)
        //System.out.println("swap used: " + (System.currentTimeMillis() - start) + " milliseconds");
        return triList
    }

    companion object {
        /**
         * Computes the Constrained Delaunay Triangulation of each polygon element in a geometry.
         *
         * @param geom the input geometry
         * @return a GeometryCollection of the computed triangle polygons
         */
        fun triangulate(geom: Geometry): Geometry {
            val cdt = ConstrainedDelaunayTriangulator(geom)
            return cdt.result
        }
    }
}