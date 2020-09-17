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
package org.locationtech.jts.index.strtree

import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.index.ItemVisitor
import org.locationtech.jts.legacy.Serializable
import org.locationtech.jts.util.Assert.isTrue
import org.locationtech.jts.util.Assert.shouldNeverReachHere
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlin.jvm.Synchronized

/**
 * Base class for STRtree and SIRtree. STR-packed R-trees are described in:
 * P. Rigaux, Michel Scholl and Agnes Voisard. *Spatial Databases With
 * Application To GIS.* Morgan Kaufmann, San Francisco, 2002.
 *
 * This implementation is based on [Boundable]s rather than [AbstractNode]s,
 * because the STR algorithm operates on both nodes and
 * data, both of which are treated as Boundables.
 *
 * This class is thread-safe.  Building the tree is synchronized,
 * and querying is stateless.
 *
 * @see STRtree
 *
 * @see SIRtree
 *
 * @version 1.7
 */
abstract class AbstractSTRtree @JvmOverloads constructor(nodeCapacity: Int = DEFAULT_NODE_CAPACITY) : Serializable {
    /**
     * A test for intersection between two bounds, necessary because subclasses
     * of AbstractSTRtree have different implementations of bounds.
     */
    interface IntersectsOp {
        /**
         * For STRtrees, the bounds will be Envelopes; for SIRtrees, Intervals;
         * for other subclasses of AbstractSTRtree, some other class.
         * @param aBounds the bounds of one spatial object
         * @param bBounds the bounds of another spatial object
         * @return whether the two bounds intersect
         */
        fun intersects(aBounds: Any?, bBounds: Any?): Boolean
    }

    //    @JvmField
    private var root: AbstractNode? = null
    private var built = false

    /**
     * Set to <tt>null</tt> when index is built, to avoid retaining memory.
     */
    private var itemBoundables: MutableList<ItemBoundable>? = ArrayList()

    /**
     * Returns the maximum number of child nodes that a node may have.
     *
     * @return the node capacity
     */
    val nodeCapacity: Int

    /**
     * Creates parent nodes, grandparent nodes, and so forth up to the root
     * node, for the data that has been inserted into the tree. Can only be
     * called once, and thus can be called only after all of the data has been
     * inserted into the tree.
     */
    @Synchronized
    fun build() {
        if (built) return
        root = if (itemBoundables!!.isEmpty()) createNode(0) else createHigherLevels(itemBoundables, -1)
        // the item list is no longer needed
        itemBoundables = null
        built = true
    }

    protected abstract fun createNode(level: Int): AbstractNode

    /**
     * Sorts the childBoundables then divides them into groups of size M, where
     * M is the node capacity.
     */
    protected open fun createParentBoundables(childBoundables: List<Any?>, newLevel: Int): List<Any?> {
        isTrue(childBoundables.isNotEmpty())
        val parentBoundables: ArrayList<Any?> = ArrayList()
        parentBoundables.add(createNode(newLevel))
        val sortedChildBoundables: ArrayList<Any?> = ArrayList(childBoundables)
        sortedChildBoundables.sortWith(getComparator()!!)
        val i: Iterator<*> = sortedChildBoundables.iterator()
        while (i.hasNext()) {
            val childBoundable = i.next() as Boundable
            if (lastNode(parentBoundables).childBoundables.size == nodeCapacity) {
                parentBoundables.add(createNode(newLevel))
            }
            lastNode(parentBoundables).addChildBoundable(childBoundable)
        }
        return parentBoundables
    }

    protected fun lastNode(nodes: List<*>): AbstractNode {
        return nodes[nodes.size - 1] as AbstractNode
    }

    /**
     * Creates the levels higher than the given level
     *
     * @param boundablesOfALevel
     * the level to build on
     * @param level
     * the level of the Boundables, or -1 if the boundables are item
     * boundables (that is, below level 0)
     * @return the root, which may be a ParentNode or a LeafNode
     */
    private fun createHigherLevels(boundablesOfALevel: List<*>?, level: Int): AbstractNode {
        isTrue(boundablesOfALevel!!.isNotEmpty())
        val parentBoundables = createParentBoundables(boundablesOfALevel, level + 1)
        return if (parentBoundables.size == 1) {
            parentBoundables[0] as AbstractNode
        } else createHigherLevels(parentBoundables, level + 1)
    }

    /**
     * Gets the root node of the tree.
     *
     * @return the root node
     */
    open fun getRoot(): AbstractNode? {
        build()
        return root
    }

    /**
     * Tests whether the index contains any items.
     * This method does not build the index,
     * so items can still be inserted after it has been called.
     *
     * @return true if the index does not contain any items
     */
    val isEmpty: Boolean
        get() = if (!built) itemBoundables!!.isEmpty() else root!!.isEmpty

    protected open fun size(): Int {
        if (isEmpty) {
            return 0
        }
        build()
        return size(root)
    }

    protected fun size(node: AbstractNode?): Int {
        var size = 0
        val i = node!!.childBoundables.iterator()
        while (i.hasNext()) {
            val childBoundable = i.next() as Boundable
            if (childBoundable is AbstractNode) {
                size += size(childBoundable)
            } else if (childBoundable is ItemBoundable) {
                size += 1
            }
        }
        return size
    }

    protected open fun depth(): Int {
        if (isEmpty) {
            return 0
        }
        build()
        return depth(root)
    }

    protected fun depth(node: AbstractNode?): Int {
        var maxChildDepth = 0
        val i = node!!.childBoundables.iterator()
        while (i.hasNext()) {
            val childBoundable = i.next() as Boundable
            if (childBoundable is AbstractNode) {
                val childDepth = depth(childBoundable)
                if (childDepth > maxChildDepth) maxChildDepth = childDepth
            }
        }
        return maxChildDepth + 1
    }

    protected fun insert(bounds: Any, item: Any) {
        isTrue(!built, "Cannot insert items into an STR packed R-tree after it has been built.")
        itemBoundables!!.add(ItemBoundable(bounds, item))
    }

    /**
     * Also builds the tree, if necessary.
     */
    fun query(searchBounds: Any?): List<*> {
        build()
        val matches: ArrayList<Any?> = ArrayList()
        if (isEmpty) {
            //Assert.isTrue(root.getBounds() == null);
            return matches
        }
        if (intersectsOp.intersects(root!!.bounds, searchBounds)) {
            queryInternal(searchBounds, root, matches)
        }
        return matches
    }

    /**
     * Also builds the tree, if necessary.
     */
    fun query(searchBounds: Any?, visitor: ItemVisitor?) {
        build()
        if (isEmpty) {
            // nothing in tree, so return
            //Assert.isTrue(root.getBounds() == null);
            return
        }
        if (intersectsOp.intersects(root!!.bounds, searchBounds)) {
            queryInternal(searchBounds, root, visitor)
        }
    }

    /**
     * @return a test for intersection between two bounds, necessary because subclasses
     * of AbstractSTRtree have different implementations of bounds.
     * @see IntersectsOp
     */
    protected abstract val intersectsOp: IntersectsOp
    private fun queryInternal(searchBounds: Any?, node: AbstractNode?, matches: MutableList<Any?>) {
        val childBoundables = node!!.childBoundables
        for (i in childBoundables.indices) {
            val childBoundable = childBoundables[i] as Boundable
            if (!intersectsOp.intersects(childBoundable.bounds, searchBounds)) {
                continue
            }
            when (childBoundable) {
                is AbstractNode -> {
                    queryInternal(searchBounds, childBoundable, matches)
                }
                is ItemBoundable -> {
                    matches.add(childBoundable.item)
                }
                else -> {
                    shouldNeverReachHere()
                }
            }
        }
    }

    private fun queryInternal(searchBounds: Any?, node: AbstractNode?, visitor: ItemVisitor?) {
        val childBoundables = node!!.childBoundables
        for (i in childBoundables.indices) {
            val childBoundable = childBoundables[i] as Boundable
            if (!intersectsOp.intersects(childBoundable.bounds, searchBounds)) {
                continue
            }
            when (childBoundable) {
                is AbstractNode -> {
                    queryInternal(searchBounds, childBoundable, visitor)
                }
                is ItemBoundable -> {
                    visitor!!.visitItem(childBoundable.item)
                }
                else -> {
                    shouldNeverReachHere()
                }
            }
        }
    }

    /**
     * Gets a tree structure (as a nested list)
     * corresponding to the structure of the items and nodes in this tree.
     *
     * The returned [List]s contain either [Object] items,
     * or Lists which correspond to subtrees of the tree
     * Subtrees which do not contain any items are not included.
     *
     * Builds the tree if necessary.
     *
     * @return a List of items and/or Lists
     */
    fun itemsTree(): List<*> {
        build()
        return itemsTree(root) ?: return ArrayList<Any?>()
    }

    private fun itemsTree(node: AbstractNode?): List<*>? {
        val valuesTreeForNode: MutableList<Any?> = ArrayList()
        val i = node!!.childBoundables.iterator()
        while (i.hasNext()) {
            val childBoundable = i.next() as Boundable
            if (childBoundable is AbstractNode) {
                val valuesTreeForChild = itemsTree(childBoundable)
                // only add if not null (which indicates an item somewhere in this tree
                if (valuesTreeForChild != null) valuesTreeForNode.add(valuesTreeForChild)
            } else if (childBoundable is ItemBoundable) {
                valuesTreeForNode.add(childBoundable.item)
            } else {
                shouldNeverReachHere()
            }
        }
        return if (valuesTreeForNode.size <= 0) null else valuesTreeForNode
    }

    /**
     * Removes an item from the tree.
     * (Builds the tree, if necessary.)
     */
    fun remove(searchBounds: Envelope?, item: Any?): Boolean {
        build()
        return if (intersectsOp.intersects(root!!.bounds, searchBounds)) {
            remove(searchBounds, root, item)
        } else false
    }

    private fun removeItem(node: AbstractNode?, item: Any?): Boolean {
        var childToRemove: Boundable? = null
        val i = node!!.childBoundables.iterator()
        while (i.hasNext()) {
            val childBoundable = i.next() as Boundable
            if (childBoundable is ItemBoundable) {
                if (childBoundable.item === item) childToRemove = childBoundable
            }
        }
        if (childToRemove != null) {
            node.childBoundables.remove(childToRemove)
            return true
        }
        return false
    }

    private fun remove(searchBounds: Any?, node: AbstractNode?, item: Any?): Boolean {
        // first try removing item from this node
        var found = removeItem(node, item)
        if (found) return true
        var childToPrune: AbstractNode? = null
        // next try removing item from lower nodes
        val i = node!!.childBoundables.iterator()
        while (i.hasNext()) {
            val childBoundable = i.next() as Boundable
            if (!intersectsOp.intersects(childBoundable.bounds, searchBounds)) {
                continue
            }
            if (childBoundable is AbstractNode) {
                found = remove(searchBounds, childBoundable, item)
                // if found, record child for pruning and exit
                if (found) {
                    childToPrune = childBoundable
                    break
                }
            }
        }
        // prune child if possible
        if (childToPrune != null) {
            if (childToPrune.childBoundables.isEmpty()) {
                node.childBoundables.remove(childToPrune)
            }
        }
        return found
    }

    protected open fun boundablesAtLevel(level: Int): List<*> {
        val boundables: ArrayList<Any?> = ArrayList()
        boundablesAtLevel(level, root, boundables)
        return boundables
    }

    /**
     * @param level -1 to get items
     */
    private fun boundablesAtLevel(level: Int, top: AbstractNode?, boundables: MutableCollection<Any?>) {
        isTrue(level > -2)
        if (top!!.level == level) {
            boundables.add(top)
            return
        }
        val i = top.childBoundables.iterator()
        while (i.hasNext()) {
            val boundable = i.next() as Boundable
            if (boundable is AbstractNode) {
                boundablesAtLevel(level, boundable, boundables)
            } else {
                isTrue(boundable is ItemBoundable)
                if (level == -1) {
                    boundables.add(boundable)
                }
            }
        }
        return
    }

    protected abstract fun getComparator(): Comparator<Any?>?

    companion object {
        /**
         *
         */
        private const val serialVersionUID = -3886435814360241337L
        private const val DEFAULT_NODE_CAPACITY = 10

        @JvmStatic
        fun compareDoubles(a: Double, b: Double): Int {
            return if (a > b) 1 else if (a < b) -1 else 0
        }
    }

    /**
     * Constructs an AbstractSTRtree with the specified maximum number of child
     * nodes that a node may have
     *
     * @param nodeCapacity the maximum number of child nodes in a node
     */
    /**
     * Constructs an AbstractSTRtree with the
     * default node capacity.
     */
    init {
        isTrue(nodeCapacity > 1, "Node capacity must be greater than 1")
        this.nodeCapacity = nodeCapacity
    }
}