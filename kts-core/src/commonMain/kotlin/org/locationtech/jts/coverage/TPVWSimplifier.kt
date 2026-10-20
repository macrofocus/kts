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

import org.locationtech.jts.algorithm.Area
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.CoordinateArrays
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.index.VertexSequencePackedRtree
import org.locationtech.jts.index.strtree.STRtree
import org.locationtech.jts.legacy.queue.PriorityQueue
import org.locationtech.jts.simplify.LinkedLine
import kotlin.jvm.JvmStatic

/**
 * Computes a Topology-Preserving Visvalingam-Whyatt simplification
 * of a set of input lines.
 * The simplified lines will contain no more intersections than are present
 * in the original input.
 * Line and ring endpoints are preserved, except for rings
 * which are flagged as "free".
 * Rings which are smaller than the tolerance area
 * may be removed entirely, as long as they are flagged as removable.
 *
 * The amount of simplification is determined by a tolerance value,
 * which is a non-zero quantity.
 * It is the square root of the area tolerance used
 * in the Visvalingam-Whyatt algorithm.
 * This equates roughly to the maximum
 * distance by which a simplified line can change from the original.
 *
 * @author mdavis
 */
class TPVWSimplifier(private val edges: Array<Edge>) {

    private var cornerArea: CornerArea? = null
    private var removableSizeFactor = 1.0

    fun setRemovableRingSizeFactor(removableSizeFactor: Double) {
        this.removableSizeFactor = removableSizeFactor
    }

    fun setCornerArea(cornerArea: CornerArea) {
        this.cornerArea = cornerArea
    }

    private fun simplify() {
        val edgeIndex = EdgeIndex()
        add(edges, edgeIndex)

        for (i in edges.indices) {
            val edge = edges[i]
            edge.simplify(cornerArea!!, edgeIndex)
        }
    }

    private fun add(edges: Array<Edge>, edgeIndex: EdgeIndex) {
        for (edge in edges) {
            //-- don't include removed edges in index
            edge.updateRemoved(removableSizeFactor)
            if (!edge.isRemoved) {
                //-- avoid fluffing up removed edges
                edge.init()
                edgeIndex.add(edge)
            }
        }
    }

    /**
     * Creates a new edge.
     * The endpoints of the edge are preserved during simplification,
     * unless it is a ring and the [isFreeRing] flag is set.
     *
     * @param pts the line or ring
     * @param distanceTolerance
     * @param isFreeRing whether a ring endpoint can be removed
     * @param isRemovable
     */
    class Edge(
        private val pts: Array<Coordinate>,
        private val distanceTolerance: Double,
        private val isFreeRing: Boolean,
        private val isRemovable: Boolean
    ) {
        private lateinit var linkedLine: LinkedLine
        private val nPts: Int = pts.size
        private var vertexIndex: VertexSequencePackedRtree? = null
        val envelope: Envelope = CoordinateArrays.envelope(pts)
        var isRemoved = false
            private set

        fun updateRemoved(removableSizeFactor: Double) {
            if (!isRemovable) return
            val areaTolerance = distanceTolerance * distanceTolerance
            isRemoved = (CoordinateArrays.isRing(pts)
                    && Area.ofRing(pts) < removableSizeFactor * areaTolerance)
        }

        fun init() {
            linkedLine = LinkedLine(pts)
        }

        val tolerance: Double
            get() = distanceTolerance

        private fun getCoordinate(index: Int): Coordinate {
            return pts[index]
        }

        val coordinates: Array<Coordinate>
            get() {
                if (isRemoved) {
                    return arrayOf()
                }
                return linkedLine.coordinates
            }

        fun size(): Int {
            return linkedLine.size()
        }

        internal fun simplify(cornerArea: CornerArea, edgeIndex: EdgeIndex) {
            if (isRemoved) {
                return
            }
            //-- don't simplify
            if (distanceTolerance <= 0.0) return

            val areaTolerance = distanceTolerance * distanceTolerance
            val minEdgeSize = if (linkedLine.isRing) MIN_RING_SIZE else MIN_EDGE_SIZE

            val cornerQueue = createQueue(areaTolerance, cornerArea)
            while (!cornerQueue.isEmpty()
                && size() > minEdgeSize
            ) {
                val corner = cornerQueue.poll()!!
                //-- a corner may no longer be valid due to removal of adjacent corners
                if (corner.isRemoved) continue
                //System.out.println(corner.toLineString(edge));
                //-- done when all small corners are removed
                if (corner.area > areaTolerance) break
                if (isRemovable(corner, edgeIndex)) {
                    removeCorner(corner, areaTolerance, cornerArea, cornerQueue)
                }
            }
        }

        private fun createQueue(areaTolerance: Double, cornerArea: CornerArea): PriorityQueue<Corner> {
            val cornerQueue = PriorityQueue<Corner>()
            val minIndex = if (linkedLine.isRing && isFreeRing) 0 else 1
            val maxIndex = nPts - 1
            for (i in minIndex until maxIndex) {
                addCorner(i, areaTolerance, cornerArea, cornerQueue)
            }
            return cornerQueue
        }

        private fun addCorner(i: Int, areaTolerance: Double, cornerArea: CornerArea, cornerQueue: PriorityQueue<Corner>) {
            //-- add if this vertex can be a corner
            if (isFreeRing || (i != 0 && i != nPts - 1)) {
                val area = area(i, cornerArea)
                if (area <= areaTolerance) {
                    val corner = Corner(linkedLine, i, area)
                    cornerQueue.add(corner)
                }
            }
        }

        private fun area(index: Int, cornerArea: CornerArea): Double {
            val pp = linkedLine.prevCoordinate(index)
            val p = linkedLine.getCoordinate(index)
            val pn = linkedLine.nextCoordinate(index)
            return cornerArea.area(pp, p, pn)
        }

        private fun isRemovable(corner: Corner, edgeIndex: EdgeIndex): Boolean {
            val cornerEnv = corner.envelope()
            //-- check nearby lines for violating intersections
            //-- the query also returns this line for checking
            for (edge in edgeIndex.query(cornerEnv)) {
                if (hasIntersectingVertex(corner, cornerEnv, edge)) return false
                //-- check if corner base equals line (2-pts)
                //-- if so, don't remove corner, since that would collapse to the line
                if (edge !== this && edge.size() == 2) {
                    val linePts = edge.linkedLine.coordinates
                    if (corner.isBaseline(linePts[0], linePts[1])) return false
                }
            }
            return true
        }

        /**
         * Tests if any vertices in a line intersect the corner triangle.
         * Uses the vertex spatial index for efficiency.
         *
         * @param corner the corner vertices
         * @param cornerEnv the envelope of the corner
         * @param edge the hull to test
         * @return true if there is an intersecting vertex
         */
        private fun hasIntersectingVertex(
            corner: Corner, cornerEnv: Envelope,
            edge: Edge
        ): Boolean {
            val result = edge.query(cornerEnv)
            for (index in result) {
                val v = edge.getCoordinate(index)
                // ok if corner touches another line - should only happen at endpoints
                if (corner.isVertex(v)) continue

                //--- does corner triangle contain vertex?
                if (corner.intersects(v)) return true
            }
            return false
        }

        private fun initIndex() {
            val vertexIndex = VertexSequencePackedRtree(pts)
            //-- remove ring duplicate final vertex
            if (CoordinateArrays.isRing(pts)) {
                vertexIndex.remove(pts.size - 1)
            }
            this.vertexIndex = vertexIndex
        }

        private fun query(cornerEnv: Envelope): IntArray {
            if (vertexIndex == null) {
                initIndex()
            }
            return vertexIndex!!.query(cornerEnv)
        }

        /**
         * Removes a corner by removing the apex vertex from the ring.
         * Two new corners are created with apexes
         * at the other vertices of the corner
         * (if they are non-convex and thus removable).
         *
         * @param corner the corner to remove
         * @param cornerArea
         * @param areaTolerance
         * @param cornerQueue the corner queue
         */
        private fun removeCorner(
            corner: Corner,
            areaTolerance: Double,
            cornerArea: CornerArea,
            cornerQueue: PriorityQueue<Corner>
        ) {
            val index = corner.index
            val prev = linkedLine.prev(index)
            val next = linkedLine.next(index)
            linkedLine.remove(index)
            vertexIndex!!.remove(index)

            //-- potentially add the new corners created
            addCorner(prev, areaTolerance, cornerArea, cornerQueue)
            addCorner(next, areaTolerance, cornerArea, cornerQueue)
        }

        override fun toString(): String {
            return linkedLine.toString()
        }

        companion object {
            private const val MIN_EDGE_SIZE = 2
            private const val MIN_RING_SIZE = 4
        }
    }

    internal class EdgeIndex {

        var index = STRtree()

        fun add(edge: Edge) {
            index.insert(edge.envelope, edge)
        }

        fun query(queryEnv: Envelope): List<Edge> {
            @Suppress("UNCHECKED_CAST")
            return index.query(queryEnv) as List<Edge>
        }
    }

    companion object {
        /**
         * Simplifies a set of lines, preserving the topology of the lines between
         * themselves and a set of linear constraints.
         * The endpoints of lines are preserved.
         * The endpoint of rings are preserved as well, unless
         * the ring is indicated as "free" via a bit flag with the same index.
         *
         * @param edges the edges to simplify
         * @param cornerArea the corner area computer
         * @param removableSizeFactor the factor to determine ring size to remove
         */
        @JvmStatic
        fun simplify(
            edges: Array<Edge>,
            cornerArea: CornerArea,
            removableSizeFactor: Double
        ) {
            val simp = TPVWSimplifier(edges)
            simp.setCornerArea(cornerArea)
            simp.setRemovableRingSizeFactor(removableSizeFactor)
            simp.simplify()
        }
    }
}
