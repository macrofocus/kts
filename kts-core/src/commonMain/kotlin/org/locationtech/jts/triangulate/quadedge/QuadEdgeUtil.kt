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
package org.locationtech.jts.triangulate.quadedge

/**
 * Utilities for working with [QuadEdge]s.
 *
 * @author mbdavis
 */
object QuadEdgeUtil {
    /**
     * Gets all edges which are incident on the origin of the given edge.
     *
     * @param start
     * the edge to start at
     * @return a List of edges which have their origin at the origin of the given
     * edge
     */
    fun findEdgesIncidentOnOrigin(start: QuadEdge): MutableList<QuadEdge> {
        val incEdge: MutableList<QuadEdge> = ArrayList()
        var qe: QuadEdge = start
        do {
            incEdge.add(qe)
            qe = qe.oNext()!!
        } while (qe !== start)
        return incEdge
    }
}