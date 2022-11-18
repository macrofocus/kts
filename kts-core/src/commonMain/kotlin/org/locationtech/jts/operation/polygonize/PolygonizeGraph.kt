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
package org.locationtech.jts.operation.polygonize

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.CoordinateArrays
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.legacy.Stack
import org.locationtech.jts.legacy.pop
import org.locationtech.jts.legacy.push
import org.locationtech.jts.planargraph.*
import org.locationtech.jts.util.Assert

/**
 * Represents a planar graph of edges that can be used to compute a
 * polygonization, and implements the algorithms to compute the
 * [EdgeRing]s formed by the graph.
 *
 * The marked flag on [DirectedEdge]s is used to indicate that a directed edge
 * has be logically deleted from the graph.
 *
 * @version 1.7
 */
class PolygonizeGraph
/**
 * Create a new polygonization graph.
 */(private val factory: GeometryFactory) : PlanarGraph() {
    //private List labelledRings;
    /**
     * Add a [LineString] forming an edge of the polygon graph.
     * @param line the line to add
     */
    fun addEdge(line: LineString) {
        if (line.isEmpty) {
            return
        }
        val linePts = CoordinateArrays.removeRepeatedPoints(line.coordinates)
        if (linePts.size < 2) {
            return
        }
        val startPt = linePts[0]
        val endPt = linePts[linePts.size - 1]
        val nStart: Node = getNode(startPt)!!
        val nEnd: Node = getNode(endPt)!!
        val de0: DirectedEdge =
            PolygonizeDirectedEdge(nStart, nEnd, linePts[1], true)
        val de1: DirectedEdge = PolygonizeDirectedEdge(
            nEnd,
            nStart,
            linePts[linePts.size - 2],
            false
        )
        val edge: Edge = PolygonizeEdge(line)
        edge.setDirectedEdges(de0, de1)
        add(edge)
    }

    private fun getNode(pt: Coordinate): Node? {
        var node: Node? = findNode(pt)
        if (node == null) {
            node = Node(pt)
            // ensure node is only added once to graph
            add(node)
        }
        return node
    }

    private fun computeNextCWEdges() {
        // set the next pointers for the edges around each node
        val iNode: Iterator<*> = nodeIterator()
        while (iNode.hasNext()) {
            val node: Node = iNode.next() as Node
            computeNextCWEdges(node)
        }
    }

    /**
     * Convert the maximal edge rings found by the initial graph traversal
     * into the minimal edge rings required by JTS polygon topology rules.
     *
     * @param ringEdges the list of start edges for the edgeRings to convert.
     */
    private fun convertMaximalToMinimalEdgeRings(ringEdges: MutableList<DirectedEdge>) {
        val i: Iterator<*> = ringEdges.iterator()
        while (i.hasNext()) {
            val de: PolygonizeDirectedEdge =
                i.next() as PolygonizeDirectedEdge
            val label: Long = de.label
            val intNodes: MutableList<Node> = findIntersectionNodes(de, label)
                ?: continue
            // flip the next pointers on the intersection nodes to create minimal edge rings
            val iNode: Iterator<*> = intNodes.iterator()
            while (iNode.hasNext()) {
                val node: Node = iNode.next() as Node
                computeNextCCWEdges(node, label)
            }
        }
    }// maybe could optimize this, since most of these pointers should be set correctly already
    // by deleteCutEdges()
    // clear labels of all edges in graph

    // find all edgerings (which will now be minimal ones, as required)
    /**
     * Computes the minimal EdgeRings formed by the edges in this graph.
     * @return a list of the [EdgeRing]s found by the polygonization process.
     */
    val edgeRings: MutableList<EdgeRing>
        get() {
            // maybe could optimize this, since most of these pointers should be set correctly already
            // by deleteCutEdges()
            computeNextCWEdges()
            // clear labels of all edges in graph
            label(dirEdges, -1)
            val maximalRings: MutableList<DirectedEdge> = findLabeledEdgeRings(dirEdges)
            convertMaximalToMinimalEdgeRings(maximalRings)

            // find all edgerings (which will now be minimal ones, as required)
            val edgeRingList: MutableList<EdgeRing> = ArrayList()
            val i: Iterator<*> = dirEdges.iterator()
            while (i.hasNext()) {
                val de: PolygonizeDirectedEdge =
                    i.next() as PolygonizeDirectedEdge
                if (de.isMarked) {
                    continue
                }
                if (de.isInRing) {
                    continue
                }
                val er: EdgeRing = findEdgeRing(de)
                edgeRingList.add(er)
            }
            return edgeRingList
        }

    /**
     * Finds and removes all cut edges from the graph.
     * @return a list of the [LineString]s forming the removed cut edges
     */
    fun deleteCutEdges(): MutableList<LineString> {
        computeNextCWEdges()
        // label the current set of edgerings
        findLabeledEdgeRings(dirEdges)
        /**
         * Cut Edges are edges where both dirEdges have the same label.
         * Delete them, and record them
         */
        val cutLines: MutableList<LineString> = ArrayList()
        val i: Iterator<*> = dirEdges.iterator()
        while (i.hasNext()) {
            val de: PolygonizeDirectedEdge =
                i.next() as PolygonizeDirectedEdge
            if (de.isMarked) {
                continue
            }
            val sym: PolygonizeDirectedEdge =
                de.sym as PolygonizeDirectedEdge
            if (de.label == sym.label) {
                de.isMarked = true
                sym.isMarked = true

                // save the line as a cut edge
                val e: PolygonizeEdge =
                    de.edge as PolygonizeEdge
                cutLines.add(e.line)
            }
        }
        return cutLines
    }

    private fun findEdgeRing(startDE: PolygonizeDirectedEdge): EdgeRing {
        val er: EdgeRing = EdgeRing(
            factory
        )
        er.build(startDE)
        return er
    }

    /**
     * Marks all edges from the graph which are "dangles".
     * Dangles are which are incident on a node with degree 1.
     * This process is recursive, since removing a dangling edge
     * may result in another edge becoming a dangle.
     * In order to handle large recursion depths efficiently,
     * an explicit recursion stack is used
     *
     * @return a List containing the [LineString]s that formed dangles
     */
    fun deleteDangles(): Collection<LineString> {
        val nodesToRemove: MutableList<Node> = findNodesOfDegree(1)
        val dangleLines: MutableSet<LineString> = HashSet()
        val nodeStack: Stack<Node> = ArrayList()
        val i: Iterator<Node> = nodesToRemove.iterator()
        while (i.hasNext()) {
            nodeStack.push(i.next())
        }
        while (!nodeStack.isEmpty()) {
            val node: Node = nodeStack.pop() as Node
            deleteAllEdges(node)
            val nodeOutEdges: List<DirectedEdge> = node.outEdges.edges
            val i: Iterator<*> = nodeOutEdges.iterator()
            while (i.hasNext()) {
                val de: PolygonizeDirectedEdge =
                    i.next() as PolygonizeDirectedEdge
                // delete this edge and its sym
                de.isMarked = true
                val sym: PolygonizeDirectedEdge =
                    de.sym as PolygonizeDirectedEdge
                if (sym != null) sym.isMarked = true

                // save the line as a dangle
                val e: PolygonizeEdge =
                    de.edge as PolygonizeEdge
                dangleLines.add(e.line)
                val toNode: Node = de.toNode
                // add the toNode to the list to be processed, if it is now a dangle
                if (getDegreeNonDeleted(toNode) == 1) nodeStack.push(toNode)
            }
        }
        return dangleLines
    }

    /**
     * Traverses the polygonized edge rings in the graph
     * and computes the depth parity (odd or even)
     * relative to the exterior of the graph.
     * If the client has requested that the output
     * be polygonally valid, only odd polygons will be constructed.
     *
     */
    fun computeDepthParity() {
        while (true) {
            val de = null ?: return //findLowestDirEdge();
            computeDepthParity(de)
        }
    }

    /**
     * Traverses all connected edges, computing the depth parity
     * of the associated polygons.
     *
     * @param de
     */
    private fun computeDepthParity(de: PolygonizeDirectedEdge) {}

    companion object {
        private fun getDegreeNonDeleted(node: Node): Int {
            val edges: List<DirectedEdge> = node.outEdges.edges
            var degree = 0
            val i: Iterator<DirectedEdge> = edges.iterator()
            while (i.hasNext()) {
                if (!i.next().isMarked) degree++
            }
            return degree
        }

        private fun getDegree(node: Node, label: Long): Int {
            val edges: List<DirectedEdge> = node.outEdges.edges
            var degree = 0
            val i: Iterator<DirectedEdge> = edges.iterator()
            while (i.hasNext()) {
                val de = i.next() as PolygonizeDirectedEdge
                if (de.label == label) degree++
            }
            return degree
        }

        /**
         * Deletes all edges at a node
         */
        fun deleteAllEdges(node: Node) {
            val edges: List<DirectedEdge> = node.outEdges.edges
            val i: Iterator<DirectedEdge> = edges.iterator()
            while (i.hasNext()) {
                val de: PolygonizeDirectedEdge = i.next() as PolygonizeDirectedEdge
                de.isMarked = true
                val sym: PolygonizeDirectedEdge =
                    de.sym as PolygonizeDirectedEdge
                if (sym != null) sym.isMarked = true
            }
        }

        /**
         * Finds all nodes in a maximal edgering which are self-intersection nodes
         * @param startDE
         * @param label
         * @return the list of intersection nodes found,
         * or `null` if no intersection nodes were found
         */
        private fun findIntersectionNodes(
            startDE: PolygonizeDirectedEdge,
            label: Long
        ): MutableList<Node>? {
            var de: PolygonizeDirectedEdge = startDE
            var intNodes: MutableList<Node>? = null
            do {
                val node: Node = de.fromNode
                if (getDegree(node, label) > 1) {
                    if (intNodes == null) intNodes = ArrayList<Node>()
                    intNodes.add(node)
                }
                de = de.next!!
                Assert.isTrue(de != null, "found null DE in ring")
                Assert.isTrue(de === startDE || !de!!.isInRing, "found DE already in ring")
            } while (de !== startDE)
            return intNodes
        }

        /**
         * Finds and labels all edgerings in the graph.
         * The edge rings are labeling with unique integers.
         * The labeling allows detecting cut edges.
         *
         * @param dirEdges a List of the DirectedEdges in the graph
         * @return a List of DirectedEdges, one for each edge ring found
         */
        private fun findLabeledEdgeRings(dirEdges: Collection<*>): MutableList<DirectedEdge> {
            val edgeRingStarts: MutableList<DirectedEdge> = ArrayList()
            // label the edge rings formed
            var currLabel: Long = 1
            val i = dirEdges.iterator()
            while (i.hasNext()) {
                val de: PolygonizeDirectedEdge =
                    i.next() as PolygonizeDirectedEdge
                if (de.isMarked) {
                    continue
                }
                if (de.label >= 0) {
                    continue
                }
                edgeRingStarts.add(de)
                val edges: MutableList<DirectedEdge> =
                    EdgeRing.findDirEdgesInRing(de)
                label(edges, currLabel)
                currLabel++
            }
            return edgeRingStarts
        }

        private fun label(dirEdges: Collection<*>, label: Long) {
            val i = dirEdges.iterator()
            while (i.hasNext()) {
                val de: PolygonizeDirectedEdge =
                    i.next() as PolygonizeDirectedEdge
                de.label = label
            }
        }

        private fun computeNextCWEdges(node: Node) {
            val deStar: DirectedEdgeStar = node.outEdges
            var startDE: PolygonizeDirectedEdge? = null
            var prevDE: PolygonizeDirectedEdge? = null

            // the edges are stored in CCW order around the star
            val i: Iterator<*> = deStar.edges.iterator()
            while (i.hasNext()) {
                val outDE: PolygonizeDirectedEdge =
                    i.next() as PolygonizeDirectedEdge
                if (outDE.isMarked) {
                    continue
                }
                if (startDE == null) startDE = outDE
                if (prevDE != null) {
                    val sym: PolygonizeDirectedEdge =
                        prevDE.sym as PolygonizeDirectedEdge
                    sym.next = outDE
                }
                prevDE = outDE
            }
            if (prevDE != null) {
                val sym: PolygonizeDirectedEdge =
                    prevDE.sym as PolygonizeDirectedEdge
                sym.next = startDE
            }
        }

        /**
         * Computes the next edge pointers going CCW around the given node, for the
         * given edgering label.
         * This algorithm has the effect of converting maximal edgerings into minimal edgerings
         */
        private fun computeNextCCWEdges(node: Node, label: Long) {
            val deStar: DirectedEdgeStar = node.outEdges
            //PolyDirectedEdge lastInDE = null;
            var firstOutDE: PolygonizeDirectedEdge? = null
            var prevInDE: PolygonizeDirectedEdge? = null

            // the edges are stored in CCW order around the star
            val edges: List<DirectedEdge> = deStar.edges
            //for (Iterator i = deStar.getEdges().iterator(); i.hasNext(); ) {
            for (i in edges.indices.reversed()) {
                val de: PolygonizeDirectedEdge = edges[i] as PolygonizeDirectedEdge
                val sym: PolygonizeDirectedEdge =
                    de.sym as PolygonizeDirectedEdge
                var outDE: PolygonizeDirectedEdge? = null
                if (de.label == label) outDE = de
                var inDE: PolygonizeDirectedEdge? = null
                if (sym.label == label) inDE = sym
                if (outDE == null && inDE == null) continue  // this edge is not in edgering
                if (inDE != null) {
                    prevInDE = inDE
                }
                if (outDE != null) {
                    if (prevInDE != null) {
                        prevInDE.next = outDE
                        prevInDE = null
                    }
                    if (firstOutDE == null) firstOutDE = outDE
                }
            }
            if (prevInDE != null) {
                Assert.isTrue(firstOutDE != null)
                prevInDE.next = firstOutDE
            }
        }
    }
}