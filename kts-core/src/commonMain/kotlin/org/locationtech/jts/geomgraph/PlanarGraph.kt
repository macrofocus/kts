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

import org.locationtech.jts.algorithm.Orientation
import org.locationtech.jts.algorithm.Orientation.index
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Location
import org.locationtech.jts.geom.Quadrant.quadrant
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * @version 1.7
 */
/**
 * The computation of the `IntersectionMatrix` relies on the use of a structure
 * called a "topology graph".  The topology graph contains nodes and edges
 * corresponding to the nodes and line segments of a `Geometry`. Each
 * node and edge in the graph is labeled with its topological location relative to
 * the source geometry.
 * <P>
 * Note that there is no requirement that points of self-intersection be a vertex.
 * Thus to obtain a correct topology graph, `Geometry`s must be
 * self-noded before constructing their graphs.
</P> * <P>
 * Two fundamental operations are supported by topology graphs:
</P> * <UL>
 * <LI>Computing the intersections between all the edges and nodes of a single graph
</LI> * <LI>Computing the intersections between the edges and nodes of two different graphs
</LI></UL> *
 *
 * @version 1.7
 */
open class PlanarGraph {
    @JvmField
    protected var edges: MutableList<Any?> = ArrayList()

    @JvmField
    var nodes: NodeMap
    protected var edgeEndList: MutableList<Any?> = ArrayList()

    constructor(nodeFact: NodeFactory?) {
        nodes = NodeMap(nodeFact!!)
    }

    constructor() {
        nodes = NodeMap(NodeFactory())
    }

    val edgeIterator: Iterator<*>
        get() = edges.iterator()
    val edgeEnds: Collection<*>
        get() = edgeEndList

    fun isBoundaryNode(geomIndex: Int, coord: Coordinate?): Boolean {
        val node = nodes.find(coord) ?: return false
        val label = node.label
        return label != null && label.getLocation(geomIndex) == Location.BOUNDARY
    }

    protected fun insertEdge(e: Edge?) {
        edges.add(e)
    }

    fun add(e: EdgeEnd?) {
        nodes.add(e!!)
        edgeEndList.add(e)
    }

    val nodeIterator: Iterator<*>
        get() = nodes.iterator()

    fun getNodes(): Collection<*> {
        return nodes.values()
    }

    fun addNode(node: Node?): Node {
        return nodes.addNode(node!!)
    }

    fun addNode(coord: Coordinate): Node {
        return nodes.addNode(coord)
    }

    /**
     * @return the node if found; null otherwise
     */
    fun find(coord: Coordinate?): Node? {
        return nodes.find(coord)
    }

    /**
     * Add a set of edges to the graph.  For each edge two DirectedEdges
     * will be created.  DirectedEdges are NOT linked by this method.
     */
    fun addEdges(edgesToAdd: List<*>) {
        // create all the nodes for the edges
        val it = edgesToAdd.iterator()
        while (it.hasNext()) {
            val e = it.next() as Edge
            edges.add(e)
            val de1 = DirectedEdge(e, true)
            val de2 = DirectedEdge(e, false)
            de1.sym = de2
            de2.sym = de1
            add(de1)
            add(de2)
        }
    }

    /**
     * Link the DirectedEdges at the nodes of the graph.
     * This allows clients to link only a subset of nodes in the graph, for
     * efficiency (because they know that only a subset is of interest).
     */
    fun linkResultDirectedEdges() {
        val nodeit = nodes.iterator()
        while (nodeit.hasNext()) {
            val node = nodeit.next() as Node
            (node.edges as DirectedEdgeStar).linkResultDirectedEdges()
        }
    }

    /**
     * Link the DirectedEdges at the nodes of the graph.
     * This allows clients to link only a subset of nodes in the graph, for
     * efficiency (because they know that only a subset is of interest).
     */
    fun linkAllDirectedEdges() {
        val nodeit = nodes.iterator()
        while (nodeit.hasNext()) {
            val node = nodeit.next() as Node
            (node.edges as DirectedEdgeStar).linkAllDirectedEdges()
        }
    }

    /**
     * Returns the EdgeEnd which has edge e as its base edge
     * (MD 18 Feb 2002 - this should return a pair of edges)
     *
     * @return the edge, if found
     * `null` if the edge was not found
     */
    fun findEdgeEnd(e: Edge): EdgeEnd? {
        val i = edgeEnds.iterator()
        while (i.hasNext()) {
            val ee = i.next() as EdgeEnd
            if (ee.edge === e) return ee
        }
        return null
    }

    /**
     * Returns the edge whose first two coordinates are p0 and p1
     *
     * @return the edge, if found
     * `null` if the edge was not found
     */
    fun findEdge(p0: Coordinate, p1: Coordinate): Edge? {
        for (i in edges.indices) {
            val e = edges[i] as Edge
            val eCoord = e.getCoordinates()
            if (p0 == eCoord[0] && p1 == eCoord[1]) return e
        }
        return null
    }

    /**
     * Returns the edge which starts at p0 and whose first segment is
     * parallel to p1
     *
     * @return the edge, if found
     * `null` if the edge was not found
     */
    fun findEdgeInSameDirection(p0: Coordinate, p1: Coordinate): Edge? {
        for (i in edges.indices) {
            val e = edges[i] as Edge
            val eCoord = e.getCoordinates()
            if (matchInSameDirection(p0, p1, eCoord[0], eCoord[1])) return e
            if (matchInSameDirection(p0, p1, eCoord[eCoord.size - 1], eCoord[eCoord.size - 2])) return e
        }
        return null
    }

    /**
     * The coordinate pairs match if they define line segments lying in the same direction.
     * E.g. the segments are parallel and in the same quadrant
     * (as opposed to parallel and opposite!).
     */
    private fun matchInSameDirection(p0: Coordinate, p1: Coordinate, ep0: Coordinate, ep1: Coordinate): Boolean {
        if (p0 != ep0) return false
        return (index(
            p0,
            p1,
            ep1
        ) == Orientation.COLLINEAR
                && quadrant(p0, p1) == quadrant(ep0, ep1))
    }

//    fun printEdges(out: PrintStream) {
//        out.println("Edges:")
//        for (i in edges.indices) {
//            out.println("edge $i:")
//            val e = edges[i] as Edge
//            e.print(out)
//            e.eiList.print(out)
//        }
//    }

    fun debugPrint(o: Any?) {
        print(o)
    }

    fun debugPrintln(o: Any?) {
        println(o)
    }

    companion object {
        /**
         * For nodes in the Collection, link the DirectedEdges at the node that are in the result.
         * This allows clients to link only a subset of nodes in the graph, for
         * efficiency (because they know that only a subset is of interest).
         */
        @JvmStatic
        fun linkResultDirectedEdges(nodes: Collection<Any?>) {
            val nodeit = nodes.iterator()
            while (nodeit.hasNext()) {
                val node = nodeit.next() as Node
                (node.edges as DirectedEdgeStar).linkResultDirectedEdges()
            }
        }
    }
}