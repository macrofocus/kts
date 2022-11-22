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

import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryComponentFilter
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.planargraph.GraphComponent
import org.locationtech.jts.planargraph.Node
import org.locationtech.jts.util.Assert

/**
 * Merges a collection of linear components to form maximal-length linestrings.
 *
 * Merging stops at nodes of degree 1 or degree 3 or more.
 * In other words, all nodes of degree 2 are merged together.
 * The exception is in the case of an isolated loop, which only has degree-2 nodes.
 * In this case one of the nodes is chosen as a starting point.
 *
 * The direction of each
 * merged LineString will be that of the majority of the LineStrings from which it
 * was derived.
 *
 * Any dimension of Geometry is handled - the constituent linework is extracted to
 * form the edges. The edges must be correctly noded; that is, they must only meet
 * at their endpoints.  The LineMerger will accept non-noded input
 * but will not merge non-noded edges.
 *
 * Input lines which are empty or contain only a single unique coordinate are not included
 * in the merging.
 *
 * @version 1.7
 */
class LineMerger
/**
 * Creates a new line merger.
 *
 */
{
    private val graph: LineMergeGraph =
        LineMergeGraph()
    private var mergedLineStrings: MutableCollection<LineString>? = null
    private var factory: GeometryFactory? = null

    /**
     * Adds a Geometry to be processed. May be called multiple times.
     * Any dimension of Geometry may be added; the constituent linework will be
     * extracted.
     *
     * @param geometry geometry to be line-merged
     */
    fun add(geometry: Geometry) {
        geometry.apply(object : GeometryComponentFilter {
            override fun filter(component: Geometry) {
                if (component is LineString) {
                    add(component)
                }
            }
        })
    }

    /**
     * Adds a collection of Geometries to be processed. May be called multiple times.
     * Any dimension of Geometry may be added; the constituent linework will be
     * extracted.
     *
     * @param geometries the geometries to be line-merged
     */
    fun add(geometries: Collection<*>) {
        mergedLineStrings = null
        val i = geometries.iterator()
        while (i.hasNext()) {
            val geometry = i.next() as Geometry
            add(geometry)
        }
    }

    private fun add(lineString: LineString) {
        if (factory == null) {
            factory = lineString.factory
        }
        graph.addEdge(lineString)
    }

    private var edgeStrings: MutableCollection<EdgeString>? = null
    private fun merge() {
        if (mergedLineStrings != null) {
            return
        }

        // reset marks (this allows incremental processing)
        GraphComponent.setMarked(graph.nodeIterator(), false)
        GraphComponent.setMarked(graph.edgeIterator(), false)
        edgeStrings = ArrayList()
        buildEdgeStringsForObviousStartNodes()
        buildEdgeStringsForIsolatedLoops()
        mergedLineStrings = ArrayList()
        val i: Iterator<*> = edgeStrings!!.iterator()
        while (i.hasNext()) {
            val edgeString: EdgeString =
                i.next() as EdgeString
            mergedLineStrings!!.add(edgeString.toLineString())
        }
    }

    private fun buildEdgeStringsForObviousStartNodes() {
        buildEdgeStringsForNonDegree2Nodes()
    }

    private fun buildEdgeStringsForIsolatedLoops() {
        buildEdgeStringsForUnprocessedNodes()
    }

    private fun buildEdgeStringsForUnprocessedNodes() {
        val i: Iterator<*> = graph.nodes.iterator()
        while (i.hasNext()) {
            val node: Node = i.next() as Node
            if (!node.isMarked) {
                Assert.isTrue(node.degree == 2)
                buildEdgeStringsStartingAt(node)
                node.isMarked = true
            }
        }
    }

    private fun buildEdgeStringsForNonDegree2Nodes() {
        val i: Iterator<*> = graph.nodes.iterator()
        while (i.hasNext()) {
            val node: Node = i.next() as Node
            if (node.degree != 2) {
                buildEdgeStringsStartingAt(node)
                node.isMarked = true
            }
        }
    }

    private fun buildEdgeStringsStartingAt(node: Node) {
        val i: Iterator<*> = node.outEdges.iterator()
        while (i.hasNext()) {
            val directedEdge: LineMergeDirectedEdge =
                i.next() as LineMergeDirectedEdge
            if (directedEdge.edge!!.isMarked) {
                continue
            }
            edgeStrings!!.add(buildEdgeStringStartingWith(directedEdge))
        }
    }

    private fun buildEdgeStringStartingWith(start: LineMergeDirectedEdge): EdgeString {
        val edgeString: EdgeString =
            EdgeString(factory!!)
        var current: LineMergeDirectedEdge? = start
        do {
            edgeString.add(current!!)
            current.edge!!.isMarked = true
            current = current.next
        } while (current != null && current !== start)
        return edgeString
    }

    /**
     * Gets the [LineString]s created by the merging process.
     *
     * @return the collection of merged LineStrings
     */
    fun getMergedLineStrings(): Collection<*>? {
        merge()
        return mergedLineStrings
    }
}