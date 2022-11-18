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
package org.locationtech.jts.index.strtree

import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.index.ItemVisitor
import org.locationtech.jts.index.SpatialIndex
import org.locationtech.jts.legacy.Math.ceil
import org.locationtech.jts.legacy.Math.sqrt
import org.locationtech.jts.legacy.Serializable
import org.locationtech.jts.legacy.queue.PriorityQueue
import org.locationtech.jts.util.Assert
import kotlin.jvm.JvmOverloads

/**
 * A query-only R-tree created using the Sort-Tile-Recursive (STR) algorithm.
 * For two-dimensional spatial data.
 * <P>
 * The STR packed R-tree is simple to implement and maximizes space
 * utilization; that is, as many leaves as possible are filled to capacity.
 * Overlap between nodes is far less than in a basic R-tree.
 * However, the index is semi-static; once the tree has been built
 * (which happens automatically upon the first query), items may
 * not be added.
 * Items may be removed from the tree using [.remove].
</P> * <P>
 * Described in: P. Rigaux, Michel Scholl and Agnes Voisard.
 * *Spatial Databases With Application To GIS*.
 * Morgan Kaufmann, San Francisco, 2002.
</P> *
 * **Note that inserting items into a tree is not thread-safe.**
 * Inserting performed on more than one thread must be synchronized externally.
 *
 * Querying a tree is thread-safe.
 * The building phase is done synchronously,
 * and querying is stateless.
 *
 * @version 1.7
 */
open class STRtree : AbstractSTRtree, SpatialIndex, Serializable {
    class STRtreeNode(level: Int) : AbstractNode(level) {
        override fun computeBounds(): Any? {
            var bounds: Envelope? = null
            val i: Iterator<*> = getChildBoundables().iterator()
            while (i.hasNext()) {
                val childBoundable = i.next() as Boundable
                if (bounds == null) {
                    bounds = Envelope((childBoundable.bounds as Envelope?)!!)
                } else {
                    bounds.expandToInclude((childBoundable.bounds as Envelope?)!!)
                }
            }
            return bounds
        }
    }

    /**
     * Creates the parent level for the given child level. First, orders the items
     * by the x-values of the midpoints, and groups them into vertical slices.
     * For each slice, orders the items by the y-values of the midpoints, and
     * group them into runs of size M (the node capacity). For each run, creates
     * a new (parent) node.
     */
    override fun createParentBoundables(childBoundables: List<Any?>, newLevel: Int): List<Any?> {
        Assert.isTrue(!childBoundables.isEmpty())
        val minLeafCount: Int = ceil(childBoundables.size / nodeCapacity.toDouble()).toInt()
        val sortedChildBoundables: ArrayList<*> = ArrayList(childBoundables)
        sortedChildBoundables.sortWith(xComparator)
        val verticalSlices = verticalSlices(
            sortedChildBoundables,
            ceil(sqrt(minLeafCount.toDouble())).toInt()
        )
        return createParentBoundablesFromVerticalSlices(verticalSlices, newLevel)
    }

    private fun createParentBoundablesFromVerticalSlices(
        verticalSlices: Array<MutableList<Any?>?>,
        newLevel: Int
    ): List<*> {
        Assert.isTrue(verticalSlices.size > 0)
        val parentBoundables: MutableList<Any?> = ArrayList()
        for (i in verticalSlices.indices) {
            parentBoundables.addAll(
                createParentBoundablesFromVerticalSlice(verticalSlices[i]!!, newLevel)
            )
        }
        return parentBoundables
    }

    protected open fun createParentBoundablesFromVerticalSlice(
        childBoundables: List<Any?>,
        newLevel: Int
    ): List<Any?> {
        return super.createParentBoundables(childBoundables, newLevel)
    }

    /**
     * @param childBoundables Must be sorted by the x-value of the envelope midpoints
     */
    protected open fun verticalSlices(childBoundables: List<*>, sliceCount: Int): Array<MutableList<Any?>?> {
        val sliceCapacity: Int = ceil(childBoundables.size / sliceCount.toDouble()).toInt()
        val slices: Array<MutableList<Any?>?> = arrayOfNulls<MutableList<Any?>>(sliceCount)
        val i: Iterator<*> = childBoundables.iterator()
        for (j in 0 until sliceCount) {
            slices[j] = ArrayList()
            var boundablesAddedToSlice = 0
            while (i.hasNext() && boundablesAddedToSlice < sliceCapacity) {
                val childBoundable = i.next() as Boundable
                slices[j]!!.add(childBoundable)
                boundablesAddedToSlice++
            }
        }
        return slices
    }
    /**
     * Constructs an STRtree with the given maximum number of child nodes that
     * a node may have.
     *
     * The minimum recommended capacity setting is 4.
     *
     */
    /**
     * Constructs an STRtree with the default node capacity.
     */
    @JvmOverloads
    constructor(nodeCapacity: Int = DEFAULT_NODE_CAPACITY) : super(nodeCapacity) {
    }

    /**
     * Constructs an STRtree with the given maximum number of child nodes that
     * a node may have, and the root that links to all other nodes
     *
     * The minimum recommended capacity setting is 4.
     *
     */
    constructor(nodeCapacity: Int, root: STRtreeNode?) : super(nodeCapacity, root) {}

    /**
     * Constructs an STRtree with the given maximum number of child nodes that
     * a node may have, and all leaf nodes in the tree
     *
     * The minimum recommended capacity setting is 4.
     *
     */
    constructor(nodeCapacity: Int, itemBoundables: MutableList<ItemBoundable>?) : super(nodeCapacity, itemBoundables) {}

    override fun createNode(level: Int): AbstractNode? {
        return STRtreeNode(level)
    }

    override val intersectsOp: IntersectsOp
        protected get() = Companion.intersectsOp

    /**
     * Inserts an item having the given bounds into the tree.
     */
    override fun insert(itemEnv: Envelope?, item: Any?) {
        if (itemEnv!!.isNull) {
            return
        }
        super.insert(itemEnv, item!!)
    }

    /**
     * Returns items whose bounds intersect the given envelope.
     */
    override fun query(searchEnv: Envelope?): MutableList<*>? {
        //Yes this method does something. It specifies that the bounds is an
        //Envelope. super.query takes an Object, not an Envelope. [Jon Aquino 10/24/2003]
        return super.query(searchEnv as Any?)
    }

    /**
     * Returns items whose bounds intersect the given envelope.
     */
    override fun query(searchEnv: Envelope?, visitor: ItemVisitor?) {
        //Yes this method does something. It specifies that the bounds is an
        //Envelope. super.query takes an Object, not an Envelope. [Jon Aquino 10/24/2003]
        super.query(searchEnv, visitor)
    }

    /**
     * Removes a single item from the tree.
     *
     * @param itemEnv the Envelope of the item to remove
     * @param item the item to remove
     * @return `true` if the item was found
     */
    override fun remove(itemEnv: Envelope?, item: Any?): Boolean {
        return super.remove(itemEnv, item!!)
    }

    /**
     * Returns the number of items in the tree.
     *
     * @return the number of items in the tree
     */
    public override fun size(): Int {
        return super.size()
    }

    /**
     * Returns the number of items in the tree.
     *
     * @return the number of items in the tree
     */
    public override fun depth(): Int {
        return super.depth()
    }

    override val comparator: Comparator<Any?>
        get() = yComparator

    /**
     * Finds the two nearest items in the tree,
     * using [ItemDistance] as the distance metric.
     * A Branch-and-Bound tree traversal algorithm is used
     * to provide an efficient search.
     *
     * If the tree is empty, the return value is `null
     *         **
     * If it is required to find only pairs of distinct items,
     * the [ItemDistance] function must be **anti-reflexive**.
     *
     * @param itemDist a distance metric applicable to the items in this tree
     * @return the pair of the nearest items
     * or `null` if the tree is empty
     **` */
    fun nearestNeighbour(itemDist: ItemDistance?): Array<Any>? {
        if (isEmpty) return null

        // if tree has only one item this will return null
        val bp = BoundablePair(root!!, root!!, itemDist!!)
        return nearestNeighbour(bp)
    }

    /**
     * Finds the item in this tree which is nearest to the given [Object],
     * using [ItemDistance] as the distance metric.
     * A Branch-and-Bound tree traversal algorithm is used
     * to provide an efficient search.
     *
     * The query <tt>object</tt> does **not** have to be
     * contained in the tree, but it does
     * have to be compatible with the <tt>itemDist</tt>
     * distance metric.
     *
     * @param env the envelope of the query item
     * @param item the item to find the nearest neighbour of
     * @param itemDist a distance metric applicable to the items in this tree and the query item
     * @return the nearest item in this tree
     * or `null` if the tree is empty
     */
    fun nearestNeighbour(env: Envelope?, item: Any?, itemDist: ItemDistance?): Any? {
        if (isEmpty) return null
        val bnd: Boundable = ItemBoundable(env!!, item!!)
        val bp = BoundablePair(root!!, bnd, itemDist!!)
        return nearestNeighbour(bp)!!.get(0)
    }

    /**
     * Finds the two nearest items from this tree
     * and another tree,
     * using [ItemDistance] as the distance metric.
     * A Branch-and-Bound tree traversal algorithm is used
     * to provide an efficient search.
     * The result value is a pair of items,
     * the first from this tree and the second
     * from the argument tree.
     *
     * @param tree another tree
     * @param itemDist a distance metric applicable to the items in the trees
     * @return the pair of the nearest items, one from each tree
     * or `null` if no pair of distinct items can be found
     */
    fun nearestNeighbour(tree: STRtree, itemDist: ItemDistance?): Array<Any>? {
        if (isEmpty || tree.isEmpty) return null
        val bp = BoundablePair(root!!, tree.root!!, itemDist!!)
        return nearestNeighbour(bp)
    }

    private fun nearestNeighbour(initBndPair: BoundablePair): Array<Any>? {
        var distanceLowerBound = Double.POSITIVE_INFINITY
        var minPair: BoundablePair? = null

        // initialize search queue
        val priQ: PriorityQueue<BoundablePair> = PriorityQueue()
        priQ.add(initBndPair)
        while (!priQ.isEmpty() && distanceLowerBound > 0.0) {
            // pop head of queue and expand one side of pair
            val bndPair: BoundablePair = priQ.poll() as BoundablePair
            val pairDistance: Double = bndPair.distance
            /**
             * If the distance for the first pair in the queue
             * is >= current minimum distance, other nodes
             * in the queue must also have a greater distance.
             * So the current minDistance must be the true minimum,
             * and we are done.
             */
            if (pairDistance >= distanceLowerBound) break
            /**
             * If the pair members are leaves
             * then their distance is the exact lower bound.
             * Update the distanceLowerBound to reflect this
             * (which must be smaller, due to the test
             * immediately prior to this).
             */
            if (bndPair.isLeaves) {
                // assert: currentDistance < minimumDistanceFound
                distanceLowerBound = pairDistance
                minPair = bndPair
            } else {
                /**
                 * Otherwise, expand one side of the pair,
                 * and insert the expanded pairs into the queue.
                 * The choice of which side to expand is determined heuristically.
                 */
                bndPair.expandToQueue(priQ, distanceLowerBound)
            }
        }
        return if (minPair == null) null else arrayOf(
            (minPair.getBoundable(0) as ItemBoundable).item,
            (minPair.getBoundable(1) as ItemBoundable).item
        )
        // done - return items with min distance
    }

    /**
     * Tests whether some two items from this tree and another tree
     * lie within a given distance.
     * [ItemDistance] is used as the distance metric.
     * A Branch-and-Bound tree traversal algorithm is used
     * to provide an efficient search.
     *
     * @param tree another tree
     * @param itemDist a distance metric applicable to the items in the trees
     * @param maxDistance the distance limit for the search
     * @return true if there are items within the distance
     */
    fun isWithinDistance(tree: STRtree, itemDist: ItemDistance?, maxDistance: Double): Boolean {
        val bp = BoundablePair(root!!, tree.root!!, itemDist!!)
        return isWithinDistance(bp, maxDistance)
    }

    /**
     * Performs a withinDistance search on the tree node pairs.
     * This is a different search algorithm to nearest neighbour.
     * It can utilize the [BoundablePair.maximumDistance] between
     * tree nodes to confirm if two internal nodes must
     * have items closer than the maxDistance,
     * and short-circuit the search.
     *
     * @param initBndPair the initial pair containing the tree root nodes
     * @param maxDistance the maximum distance to search for
     * @return true if two items lie within the given distance
     */
    private fun isWithinDistance(initBndPair: BoundablePair, maxDistance: Double): Boolean {
        var distanceUpperBound = Double.POSITIVE_INFINITY

        // initialize search queue
        val priQ: PriorityQueue<BoundablePair> = PriorityQueue()
        priQ.add(initBndPair)
        while (!priQ.isEmpty()) {
            // pop head of queue and expand one side of pair
            val bndPair: BoundablePair = priQ.poll() as BoundablePair
            val pairDistance: Double = bndPair.distance
            /**
             * If the distance for the first pair in the queue
             * is > maxDistance, all other pairs
             * in the queue must have a greater distance as well.
             * So can conclude no items are within the distance
             * and terminate with result = false
             */
            if (pairDistance > maxDistance) return false
            /**
             * If the maximum distance between the nodes
             * is less than the maxDistance,
             * than all items in the nodes must be
             * closer than the max distance.
             * Then can terminate with result = true.
             *
             * NOTE: using Envelope MinMaxDistance
             * would provide a tighter bound,
             * but not much performance improvement has been observed
             */
            if (bndPair.maximumDistance() <= maxDistance) return true
            /**
             * If the pair items are leaves
             * then their actual distance is an upper bound.
             * Update the distanceUpperBound to reflect this
             */
            if (bndPair.isLeaves) {
                // assert: currentDistance < minimumDistanceFound
                distanceUpperBound = pairDistance
                /**
                 * If the items are closer than maxDistance
                 * can terminate with result = true.
                 */
                if (distanceUpperBound <= maxDistance) return true
            } else {
                /**
                 * Otherwise, expand one side of the pair,
                 * and insert the expanded pairs into the queue.
                 * The choice of which side to expand is determined heuristically.
                 */
                bndPair.expandToQueue(priQ, distanceUpperBound)
            }
        }
        return false
    }

    /**
     * Finds up to k items in this tree which are the nearest neighbors to the given `item`,
     * using `itemDist` as the distance metric.
     * A Branch-and-Bound tree traversal algorithm is used
     * to provide an efficient search.
     * This method implements the KNN algorithm described in the following paper:
     *
     * Roussopoulos, Nick, Stephen Kelley, and Frédéric Vincent. "Nearest neighbor queries."
     * ACM sigmod record. Vol. 24. No. 2. ACM, 1995.
     *
     * The query `item` does **not** have to be
     * contained in the tree, but it does
     * have to be compatible with the `itemDist`
     * distance metric.
     *
     * If the tree size is smaller than k fewer items will be returned.
     * If the tree is empty an array of size 0 is returned.
     *
     * @param env the envelope of the query item
     * @param item the item to find the nearest neighbours of
     * @param itemDist a distance metric applicable to the items in this tree and the query item
     * @param k the maximum number of nearest items to search for
     * @return an array of the nearest items found (with length between 0 and K)
     */
    fun nearestNeighbour(env: Envelope?, item: Any?, itemDist: ItemDistance?, k: Int): Array<Any?> {
        if (isEmpty) return arrayOfNulls(0)
        val bnd: Boundable = ItemBoundable(env!!, item!!)
        val bp = BoundablePair(root!!, bnd, itemDist!!)
        return nearestNeighbourK(bp, k)
    }

    private fun nearestNeighbourK(initBndPair: BoundablePair, k: Int): Array<Any?> {
        return nearestNeighbourK(initBndPair, Double.POSITIVE_INFINITY, k)
    }

    private fun nearestNeighbourK(initBndPair: BoundablePair, maxDistance: Double, k: Int): Array<Any?> {
        var distanceLowerBound = maxDistance

        // initialize internal structures
        val priQ: PriorityQueue<BoundablePair> = PriorityQueue()

        // initialize queue
        priQ.add(initBndPair)
        val kNearestNeighbors: PriorityQueue<BoundablePair> = PriorityQueue()
        while (!priQ.isEmpty() && distanceLowerBound >= 0.0) {
            // pop head of queue and expand one side of pair
            val bndPair: BoundablePair = priQ.poll() as BoundablePair
            val pairDistance: Double = bndPair.distance
            /**
             * If the distance for the first node in the queue
             * is >= the current maximum distance in the k queue , all other nodes
             * in the queue must also have a greater distance.
             * So the current minDistance must be the true minimum,
             * and we are done.
             */
            if (pairDistance >= distanceLowerBound) {
                break
            }
            /**
             * If the pair members are leaves
             * then their distance is the exact lower bound.
             * Update the distanceLowerBound to reflect this
             * (which must be smaller, due to the test
             * immediately prior to this).
             */
            if (bndPair.isLeaves) {
                // assert: currentDistance < minimumDistanceFound
                if (kNearestNeighbors.size < k) {
                    kNearestNeighbors.add(bndPair)
                } else {
                    val bp1: BoundablePair = kNearestNeighbors.peek() as BoundablePair
                    if (bp1.distance > pairDistance) {
                        kNearestNeighbors.poll()
                        kNearestNeighbors.add(bndPair)
                    }
                    /*
    		   * minDistance should be the farthest point in the K nearest neighbor queue.
    		   */
                    val bp2: BoundablePair = kNearestNeighbors.peek() as BoundablePair
                    distanceLowerBound = bp2.distance
                }
            } else {
                /**
                 * Otherwise, expand one side of the pair,
                 * (the choice of which side to expand is heuristically determined)
                 * and insert the new expanded pairs into the queue
                 */
                bndPair.expandToQueue(priQ, distanceLowerBound)
            }
        }
        // done - return items with min distance
        return getItems(kNearestNeighbors)
    }

    companion object {
        /**
         *
         */
        private const val serialVersionUID = 259274702368956900L
        private val xComparator: Comparator<Any?> = object : Comparator<Any?> {
            override fun compare(o1: Any?, o2: Any?): Int {
                return compareDoubles(
                    centreX((o1 as Boundable).bounds as Envelope?),
                    centreX((o2 as Boundable).bounds as Envelope?)
                )
            }
        }
        private val yComparator: Comparator<Any?> = object : Comparator<Any?> {
            override fun compare(o1: Any?, o2: Any?): Int {
                return compareDoubles(
                    centreY((o1 as Boundable).bounds as Envelope?),
                    centreY((o2 as Boundable).bounds as Envelope?)
                )
            }
        }

        private fun centreX(e: Envelope?): Double {
            return avg(e!!.minX, e.maxX)
        }

        private fun centreY(e: Envelope?): Double {
            return avg(e!!.minY, e.maxY)
        }

        private fun avg(a: Double, b: Double): Double {
            return (a + b) / 2.0
        }

        private val intersectsOp: IntersectsOp = object : IntersectsOp {
            override fun intersects(aBounds: Any?, bBounds: Any?): Boolean {
                return (aBounds as Envelope?)!!.intersects((bBounds as Envelope?)!!)
            }
        }
        private const val DEFAULT_NODE_CAPACITY = 10
        private fun getItems(kNearestNeighbors: PriorityQueue<*>): Array<Any?> {
            /**
             * Iterate the K Nearest Neighbour Queue and retrieve the item from each BoundablePair
             * in this queue
             */
            val items = arrayOfNulls<Any>(kNearestNeighbors.size)
            var count = 0
            while (!kNearestNeighbors.isEmpty()) {
                val bp: BoundablePair = kNearestNeighbors.poll() as BoundablePair
                items[count] = (bp.getBoundable(0) as ItemBoundable).item
                count++
            }
            return items
        }
    }
}