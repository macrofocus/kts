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

import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Point

/**
 * Extracts Point resultants from an overlay graph
 * created by an Intersection operation
 * between non-Point inputs.
 * Points may be created during intersection
 * if lines or areas touch one another at single points.
 * Intersection is the only overlay operation which can
 * result in Points from non-Point inputs.
 *
 * Overlay operations where one or more inputs
 * are Points are handled via a different code path.
 *
 * @author Martin Davis
 *
 * @see OverlayPoints
 */
internal class IntersectionPointBuilder(
    graph: OverlayGraph,
    geomFact: GeometryFactory
) {
    private val geometryFactory: GeometryFactory
    private val graph: OverlayGraph
    private val points: MutableList<Point> = ArrayList<Point>()

    /**
     * Controls whether lines created by area topology collapses
     * to participate in the result computation.
     * True provides the original JTS semantics.
     */
    private var isAllowCollapseLines: Boolean =
        !OverlayNG.STRICT_MODE_DEFAULT

    init {
        this.graph = graph
        geometryFactory = geomFact
    }

    fun setStrictMode(isStrictMode: Boolean) {
        isAllowCollapseLines = !isStrictMode
    }

    fun getPoints(): List<Point> {
        addResultPoints()
        return points
    }

    private fun addResultPoints() {
        for (nodeEdge in graph.nodeEdges) {
            if (isResultPoint(nodeEdge)) {
                val pt: Point = geometryFactory.createPoint(nodeEdge.coordinate.copy())
                points.add(pt)
            }
        }
    }

    /**
     * Tests if a node is a result point.
     * This is the case if the node is incident on edges from both
     * inputs, and none of the edges are themselves in the result.
     *
     * @param nodeEdge an edge originating at the node
     * @return true if this node is a result point
     */
    private fun isResultPoint(nodeEdge: OverlayEdge): Boolean {
        var isEdgeOfA = false
        var isEdgeOfB = false
        var edge: OverlayEdge = nodeEdge
        do {
            if (edge.isInResult) return false
            val label: OverlayLabel = edge.getLabel()
            isEdgeOfA = isEdgeOfA or isEdgeOf(label, 0)
            isEdgeOfB = isEdgeOfB or isEdgeOf(label, 1)
            edge = edge.oNext() as OverlayEdge
        } while (edge !== nodeEdge)
        return isEdgeOfA && isEdgeOfB
    }

    private fun isEdgeOf(label: OverlayLabel, i: Int): Boolean {
        return if (!isAllowCollapseLines && label.isBoundaryCollapse) false else label.isBoundary(i) || label.isLine(i)
    }
}