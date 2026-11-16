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

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.prep.PreparedGeometry
import org.locationtech.jts.operation.union.UnaryUnionOp

// --- Spatial join -------------------------------------------------------------------------------------------------

/** The relation a [spatialJoin] keeps, read as "left `predicate` right". */
enum class SpatialPredicate {
    /** Only the envelopes intersect: no exact test, the fastest. */
    EnvelopesIntersect,
    Intersects, Contains, ContainsProperly, Within, Covers, CoveredBy, Touches, Crosses, Overlaps;

    internal fun test(left: Geometry, right: Geometry): Boolean = when (this) {
        EnvelopesIntersect -> true
        Intersects -> left.intersects(right)
        Contains -> left.contains(right)
        ContainsProperly -> left.relate(right, "T**FF*FF*")
        Within -> left.within(right)
        Covers -> left.covers(right)
        CoveredBy -> left.coveredBy(right)
        Touches -> left.touches(right)
        Crosses -> left.crosses(right)
        Overlaps -> left.overlaps(right)
    }

    internal fun test(left: PreparedGeometry, right: Geometry): Boolean = when (this) {
        EnvelopesIntersect -> true
        Intersects -> left.intersects(right)
        Contains -> left.contains(right)
        ContainsProperly -> left.containsProperly(right)
        Within -> left.within(right)
        Covers -> left.covers(right)
        CoveredBy -> left.coveredBy(right)
        Touches -> left.touches(right)
        Crosses -> left.crosses(right)
        Overlaps -> left.overlaps(right)
    }
}

/**
 * Calls [action] with every pair of items of this tree and [other] whose boxes intersect, the items of this tree
 * taken along its Hilbert curve (so that consecutive queries of [other] visit the same nodes).
 */
inline fun <T, U> PackedRTree<T>.join(other: PackedRTree<U>, action: (T, U) -> Unit) {
    for (k in 0 until size) {
        val p = k * 4
        @Suppress("UNCHECKED_CAST")
        val item = items[indices[k]] as T
        other.query(boxes[p], boxes[p + 1], boxes[p + 2], boxes[p + 3]) { action(item, it) }
    }
}

/** The right side of a join: its geometries, a tree of their indices, and their prepared forms, made on first use. */
private class JoinIndex<B>(right: List<B>, rightGeometry: (B) -> Geometry?) {
    val geometries: Array<Geometry?> = Array(right.size) { rightGeometry(right[it]) }
    val tree: PackedRTree<Int> = PackedRTree.Builder<Int>(right.size).apply {
        geometries.forEachIndexed { i, g -> if (g != null && !g.isEmpty) add(g.envelopeInternal, i) }
    }.build()

    /** A racy initialisation only prepares a geometry twice. */
    private val prepared = arrayOfNulls<PreparedGeometry>(right.size)

    private fun prepared(i: Int): PreparedGeometry = prepared[i] ?: geometries[i]!!.prepare().also { prepared[i] = it }

    /** The indices of the right items in relation with [left], in increasing order. */
    fun matches(left: Geometry?, predicate: SpatialPredicate): IntArray {
        if (left == null || left.isEmpty) return IntArray(0)
        val candidates = ArrayList<Int>()
        tree.query(left.envelopeInternal) { candidates += it }
        candidates.sort()
        if (predicate == SpatialPredicate.EnvelopesIntersect) return candidates.toIntArray()
        // The right geometries are tested again and again: prepared once, with the converse predicate
        val converse = when (predicate) {
            SpatialPredicate.Intersects, SpatialPredicate.Touches, SpatialPredicate.Overlaps -> predicate
            SpatialPredicate.Within -> SpatialPredicate.Contains
            SpatialPredicate.CoveredBy -> SpatialPredicate.Covers
            else -> null
        }
        if (converse != null) return candidates.filter { i -> converse.test(prepared(i), left) }.toIntArray()
        // Else the left one, when it has enough candidates to pay off
        val preparedLeft = if (candidates.size >= 4) left.prepare() else null
        return candidates.filter { i ->
            val g = geometries[i]!!
            if (preparedLeft != null) predicate.test(preparedLeft, g) else predicate.test(left, g)
        }.toIntArray()
    }
}

/**
 * The pairs of a left and a right item whose geometries are in [predicate] relation (left `predicate` right), in the
 * order of [left], then of [right]: e.g. the points of a data set with the districts that cover them. The right items
 * are indexed in a [PackedRTree] and prepared on first use (tested with the converse predicate: `a within b` as
 * `b contains a`); for Contains, ContainsProperly, Covers and Crosses each left geometry is prepared instead when it
 * has several candidates.
 */
fun <A, B> spatialJoin(
    left: List<A>,
    right: List<B>,
    predicate: SpatialPredicate = SpatialPredicate.Intersects,
    leftGeometry: (A) -> Geometry?,
    rightGeometry: (B) -> Geometry?,
): List<Pair<A, B>> {
    val index = JoinIndex(right, rightGeometry)
    val result = ArrayList<Pair<A, B>>()
    for (a in left) for (i in index.matches(leftGeometry(a), predicate)) result += a to right[i]
    return result
}

/** [spatialJoin] of geometries. */
fun <A : Geometry, B : Geometry> spatialJoin(
    left: List<A>,
    right: List<B>,
    predicate: SpatialPredicate = SpatialPredicate.Intersects,
): List<Pair<A, B>> = spatialJoin(left, right, predicate, { it }, { it })

/** [spatialJoin] with the left items split in chunks tested concurrently on [dispatcher]; the same order. */
suspend fun <A, B> spatialJoinConcurrently(
    left: List<A>,
    right: List<B>,
    predicate: SpatialPredicate = SpatialPredicate.Intersects,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    chunkSize: Int = 1024,
    leftGeometry: (A) -> Geometry?,
    rightGeometry: (B) -> Geometry?,
): List<Pair<A, B>> {
    val index = JoinIndex(right, rightGeometry)
    return coroutineScope {
        left.chunked(chunkSize).map { chunk ->
            async(dispatcher) {
                val pairs = ArrayList<Pair<A, B>>()
                for (a in chunk) for (i in index.matches(leftGeometry(a), predicate)) pairs += a to right[i]
                pairs
            }
        }.awaitAll().flatten()
    }
}

/**
 * The pairs of [spatialJoin] as a flow, emitted chunk by chunk as soon as each chunk of left items has been tested
 * concurrently on [dispatcher]: the pairs of a left item stay together and in order, the chunks come in the order they
 * finish. Cancelling the collection stops the work.
 */
fun <A, B> spatialJoinFlow(
    left: List<A>,
    right: List<B>,
    predicate: SpatialPredicate = SpatialPredicate.Intersects,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    chunkSize: Int = 1024,
    leftGeometry: (A) -> Geometry?,
    rightGeometry: (B) -> Geometry?,
): Flow<Pair<A, B>> = channelFlow {
    val index = JoinIndex(right, rightGeometry)
    for (chunk in left.chunked(chunkSize)) {
        launch(dispatcher) {
            val pairs = ArrayList<Pair<A, B>>()
            for (a in chunk) for (i in index.matches(leftGeometry(a), predicate)) pairs += a to right[i]
            for (pair in pairs) send(pair)
        }
    }
}.buffer(Channel.UNLIMITED)

// --- Union --------------------------------------------------------------------------------------------------------

/**
 * The union of all these geometries ([unionAll]), computed concurrently on [dispatcher]: the geometries are grouped
 * along the Hilbert curve of their centres, so that each group is compact and the unions of the groups simplify
 * (shared boundaries dissolve), each group is unioned in its own coroutine, then the results pairwise, also
 * concurrently. Null when there are none.
 */
suspend fun Collection<Geometry>.unionAllConcurrently(
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    groupSize: Int = 256,
): Geometry? {
    if (size <= groupSize) return unionAll()
    val nonEmpty = filter { !it.isEmpty }
    if (nonEmpty.isEmpty()) return unionAll()
    val tree = nonEmpty.toPackedRTree()
    @Suppress("UNCHECKED_CAST")
    val ordered = List(tree.size) { tree.items[tree.indices[it]] as Geometry }
    return coroutineScope {
        var parts: List<Geometry> = ordered.chunked(groupSize).map { group ->
            async(dispatcher) { UnaryUnionOp.union(group) }
        }.awaitAll().filterNotNull()
        while (parts.size > 1) {
            // Neighbours along the curve are unioned together
            parts = parts.chunked(2).map { pair ->
                async(dispatcher) { if (pair.size == 1) pair[0] else pair[0].union(pair[1]) }
            }.awaitAll()
        }
        parts.firstOrNull()
    }
}
