/*
 * Copyright (c) 2022 Martin Davis.
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.simplify

import org.locationtech.jts.algorithm.Orientation
import org.locationtech.jts.algorithm.Orientation.index
import org.locationtech.jts.algorithm.Orientation.isCCW
import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.CoordinateArrays.reverse
import org.locationtech.jts.index.VertexSequencePackedRtree
import org.locationtech.jts.legacy.queue.PriorityQueue

/**
 * Computes the outer or inner hull of a ring.
 *
 * @author Martin Davis
 */
internal class RingHull(private val inputRing: LinearRing, isOuter: Boolean) {
    private var targetVertexNum = -1
    private var targetAreaDelta = -1.0

    /**
     * The ring vertices are oriented so that
     * for corners which are to be kept
     * the vertices forming the corner are in CW orientation.
     */
    private var vertexRing: LinkedRing? = null
    private var areaDelta = 0.0

    /**
     * Indexing vertices improves corner intersection testing performance.
     * The ring vertices are contiguous, so are suitable for a
     * [VertexSequencePackedRtree].
     */
    var vertexIndex: VertexSequencePackedRtree? = null
        private set
    private var cornerQueue: PriorityQueue<Corner>? = null

    /**
     * Creates a new instance.
     *
     * @param ring the ring vertices to process
     * @param isOuter whether the hull is outer or inner
     */
    init {
        init(inputRing.coordinates, isOuter)
    }

    fun setMinVertexNum(minVertexNum: Int) {
        targetVertexNum = minVertexNum
    }

    fun setMaxAreaDelta(maxAreaDelta: Double) {
        targetAreaDelta = maxAreaDelta
    }

    fun getEnvelope(): Envelope {
        return inputRing.envelopeInternal
    }

    fun getHull(hullIndex: RingHullIndex?): LinearRing {
        compute(hullIndex)
        val hullPts: Array<Coordinate> = vertexRing!!.coordinates
        return inputRing.factory.createLinearRing(hullPts)
    }

    private fun init(ring: Array<Coordinate>, isOuter: Boolean) {
        /**
         * Ensure ring is oriented according to outer/inner:
         * - outer, CW
         * - inner: CCW
         */
        var ring = ring
        if (isOuter == isCCW(ring)) {
            ring = ring.copyOf()
            reverse(ring)
        }
        vertexRing = LinkedRing(ring)
        vertexIndex = VertexSequencePackedRtree(ring)
        //-- remove duplicate final vertex
        vertexIndex!!.remove(ring.size - 1)
        cornerQueue = PriorityQueue()
        for (i in 0 until vertexRing!!.size()) {
            addCorner(i, cornerQueue)
        }
    }

    private fun addCorner(i: Int, cornerQueue: PriorityQueue<Corner>?) {
        //-- convex corners are left untouched
        if (isConvex(vertexRing, i)) return
        //-- corner is concave or flat - both can be removed
        val corner = Corner(
            i,
            vertexRing!!.prev(i),
            vertexRing!!.next(i),
            area(vertexRing, i)
        )
        cornerQueue!!.add(corner)
    }

    fun compute(hullIndex: RingHullIndex?) {
        while (!cornerQueue!!.isEmpty()
            && vertexRing!!.size() > 3
        ) {
            val corner: Corner = cornerQueue!!.poll()!!
            //-- a corner may no longer be valid due to removal of adjacent corners
            if (corner.isRemoved(vertexRing)) continue
            if (isAtTarget(corner)) return
            //System.out.println(corner.toLineString(vertexList));
            /**
             * Corner is concave or flat - remove it if possible.
             */
            if (isRemovable(corner, hullIndex)) {
                removeCorner(corner, cornerQueue)
            }
        }
    }

    private fun isAtTarget(corner: Corner): Boolean {
        if (targetVertexNum >= 0) {
            return vertexRing!!.size() < targetVertexNum
        }
        return if (targetAreaDelta >= 0) {
            //-- include candidate corder to avoid overshooting target
            // (important for very small target area deltas)
            areaDelta + corner.area > targetAreaDelta
        } else true
        //-- no target set
    }

    /**
     * Removes a corner by removing the apex vertex from the ring.
     * Two new corners are created with apexes
     * at the other vertices of the corner
     * (if they are non-convex and thus removable).
     *
     * @param corner the corner to remove
     * @param cornerQueue the corner queue
     */
    private fun removeCorner(corner: Corner, cornerQueue: PriorityQueue<Corner>?) {
        val index: Int = corner.index
        val prev: Int = vertexRing!!.prev(index)
        val next: Int = vertexRing!!.next(index)
        vertexRing!!.remove(index)
        vertexIndex!!.remove(index)
        areaDelta += corner.area

        //-- potentially add the new corners created
        addCorner(prev, cornerQueue)
        addCorner(next, cornerQueue)
    }

    private fun isRemovable(corner: Corner, hullIndex: RingHullIndex?): Boolean {
        val cornerEnv = corner.envelope(vertexRing)
        if (hasIntersectingVertex(corner, cornerEnv, this)) return false
        //-- no other rings to check
        if (hullIndex == null) return true
        //-- check other rings for intersections
        for (hull in hullIndex.query(cornerEnv)) {
            //-- this hull was already checked above
            if (hull === this) continue
            if (hasIntersectingVertex(corner, cornerEnv, hull)) return false
        }
        return true
    }

    /**
     * Tests if any vertices in a hull intersect the corner triangle.
     * Uses the vertex spatial index for efficiency.
     *
     * @param corner the corner vertices
     * @param cornerEnv the envelope of the corner
     * @param hull the hull to test
     * @return true if there is an intersecting vertex
     */
    private fun hasIntersectingVertex(
        corner: Corner, cornerEnv: Envelope,
        hull: RingHull
    ): Boolean {
        val result = hull.query(cornerEnv)
        for (i in result.indices) {
            val index = result[i]
            //-- skip vertices of corner
            if (hull === this && corner.isVertex(index)) continue
            val v = hull.getCoordinate(index)
            //--- does corner triangle contain vertex?
            if (corner.intersects(v, vertexRing)) return true
        }
        return false
    }

    private fun getCoordinate(index: Int): Coordinate? {
        return vertexRing!!.getCoordinate(index)
    }

    private fun query(cornerEnv: Envelope): IntArray {
        return vertexIndex!!.query(cornerEnv)
    }

    fun queryHull(queryEnv: Envelope?, pts: MutableList<Coordinate?>) {
        val result = vertexIndex!!.query(queryEnv!!)
        for (i in result.indices) {
            val index = result[i]
            //-- skip if already removed
            if (!vertexRing!!.hasCoordinate(index)) continue
            val v: Coordinate? = vertexRing!!.getCoordinate(index)
            pts.add(v)
        }
    }

    fun toGeometry(): Polygon {
        val fact = GeometryFactory()
        val coords: Array<Coordinate> = vertexRing!!.coordinates
        return fact.createPolygon(fact.createLinearRing(coords))
    }

    private class Corner(val index: Int, private val prev: Int, private val next: Int, val area: Double) :
        Comparable<Corner?> {

        fun isVertex(index: Int): Boolean {
            return index == this.index || index == prev || index == next
        }

        /**
         * Orders corners by increasing area
         */
        override operator fun compareTo(o: Corner?): Int {
            return area.compareTo(o!!.area)
        }

        fun envelope(ring: LinkedRing?): Envelope {
            val pp: Coordinate = ring!!.getCoordinate(prev)
            val p: Coordinate = ring.getCoordinate(index)
            val pn: Coordinate = ring.getCoordinate(next)
            val env = Envelope(pp, pn)
            env.expandToInclude(p)
            return env
        }

        fun intersects(v: Coordinate?, ring: LinkedRing?): Boolean {
            val pp: Coordinate = ring!!.getCoordinate(prev)
            val p: Coordinate = ring.getCoordinate(index)
            val pn: Coordinate = ring.getCoordinate(next)
            return Triangle.intersects(pp, p, pn, v)
        }

        fun isRemoved(ring: LinkedRing?): Boolean {
            return ring!!.prev(index) != prev || ring.next(index) != next
        }

        fun toLineString(ring: LinkedRing): LineString {
            val pp: Coordinate = ring.getCoordinate(prev)
            val p: Coordinate = ring.getCoordinate(index)
            val pn: Coordinate = ring.getCoordinate(next)
            return GeometryFactory().createLineString(arrayOf(safeCoord(pp), safeCoord(p), safeCoord(pn)))
        }

        companion object {
            private fun safeCoord(p: Coordinate?): Coordinate {
                return p ?: Coordinate(Double.NaN, Double.NaN)
            }
        }
    }

    companion object {
        fun isConvex(vertexRing: LinkedRing?, index: Int): Boolean {
            val pp: Coordinate = vertexRing!!.prevCoordinate(index)
            val p: Coordinate = vertexRing.getCoordinate(index)
            val pn: Coordinate = vertexRing.nextCoordinate(index)
            return Orientation.CLOCKWISE == index(pp, p, pn)
        }

        fun area(vertexRing: LinkedRing?, index: Int): Double {
            val pp: Coordinate = vertexRing!!.prevCoordinate(index)
            val p: Coordinate = vertexRing.getCoordinate(index)
            val pn: Coordinate = vertexRing.nextCoordinate(index)
            return Triangle.area(pp, p, pn)
        }
    }
}