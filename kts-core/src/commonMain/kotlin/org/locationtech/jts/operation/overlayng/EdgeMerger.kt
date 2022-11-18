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

import org.locationtech.jts.util.Assert

/**
 * Performs merging on the noded edges of the input geometries.
 * Merging takes place on edges which are coincident
 * (i.e. have the same coordinate list, modulo direction).
 * The following situations can occur:
 *
 *  * Coincident edges from different input geometries have their labels combined
 *  * Coincident edges from the same area geometry indicate a topology collapse.
 * In this case the topology locations are "summed" to provide a final
 * assignment of side location
 *  * Coincident edges from the same linear geometry can simply be merged
 * using the same ON location
 *
 * The merging attempts to preserve the direction of linear
 * edges if possible (which is the case if there is
 * no other coincident edge, or if all coincident edges have the same direction).
 * This ensures that the overlay output line direction will be as consistent
 * as possible with input lines.
 *
 * The merger also preserves the order of the edges in the input.
 * This means that for polygon-line overlay
 * the result lines will be in the same order as in the input
 * (possibly with multiple result lines for a single input line).
 *
 * @author mdavis
 */
internal object EdgeMerger {
    fun merge(edges: List<Edge>): List<Edge> {
        // use a list to collect the final edges, to preserve order
        val mergedEdges: MutableList<Edge> =
            ArrayList<Edge>()
        val edgeMap: MutableMap<EdgeKey, Edge> =
            HashMap<EdgeKey, Edge>()
        for (edge in edges) {
            val edgeKey: EdgeKey =
                EdgeKey.create(edge)
            val baseEdge: Edge? = edgeMap[edgeKey]
            if (baseEdge == null) {
                // this is the first (and maybe only) edge for this line
                edgeMap[edgeKey] = edge
                //Debug.println("edge added: " + edge);
                //Debug.println(edge.toLineString());
                mergedEdges.add(edge)
            } else {
                // found an existing edge

                // Assert: edges are identical (up to direction)
                // this is a fast (but incomplete) sanity check
                Assert.isTrue(
                    baseEdge.size() == edge.size(),
                    "Merge of edges of different sizes - probable noding error."
                )
                baseEdge.merge(edge)
                //Debug.println("edge merged: " + existing);
                //Debug.println(edge.toLineString());
            }
        }
        return mergedEdges
    }
}