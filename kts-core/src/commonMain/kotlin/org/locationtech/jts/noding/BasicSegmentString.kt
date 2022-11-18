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
package org.locationtech.jts.noding

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.impl.CoordinateArraySequence
import org.locationtech.jts.io.WKTWriter
import org.locationtech.jts.noding.Octant.octant

/**
 * Represents a read-only list of contiguous line segments.
 * This can be used for detection of intersections or nodes.
 * [SegmentString]s can carry a context object, which is useful
 * for preserving topological or parentage information.
 *
 * If adding nodes is required use [NodedSegmentString].
 *
 * @version 1.7
 * @see NodedSegmentString
 */
class BasicSegmentString
/**
 * Creates a new segment string from a list of vertices.
 *
 * @param pts the vertices of the segment string
 * @param data the user-defined data of this segment string (may be null)
 */(
    override val coordinates: Array<Coordinate>,
    /**
     * Sets the user-defined data for this segment string.
     *
     * @param data an Object containing user-defined data
     */
    override var data: Any?
) : SegmentString {
    /**
     * Gets the user-defined data for this segment string.
     *
     * @return the user-defined data
     */

    override fun size(): Int {
        return coordinates.size
    }

    override fun getCoordinate(i: Int): Coordinate {
        return coordinates[i]
    }

    override val isClosed: Boolean
        get() = coordinates[0]!! == coordinates[coordinates.size - 1]

    /**
     * Gets the octant of the segment starting at vertex `index`.
     *
     * @param index the index of the vertex starting the segment.  Must not be
     * the last index in the vertex list
     * @return the octant of the segment at the vertex
     */
    fun getSegmentOctant(index: Int): Int {
        return if (index == coordinates.size - 1) -1 else octant(
            getCoordinate(index)!!,
            getCoordinate(index + 1)!!
        )
    }

    override fun toString(): String {
        return WKTWriter.toLineString(
            CoordinateArraySequence(
                coordinates
            )
        )
    }
}