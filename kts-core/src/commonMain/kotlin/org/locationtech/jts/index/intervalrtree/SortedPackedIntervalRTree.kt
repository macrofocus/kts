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
package org.locationtech.jts.index.intervalrtree

import org.locationtech.jts.index.ItemVisitor
import kotlin.jvm.Synchronized
import kotlin.jvm.Volatile

/**
 * A static index on a set of 1-dimensional intervals,
 * using an R-Tree packed based on the order of the interval midpoints.
 * It supports range searching,
 * where the range is an interval of the real line (which may be a single point).
 * A common use is to index 1-dimensional intervals which
 * are the projection of 2-D objects onto an axis of the coordinate system.
 *
 * This index structure is *static*
 * - items cannot be added or removed once the first query has been made.
 * The advantage of this characteristic is that the index performance
 * can be optimized based on a fixed set of items.
 *
 * @author Martin Davis
 */
class SortedPackedIntervalRTree {
    private val leaves: MutableList<Any?> = ArrayList()

    /**
     * If root is null that indicates
     * that the tree has not yet been built,
     * OR nothing has been added to the tree.
     * In both cases, the tree is still open for insertions.
     */
    @Volatile
    private var root: IntervalRTreeNode? = null

    /**
     * Adds an item to the index which is associated with the given interval
     *
     * @param min the lower bound of the item interval
     * @param max the upper bound of the item interval
     * @param item the item to insert
     *
     * @throws IllegalStateException if the index has already been queried
     */
    fun insert(min: Double, max: Double, item: Any?) {
        if (root != null) throw IllegalStateException("Index cannot be added to once it has been queried")
        leaves.add(IntervalRTreeLeafNode(min, max, item!!))
    }

    @Synchronized
    private fun init() {
        // already built
        if (root != null) return
        /**
         * if leaves is empty then nothing has been inserted.
         * In this case it is safe to leave the tree in an open state
         */
        if (leaves.size == 0) return
        buildRoot()
    }

    private fun buildRoot() {
        if (root != null) return
        root = buildTree()
    }

    private fun buildTree(): IntervalRTreeNode {

        // sort the leaf nodes
        leaves.sortWith(IntervalRTreeNode.NodeComparator())

        // now group nodes into blocks of two and build tree up recursively
        var src: MutableList<Any?> = leaves
        var temp: MutableList<Any?>? = null
        var dest: MutableList<Any?> = ArrayList()
        while (true) {
            buildLevel(src, dest)
            if (dest.size == 1) return dest.get(0)!! as IntervalRTreeNode
            temp = src
            src = dest
            dest = temp
        }
    }

    //private int level = 0;
    private fun buildLevel(src: MutableList<*>, dest: MutableList<Any?>) {
        //level++;
        dest.clear()
        var i = 0
        while (i < src.size) {
            val n1 = src.get(i) as IntervalRTreeNode
            val n2 = if (i + 1 < src.size) src.get(i) else null
            if (n2 == null) {
                dest!!.add(n1)
            } else {
                val node: IntervalRTreeNode = IntervalRTreeBranchNode(
                    src.get(i) as IntervalRTreeNode,
                    src.get(i + 1) as IntervalRTreeNode
                )
                //        printNode(node);
//				System.out.println(node);
                dest!!.add(node)
            }
            i += 2
        }
    }
    // private void printNode(IntervalRTreeNode node)
    // {
    //   System.out.println(WKTWriter.toLineString(new Coordinate(node.min, level), new Coordinate(node.max, level)));
    // }
    /**
     * Search for intervals in the index which intersect the given closed interval
     * and apply the visitor to them.
     *
     * @param min the lower bound of the query interval
     * @param max the upper bound of the query interval
     * @param visitor the visitor to pass any matched items to
     */
    fun query(min: Double, max: Double, visitor: ItemVisitor?) {
        init()

        // if root is null tree must be empty
        if (root == null) return
        root!!.query(min, max, visitor!!)
    }
}