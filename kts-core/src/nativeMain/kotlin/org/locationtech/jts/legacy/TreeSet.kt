package org.locationtech.jts.legacy

import kotlin.math.abs

actual class TreeSet<E : Comparable<E>> actual constructor(
    val comparator: Comparator<E>,
    collection: Collection<E>
) : AbstractMutableSet<E>() {
    private val store = ArrayList<E>().apply {
        addAll(collection.toSet())
        sortWith(comparator)
    }

    override val size: Int
        get() = store.size

    override fun add(element: E): Boolean {
        val index = store.binarySearch(element, comparator)
        return if (index >= 0)
            false
        else {
            store.add(abs(index) - 1, element)
            true
        }
    }

    override fun iterator() = store.iterator()

    actual fun higher(holeCoord: E): E {
        TODO("Not yet implemented")
    }

    actual fun lower(holeCoord: E): E {
        TODO("Not yet implemented")
    }
}