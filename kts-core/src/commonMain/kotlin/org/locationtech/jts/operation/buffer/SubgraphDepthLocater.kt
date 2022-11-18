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
package org.locationtech.jts.operation.buffer

import org.locationtech.jts.algorithm.Orientation
import org.locationtech.jts.algorithm.Orientation.index
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.LineSegment
import org.locationtech.jts.geom.Position
import org.locationtech.jts.geomgraph.DirectedEdge
import org.locationtech.jts.legacy.Math.max

/**
 * Locates a subgraph inside a set of subgraphs,
 * in order to determine the outside depth of the subgraph.
 * The input subgraphs are assumed to have had depths
 * already calculated for their edges.
 *
 * @version 1.7
 */
internal class SubgraphDepthLocater(subgraphs: MutableList<BufferSubgraph>) {
    private val subgraphs: Collection<*>
    private val seg = LineSegment()

    init {
        this.subgraphs = subgraphs
    }

    fun getDepth(p: Coordinate?): Int {
        val stabbedSegments: MutableList<DepthSegment> = findStabbedSegments(p)
        // if no segments on stabbing line subgraph must be outside all others.
        if (stabbedSegments.size == 0) return 0
        val ds = stabbedSegments.min()
        return ds.leftDepth
    }

    /**
     * Finds all non-horizontal segments intersecting the stabbing line.
     * The stabbing line is the ray to the right of stabbingRayLeftPt.
     *
     * @param stabbingRayLeftPt the left-hand origin of the stabbing line
     * @return a List of [DepthSegments] intersecting the stabbing line
     */
    private fun findStabbedSegments(stabbingRayLeftPt: Coordinate?): MutableList<DepthSegment> {
        val stabbedSegments: MutableList<DepthSegment> = ArrayList()
        val i = subgraphs.iterator()
        while (i.hasNext()) {
            val bsg: BufferSubgraph =
                i.next() as BufferSubgraph

            // optimization - don't bother checking subgraphs which the ray does not intersect
            val env: Envelope = bsg.envelope!!
            if (stabbingRayLeftPt!!.y < env.minY
                || stabbingRayLeftPt.y > env.maxY
            ) {
                continue
            }
            findStabbedSegments(stabbingRayLeftPt, bsg.directedEdges, stabbedSegments)
        }
        return stabbedSegments
    }

    /**
     * Finds all non-horizontal segments intersecting the stabbing line
     * in the list of dirEdges.
     * The stabbing line is the ray to the right of stabbingRayLeftPt.
     *
     * @param stabbingRayLeftPt the left-hand origin of the stabbing line
     * @param stabbedSegments the current list of [DepthSegments] intersecting the stabbing line
     */
    private fun findStabbedSegments(
        stabbingRayLeftPt: Coordinate,
        dirEdges: MutableList<DirectedEdge>,
        stabbedSegments: MutableList<DepthSegment>
    ) {
        /**
         * Check all forward DirectedEdges only.  This is still general,
         * because each Edge has a forward DirectedEdge.
         */
        val i: Iterator<*> = dirEdges.iterator()
        while (i.hasNext()) {
            val de = i.next() as DirectedEdge
            if (!de.isForward) {
                continue
            }
            findStabbedSegments(stabbingRayLeftPt, de, stabbedSegments)
        }
    }

    /**
     * Finds all non-horizontal segments intersecting the stabbing line
     * in the input dirEdge.
     * The stabbing line is the ray to the right of stabbingRayLeftPt.
     *
     * @param stabbingRayLeftPt the left-hand origin of the stabbing line
     * @param stabbedSegments the current list of [DepthSegments] intersecting the stabbing line
     */
    private fun findStabbedSegments(
        stabbingRayLeftPt: Coordinate,
        dirEdge: DirectedEdge,
        stabbedSegments: MutableList<DepthSegment>
    ) {
        val pts = dirEdge.edge.getCoordinates()
        for (i in 0 until pts.size - 1) {
            seg.p0 = pts[i]!!
            seg.p1 = pts[i + 1]!!
            // ensure segment always points upwards
            if (seg.p0.y > seg.p1.y) seg.reverse()

            // skip segment if it is left of the stabbing line
            val maxx: Double = max(seg.p0.x, seg.p1.x)
            if (maxx < stabbingRayLeftPt.x) continue

            // skip horizontal segments (there will be a non-horizontal one carrying the same depth info
            if (seg.isHorizontal) continue

            // skip if segment is above or below stabbing line
            if (stabbingRayLeftPt.y < seg.p0.y || stabbingRayLeftPt.y > seg.p1.y) continue

            // skip if stabbing ray is right of the segment
            if (index(seg.p0, seg.p1, stabbingRayLeftPt)
                == Orientation.RIGHT
            ) continue

            // stabbing line cuts this segment, so record it
            var depth = dirEdge.getDepth(Position.LEFT)
            // if segment direction was flipped, use RHS depth instead
            if (seg.p0 != pts[i]) depth = dirEdge.getDepth(Position.RIGHT)
            val ds = DepthSegment(seg, depth)
            stabbedSegments.add(ds)
        }
    }

    /**
     * A segment from a directed edge which has been assigned a depth value
     * for its sides.
     */
    internal class DepthSegment(seg: LineSegment?, depth: Int) : Comparable<Any?> {
        private val upwardSeg: LineSegment
        val leftDepth: Int

        init {
            // input seg is assumed to be normalized
            upwardSeg = LineSegment(seg!!)
            //upwardSeg.normalize();
            leftDepth = depth
        }

        /**
         * Defines a comparison operation on DepthSegments
         * which orders them left to right.
         * Assumes the segments are normalized.
         *
         *
         * The definition of the ordering is:
         *
         *  * -1 : if DS1.seg is left of or below DS2.seg (DS1 < DS2)
         *  * 1 : if   DS1.seg is right of or above DS2.seg (DS1 > DS2)
         *  * 0 : if the segments are identical
         *
         *
         * KNOWN BUGS:
         *
         *  * The logic does not obey the [Comparator.compareTo] contract.
         * This is acceptable for the intended usage, but may cause problems if used with some
         * utilities in the Java standard library (e.g. [].
         *
         *
         * @param obj a DepthSegment
         * @return the comparison value
         */
        override operator fun compareTo(obj: Any?): Int {
            val other = obj as DepthSegment

            // fast check if segments are trivially ordered along X
            if (upwardSeg.minX() >= other.upwardSeg.maxX()) return 1
            if (upwardSeg.maxX() <= other.upwardSeg.minX()) return -1
            /**
             * try and compute a determinate orientation for the segments.
             * Test returns 1 if other is left of this (i.e. this > other)
             */
            var orientIndex = upwardSeg.orientationIndex(other.upwardSeg)
            if (orientIndex != 0) return orientIndex
            /**
             * If comparison between this and other is indeterminate,
             * try the opposite call order.
             * The sign of the result needs to be flipped.
             */
            orientIndex = -1 * other.upwardSeg.orientationIndex(upwardSeg)
            return if (orientIndex != 0) orientIndex else upwardSeg.compareTo(other.upwardSeg)

            // otherwise, use standard lexocographic segment ordering
        }

        /**
         * Compare two collinear segments for left-most ordering.
         * If segs are vertical, use vertical ordering for comparison.
         * If segs are equal, return 0.
         * Segments are assumed to be directed so that the second coordinate is >= to the first
         * (e.g. up and to the right).
         *
         * @param seg0 a segment to compare
         * @param seg1 a segment to compare
         * @return
         */
        private fun compareX(seg0: LineSegment, seg1: LineSegment): Int {
            val compare0 = seg0.p0.compareTo(seg1.p0)
            return if (compare0 != 0) compare0 else seg0.p1.compareTo(seg1.p1)
        }

        override fun toString(): String {
            return upwardSeg.toString()
        }
    }
}