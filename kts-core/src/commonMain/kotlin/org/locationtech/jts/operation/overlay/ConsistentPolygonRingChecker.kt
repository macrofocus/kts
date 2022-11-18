/*
 * Copyright (c) 2016 Vivid Solutions.
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.operation.overlay

import org.locationtech.jts.geom.Position
import org.locationtech.jts.geom.TopologyException
import org.locationtech.jts.geomgraph.*

/**
 * Tests whether the polygon rings in a [GeometryGraph]
 * are consistent.
 * Used for checking if Topology errors are present after noding.
 *
 * @author Martin Davis
 * @version 1.7
 */
class ConsistentPolygonRingChecker(private val graph: PlanarGraph) {
    fun checkAll() {
        check(OverlayOp.INTERSECTION)
        check(OverlayOp.DIFFERENCE)
        check(OverlayOp.UNION)
        check(OverlayOp.SYMDIFFERENCE)
    }

    /**
     * Tests whether the result geometry is consistent
     *
     * @throws TopologyException if inconsistent topology is found
     */
    fun check(opCode: Int) {
        val nodeit = graph.getNodeIterator()
        while (nodeit.hasNext()) {
            val node = nodeit.next() as Node
            testLinkResultDirectedEdges(node.edges as DirectedEdgeStar?, opCode)
        }
    }

    private fun getPotentialResultAreaEdges(deStar: DirectedEdgeStar?, opCode: Int): MutableList<DirectedEdge> {
//print(System.out);
        val resultAreaEdgeList: MutableList<DirectedEdge> = ArrayList()
        val it = deStar!!.iterator()
        while (it.hasNext()) {
            val de = it.next() as DirectedEdge
            if (isPotentialResultAreaEdge(de, opCode) || isPotentialResultAreaEdge(
                    de.sym,
                    opCode
                )
            ) resultAreaEdgeList.add(de)
        }
        return resultAreaEdgeList
    }

    private fun isPotentialResultAreaEdge(de: DirectedEdge?, opCode: Int): Boolean {
        // mark all dirEdges with the appropriate label
        val label: Label = de!!.label!!
        return if (label.isArea()
            && !de!!.isInteriorAreaEdge
            && OverlayOp.isResultOfOp(
                label.getLocation(0, Position.RIGHT),
                label.getLocation(1, Position.RIGHT),
                opCode
            )
        ) {
            true
            //Debug.print("in result "); Debug.println(de);
        } else false
    }

    private fun testLinkResultDirectedEdges(deStar: DirectedEdgeStar?, opCode: Int) {
        // make sure edges are copied to resultAreaEdges list
        val ringEdges: MutableList<DirectedEdge> = getPotentialResultAreaEdges(deStar, opCode)
        // find first area edge (if any) to start linking at
        var firstOut: DirectedEdge? = null
        var incoming: DirectedEdge? = null
        var state = SCANNING_FOR_INCOMING
        // link edges in CCW order
        for (i in ringEdges.indices) {
            val nextOut = ringEdges.get(i) as DirectedEdge
            val nextIn = nextOut.sym

            // skip de's that we're not interested in
            if (!nextOut.label!!.isArea()) continue

            // record first outgoing edge, in order to link the last incoming edge
            if (firstOut == null
                && isPotentialResultAreaEdge(nextOut, opCode)
            ) firstOut = nextOut
            when (state) {
                SCANNING_FOR_INCOMING -> {
                    if (!isPotentialResultAreaEdge(nextIn, opCode)) continue
                    incoming = nextIn
                    state = LINKING_TO_OUTGOING
                }

                LINKING_TO_OUTGOING -> {
                    if (!isPotentialResultAreaEdge(nextOut, opCode)) continue
                    //incoming.setNext(nextOut);
                    state = SCANNING_FOR_INCOMING
                }
            }
        }
        //Debug.print(this);
        if (state == LINKING_TO_OUTGOING) {
//Debug.print(firstOut == null, this);
            if (firstOut == null) throw TopologyException("no outgoing dirEdge found", deStar!!.coordinate)
        }
    }

    companion object {
        private const val SCANNING_FOR_INCOMING = 1
        private const val LINKING_TO_OUTGOING = 2
    }
}