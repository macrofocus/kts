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
package org.locationtech.jts.noding

import org.locationtech.jts.geom.Coordinate

/**
 * A noder which extracts all line segments
 * as [SegmentString]s.
 * This enables fast overlay of geometries which are known to be already fully noded.
 * In particular, it provides fast union of polygonal and linear coverages.
 * Unioning a noded set of lines is an effective way
 * to perform line merging and line dissolving.
 *
 * No precision reduction is carried out.
 * If that is required, another noder must be used (such as a snap-rounding noder),
 * or the input must be precision-reduced beforehand.
 *
 * @author Martin Davis
 */
class SegmentExtractingNoder
/**
 * Creates a new segment-extracting noder.
 */
    : Noder {
    private var segList: List<SegmentString>? = null
    override fun computeNodes(segStrings: Collection<SegmentString>) {
        segList = extractSegments(segStrings)
    }

    override val nodedSubstrings: Collection<SegmentString>
        get() = segList!!

    companion object {
        private fun extractSegments(segStrings: Collection<SegmentString>): List<SegmentString> {
            val segList: MutableList<SegmentString> =
                ArrayList()
            for (ss in segStrings) {
                extractSegments(ss, segList)
            }
            return segList
        }

        private fun extractSegments(
            ss: SegmentString,
            segList: MutableList<SegmentString>
        ) {
            for (i in 0 until ss.size() - 1) {
                val p0: Coordinate = ss.getCoordinate(i)
                val p1: Coordinate = ss.getCoordinate(i + 1)
                val seg: SegmentString =
                    BasicSegmentString(arrayOf(p0, p1), ss.data)
                segList.add(seg)
            }
        }
    }
}