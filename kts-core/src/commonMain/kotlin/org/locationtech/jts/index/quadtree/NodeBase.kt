/*
 * Copyright (c) 2016 Vivid Solutions.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.index.quadtree

import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.index.ItemVisitor
import org.locationtech.jts.index.quadtree.Root
import org.locationtech.jts.legacy.Serializable

/**
 * The base class for nodes in a [Quadtree].
 *
 * @version 1.7
 */
abstract class NodeBase : Serializable {
    var items: MutableList<Any> = ArrayList()

    /**
     * subquads are numbered as follows:
     * <pre>
     * 2 | 3
     * --+--
     * 0 | 1
    </pre> *
     */
    protected var subnode: Array<Node?> = arrayOfNulls<Node>(4)

    fun hasItems(): Boolean {
        return !items.isEmpty()
    }

    fun add(item: Any) {
        items.add(item)
        //DEBUG itemCount++;
//DEBUG System.out.print(itemCount);
    }

    /**
     * Removes a single item from this subtree.
     *
     * @param itemEnv the envelope containing the item
     * @param item the item to remove
     * @return `true` if the item was found and removed
     */
    fun remove(itemEnv: Envelope?, item: Any?): Boolean {
        // use envelope to restrict nodes scanned
        if (!isSearchMatch(itemEnv)) return false
        var found = false
        for (i in 0..3) {
            if (subnode[i] != null) {
                found = subnode[i]!!.remove(itemEnv, item)
                if (found) {
                    // trim subtree if empty
                    if (subnode[i]!!.isPrunable) subnode[i] = null
                    break
                }
            }
        }
        // if item was found lower down, don't need to search for it here
        if (found) return found
        // otherwise, try and remove the item from the list of items in this node
        found = items.remove(item)
        return found
    }

    val isPrunable: Boolean
        get() = !(hasChildren() || hasItems())

    fun hasChildren(): Boolean {
        for (i in 0..3) {
            if (subnode[i] != null) return true
        }
        return false
    }

    val isEmpty: Boolean
        get() {
            var isEmpty = true
            if (!items.isEmpty()) isEmpty = false else {
                for (i in 0..3) {
                    if (subnode[i] != null) {
                        if (!subnode[i]!!.isEmpty) {
                            isEmpty = false
                            break
                        }
                    }
                }
            }
            return isEmpty
        }

    //<<TODO:RENAME?>> Sounds like this method adds resultItems to items
    //(like List#addAll). Perhaps it should be renamed to "addAllItemsTo" [Jon Aquino]
    fun addAllItems(resultItems: MutableList<Any>): MutableList<Any> {
        // this node may have items as well as subnodes (since items may not
        // be wholely contained in any single subnode
        resultItems.addAll(items)
        for (i in 0..3) {
            if (subnode[i] != null) {
                subnode[i]!!.addAllItems(resultItems)
            }
        }
        return resultItems
    }

    protected abstract fun isSearchMatch(searchEnv: Envelope?): Boolean
    fun addAllItemsFromOverlapping(searchEnv: Envelope?, resultItems: MutableList<Any>) {
        if (!isSearchMatch(searchEnv)) return

        // this node may have items as well as subnodes (since items may not
        // be wholely contained in any single subnode
        resultItems.addAll(items)
        for (i in 0..3) {
            if (subnode[i] != null) {
                subnode[i]!!.addAllItemsFromOverlapping(searchEnv, resultItems)
            }
        }
    }

    fun visit(searchEnv: Envelope?, visitor: ItemVisitor) {
        if (!isSearchMatch(searchEnv)) return

        // this node may have items as well as subnodes (since items may not
        // be wholely contained in any single subnode
        visitItems(searchEnv, visitor)
        for (i in 0..3) {
            if (subnode[i] != null) {
                subnode[i]!!.visit(searchEnv, visitor)
            }
        }
    }

    private fun visitItems(searchEnv: Envelope?, visitor: ItemVisitor) {
        // would be nice to filter items based on search envelope, but can't until they contain an envelope
        val i: Iterator<*> = items.iterator()
        while (i.hasNext()) {
            visitor.visitItem(i.next())
        }
    }

    //<<TODO:RENAME?>> In Samet's terminology, I think what we're returning here is
    //actually level+1 rather than depth. (See p. 4 of his book) [Jon Aquino]
    fun depth(): Int {
        var maxSubDepth = 0
        for (i in 0..3) {
            if (subnode[i] != null) {
                val sqd: Int = subnode[i]!!.depth()
                if (sqd > maxSubDepth) maxSubDepth = sqd
            }
        }
        return maxSubDepth + 1
    }

    fun size(): Int {
        var subSize = 0
        for (i in 0..3) {
            if (subnode[i] != null) {
                subSize += subnode[i]!!.size()
            }
        }
        return subSize + items.size
    }

    val nodeCount: Int
        get() {
            var subSize = 0
            for (i in 0..3) {
                if (subnode[i] != null) {
                    subSize += subnode[i]!!.size()
                }
            }
            return subSize + 1
        }

    companion object {
        //DEBUG private static int itemCount = 0;  // debugging
        /**
         * Gets the index of the subquad that wholly contains the given envelope.
         * If none does, returns -1.
         *
         * @return the index of the subquad that wholly contains the given envelope
         * or -1 if no subquad wholly contains the envelope
         */
        fun getSubnodeIndex(env: Envelope?, centrex: Double, centrey: Double): Int {
            var subnodeIndex = -1
            if (env!!.minX >= centrex) {
                if (env.minY >= centrey) subnodeIndex = 3
                if (env.maxY <= centrey) subnodeIndex = 1
            }
            if (env.maxX <= centrex) {
                if (env.minY >= centrey) subnodeIndex = 2
                if (env.maxY <= centrey) subnodeIndex = 0
            }
            return subnodeIndex
        }
    }
}