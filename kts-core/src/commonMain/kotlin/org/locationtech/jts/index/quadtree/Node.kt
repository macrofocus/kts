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
package org.locationtech.jts.index.quadtree

import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.index.quadtree.Root
import org.locationtech.jts.util.Assert.isTrue

/**
 * Represents a node of a [Quadtree].  Nodes contain
 * items which have a spatial extent corresponding to the node's position
 * in the quadtree.
 *
 * @version 1.7
 */
class Node(val envelope: Envelope?, private val level: Int) : NodeBase() {
    private val centrex: Double
    private val centrey: Double
    protected override fun isSearchMatch(searchEnv: Envelope?): Boolean {
        return if (searchEnv == null) false else envelope!!.intersects(searchEnv)
    }

    /**
     * Returns the subquad containing the envelope <tt>searchEnv</tt>.
     * Creates the subquad if
     * it does not already exist.
     *
     * @return the subquad containing the search envelope
     */
    fun getNode(searchEnv: Envelope?): Node {
        val subnodeIndex: Int = getSubnodeIndex(searchEnv, centrex, centrey)
        // if subquadIndex is -1 searchEnv is not contained in a subquad
        return if (subnodeIndex != -1) {
            // create the quad if it does not exist
            val node = getSubnode(subnodeIndex)
            // recursively search the found/created quad
            node.getNode(searchEnv)
        } else {
            this
        }
    }

    /**
     * Returns the smallest *existing*
     * node containing the envelope.
     */
    fun find(searchEnv: Envelope?): NodeBase {
        val subnodeIndex: Int = getSubnodeIndex(searchEnv, centrex, centrey)
        if (subnodeIndex == -1) return this
        if (subnode.get(subnodeIndex) != null) {
            // query lies in subquad, so search it
            val node: Node = subnode.get(subnodeIndex)!!
            return node.find(searchEnv)
        }
        // no existing subquad, so return this one anyway
        return this
    }

    fun insertNode(node: Node) {
        isTrue(envelope == null || envelope.contains(node.envelope!!))
        //System.out.println(env);
//System.out.println(quad.env);
        val index: Int = getSubnodeIndex(node.envelope, centrex, centrey)
        //System.out.println(index);
        if (node.level == level - 1) {
            subnode.set(index, node)
            //System.out.println("inserted");
        } else {
            // the quad is not a direct child, so make a new child quad to contain it
            // and recursively insert the quad
            val childNode = createSubnode(index)
            childNode.insertNode(node)
            subnode.set(index, childNode)
        }
    }

    /**
     * get the subquad for the index.
     * If it doesn't exist, create it
     */
    private fun getSubnode(index: Int): Node {
        if (subnode.get(index) == null) {
            subnode.set(index, createSubnode(index))
        }
        return subnode.get(index)!!
    }

    private fun createSubnode(index: Int): Node {
        // create a new subquad in the appropriate quadrant
        var minx = 0.0
        var maxx = 0.0
        var miny = 0.0
        var maxy = 0.0
        when (index) {
            0 -> {
                minx = envelope!!.minX
                maxx = centrex
                miny = envelope.minY
                maxy = centrey
            }
            1 -> {
                minx = centrex
                maxx = envelope!!.maxX
                miny = envelope.minY
                maxy = centrey
            }
            2 -> {
                minx = envelope!!.minX
                maxx = centrex
                miny = centrey
                maxy = envelope.maxY
            }
            3 -> {
                minx = centrex
                maxx = envelope!!.maxX
                miny = centrey
                maxy = envelope.maxY
            }
        }
        val sqEnv = Envelope(minx, maxx, miny, maxy)
        return Node(sqEnv, level - 1)
    }

    companion object {
        fun createNode(env: Envelope): Node {
            val key = Key(env)
            return Node(key.envelope, key.level)
        }

        fun createExpanded(node: Node?, addEnv: Envelope?): Node {
            val expandEnv = Envelope(addEnv!!)
            if (node != null) expandEnv.expandToInclude(node.envelope!!)
            val largerNode = createNode(expandEnv)
            if (node != null) largerNode.insertNode(node)
            return largerNode
        }
    }

    init {
        //this.parent = parent;
        centrex = (envelope!!.minX + envelope.maxX) / 2
        centrey = (envelope.minY + envelope.maxY) / 2
    }
}