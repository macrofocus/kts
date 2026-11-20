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

    actual override val size: Int
        get() = store.size

    actual override fun add(element: E): Boolean {
        val index = store.binarySearch(element, comparator)
        return if (index >= 0)
            false
        else {
            store.add(abs(index) - 1, element)
            true
        }
    }

    actual override fun iterator() = store.iterator()

    // Found in the sorted elements as java.util.TreeSet finds them: they threw NotImplementedError, so that the
    // triangulation of every polygon with a hole failed in the browser (PolygonHoleJoiner; B5 of the review)
    actual fun higher(holeCoord: E): E? {
        val index = store.binarySearch(holeCoord, comparator)
        val next = if (index >= 0) index + 1 else -index - 1
        return store.getOrNull(next)
    }

    actual fun lower(holeCoord: E): E? {
        val index = store.binarySearch(holeCoord, comparator)
        val previous = if (index >= 0) index - 1 else -index - 2
        return store.getOrNull(previous)
    }
}