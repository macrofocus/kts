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

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.util.Assert.isTrue

/**
 * QuadRoot is the root of a single Quadtree.  It is centred at the origin,
 * and does not have a defined extent.
 *
 * @version 1.7
 */
class Root : NodeBase() {
    /**
     * Insert an item into the quadtree this is the root of.
     */
    fun insert(itemEnv: Envelope, item: Any) {
        val index: Int = getSubnodeIndex(itemEnv, origin.x, origin.y)
        // if index is -1, itemEnv must cross the X or Y axis.
        if (index == -1) {
            add(item)
            return
        }
        /**
         * the item must be contained in one quadrant, so insert it into the
         * tree for that quadrant (which may not yet exist)
         */
        val node: Node? = subnode.get(index)
        /**
         * If the subquad doesn't exist or this item is not contained in it,
         * have to expand the tree upward to contain the item.
         */
        if (node == null || !node.envelope!!.contains(itemEnv)) {
            val largerNode: Node = Node.createExpanded(node, itemEnv)
            subnode.set(index, largerNode)
        }
        /**
         * At this point we have a subquad which exists and must contain
         * contains the env for the item.  Insert the item into the tree.
         */
        insertContained(subnode.get(index)!!, itemEnv, item)
        //System.out.println("depth = " + root.depth() + " size = " + root.size());
        //System.out.println(" size = " + size());
    }

    /**
     * insert an item which is known to be contained in the tree rooted at
     * the given QuadNode root.  Lower levels of the tree will be created
     * if necessary to hold the item.
     */
    private fun insertContained(tree: Node, itemEnv: Envelope, item: Any) {
        isTrue(tree.envelope!!.contains(itemEnv))
        /**
         * Do NOT create a new quad for zero-area envelopes - this would lead
         * to infinite recursion. Instead, use a heuristic of simply returning
         * the smallest existing quad containing the query
         */
        val isZeroX: Boolean = IntervalSize.isZeroWidth(itemEnv.minX, itemEnv.maxX)
        val isZeroY: Boolean = IntervalSize.isZeroWidth(itemEnv.minY, itemEnv.maxY)
        val node: NodeBase
        node = if (isZeroX || isZeroY) tree.find(itemEnv) else tree.getNode(itemEnv)
        node.add(item)
    }

    protected override fun isSearchMatch(searchEnv: Envelope?): Boolean {
        return true
    }

    companion object {
        // the singleton root quad is centred at the origin.
        private val origin = Coordinate(0.0, 0.0)
    }
}