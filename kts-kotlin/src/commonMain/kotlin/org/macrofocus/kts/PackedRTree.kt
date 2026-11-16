/*
 * Copyright (c) 2026 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.macrofocus.kts

import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry
import kotlin.math.floor

/**
 * A static R-tree packed along a Hilbert curve, after Vladimir Agafonkin's Flatbush: the boxes of the items and of the
 * nodes are in one `DoubleArray`, the tree in one `IntArray`. Compared to JTS' `STRtree` it has no node or Envelope
 * objects, builds by sorting a `LongArray`, and [query] is inline: it walks the tree with an `IntArray` as a stack and
 * calls the lambda with the items found, without building a list or boxing a visitor.
 *
 * Built once with a [Builder] (or [toPackedRTree]); immutable then, so it can be shared between threads.
 */
class PackedRTree<T> @PublishedApi internal constructor(
    @PublishedApi internal val items: Array<Any?>,
    /** minX, minY, maxX, maxY of the items (in their order, sorted along the curve), then of the nodes level by level. */
    @PublishedApi internal val boxes: DoubleArray,
    /** For an item, its index in [items]; for a node, the position in [boxes] of its first child. */
    @PublishedApi internal val indices: IntArray,
    /** The end in [boxes] of each level, the items being the first. */
    @PublishedApi internal val levelBounds: IntArray,
    @PublishedApi internal val nodeSize: Int,
) {
    val size: Int get() = items.size

    /** The envelope of all the items. */
    val bounds: Envelope
        get() = if (items.isEmpty()) Envelope() else {
            val r = boxes.size - 4
            envelope(boxes[r], boxes[r + 1], boxes[r + 2], boxes[r + 3])
        }

    /** The end in [boxes] of the level of the node at [position]. */
    @PublishedApi
    internal fun levelEnd(position: Int): Int {
        var lo = 0
        var hi = levelBounds.size - 1
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (levelBounds[mid] > position) hi = mid else lo = mid + 1
        }
        return levelBounds[lo]
    }

    /** Calls [action] with every item whose box intersects the given one (touching included), in no particular order. */
    @Suppress("UNCHECKED_CAST")
    inline fun query(minX: Double, minY: Double, maxX: Double, maxY: Double, action: (T) -> Unit) {
        if (items.isEmpty()) return
        val boxes = boxes
        val indices = indices
        val leaves = items.size * 4
        val stack = IntArray(levelBounds.size * nodeSize + 1)
        var top = 0
        var node = boxes.size - 4
        while (true) {
            val end = minOf(node + nodeSize * 4, levelEnd(node))
            var pos = node
            while (pos < end) {
                if (maxX >= boxes[pos] && maxY >= boxes[pos + 1] && minX <= boxes[pos + 2] && minY <= boxes[pos + 3]) {
                    val index = indices[pos shr 2]
                    if (node >= leaves) stack[top++] = index else action(items[index] as T)
                }
                pos += 4
            }
            if (top == 0) break
            node = stack[--top]
        }
    }

    inline fun query(envelope: Envelope, action: (T) -> Unit) {
        if (!envelope.isNull) query(envelope.minX, envelope.minY, envelope.maxX, envelope.maxY, action)
    }

    /** The items whose box intersects the given one. */
    fun query(minX: Double, minY: Double, maxX: Double, maxY: Double): List<T> =
        ArrayList<T>().also { result -> query(minX, minY, maxX, maxY) { result += it } }

    fun query(envelope: Envelope): List<T> = ArrayList<T>().also { result -> query(envelope) { result += it } }

    /** Whether the box of an item intersects the given one, stopping at the first. */
    fun any(minX: Double, minY: Double, maxX: Double, maxY: Double, predicate: (T) -> Boolean = { true }): Boolean {
        // A non-local return out of the inline query
        query(minX, minY, maxX, maxY) { if (predicate(it)) return true }
        return false
    }

    /**
     * The items closest to ([x], [y]), the closest first, by the distance to their box (exact for points): at most
     * [maxResults], none farther than [maxDistance], only those accepted by [filter].
     */
    fun neighbors(
        x: Double,
        y: Double,
        maxResults: Int = Int.MAX_VALUE,
        maxDistance: Double = Double.POSITIVE_INFINITY,
        filter: (T) -> Boolean = { true },
    ): List<T> {
        val result = ArrayList<T>()
        if (items.isEmpty() || maxResults <= 0) return result
        val maxDistanceSquared = maxDistance * maxDistance
        val leaves = items.size * 4
        val queue = DistanceQueue()
        var node = boxes.size - 4
        while (true) {
            val end = minOf(node + nodeSize * 4, levelEnd(node))
            var pos = node
            while (pos < end) {
                val index = indices[pos shr 2]
                val dx = axisDistance(x, boxes[pos], boxes[pos + 2])
                val dy = axisDistance(y, boxes[pos + 1], boxes[pos + 3])
                val distance = dx * dx + dy * dy
                if (distance <= maxDistanceSquared) {
                    if (node >= leaves) queue.push(index shl 1, distance)
                    else {
                        @Suppress("UNCHECKED_CAST")
                        if (filter(items[index] as T)) queue.push((index shl 1) + 1, distance)
                    }
                }
                pos += 4
            }
            // The items closer than any node left are the next closest
            while (queue.size > 0 && queue.peekId() and 1 == 1) {
                @Suppress("UNCHECKED_CAST")
                result += items[queue.pop() shr 1] as T
                if (result.size == maxResults) return result
            }
            if (queue.size == 0) break
            node = queue.pop() shr 1
        }
        return result
    }

    private fun axisDistance(k: Double, min: Double, max: Double): Double = if (k < min) min - k else if (k <= max) 0.0 else k - max

    /** Collects the boxes of the items, then [build]s the tree. */
    class Builder<T>(expectedSize: Int = 16, private val nodeSize: Int = 16) {
        init {
            require(nodeSize in 2..65535) { "The node size must be in 2..65535, not $nodeSize" }
        }

        private var items = arrayOfNulls<Any?>(maxOf(expectedSize, 1))
        private var boxes = DoubleArray(maxOf(expectedSize, 1) * 4)
        private var count = 0

        fun add(minX: Double, minY: Double, maxX: Double, maxY: Double, item: T): Builder<T> {
            if (count == items.size) {
                items = items.copyOf(count * 2)
                boxes = boxes.copyOf(count * 8)
            }
            val p = count * 4
            boxes[p] = minX; boxes[p + 1] = minY; boxes[p + 2] = maxX; boxes[p + 3] = maxY
            items[count++] = item
            return this
        }

        fun add(envelope: Envelope, item: T): Builder<T> =
            add(envelope.minX, envelope.minY, envelope.maxX, envelope.maxY, item)

        fun build(): PackedRTree<T> {
            val n = count
            val items = items.copyOf(n)
            if (n == 0) return PackedRTree(items, DoubleArray(0), IntArray(0), IntArray(0), nodeSize)

            // The levels: the items, then nodes of up to nodeSize children up to a single root
            val bounds = ArrayList<Int>()
            var levelSize = n
            var total = n
            bounds += n * 4
            do {
                levelSize = (levelSize + nodeSize - 1) / nodeSize
                total += levelSize
                bounds += total * 4
            } while (levelSize != 1)

            val boxes = DoubleArray(total * 4)
            val indices = IntArray(total)
            var minX = Double.POSITIVE_INFINITY
            var minY = Double.POSITIVE_INFINITY
            var maxX = Double.NEGATIVE_INFINITY
            var maxY = Double.NEGATIVE_INFINITY
            for (i in 0 until n) {
                val p = i * 4
                minX = minOf(minX, this.boxes[p]); minY = minOf(minY, this.boxes[p + 1])
                maxX = maxOf(maxX, this.boxes[p + 2]); maxY = maxOf(maxY, this.boxes[p + 3])
            }

            // The items sorted along the Hilbert curve of their centres: the value in the high 32 bits, the index in
            // the low ones, so that sorting a LongArray sorts both
            val keys = LongArray(n)
            val width = (maxX - minX).takeIf { it > 0 } ?: 1.0
            val height = (maxY - minY).takeIf { it > 0 } ?: 1.0
            val scale = 65535.0
            for (i in 0 until n) {
                val p = i * 4
                val hx = floor(scale * ((this.boxes[p] + this.boxes[p + 2]) / 2 - minX) / width).toInt()
                val hy = floor(scale * ((this.boxes[p + 1] + this.boxes[p + 3]) / 2 - minY) / height).toInt()
                keys[i] = ((hilbert(hx, hy).toLong() and 0xFFFFFFFFL) shl 32) or i.toLong()
            }
            keys.sort()
            for (k in 0 until n) {
                val i = (keys[k] and 0xFFFFFFFFL).toInt()
                this.boxes.copyInto(boxes, k * 4, i * 4, i * 4 + 4)
                indices[k] = i
            }

            // Each node: the union of the boxes of its children
            var pos = 0
            var out = n * 4
            for (level in 0 until bounds.size - 1) {
                val end = bounds[level]
                while (pos < end) {
                    val first = pos
                    var nMinX = boxes[pos]
                    var nMinY = boxes[pos + 1]
                    var nMaxX = boxes[pos + 2]
                    var nMaxY = boxes[pos + 3]
                    pos += 4
                    var j = 1
                    while (j < nodeSize && pos < end) {
                        nMinX = minOf(nMinX, boxes[pos]); nMinY = minOf(nMinY, boxes[pos + 1])
                        nMaxX = maxOf(nMaxX, boxes[pos + 2]); nMaxY = maxOf(nMaxY, boxes[pos + 3])
                        pos += 4
                        j++
                    }
                    indices[out shr 2] = first
                    boxes[out] = nMinX; boxes[out + 1] = nMinY; boxes[out + 2] = nMaxX; boxes[out + 3] = nMaxY
                    out += 4
                }
            }
            return PackedRTree(items, boxes, indices, bounds.toIntArray(), nodeSize)
        }
    }
}

/** A [PackedRTree] of these items, each under the box given by [envelope]. */
inline fun <T> Iterable<T>.toPackedRTree(nodeSize: Int = 16, envelope: (T) -> Envelope): PackedRTree<T> {
    val builder = PackedRTree.Builder<T>(if (this is Collection<*>) size else 16, nodeSize)
    for (item in this) builder.add(envelope(item), item)
    return builder.build()
}

/** A [PackedRTree] of these geometries, each under its envelope. */
fun <G : Geometry> Iterable<G>.toPackedRTree(nodeSize: Int = 16): PackedRTree<G> = toPackedRTree(nodeSize) { it.envelopeInternal }

/** A min-heap of ids by distance, in two primitive arrays. */
private class DistanceQueue {
    private var ids = IntArray(64)
    private var values = DoubleArray(64)
    var size = 0
        private set

    fun push(id: Int, value: Double) {
        if (size == ids.size) {
            ids = ids.copyOf(size * 2)
            values = values.copyOf(size * 2)
        }
        var pos = size++
        while (pos > 0) {
            val parent = (pos - 1) shr 1
            if (value >= values[parent]) break
            ids[pos] = ids[parent]
            values[pos] = values[parent]
            pos = parent
        }
        ids[pos] = id
        values[pos] = value
    }

    fun peekId(): Int = ids[0]

    fun pop(): Int {
        val top = ids[0]
        size--
        if (size > 0) {
            val id = ids[size]
            val value = values[size]
            var pos = 0
            val half = size shr 1
            while (pos < half) {
                var child = 2 * pos + 1
                if (child + 1 < size && values[child + 1] < values[child]) child++
                if (values[child] >= value) break
                ids[pos] = ids[child]
                values[pos] = values[child]
                pos = child
            }
            ids[pos] = id
            values[pos] = value
        }
        return top
    }
}

/** The position of ([x], [y]), both in 0..65535, along a Hilbert curve, as an unsigned 32-bit value. */
private fun hilbert(x: Int, y: Int): Int {
    var a = x xor y
    var b = 0xFFFF xor a
    var c = 0xFFFF xor (x or y)
    var d = x and (y xor 0xFFFF)

    var aa = a or (b ushr 1)
    var bb = (a ushr 1) xor a
    var cc = ((c ushr 1) xor (b and (d ushr 1))) xor c
    var dd = ((a and (c ushr 1)) xor (d ushr 1)) xor d

    a = aa; b = bb; c = cc; d = dd
    aa = (a and (a ushr 2)) xor (b and (b ushr 2))
    bb = (a and (b ushr 2)) xor (b and ((a xor b) ushr 2))
    cc = cc xor ((a and (c ushr 2)) xor (b and (d ushr 2)))
    dd = dd xor ((b and (c ushr 2)) xor ((a xor b) and (d ushr 2)))

    a = aa; b = bb; c = cc; d = dd
    aa = (a and (a ushr 4)) xor (b and (b ushr 4))
    bb = (a and (b ushr 4)) xor (b and ((a xor b) ushr 4))
    cc = cc xor ((a and (c ushr 4)) xor (b and (d ushr 4)))
    dd = dd xor ((b and (c ushr 4)) xor ((a xor b) and (d ushr 4)))

    a = aa; b = bb; c = cc; d = dd
    cc = cc xor ((a and (c ushr 8)) xor (b and (d ushr 8)))
    dd = dd xor ((b and (c ushr 8)) xor ((a xor b) and (d ushr 8)))

    a = cc xor (cc ushr 1)
    b = dd xor (dd ushr 1)

    var i0 = x xor y
    var i1 = b or (0xFFFF xor (i0 or a))

    i0 = (i0 or (i0 shl 8)) and 0x00FF00FF
    i0 = (i0 or (i0 shl 4)) and 0x0F0F0F0F
    i0 = (i0 or (i0 shl 2)) and 0x33333333
    i0 = (i0 or (i0 shl 1)) and 0x55555555

    i1 = (i1 or (i1 shl 8)) and 0x00FF00FF
    i1 = (i1 or (i1 shl 4)) and 0x0F0F0F0F
    i1 = (i1 or (i1 shl 2)) and 0x33333333
    i1 = (i1 or (i1 shl 1)) and 0x55555555

    return (i1 shl 1) or i0
}
