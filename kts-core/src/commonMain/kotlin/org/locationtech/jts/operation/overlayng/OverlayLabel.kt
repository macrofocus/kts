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

import org.locationtech.jts.geom.Location
import org.locationtech.jts.geom.Location.toLocationSymbol
import org.locationtech.jts.geom.Position

/**
 * A structure recording the topological situation
 * for an edge in a topology graph
 * used during overlay processing.
 * A label contains the topological [Location]s for
 * one or two input geometries to an overlay operation.
 * An input geometry may be either a Line or an Area.
 * The label locations for each input geometry are populated
 * with the {@Location}s for the edge [Position]s
 * when they are created or once they are computed by topological evaluation.
 * A label also records the (effective) dimension of each input geometry.
 * For area edges the role (shell or hole)
 * of the originating ring is recorded, to allow
 * determination of edge handling in collapse cases.
 *
 * In an [OverlayGraph] a single label is shared between
 * the two oppositely-oriented {@ link OverlayEdge}s of a symmetric pair.
 * * Accessors for orientation-sensitive information
 * * are parameterized by the orientation of the containing edge.
 * *
 *
 * * For each input geometry (0 and 1), the label records
 * * that an edge is in one of the following states
 * * (identified by the `dim` field).
 * * Each state has additional information about the edge topology.
 * *
 * *  * A **Boundary** edge of an Area (polygon)
 * *
 * *    * `dim` = DIM_BOUNDARY
 * *    * `locLeft, locRight` : the locations of the edge sides for the Area
 * *    * `locLine` : INTERIOR
 * *    * `isHole` : whether the
 * * edge is in a shell or a hole (the ring role)
 * *
 * *
 * *  * A **Collapsed** edge of an input Area
 * * (formed by merging two or more parent edges)
 * *
 * *    * `dim` = DIM_COLLAPSE
 * *    * `locLine` : the location of the edge relative to the effective input Area
 * *       (a collapsed spike is EXTERIOR, a collapsed gore or hole is INTERIOR)
 * *    * `isHole` : `true` if all parent edges are in holes;
 * *                             `false` if some parent edge is in a shell
 * *
 * *
 * *  * A **Line** edge from an input line
 * *
 * *    * `dim` = DIM_LINE
 * *    * `locLine` : the location of the edge relative to the Line.
 * *   Initialized to LOC_UNKNOWN to simplify logic.
 * *
 * *
 * *  * An edge which is **Not Part** of an input geometry
 * * (and thus must be part of the other geometry).
 * *
 * *    * `dim` = NOT_PART
 * *
 * *
 * *
 * * Note that:
 * *
 * *  * an edge cannot be both a Collapse edge and a Line edge in the same input geometry,
 * * because input geometries must be homogeneous in dimension.
 * *  * an edge may be an Boundary edge in one input geometry
 * * and a Line or Collapse edge in the other input.
 * *
 * *
 * * @author Martin Davis
 *
 */
class OverlayLabel {
    private var aDim = DIM_NOT_PART
    private var aIsHole = false
    private var aLocLeft = LOC_UNKNOWN
    private var aLocRight = LOC_UNKNOWN
    private var aLocLine = LOC_UNKNOWN
    private var bDim = DIM_NOT_PART
    private var bIsHole = false
    private var bLocLeft = LOC_UNKNOWN
    private var bLocRight = LOC_UNKNOWN
    private var bLocLine = LOC_UNKNOWN

    /**
     * Creates a label for an Area edge.
     *
     * @param index the input index of the parent geometry
     * @param locLeft the location of the left side of the edge
     * @param locRight the location of the right side of the edge
     * @param isHole whether the edge role is a hole or a shell
     */
    constructor(index: Int, locLeft: Int, locRight: Int, isHole: Boolean) {
        initBoundary(index, locLeft, locRight, isHole)
    }

    /**
     * Creates a label for a Line edge.
     *
     * @param index the input index of the parent geometry
     */
    constructor(index: Int) {
        initLine(index)
    }

    /**
     * Creates an uninitialized label.
     *
     */
    constructor() {}

    /**
     * Creates a label which is a copy of another label.
     *
     * @param lbl
     */
    constructor(lbl: OverlayLabel) {
        aLocLeft = lbl.aLocLeft
        aLocRight = lbl.aLocRight
        aLocLine = lbl.aLocLine
        aDim = lbl.aDim
        aIsHole = lbl.aIsHole
        bLocLeft = lbl.bLocLeft
        bLocRight = lbl.bLocRight
        bLocLine = lbl.bLocLine
        bDim = lbl.bDim
        bIsHole = lbl.bIsHole
    }

    /**
     * Gets the effective dimension of the given input geometry.
     *
     * @param index the input geometry index
     * @return the dimension
     *
     * @see .DIM_UNKNOWN
     *
     * @see .DIM_NOT_PART
     *
     * @see .DIM_LINE
     *
     * @see .DIM_BOUNDARY
     *
     * @see .DIM_COLLAPSE
     */
    fun dimension(index: Int): Int {
        return if (index == 0) aDim else bDim
    }

    /**
     * Initializes the label for an input geometry which is an Area boundary.
     *
     * @param index the input index of the parent geometry
     * @param locLeft the location of the left side of the edge
     * @param locRight the location of the right side of the edge
     * @param isHole whether the edge role is a hole or a shell
     */
    fun initBoundary(index: Int, locLeft: Int, locRight: Int, isHole: Boolean) {
        if (index == 0) {
            aDim = DIM_BOUNDARY
            aIsHole = isHole
            aLocLeft = locLeft
            aLocRight = locRight
            aLocLine = Location.INTERIOR
        } else {
            bDim = DIM_BOUNDARY
            bIsHole = isHole
            bLocLeft = locLeft
            bLocRight = locRight
            bLocLine = Location.INTERIOR
        }
    }

    /**
     * Initializes the label for an edge which is the collapse of
     * part of the boundary of an Area input geometry.
     * The location of the collapsed edge relative to the
     * parent area geometry is initially unknown.
     * It must be determined from the topology of the overlay graph
     *
     * @param index the index of the parent input geometry
     * @param isHole whether the dominant edge role is a hole or a shell
     */
    fun initCollapse(index: Int, isHole: Boolean) {
        if (index == 0) {
            aDim = DIM_COLLAPSE
            aIsHole = isHole
        } else {
            bDim = DIM_COLLAPSE
            bIsHole = isHole
        }
    }

    /**
     * Initializes the label for an input geometry which is a Line.
     *
     * @param index the index of the parent input geometry
     */
    fun initLine(index: Int) {
        if (index == 0) {
            aDim = DIM_LINE
            aLocLine = LOC_UNKNOWN
        } else {
            bDim = DIM_LINE
            bLocLine = LOC_UNKNOWN
        }
    }

    /**
     * Initializes the label for an edge which is not part of an input geometry.
     * @param index the index of the input geometry
     */
    fun initNotPart(index: Int) {
        // this assumes locations are initialized to UNKNOWN
        if (index == 0) {
            aDim = DIM_NOT_PART
        } else {
            bDim = DIM_NOT_PART
        }
    }

    /**
     * Sets the line location.
     *
     * This is used to set the locations for linear edges
     * encountered during area label propagation.
     *
     * @param index source to update
     * @param loc location to set
     */
    fun setLocationLine(index: Int, loc: Int) {
        if (index == 0) {
            aLocLine = loc
        } else {
            bLocLine = loc
        }
    }

    /**
     * Sets the location of all postions for a given input.
     *
     * @param index the index of the input geometry
     * @param loc the location to set
     */
    fun setLocationAll(index: Int, loc: Int) {
        if (index == 0) {
            aLocLine = loc
            aLocLeft = loc
            aLocRight = loc
        } else {
            bLocLine = loc
            bLocLeft = loc
            bLocRight = loc
        }
    }

    /**
     * Sets the location for a collapsed edge (the Line position)
     * for an input geometry,
     * depending on the ring role recorded in the label.
     * If the input geometry edge is from a shell,
     * the location is [Location.EXTERIOR], if it is a hole
     * it is [Location.INTERIOR].
     *
     * @param index the index of the input geometry
     */
    fun setLocationCollapse(index: Int) {
        val loc = if (isHole(index)) Location.INTERIOR else Location.EXTERIOR
        if (index == 0) {
            aLocLine = loc
        } else {
            bLocLine = loc
        }
    }

    /**
     * Tests whether at least one of the sources is a Line.
     *
     * @return true if at least one source is a line
     */
    val isLine: Boolean
        get() = aDim == DIM_LINE || bDim == DIM_LINE

    /**
     * Tests whether a source is a Line.
     *
     * @param index the index of the input geometry
     * @return true if the input is a Line
     */
    fun isLine(index: Int): Boolean {
        return if (index == 0) {
            aDim == DIM_LINE
        } else bDim == DIM_LINE
    }

    /**
     * Tests whether an edge is linear (a Line or a Collapse) in an input geometry.
     *
     * @param index the index of the input geometry
     * @return true if the edge is linear
     */
    fun isLinear(index: Int): Boolean {
        return if (index == 0) {
            aDim == DIM_LINE || aDim == DIM_COLLAPSE
        } else bDim == DIM_LINE || bDim == DIM_COLLAPSE
    }

    /**
     * Tests whether a the source of a label is known.
     *
     * @param index the index of the source geometry
     * @return true if the source is known
     */
    fun isKnown(index: Int): Boolean {
        return if (index == 0) {
            aDim != DIM_UNKNOWN
        } else bDim != DIM_UNKNOWN
    }

    /**
     * Tests whether a label is for an edge which is not part
     * of a given input geometry.
     *
     * @param index the index of the source geometry
     * @return true if the edge is not part of the geometry
     */
    fun isNotPart(index: Int): Boolean {
        return if (index == 0) {
            aDim == DIM_NOT_PART
        } else bDim == DIM_NOT_PART
    }

    /**
     * Tests if a label is for an edge which is in the boundary of either source geometry.
     *
     * @return true if the label is a boundary for either source
     */
    val isBoundaryEither: Boolean
        get() = aDim == DIM_BOUNDARY || bDim == DIM_BOUNDARY

    /**
     * Tests if a label is for an edge which is in the boundary of both source geometries.
     *
     * @return true if the label is a boundary for both sources
     */
    val isBoundaryBoth: Boolean
        get() = aDim == DIM_BOUNDARY && bDim == DIM_BOUNDARY

    /**
     * Tests if the label is a collapsed edge of one area
     * and is a (non-collapsed) boundary edge of the other area.
     *
     * @return true if the label is for a collapse coincident with a boundary
     */
    val isBoundaryCollapse: Boolean
        get() = if (isLine) false else !isBoundaryBoth

    /**
     * Tests if a label is for an edge where two
     * area touch along their boundary.
     *
     * @return true if the edge is a boundary touch
     */
    val isBoundaryTouch: Boolean
        get() = (isBoundaryBoth
                && getLocation(0, Position.RIGHT, true) != getLocation(1, Position.RIGHT, true))

    /**
     * Tests if a label is for an edge which is in the boundary of a source geometry.
     * Collapses are not reported as being in the boundary.
     *
     * @param index the index of the input geometry
     * @return true if the label is a boundary for the source
     */
    fun isBoundary(index: Int): Boolean {
        return if (index == 0) {
            aDim == DIM_BOUNDARY
        } else bDim == DIM_BOUNDARY
    }

    /**
     * Tests whether a label is for an edge which is a boundary of one geometry
     * and not part of the other.
     *
     * @return true if the edge is a boundary singleton
     */
    val isBoundarySingleton: Boolean
        get() {
            if (aDim == DIM_BOUNDARY && bDim == DIM_NOT_PART) return true
            return bDim == DIM_BOUNDARY && aDim == DIM_NOT_PART
        }

    /**
     * Tests if the line location for a source is unknown.
     *
     * @param index the index of the input geometry
     * @return true if the line location is unknown
     */
    fun isLineLocationUnknown(index: Int): Boolean {
        return if (index == 0) {
            aLocLine == LOC_UNKNOWN
        } else {
            bLocLine == LOC_UNKNOWN
        }
    }

    /**
     * Tests if a line edge is inside a source geometry
     * (i.e. it has location [Location.INTERIOR]).
     *
     * @param index the index of the input geometry
     * @return true if the line is inside the source geometry
     */
    fun isLineInArea(index: Int): Boolean {
        return if (index == 0) {
            aLocLine == Location.INTERIOR
        } else bLocLine == Location.INTERIOR
    }

    /**
     * Tests if the ring role of an edge is a hole.
     *
     * @param index the index of the input geometry
     * @return true if the ring role is a hole
     */
    fun isHole(index: Int): Boolean {
        return if (index == 0) {
            aIsHole
        } else {
            bIsHole
        }
    }

    /**
     * Tests if an edge is a Collapse for a source geometry.
     *
     * @param index the index of the input geometry
     * @return true if the label indicates the edge is a collapse for the source
     */
    fun isCollapse(index: Int): Boolean {
        return dimension(index) == DIM_COLLAPSE
    }

    /**
     * Tests if a label is a Collapse has location [Location.INTERIOR],
     * to at least one source geometry.
     *
     * @return true if the label is an Interior Collapse to a source geometry
     */
    val isInteriorCollapse: Boolean
        get() {
            if (aDim == DIM_COLLAPSE && aLocLine == Location.INTERIOR) return true
            return bDim == DIM_COLLAPSE && bLocLine == Location.INTERIOR
        }

    /**
     * Tests if a label is a Collapse
     * and NotPart with location [Location.INTERIOR] for the other geometry.
     *
     * @return true if the label is a Collapse and a NotPart with Location Interior
     */
    val isCollapseAndNotPartInterior: Boolean
        get() {
            if (aDim == DIM_COLLAPSE && bDim == DIM_NOT_PART && bLocLine == Location.INTERIOR) return true
            return bDim == DIM_COLLAPSE && aDim == DIM_NOT_PART && aLocLine == Location.INTERIOR
        }

    /**
     * Gets the line location for a source geometry.
     *
     * @param index the index of the input geometry
     * @return the line location for the source
     */
    fun getLineLocation(index: Int): Int {
        return if (index == 0) {
            aLocLine
        } else {
            bLocLine
        }
    }

    /**
     * Tests if a line is in the interior of a source geometry.
     *
     * @param index the index of the source geometry
     * @return true if the label is a line and is interior
     */
    fun isLineInterior(index: Int): Boolean {
        return if (index == 0) {
            aLocLine == Location.INTERIOR
        } else bLocLine == Location.INTERIOR
    }

    /**
     * Gets the location for a [Position] of an edge of a source
     * for an edge with given orientation.
     *
     * @param index the index of the source geometry
     * @param position the position to get the location for
     * @param isForward true if the orientation of the containing edge is forward
     * @return the location of the oriented position in the source
     */
    fun getLocation(index: Int, position: Int, isForward: Boolean): Int {
        if (index == 0) {
            when (position) {
                Position.LEFT -> return if (isForward) aLocLeft else aLocRight
                Position.RIGHT -> return if (isForward) aLocRight else aLocLeft
                Position.ON -> return aLocLine
            }
        }
        when (position) {
            Position.LEFT -> return if (isForward) bLocLeft else bLocRight
            Position.RIGHT -> return if (isForward) bLocRight else bLocLeft
            Position.ON -> return bLocLine
        }
        return LOC_UNKNOWN
    }

    /**
     * Gets the location for this label for either
     * a Boundary or a Line edge.
     * This supports a simple determination of
     * whether the edge should be included as a result edge.
     *
     * @param index the source index
     * @param position the position for a boundary label
     * @param isForward the direction for a boundary label
     * @return the location for the specified position
     */
    fun getLocationBoundaryOrLine(index: Int, position: Int, isForward: Boolean): Int {
        return if (isBoundary(index)) {
            getLocation(index, position, isForward)
        } else getLineLocation(index)
    }

    /**
     * Gets the linear location for the given source.
     *
     * @param index the source geometry index
     * @return the linear location for the source
     */
    fun getLocation(index: Int): Int {
        return if (index == 0) {
            aLocLine
        } else bLocLine
    }

    /**
     * Tests whether this label has side position information
     * for a source geometry.
     *
     * @param index the source geometry index
     * @return true if at least one side position is known
     */
    fun hasSides(index: Int): Boolean {
        return if (index == 0) {
            (aLocLeft != LOC_UNKNOWN
                    || aLocRight != LOC_UNKNOWN)
        } else (bLocLeft != LOC_UNKNOWN
                || bLocRight != LOC_UNKNOWN)
    }

    /**
     * Creates a copy of this label.
     *
     * @return a copy of the label
     */
    fun copy(): OverlayLabel {
        return OverlayLabel(this)
    }

    override fun toString(): String {
        return toString(true)
    }

    fun toString(isForward: Boolean): String {
        val buf: StringBuilder = StringBuilder()
        buf.append("A:")
        buf.append(locationString(0, isForward))
        buf.append("/B:")
        buf.append(locationString(1, isForward))
        return buf.toString()
    }

    private fun locationString(index: Int, isForward: Boolean): String {
        val buf: StringBuilder = StringBuilder()
        if (isBoundary(index)) {
            buf.append(toLocationSymbol(getLocation(index, Position.LEFT, isForward)))
            buf.append(toLocationSymbol(getLocation(index, Position.RIGHT, isForward)))
        } else {
            // is a linear edge
            buf.append(toLocationSymbol(if (index == 0) aLocLine else bLocLine))
        }
        if (isKnown(index)) buf.append(dimensionSymbol(if (index == 0) aDim else bDim))
        if (isCollapse(index)) {
            buf.append(ringRoleSymbol(if (index == 0) aIsHole else bIsHole))
        }
        return buf.toString()
    }

    companion object {
        private const val SYM_UNKNOWN = '#'
        private const val SYM_BOUNDARY = 'B'
        private const val SYM_COLLAPSE = 'C'
        private const val SYM_LINE = 'L'

        /**
         * The dimension of an input geometry which is not known
         */
        const val DIM_UNKNOWN = -1

        /**
         * The dimension of an edge which is not part of a specified input geometry.
         */
        const val DIM_NOT_PART = DIM_UNKNOWN

        /**
         * The dimension of an edge which is a line.
         */
        const val DIM_LINE = 1

        /**
         * The dimension for an edge which is part of an input Area geometry boundary.
         */
        const val DIM_BOUNDARY = 2

        /**
         * The dimension for an edge which is a collapsed part of an input Area geometry boundary.
         * A collapsed edge represents two or more line segments which have the same endpoints.
         * They usually are caused by edges in valid polygonal geometries
         * having their endpoints become identical due to precision reduction.
         */
        const val DIM_COLLAPSE = 3

        /**
         * Indicates that the location is currently unknown
         */
        var LOC_UNKNOWN = Location.NONE

        /**
         * Gets a symbol for the a ring role (Shell or Hole).
         *
         * @param isHole true for a hole, false for a shell
         * @return the ring role symbol character
         */
        fun ringRoleSymbol(isHole: Boolean): Char {
            return if (isHole) 'h' else 's'
        }

        /**
         * Gets the symbol for the dimension code of an edge.
         *
         * @param dim the dimension code
         * @return the dimension symbol character
         */
        fun dimensionSymbol(dim: Int): Char {
            when (dim) {
                DIM_LINE -> return SYM_LINE
                DIM_COLLAPSE -> return SYM_COLLAPSE
                DIM_BOUNDARY -> return SYM_BOUNDARY
            }
            return SYM_UNKNOWN
        }
    }
}