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
package org.locationtech.jts.operation.linemerge

import org.locationtech.jts.geom.*
import org.locationtech.jts.legacy.LinkedList
import org.locationtech.jts.legacy.TreeSet
import org.locationtech.jts.planargraph.DirectedEdge
import org.locationtech.jts.planargraph.GraphComponent
import org.locationtech.jts.planargraph.Node
import org.locationtech.jts.planargraph.Subgraph
import org.locationtech.jts.planargraph.algorithm.ConnectedSubgraphFinder
import org.locationtech.jts.util.Assert

/**
 * Builds a sequence from a set of LineStrings so that
 * they are ordered end to end.
 * A sequence is a complete non-repeating list of the linear
 * components of the input.  Each linestring is oriented
 * so that identical endpoints are adjacent in the list.
 *
 * A typical use case is to convert a set of
 * unoriented geometric links
 * from a linear network
 * (e.g. such as block faces on a bus route)
 * into a continuous oriented path through the network.
 *
 * The input linestrings may form one or more connected sets.
 * The input linestrings should be correctly noded, or the results may
 * not be what is expected.
 * The computed output is a single [MultiLineString] containing the ordered
 * linestrings in the sequence.
 *
 * The sequencing employs the classic **Eulerian path** graph algorithm.
 * Since Eulerian paths are not uniquely determined,
 * further rules are used to
 * make the computed sequence preserve as much as possible of the input
 * ordering.
 * Within a connected subset of lines, the ordering rules are:
 *
 *  * If there is degree-1 node which is the start
 * node of an linestring, use that node as the start of the sequence
 *  * If there is a degree-1 node which is the end
 * node of an linestring, use that node as the end of the sequence
 *  * If the sequence has no degree-1 nodes, use any node as the start
 *
 * Note that not all arrangements of lines can be sequenced.
 * For a connected set of edges in a graph,
 * *Euler's Theorem* states that there is a sequence containing each edge once
 * **if and only if** there are no more than 2 nodes of odd degree.
 * If it is not possible to find a sequence, the [.isSequenceable] method
 * will return `false`.
 *
 * @version 1.7
 */
class LineSequencer {
    private val graph: LineMergeGraph =
        LineMergeGraph()

    // initialize with default, in case no lines are input
    private var factory: GeometryFactory? = GeometryFactory()
    private var lineCount = 0
    private var isRun = false
    private var sequencedGeometry: Geometry? = null
    private var isSequenceable = false

    /**
     * Adds a [Collection] of [Geometry]s to be sequenced.
     * May be called multiple times.
     * Any dimension of Geometry may be added; the constituent linework will be
     * extracted.
     *
     * @param geometries a Collection of geometries to add
     */
    fun add(geometries: Collection<*>) {
        val i = geometries.iterator()
        while (i.hasNext()) {
            val geometry = i.next() as Geometry
            add(geometry)
        }
    }

    /**
     * Adds a [Geometry] to be sequenced.
     * May be called multiple times.
     * Any dimension of Geometry may be added; the constituent linework will be
     * extracted.
     *
     * @param geometry the geometry to add
     */
    fun add(geometry: Geometry) {
        geometry.apply(object : GeometryComponentFilter {
            override fun filter(component: Geometry) {
                if (component is LineString) {
                    addLine(component)
                }
            }
        })
    }

    private fun addLine(lineString: LineString) {
        if (factory == null) {
            factory = lineString.factory
        }
        graph.addEdge(lineString)
        lineCount++
    }

    /**
     * Tests whether the arrangement of linestrings has a valid
     * sequence.
     *
     * @return `true` if a valid sequence exists.
     */
    fun isSequenceable(): Boolean {
        computeSequence()
        return isSequenceable
    }

    /**
     * Returns the [LineString] or [MultiLineString]
     * built by the sequencing process, if one exists.
     *
     * @return the sequenced linestrings,
     * or `null` if a valid sequence does not exist
     */
    val sequencedLineStrings: Geometry?
        get() {
            computeSequence()
            return sequencedGeometry
        }

    private fun computeSequence() {
        if (isRun) {
            return
        }
        isRun = true
        val sequences: MutableList<List<DirectedEdge>> = findSequences() ?: return
        sequencedGeometry = buildSequencedGeometry(sequences)
        isSequenceable = true
        val finalLineCount = sequencedGeometry!!.numGeometries
        Assert.isTrue(lineCount == finalLineCount, "Lines were missing from result")
        Assert.isTrue(
            sequencedGeometry is LineString
                    || sequencedGeometry is MultiLineString,
            "Result is not lineal"
        )
    }

    private fun findSequences(): MutableList<List<DirectedEdge>>? {
        val sequences: MutableList<List<DirectedEdge>> = ArrayList()
        val csFinder = ConnectedSubgraphFinder(graph)
        val subgraphs: MutableList<Subgraph> = csFinder.connectedSubgraphs
        val i: Iterator<*> = subgraphs.iterator()
        while (i.hasNext()) {
            val subgraph: Subgraph = i.next() as Subgraph
            if (hasSequence(subgraph)) {
                val seq: MutableList<DirectedEdge> = findSequence(subgraph)
                sequences.add(seq)
            } else {
                // if any subgraph cannot be sequenced, abort
                return null
            }
        }
        return sequences
    }

    /**
     * Tests whether a complete unique path exists in a graph
     * using Euler's Theorem.
     *
     * @param graph the subgraph containing the edges
     * @return `true` if a sequence exists
     */
    private fun hasSequence(graph: Subgraph): Boolean {
        var oddDegreeCount = 0
        val i: Iterator<*> = graph.nodeIterator()
        while (i.hasNext()) {
            val node: Node = i.next() as Node
            if (node.degree % 2 == 1) oddDegreeCount++
        }
        return oddDegreeCount <= 2
    }

    private fun findSequence(graph: Subgraph): MutableList<DirectedEdge> {
        GraphComponent.setVisited(graph.edgeIterator(), false)
        val startNode: Node? =
            findLowestDegreeNode(graph)
        val startDE: DirectedEdge = startNode!!.outEdges.iterator().next() as DirectedEdge
        val startDESym: DirectedEdge = startDE.sym!!
        val seq: MutableList<DirectedEdge> = LinkedList()
        val lit: MutableListIterator<DirectedEdge> = seq.listIterator()
        addReverseSubpath(startDESym, lit, false)
        while (lit.hasPrevious()) {
            val prev: DirectedEdge = lit.previous() as DirectedEdge
            val unvisitedOutDE: DirectedEdge? =
                findUnvisitedBestOrientedDE(prev.fromNode)
            if (unvisitedOutDE != null) addReverseSubpath(unvisitedOutDE.sym!!, lit, true)
        }
        return orient(seq)
    }

    private fun addReverseSubpath(de: DirectedEdge, lit: MutableListIterator<DirectedEdge>, expectedClosed: Boolean) {
        // trace an unvisited path *backwards* from this de
        var de: DirectedEdge = de
        val endNode: Node = de.toNode
        var fromNode: Node? = null
        while (true) {
            lit.add(de!!.sym!!)
            de.edge!!.isVisited = true
            fromNode = de.fromNode
            val unvisitedOutDE: DirectedEdge = findUnvisitedBestOrientedDE(fromNode)
                ?: break
            // this must terminate, since we are continually marking edges as visited
            de = unvisitedOutDE.sym!!
        }
        if (expectedClosed) {
            // the path should end at the toNode of this de, otherwise we have an error
            Assert.isTrue(fromNode === endNode, "path not contiguous")
        }
    }

    /**
     * Computes a version of the sequence which is optimally
     * oriented relative to the underlying geometry.
     *
     * Heuristics used are:
     *
     *  * If the path has a degree-1 node which is the start
     * node of an linestring, use that node as the start of the sequence
     *  * If the path has a degree-1 node which is the end
     * node of an linestring, use that node as the end of the sequence
     *  * If the sequence has no degree-1 nodes, use any node as the start
     * (NOTE: in this case could orient the sequence according to the majority of the
     * linestring orientations)
     *
     * @param seq a List of DirectedEdges
     * @return a List of DirectedEdges oriented appropriately
     */
    private fun orient(seq: MutableList<DirectedEdge>): MutableList<DirectedEdge> {
        val startEdge: DirectedEdge = seq.get(0) as DirectedEdge
        val endEdge: DirectedEdge = seq.get(seq.size - 1) as DirectedEdge
        val startNode: Node = startEdge.fromNode
        val endNode: Node = endEdge.toNode
        var flipSeq = false
        val hasDegree1Node = (startNode.degree == 1
                || endNode.degree == 1)
        if (hasDegree1Node) {
            var hasObviousStartNode = false

            // test end edge before start edge, to make result stable
            // (ie. if both are good starts, pick the actual start
            if (endEdge.toNode.degree == 1 && !endEdge.edgeDirection) {
                hasObviousStartNode = true
                flipSeq = true
            }
            if (startEdge.fromNode.degree == 1 && startEdge.edgeDirection) {
                hasObviousStartNode = true
                flipSeq = false
            }

            // since there is no obvious start node, use any node of degree 1
            if (!hasObviousStartNode) {
                // check if the start node should actually be the end node
                if (startEdge.fromNode.degree == 1) flipSeq = true
                // if the end node is of degree 1, it is properly the end node
            }
        }


        // if there is no degree 1 node, just use the sequence as is
        // (Could insert heuristic of taking direction of majority of lines as overall direction)
        return if (flipSeq) reverse(seq) else seq
    }

    /**
     * Reverse the sequence.
     * This requires reversing the order of the dirEdges, and flipping
     * each dirEdge as well
     *
     * @param seq a List of DirectedEdges, in sequential order
     * @return the reversed sequence
     */
    private fun reverse(seq: MutableList<DirectedEdge>): MutableList<DirectedEdge> {
        val newSeq: LinkedList<DirectedEdge> = LinkedList()
        val i: Iterator<*> = seq.iterator()
        while (i.hasNext()) {
            val de: DirectedEdge = i.next() as DirectedEdge
            newSeq.add(0, de.sym!!)
        }
        return newSeq
    }

    /**
     * Builds a geometry ([LineString] or [MultiLineString] )
     * representing the sequence.
     *
     * @param sequences a List of Lists of DirectedEdges with
     * LineMergeEdges as their parent edges.
     * @return the sequenced geometry, or `null` if no sequence exists
     */
    private fun buildSequencedGeometry(sequences: MutableList<List<DirectedEdge>>): Geometry {
        val lines: MutableList<Geometry> = ArrayList()
        val i1: Iterator<*> = sequences.iterator()
        while (i1.hasNext()) {
            val seq: List<DirectedEdge> = i1.next() as List<DirectedEdge>
            val i2: Iterator<*> = seq.iterator()
            while (i2.hasNext()) {
                val de: DirectedEdge = i2.next() as DirectedEdge
                val e = de.edge as LineMergeEdge
                val line: LineString = e.line
                var lineToAdd = line
                if (!de.edgeDirection && !line.isClosed) lineToAdd = Companion.reverse(line)
                lines.add(lineToAdd)
            }
        }
        return if (lines.size == 0) factory!!.createMultiLineString(emptyArray()) else factory!!.buildGeometry(lines)
    }

    companion object {
        fun sequence(geom: Geometry): Geometry? {
            val sequencer = LineSequencer()
            sequencer.add(geom)
            return sequencer.sequencedLineStrings
        }

        /**
         * Tests whether a [Geometry] is sequenced correctly.
         * [LineString]s are trivially sequenced.
         * [MultiLineString]s are checked for correct sequencing.
         * Otherwise, `isSequenced` is defined
         * to be `true` for geometries that are not lineal.
         *
         * @param geom the geometry to test
         * @return `true` if the geometry is sequenced or is not lineal
         */
        fun isSequenced(geom: Geometry?): Boolean {
            if (geom !is MultiLineString) {
                return true
            }
            val mls = geom
            // the nodes in all subgraphs which have been completely scanned
            val prevSubgraphNodes: MutableSet<Coordinate> = TreeSet()
            var lastNode: Coordinate? = null
            val currNodes: MutableList<Coordinate> = ArrayList()
            for (i in 0 until mls.numGeometries) {
                val line = mls.getGeometryN(i) as LineString
                val startNode = line.getCoordinateN(0)
                val endNode = line.getCoordinateN(line.numPoints - 1)
                /**
                 * If this linestring is connected to a previous subgraph, geom is not sequenced
                 */
                if (prevSubgraphNodes.contains(startNode)) return false
                if (prevSubgraphNodes.contains(endNode)) return false
                if (lastNode != null) {
                    if (!startNode.equals(lastNode)) {
                        // start new connected sequence
                        prevSubgraphNodes.addAll(currNodes)
                        currNodes.clear()
                    }
                }
                currNodes.add(startNode)
                currNodes.add(endNode)
                lastNode = endNode
            }
            return true
        }

        /**
         * Finds an [DirectedEdge] for an unvisited edge (if any),
         * choosing the dirEdge which preserves orientation, if possible.
         *
         * @param node the node to examine
         * @return the dirEdge found, or `null` if none were unvisited
         */
        private fun findUnvisitedBestOrientedDE(node: Node): DirectedEdge? {
            var wellOrientedDE: DirectedEdge? = null
            var unvisitedDE: DirectedEdge? = null
            val i: Iterator<*> = node.outEdges.iterator()
            while (i.hasNext()) {
                val de: DirectedEdge = i.next() as DirectedEdge
                if (!de.edge!!.isVisited) {
                    unvisitedDE = de
                    if (de.edgeDirection) wellOrientedDE = de
                }
            }
            return if (wellOrientedDE != null) wellOrientedDE else unvisitedDE
        }

        private fun findLowestDegreeNode(graph: Subgraph): Node? {
            var minDegree = Int.MAX_VALUE
            var minDegreeNode: Node? = null
            val i: Iterator<*> = graph.nodeIterator()
            while (i.hasNext()) {
                val node: Node = i.next() as Node
                if (minDegreeNode == null || node.degree < minDegree) {
                    minDegree = node.degree
                    minDegreeNode = node
                }
            }
            return minDegreeNode
        }

        private fun reverse(line: LineString): LineString {
            val pts = line.coordinates
            val revPts = arrayOfNulls<Coordinate>(pts.size)
            val len = pts.size
            for (i in 0 until len) {
                revPts[len - 1 - i] = Coordinate(pts[i])
            }
            return line.factory.createLineString(revPts.requireNoNulls())
        }
    }
}