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
import org.locationtech.jts.geom.CoordinateArrays.reverse
import org.locationtech.jts.geom.CoordinateList
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LineString

/**
 * A sequence of [LineMergeDirectedEdge]s forming one of the lines that will
 * be output by the line-merging process.
 *
 * @version 1.7
 */
class EdgeString
/**
 * Constructs an EdgeString with the given factory used to convert this EdgeString
 * to a LineString
 */(private val factory: GeometryFactory) {
    private val directedEdges: MutableList<LineMergeDirectedEdge> = ArrayList()
    private var coordinates: Array<Coordinate>? = null
        private get() {
            if (field == null) {
                var forwardDirectedEdges = 0
                var reverseDirectedEdges = 0
                val coordinateList = CoordinateList()
                val i: Iterator<*> = directedEdges.iterator()
                while (i.hasNext()) {
                    val directedEdge: LineMergeDirectedEdge =
                        i.next() as LineMergeDirectedEdge
                    if (directedEdge.edgeDirection) {
                        forwardDirectedEdges++
                    } else {
                        reverseDirectedEdges++
                    }
                    coordinateList.add(
                        (directedEdge.edge as LineMergeEdge).line
                            .coordinates, false,
                        directedEdge.edgeDirection
                    )
                }
                field = coordinateList.toCoordinateArray()
                if (reverseDirectedEdges > forwardDirectedEdges) {
                    reverse(field!!)
                }
            }
            return field
        }

    /**
     * Adds a directed edge which is known to form part of this line.
     */
    fun add(directedEdge: LineMergeDirectedEdge) {
        directedEdges.add(directedEdge)
    }

    /**
     * Converts this EdgeString into a LineString.
     */
    fun toLineString(): LineString {
        return factory.createLineString(coordinates)
    }
}