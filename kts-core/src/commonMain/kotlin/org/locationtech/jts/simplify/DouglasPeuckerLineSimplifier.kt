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
import org.locationtech.jts.geom.LineSegment

/**
 * Simplifies a linestring (sequence of points) using
 * the standard Douglas-Peucker algorithm.
 *
 * @version 1.7
 */
internal class DouglasPeuckerLineSimplifier(private val pts: Array<Coordinate>) {
    private lateinit var usePt: BooleanArray
    private var distanceTolerance = 0.0

    /**
     * Sets the distance tolerance for the simplification.
     * All vertices in the simplified linestring will be within this
     * distance of the original linestring.
     *
     * @param distanceTolerance the approximation tolerance to use
     */
    fun setDistanceTolerance(distanceTolerance: Double) {
        this.distanceTolerance = distanceTolerance
    }

    fun simplify(): Array<Coordinate> {
        usePt = BooleanArray(pts.size)
        for (i in pts.indices) {
            usePt[i] = true
        }
        simplifySection(0, pts.size - 1)
        val coordList = CoordinateList()
        for (i in pts.indices) {
            if (usePt[i]) coordList.add(Coordinate(pts[i]))
        }
        return coordList.toCoordinateArray()
    }

    private val seg = LineSegment()
    private fun simplifySection(i: Int, j: Int) {
        if (i + 1 == j) {
            return
        }
        seg.p0 = pts[i]
        seg.p1 = pts[j]
        var maxDistance = -1.0
        var maxIndex = i
        for (k in i + 1 until j) {
            val distance = seg.distance(pts[k])
            if (distance > maxDistance) {
                maxDistance = distance
                maxIndex = k
            }
        }
        if (maxDistance <= distanceTolerance) {
            for (k in i + 1 until j) {
                usePt[k] = false
            }
        } else {
            simplifySection(i, maxIndex)
            simplifySection(maxIndex, j)
        }
    }

    companion object {
        fun simplify(pts: Array<Coordinate>, distanceTolerance: Double): Array<Coordinate> {
            val simp = DouglasPeuckerLineSimplifier(pts)
            simp.setDistanceTolerance(distanceTolerance)
            return simp.simplify()
        }
    }
}