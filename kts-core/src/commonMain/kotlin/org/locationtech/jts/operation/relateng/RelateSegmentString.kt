/*
 * Copyright (c) 2024 Martin Davis.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.operation.relateng

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.CoordinateArrays
import org.locationtech.jts.geom.Dimension
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.noding.BasicSegmentString
import kotlin.jvm.JvmStatic

/**
 * Models a linear edge of a [RelateGeometry].
 *
 * @author mdavis
 */
class RelateSegmentString private constructor(
    pts: Array<Coordinate>,
    private val isA: Boolean,
    private val dimension: Int,
    private val id: Int,
    private val ringId: Int,
    poly: Geometry?,
    private val inputGeom: RelateGeometry
) : BasicSegmentString(pts, null) {

    private val parentPolygonal: Geometry? = poly

    fun isA(): Boolean {
        return isA
    }

    fun getGeometry(): RelateGeometry {
        return inputGeom
    }

    fun getPolygonal(): Geometry? {
        return parentPolygonal
    }

    fun createNodeSection(segIndex: Int, intPt: Coordinate): NodeSection {
        val isNodeAtVertex = (intPt.equals2D(getCoordinate(segIndex))
                || intPt.equals2D(getCoordinate(segIndex + 1)))
        val prev = prevVertex(segIndex, intPt)
        val next = nextVertex(segIndex, intPt)
        val a = NodeSection(isA, dimension, id, ringId, parentPolygonal, isNodeAtVertex, prev, intPt, next)
        return a
    }

    /**
     *
     * @param ss
     * @param segIndex
     * @param pt
     * @return the previous vertex, or null if none exists
     */
    private fun prevVertex(segIndex: Int, pt: Coordinate): Coordinate? {
        val segStart = getCoordinate(segIndex)
        if (!segStart.equals2D(pt))
            return segStart
        //-- pt is at segment start, so get previous vertex
        if (segIndex > 0)
            return getCoordinate(segIndex - 1)
        if (isClosed)
            return prevInRing(segIndex)
        return null
    }

    /**
     *
     * @param ss
     * @param segIndex
     * @param pt
     * @return the next vertex, or null if none exists
     */
    private fun nextVertex(segIndex: Int, pt: Coordinate): Coordinate? {
        val segEnd = getCoordinate(segIndex + 1)
        if (!segEnd.equals2D(pt))
            return segEnd
        //-- pt is at seg end, so get next vertex
        if (segIndex < size() - 2)
            return getCoordinate(segIndex + 2)
        if (isClosed)
            return nextInRing(segIndex + 1)
        //-- segstring is not closed, so there is no next segment
        return null
    }

    /**
     * Tests if a segment intersection point has that segment as its
     * canonical containing segment.
     * Segments are half-closed, and contain their start point but not the endpoint,
     * except for the final segment in a non-closed segment string, which contains
     * its endpoint as well.
     * This test ensures that vertices are assigned to a unique segment in a segment string.
     * In particular, this avoids double-counting intersections which lie exactly
     * at segment endpoints.
     *
     * @param segIndex the segment the point may lie on
     * @param pt the point
     * @return true if the segment contains the point
     */
    fun isContainingSegment(segIndex: Int, pt: Coordinate): Boolean {
        //-- intersection is at segment start vertex - process it
        if (pt.equals2D(getCoordinate(segIndex)))
            return true
        if (pt.equals2D(getCoordinate(segIndex + 1))) {
            val isFinalSegment = segIndex == size() - 2
            if (isClosed || !isFinalSegment)
                return false
            //-- for final segment, process intersections with final endpoint
            return true
        }
        //-- intersection is interior - process it
        return true
    }

    companion object {
        @JvmStatic
        fun createLine(pts: Array<Coordinate>, isA: Boolean, elementId: Int, parent: RelateGeometry): RelateSegmentString {
            return createSegmentString(pts, isA, Dimension.L, elementId, -1, null, parent)
        }

        @JvmStatic
        fun createRing(
            pts: Array<Coordinate>, isA: Boolean, elementId: Int, ringId: Int,
            poly: Geometry?, parent: RelateGeometry
        ): RelateSegmentString {
            return createSegmentString(pts, isA, Dimension.A, elementId, ringId, poly, parent)
        }

        private fun createSegmentString(
            pts: Array<Coordinate>, isA: Boolean, dim: Int, elementId: Int, ringId: Int,
            poly: Geometry?, parent: RelateGeometry
        ): RelateSegmentString {
            val ptsNoRepeat = removeRepeatedPoints(pts)
            return RelateSegmentString(ptsNoRepeat, isA, dim, elementId, ringId, poly, parent)
        }

        private fun removeRepeatedPoints(pts: Array<Coordinate>): Array<Coordinate> {
            var pts = pts
            if (CoordinateArrays.hasRepeatedPoints(pts)) {
                pts = CoordinateArrays.removeRepeatedPoints(pts)
            }
            return pts
        }
    }
}
