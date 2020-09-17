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
package org.locationtech.jts.noding

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.noding.SegmentPointComparator.compare

/**
 * Represents an intersection point between two [SegmentString]s.
 *
 * @version 1.7
 */
class SegmentNode(
    segString: NodedSegmentString,
    val coord: Coordinate,
    segmentIndex: Int,
    segmentOctant: Int
) : Comparable<Any?> {
    /**
     * Gets the [Coordinate] giving the location of this node.
     *
     * @return the coordinate of the node
     */
    val coordinate // the point of intersection
            : Coordinate = Coordinate(coord)
    val segmentIndex // the index of the containing line segment in the parent edge
            : Int = segmentIndex
    private val segmentOctant: Int
    val isInterior: Boolean
    fun isEndPoint(maxSegmentIndex: Int): Boolean {
        if (segmentIndex == 0 && !isInterior) return true
        return segmentIndex == maxSegmentIndex
    }

    /**
     * @return -1 this SegmentNode is located before the argument location;
     * 0 this SegmentNode is at the argument location;
     * 1 this SegmentNode is located after the argument location
     */
    override fun compareTo(obj: Any?): Int {
        val other = obj as SegmentNode?
        if (segmentIndex < other!!.segmentIndex) return -1
        if (segmentIndex > other.segmentIndex) return 1
        if (coordinate.equals2D(other.coordinate)) return 0

        // an exterior node is the segment start point, so always sorts first
        // this guards against a robustness problem where the octants are not reliable
        if (!isInterior) return -1
        return if (!other.isInterior) 1 else compare(
            segmentOctant,
            coordinate,
            other.coordinate
        )
        //return segment.compareNodePosition(this, other);
    }

//    fun print(out: PrintStream) {
//        out.print(coordinate)
//        out.print(" seg # = $segmentIndex")
//    }

    override fun toString(): String {
        return "$segmentIndex:$coordinate"
    }

    init {
        this.segmentOctant = segmentOctant
        isInterior = !coord.equals2D(segString.getCoordinate(segmentIndex))
    }
}