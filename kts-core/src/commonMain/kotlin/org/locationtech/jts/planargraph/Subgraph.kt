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
package org.locationtech.jts.planargraph

/**
 * A subgraph of a [PlanarGraph].
 * A subgraph may contain any subset of [Edge]s
 * from the parent graph.
 * It will also automatically contain all [DirectedEdge]s
 * and [Node]s associated with those edges.
 * No new objects are created when edges are added -
 * all associated components must already exist in the parent graph.
 */
class Subgraph
/**
 * Creates a new subgraph of the given [PlanarGraph]
 *
 * @param parentGraph the parent graph
 */(
    /**
     * Gets the [PlanarGraph] which this subgraph
     * is part of.
     *
     * @return the parent PlanarGraph
     */
    var parent: PlanarGraph
) {
    protected var edges: MutableSet<Edge> = HashSet()
    protected var dirEdges: MutableList<DirectedEdge> = ArrayList()
    protected var nodeMap = NodeMap()

    /**
     * Adds an [Edge] to the subgraph.
     * The associated [DirectedEdge]s and [Node]s
     * are also added.
     *
     * @param e the edge to add
     */
    fun add(e: Edge) {
        if (edges.contains(e)) return
        edges.add(e)
        dirEdges.add(e.getDirEdge(0))
        dirEdges.add(e.getDirEdge(1))
        nodeMap.add(e.getDirEdge(0).fromNode)
        nodeMap.add(e.getDirEdge(1).fromNode)
    }

    /**
     * Returns an [Iterator] over the [DirectedEdge]s in this graph,
     * in the order in which they were added.
     *
     * @return an iterator over the directed edges
     *
     * @see .add
     */
    fun dirEdgeIterator(): Iterator<*> {
        return dirEdges.iterator()
    }

    /**
     * Returns an [Iterator] over the [Edge]s in this graph,
     * in the order in which they were added.
     *
     * @return an iterator over the edges
     *
     * @see .add
     */
    fun edgeIterator(): Iterator<*> {
        return edges.iterator()
    }

    /**
     * Returns an [Iterator] over the [Node]s in this graph.
     * @return an iterator over the nodes
     */
    fun nodeIterator(): Iterator<*> {
        return nodeMap.iterator()
    }

    /**
     * Tests whether an [Edge] is contained in this subgraph
     * @param e the edge to test
     * @return `true` if the edge is contained in this subgraph
     */
    operator fun contains(e: Edge?): Boolean {
        return edges.contains(e)
    }
}