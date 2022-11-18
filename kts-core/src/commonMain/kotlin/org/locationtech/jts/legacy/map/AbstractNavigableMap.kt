/*
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
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

import org.locationtech.jts.legacy.SortedSet

/**
 * Skeletal implementation of a NavigableMap.
 */
abstract class AbstractNavigableMap<K, V> : AbstractMap<K, V>(), NavigableMap<K, V> {
    internal inner class DescendingMap : AbstractNavigableMap<K, V>() {
        override fun clear() {
            ascendingMap().clear()
        }

        override fun comparator(): Comparator<in K> {
            val comparator = ascendingMap().comparator()
            return comparator.reversed()
        }

        override fun descendingMap(): NavigableMap<K, V> {
            return ascendingMap()
        }

        override fun headMap(toKey: K, inclusive: Boolean): NavigableMap<K, V> {
            return ascendingMap().tailMap(toKey, inclusive).descendingMap()
        }

        override fun put(key: K, value: V): V? {
            return ascendingMap().put(key, value)
        }

        override fun remove(key: K): V? {
            return ascendingMap().remove(key)
        }

        override val size = ascendingMap().size

        override fun subMap(
            fromKey: K, fromInclusive: Boolean,
            toKey: K, toInclusive: Boolean
        ): NavigableMap<K, V> {
            return ascendingMap().subMap(toKey, toInclusive, fromKey, fromInclusive).descendingMap()
        }

        override fun tailMap(fromKey: K, inclusive: Boolean): NavigableMap<K, V> {
            return ascendingMap().headMap(fromKey, inclusive).descendingMap()
        }

        fun ascendingMap(): AbstractNavigableMap<K, V> {
            return this@AbstractNavigableMap
        }

        override fun descendingEntryIterator(): MutableIterator<MutableMap.MutableEntry<K, V>> {
            return ascendingMap().entryIterator()
        }

        override fun entryIterator(): MutableIterator<MutableMap.MutableEntry<K, V>> {
            return ascendingMap().descendingEntryIterator()
        }

        override fun getEntry(key: K): Map.Entry<K, V>? {
            return ascendingMap().getEntry(key)
        }

        override fun firstEntry(): Map.Entry<K, V>? {
            return ascendingMap().lastEntry()
        }

        override fun lastEntry(): Map.Entry<K, V>? {
            return ascendingMap().firstEntry()
        }

        override fun getCeilingEntry(key: K): Map.Entry<K, V>? {
            return ascendingMap().getFloorEntry(key)
        }

        override fun getFloorEntry(key: K): Map.Entry<K, V>? {
            return ascendingMap().getCeilingEntry(key)
        }

        override fun getHigherEntry(key: K): Map.Entry<K, V>? {
            return ascendingMap().getLowerEntry(key)
        }

        override fun getLowerEntry(key: K): Map.Entry<K, V>? {
            return ascendingMap().getHigherEntry(key)
        }

        override fun removeEntry(entry: Map.Entry<K, V>): Boolean {
            return ascendingMap().removeEntry(entry)
        }

        override val firstEntryField: Map.Entry<K, V>
            get() = firstEntry()!!
        override val lastEntryField: Map.Entry<K, V>
            get() = lastEntry()!!

        override fun putAll(from: Map<out K, V>) {
            TODO("Not yet implemented")
        }
    }

    internal open inner class EntrySet : AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {
//        override operator fun contains(o: Any): Boolean {
//            return o is Map.Entry<*, *> && containsEntry(o)
//        }

//        override operator fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> {
//            return entryIterator()
//        }

        override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> {
            return entryIterator()
        }

        override fun add(element: MutableMap.MutableEntry<K, V>): Boolean {
            TODO("Not yet implemented")
        }

//        override fun remove(o: Any): Boolean {
//            if (o is Map.Entry<*, *>) {
//                val entry = o as Map.Entry<K, V>
//                return removeEntry(entry)
//            }
//            return false
//        }

        override val size: Int
            get() = this@AbstractNavigableMap.size
    }

    private class NavigableKeySet<K, V>(map: NavigableMap<K, V>) : AbstractSet<K>(),
        NavigableSet<K> {
        private val map: NavigableMap<K, V> = map
        override fun ceiling(k: K): K? {
            return map.ceilingKey(k)
        }

        override fun clear() {
            map.clear()
        }

        override fun comparator(): Comparator<in K> {
            return map.comparator()
        }

//        override operator fun contains(o: Any): Boolean {
//            return map.containsKey(o)
//        }

        override fun descendingIterator(): Iterator<K> {
            return descendingSet().iterator()
        }

        override fun descendingSet(): NavigableSet<K> {
            return map.descendingMap().navigableKeySet()
        }

        override fun first(): K {
            return map.firstKey()
        }

        override fun floor(k: K): K? {
            return map.floorKey(k)
        }

        override fun headSet(toElement: K): SortedSet<K> {
            return headSet(toElement, false)
        }

        override fun headSet(toElement: K, inclusive: Boolean): NavigableSet<K> {
            return map.headMap(toElement, inclusive).navigableKeySet()
        }

        override fun higher(k: K): K? {
            return map.higherKey(k)
        }

        override operator fun iterator(): MutableIterator<K> {
            val entryIterator: MutableIterator<Map.Entry<K, V>> = map.entries.iterator()
            return object : MutableIterator<K> {
                override fun hasNext(): Boolean {
                    return entryIterator.hasNext()
                }

                override fun next(): K {
                    val entry = entryIterator.next()
                    return entry.key
                }

                override fun remove() {
                    entryIterator.remove()
                }
            }
        }

        override fun last(): K {
            return map.lastKey()
        }

        override fun lower(k: K): K? {
            return map.lowerKey(k)
        }

        override fun pollFirst(): K? {
            return getEntryKeyOrNull(map.pollFirstEntry())
        }

        override fun pollLast(): K? {
            return getEntryKeyOrNull(map.pollLastEntry())
        }

        override fun remove(o: K): Boolean {
            if (map.containsKey(o)) {
                map.remove(o)
                return true
            }
            return false
        }

        override val size: Int
            get() = map.size

        override fun subSet(
            fromElement: K, fromInclusive: Boolean,
            toElement: K, toInclusive: Boolean
        ): NavigableSet<K> {
            return map.subMap(fromElement, fromInclusive, toElement, toInclusive).navigableKeySet()
        }

        override fun subSet(fromElement: K, toElement: K): SortedSet<K> {
            return subSet(fromElement, true, toElement, false)
        }

        override fun tailSet(fromElement: K): SortedSet<K> {
            return tailSet(fromElement, true)
        }

        override fun tailSet(fromElement: K, inclusive: Boolean): NavigableSet<K> {
            return map.tailMap(fromElement, inclusive).navigableKeySet()
        }

        override fun add(element: K): Boolean {
            TODO("Not yet implemented")
        }

        override fun addAll(elements: Collection<K>): Boolean {
            TODO("Not yet implemented")
        }

        override fun removeAll(elements: Collection<K>): Boolean {
            TODO("Not yet implemented")
        }

        override fun retainAll(elements: Collection<K>): Boolean {
            TODO("Not yet implemented")
        }

    }

    override fun ceilingEntry(key: K): Map.Entry<K, V> {
        return copyOf(getCeilingEntry(key))!!
    }

    override fun ceilingKey(key: K): K? {
        return getEntryKeyOrNull(getCeilingEntry(key))
    }

    override fun containsKey(k: K): Boolean {
        return getEntry(k) != null
    }

    override fun descendingKeySet(): NavigableSet<K> {
        return descendingMap().navigableKeySet()
    }

    override fun descendingMap(): NavigableMap<K, V> {
        return DescendingMap()
    }

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = EntrySet()

    override fun firstEntry(): Map.Entry<K, V>? {
        return copyOf(firstEntryField)
    }

    override fun firstKey(): K {
        return getKeyOrNSE(firstEntryField)
    }

    override fun floorEntry(key: K): Map.Entry<K, V> {
        return copyOf(getFloorEntry(key))!!
    }

    override fun floorKey(key: K): K? {
        return getEntryKeyOrNull(getFloorEntry(key))
    }

    override operator fun get(k: K): V? {
        return getEntryValueOrNull(getEntry(k))
    }

    override fun headMap(toKey: K): SortedMap<K, V> {
        return headMap(toKey, false)
    }

    override fun higherEntry(key: K): Map.Entry<K, V> {
        return copyOf(getHigherEntry(key))!!
    }

    override fun higherKey(key: K): K? {
        return getEntryKeyOrNull(getHigherEntry(key))
    }

    override val keys: MutableSet<K>
        get() = navigableKeySet()

    override fun lastEntry(): Map.Entry<K, V>? {
        return copyOf(lastEntryField)!!
    }

    override fun lastKey(): K {
        return getKeyOrNSE(lastEntryField)
    }

    override fun lowerEntry(key: K): Map.Entry<K, V> {
        return copyOf(getLowerEntry(key))!!
    }

    override fun lowerKey(key: K): K? {
        return getEntryKeyOrNull(getLowerEntry(key))
    }

    override fun navigableKeySet(): NavigableSet<K> {
        return NavigableKeySet(this)
    }

    override fun pollFirstEntry(): Map.Entry<K, V> {
        return pollEntry(firstEntryField)
    }

    override fun pollLastEntry(): Map.Entry<K, V> {
        return pollEntry(lastEntryField)
    }

    override fun subMap(fromKey: K, toKey: K): SortedMap<K, V> {
        return subMap(fromKey, true, toKey, false)
    }

    override fun tailMap(fromKey: K): SortedMap<K, V> {
        return tailMap(fromKey, true)
    }

    override fun headMap(toKey: K, inclusive: Boolean): NavigableMap<K, V> {
        TODO("Not yet implemented")
    }

    override fun subMap(fromKey: K, fromInclusive: Boolean, toKey: K, toInclusive: Boolean): NavigableMap<K, V> {
        TODO("Not yet implemented")
    }

    override fun tailMap(fromKey: K, inclusive: Boolean): NavigableMap<K, V> {
        TODO("Not yet implemented")
    }

    override val values: MutableCollection<V>
        get() = TODO("Not yet implemented")

    override fun comparator(): Comparator<in K> {
        TODO("Not yet implemented")
    }

    override fun clear() {
        TODO("Not yet implemented")
    }

    override fun put(key: K, value: V): V? {
        TODO("Not yet implemented")
    }

    override fun putAll(from: Map<out K, V>) {
        TODO("Not yet implemented")
    }

    override fun remove(key: K): V? {
        TODO("Not yet implemented")
    }

    fun containsEntry(entry: Map.Entry<*, *>): Boolean {
        val key = entry.key as K
        val lookupEntry = getEntry(key)
        return lookupEntry != null && lookupEntry.value == entry.value
    }

    /**
     * Returns an iterator over the entries in this map in descending order.
     */
    abstract fun descendingEntryIterator(): MutableIterator<MutableMap.MutableEntry<K, V>>

    /**
     * Returns an iterator over the entries in this map in ascending order.
     */
    abstract fun entryIterator(): MutableIterator<MutableMap.MutableEntry<K, V>>

    /**
     * Returns the entry corresponding to the specified key. If no such entry exists returns
     * `null`.
     */
    abstract fun getEntry(key: K): Map.Entry<K, V>?

    /**
     * Returns the first entry or `null` if map is empty.
     */
    abstract val firstEntryField: Map.Entry<K, V>?

    /**
     * Returns the last entry or `null` if map is empty.
     */
    abstract val lastEntryField: Map.Entry<K, V>?

    /**
     * Gets the entry corresponding to the specified key or the entry for the least key greater than
     * the specified key. If no such entry exists returns `null`.
     */
    abstract fun getCeilingEntry(key: K): Map.Entry<K, V>?

    /**
     * Gets the entry corresponding to the specified key or the entry for the greatest key less than
     * the specified key. If no such entry exists returns `null`.
     */
    abstract fun getFloorEntry(key: K): Map.Entry<K, V>?

    /**
     * Gets the entry for the least key greater than the specified key. If no such entry exists
     * returns `null`.
     */
    abstract fun getHigherEntry(key: K): Map.Entry<K, V>?

    /**
     * Returns the entry for the greatest key less than the specified key. If no such entry exists
     * returns `null`.
     */
    abstract fun getLowerEntry(key: K): Map.Entry<K, V>?

    /**
     * Remove an entry from the tree, returning whether it was found.
     */
    abstract fun removeEntry(entry: Map.Entry<K, V>): Boolean
    private fun pollEntry(entry: Map.Entry<K, V>?): Map.Entry<K, V> {
        entry?.let { removeEntry(it) }
        return copyOf(entry)!!
    }

    companion object {
        private fun <K, V> copyOf(entry: Map.Entry<K, V>?): Map.Entry<K, V>? {
            return entry?.let { SimpleImmutableEntry(it) }
        }

        private fun <K, V> getKeyOrNSE(entry: Map.Entry<K, V>?): K {
            if (entry == null) {
                throw NoSuchElementException()
            }
            return entry.key
        }

        open fun <K, V> getEntryKeyOrNull(entry: Map.Entry<K, V>?): K? {
            return entry?.key
        }

        open fun <K, V> getEntryValueOrNull(entry: Map.Entry<K, V>?): V? {
            return entry?.value
        }
    }

    /**
     * An immutable [Map.Entry] shared by several [Map] implementations.
     */
    class SimpleImmutableEntry<K, V> : AbstractEntry<K, V> {
        constructor(key: K, value: V) : super(key, value)
        constructor(entry: Map.Entry<K, V>) : super(entry.key, entry.value)

        override fun setValue(value: V): V {
            throw UnsupportedOperationException()
        }
    }

    /**
     * Basic [Map.Entry] implementation used by [SimpleEntry]
     * and [SimpleImmutableEntry].
     */
    abstract class AbstractEntry<K, V> protected constructor(override val key: K, override var value: V) :
        MutableMap.MutableEntry<K, V> {

        override fun setValue(value: V): V {
            val oldValue = this.value
            this.value = value
            return oldValue
        }

        override fun equals(other: Any?): Boolean {
            if (other !is Map.Entry<*, *>) {
                return false
            }
            return (key == other.key
                    && value == other.value)
        }

        /**
         * Calculate the hash code using Sun's specified algorithm.
         */
        override fun hashCode(): Int {
            return key.hashCode() xor value.hashCode()
        }

        override fun toString(): String {
            // for compatibility with the real Jre: issue 3422
            return key.toString() + "=" + value
        }
    }

    /**
     * A mutable [Map.Entry] shared by several [Map] implementations.
     */
    open class SimpleEntry<K, V> : AbstractEntry<K, V> {
        constructor(key: K, value: V) : super(key, value)
        constructor(entry: Map.Entry<K, V>) : super(entry.key, entry.value)
    }
}
