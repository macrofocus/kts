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
package org.locationtech.jts.index.bintree

import org.locationtech.jts.util.Assert

/**
 * A node of a [Bintree].
 *
 * @version 1.7
 */
class Node(interval: Interval?, level: Int) : NodeBase() {
    private val interval: Interval?
    private val centre: Double
    private val level: Int

    init {
        this.interval = interval
        this.level = level
        centre = (interval!!.min + interval.max) / 2
    }

    fun getInterval(): Interval? {
        return interval
    }

    protected override fun isSearchMatch(itemInterval: Interval?): Boolean {
//    System.out.println(itemInterval + " overlaps " + interval + " : "
//                       + itemInterval.overlaps(interval));
        return itemInterval!!.overlaps(interval)
    }

    /**
     * Returns the subnode containing the envelope.
     * Creates the node if
     * it does not already exist.
     */
    fun getNode(searchInterval: Interval?): Node {
        val subnodeIndex: Int = getSubnodeIndex(searchInterval, centre)
        // if index is -1 searchEnv is not contained in a subnode
        return if (subnodeIndex != -1) {
            // create the node if it does not exist
            val node = getSubnode(subnodeIndex)
            // recursively search the found/created node
            node.getNode(searchInterval)
        } else {
            this
        }
    }

    /**
     * Returns the smallest *existing*
     * node containing the envelope.
     */
    fun find(searchInterval: Interval?): NodeBase {
        val subnodeIndex: Int = getSubnodeIndex(searchInterval, centre)
        if (subnodeIndex == -1) return this
        if (subnode.get(subnodeIndex) != null) {
            // query lies in subnode, so search it
            val node: Node = subnode.get(subnodeIndex)!!
            return node.find(searchInterval)
        }
        // no existing subnode, so return this one anyway
        return this
    }

    fun insert(node: Node) {
        Assert.isTrue(interval == null || interval.contains(node.interval))
        val index: Int = getSubnodeIndex(node.interval, centre)
        if (node.level == level - 1) {
            subnode.set(index, node)
        } else {
            // the node is not a direct child, so make a new child node to contain it
            // and recursively insert the node
            val childNode = createSubnode(index)
            childNode.insert(node)
            subnode.set(index, childNode)
        }
    }

    /**
     * get the subnode for the index.
     * If it doesn't exist, create it
     */
    private fun getSubnode(index: Int): Node {
        if (subnode.get(index) == null) {
            subnode.set(index, createSubnode(index))
        }
        return subnode.get(index)!!
    }

    private fun createSubnode(index: Int): Node {
        // create a new subnode in the appropriate interval
        var min = 0.0
        var max = 0.0
        when (index) {
            0 -> {
                min = interval!!.min
                max = centre
            }

            1 -> {
                min = centre
                max = interval!!.max
            }
        }
        val subInt: Interval = Interval(min, max)
        return Node(subInt, level - 1)
    }

    companion object {
        fun createNode(itemInterval: Interval): Node {
            val key: Key = Key(itemInterval)

//System.out.println("input: " + env + "  binaryEnv: " + key.getEnvelope());
            return Node(key.getInterval(), key.level)
        }

        fun createExpanded(node: Node?, addInterval: Interval): Node {
            val expandInt: Interval =
                Interval(addInterval)
            if (node != null) expandInt.expandToInclude(node.interval)
            val largerNode = createNode(expandInt)
            if (node != null) largerNode.insert(node)
            return largerNode
        }
    }
}