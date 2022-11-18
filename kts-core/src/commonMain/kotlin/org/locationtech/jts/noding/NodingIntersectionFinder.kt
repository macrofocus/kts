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
package org.locationtech.jts.noding

import org.locationtech.jts.algorithm.LineIntersector
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.legacy.Math.abs

/**
 * Finds non-noded intersections in a set of [SegmentString]s,
 * if any exist.
 *
 * Non-noded intersections include:
 *
 *  * **Interior intersections** which lie in the interior of a segment
 * (with another segment interior or with a vertex or endpoint)
 *  * **Vertex intersections** which occur at vertices in the interior of [SegmentString]s
 * (with a segment string endpoint or with another interior vertex)
 *
 * The finder can be limited to finding only interior intersections
 * by setting [.setInteriorIntersectionsOnly].
 *
 * By default only the first intersection is found,
 * but all can be found by setting [.setFindAllIntersections]
 *
 * @version 1.7
 */
class NodingIntersectionFinder(private val li: LineIntersector) : SegmentIntersector {
    private var findAllIntersections = false
    private var isCheckEndSegmentsOnly = false
    private var keepIntersections = true
    private var isInteriorIntersectionsOnly = false

    /**
     * Gets the computed location of the intersection.
     * Due to round-off, the location may not be exact.
     *
     * @return the coordinate for the intersection location
     */
    var intersection: Coordinate? = null
        private set

    /**
     * Gets the endpoints of the intersecting segments.
     *
     * @return an array of the segment endpoints (p00, p01, p10, p11)
     */
    var intersectionSegments: Array<Coordinate?>? = null
        private set
    private val intersections: MutableList<Coordinate> = ArrayList()
    private var intersectionCount = 0

    /**
     * Creates an intersection finder which finds an intersection
     * if one exists
     *
     * @param li the LineIntersector to use
     */
    init {
        intersection = null
    }

    /**
     * Sets whether all intersections should be computed.
     * When this is `false` (the default value)
     * the value of [.isDone] is `true` after the first intersection is found.
     *
     * Default is `false`.
     *
     * @param findAllIntersections whether all intersections should be computed
     */
    fun setFindAllIntersections(findAllIntersections: Boolean) {
        this.findAllIntersections = findAllIntersections
    }

    /**
     * Sets whether only interior (proper) intersections will be found.
     * @param isInteriorIntersectionsOnly whether to find only interior intersections
     */
    fun setInteriorIntersectionsOnly(isInteriorIntersectionsOnly: Boolean) {
        this.isInteriorIntersectionsOnly = isInteriorIntersectionsOnly
    }

    /**
     * Sets whether only end segments should be tested for intersection.
     * This is a performance optimization that may be used if
     * the segments have been previously noded by an appropriate algorithm.
     * It may be known that any potential noding failures will occur only in
     * end segments.
     *
     * @param isCheckEndSegmentsOnly whether to test only end segments
     */
    fun setCheckEndSegmentsOnly(isCheckEndSegmentsOnly: Boolean) {
        this.isCheckEndSegmentsOnly = isCheckEndSegmentsOnly
    }

    /**
     * Sets whether intersection points are recorded.
     * If the only need is to count intersection points, this can be set to `false`.
     *
     * Default is `true`.
     *
     * @param keepIntersections indicates whether intersections should be recorded
     */
    fun setKeepIntersections(keepIntersections: Boolean) {
        this.keepIntersections = keepIntersections
    }

    /**
     * Gets the intersections found.
     *
     * @return a List of [Coordinate]
     */
    fun getIntersections(): MutableList<Coordinate> {
        return intersections
    }

    /**
     * Gets the count of intersections found.
     *
     * @return the intersection count
     */
    fun count(): Int {
        return intersectionCount
    }

    /**
     * Tests whether an intersection was found.
     *
     * @return true if an intersection was found
     */
    fun hasIntersection(): Boolean {
        return intersection != null
    }

    /**
     * This method is called by clients
     * of the [SegmentIntersector] class to process
     * intersections for two segments of the [SegmentString]s being intersected.
     * Note that some clients (such as `MonotoneChain`s) may optimize away
     * this call for segment pairs which they have determined do not intersect
     * (e.g. by an disjoint envelope test).
     */
    override fun processIntersections(
        e0: SegmentString, segIndex0: Int,
        e1: SegmentString, segIndex1: Int
    ) {
        // short-circuit if intersection already found
        if (!findAllIntersections && hasIntersection()) return

        // don't bother intersecting a segment with itself
        val isSameSegString = e0 === e1
        val isSameSegment = isSameSegString && segIndex0 == segIndex1
        if (isSameSegment) return
        /**
         * If enabled, only test end segments (on either segString).
         *
         */
        if (isCheckEndSegmentsOnly) {
            val isEndSegPresent = isEndSegment(e0, segIndex0) || isEndSegment(e1, segIndex1)
            if (!isEndSegPresent) return
        }
        val p00: Coordinate = e0.getCoordinate(segIndex0)
        val p01: Coordinate = e0.getCoordinate(segIndex0 + 1)
        val p10: Coordinate = e1.getCoordinate(segIndex1)
        val p11: Coordinate = e1.getCoordinate(segIndex1 + 1)
        val isEnd00 = segIndex0 == 0
        val isEnd01 = segIndex0 + 2 == e0.size()
        val isEnd10 = segIndex1 == 0
        val isEnd11 = segIndex1 + 2 == e1.size()
        li.computeIntersection(p00, p01, p10, p11)
        //if (li.hasIntersection() && li.isProper()) Debug.println(li);
        /**
         * Check for an intersection in the interior of a segment
         */
        val isInteriorInt = li.hasIntersection() && li.isInteriorIntersection

        /**
         * Check for an intersection between two vertices which are not both endpoints.
         */
        var isInteriorVertexInt = false
        if (!isInteriorIntersectionsOnly) {
            val isAdjacentSegment = isSameSegString && abs(segIndex1 - segIndex0) <= 1
            isInteriorVertexInt = !isAdjacentSegment && isInteriorVertexIntersection(
                p00, p01, p10, p11,
                isEnd00, isEnd01, isEnd10, isEnd11
            )
        }
        if (isInteriorInt || isInteriorVertexInt) {
            // found an intersection!
            intersectionSegments = arrayOfNulls(4)
            intersectionSegments!![0] = p00
            intersectionSegments!![1] = p01
            intersectionSegments!![2] = p10
            intersectionSegments!![3] = p11

            //TODO: record endpoint intersection(s)
            intersection = li.getIntersection(0)
            if (keepIntersections) intersections.add(intersection!!)
            intersectionCount++
        }
    }

    /**
     *
     */
    override val isDone: Boolean
        get() = if (findAllIntersections) false else intersection != null

    companion object {
        /**
         * Creates a finder which tests if there is at least one intersection.
         * Uses short-circuiting for efficient performance.
         * The intersection found is recorded.
         *
         * @param li a line intersector
         * @return a finder which tests if there is at least one intersection.
         */
        fun createAnyIntersectionFinder(li: LineIntersector): NodingIntersectionFinder {
            return NodingIntersectionFinder(li)
        }

        /**
         * Creates a finder which finds all intersections.
         * The intersections are recorded for later inspection.
         *
         * @param li a line intersector
         * @return a finder which finds all intersections.
         */
        fun createAllIntersectionsFinder(li: LineIntersector): NodingIntersectionFinder {
            val finder = NodingIntersectionFinder(li)
            finder.setFindAllIntersections(true)
            return finder
        }

        /**
         * Creates a finder which finds all interior intersections.
         * The intersections are recorded for later inspection.
         *
         * @param li a line intersector
         * @return a finder which finds all interior intersections.
         */
        fun createInteriorIntersectionsFinder(li: LineIntersector): NodingIntersectionFinder {
            val finder = NodingIntersectionFinder(li)
            finder.setFindAllIntersections(true)
            finder.setInteriorIntersectionsOnly(true)
            return finder
        }

        /**
         * Creates an finder which counts all intersections.
         * The intersections are note recorded to reduce memory usage.
         *
         * @param li a line intersector
         * @return a finder which counts all intersections.
         */
        fun createIntersectionCounter(li: LineIntersector): NodingIntersectionFinder {
            val finder = NodingIntersectionFinder(li)
            finder.setFindAllIntersections(true)
            finder.setKeepIntersections(false)
            return finder
        }

        /**
         * Creates an finder which counts all interior intersections.
         * The intersections are note recorded to reduce memory usage.
         *
         * @param li a line intersector
         * @return a finder which counts all interior intersections.
         */
        fun createInteriorIntersectionCounter(li: LineIntersector): NodingIntersectionFinder {
            val finder = NodingIntersectionFinder(li)
            finder.setInteriorIntersectionsOnly(true)
            finder.setFindAllIntersections(true)
            finder.setKeepIntersections(false)
            return finder
        }

        /**
         * Tests if an intersection occurs between a segmentString interior vertex and another vertex.
         * Note that intersections between two endpoint vertices are valid noding,
         * and are not flagged.
         *
         * @param p00 a segment vertex
         * @param p01 a segment vertex
         * @param p10 a segment vertex
         * @param p11 a segment vertex
         * @param isEnd00 true if vertex is a segmentString endpoint
         * @param isEnd01 true if vertex is a segmentString endpoint
         * @param isEnd10 true if vertex is a segmentString endpoint
         * @param isEnd11 true if vertex is a segmentString endpoint
         * @return true if an intersection is found
         */
        private fun isInteriorVertexIntersection(
            p00: Coordinate, p01: Coordinate,
            p10: Coordinate, p11: Coordinate,
            isEnd00: Boolean, isEnd01: Boolean,
            isEnd10: Boolean, isEnd11: Boolean
        ): Boolean {
            if (isInteriorVertexIntersection(p00, p10, isEnd00, isEnd10)) return true
            if (isInteriorVertexIntersection(p00, p11, isEnd00, isEnd11)) return true
            if (isInteriorVertexIntersection(p01, p10, isEnd01, isEnd10)) return true
            return isInteriorVertexIntersection(
                    p01,
                    p11,
                    isEnd01,
                    isEnd11
                )
        }

        /**
         * Tests if two vertices with at least one in a segmentString interior
         * are equal.
         *
         * @param p0 a segment vertex
         * @param p1 a segment vertex
         * @param isEnd0 true if vertex is a segmentString endpoint
         * @param isEnd1 true if vertex is a segmentString endpoint
         * @return true if an intersection is found
         */
        private fun isInteriorVertexIntersection(
            p0: Coordinate, p1: Coordinate,
            isEnd0: Boolean, isEnd1: Boolean
        ): Boolean {

            // Intersections between endpoints are valid nodes, so not reported
            if (isEnd0 && isEnd1) return false
            return p0.equals2D(p1)
        }

        /**
         * Tests whether a segment in a [SegmentString] is an end segment.
         * (either the first or last).
         *
         * @param segStr a segment string
         * @param index the index of a segment in the segment string
         * @return true if the segment is an end segment
         */
        private fun isEndSegment(segStr: SegmentString, index: Int): Boolean {
            if (index == 0) return true
            return index >= segStr.size() - 2
        }
    }
}