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

/**
 * An `BinTree` (or "Binary Interval Tree")
 * is a 1-dimensional version of a quadtree.
 * It indexes 1-dimensional intervals (which may
 * be the projection of 2-D objects on an axis).
 * It supports range searching
 * (where the range may be a single point).
 * This structure is dynamic -
 * new items can be added at any time,
 * and it will support deletion of items
 * (although this is not currently implemented).
 *
 * This implementation does not require specifying the extent of the inserted
 * items beforehand.  It will automatically expand to accommodate any extent
 * of dataset.
 *
 * The bintree structure is used to provide a primary filter
 * for interval queries.  The query() method returns a list of
 * all objects which *may* intersect the query interval.
 * Note that it may return objects which do not in fact intersect.
 * A secondary filter is required to test for exact intersection.
 * Of course, this secondary filter may consist of other tests besides
 * intersection, such as testing other kinds of spatial relationships.
 *
 * This index is different to the Interval Tree of Edelsbrunner
 * or the Segment Tree of Bentley.
 *
 * @version 1.7
 */
class Bintree {
    private val root: Root?

    /**
     * Statistics
     *
     * minExtent is the minimum extent of all items
     * inserted into the tree so far. It is used as a heuristic value
     * to construct non-zero extents for features with zero extent.
     * Start with a non-zero extent, in case the first feature inserted has
     * a zero extent in both directions.  This value may be non-optimal, but
     * only one feature will be inserted with this value.
     */
    private var minExtent = 1.0

    init {
        root = Root()
    }

    fun depth(): Int {
        return if (root != null) root.depth() else 0
    }

    fun size(): Int {
        return if (root != null) root.size() else 0
    }

    /**
     * Compute the total number of nodes in the tree
     *
     * @return the number of nodes in the tree
     */
    fun nodeSize(): Int {
        return if (root != null) root.nodeSize() else 0
    }

    fun insert(itemInterval: Interval, item: Node) {
        collectStats(itemInterval)
        val insertInterval: Interval = ensureExtent(itemInterval, minExtent)
        //int oldSize = size();
        root!!.insert(insertInterval, item)
        /* DEBUG
int newSize = size();
System.out.println("BinTree: size = " + newSize + "   node size = " + nodeSize());
if (newSize <= oldSize) {
      System.out.println("Lost item!");
      root.insert(insertInterval, item);
      System.out.println("reinsertion size = " + size());
}
    */
    }

    /**
     * Removes a single item from the tree.
     *
     * @param itemInterval the interval of the item to be removed
     * @param item the item to remove
     * @return `true` if the item was found (and thus removed)
     */
    fun remove(itemInterval: Interval, item: Any?): Boolean {
        val insertInterval: Interval = ensureExtent(itemInterval, minExtent)
        return root!!.remove(insertInterval, item)
    }

    operator fun iterator(): Iterator<*> {
        val foundItems: MutableList<Node> = ArrayList()
        root!!.addAllItems(foundItems)
        return foundItems.iterator()
    }

    fun query(x: Double): MutableList<Node> {
        return query(Interval(x, x))
    }

    /**
     * Queries the tree to find all candidate items which
     * may overlap the query interval.
     * If the query interval is <tt>null</tt>, all items in the tree are found.
     *
     * min and max may be the same value
     */
    fun query(interval: Interval?): MutableList<Node> {
        /**
         * the items that are matched are all items in intervals
         * which overlap the query interval
         */
        val foundItems: MutableList<Node> = ArrayList()
        query(interval, foundItems)
        return foundItems
    }

    /**
     * Adds items in the tree which potentially overlap the query interval
     * to the given collection.
     * If the query interval is <tt>null</tt>, add all items in the tree.
     *
     * @param interval a query interval, or null
     * @param foundItems the candidate items found
     */
    fun query(interval: Interval?, foundItems: MutableCollection<Node>) {
        root!!.addAllItemsFromOverlapping(interval, foundItems)
    }

    private fun collectStats(interval: Interval) {
        val del: Double = interval.width
        if (del < minExtent && del > 0.0) minExtent = del
    }

    companion object {
        /**
         * Ensure that the Interval for the inserted item has non-zero extents.
         * Use the current minExtent to pad it, if necessary
         */
        fun ensureExtent(
            itemInterval: Interval,
            minExtent: Double
        ): Interval {
            var min: Double = itemInterval.min
            var max: Double = itemInterval.max
            // has a non-zero extent
            if (min != max) return itemInterval

            // pad extent
            if (min == max) {
                min = min - minExtent / 2.0
                max = min + minExtent / 2.0
            }
            return Interval(min, max)
        }
    }
}