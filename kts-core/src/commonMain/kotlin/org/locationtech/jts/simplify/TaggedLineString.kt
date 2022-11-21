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
import kotlin.jvm.JvmOverloads

/**
 * Represents a [LineString] which can be modified to a simplified shape.
 * This class provides an attribute which specifies the minimum allowable length
 * for the modified result.
 *
 * @version 1.7
 */
class TaggedLineString @JvmOverloads constructor(val parent: LineString, val minimumSize: Int = 2) {
    private var segs: Array<TaggedLineSegment?>? = null
    private val resultSegs: MutableList<LineSegment> = ArrayList()

    init {
        init()
    }

    val resultCoordinates: Array<Coordinate>
        get() = extractCoordinates(resultSegs)
    val resultSize: Int
        get() {
            val resultSegsSize: Int = resultSegs.size
            return if (resultSegsSize == 0) 0 else resultSegsSize + 1
        }

    fun getSegment(i: Int): TaggedLineSegment? {
        return segs!![i]
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

    fun addToResult(seg: LineSegment) {
        resultSegs.add(seg)
    }

    fun asLineString(): LineString {
        return parent.factory.createLineString(extractCoordinates(resultSegs))
    }

    fun asLinearRing(): LinearRing {
        return parent.factory.createLinearRing(extractCoordinates(resultSegs))
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