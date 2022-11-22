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
package org.locationtech.jts.operation.linemerge

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.planargraph.DirectedEdge
import org.locationtech.jts.planargraph.Node
import org.locationtech.jts.util.Assert

/**
 * A [org.locationtech.jts.planargraph.DirectedEdge] of a
 * [LineMergeGraph].
 *
 * @version 1.7
 */
class LineMergeDirectedEdge
/**
 * Constructs a LineMergeDirectedEdge connecting the `from` node to the
 * `to` node.
 *
 * @param directionPt
 * specifies this DirectedEdge's direction (given by an imaginary
 * line from the `from` node to `directionPt`)
 * @param edgeDirection
 * whether this DirectedEdge's direction is the same as or
 * opposite to that of the parent Edge (if any)
 */
    (
    from: Node, to: Node, directionPt: Coordinate,
    edgeDirection: Boolean
) : DirectedEdge(from, to, directionPt, edgeDirection) {
    /**
     * Returns the directed edge that starts at this directed edge's end point, or null
     * if there are zero or multiple directed edges starting there.
     * @return the directed edge
     */
    val next: LineMergeDirectedEdge?
        get() {
            if (toNode.degree != 2) {
                return null
            }
            if (toNode.outEdges.edges[0] === sym) {
                return toNode.outEdges.edges[1] as LineMergeDirectedEdge
            }
            Assert.isTrue(toNode.outEdges.edges[1] === sym)
            return toNode.outEdges.edges[0] as LineMergeDirectedEdge
        }
}