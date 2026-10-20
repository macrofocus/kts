/*
 * Copyright (c) 2016 Vivid Solutions.
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
package org.locationtech.jts.simplify

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.LineSegment
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.LinearRing

/**
 * Represents a [LineString] which can be modified to a simplified shape.
 * This class provides an attribute which specifies the minimum allowable length
 * for the modified result.
 *
 * @version 1.7
 */
class TaggedLineString(val parent: LineString, val minimumSize: Int, val isRing: Boolean) {
    private var segs: Array<TaggedLineSegment?>? = null
    private val resultSegs: MutableList<LineSegment> = ArrayList()

    init {
        init()
    }

    val parentCoordinates: Array<Coordinate>
        get() = parent.coordinates!!
    val resultCoordinates: Array<Coordinate>
        get() = extractCoordinates(resultSegs)

    fun getCoordinate(i: Int): Coordinate {
        return parent.getCoordinateN(i)
    }

    fun size(): Int {
        return parent.numPoints
    }

    val componentPoint: Coordinate
        get() = parentCoordinates[1]

    val resultSize: Int
        get() {
            val resultSegsSize: Int = resultSegs.size
            return if (resultSegsSize == 0) 0 else resultSegsSize + 1
        }

    fun getSegment(i: Int): TaggedLineSegment? {
        return segs!![i]
    }

    /**
     * Gets a segment of the result list.
     * Negative indexes can be used to retrieve from the end of the list.
     * @param i the segment index to retrieve
     * @return the result segment
     */
    fun getResultSegment(i: Int): LineSegment {
        var index = i
        if (i < 0) {
            index = resultSegs.size + i
        }
        return resultSegs[index]
    }

    private fun init() {
        val pts = parent.coordinates!!
        segs = arrayOfNulls(pts.size - 1)
        for (i in 0 until pts.size - 1) {
            val seg: TaggedLineSegment = TaggedLineSegment(
                pts[i], pts[i + 1], parent, i
            )
            segs!![i] = seg
        }
    }

    val segments: Array<TaggedLineSegment?>
        get() = segs!!

    /**
     * Add a simplified segment to the result.
     * This assumes simplified segments are computed in the order
     * they occur in the line.
     *
     * @param seg the result segment to add
     */
    fun addToResult(seg: LineSegment) {
        resultSegs.add(seg)
    }

    fun asLineString(): LineString {
        return parent.factory.createLineString(extractCoordinates(resultSegs))
    }

    fun asLinearRing(): LinearRing {
        return parent.factory.createLinearRing(extractCoordinates(resultSegs))
    }

    fun removeRingEndpoint(): LineSegment {
        val firstSeg = resultSegs[0]
        val lastSeg = resultSegs[resultSegs.size - 1]

        firstSeg.p0 = lastSeg.p0
        resultSegs.removeAt(resultSegs.size - 1)
        return firstSeg
    }

    companion object {
        private fun extractCoordinates(segs: MutableList<LineSegment>): Array<Coordinate> {
            val pts = arrayOfNulls<Coordinate>(segs.size + 1)
            var seg: LineSegment? = null
            for (i in segs.indices) {
                seg = segs.get(i)
                pts[i] = seg!!.p0
            }
            // add last point
            pts[pts.size - 1] = seg!!.p1
            return pts.requireNoNulls()
        }
    }
}