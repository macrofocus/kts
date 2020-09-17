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
package org.locationtech.jts.planargraph

import org.locationtech.jts.geom.Coordinate

/**
 * Represents a directed graph which is embeddable in a planar surface.
 *
 * This class and the other classes in this package serve as a framework for
 * building planar graphs for specific algorithms. This class must be
 * subclassed to expose appropriate methods to construct the graph. This allows
 * controlling the types of graph components ([DirectedEdge]s,
 * [Edge]s and [Node]s) which can be added to the graph. An
 * application which uses the graph framework will almost always provide
 * subclasses for one or more graph components, which hold application-specific
 * data and graph algorithms.
 *
 * @version 1.7
 */
abstract class PlanarGraph
/**
 * Constructs a empty graph.
 */
{
    protected var edges: MutableSet<Any?> = HashSet()
    protected var dirEdges: MutableSet<Any?> = HashSet()
    protected var nodeMap = NodeMap()

    /**
     * Returns the [Node] at the given location,
     * or null if no [Node] was there.
     *
     * @param pt the location to query
     * @return the node found
     * or `null` if this graph contains no node at the location
     */
    fun findNode(pt: Coordinate?): Node? {
        return nodeMap.find(pt) as Node
    }

    /**
     * Adds a node to the map, replacing any that is already at that location.
     * Only subclasses can add Nodes, to ensure Nodes are of the right type.
     *
     * @param node the node to add
     */
    protected fun add(node: Node) {
        nodeMap.add(node)
    }

    /**
     * Adds the Edge and its DirectedEdges with this PlanarGraph.
     * Assumes that the Edge has already been created with its associated DirectEdges.
     * Only subclasses can add Edges, to ensure the edges added are of the right class.
     */
    protected fun add(edge: Edge) {
        edges.add(edge)
        add(edge.getDirEdge(0))
        add(edge.getDirEdge(1))
    }

    /**
     * Adds the Edge to this PlanarGraph; only subclasses can add DirectedEdges,
     * to ensure the edges added are of the right class.
     */
    protected fun add(dirEdge: DirectedEdge?) {
        dirEdges.add(dirEdge)
    }

    /**
     * Returns an Iterator over the Nodes in this PlanarGraph.
     */
    fun nodeIterator(): Iterator<*>? {
        return nodeMap.iterator()
    }
    /**
     * Returns the Nodes in this PlanarGraph.
     */
    /**
     * Tests whether this graph contains the given [Edge]
     *
     * @param e the edge to query
     * @return `true` if the graph contains the edge
     */
    operator fun contains(e: Edge?): Boolean {
        return edges.contains(e)
    }

    /**
     * Tests whether this graph contains the given [DirectedEdge]
     *
     * @param de the directed edge to query
     * @return `true` if the graph contains the directed edge
     */
    operator fun contains(de: DirectedEdge?): Boolean {
        return dirEdges.contains(de)
    }

    val nodes: Collection<*>?
        get() = nodeMap.values()

    /**
     * Returns an Iterator over the DirectedEdges in this PlanarGraph, in the order in which they
     * were added.
     *
     * @see .add
     * @see .add
     */
    fun dirEdgeIterator(): Iterator<*> {
        return dirEdges.iterator()
    }

    /**
     * Returns an Iterator over the Edges in this PlanarGraph, in the order in which they
     * were added.
     *
     * @see .add
     */
    fun edgeIterator(): Iterator<*> {
        return edges.iterator()
    }

    /**
     * Returns the Edges that have been added to this PlanarGraph
     * @see .add
     */
    fun getEdges(): Collection<*> {
        return edges
    }

    /**
     * Removes an [Edge] and its associated [DirectedEdge]s
     * from their from-Nodes and from the graph.
     * Note: This method does not remove the [Node]s associated
     * with the [Edge], even if the removal of the [Edge]
     * reduces the degree of a [Node] to zero.
     */
    fun remove(edge: Edge) {
        remove(edge.getDirEdge(0))
        remove(edge.getDirEdge(1))
        edges.remove(edge)
        edge.remove()
    }

    /**
     * Removes a [DirectedEdge] from its from-[Node] and from this graph.
     * This method does not remove the [Node]s associated with the DirectedEdge,
     * even if the removal of the DirectedEdge reduces the degree of a Node to zero.
     */
    fun remove(de: DirectedEdge?) {
        val sym = de!!.sym
        if (sym != null) sym.sym = null
        de.fromNode.remove(de)
        de.remove()
        dirEdges.remove(de)
    }

    /**
     * Removes a node from the graph, along with any associated DirectedEdges and
     * Edges.
     */
    fun remove(node: Node) {
        // unhook all directed edges
        val outEdges = node.outEdges.edges
        val i = outEdges.iterator()
        while (i.hasNext()) {
            val de = i.next() as DirectedEdge
            val sym = de.sym
            // remove the diredge that points to this node
            sym?.let { remove(it) }
            // remove this diredge from the graph collection
            dirEdges.remove(de)
            val edge = de.edge
            if (edge != null) {
                edges.remove(edge)
            }
        }
        // remove the node from the graph
        nodeMap.remove(node.coordinate)
        node.remove()
    }

    /**
     * Returns all Nodes with the given number of Edges around it.
     */
    fun findNodesOfDegree(degree: Int): List<*> {
        val nodesFound: MutableList<Any?> = ArrayList()
        val i = nodeIterator()
        while (i!!.hasNext()) {
            val node = i.next() as Node
            if (node.degree == degree) nodesFound.add(node)
        }
        return nodesFound
    }
}