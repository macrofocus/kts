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
package org.locationtech.jts.operation.valid

import org.locationtech.jts.geom.*
import org.locationtech.jts.geomgraph.*
import org.locationtech.jts.operation.overlay.MaximalEdgeRing
import org.locationtech.jts.operation.overlay.OverlayNodeFactory
import org.locationtech.jts.util.Assert.isTrue

/**
 * This class tests that the interior of an area [Geometry]
 * ( [Polygon]  or [MultiPolygon] )
 * is connected.
 * This can happen if:
 *
 *  * a shell self-intersects
 *  * one or more holes form a connected chain touching a shell at two different points
 *  * one or more holes form a ring around a subset of the interior
 *
 * If a disconnected situation is found the location of the problem is recorded.
 *
 * @version 1.7
 */
class ConnectedInteriorTester(private val geomGraph: GeometryGraph) {
    private val geometryFactory = GeometryFactory()

    // save a coordinate for any disconnected interior found
    // the coordinate will be somewhere on the ring surrounding the disconnected interior
    var coordinate: Coordinate? = null
        private set

    // node the edges, in case holes touch the shell
    val isInteriorsConnected: Boolean

    // form the edges into rings
            /**
             * Mark all the edges for the edgeRings corresponding to the shells
             * of the input polygons.  Note only ONE ring gets marked for each shell.
             */
        /**
         * If there are any unvisited shell edges
         * (i.e. a ring which is not a hole and which has the interior
         * of the parent area on the RHS)
         * this means that one or more holes must have split the interior of the
         * polygon into at least two pieces.  The polygon is thus invalid.
         */
        get() {
            // node the edges, in case holes touch the shell
            val splitEdges: MutableList<Edge> = ArrayList()
            geomGraph.computeSplitEdges(splitEdges)

            // form the edges into rings
            val graph = PlanarGraph(OverlayNodeFactory())
            graph.addEdges(splitEdges)
            setInteriorEdgesInResult(graph)
            graph.linkResultDirectedEdges()
            val edgeRings = buildEdgeRings(graph.edgeEnds)
            /**
             * Mark all the edges for the edgeRings corresponding to the shells
             * of the input polygons.  Note only ONE ring gets marked for each shell.
             */
            visitShellInteriors(geomGraph.geometry, graph)
            /**
             * If there are any unvisited shell edges
             * (i.e. a ring which is not a hole and which has the interior
             * of the parent area on the RHS)
             * this means that one or more holes must have split the interior of the
             * polygon into at least two pieces.  The polygon is thus invalid.
             */
            return !hasUnvisitedShellEdge(edgeRings)
        }

    private fun setInteriorEdgesInResult(graph: PlanarGraph) {
        val it = graph.edgeEnds.iterator()
        while (it.hasNext()) {
            val de = it.next() as DirectedEdge
            if (de.label!!.getLocation(0, Position.RIGHT) == Location.INTERIOR) {
                de.isInResult = true
            }
        }
    }

    /**
     * Form DirectedEdges in graph into Minimal EdgeRings.
     * (Minimal Edgerings must be used, because only they are guaranteed to provide
     * a correct isHole computation)
     */
    private fun buildEdgeRings(dirEdges: Collection<*>): List<*> {
        val edgeRings: MutableList<Any?> = ArrayList()
        val it = dirEdges.iterator()
        while (it.hasNext()) {
            val de = it.next() as DirectedEdge
            // if this edge has not yet been processed
            if (de.isInResult
                && de.edgeRing == null
            ) {
                val er = MaximalEdgeRing(de, geometryFactory)
                er.linkDirectedEdgesForMinimalEdgeRings()
                val minEdgeRings = er.buildMinimalRings()
                edgeRings.addAll(minEdgeRings)
            }
        }
        return edgeRings
    }

    /**
     * Mark all the edges for the edgeRings corresponding to the shells
     * of the input polygons.
     * Only ONE ring gets marked for each shell - if there are others which remain unmarked
     * this indicates a disconnected interior.
     */
    private fun visitShellInteriors(g: Geometry, graph: PlanarGraph) {
        if (g is Polygon) {
            visitInteriorRing(g.exteriorRing, graph)
        }
        if (g is MultiPolygon) {
            for (i in 0 until g.numGeometries) {
                val p = g.getGeometryN(i) as Polygon
                visitInteriorRing(p.exteriorRing, graph)
            }
        }
    }

    private fun visitInteriorRing(ring: LineString?, graph: PlanarGraph) {
        if (ring!!.isEmpty) return
        val pts = ring.coordinates
        val pt0 = pts[0]

        /**
         * Find first point in coord list different to initial point.
         * Need special check since the first point may be repeated.
         */
        val pt1 = findDifferentPoint(pts, pt0)
        val e = graph.findEdgeInSameDirection(pt0, pt1!!)
        val de = graph.findEdgeEnd(e!!) as DirectedEdge?
        var intDe: DirectedEdge? = null
        if (de!!.label!!.getLocation(0, Position.RIGHT) == Location.INTERIOR) {
            intDe = de
        } else if (de.sym!!.label!!.getLocation(0, Position.RIGHT) == Location.INTERIOR) {
            intDe = de.sym
        }
        isTrue(intDe != null, "unable to find dirEdge with Interior on RHS")
        visitLinkedDirectedEdges(intDe)
    }

    protected fun visitLinkedDirectedEdges(start: DirectedEdge?) {
        var de = start
        do {
            isTrue(de != null, "found null Directed Edge")
            de!!.isVisited = true
            de = de.next
        } while (de != start)
    }

    /**
     * Check if any shell ring has an unvisited edge.
     * A shell ring is a ring which is not a hole and which has the interior
     * of the parent area on the RHS.
     * (Note that there may be non-hole rings with the interior on the LHS,
     * since the interior of holes will also be polygonized into CW rings
     * by the linkAllDirectedEdges() step)
     *
     * @return true if there is an unvisited edge in a non-hole ring
     */
    private fun hasUnvisitedShellEdge(edgeRings: List<*>): Boolean {
        for (i in edgeRings.indices) {
            val er = edgeRings[i] as EdgeRing
            // don't check hole rings
            if (er.isHole) continue
            val edges = er.getEdges()
            var de = edges[0] as DirectedEdge
            // don't check CW rings which are holes
            // (MD - this check may now be irrelevant)
            if (de.label!!.getLocation(0, Position.RIGHT) != Location.INTERIOR) continue
            /**
             * the edgeRing is CW ring which surrounds the INT of the area, so check all
             * edges have been visited.  If any are unvisited, this is a disconnected part of the interior
             */
            for (j in edges.indices) {
                de = edges[j] as DirectedEdge
                //Debug.print("visted? "); Debug.println(de);
                if (!de.isVisited) {
//Debug.print("not visited "); Debug.println(de);
                    coordinate = de.coordinate
                    return true
                }
            }
        }
        return false
    }

    companion object {
        fun findDifferentPoint(coord: Array<Coordinate>, pt: Coordinate?): Coordinate? {
            for (i in coord.indices) {
                if (coord[i] != pt) return coord[i]
            }
            return null
        }
    }
}