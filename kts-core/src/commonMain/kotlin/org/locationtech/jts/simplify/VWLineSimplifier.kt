/*
 * Copyright (c) 2016 Vivid Solutions.
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.simplify

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.CoordinateList
import org.locationtech.jts.geom.Triangle
import org.locationtech.jts.legacy.Math.abs

/**
 * Simplifies a linestring (sequence of points) using the
 * Visvalingam-Whyatt algorithm.
 * The Visvalingam-Whyatt algorithm simplifies geometry
 * by removing vertices while trying to minimize the area changed.
 *
 * @version 1.7
 */
internal class VWLineSimplifier(private val pts: Array<Coordinate>, distanceTolerance: Double) {
    private val tolerance: Double

    init {
        tolerance = distanceTolerance * distanceTolerance
    }

    fun simplify(): Array<Coordinate> {
        val vwLine = VWVertex.buildLine(
            pts
        )
        var minArea = tolerance
        do {
            minArea = simplifyVertex(vwLine)
        } while (minArea < tolerance)
        val simp = vwLine!!.coordinates
        // ensure computed value is a valid line
        return if (simp.size < 2) {
            arrayOf(
                simp[0],
                Coordinate(simp[0])
            )
        } else simp
    }

    private fun simplifyVertex(vwLine: VWVertex?): Double {
        /**
         * Scan vertices in line and remove the one with smallest effective area.
         */
        // TODO: use an appropriate data structure to optimize finding the smallest area vertex
        var curr = vwLine
        var minArea = curr!!.area
        var minVertex: VWVertex? = null
        while (curr != null) {
            val area = curr.area
            if (area < minArea) {
                minArea = area
                minVertex = curr
            }
            curr = curr.next
        }
        if (minVertex != null && minArea < tolerance) {
            minVertex.remove()
        }
        return if (!vwLine!!.isLive) (-1).toDouble() else minArea
    }

    internal class VWVertex(private val pt: Coordinate) {
        private var prev: VWVertex? = null
        var next: VWVertex? = null
        var area = MAX_AREA
            private set
        var isLive = true
            private set

        fun setPrev(prev: VWVertex?) {
            this.prev = prev
        }

        fun updateArea() {
            if (prev == null || next == null) {
                area = MAX_AREA
                return
            }
            area = abs(Triangle.area(prev!!.pt, pt, next!!.pt))
        }

        fun remove(): VWVertex? {
            val tmpPrev = prev
            val tmpNext = next
            var result: VWVertex? = null
            if (prev != null) {
                prev!!.next = tmpNext
                prev!!.updateArea()
                result = prev
            }
            if (next != null) {
                next!!.setPrev(tmpPrev)
                next!!.updateArea()
                if (result == null) result = next
            }
            isLive = false
            return result
        }

        val coordinates: Array<Coordinate>
            get() {
                val coords = CoordinateList()
                var curr: VWVertex? = this
                do {
                    coords.add(curr!!.pt, false)
                    curr = curr.next
                } while (curr != null)
                return coords.toCoordinateArray()
            }

        companion object {
            fun buildLine(pts: Array<Coordinate>): VWVertex? {
                var first: VWVertex? = null
                var prev: VWVertex? = null
                for (i in pts.indices) {
                    val v = VWVertex(pts[i])
                    if (first == null) first = v
                    v.setPrev(prev)
                    if (prev != null) {
                        prev.next = v
                        prev.updateArea()
                    }
                    prev = v
                }
                return first
            }

            var MAX_AREA = Double.MAX_VALUE
        }
    }

    companion object {
        fun simplify(pts: Array<Coordinate>, distanceTolerance: Double): Array<Coordinate> {
            val simp = VWLineSimplifier(pts, distanceTolerance)
            return simp.simplify()
        }
    }
}