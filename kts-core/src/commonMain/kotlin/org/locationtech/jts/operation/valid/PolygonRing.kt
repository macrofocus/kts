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

import org.locationtech.jts.algorithm.Orientation.isCCW
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.LinearRing
import org.locationtech.jts.legacy.pop
import org.locationtech.jts.legacy.push

/**
 * A ring of a polygon being analyzed for topological validity.
 * The shell and hole rings of valid polygons touch only at discrete points.
 * The "touch" relationship induces a graph over the set of rings.
 * The interior of a valid polygon must be connected.
 * This is the case if there is no "chain" of touching rings
 * (which would partition off part of the interior).
 * This is equivalent to the touch graph having no cycles.
 * Thus the touch graph of a valid polygon is a forest - a set of disjoint trees.
 *
 * Also, in a valid polygon two rings can touch only at a single location,
 * since otherwise they disconnect a portion of the interior between them.
 * This is checked as the touches relation is built
 * (so the touch relation representation for a polygon ring does not need to support
 * more than one touch location for each adjacent ring).
 *
 * The cycle detection algorithm works for polygon rings which also contain self-touches
 * (inverted shells and exverted holes).
 *
 * Polygons with no holes do not need to be checked for
 * a connected interior, unless self-touches are allowed.
 * The class also records the topology at self-touch nodes,
 * to support checking if an invalid self-touch disconnects the polygon.
 *
 * @author mdavis
 */
internal class PolygonRing {
    private var id: Int
    private var shell: PolygonRing?
    private var ring: LinearRing?

    /**
     * The root of the touch graph tree containing this ring.
     * Serves as the id for the graph partition induced by the touch relation.
     */
    private var touchSetRoot: PolygonRing? = null
    // lazily created
    /**
     * The set of PolygonRingTouch links
     * for this ring.
     * The set of all touches in the rings of a polygon
     * forms the polygon touch graph.
     * This supports detecting touch cycles, which
     * reveal the condition of a disconnected interior.
     *
     * Only a single touch is recorded between any two rings,
     * since more than one touch between two rings
     * indicates interior disconnection as well.
     */
    private var touches: MutableMap<Int, PolygonRingTouch>? = null

    /**
     * The set of self-nodes in this ring.
     * This supports checking valid ring self-touch topology.
     */
    private var selfNodes: ArrayList<PolygonRingSelfNode>? = null

    /**
     * Creates a ring for a polygon shell.
     * @param ring
     */
    constructor(ring: LinearRing?) {
        this.ring = ring
        id = -1
        shell = this
    }

    /**
     * Creates a ring for a polygon hole.
     * @param ring the ring geometry
     * @param index the index of the hole
     * @param shell the parent polygon shell
     */
    constructor(ring: LinearRing?, index: Int, shell: PolygonRing?) {
        this.ring = ring
        id = index
        this.shell = shell
    }

    fun isSamePolygon(ring: PolygonRing): Boolean {
        return shell === ring.shell
    }

    fun isShell(): Boolean {
        return shell === this
    }

    private val isInTouchSet: Boolean
        private get() = touchSetRoot != null

    private fun setTouchSetRoot(ring: PolygonRing) {
        touchSetRoot = ring
    }

    private fun hasTouches(): Boolean {
        return touches != null && !touches!!.isEmpty()
    }

    private fun getTouches(): Collection<PolygonRingTouch> {
        return touches!!.values
    }

    private fun addTouch(ring: PolygonRing, pt: Coordinate) {
        if (touches == null) {
            touches = HashMap<Int, PolygonRingTouch>()
        }
        val touch = touches!![ring.id]
        if (touch == null) {
            touches!![ring.id] = PolygonRingTouch(ring, pt)
        }
    }

    fun addSelfTouch(origin: Coordinate, e00: Coordinate?, e01: Coordinate?, e10: Coordinate?, e11: Coordinate?) {
        if (selfNodes == null) {
            selfNodes = ArrayList<PolygonRingSelfNode>()
        }
        selfNodes!!.add(PolygonRingSelfNode(origin, e00, e01, e10, e11))
    }

    /**
     * Tests if this ring touches a given ring at
     * the single point specified.
     *
     * @param ring the other PolygonRing
     * @param pt the touch point
     * @return true if the rings touch only at the given point
     */
    private fun isOnlyTouch(ring: PolygonRing, pt: Coordinate): Boolean {
        //--- no touches for this ring
        if (touches == null) return true
        //--- no touches for other ring
        val touch = touches!![ring.id] ?: return true
        //--- the rings touch - check if point is the same
        return touch.isAtLocation(pt)
    }

    /**
     * Detects whether the subgraph of holes linked by touch to this ring
     * contains a hole cycle.
     * If no cycles are detected, the set of touching rings is a tree.
     * The set is marked using this ring as the root.
     *
     * @return a vertex in a hole cycle, or null if no cycle found
     */
    private fun findHoleCycleLocation(): Coordinate? {
        //--- the touch set including this ring is already processed
        if (isInTouchSet) return null

        //--- scan the touch set tree rooted at this ring
        // Assert: this.touchSetRoot is null
        val root = this
        root.setTouchSetRoot(root)
        if (!hasTouches()) return null
        val touchStack: ArrayDeque<PolygonRingTouch> = ArrayDeque<PolygonRingTouch>()
        init(root, touchStack)
        while (!touchStack.isEmpty()) {
            val touch: PolygonRingTouch = touchStack.pop()!!
            val holeCyclePt = scanForHoleCycle(touch, root, touchStack)
            if (holeCyclePt != null) {
                return holeCyclePt
            }
        }
        return null
    }

    /**
     * Scans for a hole cycle starting at a given touch.
     *
     * @param currentTouch the touch to investigate
     * @param root the root of the touch subgraph
     * @param touchStack the stack of touches to scan
     * @return a vertex in a hole cycle if found, or null
     */
    private fun scanForHoleCycle(
        currentTouch: PolygonRingTouch,
        root: PolygonRing,
        touchStack: ArrayDeque<PolygonRingTouch>
    ): Coordinate? {
        val ring = currentTouch.ring
        val currentPt = currentTouch.coordinate
        /**
         * Scan the touched rings
         * Either they form a hole cycle, or they are added to the touch set
         * and pushed on the stack for scanning
         */
        for (touch in ring.getTouches()) {
            /**
             * Don't check touches at the entry point
             * to avoid trivial cycles.
             * They will already be processed or on the stack
             * from the previous ring (which touched
             * all the rings at that point as well)
             */
            if (currentPt.equals2D(touch.coordinate)) continue
            /**
             * Test if the touched ring has already been
             * reached via a different touch path.
             * This is indicated by it already being marked as
             * part of the touch set.
             * This indicates a hole cycle has been found.
             */
            val touchRing = touch.ring
            if (touchRing.touchSetRoot === root) return touch.coordinate
            touchRing.setTouchSetRoot(root)
            touchStack.push(touch)
        }
        return null
    }

    /**
     * Finds the location of an invalid interior self-touch in this ring,
     * if one exists.
     *
     * @return the location of an interior self-touch node, or null if there are none
     */
    fun findInteriorSelfNode(): Coordinate? {
        if (selfNodes == null) return null
        /**
         * Determine if the ring interior is on the Right.
         * This is the case if the ring is a shell and is CW,
         * or is a hole and is CCW.
         */
        val isCCW: Boolean = isCCW(ring!!.coordinates)
        val isInteriorOnRight = isShell() xor isCCW
        for (selfNode in selfNodes!!) {
            if (!selfNode.isExterior(isInteriorOnRight)) {
                return selfNode.coordinate
            }
        }
        return null
    }

    override fun toString(): String {
        return ring.toString()
    }

    companion object {
        /**
         * Tests if a polygon ring represents a shell.
         *
         * @param polyRing the ring to test (may be null)
         * @return true if the ring represents a shell
         */
        fun isShell(polyRing: PolygonRing?): Boolean {
            return polyRing?.isShell() ?: true
        }

        /**
         * Records a touch location between two rings,
         * and checks if the rings already touch in a different location.
         *
         * @param ring0 a polygon ring
         * @param ring1 a polygon ring
         * @param pt the location where they touch
         * @return true if the polygons already touch
         */
        fun addTouch(ring0: PolygonRing?, ring1: PolygonRing?, pt: Coordinate): Boolean {
            //--- skip if either polygon does not have holes
            if (ring0 == null || ring1 == null) return false

            //--- only record touches within a polygon
            if (!ring0.isSamePolygon(ring1)) return false
            if (!ring0.isOnlyTouch(ring1, pt)) return true
            if (!ring1.isOnlyTouch(ring0, pt)) return true
            ring0.addTouch(ring1, pt)
            ring1.addTouch(ring0, pt)
            return false
        }

        /**
         * Finds a location (if any) where a chain of holes forms a cycle
         * in the ring touch graph.
         * The shell may form part of the chain as well.
         * This indicates that a set of holes disconnects the interior of a polygon.
         *
         * @param polyRings the list of rings to check
         * @return a vertex contained in a ring cycle, or null if none is found
         */
        fun findHoleCycleLocation(polyRings: List<PolygonRing>): Coordinate? {
            for (polyRing in polyRings) {
                if (!polyRing.isInTouchSet) {
                    val holeCycleLoc = polyRing.findHoleCycleLocation()
                    if (holeCycleLoc != null) return holeCycleLoc
                }
            }
            return null
        }

        /**
         * Finds a location of an interior self-touch in a list of rings,
         * if one exists.
         * This indicates that a self-touch disconnects the interior of a polygon,
         * which is invalid.
         *
         * @param polyRings the list of rings to check
         * @return the location of an interior self-touch node, or null if there are none
         */
        fun findInteriorSelfNode(polyRings: List<PolygonRing>): Coordinate? {
            for (polyRing in polyRings) {
                val interiorSelfNode = polyRing.findInteriorSelfNode()
                if (interiorSelfNode != null) {
                    return interiorSelfNode
                }
            }
            return null
        }

        private fun init(
            root: PolygonRing,
            touchStack: ArrayDeque<PolygonRingTouch>
        ) {
            for (touch in root.getTouches()) {
                touch.ring.setTouchSetRoot(root)
                touchStack.push(touch)
            }
        }
    }
}

/**
 * Records a point where a [PolygonRing] touches another one.
 * This forms an edge in the induced ring touch graph.
 *
 * @author mdavis
 */
internal class PolygonRingTouch(val ring: PolygonRing, val coordinate: Coordinate) {

    fun isAtLocation(pt: Coordinate?): Boolean {
        return coordinate.equals2D(pt!!)
    }
}

/**
 * Represents a ring self-touch node, recording the node (intersection point)
 * and the endpoints of the four adjacent segments.
 *
 * This is used to evaluate validity of self-touching nodes,
 * when they are allowed.
 *
 * @author mdavis
 */
internal class PolygonRingSelfNode//this.e11 = e11;    //private Coordinate e11;
    (
    /**
     * The node point.
     *
     * @return
     */
    val coordinate: Coordinate,
    private val e00: Coordinate?, private val e01: Coordinate?,
    private val e10: Coordinate?, e11: Coordinate?
) {
    /**
     * Tests if a self-touch has the segments of each half of the touch
     * lying in the exterior of a polygon.
     * This is a valid self-touch.
     * It applies to both shells and holes.
     * Only one of the four possible cases needs to be tested,
     * since the situation has full symmetry.
     *
     * @param isInteriorOnRight whether the interior is to the right of the parent ring
     * @return true if the self-touch is in the exterior
     */
    fun isExterior(isInteriorOnRight: Boolean): Boolean {
        /**
         * Note that either corner and either of the other edges could be used to test.
         * The situation is fully symmetrical.
         */
        val isInteriorSeg: Boolean =
            PolygonNode.isInteriorSegment(coordinate, e00, e01, e10)
        return if (isInteriorOnRight) !isInteriorSeg else isInteriorSeg
    }
}