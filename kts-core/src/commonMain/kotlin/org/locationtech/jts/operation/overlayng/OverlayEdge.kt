/*
 * Copyright (c) 2019 Martin Davis.
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
package org.locationtech.jts.operation.overlayng

import org.locationtech.jts.edgegraph.HalfEdge
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.CoordinateArrays.reverse
import org.locationtech.jts.geom.CoordinateList
import org.locationtech.jts.io.WKTWriter

internal class OverlayEdge(
    orig: Coordinate?,
    private val dirPt: Coordinate?,
    /**
     * `true` indicates direction is forward along segString
     * `false` is reverse direction
     * The label must be interpreted accordingly.
     */
    val isForward: Boolean,
    label: OverlayLabel,
    val coordinates: Array<Coordinate>
) : HalfEdge(
    orig!!
) {

    private val label: OverlayLabel
    var isInResultArea = false
        private set
    var isInResultLine = false
        private set
    var isVisited = false
        private set

    /**
     * Link to next edge in the result ring.
     * The origin of the edge is the dest of this edge.
     */
    private var nextResultEdge: OverlayEdge? = null
    var edgeRing: OverlayEdgeRing? = null
    private var maxEdgeRing: MaximalEdgeRing? = null
    private var nextResultMaxEdge: OverlayEdge? = null

    init {
        this.label = label
    }

    public override fun directionPt(): Coordinate {
        return dirPt!!
    }

    fun getLabel(): OverlayLabel {
        return label
    }

    fun getLocation(index: Int, position: Int): Int {
        return label.getLocation(index, position, isForward)
    }

    val coordinate: Coordinate
        get() = orig()
    val coordinatesOriented: Array<Coordinate>
        get() {
            if (isForward) {
                return coordinates
            }
            val copy: Array<Coordinate> = coordinates.copyOf()
            reverse(copy)
            return copy
        }

    /**
     * Adds the coordinates of this edge to the given list,
     * in the direction of the edge.
     * Duplicate coordinates are removed
     * (which means that this is safe to use for a path
     * of connected edges in the topology graph).
     *
     * @param coords the coordinate list to add to
     */
    fun addCoordinates(coords: CoordinateList) {
        val isFirstEdge: Boolean = coords.size > 0
        if (isForward) {
            var startIndex = 1
            if (isFirstEdge) startIndex = 0
            for (i in startIndex until coordinates.size) {
                coords.add(coordinates[i], false)
            }
        } else { // is backward
            var startIndex = coordinates.size - 2
            if (isFirstEdge) startIndex = coordinates.size - 1
            for (i in startIndex downTo 0) {
                coords.add(coordinates[i], false)
            }
        }
    }

    /**
     * Gets the symmetric pair edge of this edge.
     *
     * @return the symmetric pair edge
     */
    fun symOE(): OverlayEdge {
        return sym() as OverlayEdge
    }

    /**
     * Gets the next edge CCW around the origin of this edge,
     * with the same origin.
     * If the origin vertex has degree 1 then this is the edge itself.
     *
     * @return the next edge around the origin
     */
    fun oNextOE(): OverlayEdge {
        return oNext() as OverlayEdge
    }

    val isInResultAreaBoth: Boolean
        get() = isInResultArea && symOE().isInResultArea

    fun unmarkFromResultAreaBoth() {
        isInResultArea = false
        symOE().isInResultArea = false
    }

    fun markInResultArea() {
        isInResultArea = true
    }

    fun markInResultAreaBoth() {
        isInResultArea = true
        symOE().isInResultArea = true
    }

    fun markInResultLine() {
        isInResultLine = true
        symOE().isInResultLine = true
    }

    val isInResult: Boolean
        get() = isInResultArea || isInResultLine
    val isInResultEither: Boolean
        get() = isInResult || symOE().isInResult

    fun setNextResult(e: OverlayEdge?) {
        // Assert: e.orig() == this.dest();
        nextResultEdge = e
    }

    fun nextResult(): OverlayEdge? {
        return nextResultEdge
    }

    val isResultLinked: Boolean
        get() = nextResultEdge != null

    fun setNextResultMax(e: OverlayEdge?) {
        // Assert: e.orig() == this.dest();
        nextResultMaxEdge = e
    }

    fun nextResultMax(): OverlayEdge? {
        return nextResultMaxEdge
    }

    val isResultMaxLinked: Boolean
        get() = nextResultMaxEdge != null

    private fun markVisited() {
        isVisited = true
    }

    fun markVisitedBoth() {
        markVisited()
        symOE().markVisited()
    }

    fun getEdgeRingMax(): MaximalEdgeRing? {
        return maxEdgeRing
    }

    fun setEdgeRingMax(maximalEdgeRing: MaximalEdgeRing) {
        maxEdgeRing = maximalEdgeRing
    }

    override fun toString(): String {
        val orig: Coordinate = orig()
        val dest: Coordinate = dest()
        val dirPtStr = if (coordinates.size > 2) ", " + WKTWriter.format(directionPt()) else ""
        return (("OE( " + WKTWriter.format(orig) + dirPtStr
                + " .. " + WKTWriter.format(dest)
                ) + " ) "
                + label.toString(isForward)
                + resultSymbol()
                + " / Sym: " + symOE().getLabel().toString(symOE().isForward)
                + symOE().resultSymbol())
    }

    private fun resultSymbol(): String {
        if (isInResultArea) return " resA"
        return if (isInResultLine) " resL" else ""
    }

    companion object {
        /**
         * Creates a single OverlayEdge.
         *
         * @param pts
         * @param lbl
         * @param direction
         *
         * @return a new edge based on the given coordinates and direction
         */
        fun createEdge(
            pts: Array<Coordinate>,
            lbl: OverlayLabel,
            direction: Boolean
        ): OverlayEdge {
            val origin: Coordinate?
            val dirPt: Coordinate?
            if (direction) {
                origin = pts[0]
                dirPt = pts[1]
            } else {
                val ilast = pts.size - 1
                origin = pts[ilast]
                dirPt = pts[ilast - 1]
            }
            return OverlayEdge(origin, dirPt, direction, lbl, pts)
        }

        fun createEdgePair(
            pts: Array<Coordinate>,
            lbl: OverlayLabel
        ): OverlayEdge {
            val e0 = createEdge(pts, lbl, true)
            val e1 = createEdge(pts, lbl, false)
            e0.link(e1)
            return e0
        }

        /**
         * Gets a [Comparator] which sorts by the origin Coordinates.
         *
         * @return a Comparator sorting by origin coordinate
         */
        fun nodeComparator(): Comparator<OverlayEdge> {
            return Comparator { e1, e2 -> e1.orig().compareTo(e2.orig()) }
        }
    }
}