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
import org.locationtech.jts.geom.CoordinateArrays.removeRepeatedPoints
import org.locationtech.jts.legacy.Math.round
import kotlin.jvm.JvmOverloads

/**
 * Wraps a [Noder] and transforms its input
 * into the integer domain.
 * This is intended for use with Snap-Rounding noders,
 * which typically are only intended to work in the integer domain.
 * Offsets can be provided to increase the number of digits of available precision.
 *
 * Clients should be aware that rescaling can involve loss of precision,
 * which can cause zero-length line segments to be created.
 * These in turn can cause problems when used to build a planar graph.
 * This situation should be checked for and collapsed segments removed if necessary.
 *
 * @version 1.7
 */
class ScaledNoder @JvmOverloads constructor(
    noder: Noder,
    scaleFactor: Double,
    offsetX: Double = 0.0,
    offsetY: Double = 0.0
) : Noder {
    private val noder: Noder
    private val scaleFactor: Double
    private val offsetX = 0.0
    private val offsetY = 0.0
    private var isScaled = false

    init {
        this.noder = noder
        this.scaleFactor = scaleFactor
        // no need to scale if input precision is already integral
        isScaled = !isIntegerPrecision
    }

    val isIntegerPrecision: Boolean
        get() = scaleFactor == 1.0
    override val nodedSubstrings: Collection<SegmentString>
        get() {
            val splitSS: Collection<SegmentString> = noder.nodedSubstrings!!
            if (isScaled) rescale(splitSS)
            return splitSS
        }

    override fun computeNodes(inputSegStrings: Collection<SegmentString>) {
        var intSegStrings = inputSegStrings
        if (isScaled) intSegStrings = scale(inputSegStrings)
        noder.computeNodes(intSegStrings)
    }

    private fun scale(segStrings: Collection<SegmentString>): Collection<SegmentString> {
        val nodedSegmentStrings: MutableList<SegmentString> = ArrayList(segStrings.size)
        val i = segStrings.iterator()
        while (i.hasNext()) {
            val ss: SegmentString = i.next() as SegmentString
            nodedSegmentStrings.add(
                NodedSegmentString(
                    scale(ss.coordinates),
                    ss.data
                )
            )
        }
        return nodedSegmentStrings
    }

    private fun scale(pts: Array<Coordinate>): Array<Coordinate> {
        val roundPts =
            arrayOfNulls<Coordinate>(pts.size)
        for (i in pts.indices) {
            roundPts[i] = Coordinate(
                round((pts[i]!!.x - offsetX) * scaleFactor).toDouble(),
                round((pts[i]!!.y - offsetY) * scaleFactor).toDouble(),
                pts[i]!!.z
            )
        }
        return removeRepeatedPoints(roundPts.requireNoNulls())
    }

    //private double scale(double val) { return (double) Math.round(val * scaleFactor); }
    private fun rescale(segStrings: Collection<SegmentString>) {
        val i = segStrings.iterator()
        while (i.hasNext()) {
            val ss: SegmentString = i.next() as SegmentString
            rescale(ss.coordinates)
        }
    }

    private fun rescale(pts: Array<Coordinate>) {
        for (i in pts.indices) {
            pts[i]!!.x = pts[i]!!.x / scaleFactor + offsetX
            pts[i]!!.y = pts[i]!!.y / scaleFactor + offsetY
        }
        /*
    if (pts.length == 2 && pts[0].equals2D(pts[1])) {
      System.out.println(pts);
    }
    */
    } //private double rescale(double val) { return val / scaleFactor; }
}