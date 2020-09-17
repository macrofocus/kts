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
package org.locationtech.jts.geomgraph

import org.locationtech.jts.legacy.map.TreeMap
import org.locationtech.jts.noding.OrientedCoordinateArray

/**
 * A EdgeList is a list of Edges.  It supports locating edges
 * that are pointwise equals to a target edge.
 * @version 1.7
 */
class EdgeList {
    private val edges: MutableList<Edge> = ArrayList()

    /**
     * An index of the edges, for fast lookup.
     *
     */
    private val ocaMap: MutableMap<OrientedCoordinateArray, Any?> = TreeMap()

    /**
     * Insert an edge unless it is already in the list
     */
    fun add(e: Edge) {
        edges.add(e)
        val oca = OrientedCoordinateArray(e.getCoordinates())
        ocaMap[oca] = e
    }

    fun addAll(edgeColl: Collection<*>) {
        val i = edgeColl.iterator()
        while (i.hasNext()) {
            add(i.next() as Edge)
        }
    }

    fun getEdges(): List<Any?> {
        return edges
    }

    /**
     * If there is an edge equal to e already in the list, return it.
     * Otherwise return null.
     * @return  equal edge, if there is one already in the list
     * null otherwise
     */
    fun findEqualEdge(e: Edge): Edge? {
        val oca = OrientedCoordinateArray(e.getCoordinates())
        // will return null if no edge matches
        return ocaMap[oca] as Edge?
    }

    operator fun iterator(): MutableListIterator<Edge> {
        return edges.listIterator()
    }

    operator fun get(i: Int): Edge {
        return edges[i]
    }

    /**
     * If the edge e is already in the list, return its index.
     * @return  index, if e is already in the list
     * -1 otherwise
     */
    fun findEdgeIndex(e: Edge?): Int {
        for (i in edges.indices) {
            if ((edges[i]) == e) return i
        }
        return -1
    }

//    fun print(out: PrintStream) {
//        out.print("MULTILINESTRING ( ")
//        for (j in edges.indices) {
//            val e = edges[j]
//            if (j > 0) out.print(",")
//            out.print("(")
//            val pts = e.getCoordinates()
//            for (i in pts.indices) {
//                if (i > 0) out.print(",")
//                out.print(pts[i].x.toString() + " " + pts[i].y)
//            }
//            out.println(")")
//        }
//        out.print(")  ")
//    }
}