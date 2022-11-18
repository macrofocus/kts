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

import org.locationtech.jts.triangulate.quadedge.QuadEdge
import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision
import org.locationtech.jts.triangulate.quadedge.Vertex

/**
 * Computes a Delaunay Triangulation of a set of [Vertex]es, using an
 * incremental insertion algorithm.
 *
 * @author Martin Davis
 * @version 1.0
 */
class IncrementalDelaunayTriangulator(private val subdiv: QuadEdgeSubdivision) {
    private var isUsingTolerance = false

    /**
     * Creates a new triangulator using the given [QuadEdgeSubdivision].
     * The triangulator uses the tolerance of the supplied subdivision.
     *
     * @param subdiv
     * a subdivision in which to build the TIN
     */
    init {
        isUsingTolerance = subdiv.tolerance > 0.0
    }

    /**
     * Inserts all sites in a collection. The inserted vertices **MUST** be
     * unique up to the provided tolerance value. (i.e. no two vertices should be
     * closer than the provided tolerance value). They do not have to be rounded
     * to the tolerance grid, however.
     *
     * @param vertices a Collection of Vertex
     *
     * @throws LocateFailureException if the location algorithm fails to converge in a reasonable number of iterations
     */
    fun insertSites(vertices: Collection<*>) {
        val i = vertices.iterator()
        while (i.hasNext()) {
            val v = i.next() as Vertex
            insertSite(v)
        }
    }

    /**
     * Inserts a new point into a subdivision representing a Delaunay
     * triangulation, and fixes the affected edges so that the result is still a
     * Delaunay triangulation.
     *
     *
     * @return a quadedge containing the inserted vertex
     */
    fun insertSite(v: Vertex?): QuadEdge? {
        /**
         * This code is based on Guibas and Stolfi (1985), with minor modifications
         * and a bug fix from Dani Lischinski (Graphic Gems 1993). (The modification
         * I believe is the test for the inserted site falling exactly on an
         * existing edge. Without this test zero-width triangles have been observed
         * to be created)
         */
        var e: QuadEdge? = subdiv.locate(v)
        if (subdiv.isVertexOfEdge(e!!, v!!)) {
            // point is already in subdivision.
            return e
        } else if (subdiv.isOnEdge(e, v.coordinate)) {
            // the point lies exactly on an edge, so delete the edge 
            // (it will be replaced by a pair of edges which have the point as a vertex)
            e = e.oPrev()
            subdiv.delete(e!!.oNext()!!)
        }
        /**
         * Connect the new point to the vertices of the containing triangle
         * (or quadrilateral, if the new point fell on an existing edge.)
         */
        var base = subdiv.makeEdge(e.orig(), v)
        QuadEdge.splice(base, e)
        val startEdge = base
        do {
            base = subdiv.connect(e!!, base.sym()!!)
            e = base.oPrev()
        } while (e!!.lNext() != startEdge)

        // Examine suspect edges to ensure that the Delaunay condition
        // is satisfied.
        do {
            val t = e!!.oPrev()
            e = if (t!!.dest()!!.rightOf(e) && v.isInCircle(e.orig()!!, t.dest()!!, e.dest()!!)) {
                QuadEdge.swap(e)
                e.oPrev()
            } else if (e.oNext() == startEdge) {
                return base // no more suspect edges.
            } else {
                e.oNext()!!.lPrev()
            }
        } while (true)
    }
}