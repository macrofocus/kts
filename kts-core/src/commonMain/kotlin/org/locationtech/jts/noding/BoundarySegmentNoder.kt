/*
 * Copyright (c) 2022 Martin Davis.
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
package org.locationtech.jts.noding

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.LineSegment

/**
 * A noder which extracts boundary line segments
 * as [SegmentString]s.
 * Boundary segments are those which are not duplicated in the input.
 * It is appropriate for use with valid polygonal coverages.
 *
 * No precision reduction is carried out.
 * If that is required, another noder must be used (such as a snap-rounding noder),
 * or the input must be precision-reduced beforehand.
 *
 * @author Martin Davis
 */
class BoundarySegmentNoder
/**
 * Creates a new segment-dissolving noder.
 */
    : Noder {
    private var segList: List<SegmentString>? = null
    override fun computeNodes(segStrings: Collection<SegmentString>) {
        val segSet: HashSet<Segment> = HashSet<Segment>()
        addSegments(segStrings, segSet)
        segList = extractSegments(segSet)
    }

    override val nodedSubstrings: Collection<SegmentString>
        get() = segList!!

    internal class Segment(
        p0: Coordinate?, p1: Coordinate?,
        segStr: SegmentString, index: Int
    ) : LineSegment(
        p0!!, p1!!
    ) {
        private val segStr: SegmentString
        val index: Int

        init {
            this.segStr = segStr
            this.index = index
            normalize()
        }

        val segmentString: SegmentString
            get() = segStr
    }

    companion object {
        private fun addSegments(
            segStrings: Collection<SegmentString>,
            segSet: HashSet<Segment>
        ) {
            for (ss in segStrings) {
                addSegments(ss, segSet)
            }
        }

        private fun addSegments(
            segString: SegmentString,
            segSet: HashSet<Segment>
        ) {
            for (i in 0 until segString.size() - 1) {
                val p0: Coordinate = segString.getCoordinate(i)
                val p1: Coordinate = segString.getCoordinate(i + 1)
                val seg = Segment(p0, p1, segString, i)
                if (segSet.contains(seg)) {
                    segSet.remove(seg)
                } else {
                    segSet.add(seg)
                }
            }
        }

        private fun extractSegments(segSet: HashSet<Segment>): List<SegmentString> {
            val segList: MutableList<SegmentString> =
                ArrayList<SegmentString>()
            for (seg in segSet) {
                val ss: SegmentString = seg.segmentString
                val i = seg.index
                val p0: Coordinate = ss.getCoordinate(i)
                val p1: Coordinate = ss.getCoordinate(i + 1)
                val segStr: SegmentString =
                    BasicSegmentString(arrayOf<Coordinate>(p0, p1), ss.data)
                segList.add(segStr)
            }
            return segList
        }
    }
}