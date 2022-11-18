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
package org.locationtech.jts.triangulate.tri

import org.locationtech.jts.geom.Coordinate
import kotlin.jvm.JvmStatic

/**
 * Builds a triangulation from a set of [Tri]s
 * by populating the links to adjacent triangles.
 *
 * @author mdavis
 */
class TriangulationBuilder private constructor(triList: List<Tri>) {
    private val triMap: HashMap<TriEdge, Tri> = HashMap()

    /**
     * Computes the triangulation of a set of [Tri]s.
     *
     * @param triList the list of Tris
     */
    init {
        for (tri in triList) {
            add(tri)
        }
    }

    private fun find(p0: Coordinate, p1: Coordinate): Tri? {
        val e: TriEdge = TriEdge(p0, p1)
        return triMap[e]
    }

    private fun add(tri: Tri) {
        val p0: Coordinate = tri.getCoordinate(0)
        val p1: Coordinate = tri.getCoordinate(1)
        val p2: Coordinate = tri.getCoordinate(2)

        // get adjacent triangles, if any
        val n0: Tri? = find(p0, p1)
        val n1: Tri? = find(p1, p2)
        val n2: Tri? = find(p2, p0)
        tri.setAdjacent(n0, n1, n2)
        addAdjacent(tri, n0, p0, p1)
        addAdjacent(tri, n1, p1, p2)
        addAdjacent(tri, n2, p2, p0)
    }

    private fun addAdjacent(
        tri: Tri,
        adj: Tri?,
        p0: Coordinate,
        p1: Coordinate
    ) {
        /**
         * If adjacent is null, this tri is first one to be recorded for edge
         */
        if (adj == null) {
            triMap[TriEdge(p0, p1)] = tri
            return
        }
        adj.setAdjacent(p1, tri)
    }

    companion object {
        /**
         * Computes the triangulation of a set of [Tri]s.
         *
         * @param triList the list of Tris
         */
        @JvmStatic
        fun build(triList: List<Tri>) {
            TriangulationBuilder(triList)
        }
    }
}