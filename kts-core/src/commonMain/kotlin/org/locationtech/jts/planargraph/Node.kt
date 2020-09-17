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
import org.locationtech.jts.planargraph.DirectedEdge.Companion.toEdges
import kotlin.jvm.JvmOverloads

/**
 * A node in a [PlanarGraph]is a location where 0 or more [Edge]s
 * meet. A node is connected to each of its incident Edges via an outgoing
 * DirectedEdge. Some clients using a `PlanarGraph` may want to
 * subclass `Node` to add their own application-specific
 * data and methods.
 *
 * @version 1.7
 */
class Node
/**
 * Constructs a Node with the given location.
 */ @JvmOverloads constructor(
    /** The location of this Node  */
    var coordinate: Coordinate?,
    /** The collection of DirectedEdges that leave this Node  */
    var outEdges: DirectedEdgeStar = DirectedEdgeStar()
) : GraphComponent() {
    /**
     * Returns the location of this Node.
     */
    /**
     * Returns the collection of DirectedEdges that leave this Node.
     */
    /**
     * Adds an outgoing DirectedEdge to this Node.
     */
    fun addOutEdge(de: DirectedEdge) {
        outEdges.add(de)
    }

    /**
     * Returns the number of edges around this Node.
     */
    val degree: Int
        get() = outEdges.degree

    /**
     * Returns the zero-based index of the given Edge, after sorting in ascending order
     * by angle with the positive x-axis.
     */
    fun getIndex(edge: Edge): Int {
        return outEdges.getIndex(edge)
    }

    /**
     * Removes a [DirectedEdge] incident on this node.
     * Does not change the state of the directed edge.
     */
    fun remove(de: DirectedEdge?) {
        outEdges.remove(de)
    }

    /**
     * Removes this node from its containing graph.
     */
    fun remove() {
        coordinate = null
    }

    /**
     * Tests whether this node has been removed from its containing graph
     *
     * @return `true` if this node is removed
     */
    override val isRemoved: Boolean
        get() = coordinate == null

    companion object {
        /**
         * Returns all Edges that connect the two nodes (which are assumed to be different).
         */
        fun getEdgesBetween(node0: Node, node1: Node): Collection<*> {
            val edges0 = toEdges(node0.outEdges.edges)
            val commonEdges: MutableSet<*> = HashSet(edges0)
            val edges1 = toEdges(node1.outEdges.edges)
            commonEdges.retainAll(edges1)
            return commonEdges
        }
    }
    /**
     * Constructs a Node with the given location and collection of outgoing DirectedEdges.
     */
}