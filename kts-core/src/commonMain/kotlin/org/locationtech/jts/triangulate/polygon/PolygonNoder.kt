/*
 * Copyright (c) 2022 Martin Davis.
 * Copyright (c) 2026 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.triangulate.polygon

import org.locationtech.jts.algorithm.LineIntersector
import org.locationtech.jts.algorithm.RobustLineIntersector
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.noding.MCIndexNoder
import org.locationtech.jts.noding.NodedSegmentString
import org.locationtech.jts.noding.SegmentIntersector
import org.locationtech.jts.noding.SegmentString
import kotlin.jvm.JvmStatic

/**
 * Adds node vertices to the rings of a polygon
 * where holes touch the shell or each other.
 * The structure of the polygon is preserved.
 *
 * This does not fix invalid polygon topology
 * (such as self-touching or crossing rings).
 * Invalid input remains invalid after noding,
 * and does not trigger an error.
 */
class PolygonNoder(shellRing: Array<Coordinate>, holeRings: Array<Array<Coordinate>>) {

    private val isHoleTouching: BooleanArray = BooleanArray(holeRings.size)
    private val nodedRings: List<NodedSegmentString> = createNodedSegmentStrings(shellRing, holeRings)

    fun node() {
        val nodeAdder: SegmentIntersector = NodeAdder(isHoleTouching)
        val noder = MCIndexNoder(nodeAdder)
        noder.computeNodes(nodedRings)
    }

    val isShellNoded: Boolean
        get() = nodedRings[0].hasNodes()

    fun isHoleNoded(i: Int): Boolean {
        return nodedRings[i + 1].hasNodes()
    }

    val nodedShell: Array<Coordinate>
        get() = nodedRings[0].getNodedCoordinates()

    fun getNodedHole(i: Int): Array<Coordinate> {
        return nodedRings[i + 1].getNodedCoordinates()
    }

    val holesTouching: BooleanArray
        get() = isHoleTouching

    /**
     * A [SegmentIntersector] that added node vertices
     * to [NodedSegmentString]s where a segment touches another
     * segment in its interior.
     *
     * @author mdavis
     */
    private class NodeAdder(private val isHoleTouching: BooleanArray) : SegmentIntersector {

        private val li: LineIntersector = RobustLineIntersector()

        override fun processIntersections(e0: SegmentString, segIndex0: Int, e1: SegmentString, segIndex1: Int) {
            //-- input is assumed valid, so rings do not self-intersect
            if (e0 === e1) return

            val p00 = e0.getCoordinate(segIndex0)
            val p01 = e0.getCoordinate(segIndex0 + 1)
            val p10 = e1.getCoordinate(segIndex1)
            val p11 = e1.getCoordinate(segIndex1 + 1)

            li.computeIntersection(p00, p01, p10, p11)
            /**
             * There should never be 2 intersection points, since
             * that would imply collinear segments, and an invalid polygon
             */
            if (li.intersectionNum == 1) {
                addTouch(e0)
                addTouch(e1)
                val intPt = li.getIntersection(0)
                if (li.isInteriorIntersection(0)) {
                    (e0 as NodedSegmentString).addIntersectionNode(intPt, segIndex0)
                } else if (li.isInteriorIntersection(1)) {
                    (e1 as NodedSegmentString).addIntersectionNode(intPt, segIndex1)
                }
            }
        }

        private fun addTouch(ss: SegmentString) {
            val holeIndex = ss.data as Int
            if (holeIndex >= 0) {
                isHoleTouching[holeIndex] = true
            }
        }

        override val isDone: Boolean
            get() = false
    }

    companion object {
        @JvmStatic
        fun createNodedSegmentStrings(
            shellRing: Array<Coordinate>,
            holeRings: Array<Array<Coordinate>>
        ): MutableList<NodedSegmentString> {
            val segStr: MutableList<NodedSegmentString> = ArrayList()
            segStr.add(createNodedSegString(shellRing, -1))
            for (i in holeRings.indices) {
                segStr.add(createNodedSegString(holeRings[i], i))
            }
            return segStr
        }

        private fun createNodedSegString(ringPts: Array<Coordinate>, i: Int): NodedSegmentString {
            return NodedSegmentString(ringPts, i)
        }
    }
}
