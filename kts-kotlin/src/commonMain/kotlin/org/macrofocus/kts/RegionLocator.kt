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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.locationtech.jts.algorithm.locate.SimplePointInAreaLocator
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.CoordinateSequence
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.Location
import org.locationtech.jts.geom.Polygonal
import kotlin.math.sqrt

/**
 * Finds the regions at a point among many: for hovering a map, or assigning many data points to the regions they fall
 * in. A [PackedRTree] of the envelopes narrows the candidates, then a polygon covers the point if a [PointLocator]
 * (built the first time the polygon is a candidate) says so, and any geometry is hit within [tolerance] of its
 * vertices and edges (lines and points can only be hit with a tolerance).
 *
 * When several regions are hit, [locate] returns the first one in the order they were given, so that callers can
 * order them by priority (e.g. lines before areas). Safe to share between threads.
 *
 * @param geometry the geometry of a region, null for a region that can never be hit
 */
class RegionLocator<T>(regions: Iterable<T>, geometry: (T) -> Geometry?) {
    private val regions: List<T> = regions.toList()
    private val geometries: Array<Geometry?> = Array(this.regions.size) { geometry(this.regions[it]) }

    /** Built on first use; a racy initialisation only builds an immutable locator twice. */
    private val locators = arrayOfNulls<PointLocator>(this.regions.size)

    private val tree: PackedRTree<Int> = PackedRTree.Builder<Int>(this.regions.size).apply {
        geometries.forEachIndexed { i, g -> if (g != null && !g.isEmpty) add(g.envelopeInternal, i) }
    }.build()

    val size: Int get() = regions.size

    private fun locator(i: Int): PointLocator = locators[i] ?: PointLocator(geometries[i]!!).also { locators[i] = it }

    private fun hits(i: Int, x: Double, y: Double, tolerance: Double): Boolean {
        val g = geometries[i]!!
        if (g is Polygonal && locator(i).covers(x, y)) return true
        return tolerance > 0 && g.boundaryDistance(x, y, tolerance) <= tolerance
    }

    /** Calls [action] with the index (in the order given) of every region hit at ([x], [y]). */
    private inline fun forEachHit(x: Double, y: Double, tolerance: Double, action: (Int) -> Unit) {
        tree.query(x - tolerance, y - tolerance, x + tolerance, y + tolerance) { i ->
            if (hits(i, x, y, tolerance)) action(i)
        }
    }

    /** The first region (in the order given) hit at ([x], [y]) among those accepted by [filter], or null. */
    fun locate(x: Double, y: Double, tolerance: Double = 0.0, filter: (T) -> Boolean = { true }): T? {
        var best = Int.MAX_VALUE
        tree.query(x - tolerance, y - tolerance, x + tolerance, y + tolerance) { i ->
            if (i < best && filter(regions[i]) && hits(i, x, y, tolerance)) best = i
        }
        return if (best == Int.MAX_VALUE) null else regions[best]
    }

    fun locate(coordinate: Coordinate, tolerance: Double = 0.0): T? = locate(coordinate.x, coordinate.y, tolerance)

    /** All the regions hit at ([x], [y]), in the order given. */
    fun locateAll(x: Double, y: Double, tolerance: Double = 0.0): List<T> {
        val found = ArrayList<Int>()
        forEachHit(x, y, tolerance) { found += it }
        found.sort()
        return found.map { regions[it] }
    }

    /**
     * The region of each point (`xs[i]`, `ys[i]`), or null, the points split in chunks located concurrently on
     * [dispatcher] (in parallel on the JVM and native).
     */
    suspend fun locateAll(
        xs: DoubleArray,
        ys: DoubleArray,
        tolerance: Double = 0.0,
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
        chunkSize: Int = 16_384,
    ): List<T?> {
        require(xs.size == ys.size) { "${xs.size} x for ${ys.size} y" }
        val chunks = coroutineScope {
            (xs.indices step chunkSize).map { start ->
                async(dispatcher) {
                    val end = minOf(start + chunkSize, xs.size)
                    List(end - start) { k -> locate(xs[start + k], ys[start + k], tolerance) }
                }
            }.awaitAll()
        }
        return chunks.flatten()
    }
}

/** A [RegionLocator] of these geometries. */
fun <G : Geometry> Iterable<G>.toRegionLocator(): RegionLocator<G> = RegionLocator(this) { it }

/** Each element paired with the region at its position (null when none), as they come. */
fun <P, T> Flow<P>.locateIn(
    locator: RegionLocator<T>,
    tolerance: Double = 0.0,
    x: (P) -> Double,
    y: (P) -> Double,
): Flow<Pair<P, T?>> = map { it to locator.locate(x(it), y(it), tolerance) }

/**
 * The distance from ([x], [y]) to this geometry: 0 inside a polygon, else to the nearest vertex or edge. Allocates
 * nothing per vertex. With [stopBelow], returns as soon as a distance at most that is found.
 */
fun Geometry.distanceTo(x: Double, y: Double, stopBelow: Double = 0.0): Double {
    if (this is Polygonal && !isEmpty && envelopeInternal.covers(x, y) &&
        SimplePointInAreaLocator.locate(Coordinate(x, y), this) != Location.EXTERIOR
    ) return 0.0
    return boundaryDistance(x, y, stopBelow)
}

/** The distance from ([x], [y]) to the nearest vertex or edge of this geometry. */
internal fun Geometry.boundaryDistance(x: Double, y: Double, stopBelow: Double = 0.0): Double {
    var best = Double.POSITIVE_INFINITY
    val limit = stopBelow * stopBelow
    for (sequence in coordinateSequences()) {
        best = minOf(best, squaredDistance(sequence, x, y))
        if (best <= limit) break
    }
    return sqrt(best)
}

private fun squaredDistance(sequence: CoordinateSequence, x: Double, y: Double): Double {
    val n = sequence.size()
    if (n == 0) return Double.POSITIVE_INFINITY
    var x0 = sequence.getX(0)
    var y0 = sequence.getY(0)
    var best = (x - x0) * (x - x0) + (y - y0) * (y - y0)
    for (i in 1 until n) {
        val x1 = sequence.getX(i)
        val y1 = sequence.getY(i)
        val dx = x1 - x0
        val dy = y1 - y0
        val length = dx * dx + dy * dy
        val t = if (length == 0.0) 0.0 else (((x - x0) * dx + (y - y0) * dy) / length).coerceIn(0.0, 1.0)
        val px = x0 + t * dx - x
        val py = y0 + t * dy - y
        best = minOf(best, px * px + py * py)
        x0 = x1
        y0 = y1
    }
    return best
}
