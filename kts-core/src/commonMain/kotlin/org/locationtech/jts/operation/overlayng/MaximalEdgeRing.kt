/*
 * Copyright (c) 2020 Martin Davis
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
import org.locationtech.jts.geom.CoordinateList
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.TopologyException
import org.locationtech.jts.io.WKTWriter
import org.locationtech.jts.util.Assert

internal class MaximalEdgeRing(e: OverlayEdge) {
    private val startEdge: OverlayEdge

    init {
        startEdge = e
        attachEdges(e)
    }

    private fun attachEdges(startEdge: OverlayEdge) {
        var edge: OverlayEdge = startEdge
        do {
            if (edge == null) throw TopologyException("Ring edge is null")
            if (edge.getEdgeRingMax() === this) throw TopologyException(
                "Ring edge visited twice at " + edge.coordinate,
                edge.coordinate
            )
            if (edge.nextResultMax() == null) {
                throw TopologyException("Ring edge missing at", edge.dest())
            }
            edge.setEdgeRingMax(this)
            edge = edge.nextResultMax()!!
        } while (edge !== startEdge)
    }

    fun buildMinimalRings(geometryFactory: GeometryFactory): List<OverlayEdgeRing> {
        linkMinimalRings()
        val minEdgeRings: MutableList<OverlayEdgeRing> =
            ArrayList()
        var e: OverlayEdge = startEdge
        do {
            if (e.edgeRing == null) {
                val minEr: OverlayEdgeRing =
                    OverlayEdgeRing(e, geometryFactory)
                minEdgeRings.add(minEr)
            }
            e = e.nextResultMax()!!
        } while (e !== startEdge)
        return minEdgeRings
    }

    private fun linkMinimalRings() {
        var e: OverlayEdge = startEdge
        do {
            linkMinRingEdgesAtNode(e, this)
            e = e.nextResultMax()!!
        } while (e !== startEdge)
    }

    override fun toString(): String {
        val pts = coordinates
        return WKTWriter.toLineString(pts)
    }

    // add last coordinate
    private val coordinates: Array<Coordinate>
        get() {
            val coords = CoordinateList()
            var edge: OverlayEdge = startEdge
            do {
                coords.add(edge.orig())
                if (edge.nextResultMax() == null) {
                    break
                }
                edge = edge.nextResultMax()!!
            } while (edge !== startEdge)
            // add last coordinate
            coords.add(edge.dest())
            return coords.toCoordinateArray()
        }

    companion object {
        private const val STATE_FIND_INCOMING = 1
        private const val STATE_LINK_OUTGOING = 2

        /**
         * Traverses the star of edges originating at a node
         * and links consecutive result edges together
         * into **maximal** edge rings.
         * To link two edges the `resultNextMax` pointer
         * for an **incoming** result edge
         * is set to the next **outgoing** result edge.
         *
         *
         * Edges are linked when:
         *
         *  * they belong to an area (i.e. they have sides)
         *  * they are marked as being in the result
         *
         *
         *
         * Edges are linked in CCW order
         * (which is the order they are linked in the underlying graph).
         * This means that rings have their face on the Right
         * (in other words,
         * the topological location of the face is given by the RHS label of the DirectedEdge).
         * This produces rings with CW orientation.
         *
         *
         * PRECONDITIONS:
         * - This edge is in the result
         * - This edge is not yet linked
         * - The edge and its sym are NOT both marked as being in the result
         */
        fun linkResultAreaMaxRingAtNode(nodeEdge: OverlayEdge) {
            Assert.isTrue(nodeEdge.isInResultArea, "Attempt to link non-result edge")
            // assertion is only valid if building a polygonal geometry (ie not a coverage)
            //Assert.isTrue(! nodeEdge.symOE().isInResultArea(), "Found both half-edges in result");
            /**
             * Since the node edge is an out-edge,
             * make it the last edge to be linked
             * by starting at the next edge.
             * The node edge cannot be an in-edge as well,
             * but the next one may be the first in-edge.
             */
            val endOut: OverlayEdge = nodeEdge.oNextOE()
            var currOut: OverlayEdge = endOut
            //Debug.println("\n------  Linking node MAX edges");
//Debug.println("BEFORE: " + toString(nodeEdge));
            var state = STATE_FIND_INCOMING
            var currResultIn: OverlayEdge? = null
            do {
                /**
                 * If an edge is linked this node has already been processed
                 * so can skip further processing
                 */
                if (currResultIn != null && currResultIn.isResultMaxLinked) return
                when (state) {
                    STATE_FIND_INCOMING -> {
                        val currIn: OverlayEdge = currOut.symOE()
                        if (!currIn.isInResultArea) break
                        currResultIn = currIn
                        state = STATE_LINK_OUTGOING
                    }

                    STATE_LINK_OUTGOING -> {
                        if (!currOut.isInResultArea) break
                        // link the in edge to the out edge
                        currResultIn!!.setNextResultMax(currOut)
                        state = STATE_FIND_INCOMING
                    }
                }
                currOut = currOut.oNextOE()
            } while (currOut !== endOut)
            //Debug.println("AFTER: " + toString(nodeEdge));
            if (state == STATE_LINK_OUTGOING) {
//Debug.print(firstOut == null, this);
                throw TopologyException("no outgoing edge found", nodeEdge.coordinate)
            }
        }

        /**
         * Links the edges of a [MaximalEdgeRing] around this node
         * into minimal edge rings ([OverlayEdgeRing]s).
         * Minimal ring edges are linked in the opposite orientation (CW)
         * to the maximal ring.
         * This changes self-touching rings into a two or more separate rings,
         * as per the OGC SFS polygon topology semantics.
         * This relinking must be done to each max ring separately,
         * rather than all the node result edges, since there may be
         * more than one max ring incident at the node.
         *
         * @param nodeEdge an edge originating at this node
         * @param maxRing the maximal ring to link
         */
        private fun linkMinRingEdgesAtNode(
            nodeEdge: OverlayEdge?,
            maxRing: MaximalEdgeRing
        ) {
            //Assert.isTrue(nodeEdge.isInResult(), "Attempt to link non-result edge");
            /**
             * The node edge is an out-edge,
             * so it is the first edge linked
             * with the next CCW in-edge
             */
            val endOut: OverlayEdge? = nodeEdge
            var currMaxRingOut: OverlayEdge? = endOut
            var currOut: OverlayEdge = endOut!!.oNextOE()
            //Debug.println("\n------  Linking node MIN ring edges");
//Debug.println("BEFORE: " + toString(nodeEdge));
            do {
                if (isAlreadyLinked(currOut.symOE(), maxRing)) return
                currMaxRingOut = if (currMaxRingOut == null) {
                    selectMaxOutEdge(currOut, maxRing)
                } else {
                    linkMaxInEdge(
                        currOut,
                        currMaxRingOut,
                        maxRing
                    )
                }
                currOut = currOut.oNextOE()
            } while (currOut !== endOut)
            //Debug.println("AFTER: " + toString(nodeEdge));
            if (currMaxRingOut != null) {
                throw TopologyException("Unmatched edge found during min-ring linking", nodeEdge.coordinate)
            }
        }

        /**
         * Tests if an edge of the maximal edge ring is already linked into
         * a minimal [OverlayEdgeRing].  If so, this node has already been processed
         * earlier in the maximal edgering linking scan.
         *
         * @param edge an edge of a maximal edgering
         * @param maxRing the maximal edgering
         * @return true if the edge has already been linked into a minimal edgering.
         */
        private fun isAlreadyLinked(
            edge: OverlayEdge,
            maxRing: MaximalEdgeRing
        ): Boolean {
            return (edge.getEdgeRingMax() === maxRing
                    && edge.isResultLinked)
        }

        private fun selectMaxOutEdge(
            currOut: OverlayEdge,
            maxEdgeRing: MaximalEdgeRing
        ): OverlayEdge? {
            // select if currOut edge is part of this max ring
            return if (currOut.getEdgeRingMax() === maxEdgeRing) currOut else null
            // otherwise skip this edge
        }

        private fun linkMaxInEdge(
            currOut: OverlayEdge,
            currMaxRingOut: OverlayEdge,
            maxEdgeRing: MaximalEdgeRing
        ): OverlayEdge? {
            val currIn: OverlayEdge = currOut.symOE()
            // currIn is not in this max-edgering, so keep looking
            if (currIn.getEdgeRingMax() !== maxEdgeRing) return currMaxRingOut

            //Debug.println("Found result in-edge:  " + currIn);
            currIn.setNextResult(currMaxRingOut)
            //Debug.println("Linked Min Edge:  " + currIn + " -> " + currMaxRingOut);
            // return null to indicate to scan for the next max-ring out-edge
            return null
        }
    }
}