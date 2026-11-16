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

import org.locationtech.jts.algorithm.CGAlgorithmsDD
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.Location
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.Polygonal
import kotlin.math.floor

/**
 * Locates points in a polygonal geometry, for many points: the segments of its rings are packed in arrays and binned
 * in horizontal bands, so that locating a point only tests the segments of its band, allocating nothing (no Point,
 * no Coordinate, no visitor). It follows the rules of JTS' `RayCrossingCounter`, with the same robust orientation,
 * so it agrees with `IndexedPointInAreaLocator` and `Geometry.covers`.
 *
 * Immutable once built, so it can be shared between threads.
 */
class PointLocator(geometry: Geometry) {
    private val minX: Double
    private val minY: Double
    private val maxX: Double
    private val maxY: Double

    /** x0, y0, x1, y1 of every segment of every ring. */
    private val segments: DoubleArray

    /** The segments of band b are bandSegments[bandStart[b] until bandStart[b + 1]]. */
    private val bandStart: IntArray
    private val bandSegments: IntArray
    private val bandHeight: Double

    init {
        require(geometry is Polygonal) { "A PointLocator locates points in polygons, not in a ${geometry.geometryType}" }
        val coords = ArrayList<DoubleArray>()
        var count = 0
        for (polygon in geometry.extract<Polygon>()) {
            for (ring in polygon.rings) {
                val seq = ring.coordinateSequence ?: continue
                val n = seq.size()
                if (n < 2) continue
                val packed = DoubleArray((n - 1) * 4)
                for (i in 0 until n - 1) {
                    packed[i * 4] = seq.getX(i)
                    packed[i * 4 + 1] = seq.getY(i)
                    packed[i * 4 + 2] = seq.getX(i + 1)
                    packed[i * 4 + 3] = seq.getY(i + 1)
                }
                coords += packed
                count += n - 1
            }
        }
        segments = DoubleArray(count * 4)
        var offset = 0
        for (packed in coords) {
            packed.copyInto(segments, offset)
            offset += packed.size
        }
        val env = geometry.envelopeInternal
        minX = env.minX; minY = env.minY; maxX = env.maxX; maxY = env.maxY

        val bands = (count / 4).coerceIn(1, 1 shl 16)
        bandHeight = if (env.isNull || env.height == 0.0) 1.0 else env.height / bands
        // Counting sort of the segments into the bands they overlap
        val counts = IntArray(bands + 1)
        for (s in 0 until count) {
            val (b0, b1) = bandsOf(s, bands)
            for (b in b0..b1) counts[b + 1]++
        }
        for (b in 0 until bands) counts[b + 1] += counts[b]
        bandStart = counts.copyOf()
        bandSegments = IntArray(counts[bands])
        for (s in 0 until count) {
            val (b0, b1) = bandsOf(s, bands)
            for (b in b0..b1) bandSegments[counts[b]++] = s
        }
    }

    private fun bandsOf(segment: Int, bands: Int): Pair<Int, Int> {
        val y0 = segments[segment * 4 + 1]
        val y1 = segments[segment * 4 + 3]
        return band(minOf(y0, y1), bands) to band(maxOf(y0, y1), bands)
    }

    private fun band(y: Double, bands: Int = bandStart.size - 1): Int =
        floor((y - minY) / bandHeight).toInt().coerceIn(0, bands - 1)

    /** [Location.INTERIOR], [Location.BOUNDARY] or [Location.EXTERIOR]. */
    fun locate(x: Double, y: Double): Int {
        if (!(x >= minX && x <= maxX && y >= minY && y <= maxY)) return Location.EXTERIOR
        val b = band(y)
        var crossings = 0
        for (k in bandStart[b] until bandStart[b + 1]) {
            val i = bandSegments[k] * 4
            val x1 = segments[i]
            val y1 = segments[i + 1]
            val x2 = segments[i + 2]
            val y2 = segments[i + 3]
            // As RayCrossingCounter.countSegment, for a ray from (x, y) towards +x
            if (x1 < x && x2 < x) continue
            if (x == x2 && y == y2) return Location.BOUNDARY
            if (y1 == y && y2 == y) {
                if (x >= minOf(x1, x2) && x <= maxOf(x1, x2)) return Location.BOUNDARY
                continue
            }
            if ((y1 > y && y2 <= y) || (y2 > y && y1 <= y)) {
                var orientation = CGAlgorithmsDD.orientationIndex(x1, y1, x2, y2, x, y)
                if (orientation == 0) return Location.BOUNDARY
                if (y2 < y1) orientation = -orientation
                if (orientation > 0) crossings++
            }
        }
        return if (crossings % 2 == 1) Location.INTERIOR else Location.EXTERIOR
    }

    fun locate(coordinate: Coordinate): Int = locate(coordinate.x, coordinate.y)

    /** Whether ([x], [y]) is in the interior or on the boundary (as `Geometry.covers`). */
    fun covers(x: Double, y: Double): Boolean = locate(x, y) != Location.EXTERIOR

    /** Whether ([x], [y]) is in the interior (as `Geometry.contains`). */
    fun contains(x: Double, y: Double): Boolean = locate(x, y) == Location.INTERIOR

    operator fun contains(coordinate: Coordinate): Boolean = contains(coordinate.x, coordinate.y)
}

/** A [PointLocator] for this polygonal geometry, to locate many points. */
fun Geometry.pointLocator(): PointLocator = PointLocator(this)
