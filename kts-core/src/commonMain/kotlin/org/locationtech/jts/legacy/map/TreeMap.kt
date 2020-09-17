/*
 * Copyright (c) 2020 Macrofocus GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Some of the code has been derived from GWT 2.9.0, which came with the following license:
 *
 * Copyright 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.locationtech.jts.legacy.map

import org.locationtech.jts.legacy.Serializable
import kotlin.jvm.JvmOverloads
import kotlin.jvm.Transient

/**
 * Implements a TreeMap using a red-black tree. This guarantees O(log n)
 * performance on lookups, inserts, and deletes while maintaining linear
 * in-order traversal time. Null keys and values are fully supported if the
 * comparator supports them (the default comparator does not).
 *
 * @param <K> key type
 * @param <V> value type
</V></K> */
open class TreeMap<K : Comparable<K?>, V> @JvmOverloads constructor(c: Comparator<in K?>? = naturalOrder<K>() as Comparator<in K?>?) :
    AbstractNavigableMap<K, V>(), Serializable {
    /*
   * Implementation derived from public domain C implementation as of 5
   * September 2007 at:
   * http://eternallyconfuzzled.com/tuts/datastructures/jsw_tut_rbtree.aspx
   * written by Julienne Walker.
   *
   * This version does not require a parent pointer kept in each node.
   */
    /**
     * Iterator for `descendingMap().entrySet()`.
     */
    private inner class DescendingEntryIterator @JvmOverloads constructor(
        type: SubMapType = SubMapType.All,
        fromKey: K? = null, fromInclusive: Boolean = false, toKey: K? = null, toInclusive: Boolean = false
    ) :
        MutableIterator<MutableMap.MutableEntry<K, V>> {
        private val iter: MutableListIterator<MutableMap.MutableEntry<K, V>>
        private var last: Map.Entry<K, V>? = null
        override fun hasNext(): Boolean {
            return iter.hasPrevious()
        }

        override fun next(): MutableMap.MutableEntry<K, V> {
            return iter.previous().also { last = it }
        }

        override fun remove() {
            iter.remove()
            removeEntry(last!!)
            last = null
        }
        /**
         * Create an iterator which may return only a restricted range.
         *
         * @param fromKey the first key to return in the iterator.
         * @param toKey the upper bound of keys to return.
         */
        /**
         * Constructor for `DescendingEntryIterator`.
         */
        init {
            val list: MutableList<MutableMap.MutableEntry<K, V>> = ArrayList()
            inOrderAdd(
                list, type, root,
                fromKey, fromInclusive, toKey, toInclusive
            )
            iter = list.listIterator(list.size)
        }
    }

    /**
     * Iterator for `EntrySet`.
     */
    private inner class EntryIterator @JvmOverloads constructor(
        type: SubMapType = SubMapType.All,
        fromKey: K? = null, fromInclusive: Boolean = false, toKey: K? = null, toInclusive: Boolean = false
    ) :
        MutableIterator<MutableMap.MutableEntry<K, V>> {
        private val iter: MutableListIterator<MutableMap.MutableEntry<K, V>>
        private var last: MutableMap.MutableEntry<K, V>? = null
        override fun hasNext(): Boolean {
            return iter.hasNext()
        }

        override fun next(): MutableMap.MutableEntry<K, V> {
            return iter.next().also { last = it }
        }

        override fun remove() {
            iter.remove()
            removeEntry(last!!)
            last = null
        }
        /**
         * Create an iterator which may return only a restricted range.
         *
         * @param fromKey the first key to return in the iterator.
         * @param toKey the upper bound of keys to return.
         */
        /**
         * Constructor for `EntrySetIterator`.
         */
        init {
            val list: MutableList<MutableMap.MutableEntry<K, V>> = ArrayList()
            inOrderAdd(
                list, type, root,
                fromKey, fromInclusive, toKey, toInclusive
            )
            iter = list.listIterator()
        }
    }

    private inner class TEntrySet<K, V> : EntrySet() {
        override fun clear() {
            this@TreeMap.clear()
        }
    }

    /**
     * Tree node.
     *
     * @param <K> key type
     * @param <V> value type
    </V></K> */
    private class Node<K, V>
    /**
     * Create a red node.
     *
     * @param key
     * @param value
     */ @JvmOverloads constructor(key: K?, value: V?, var isRed: Boolean = true) :
        SimpleEntry<K?, V?>(key, value) {
        /*
     * The children are kept in an array to minimize the normal duplication of
     * code.
     */
        val child: Array<Node<K, V>?> = arrayOfNulls<Node<K, V>?>(2)
        /**
         * Create a node of the specified color.
         *
         * @param key
         * @param value
         * @param isRed true if this should be a red node, false for black
         */

//        fun key : K? {
//            return key
//        }
//
//        fun value : V? {
//            return value
//        }
    }

    /**
     * A state object which is passed down the tree for both insert and remove.
     * All uses make use of the done flag to indicate when no further rebalancing
     * of the tree is required. Remove methods use the found flag to indicate when
     * the desired key has been found. value is used both to return the value of a
     * removed node as well as to pass in a value which must match (used for
     * entrySet().remove(entry)), and the matchValue flag is used to request this
     * behavior.
     *
     * @param <V> value type
    </V> */
    private class State<V> {
        var done = false
        var found = false
        var matchValue = false
        var value: V? = null
        override fun toString(): String {
            return "State: mv=$matchValue value=$value done=$done found=$found"
        }
    }

    private inner class SubMap(
        type: SubMapType,
        fromKey: K?, fromInclusive: Boolean,
        toKey: K?, toInclusive: Boolean
    ) : AbstractNavigableMap<K, V>() {
        private val fromInclusive: Boolean

        // valid only if type is Range or Tail
        private val fromKey: K?
        private val toInclusive: Boolean

        // valid only if type is Range or Head
        private val toKey: K?
        private val type: SubMapType
        override fun comparator(): Comparator<in K> {
            return this@TreeMap.comparator()
        }

//        override fun entrySet(): Set<Map.Entry<K, V>> {
//            return EntrySet()
//        }

        override fun headMap(toKey: K, toInclusive: Boolean): NavigableMap<K, V> {
            if (type.toKeyValid() && cmp!!.compare(toKey, this.toKey) > 0) {
                throw IllegalArgumentException(
                    "subMap: " + toKey +
                            " greater than " + this.toKey
                )
            }
            return if (type.fromKeyValid()) {
                this@TreeMap.subMap(fromKey!!, fromInclusive, toKey, toInclusive)
            } else {
                this@TreeMap.headMap(toKey, toInclusive)
            }
        }

        override fun put(key: K, value: V): V? {
            if (!inRange(key)) {
                throw IllegalArgumentException(
                    (key.toString() + " outside the range "
                            + fromKey + " to " + toKey)
                )
            }
            return this@TreeMap.put(key, value)
        }

        override fun remove(k: K): V? {
            return if (!inRange(k)) {
                null
            } else this@TreeMap.remove(k)
        }

        override val size: Int
            get() {
                if (getFirstEntry() == null) {
                    return 0
                }

                // TODO(jat): more efficient way to do this?
                var count = 0
                val it: Iterator<Map.Entry<K, V>> = entryIterator()
                while (it.hasNext()) {
                    count++
                    it.next()
                }
                return count
            }

        override val firstEntryField: Map.Entry<K, V>
            get() = firstEntry()!!
        override val lastEntryField: Map.Entry<K, V>
            get() = lastEntry()!!

        override fun subMap(
            newFromKey: K, newFromInclusive: Boolean,
            newToKey: K, newToInclusive: Boolean
        ): NavigableMap<K, V> {
            if (type.fromKeyValid() && cmp!!.compare(newFromKey, fromKey) < 0) {
                throw IllegalArgumentException(
                    ("subMap: " + newFromKey +
                            " less than " + fromKey)
                )
            }
            if (type.toKeyValid() && cmp!!.compare(newToKey, toKey) > 0) {
                throw IllegalArgumentException(
                    ("subMap: " + newToKey +
                            " greater than " + toKey)
                )
            }
            return this@TreeMap.subMap(newFromKey, newFromInclusive, newToKey, newToInclusive)
        }

        override fun tailMap(fromKey: K, fromInclusive: Boolean): NavigableMap<K, V> {
            if (type.fromKeyValid() && cmp!!.compare(fromKey, this.fromKey) < 0) {
                throw IllegalArgumentException(
                    ("subMap: " + fromKey +
                            " less than " + this.fromKey)
                )
            }
            return if (type.toKeyValid()) {
                this@TreeMap.subMap(fromKey, fromInclusive, toKey!!, toInclusive)
            } else {
                this@TreeMap.tailMap(fromKey, fromInclusive)
            }
        }

        override fun descendingEntryIterator(): MutableIterator<MutableMap.MutableEntry<K, V>> {
            return DescendingEntryIterator(type, fromKey, fromInclusive, toKey, toInclusive)
        }

        override fun entryIterator(): MutableIterator<MutableMap.MutableEntry<K, V>> {
            return EntryIterator(type, fromKey, fromInclusive, toKey, toInclusive)
        }

        override fun getEntry(key: K): Map.Entry<K, V>? {
            return guardInRange(this@TreeMap.getEntry(key))
        }

        fun getFirstEntry(): Map.Entry<K, V>? {
            val entry: Map.Entry<K, V> = if (type.fromKeyValid()) {
                if (fromInclusive) {
                    this@TreeMap.getCeilingEntry(fromKey!!)!!
                } else {
                    this@TreeMap.getHigherEntry(fromKey!!)!!
                }
            } else {
                this@TreeMap.firstEntry()!!
            }
            // The map is empty if the first key after fromKey is out of range.
            return guardInRange(entry)
        }

        fun getLastEntry(): Map.Entry<K, V>? {
            val entry: Map.Entry<K, V>? = if (type.toKeyValid()) {
                if (toInclusive) {
                    this@TreeMap.getFloorEntry(toKey!!)
                } else {
                    this@TreeMap.getLowerEntry(toKey!!)
                }
            } else {
                this@TreeMap.lastEntry()
            }
            // The map is empty if the last key before toKey is out of range.
            return guardInRange(entry)
        }

        override fun getCeilingEntry(key: K): Map.Entry<K, V>? {
            return guardInRange(this@TreeMap.getCeilingEntry(key))
        }

        override fun getFloorEntry(key: K): Map.Entry<K, V>? {
            return guardInRange(this@TreeMap.getFloorEntry(key))
        }

        override fun getHigherEntry(key: K): Map.Entry<K, V>? {
            return guardInRange(this@TreeMap.getHigherEntry(key))
        }

        override fun getLowerEntry(key: K): Map.Entry<K, V>? {
            return guardInRange(this@TreeMap.getLowerEntry(key))
        }

        override fun removeEntry(entry: Map.Entry<K, V>): Boolean {
            return inRange(entry.key) && this@TreeMap.removeEntry(entry)
        }

        private fun guardInRange(entry: Map.Entry<K, V>?): Map.Entry<K, V>? {
            return if (entry != null && inRange(entry.key)) entry else null
        }

        private fun inRange(key: K): Boolean {
            return this@TreeMap.inRange(type, key, fromKey, fromInclusive, toKey, toInclusive)
        }

        init {
            when (type) {
                SubMapType.Range -> if (cmp!!.compare(toKey, fromKey) < 0) {
                    throw IllegalArgumentException(
                        ("subMap: " + toKey
                                + " less than " + fromKey)
                    )
                }
                SubMapType.Head ->           // check key for compatibility with comparator
                    cmp!!.compare(toKey, toKey)
                SubMapType.Tail ->           // check key for compatibility with comparator
                    cmp!!.compare(fromKey, fromKey)
                SubMapType.All -> {
                }
            }
            this.type = type
            this.fromKey = fromKey
            this.fromInclusive = fromInclusive
            this.toKey = toKey
            this.toInclusive = toInclusive
        }
    }

    private enum class SubMapType {
        All, Head {
            override fun toKeyValid(): Boolean {
                return true
            }
        },
        Range {
            override fun fromKeyValid(): Boolean {
                return true
            }

            override fun toKeyValid(): Boolean {
                return true
            }
        },
        Tail {
            override fun fromKeyValid(): Boolean {
                return true
            }
        };

        /**
         * Returns true if this submap type uses a from-key.
         */
        open fun fromKeyValid(): Boolean {
            return false
        }

        /**
         * Returns true if this submap type uses a to-key.
         */
        open fun toKeyValid(): Boolean {
            return false
        }
    }

    // The comparator to use.
    private var cmp: Comparator<in K?>? = null

    /*
   * These two fields are just hints to STOB so that it generates serializers
   * for K and V
   */
    private val exposeKeyType: K? = null
    private val exposeValueType: V? = null

    // The root of the tree.
    @Transient
    private var root: Node<K, V>? = null

    // The number of nodes in the tree.
    override var size: Int = 0

    constructor(map: Map<out K, V>?) : this() {
        putAll((map)!!)
    }

    constructor(map: SortedMap<K?, V>?) : this(checkNotNull(map).comparator()) {
        TODO("Not yet implemented")
//        putAll(map!!) // TODO(jat): more efficient init from sorted map
    }

    override fun clear() {
        root = null
        size = 0
    }

    override fun comparator(): Comparator<in K> {
        return nullsLast(cmp!!)
    }

//    override fun entrySet(): Set<Map.Entry<K, V>?>? {
//        return EntrySet()
//    }

    private var _values: MutableCollection<V>? = null
    override val values: MutableCollection<V>
        get() {
            if (_values == null) {
                _values = object : AbstractMutableCollection<V>() {
                    override operator fun contains(element: @UnsafeVariance V): Boolean = containsValue(element)

                    override fun add(element: V): Boolean {
                        TODO("Not yet implemented")
                    }

                    override operator fun iterator(): MutableIterator<V> {
                        val entryIterator = entries.iterator()
                        return object : MutableIterator<V> {
                            override fun hasNext(): Boolean = entryIterator.hasNext()
                            override fun next(): V = entryIterator.next().value
                            override fun remove() {
                                TODO("Not yet implemented")
                            }
                        }
                    }

                    override val size: Int get() = this@TreeMap.size
                }
            }
            return _values!!
        }


    override fun headMap(toKey: K, inclusive: Boolean): NavigableMap<K, V> {
        return SubMap(SubMapType.Head, null, false, toKey, inclusive)
    }

    override fun put(key: K, value: V): V? {
        val node = Node(key, value)
        val state = State<V>()
        root = insert(root, node, state)
        if (!state.found) {
            ++size
        }
        root!!.isRed = false
        return state.value
    }

    override fun remove(k: K): V? {
        val state = State<V>()
        removeWithState(k, state)
        return state.value
    }

    override fun subMap(
        fromKey: K, fromInclusive: Boolean,
        toKey: K, toInclusive: Boolean
    ): NavigableMap<K, V> {
        return SubMap(SubMapType.Range, fromKey, fromInclusive, toKey, toInclusive)
    }

    override fun tailMap(fromKey: K, inclusive: Boolean): NavigableMap<K, V> {
        return SubMap(SubMapType.Tail, fromKey, inclusive, null, false)
    }

    /**
     * Returns the first node which compares greater than the given key.
     *
     * @param key the key to search for
     * @return the next node, or null if there is none
     */
    private fun getNodeAfter(key: K, inclusive: Boolean): Node<K, V>? {
        var foundNode: Node<K, V>? = null
        var node = root
        while (node != null) {
            val c: Int = cmp!!.compare(key, node.key)
            if (inclusive && c == 0) {
                return node
            }
            if (c >= 0) {
                node = node.child[RIGHT]
            } else {
                foundNode = node
                node = node.child[LEFT]
            }
        }
        return foundNode
    }

    /**
     * Returns the last node which is strictly less than the given key.
     *
     * @param key the key to search for
     * @return the previous node, or null if there is none
     */
    private fun getNodeBefore(key: K, inclusive: Boolean): Node<K, V>? {
        var foundNode: Node<K, V>? = null
        var node = root
        while (node != null) {
            val c: Int = cmp!!.compare(key, node.key)
            if (inclusive && c == 0) {
                return node
            }
            if (c <= 0) {
                node = node.child[LEFT]
            } else {
                foundNode = node
                node = node.child[RIGHT]
            }
        }
        return foundNode
    }

    /**
     * Used for testing. Validate that the tree meets all red-black correctness
     * requirements. These include:
     *
     * <pre>
     * - root is black
     * - no children of a red node may be red
     * - the black height of every path through the three to a leaf is exactly the same
    </pre> *
     *
     * @throws RuntimeException if any correctness errors are detected.
     */
    fun assertCorrectness() {
        assertCorrectness(root, true)
    }

    override fun descendingEntryIterator(): MutableIterator<MutableMap.MutableEntry<K, V>> {
        return DescendingEntryIterator()
    }

    override fun entryIterator(): MutableIterator<MutableMap.MutableEntry<K, V>> {
        return EntryIterator()
    }

    /**
     * Internal helper function for public [.assertCorrectness].
     *
     * @param tree the subtree to validate.
     * @param isRed true if the parent of this node is red.
     * @return the black height of this subtree.
     * @throws RuntimeException if this RB-tree is not valid.
     */
    private fun assertCorrectness(tree: Node<K, V>?, isRed: Boolean): Int {
        if (tree == null) {
            return 0
        }
        if (isRed && tree.isRed) {
            throw RuntimeException("Two red nodes adjacent")
        }
        val leftNode = tree.child[LEFT]
        if ((leftNode != null
                    && cmp!!.compare(leftNode.key, tree.key) > 0)
        ) {
            throw RuntimeException(
                ("Left child " + leftNode
                        + " larger than " + tree)
            )
        }
        val rightNode = tree.child[RIGHT]
        if ((rightNode != null
                    && cmp!!.compare(rightNode.key, tree.key) < 0)
        ) {
            throw RuntimeException(
                ("Right child " + rightNode
                        + " smaller than " + tree)
            )
        }
        val leftHeight = assertCorrectness(leftNode, tree.isRed)
        val rightHeight = assertCorrectness(rightNode, tree.isRed)
        if ((leftHeight != 0) && (rightHeight != 0) && (leftHeight != rightHeight)) {
            throw RuntimeException("Black heights don't match")
        }
        return if (tree.isRed) leftHeight else leftHeight + 1
    }

    /**
     * Finds an entry given a key and returns the node.
     *
     * @param key the search key
     * @return the node matching the key or null
     */
    override fun getEntry(key: K): Map.Entry<K, V>? {
        var tree = root
        while (tree != null) {
            val c: Int = cmp!!.compare(key, tree.key)
            if (c == 0) {
                return tree as Map.Entry<K, V>
            }
            val childNum = if (c < 0) LEFT else RIGHT
            tree = tree.child[childNum]
        }
        return null
    }

    /**
     * Returns the left-most node of the tree, or null if empty.
     */
    override val firstEntryField: Map.Entry<K, V>?
        get() {
            if (root == null) {
                return null
            }
            var node: Node<K, V>? = root
            var nextNode: Node<K, V>
            while ((node!!.child[LEFT].also { nextNode = (it)!! }) != null) {
                node = nextNode
            }
            return node as Map.Entry<K, V>
        }

    /**
     * Returns the right-most node of the tree, or null if empty.
     */
    override val lastEntryField: Map.Entry<K, V>?
        get() {
            if (root == null) {
                return null
            }
            var node: Node<K, V> = root!!
            var nextNode: Node<K, V>
            while ((node.child[RIGHT].also { nextNode = (it)!! }) != null) {
                node = nextNode
            }
            return node as Map.Entry<K, V>
        }

    override fun getCeilingEntry(key: K): Map.Entry<K, V>? {
        return getNodeAfter(key, true) as Map.Entry<K, V>
    }

    override fun getFloorEntry(key: K): Map.Entry<K, V>? {
        return getNodeBefore(key, true) as Map.Entry<K, V>
    }

    override fun getHigherEntry(key: K): Map.Entry<K, V>? {
        return getNodeAfter(key, false) as Map.Entry<K, V>
    }

    override fun getLowerEntry(key: K): Map.Entry<K, V>? {
        return getNodeBefore(key, false) as Map.Entry<K, V>
    }

    override fun removeEntry(entry: Map.Entry<K, V>): Boolean {
        val state = State<V>()
        state.matchValue = true
        state.value = entry.value
        return removeWithState(entry.key, state)
    }

    private fun inOrderAdd(
        list: MutableList<MutableMap.MutableEntry<K, V>>, type: SubMapType, current: Node<K, V>?,
        fromKey: K?, fromInclusive: Boolean, toKey: K?, toInclusive: Boolean
    ) {
        if (current == null) {
            return
        }
        // TODO: truncate this recursion if the whole subtree is known to be
        // outside of bounds?
        val leftNode = current.child[LEFT]
        leftNode?.let { inOrderAdd(list, type, it, fromKey, fromInclusive, toKey, toInclusive) }
        if (inRange(type, current.key!!, fromKey, fromInclusive, toKey, toInclusive)) {
            list.add(current as MutableMap.MutableEntry<K, V>)
        }
        val rightNode = current.child[RIGHT]
        rightNode?.let { inOrderAdd(list, type, it, fromKey, fromInclusive, toKey, toInclusive) }
    }

    private fun inRange(
        type: SubMapType, key: K,
        fromKey: K?, fromInclusive: Boolean, toKey: K?, toInclusive: Boolean
    ): Boolean {
        if (type.fromKeyValid() && smaller(key, fromKey, !fromInclusive)) {
            return false
        }
        return !(type.toKeyValid() && larger(key, toKey, !toInclusive))
    }

    /**
     * Insert a node into a subtree, collecting state about the insertion.
     *
     * If the same key already exists, the value of the node is overwritten with
     * the value from the new node instead.
     *
     * @param tree subtree to insert into
     * @param newNode new node to insert
     * @param state result of the insertion: state.found true if the key already
     * existed in the tree state.value the old value if the key existed
     * @return the new subtree root
     */
    private fun insert(tree: Node<K, V>?, newNode: Node<K, V>, state: State<V>): Node<K, V>? {
        var tree = tree
        if (tree == null) {
            return newNode
        } else {
            val c: Int = cmp!!.compare(newNode.key, tree.key)
            if (c == 0) {
                state.value = tree.setValue(newNode.value)
                state.found = true
                return tree
            }
            val childNum = if (c < 0) LEFT else RIGHT
            tree.child[childNum] = insert(tree.child[childNum], newNode, state)
            if (isRed(tree.child[childNum])) {
                if (isRed(tree.child[otherChild(childNum)])) {
                    // both children are red (nulls are black), make both black and me red
                    tree.isRed = true
                    tree.child[LEFT]!!.isRed = false
                    tree.child[RIGHT]!!.isRed = false
                } else {
                    //
                    if (isRed(tree.child[childNum]!!.child[childNum])) {
                        tree = rotateSingle(tree, otherChild(childNum))
                    } else if (isRed(tree.child[childNum]!!.child[otherChild(childNum)])) {
                        tree = rotateDouble(tree, otherChild(childNum))
                    }
                }
            }
        }
        return tree
    }

    /**
     * Returns true if `node` is red. Note that null pointers are
     * considered black.
     */
    private fun isRed(node: Node<K, V>?): Boolean {
        return node != null && node.isRed
    }

    /**
     * Returns true if `a` is greater than or equal to `b`.
     */
    private fun larger(a: K, b: K?, orEqual: Boolean): Boolean {
        val compare: Int = cmp!!.compare(a, b)
        return compare > 0 || (orEqual && compare == 0)
    }

    /**
     * Returns true if `a` is less than or equal to `b`.
     */
    private fun smaller(a: K, b: K?, orEqual: Boolean): Boolean {
        val compare: Int = cmp!!.compare(a, b)
        return compare < 0 || (orEqual && compare == 0)
    }

    /**
     * Remove a key from the tree, returning whether it was found and its value.
     *
     * @param key key to remove
     * @param state return state, not null
     * @return true if the value was found
     */
    private fun removeWithState(key: K, state: State<V>): Boolean {
        if (root == null) {
            return false
        }
        var found: Node<K, V>? = null
        var parent: Node<K, V>? = null

        // create a fake tree root to minimize special cases for changing the root
        val head = Node<K, V>(null, null)
        var dir = RIGHT
        head.child[RIGHT] = root
        var node: Node<K, V>? = head
        while (node!!.child[dir] != null) {
            val last = dir
            val grandparent = parent
            parent = node
            node = node.child[dir]
            val c: Int = cmp!!.compare(key, node!!.key)
            dir = if (c < 0) LEFT else RIGHT
            if (c == 0 && (!state.matchValue || node.value == state.value)) {
                found = node
            }
            if (!isRed(node) && !isRed(node.child[dir])) {
                if (isRed(node.child[otherChild(dir)])) {
                    parent.child[last] = rotateSingle(node, dir)
                    parent = parent.child[last]
                } else if (!isRed(node.child[otherChild(dir)])) {
                    val sibling = parent.child[otherChild(last)]
                    if (sibling != null) {
                        if ((!isRed(sibling.child[otherChild(last)])
                                    && !isRed(sibling.child[last]))
                        ) {
                            parent.isRed = false
                            sibling.isRed = true
                            node.isRed = true
                        } else {
                            assert(grandparent != null)
                            val dir2 = if (grandparent!!.child[RIGHT] === parent) RIGHT else LEFT
                            if (isRed(sibling.child[last])) {
                                grandparent!!.child[dir2] = rotateDouble(parent, last)
                            } else if (isRed(sibling.child[otherChild(last)])) {
                                grandparent!!.child[dir2] = rotateSingle(parent, last)
                            }
                            grandparent!!.child[dir2]!!.isRed = true
                            node.isRed = grandparent.child[dir2]!!.isRed
                            grandparent.child[dir2]!!.child[LEFT]!!.isRed = false
                            grandparent.child[dir2]!!.child[RIGHT]!!.isRed = false
                        }
                    }
                }
            }
        }
        if (found != null) {
            state.found = true
            state.value = found.value
            /**
             * put the "node" values in "found" (the node with key K) and cut "node"
             * out. However, we do not want to corrupt "found" -- issue 3423. So
             * create a new node "newNode" to replace the "found" node.
             *
             * TODO: (jat's suggestion) Consider using rebalance to move the deleted
             * node to a leaf to avoid the extra traversal in replaceNode.
             */
            if (node !== found) {
                val newNode = Node(node.key, node.value)
                replaceNode(head, found, newNode)
                if (parent === found) {
                    parent = newNode
                }
            }

            // cut "node" out
            parent!!.child[if (parent.child[RIGHT] === node) RIGHT else LEFT] =
                node.child[if (node.child[LEFT] == null) RIGHT else LEFT]
            size--
        }
        root = head.child[RIGHT]
        if (root != null) {
            root!!.isRed = false
        }
        return state.found
    }

    /**
     * replace 'node' with 'newNode' in the tree rooted at 'head'. Could have
     * avoided this traversal if each node maintained a parent pointer.
     */
    private fun replaceNode(head: Node<K, V>, node: Node<K, V>, newNode: Node<K, V>) {
        var parent: Node<K, V> = head
        var direction = if ((parent.key == null || cmp!!.compare(
                node.key,
                parent.key
            ) > 0)
        ) RIGHT else LEFT // parent.key == null handles the fake root node
        while (parent.child[direction] !== node) {
            parent = parent.child[direction]!!
            assert(parent != null)
            direction = if (cmp!!.compare(node.key, parent.key) > 0) RIGHT else LEFT
        }
        // replace node with newNode
        parent.child[direction] = newNode
        newNode.isRed = node.isRed
        newNode.child[LEFT] = node.child[LEFT]
        newNode.child[RIGHT] = node.child[RIGHT]
        node.child[LEFT] = null
        node.child[RIGHT] = null
    }

    private fun assert(b: Boolean) {
        if (!b)
            TODO("Something went wrong")
    }

    /**
     * Perform a double rotation, first rotating the child which will become the
     * root in the opposite direction, then rotating the root in the specified
     * direction.
     *
     * <pre>
     * A                                               F
     * B   C    becomes (with rotateDirection=0)       A   C
     * D E F G                                         B E   G
     * D
    </pre> *
     *
     * @param tree root of the subtree to rotate
     * @param rotateDirection the direction to rotate: 0=left, 1=right
     * @return the new root of the rotated subtree
     */
    private fun rotateDouble(tree: Node<K, V>?, rotateDirection: Int): Node<K, V> {
        // free the pointer of the new root
        val otherChildDir = otherChild(rotateDirection)
        tree!!.child[otherChildDir] = rotateSingle(tree.child[otherChildDir], otherChildDir)
        return rotateSingle(tree, rotateDirection)
    }

    /**
     * Perform a single rotation, pushing the root of the subtree to the specified
     * direction.
     *
     * <pre>
     * A                                              B
     * B   C     becomes (with rotateDirection=1)     D   A
     * D E                                              E   C
    </pre> *
     *
     * @param tree the root of the subtree to rotate
     * @param rotateDirection the direction to rotate: 0=left rotation, 1=right
     * @return the new root of the rotated subtree
     */
    private fun rotateSingle(tree: Node<K, V>?, rotateDirection: Int): Node<K, V> {
        val otherChildDir = otherChild(rotateDirection)
        val save = tree!!.child[otherChildDir]
        tree.child[otherChildDir] = save!!.child[rotateDirection]
        save.child[rotateDirection] = tree
        tree.isRed = true
        save.isRed = false
        return (save)
    }

    companion object {
        private const val LEFT = 0
        private const val RIGHT = 1
        private fun otherChild(child: Int): Int {
//            assert((child == 0 || child == 1))
            return 1 - child
        }
    }

    init {
        root = null
        cmp = c
    }
}
