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
import org.locationtech.jts.index.ArrayListVisitor
import org.locationtech.jts.index.ItemVisitor
import org.locationtech.jts.index.SpatialIndex
import org.locationtech.jts.index.quadtree.Root
import org.locationtech.jts.legacy.Serializable

/**
 * A Quadtree is a spatial index structure for efficient range querying
 * of items bounded by 2D rectangles.
 * [Geometry]s can be indexed by using their
 * [Envelope]s.
 * Any type of Object can also be indexed as
 * long as it has an extent that can be represented by an [Envelope].
 *
 *
 * This Quadtree index provides a **primary filter**
 * for range rectangle queries.  The various query methods return a list of
 * all items which *may* intersect the query rectangle.  Note that
 * it may thus return items which do **not** in fact intersect the query rectangle.
 * A secondary filter is required to test for actual intersection
 * between the query rectangle and the envelope of each candidate item.
 * The secondary filter may be performed explicitly,
 * or it may be provided implicitly by subsequent operations executed on the items
 * (for instance, if the index query is followed by computing a spatial predicate
 * between the query geometry and tree items,
 * the envelope intersection check is performed automatically.
 *
 *
 * This implementation does not require specifying the extent of the inserted
 * items beforehand.  It will automatically expand to accommodate any extent
 * of dataset.
 *
 *
 * This data structure is also known as an *MX-CIF quadtree*
 * following the terminology of Samet and others.
 *
 * @version 1.7
 */
class Quadtree : SpatialIndex, Serializable {
    private val root: Root?

    /**
     *
     * minExtent is the minimum envelope extent of all items
     * inserted into the tree so far. It is used as a heuristic value
     * to construct non-zero envelopes for features with zero X and/or Y extent.
     * Start with a non-zero extent, in case the first feature inserted has
     * a zero extent in both directions.  This value may be non-optimal, but
     * only one feature will be inserted with this value.
     */
    private var minExtent = 1.0

    /**
     * Returns the number of levels in the tree.
     */
    fun depth(): Int {
        //I don't think it's possible for root to be null. Perhaps we should
        //remove the check. [Jon Aquino]
        //Or make an assertion [Jon Aquino 10/29/2003]
        return if (root != null) root.depth() else 0
    }

    /**
     * Tests whether the index contains any items.
     *
     * @return true if the index does not contain any items
     */
    val isEmpty: Boolean
        get() = if (root == null) true else root.isEmpty

    /**
     * Returns the number of items in the tree.
     *
     * @return the number of items in the tree
     */
    fun size(): Int {
        return if (root != null) root.size() else 0
    }

    override fun insert(itemEnv: Envelope?, item: Any) {
        collectStats(itemEnv!!)
        val insertEnv = ensureExtent(itemEnv, minExtent)
        root!!.insert(insertEnv, item as Node)
    }

    /**
     * Removes a single item from the tree.
     *
     * @param itemEnv the Envelope of the item to be removed
     * @param item the item to remove
     * @return `true` if the item was found (and thus removed)
     */
    override fun remove(itemEnv: Envelope?, item: Any?): Boolean {
        val posEnv = ensureExtent(itemEnv!!, minExtent)
        return root!!.remove(posEnv, item)
    }
    /*
  public List OLDquery(Envelope searchEnv)
  {
    / **
     * the items that are matched are the items in quads which
     * overlap the search envelope
     */
    /*
    List foundItems = new ArrayList();
    root.addAllItemsFromOverlapping(searchEnv, foundItems);
    return foundItems;
  }
*/
    /**
     * Queries the tree and returns items which may lie in the given search envelope.
     * Precisely, the items that are returned are all items in the tree
     * whose envelope **may** intersect the search Envelope.
     * Note that some items with non-intersecting envelopes may be returned as well;
     * the client is responsible for filtering these out.
     * In most situations there will be many items in the tree which do not
     * intersect the search envelope and which are not returned - thus
     * providing improved performance over a simple linear scan.
     *
     * @param searchEnv the envelope of the desired query area.
     * @return a List of items which may intersect the search envelope
     */
    override fun query(searchEnv: Any?): MutableList<Any?> {
        /**
         * the items that are matched are the items in quads which
         * overlap the search envelope
         */
        val visitor = ArrayListVisitor()
        query(searchEnv, visitor)
        return visitor.items
    }

    /**
     * Queries the tree and visits items which may lie in the given search envelope.
     * Precisely, the items that are visited are all items in the tree
     * whose envelope **may** intersect the search Envelope.
     * Note that some items with non-intersecting envelopes may be visited as well;
     * the client is responsible for filtering these out.
     * In most situations there will be many items in the tree which do not
     * intersect the search envelope and which are not visited - thus
     * providing improved performance over a simple linear scan.
     *
     * @param searchEnv the envelope of the desired query area.
     * @param visitor a visitor object which is passed the visited items
     */
    override fun query(searchEnv: Any?, visitor: ItemVisitor?) {
        /**
         * the items that are matched are the items in quads which
         * overlap the search envelope
         */
        root!!.visit(searchEnv as Envelope, visitor!!)
    }

    /**
     * Return a list of all items in the Quadtree
     */
    fun queryAll(): MutableList<Node> {
        val foundItems: MutableList<Node> = ArrayList()
        root!!.addAllItems(foundItems)
        return foundItems
    }

    private fun collectStats(itemEnv: Envelope) {
        val delX = itemEnv.width
        if (delX < minExtent && delX > 0.0) minExtent = delX
        val delY = itemEnv.height
        if (delY < minExtent && delY > 0.0) minExtent = delY
    }

    companion object {
        private const val serialVersionUID = -7461163625812743604L

        /**
         * Ensure that the envelope for the inserted item has non-zero extents.
         * Use the current minExtent to pad the envelope, if necessary
         */
        fun ensureExtent(itemEnv: Envelope, minExtent: Double): Envelope {
            //The names "ensureExtent" and "minExtent" are misleading -- sounds like
            //this method ensures that the extents are greater than minExtent.
            //Perhaps we should rename them to "ensurePositiveExtent" and "defaultExtent".
            //[Jon Aquino]
            var minx = itemEnv.minX
            var maxx = itemEnv.maxX
            var miny = itemEnv.minY
            var maxy = itemEnv.maxY
            // has a non-zero extent
            if (minx != maxx && miny != maxy) return itemEnv

            // pad one or both extents
            if (minx == maxx) {
                minx = minx - minExtent / 2.0
                maxx = maxx + minExtent / 2.0
            }
            if (miny == maxy) {
                miny = miny - minExtent / 2.0
                maxy = maxy + minExtent / 2.0
            }
            return Envelope(minx, maxx, miny, maxy)
        }
    }

    /**
     * Constructs a Quadtree with zero items.
     */
    init {
        root = Root()
    }
}