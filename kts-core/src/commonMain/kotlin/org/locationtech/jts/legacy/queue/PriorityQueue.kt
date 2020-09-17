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
package org.locationtech.jts.legacy.queue

import kotlin.jvm.JvmOverloads

/**
 * An unbounded priority queue based on a priority heap.
 * See [
 * the official Java API doc](https://docs.oracle.com/javase/8/docs/api/java/util/PriorityQueue.html) for details.
 * A priority queue does not permit `null` elements.
 *
 * @param <E> element type.
</E> */
class PriorityQueue<E : Comparable<E>> @JvmOverloads constructor(
    initialCapacity: Int = DEFAULT_INITIAL_CAPACITY,
    private val cmp: Comparator<E> = naturalOrder()
) :
    AbstractQueue<E>() {

    /**
     * A heap held in an array. heap[0] is the root of the heap (the smallest
     * element), the subtrees of node i are 2*i+1 (left) and 2*i+2 (right). Node i
     * is a leaf node if 2*i>=n. Node i's parent, if i>0, is floor((i-1)/2).
     */
    private var heap: ArrayList<E>? = null

    constructor(c: Collection<E>) : this(c.size) {
        addAll(c)
    }

//    constructor<E : Comparable<E>(comparator: Comparator<E>) : this(DEFAULT_INITIAL_CAPACITY, comparator) {}
//    constructor(c: PriorityQueue<out E>) : this(c.size, c.comparator()) {
//        addAll(c)
//    }
//
//    constructor(c: SortedSet<out E>) : this(c.size, c.comparator() as Comparator<in E>) {
//        addAll(c)
//    }

    override fun addAll(c: Collection<E>): Boolean {
        checkArgument(c !== this)
        val oldSize: Int = heap!!.size
        for (e in c) {
            heap!!.add(e)
        }
        if (oldSize != heap!!.size) {
            makeHeap(0)
            return true
        }
        return false
    }

    override fun clear() {
        heap!!.clear()
    }

    fun comparator(): Comparator<in E> {
        return nullsLast(cmp)
    }

    override operator fun contains(o: E): Boolean {
        return indexOf(o) != -1
    }

    override operator fun iterator(): MutableIterator<E> {
        return object : MutableIterator<E> {
            var i = 0
            var last = -1
            override fun hasNext(): Boolean {
                return i < heap!!.size
            }

            override fun next(): E {
                checkElement(hasNext())
                last = i++
                return heap!!.get(last)
            }

            override fun remove() {
                checkState(last != -1)
                removeAtIndex(last.also { i = it })
                last = -1
            }
        }
    }

    override fun offer(e: E): Boolean {
        checkCriticalNotNull(e)
        var node: Int = heap!!.size
        heap!!.add(e)
        while (node > 0) {
            val childNode = node
            node = getParent(node)
            if (cmp.compare(heap!!.get(node), e) <= 0) {
                // parent is smaller, so we have a valid heap
                heap!!.set(childNode, e)
                return true
            }
            // exchange with parent and try again
            heap!!.set(childNode, heap!!.get(node))
        }
        heap!!.set(node, e)
        return true
    }

    override fun peek(): E? {
        return if (heap!!.isEmpty()) null else heap!!.get(0)
    }

    override fun poll(): E? {
        val value = peek()
        if (value != null) {
            removeAtIndex(0)
        }
        return value
    }

    override fun remove(o: E): Boolean {
        val index = indexOf(o)
        if (index < 0) {
            return false
        }
        removeAtIndex(index)
        return true
    }

    override fun removeAll(c: Collection<E>): Boolean {
        if (heap!!.removeAll(c)) {
            makeHeap(0)
            return true
        }
        return false
    }

    override fun retainAll(c: Collection<E>): Boolean {
        if (heap!!.retainAll(c)) {
            makeHeap(0)
            return true
        }
        return false
    }

    override val size: Int
        get() = heap!!.size

//    override fun spliterator(): Spliterator<E> {
//        return Spliterators.spliterator(this, Spliterator.NONNULL)
//    }

//    override fun toArray(): Array<E> {
//        return heap!!.toTypedArray()
//    }

//    override fun <T> toArray(a: Array<T>): Array<T> {
//        return heap.toArray(a)
//    }

    /**
     * Make the subtree rooted at `node` a valid heap. O(n) time
     *
     * @param node
     */
    private fun makeHeap(node: Int) {
        if (isLeaf(node)) {
            // leaf node are automatically valid heaps
            return
        }
        makeHeap(getLeftChild(node)) // make left subtree a heap
        // an interior node might not have a right child
        val rightChild = getRightChild(node)
        if (rightChild < heap!!.size) {
            makeHeap(rightChild) // make right subtree a heap
        }
        mergeHeaps(node)
    }

    /**
     * Merge two subheaps into a single heap. O(log n) time
     *
     * PRECONDITION: both children of `node` are heaps
     *
     * @param node the parent of the two subtrees to merge
     */
    private fun mergeHeaps(node: Int) {
        var node = node
        val heapSize: Int = heap!!.size
        val value: E = heap!!.get(node)
        while (!isLeaf(node, heapSize)) {
            val smallestChild = getSmallestChild(node, heapSize)
            if (cmp.compare(value, heap!!.get(smallestChild)) < 0) {
                // Current node is smaller than the smallest child, so we are done.
                break
            }
            // Move the smallest child up and iterate using its old slot.
            heap!!.set(node, heap!!.get(smallestChild))
            node = smallestChild
        }
        heap!!.set(node, value)
    }

    private fun getSmallestChild(node: Int, heapSize: Int): Int {
        var smallestChild: Int
        val leftChild = getLeftChild(node) // start with left child
        val rightChild = leftChild + 1
        smallestChild = leftChild
        if (rightChild < heapSize
            && cmp.compare(heap!!.get(rightChild), heap!!.get(leftChild)) < 0
        ) {
            // right child is smaller, go down that path
            smallestChild = rightChild
        }
        return smallestChild
    }

    private fun indexOf(o: Any?): Int {
        return if (o == null) -1 else heap!!.indexOf(o)
    }

    private fun isLeaf(node: Int): Boolean {
        return isLeaf(node, heap!!.size)
    }

    /**
     * This method leaves the elements at up to i-1, inclusive, untouched.
     * This information is used by PriorityQueue iterator implementation.
     */
    private fun removeAtIndex(index: Int) {
        // Remove the last element; put it in place of the really removed element.
        val lastValue: E = heap!!.removeAt(heap!!.size - 1)
        // Unless the last element was actually the one we wanted.
        if (index < heap!!.size) {
            // Move last element to the now-empty slot and reheap.
            heap!!.set(index, lastValue)
            mergeHeaps(index)
        }
    }

    companion object {
        private const val DEFAULT_INITIAL_CAPACITY = 11
        private fun getLeftChild(node: Int): Int {
            return 2 * node + 1
        }

        private fun getParent(node: Int): Int {
            return (node - 1) / 2
        }

        private fun getRightChild(node: Int): Int {
            return 2 * node + 2
        }

        private fun isLeaf(node: Int, size: Int): Boolean {
            return node * 2 + 1 >= size
        }
    }

    init {
        heap = ArrayList(initialCapacity)
    }
}
