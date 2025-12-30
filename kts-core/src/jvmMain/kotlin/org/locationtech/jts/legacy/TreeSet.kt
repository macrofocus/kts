package org.locationtech.jts.legacy

actual class TreeSet<E : Comparable<E>> actual constructor(
    val comparator: Comparator<E>,
    collection: Collection<E>
) : AbstractMutableSet<E>() {
    private val store = java.util.TreeSet<E>(comparator)

    init {
        store.addAll(collection)
    }

    actual override val size: Int
        get() = store.size

    actual override fun add(element: E): Boolean {
        return store.add(element)
    }

    actual override fun iterator() = store.iterator()

    actual fun higher(holeCoord: E): E {
        return store.higher(holeCoord)
    }

    actual fun lower(holeCoord: E): E {
        return store.lower(holeCoord)
    }
}