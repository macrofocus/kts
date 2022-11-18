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
package org.locationtech.jts.operation.polygonize

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.planargraph.DirectedEdge
import org.locationtech.jts.planargraph.Node

/**
 * A [DirectedEdge] of a [PolygonizeGraph], which represents
 * an edge of a polygon formed by the graph.
 * May be logically deleted from the graph by setting the `marked` flag.
 *
 * @version 1.7
 */
class PolygonizeDirectedEdge
/**
 * Constructs a directed edge connecting the `from` node to the
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
    private var edgeRing: EdgeRing? = null
    /**
     * Returns the next directed edge in the EdgeRing that this directed edge is a member
     * of.
     */
    /**
     * Sets the next directed edge in the EdgeRing that this directed edge is a member
     * of.
     */
    var next: PolygonizeDirectedEdge? = null
    /**
     * Returns the identifier attached to this directed edge.
     */
    /**
     * Attaches an identifier to this directed edge.
     */
    var label: Long = -1

    /**
     * Returns the ring of directed edges that this directed edge is
     * a member of, or null if the ring has not been set.
     * @see .setRing
     */
    val isInRing: Boolean
        get() = edgeRing != null
    /**
     * Gets the [EdgeRing] this edge is a member of.
     *
     * @return an edge ring
     */
    /**
     * Sets the ring of directed edges that this directed edge is
     * a member of.
     */
    var ring: EdgeRing?
        get() = edgeRing
        set(edgeRing) {
            this.edgeRing = edgeRing
        }
}