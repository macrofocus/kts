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
import kotlin.jvm.JvmStatic

/**
 * Computes a triangulation of each polygon in a [Geometry].
 * A polygon triangulation is a non-overlapping set of triangles which
 * cover the polygon and have the same vertices as the polygon.
 * The priority is on performance rather than triangulation quality,
 * so that the output may contain many narrow triangles.
 *
 * Holes are handled by joining them to the shell to form a
 * (self-touching) polygon shell with no holes.
 * Although invalid, this can be triangulated effectively.
 * <P>
 * For better-quality triangulation use [ConstrainedDelaunayTriangulator].
 *
 * @see ConstrainedDelaunayTriangulator
 *
 * @author Martin Davis
</P> */
class PolygonTriangulator(inputGeom: Geometry) {
    private val geomFact: GeometryFactory
    private val inputGeom: Geometry
    private var triList: MutableList<Tri>? = null

    /**
     * Constructs a new triangulator.
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
            return Tri.toGeometry(triList!!, geomFact)
        }

    /**
     * Gets the triangulation as a list of [Tri]s.
     *
     * @return the list of Tris in the triangulation
     */
    val triangles: List<Any>?
        get() {
            compute()
            return triList
        }

    private fun compute() {
        val polys: List<Polygon> = PolygonExtracter.getPolygons(inputGeom)
        triList = ArrayList()
        for (poly in polys) {
            if (poly.isEmpty) continue
            val polyTriList: List<Tri> = triangulatePolygon(poly)
            triList!!.addAll(polyTriList)
        }
    }

    /**
     * Computes the triangulation of a single polygon
     *
     * @return GeometryCollection of triangular polygons
     */
    private fun triangulatePolygon(poly: Polygon): List<Tri> {
        /**
         * Normalize to ensure that shell and holes have canonical orientation.
         *
         * TODO: perhaps better to just correct orientation of rings?
         */
        val polyNorm = poly.norm() as Polygon
        val polyShell: Array<Coordinate> =
            PolygonHoleJoiner.join(polyNorm)
        //Tri.validate(triList);
        return PolygonEarClipper.triangulate(polyShell)
    }

    companion object {
        /**
         * Computes a triangulation of each polygon in a geometry.
         *
         * @param geom a geometry containing polygons
         * @return a GeometryCollection containing the triangle polygons
         */
        @JvmStatic
        fun triangulate(geom: Geometry): Geometry {
            val triangulator = PolygonTriangulator(geom)
            return triangulator.result
        }
    }
}