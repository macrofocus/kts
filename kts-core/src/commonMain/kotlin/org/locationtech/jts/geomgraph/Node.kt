/*
 * Copyright (c) 2016 Vivid Solutions.
 * Copyright (c) 2020 Macrofocus GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.geomgraph

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.IntersectionMatrix
import org.locationtech.jts.geom.Location

/**
 * @version 1.7
 */
open class Node(// only non-null if this node is precise
    override var coordinate: Coordinate, var edges: EdgeEndStar?
) : GraphComponent() {
    /**
     * Tests whether any incident edge is flagged as
     * being in the result.
     * This test can be used to determine if the node is in the result,
     * since if any incident edge is in the result, the node must be in the result as well.
     *
     * @return `true` if any incident edge in the in the result
     */
    val isIncidentEdgeInResult: Boolean
        get() {
            val it: Iterator<*> = edges!!.getEdges().iterator()
            while (it.hasNext()) {
                val de = it.next() as DirectedEdge
                if (de.edge.isInResult) return true
            }
            return false
        }
    override val isIsolated: Boolean
        get() = label!!.geometryCount == 1

    /**
     * Basic nodes do not compute IMs
     */
    override fun computeIM(im: IntersectionMatrix) {}

    /**
     * Add the edge to the list of edges at this node
     */
    fun add(e: EdgeEnd) {
        // Assert: start pt of e is equal to node point
        edges!!.insert(e)
        e.node = this
    }

    fun mergeLabel(n: Node) {
        mergeLabel(n.label)
    }

    /**
     * To merge labels for two nodes,
     * the merged location for each LabelElement is computed.
     * The location for the corresponding node LabelElement is set to the result,
     * as long as the location is non-null.
     */
    fun mergeLabel(label2: Label?) {
        for (i in 0..1) {
            val loc = computeMergedLocation(label2, i)
            val thisLoc = label!!.getLocation(i)
            if (thisLoc == Location.NONE) label!!.setLocation(i, loc)
        }
    }

    fun setLabel(argIndex: Int, onLocation: Int) {
        if (label == null) {
            label = Label(argIndex, onLocation)
        } else label!!.setLocation(argIndex, onLocation)
    }

    /**
     * Updates the label of a node to BOUNDARY,
     * obeying the mod-2 boundaryDetermination rule.
     */
    fun setLabelBoundary(argIndex: Int) {
        if (label == null) return

        // determine the current location for the point (if any)
        var loc = Location.NONE
        if (label != null) loc = label!!.getLocation(argIndex)
        // flip the loc
        val newLoc: Int
        newLoc = when (loc) {
            Location.BOUNDARY -> Location.INTERIOR
            Location.INTERIOR -> Location.BOUNDARY
            else -> Location.BOUNDARY
        }
        label!!.setLocation(argIndex, newLoc)
    }

    /**
     * The location for a given eltIndex for a node will be one
     * of { null, INTERIOR, BOUNDARY }.
     * A node may be on both the boundary and the interior of a geometry;
     * in this case, the rule is that the node is considered to be in the boundary.
     * The merged location is the maximum of the two input values.
     */
    fun computeMergedLocation(label2: Label?, eltIndex: Int): Int {
        var loc = Location.NONE
        loc = label!!.getLocation(eltIndex)
        if (!label2!!.isNull(eltIndex)) {
            val nLoc = label2.getLocation(eltIndex)
            if (loc != Location.BOUNDARY) loc = nLoc
        }
        return loc
    }

//    fun print(out: PrintStream) {
//        out.println("node " + coordinate + " lbl: " + label)
//    }

    init {
        label = Label(0, Location.NONE)
    }
}