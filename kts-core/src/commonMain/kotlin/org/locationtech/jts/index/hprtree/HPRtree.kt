/*
 * Copyright (c) 2019 Martin Davis.
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.index.hprtree

import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.index.ArrayListVisitor
import org.locationtech.jts.index.ItemVisitor
import org.locationtech.jts.index.SpatialIndex
import org.locationtech.jts.index.strtree.STRtree
import org.locationtech.jts.legacy.Synchronized
import kotlin.jvm.JvmOverloads

/**
 * A Hilbert-Packed R-tree.  This is a static R-tree
 * which is packed by using the Hilbert ordering
 * of the tree items.
 *
 * The tree is constructed by sorting the items
 * by the Hilbert code of the midpoint of their envelope.
 * Then, a set of internal layers is created recursively
 * as follows:
 *
 *  * The items/nodes of the previous are partitioned into blocks
 * of size `nodeCapacity`
 *  * For each block a layer node is created with range
 * equal to the envelope of the items/nodess in the block
 *
 * The internal layers are stored using an array to
 * store the node bounds.
 * The link between a node and its children is
 * stored implicitly in the indexes of the array.
 * For efficiency, the offsets to the layers
 * within the node array are pre-computed and stored.
 *
 * NOTE: Based on performance testing,
 * the HPRtree is somewhat faster than the STRtree.
 * It should also be more memory-efficent,
 * due to fewer object allocations.
 * However, it is not clear whether this
 * will produce a significant improvement
 * for use in JTS operations.
 *
 * @see STRtree
 *
 *
 * @author Martin Davis
 */
class HPRtree @JvmOverloads constructor(val nodeCapacity: Int = DEFAULT_NODE_CAPACITY) : SpatialIndex {
    private val items: MutableList<Item> = ArrayList()
    private val totalExtent = Envelope()
    private var layerStartIndex: IntArray? = null
    private var nodeBounds: DoubleArray? = null
    private var isBuilt = false
    /**
     * Creates a new index with the given node capacity.
     *
     * @param nodeCapacity the node capacity to use
     */
    //public int nodeIntersectsCount;
    /**
     * Creates a new index with the default node capacity.
     */
    init {
    }

    /**
     * Gets the number of items in the index.
     *
     * @return the number of items
     */
    fun size(): Int {
        return items.size
    }

    override fun insert(itemEnv: Envelope?, item: Any?) {
        if (isBuilt) {
            throw IllegalStateException("Cannot insert items after tree is built.")
        }
        items.add(Item(itemEnv!!, item!!))
        totalExtent.expandToInclude(itemEnv)
    }

    override fun query(searchEnv: Envelope?): List<*> {
        build()
        if (!totalExtent.intersects(searchEnv!!)) return ArrayList<Any>()
        val visitor = ArrayListVisitor()
        query(searchEnv, visitor)
        return visitor.items
    }

    override fun query(searchEnv: Envelope?, visitor: ItemVisitor?) {
        build()
        if (!totalExtent.intersects(searchEnv!!)) return
        if (layerStartIndex == null) {
            queryItems(0, searchEnv, visitor!!)
        } else {
            queryTopLayer(searchEnv, visitor!!)
        }
    }

    private fun queryTopLayer(searchEnv: Envelope, visitor: ItemVisitor) {
        val layerIndex = layerStartIndex!!.size - 2
        val layerSize = layerSize(layerIndex)
        // query each node in layer
        var i = 0
        while (i < layerSize) {
            queryNode(layerIndex, i, searchEnv, visitor)
            i += ENV_SIZE
        }
    }

    private fun queryNode(layerIndex: Int, nodeOffset: Int, searchEnv: Envelope, visitor: ItemVisitor) {
        val layerStart = layerStartIndex!![layerIndex]
        val nodeIndex = layerStart + nodeOffset
        if (!intersects(nodeIndex, searchEnv)) return
        if (layerIndex == 0) {
            val childNodesOffset = nodeOffset / ENV_SIZE * nodeCapacity
            queryItems(childNodesOffset, searchEnv, visitor)
        } else {
            val childNodesOffset = nodeOffset * nodeCapacity
            queryNodeChildren(layerIndex - 1, childNodesOffset, searchEnv, visitor)
        }
    }

    private fun intersects(nodeIndex: Int, env: Envelope): Boolean {
        //nodeIntersectsCount++;
        val isBeyond =
            env.maxX < nodeBounds!![nodeIndex] || env.maxY < nodeBounds!![nodeIndex + 1] || env.minX > nodeBounds!![nodeIndex + 2] || env.minY > nodeBounds!![nodeIndex + 3]
        return !isBeyond
    }

    private fun queryNodeChildren(layerIndex: Int, blockOffset: Int, searchEnv: Envelope, visitor: ItemVisitor) {
        val layerStart = layerStartIndex!![layerIndex]
        val layerEnd = layerStartIndex!![layerIndex + 1]
        for (i in 0 until nodeCapacity) {
            val nodeOffset = blockOffset + ENV_SIZE * i
            // don't query past layer end
            if (layerStart + nodeOffset >= layerEnd) break
            queryNode(layerIndex, nodeOffset, searchEnv, visitor)
        }
    }

    private fun queryItems(blockStart: Int, searchEnv: Envelope, visitor: ItemVisitor) {
        for (i in 0 until nodeCapacity) {
            val itemIndex = blockStart + i
            // don't query past end of items
            if (itemIndex >= items.size) break

            // visit the item if its envelope intersects search env
            val item: Item = items[itemIndex]
            //nodeIntersectsCount++;
            if (intersects(item.envelope, searchEnv)) {
                //if (item.getEnvelope().intersects(searchEnv)) {
                visitor.visitItem(item.item)
            }
        }
    }

    private fun layerSize(layerIndex: Int): Int {
        val layerStart = layerStartIndex!![layerIndex]
        val layerEnd = layerStartIndex!![layerIndex + 1]
        return layerEnd - layerStart
    }

    override fun remove(itemEnv: Envelope?, item: Any?): Boolean {
        // TODO Auto-generated method stub
        return false
    }

    /**
     * Builds the index, if not already built.
     */
    @Synchronized
    fun build() {
        // skip if already built
        if (isBuilt) return
        isBuilt = true
        // don't need to build an empty or very small tree
        if (items.size <= nodeCapacity) return
        sortItems()
        //dumpItems(items);
        layerStartIndex = computeLayerIndices(items.size, nodeCapacity)
        // allocate storage
        val nodeCount = layerStartIndex!![layerStartIndex!!.size - 1] / 4
        nodeBounds = createBoundsArray(nodeCount)

        // compute tree nodes
        computeLeafNodes(layerStartIndex!![1])
        for (i in 1 until layerStartIndex!!.size - 1) {
            computeLayerNodes(i)
        }
        //dumpNodes();
    }

    private fun computeLayerNodes(layerIndex: Int) {
        val layerStart = layerStartIndex!![layerIndex]
        val childLayerStart = layerStartIndex!![layerIndex - 1]
        val layerSize = layerSize(layerIndex)
        var i = 0
        while (i < layerSize) {
            val childStart = childLayerStart + nodeCapacity * i
            computeNodeBounds(layerStart + i, childStart, layerStart)
            i += ENV_SIZE
        }
    }

    private fun computeNodeBounds(nodeIndex: Int, blockStart: Int, nodeMaxIndex: Int) {
        for (i in 0..nodeCapacity) {
            val index = blockStart + 4 * i
            if (index >= nodeMaxIndex) break
            updateNodeBounds(
                nodeIndex,
                nodeBounds!![index],
                nodeBounds!![index + 1],
                nodeBounds!![index + 2],
                nodeBounds!![index + 3]
            )
        }
    }

    private fun computeLeafNodes(layerSize: Int) {
        var i = 0
        while (i < layerSize) {
            computeLeafNodeBounds(i, nodeCapacity * i / 4)
            i += ENV_SIZE
        }
    }

    private fun computeLeafNodeBounds(nodeIndex: Int, blockStart: Int) {
        for (i in 0..nodeCapacity) {
            val itemIndex = blockStart + i
            if (itemIndex >= items.size) break
            val env: Envelope = items[itemIndex].envelope
            updateNodeBounds(nodeIndex, env.minX, env.minY, env.maxX, env.maxY)
        }
    }

    private fun updateNodeBounds(nodeIndex: Int, minX: Double, minY: Double, maxX: Double, maxY: Double) {
        if (minX < nodeBounds!![nodeIndex]) nodeBounds!![nodeIndex] = minX
        if (minY < nodeBounds!![nodeIndex + 1]) nodeBounds!![nodeIndex + 1] = minY
        if (maxX > nodeBounds!![nodeIndex + 2]) nodeBounds!![nodeIndex + 2] = maxX
        if (maxY > nodeBounds!![nodeIndex + 3]) nodeBounds!![nodeIndex + 3] = maxY
    }

    private fun getNodeEnvelope(i: Int): Envelope {
        return Envelope(nodeBounds!![i], nodeBounds!![i + 1], nodeBounds!![i + 2], nodeBounds!![i + 3])
    }// create from largest to smallest

    /**
     * Gets the extents of the internal index nodes
     *
     * @return a list of the internal node extents
     */
    val bounds: Array<Envelope?>
        get() {
            val numNodes = nodeBounds!!.size / 4
            val bounds = arrayOfNulls<Envelope>(numNodes)
            // create from largest to smallest
            for (i in numNodes - 1 downTo 0) {
                val boundIndex = 4 * i
                bounds[i] = Envelope(
                    nodeBounds!![boundIndex], nodeBounds!![boundIndex + 2],
                    nodeBounds!![boundIndex + 1], nodeBounds!![boundIndex + 3]
                )
            }
            return bounds
        }

    private fun sortItems() {
        val comp = ItemComparator(HilbertEncoder(HILBERT_LEVEL, totalExtent))
        items.sortWith(comp)
    }

    internal class ItemComparator(encoder: HilbertEncoder) : Comparator<Item> {
        private val encoder: HilbertEncoder

        init {
            this.encoder = encoder
        }

        override fun compare(item1: Item, item2: Item): Int {
            val hcode1: Int = encoder.encode(item1.envelope)
            val hcode2: Int = encoder.encode(item2.envelope)
            return hcode1.compareTo(hcode2)
        }
    }

    companion object {
        private const val ENV_SIZE = 4
        private const val HILBERT_LEVEL = 12
        private const val DEFAULT_NODE_CAPACITY = 16

        /**
         * Tests whether two envelopes intersect.
         * Avoids the null check in [Envelope.intersects].
         *
         * @param env1 an envelope
         * @param env2 an envelope
         * @return true if the envelopes intersect
         */
        private fun intersects(env1: Envelope, env2: Envelope): Boolean {
            return (env2.minX > env1.maxX || env2.maxX < env1.minX || env2.minY > env1.maxY || env2.maxY >= env1.minY)
        }

        /*
  private void dumpNodes() {
    GeometryFactory fact = new GeometryFactory();
    for (int i = 0; i < nodeMinX.length; i++) {
      Envelope env = new Envelope(nodeMinX[i], nodeMaxX[i], nodeMinY[i], nodeMaxY[i]);;
      System.out.println(fact.toGeometry(env));
    }
  }

  private static void dumpItems(List<Item> items) {
    GeometryFactory fact = new GeometryFactory();
    for (Item item : items) {
      Envelope env = item.getEnvelope();
      System.out.println(fact.toGeometry(env));
    }
  }
  */
        private fun createBoundsArray(size: Int): DoubleArray {
            val a = DoubleArray(4 * size)
            for (i in 0 until size) {
                val index = 4 * i
                a[index] = Double.MAX_VALUE
                a[index + 1] = Double.MAX_VALUE
                a[index + 2] = -Double.MAX_VALUE
                a[index + 3] = -Double.MAX_VALUE
            }
            return a
        }

        private fun computeLayerIndices(itemSize: Int, nodeCapacity: Int): IntArray {
            val layerIndexList: MutableList<Int> = ArrayList()
            var layerSize = itemSize
            var index = 0
            do {
                layerIndexList.add(index)
                layerSize = numNodesToCover(layerSize, nodeCapacity)
                index += ENV_SIZE * layerSize
            } while (layerSize > 1)
            return toIntArray(layerIndexList)
        }

        /**
         * Computes the number of blocks (nodes) required to
         * cover a given number of children.
         *
         * @param nChild
         * @param nodeCapacity
         * @return the number of nodes needed to cover the children
         */
        private fun numNodesToCover(nChild: Int, nodeCapacity: Int): Int {
            val mult = nChild / nodeCapacity
            val total = mult * nodeCapacity
            return if (total == nChild) mult else mult + 1
        }

        private fun toIntArray(list: List<Int>): IntArray {
            val array = IntArray(list.size)
            for (i in array.indices) {
                array[i] = list[i]
            }
            return array
        }
    }
}