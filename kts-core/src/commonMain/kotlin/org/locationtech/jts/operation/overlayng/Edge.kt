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

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Dimension
import org.locationtech.jts.geom.Location
import org.locationtech.jts.io.WKTWriter

/**
 * Represents the linework for edges in the topology
 * derived from (up to) two parent geometries.
 * An edge may be the result of the merging of
 * two or more edges which have the same linework
 * (although possibly different orientations).
 * In this case the topology information is
 * derived from the merging of the information in the
 * source edges.
 * Merged edges can occur in the following situations
 *
 *  * Due to coincident edges of polygonal or linear geometries.
 *  * Due to topology collapse caused by snapping or rounding
 * of polygonal geometries.
 *
 * The source edges may have the same parent geometry,
 * or different ones, or a mix of the two.
 *
 * @author mdavis
 */
internal class Edge(
    val coordinates: Array<Coordinate>,
    info: EdgeSourceInfo
) {
    private var aDim: Int = OverlayLabel.DIM_UNKNOWN
    private var aDepthDelta = 0
    private var aIsHole = false
    private var bDim: Int = OverlayLabel.DIM_UNKNOWN
    private var bDepthDelta = 0
    private var bIsHole = false

    init {
        copyInfo(info)
    }

    fun getCoordinate(index: Int): Coordinate? {
        return coordinates[index]
    }

    fun size(): Int {
        return coordinates.size
    }

    fun direction(): Boolean {
        val pts = coordinates
        if (pts.size < 2) {
            throw IllegalStateException("Edge must have >= 2 points")
        }
        val p0 = pts[0]
        val p1 = pts[1]
        val pn0 = pts[pts.size - 1]
        val pn1 = pts[pts.size - 2]
        var cmp = 0
        val cmp0 = p0!!.compareTo(pn0!!)
        if (cmp0 != 0) cmp = cmp0
        if (cmp == 0) {
            val cmp1 = p1!!.compareTo(pn1!!)
            if (cmp1 != 0) cmp = cmp1
        }
        if (cmp == 0) {
            throw IllegalStateException("Edge direction cannot be determined because endpoints are equal")
        }
        return cmp == -1
    }

    /**
     * Compares two coincident edges to determine
     * whether they have the same or opposite direction.
     *
     * @param edge1 an edge
     * @param edge2 an edge
     * @return true if the edges have the same direction, false if not
     */
    fun relativeDirection(edge2: Edge): Boolean {
        // assert: the edges match (have the same coordinates up to direction)
        if (!getCoordinate(0)!!.equals2D(edge2.getCoordinate(0)!!)) return false
        return getCoordinate(1)!!.equals2D(edge2.getCoordinate(1)!!)
    }

    fun createLabel(): OverlayLabel {
        val lbl: OverlayLabel =
            OverlayLabel()
        initLabel(lbl, 0, aDim, aDepthDelta, aIsHole)
        initLabel(lbl, 1, bDim, bDepthDelta, bIsHole)
        return lbl
    }

    /**
     * Tests whether the edge is part of a shell in the given geometry.
     * This is only the case if the edge is a boundary.
     *
     * @param geomIndex the index of the geometry
     * @return true if this edge is a boundary and part of a shell
     */
    private fun isShell(geomIndex: Int): Boolean {
        return if (geomIndex == 0) {
            aDim == OverlayLabel.DIM_BOUNDARY && !aIsHole
        } else bDim == OverlayLabel.DIM_BOUNDARY && !bIsHole
    }

    private fun copyInfo(info: EdgeSourceInfo) {
        if (info.index == 0) {
            aDim = info.dimension
            aIsHole = info.isHole
            aDepthDelta = info.depthDelta
        } else {
            bDim = info.dimension
            bIsHole = info.isHole
            bDepthDelta = info.depthDelta
        }
    }

    /**
     * Merges an edge into this edge,
     * updating the topology info accordingly.
     *
     * @param edge
     */
    fun merge(edge: Edge) {
        /**
         * Marks this
         * as a shell edge if any contributing edge is a shell.
         * Update hole status first, since it depends on edge dim
         */
        aIsHole = isHoleMerged(0, this, edge)
        bIsHole = isHoleMerged(1, this, edge)
        if (edge.aDim > aDim) aDim = edge.aDim
        if (edge.bDim > bDim) bDim = edge.bDim
        val relDir = relativeDirection(edge)
        val flipFactor = if (relDir) 1 else -1
        aDepthDelta += flipFactor * edge.aDepthDelta
        bDepthDelta += flipFactor * edge.bDepthDelta
        /*
    if (aDepthDelta > 1) {
      Debug.println(this);
    }
    */
    }

    override fun toString(): String {
        val ptsStr = toStringPts(coordinates)
        val aInfo = infoString(0, aDim, aIsHole, aDepthDelta)
        val bInfo = infoString(1, bDim, bIsHole, bDepthDelta)
        return ("Edge( " + ptsStr + " ) "
                + aInfo + "/" + bInfo)
    }

    fun toLineString(): String {
        return WKTWriter.toLineString(coordinates)
    }

    companion object {
        /**
         * Tests if the given point sequence
         * is a collapsed line.
         * A collapsed edge has fewer than two distinct points.
         *
         * @param pts the point sequence to check
         * @return true if the points form a collapsed line
         */
        fun isCollapsed(pts: Array<Coordinate>): Boolean {
            if (pts.size < 2) return true
            // zero-length line
            if (pts[0].equals2D(pts[1])) return true
            // TODO: is pts > 2 with equal points ever expected?
            if (pts.size > 2) {
                if (pts[pts.size - 1].equals2D(pts[pts.size - 2])) return true
            }
            return false
        }

        /**
         * Populates the label for an edge resulting from an input geometry.
         *
         *
         *  * If the edge is not part of the input, the label is left as NOT_PART
         *  * If input is an Area and the edge is on the boundary
         * (which may include some collapses),
         * edge is marked as an AREA edge and side locations are assigned
         *  * If input is an Area and the edge is collapsed
         * (depth delta = 0),
         * the label is set to COLLAPSE.
         * The location will be determined later
         * by evaluating the final graph topology.
         *  * If input is a Line edge is set to a LINE edge.
         * For line edges the line location is not significant
         * (since there is no parent area for which to determine location).
         *
         *
         * @param lbl
         * @param geomIndex
         * @param dim
         * @param depthDelta
         */
        private fun initLabel(
            lbl: OverlayLabel,
            geomIndex: Int,
            dim: Int,
            depthDelta: Int,
            isHole: Boolean
        ) {
            val dimLabel = labelDim(dim, depthDelta)
            when (dimLabel) {
                OverlayLabel.DIM_NOT_PART -> lbl.initNotPart(
                    geomIndex
                )

                OverlayLabel.DIM_BOUNDARY -> lbl.initBoundary(
                    geomIndex,
                    locationLeft(depthDelta),
                    locationRight(depthDelta),
                    isHole
                )

                OverlayLabel.DIM_COLLAPSE -> lbl.initCollapse(
                    geomIndex,
                    isHole
                )

                OverlayLabel.DIM_LINE -> lbl.initLine(geomIndex)
            }
        }

        private fun labelDim(dim: Int, depthDelta: Int): Int {
            if (dim == Dimension.FALSE) return OverlayLabel.DIM_NOT_PART
            if (dim == Dimension.L) return OverlayLabel.DIM_LINE

            // assert: dim is A
            val isCollapse = depthDelta == 0
            return if (isCollapse) OverlayLabel.DIM_COLLAPSE else OverlayLabel.DIM_BOUNDARY
        }

        private fun locationRight(depthDelta: Int): Int {
            val delSign = delSign(depthDelta)
            when (delSign) {
                0 -> return OverlayLabel.LOC_UNKNOWN
                1 -> return Location.INTERIOR
                -1 -> return Location.EXTERIOR
            }
            return OverlayLabel.LOC_UNKNOWN
        }

        private fun locationLeft(depthDelta: Int): Int {
            // TODO: is it always safe to ignore larger depth deltas?
            val delSign = delSign(depthDelta)
            when (delSign) {
                0 -> return OverlayLabel.LOC_UNKNOWN
                1 -> return Location.EXTERIOR
                -1 -> return Location.INTERIOR
            }
            return OverlayLabel.LOC_UNKNOWN
        }

        private fun delSign(depthDel: Int): Int {
            if (depthDel > 0) return 1
            return if (depthDel < 0) -1 else 0
        }

        private fun isHoleMerged(geomIndex: Int, edge1: Edge, edge2: Edge): Boolean {
            // TOD: this might be clearer with tri-state logic for isHole?
            val isShell1 = edge1.isShell(geomIndex)
            val isShell2 = edge2.isShell(geomIndex)
            val isShellMerged = isShell1 || isShell2
            // flip since isHole is stored
            return !isShellMerged
        }

        private fun toStringPts(pts: Array<Coordinate>): String {
            val orig = pts[0]
            val dest = pts[pts.size - 1]
            val dirPtStr =
                if (pts.size > 2) ", " + WKTWriter.format(pts[1]!!) else ""
            return (WKTWriter.format(orig!!)
                    + dirPtStr
                    + " .. " + WKTWriter.format(dest!!))
        }

        fun infoString(index: Int, dim: Int, isHole: Boolean, depthDelta: Int): String {
            return ((if (index == 0) "A:" else "B:")
                    + OverlayLabel.dimensionSymbol(dim)
                    + ringRoleSymbol(dim, isHole)
                    + depthDelta.toString()) // force to string
        }

        private fun ringRoleSymbol(dim: Int, isHole: Boolean): String {
            return if (hasAreaParent(dim)) "" + OverlayLabel.ringRoleSymbol(
                isHole
            ) else ""
        }

        private fun hasAreaParent(dim: Int): Boolean {
            return dim == OverlayLabel.DIM_BOUNDARY || dim == OverlayLabel.DIM_COLLAPSE
        }
    }
}