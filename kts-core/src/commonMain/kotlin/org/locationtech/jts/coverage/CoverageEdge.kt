/*
 * Copyright (c) 2022 Martin Davis.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.coverage

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.CoordinateArrays
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LineSegment
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.io.WKTWriter
import kotlin.jvm.JvmStatic

/**
 * An edge of a polygonal coverage formed from all or a section of a polygon ring.
 * An edge may be a free ring, which is a ring which has no node points
 * (i.e. does not share a vertex with any other rings in the parent coverage).
 *
 * @author mdavis
 */
class CoverageEdge(
    var coordinates: Array<Coordinate>,
    private var primary: Boolean,
    /**
     * Returns whether this edge is a free ring;
     * i.e. one that does not have nodes
     * which are anchored because they occur in another ring.
     *
     * @return true if this is a free ring
     */
    val isFreeRing: Boolean
) {
    var ringCount = 0
        private set
    private var adjacentIndex0 = -1
    private var adjacentIndex1 = -1

    fun incRingCount() {
        ringCount++
    }

    val isInner: Boolean
        get() = ringCount == RING_COUNT_INNER

    val isOuter: Boolean
        get() = ringCount == RING_COUNT_OUTER

    fun setPrimary(isPrimary: Boolean) {
        //-- preserve primary status if set
        if (this.primary) return
        this.primary = isPrimary
    }

    val isRemovableRing: Boolean
        get() {
            val isRing = CoordinateArrays.isRing(coordinates)
            return isRing && !primary
        }

    val endCoordinate: Coordinate
        get() = coordinates[coordinates.size - 1]

    val startCoordinate: Coordinate
        get() = coordinates[0]

    fun toLineString(geomFactory: GeometryFactory): LineString {
        return geomFactory.createLineString(coordinates)
    }

    override fun toString(): String {
        return WKTWriter.toLineString(coordinates)
    }

    fun addIndex(index: Int) {
        //TODO: keep information about which element is L and R?

        // assert: at least one elementIndex is unset (< 0)
        if (adjacentIndex0 < 0) {
            adjacentIndex0 = index
        } else {
            adjacentIndex1 = index
        }
    }

    fun getAdjacentIndex(index: Int): Int {
        if (index == 0) return adjacentIndex0
        return adjacentIndex1
    }

    fun hasAdjacentIndex(index: Int): Boolean {
        if (index == 0) return adjacentIndex0 >= 0
        return adjacentIndex1 >= 0
    }

    companion object {
        const val RING_COUNT_INNER = 2
        const val RING_COUNT_OUTER = 1

        @JvmStatic
        fun createEdge(ring: Array<Coordinate>, isPrimary: Boolean): CoverageEdge {
            val pts = extractEdgePoints(ring, 0, ring.size - 1)
            return CoverageEdge(pts, isPrimary, true)
        }

        @JvmStatic
        fun createEdge(ring: Array<Coordinate>, start: Int, end: Int, isPrimary: Boolean): CoverageEdge {
            val pts = extractEdgePoints(ring, start, end)
            return CoverageEdge(pts, isPrimary, false)
        }

        private fun extractEdgePoints(ring: Array<Coordinate>, start: Int, end: Int): Array<Coordinate> {
            val size = if (start < end) end - start + 1 else ring.size - start + end
            var iring = start
            return Array(size) {
                val p = ring[iring].copy()
                iring += 1
                if (iring >= ring.size) iring = 1
                p
            }
        }

        /**
         * Computes a key segment for a ring.
         * The key is the segment starting at the lowest vertex,
         * towards the lowest adjacent distinct vertex.
         *
         * @param ring a linear ring
         * @return a LineSegment representing the key
         */
        @JvmStatic
        fun key(ring: Array<Coordinate>): LineSegment {
            // find lowest vertex index
            var indexLow = 0
            for (i in 1 until ring.size - 1) {
                if (ring[indexLow].compareTo(ring[i]) < 0) indexLow = i
            }
            val key0 = ring[indexLow]
            // find distinct adjacent vertices
            val adj0 = findDistinctPoint(ring, indexLow, true, key0)
            val adj1 = findDistinctPoint(ring, indexLow, false, key0)
            val key1 = if (adj0.compareTo(adj1) < 0) adj0 else adj1
            return LineSegment(key0, key1)
        }

        /**
         * Computes a distinct key for a section of a linear ring.
         *
         * @param ring the linear ring
         * @param start index of the start of the section
         * @param end end index of the end of the section
         * @return a LineSegment representing the key
         */
        @JvmStatic
        fun key(ring: Array<Coordinate>, start: Int, end: Int): LineSegment {
            //-- endpoints are distinct in a line edge
            val end0 = ring[start]
            val end1 = ring[end]
            val isForward = 0 > end0.compareTo(end1)
            val key0: Coordinate
            val key1: Coordinate
            if (isForward) {
                key0 = end0
                key1 = findDistinctPoint(ring, start, true, key0)
            } else {
                key0 = end1
                key1 = findDistinctPoint(ring, end, false, key0)
            }
            return LineSegment(key0, key1)
        }

        private fun findDistinctPoint(pts: Array<Coordinate>, index: Int, isForward: Boolean, pt: Coordinate): Coordinate {
            val inc = if (isForward) 1 else -1
            var i = index
            do {
                if (!pts[i].equals2D(pt)) {
                    return pts[i]
                }
                // increment index with wrapping
                i += inc
                if (i < 0) {
                    i = pts.size - 1
                } else if (i > pts.size - 1) {
                    i = 0
                }
            } while (i != index)
            throw IllegalStateException("Edge does not contain distinct points")
        }
    }
}
