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
package org.locationtech.jts.geomgraph

import org.locationtech.jts.geom.Coordinate
import kotlin.jvm.JvmField

/**
 * Represents a point on an
 * edge which intersects with another edge.
 *
 * The intersection may either be a single point, or a line segment
 * (in which case this point is the start of the line segment)
 * The intersection point must be precise.
 *
 * @version 1.7
 */
class EdgeIntersection(coord: Coordinate?, segmentIndex: Int, dist: Double) : Comparable<Any?> {
    var coordinate // the point of intersection
            : Coordinate = Coordinate(coord!!)

    @JvmField
    var segmentIndex // the index of the containing line segment in the parent edge
            : Int = segmentIndex
    var distance // the edge distance of this point along the containing line segment
            : Double

    override fun compareTo(obj: Any?): Int {
        val other = obj as EdgeIntersection?
        return compare(other!!.segmentIndex, other.distance)
    }

    /**
     * @return -1 this EdgeIntersection is located before the argument location
     * @return 0 this EdgeIntersection is at the argument location
     * @return 1 this EdgeIntersection is located after the argument location
     */
    fun compare(segmentIndex: Int, dist: Double): Int {
        if (this.segmentIndex < segmentIndex) return -1
        if (this.segmentIndex > segmentIndex) return 1
        if (distance < dist) return -1
        return if (distance > dist) 1 else 0
    }

    fun isEndPoint(maxSegmentIndex: Int): Boolean {
        if (segmentIndex == 0 && distance == 0.0) return true
        return segmentIndex == maxSegmentIndex
    }

//    fun print(out: PrintStream) {
//        out.print(coordinate)
//        out.print(" seg # = $segmentIndex")
//        out.println(" dist = " + distance)
//    }

    override fun toString(): String {
        return "$coordinate seg # = $segmentIndex dist = $distance"
    }

    init {
        distance = dist
    }
}