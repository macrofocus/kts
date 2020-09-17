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

/**
 * Represents an undirected edge of a [PlanarGraph]. An undirected edge
 * in fact simply acts as a central point of reference for two opposite
 * [DirectedEdge]s.
 *
 * Usually a client using a `PlanarGraph` will subclass `Edge`
 * to add its own application-specific data and methods.
 *
 * @version 1.7
 */
class Edge : GraphComponent {
    /**
     * The two DirectedEdges associated with this Edge.
     * Index 0 is forward, 1 is reverse.
     */
    protected var dirEdge: Array<DirectedEdge>? = null

    /**
     * Constructs an Edge whose DirectedEdges are not yet set. Be sure to call
     * [.setDirectedEdges]
     */
    constructor()

    /**
     * Constructs an Edge initialized with the given DirectedEdges, and for each
     * DirectedEdge: sets the Edge, sets the symmetric DirectedEdge, and adds
     * this Edge to its from-Node.
     */
    constructor(de0: DirectedEdge, de1: DirectedEdge) {
        setDirectedEdges(de0, de1)
    }

    /**
     * Initializes this Edge's two DirectedEdges, and for each DirectedEdge: sets the
     * Edge, sets the symmetric DirectedEdge, and adds this Edge to its from-Node.
     */
    fun setDirectedEdges(de0: DirectedEdge, de1: DirectedEdge) {
        dirEdge = arrayOf(de0, de1)
        de0.edge = this
        de1.edge = this
        de0.sym = de1
        de1.sym = de0
        de0.fromNode.addOutEdge(de0)
        de1.fromNode.addOutEdge(de1)
    }

    /**
     * Returns one of the DirectedEdges associated with this Edge.
     * @param i 0 or 1.  0 returns the forward directed edge, 1 returns the reverse
     */
    fun getDirEdge(i: Int): DirectedEdge {
        return dirEdge!![i]
    }

    /**
     * Returns the [DirectedEdge] that starts from the given node, or null if the
     * node is not one of the two nodes associated with this Edge.
     */
    fun getDirEdge(fromNode: Node): DirectedEdge? {
        if (dirEdge!![0].fromNode === fromNode) return dirEdge!![0]
        return if (dirEdge!![1].fromNode === fromNode) dirEdge!![1] else null
        // node not found
        // possibly should throw an exception here?
    }

    /**
     * If `node` is one of the two nodes associated with this Edge,
     * returns the other node; otherwise returns null.
     */
    fun getOppositeNode(node: Node): Node? {
        if (dirEdge!![0].fromNode === node) return dirEdge!![0].toNode
        return if (dirEdge!![1].fromNode === node) dirEdge!![1].toNode else null
        // node not found
        // possibly should throw an exception here?
    }

    /**
     * Removes this edge from its containing graph.
     */
    fun remove() {
        dirEdge = null
    }

    /**
     * Tests whether this edge has been removed from its containing graph
     *
     * @return `true` if this edge is removed
     */
    override val isRemoved: Boolean
        get() = dirEdge == null
}