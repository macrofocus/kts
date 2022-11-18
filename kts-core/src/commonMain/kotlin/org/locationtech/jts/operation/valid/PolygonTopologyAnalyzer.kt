/*
 * Copyright (c) 2021 Martin Davis.
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
package org.locationtech.jts.operation.valid

import org.locationtech.jts.algorithm.LineIntersector
import org.locationtech.jts.algorithm.Orientation.isCCW
import org.locationtech.jts.algorithm.PointLocation.locateInRing
import org.locationtech.jts.algorithm.RobustLineIntersector
import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.CoordinateArrays.hasRepeatedPoints
import org.locationtech.jts.geom.CoordinateArrays.removeRepeatedPoints
import org.locationtech.jts.noding.BasicSegmentString
import org.locationtech.jts.noding.MCIndexNoder
import org.locationtech.jts.noding.SegmentString

/**
 * Analyzes the topology of polygonal geometry
 * to determine whether it is valid.
 *
 * Analyzing polygons with inverted rings (shells or exverted holes)
 * is performed if specified.
 * Inverted rings may cause a disconnected interior due to a self-touch;
 * this is reported by [.isInteriorDisconnectedBySelfTouch].
 *
 * @author mdavis
 */
internal class PolygonTopologyAnalyzer(geom: Geometry, private val isInvertedRingValid: Boolean) {
    private var intFinder: PolygonIntersectionAnalyzer? = null
    private var polyRings: List<PolygonRing>? = null

    /**
     * Gets a location where the polyonal interior is disconnected.
     * [.isInteriorDisconnected] must be called first.
     *
     * @return the location of an interior disconnection, or null
     */
    var disconnectionLocation: Coordinate? = null
        private set

    /**
     * Creates a new analyzer for a [Polygon] or [MultiPolygon].
     *
     * @param geom a Polygon or MultiPolygon
     * @param isInvertedRingValid a flag indicating whether inverted rings are allowed
     */
    init {
        analyze(geom)
    }

    fun hasInvalidIntersection(): Boolean {
        return intFinder!!.isInvalid
    }

    val invalidCode: Int
        get() = intFinder!!.invalidCode
    val invalidLocation: Coordinate?
        get() = intFinder!!.invalidLocation
    /**
     * May already be set by a double-touching hole
     */
    /**
     * Tests whether the interior of the polygonal geometry is
     * disconnected.
     * If true, the disconnection location is available from
     * [.getDisconnectionLocation].
     *
     * @return true if the interior is disconnected
     */
    val isInteriorDisconnected: Boolean
        get() {
            /**
             * May already be set by a double-touching hole
             */
            if (disconnectionLocation != null) {
                return true
            }
            if (isInvertedRingValid) {
                checkInteriorDisconnectedBySelfTouch()
                if (disconnectionLocation != null) {
                    return true
                }
            }
            checkInteriorDisconnectedByHoleCycle()
            return disconnectionLocation != null
        }

    /**
     * Tests whether any polygon with holes has a disconnected interior
     * by virtue of the holes (and possibly shell) forming a hole cycle.
     *
     * This is a global check, which relies on determining
     * the touching graph of all holes in a polygon.
     *
     * If inverted rings disconnect the interior
     * via a self-touch, this is checked by the [PolygonIntersectionAnalyzer].
     * If inverted rings are part of a hole cycle
     * this is detected here as well.
     */
    fun checkInteriorDisconnectedByHoleCycle() {
        /**
         * PolyRings will be null for empty, no hole or LinearRing inputs
         */
        if (polyRings != null) {
            disconnectionLocation = PolygonRing.findHoleCycleLocation(
                polyRings!!
            )
        }
    }

    /**
     * Tests if an area interior is disconnected by a self-touching ring.
     * This must be evaluated after other self-intersections have been analyzed
     * and determined to not exist, since the logic relies on
     * the rings not self-crossing (winding).
     *
     * If self-touching rings are not allowed,
     * then the self-touch will previously trigger a self-intersection error.
     */
    fun checkInteriorDisconnectedBySelfTouch() {
        if (polyRings != null) {
            disconnectionLocation = PolygonRing.findInteriorSelfNode(
                polyRings!!
            )
        }
    }

    private fun analyze(geom: Geometry) {
        if (geom.isEmpty) return
        val segStrings = createSegmentStrings(geom, isInvertedRingValid)
        polyRings = getPolygonRings(segStrings)
        intFinder = analyzeIntersections(segStrings)
        if (intFinder!!.hasDoubleTouch()) {
            disconnectionLocation = intFinder!!.doubleTouchLocation
            return
        }
    }

    private fun analyzeIntersections(segStrings: List<SegmentString>): PolygonIntersectionAnalyzer {
        val segInt: PolygonIntersectionAnalyzer =
            PolygonIntersectionAnalyzer(
                isInvertedRingValid
            )
        val noder = MCIndexNoder()
        noder.setSegmentIntersector(segInt)
        noder.computeNodes(segStrings)
        return segInt
    }

    companion object {
        /**
         * Tests whether a ring is nested inside another ring.
         *
         *
         * Preconditions:
         *
         *  * The rings do not cross (i.e. the test is wholly inside or outside the target)
         *  * The rings may touch at discrete points only
         *  * The target ring does not self-cross, but it may self-touch
         *
         * If the test ring start point is properly inside or outside, that provides the result.
         * Otherwise the start point is on the target ring,
         * and the incident start segment (accounting for repeated points) is
         * tested for its topology relative to the target ring.
         *
         * @param test the ring to test
         * @param target the ring to test against
         * @return true if the test ring lies inside the target ring
         */
        fun isRingNested(test: LinearRing, target: LinearRing): Boolean {
            val p0: Coordinate = test.getCoordinateN(0)
            val targetPts: Array<Coordinate> = target.coordinates
            val loc = locateInRing(p0, targetPts)
            if (loc == Location.EXTERIOR) return false
            if (loc == Location.INTERIOR) return true
            /**
             * The start point is on the boundary of the ring.
             * Use the topology at the node to check if the segment
             * is inside or outside the ring.
             */
            val p1 = findNonEqualVertex(test, p0)
            return isIncidentSegmentInRing(p0, p1, targetPts)
        }

        private fun findNonEqualVertex(ring: LinearRing, p: Coordinate): Coordinate {
            var i = 1
            var next: Coordinate = ring.getCoordinateN(i)
            while (next.equals2D(p) && i < ring.numPoints - 1) {
                i += 1
                next = ring.getCoordinateN(i)
            }
            return next
        }

        /**
         * Tests whether a touching segment is interior to a ring.
         *
         *
         * Preconditions:
         *
         *  * The segment does not intersect the ring other than at the endpoints
         *  * The segment vertex p0 lies on the ring
         *  * The ring does not self-cross, but it may self-touch
         *
         * This works for both shells and holes, but the caller must know
         * the ring role.
         *
         * @param p0 the touching vertex of the segment
         * @param p1 the second vertex of the segment
         * @param ringPts the points of the ring
         * @return true if the segment is inside the ring.
         */
        private fun isIncidentSegmentInRing(p0: Coordinate, p1: Coordinate, ringPts: Array<Coordinate>): Boolean {
            val index = intersectingSegIndex(ringPts, p0)
            if (index < 0) {
                throw IllegalArgumentException("Segment vertex does not intersect ring")
            }
            var rPrev = findRingVertexPrev(ringPts, index, p0)
            var rNext = findRingVertexNext(ringPts, index, p0)

            /**
             * If ring orientation is not normalized, flip the corner orientation
             */
            val isInteriorOnRight = !isCCW(ringPts)
            if (!isInteriorOnRight) {
                val temp = rPrev
                rPrev = rNext
                rNext = temp
            }
            return PolygonNode.isInteriorSegment(p0, rPrev, rNext, p1)
        }

        /**
         * Finds the ring vertex previous to a node point on a ring
         * (which is contained in the index'th segment,
         * as either the start vertex or an interior point).
         * Repeated points are skipped over.
         * @param ringPts the ring
         * @param index the index of the segment containing the node
         * @param node the node point
         *
         * @return the previous ring vertex
         */
        private fun findRingVertexPrev(ringPts: Array<Coordinate>, index: Int, node: Coordinate): Coordinate {
            var iPrev = index
            var prev = ringPts[iPrev]
            while (node.equals2D(prev)) {
                iPrev = ringIndexPrev(ringPts, iPrev)
                prev = ringPts[iPrev]
            }
            return prev
        }

        /**
         * Finds the ring vertex next from a node point on a ring
         * (which is contained in the index'th segment,
         * as either the start vertex or an interior point).
         * Repeated points are skipped over.
         * @param ringPts the ring
         * @param index the index of the segment containing the node
         * @param node the node point
         *
         * @return the next ring vertex
         */
        private fun findRingVertexNext(ringPts: Array<Coordinate>, index: Int, node: Coordinate): Coordinate {
            //-- safe, since index is always the start of a ring segment
            var iNext = index + 1
            var next = ringPts[iNext]
            while (node.equals2D(next)) {
                iNext = ringIndexNext(ringPts, iNext)
                next = ringPts[iNext]
            }
            return next
        }

        private fun ringIndexPrev(ringPts: Array<Coordinate>, index: Int): Int {
            return if (index == 0) ringPts.size - 2 else index - 1
        }

        private fun ringIndexNext(ringPts: Array<Coordinate>, index: Int): Int {
            return if (index >= ringPts.size - 2) 0 else index + 1
        }

        /**
         * Computes the index of the segment which intersects a given point.
         * @param ringPts the ring points
         * @param pt the intersection point
         * @return the intersection segment index, or -1 if no intersection is found
         */
        private fun intersectingSegIndex(ringPts: Array<Coordinate>, pt: Coordinate): Int {
            val li: LineIntersector = RobustLineIntersector()
            for (i in 0 until ringPts.size - 1) {
                li.computeIntersection(pt, ringPts[i], ringPts[i + 1])
                if (li.hasIntersection()) {
                    //-- check if pt is the start point of the next segment
                    return if (pt.equals2D(ringPts[i + 1])) {
                        i + 1
                    } else i
                }
            }
            return -1
        }

        /**
         * Finds a self-intersection (if any) in a [LinearRing].
         *
         * @param ring the ring to analyze
         * @return a self-intersection point if one exists, or null
         */
        fun findSelfIntersection(ring: LinearRing): Coordinate? {
            val ata = PolygonTopologyAnalyzer(ring, false)
            return if (ata.hasInvalidIntersection()) ata.invalidLocation else null
        }

        private fun createSegmentStrings(geom: Geometry, isInvertedRingValid: Boolean): List<SegmentString> {
            val segStrings: MutableList<SegmentString> = ArrayList()
            if (geom is LinearRing) {
                segStrings.add(
                    createSegString(
                        geom, null
                    )
                )
                return segStrings
            }
            for (i in 0 until geom.numGeometries) {
                val poly = geom.getGeometryN(i) as Polygon
                if (poly.isEmpty) continue
                val hasHoles = poly.getNumInteriorRing() > 0

                //--- polygons with no holes do not need connected interior analysis
                var shellRing: PolygonRing? = null
                if (hasHoles || isInvertedRingValid) {
                    shellRing = PolygonRing(poly.exteriorRing)
                }
                segStrings.add(createSegString(poly.exteriorRing!!, shellRing))
                for (j in 0 until poly.getNumInteriorRing()) {
                    val hole = poly.getInteriorRingN(j)
                    if (hole.isEmpty) continue
                    val holeRing: PolygonRing =
                        PolygonRing(hole, j, shellRing)
                    segStrings.add(createSegString(hole, holeRing))
                }
            }
            return segStrings
        }

        private fun getPolygonRings(segStrings: List<SegmentString?>): List<PolygonRing>? {
            var polyRings: MutableList<PolygonRing>? = null
            for (ss in segStrings) {
                val polyRing: PolygonRing? =
                    ss!!.data as PolygonRing?
                if (polyRing != null) {
                    if (polyRings == null) {
                        polyRings = ArrayList()
                    }
                    polyRings.add(polyRing)
                }
            }
            return polyRings
        }

        private fun createSegString(
            ring: LinearRing,
            polyRing: PolygonRing?
        ): SegmentString {
            var pts: Array<Coordinate> = ring.coordinates

            //--- repeated points must be removed for accurate intersection detection
            if (hasRepeatedPoints(pts)) {
                pts = removeRepeatedPoints(pts)
            }
            return BasicSegmentString(pts, polyRing)
        }
    }
}