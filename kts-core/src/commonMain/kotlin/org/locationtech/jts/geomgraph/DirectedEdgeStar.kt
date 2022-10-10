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
import org.locationtech.jts.geom.Quadrant.isNorthern
import org.locationtech.jts.util.Assert.isTrue
import org.locationtech.jts.util.Assert.shouldNeverReachHere

/**
 * A DirectedEdgeStar is an ordered list of **outgoing** DirectedEdges around a node.
 * It supports labelling the edges as well as linking the edges to form both
 * MaximalEdgeRings and MinimalEdgeRings.
 *
 * @version 1.7
 */
class DirectedEdgeStar : EdgeEndStar() {
    /**
     * A list of all outgoing edges in the result, in CCW order
     */
    private var resultAreaEdgeList: MutableList<Any?>? = null
    var label: Label? = null
        private set

    /**
     * Insert a directed edge in the list
     */
    override fun insert(ee: EdgeEnd) {
        val de = ee as DirectedEdge
        insertEdgeEnd(de, de)
    }

    val outgoingDegree: Int
        get() {
            var degree = 0
            val it = iterator()
            while (it.hasNext()) {
                val de = it.next() as DirectedEdge
                if (de.isInResult) degree++
            }
            return degree
        }

    fun getOutgoingDegree(er: EdgeRing): Int {
        var degree = 0
        val it = iterator()
        while (it.hasNext()) {
            val de = it.next() as DirectedEdge
            if (de.edgeRing === er) degree++
        }
        return degree
    }

    // edges are in different hemispheres - make sure we return one that is non-horizontal
    //Assert.isTrue(de0.getDy() != 0, "should never return horizontal edge!");
    val rightmostEdge: DirectedEdge?
        get() {
            val edges = getEdges()
            val size = edges.size
            if (size < 1) return null
            val de0 = edges[0] as DirectedEdge
            if (size == 1) return de0
            val deLast = edges[size - 1] as DirectedEdge
            val quad0 = de0.quadrant
            val quad1 = deLast.quadrant
            if (isNorthern(quad0) && isNorthern(quad1)) return de0 else if (!isNorthern(quad0) && !isNorthern(quad1)) return deLast else {
                // edges are in different hemispheres - make sure we return one that is non-horizontal
                //Assert.isTrue(de0.getDy() != 0, "should never return horizontal edge!");
                val nonHorizontalEdge: DirectedEdge? = null
                if (de0.dy != 0.0) return de0 else if (deLast.dy != 0.0) return deLast
            }
            shouldNeverReachHere("found two horizontal edges incident on node")
            return null
        }

    /**
     * Compute the labelling for all dirEdges in this star, as well
     * as the overall labelling
     */
    override fun computeLabelling(geom: Array<GeometryGraph>) {
//Debug.print(this);
        super.computeLabelling(geom)

        // determine the overall labelling for this DirectedEdgeStar
        // (i.e. for the node it is based at)
        label = Label(Location.NONE)
        val it = iterator()
        while (it.hasNext()) {
            val ee = it.next() as EdgeEnd
            val e = ee.edge
            val eLabel = e.label
            for (i in 0..1) {
                val eLoc = eLabel!!.getLocation(i)
                if (eLoc == Location.INTERIOR || eLoc == Location.BOUNDARY) label!!.setLocation(i, Location.INTERIOR)
            }
        }
        //Debug.print(this);
    }

    /**
     * For each dirEdge in the star,
     * merge the label from the sym dirEdge into the label
     */
    fun mergeSymLabels() {
        val it = iterator()
        while (it.hasNext()) {
            val de = it.next() as DirectedEdge
            val label = de.label
            label!!.merge(de.sym!!.label!!)
        }
    }

    /**
     * Update incomplete dirEdge labels from the labelling for the node
     */
    fun updateLabelling(nodeLabel: Label) {
        val it = iterator()
        while (it.hasNext()) {
            val de = it.next() as DirectedEdge
            val label = de.label
            label!!.setAllLocationsIfNull(0, nodeLabel.getLocation(0))
            label.setAllLocationsIfNull(1, nodeLabel.getLocation(1))
        }
    }

    //print(System.out);
    private val resultAreaEdges: List<*>
        private get() {
//print(System.out);
            if (resultAreaEdgeList != null) return resultAreaEdgeList!!
            resultAreaEdgeList = ArrayList()
            val it = iterator()
            while (it.hasNext()) {
                val de = it.next() as DirectedEdge
                if (de.isInResult || de.sym!!.isInResult) resultAreaEdgeList!!.add(de)
            }
            return resultAreaEdgeList!!
        }
    private val SCANNING_FOR_INCOMING = 1
    private val LINKING_TO_OUTGOING = 2

    /**
     * Traverse the star of DirectedEdges, linking the included edges together.
     * To link two dirEdges, the `next` pointer for an incoming dirEdge
     * is set to the next outgoing edge.
     *
     * DirEdges are only linked if:
     *
     *  * they belong to an area (i.e. they have sides)
     *  * they are marked as being in the result
     *
     *
     * Edges are linked in CCW order (the order they are stored).
     * This means that rings have their face on the Right
     * (in other words,
     * the topological location of the face is given by the RHS label of the DirectedEdge)
     *
     * PRECONDITION: No pair of dirEdges are both marked as being in the result
     */
    fun linkResultDirectedEdges() {
        // make sure edges are copied to resultAreaEdges list
        resultAreaEdges
        // find first area edge (if any) to start linking at
        var firstOut: DirectedEdge? = null
        var incoming: DirectedEdge? = null
        var state = SCANNING_FOR_INCOMING
        // link edges in CCW order
        for (i in resultAreaEdgeList!!.indices) {
            val nextOut = resultAreaEdgeList!![i] as DirectedEdge
            val nextIn = nextOut.sym

            // skip de's that we're not interested in
            if (!nextOut.label!!.isArea) continue

            // record first outgoing edge, in order to link the last incoming edge
            if (firstOut == null && nextOut.isInResult) firstOut = nextOut
            when (state) {
                SCANNING_FOR_INCOMING -> {
                    if (!nextIn!!.isInResult) continue
                    incoming = nextIn
                    state = LINKING_TO_OUTGOING
                }
                LINKING_TO_OUTGOING -> {
                    if (!nextOut.isInResult) continue
                    incoming!!.next = nextOut
                    state = SCANNING_FOR_INCOMING
                }
            }
        }
        //Debug.print(this);
        if (state == LINKING_TO_OUTGOING) {
//Debug.print(firstOut == null, this);
            if (firstOut == null) throw TopologyException("no outgoing dirEdge found", coordinate)
            //Assert.isTrue(firstOut != null, "no outgoing dirEdge found (at " + getCoordinate() );
            isTrue(firstOut.isInResult, "unable to link last incoming dirEdge")
            incoming!!.next = firstOut
        }
    }

    fun linkMinimalDirectedEdges(er: EdgeRing) {
        // find first area edge (if any) to start linking at
        var firstOut: DirectedEdge? = null
        var incoming: DirectedEdge? = null
        var state = SCANNING_FOR_INCOMING
        // link edges in CW order
        for (i in resultAreaEdgeList!!.indices.reversed()) {
            val nextOut = resultAreaEdgeList!![i] as DirectedEdge
            val nextIn = nextOut.sym

            // record first outgoing edge, in order to link the last incoming edge
            if (firstOut == null && nextOut.edgeRing === er) firstOut = nextOut
            when (state) {
                SCANNING_FOR_INCOMING -> {
                    if (nextIn!!.edgeRing !== er) continue
                    incoming = nextIn
                    state = LINKING_TO_OUTGOING
                }
                LINKING_TO_OUTGOING -> {
                    if (nextOut.edgeRing !== er) continue
                    incoming!!.nextMin = nextOut
                    state = SCANNING_FOR_INCOMING
                }
            }
        }
        //print(System.out);
        if (state == LINKING_TO_OUTGOING) {
            isTrue(firstOut != null, "found null for first outgoing dirEdge")
            isTrue(firstOut!!.edgeRing === er, "unable to link last incoming dirEdge")
            incoming!!.nextMin = firstOut
        }
    }

    fun linkAllDirectedEdges() {
        getEdges()
        // find first area edge (if any) to start linking at
        var prevOut: DirectedEdge? = null
        var firstIn: DirectedEdge? = null
        // link edges in CW order
        for (i in edgeList!!.indices.reversed()) {
            val nextOut = edgeList!![i] as DirectedEdge
            val nextIn = nextOut.sym
            if (firstIn == null) firstIn = nextIn
            if (prevOut != null) nextIn!!.next = prevOut
            // record outgoing edge, in order to link the last incoming edge
            prevOut = nextOut
        }
        firstIn!!.next = prevOut
        //Debug.print(this);
    }

    /**
     * Traverse the star of edges, maintaining the current location in the result
     * area at this node (if any).
     * If any L edges are found in the interior of the result, mark them as covered.
     */
    fun findCoveredLineEdges() {
//Debug.print("findCoveredLineEdges");
//Debug.print(this);
        // Since edges are stored in CCW order around the node,
        // as we move around the ring we move from the right to the left side of the edge
        /**
         * Find first DirectedEdge of result area (if any).
         * The interior of the result is on the RHS of the edge,
         * so the start location will be:
         * - INTERIOR if the edge is outgoing
         * - EXTERIOR if the edge is incoming
         */
        var startLoc = Location.NONE
        run {
            val it = iterator()
            while (it.hasNext()) {
                val nextOut = it.next() as DirectedEdge
                val nextIn = nextOut.sym
                if (!nextOut.isLineEdge) {
                    if (nextOut.isInResult) {
                        startLoc = Location.INTERIOR
                        break
                    }
                    if (nextIn!!.isInResult) {
                        startLoc = Location.EXTERIOR
                        break
                    }
                }
            }
        }
        // no A edges found, so can't determine if L edges are covered or not
        if (startLoc == Location.NONE) return
        /**
         * move around ring, keeping track of the current location
         * (Interior or Exterior) for the result area.
         * If L edges are found, mark them as covered if they are in the interior
         */
        var currLoc = startLoc
        val it = iterator()
        while (it.hasNext()) {
            val nextOut = it.next() as DirectedEdge
            val nextIn = nextOut.sym
            if (nextOut.isLineEdge) {
                nextOut.edge.isCovered = currLoc == Location.INTERIOR
                //Debug.println(nextOut);
            } else {  // edge is an Area edge
                if (nextOut.isInResult) currLoc = Location.EXTERIOR
                if (nextIn!!.isInResult) currLoc = Location.INTERIOR
            }
        }
    }

    fun computeDepths(de: DirectedEdge) {
        val edgeIndex = findIndex(de)
        val startDepth = de.getDepth(Position.LEFT)
        val targetLastDepth = de.getDepth(Position.RIGHT)
        // compute the depths from this edge up to the end of the edge array
        val nextDepth = computeDepths(edgeIndex + 1, edgeList!!.size, startDepth)
        // compute the depths for the initial part of the array
        val lastDepth = computeDepths(0, edgeIndex, nextDepth)
        //Debug.print(lastDepth != targetLastDepth, this);
//Debug.print(lastDepth != targetLastDepth, "mismatch: " + lastDepth + " / " + targetLastDepth);
        if (lastDepth != targetLastDepth) throw TopologyException("depth mismatch at " + de.coordinate)
        //Assert.isTrue(lastDepth == targetLastDepth, "depth mismatch at " + de.getCoordinate());
    }

    /**
     * Compute the DirectedEdge depths for a subsequence of the edge array.
     *
     * @return the last depth assigned (from the R side of the last edge visited)
     */
    private fun computeDepths(startIndex: Int, endIndex: Int, startDepth: Int): Int {
        var currDepth = startDepth
        for (i in startIndex until endIndex) {
            val nextDe = edgeList!![i] as DirectedEdge
            nextDe.setEdgeDepths(Position.RIGHT, currDepth)
            currDepth = nextDe.getDepth(Position.LEFT)
        }
        return currDepth
    }
//
//    override fun print(out: PrintStream) {
//        println("DirectedEdgeStar: $coordinate")
//        val it = iterator()
//        while (it.hasNext()) {
//            val de = it.next() as DirectedEdge
//            out.print("out ")
//            de.print(out)
//            out.println()
//            out.print("in ")
//            de.sym!!.print(out)
//            out.println()
//        }
//    }
}