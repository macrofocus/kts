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

import org.locationtech.jts.geom.Location
import org.locationtech.jts.geom.Position
import org.locationtech.jts.geom.TopologyException

/**
 * @version 1.7
 */
class DirectedEdge(edge: Edge, var isForward: Boolean) : EdgeEnd(edge) {
    var isInResult = false
    var isVisited = false

    /**
     * Each Edge gives rise to a pair of symmetric DirectedEdges, in opposite
     * directions.
     * @return the DirectedEdge for the same Edge but in the opposite direction
     */
    var sym // the symmetric edge
            : DirectedEdge? = null
    var next // the next edge in the edge ring for the polygon containing this edge
            : DirectedEdge? = null
    var nextMin // the next edge in the MinimalEdgeRing that contains this edge
            : DirectedEdge? = null
    var edgeRing // the EdgeRing that this edge is part of
            : EdgeRing? = null
    var minEdgeRing // the MinimalEdgeRing that this edge is part of
            : EdgeRing? = null

    /**
     * The depth of each side (position) of this edge.
     * The 0 element of the array is never used.
     */
    private val depth = intArrayOf(0, -999, -999)

    fun getDepth(position: Int): Int {
        return depth[position]
    }

    fun setDepth(position: Int, depthVal: Int) {
        if (depth[position] != -999) {
//      if (depth[position] != depthVal) {
//        Debug.print(this);
//      }
            if (depth[position] != depthVal) throw TopologyException("assigned depths do not match", coordinate)
            //Assert.isTrue(depth[position] == depthVal, "assigned depths do not match at " + getCoordinate());
        }
        depth[position] = depthVal
    }

    val depthDelta: Int
        get() {
            var depthDelta = edge.depthDelta
            if (!isForward) depthDelta = -depthDelta
            return depthDelta
        }

    /**
     * setVisitedEdge marks both DirectedEdges attached to a given Edge.
     * This is used for edges corresponding to lines, which will only
     * appear oriented in a single direction in the result.
     */
    fun setVisitedEdge(isVisited: Boolean) {
        this.isVisited = isVisited
        sym!!.isVisited = isVisited
    }

    /**
     * This edge is a line edge if
     *
     *  *  at least one of the labels is a line label
     *  *  any labels which are not line labels have all Locations = EXTERIOR
     *
     */
    val isLineEdge: Boolean
        get() {
            val isLine = label!!.isLine(0) || label!!.isLine(1)
            val isExteriorIfArea0 = !label!!.isArea(0) || label!!.allPositionsEqual(0, Location.EXTERIOR)
            val isExteriorIfArea1 = !label!!.isArea(1) || label!!.allPositionsEqual(1, Location.EXTERIOR)
            return isLine && isExteriorIfArea0 && isExteriorIfArea1
        }

    /**
     * This is an interior Area edge if
     *
     *  *  its label is an Area label for both Geometries
     *  *  and for each Geometry both sides are in the interior.
     *
     * @return true if this is an interior Area edge
     */
    val isInteriorAreaEdge: Boolean
        get() {
            var isInteriorAreaEdge = true
            for (i in 0..1) {
                if (!(label!!.isArea(i)
                            && label!!.getLocation(i, Position.LEFT) == Location.INTERIOR && label!!.getLocation(
                        i,
                        Position.RIGHT
                    ) == Location.INTERIOR)
                ) {
                    isInteriorAreaEdge = false
                }
            }
            return isInteriorAreaEdge
        }

    /**
     * Compute the label in the appropriate orientation for this DirEdge
     */
    private fun computeDirectedLabel() {
        label = Label(edge.label!!)
        if (!isForward) label!!.flip()
    }

    /**
     * Set both edge depths.  One depth for a given side is provided.  The other is
     * computed depending on the Location transition and the depthDelta of the edge.
     */
    fun setEdgeDepths(position: Int, depth: Int) {
        // get the depth transition delta from R to L for this directed Edge
        var depthDelta = edge.depthDelta
        if (!isForward) depthDelta = -depthDelta

        // if moving from L to R instead of R to L must change sign of delta
        var directionFactor = 1
        if (position == Position.LEFT) directionFactor = -1
        val oppositePos = Position.opposite(position)
        val delta = depthDelta * directionFactor
        //TESTINGint delta = depthDelta * DirectedEdge.depthFactor(loc, oppositeLoc);
        val oppositeDepth = depth + delta
        setDepth(position, depth)
        setDepth(oppositePos, oppositeDepth)
    }

//    override fun print(out: PrintStream) {
//        super.print(out)
//        out.print(" " + depth[Position.LEFT] + "/" + depth[Position.RIGHT])
//        out.print(" ($depthDelta)")
//        //out.print(" " + this.hashCode());
//        //if (next != null) out.print(" next:" + next.hashCode());
//        if (isInResult) out.print(" inResult")
//    }

//    fun printEdge(out: PrintStream) {
//        print(out)
//        out.print(" ")
//        if (isForward) edge.print(out) else edge.printReverse(out)
//    }

    companion object {
        /**
         * Computes the factor for the change in depth when moving from one location to another.
         * E.g. if crossing from the INTERIOR to the EXTERIOR the depth decreases, so the factor is -1
         */
        fun depthFactor(currLocation: Int, nextLocation: Int): Int {
            if (currLocation == Location.EXTERIOR && nextLocation == Location.INTERIOR) return 1 else if (currLocation == Location.INTERIOR && nextLocation == Location.EXTERIOR) return -1
            return 0
        }
    }

    init {
        if (isForward) {
            init(edge.getCoordinate(0), edge.getCoordinate(1))
        } else {
            val n = edge.getNumPoints() - 1
            init(edge.getCoordinate(n), edge.getCoordinate(n - 1))
        }
        computeDirectedLabel()
    }
}