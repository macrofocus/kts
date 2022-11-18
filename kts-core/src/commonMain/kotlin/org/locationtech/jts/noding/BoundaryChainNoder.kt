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
 * A noder which extracts chains of boundary segments
 * as [SegmentString]s from a polygonal coverage.
 * Boundary segments are those which are not duplicated in the input polygonal coverage.
 * Extracting chains of segments minimize the number of segment strings created,
 * which produces a more efficient topological graph structure.
 *
 * This enables fast overlay of polygonal coverages in [CoverageUnion].
 * Using this noder is faster than [SegmentExtractingNoder]
 * and [BoundarySegmentNoder].
 *
 * No precision reduction is carried out.
 * If that is required, another noder must be used (such as a snap-rounding noder),
 * or the input must be precision-reduced beforehand.
 *
 * @author Martin Davis
 */
class BoundaryChainNoder
/**
 * Creates a new boundary-extracting noder.
 */
    : Noder {
    private var chainList: List<SegmentString>? = null
    override fun computeNodes(segStrings: Collection<SegmentString>) {
        val segSet: HashSet<Segment> = HashSet()
        val boundaryChains = arrayOfNulls<BoundaryChainMap>(segStrings.size)
        addSegments(segStrings, segSet, boundaryChains)
        markBoundarySegments(segSet)
        chainList = extractChains(boundaryChains)
    }

    override val nodedSubstrings: Collection<SegmentString>
        get() = chainList!!

    private class BoundaryChainMap(ss: SegmentString) {
        private val segString: SegmentString
        private val isBoundary: BooleanArray

        init {
            segString = ss
            isBoundary = BooleanArray(ss.size() - 1)
        }

        fun setBoundarySegment(index: Int) {
            isBoundary[index] = true
        }

        fun createChains(chainList: MutableList<SegmentString>) {
            var endIndex = 0
            while (true) {
                val startIndex = findChainStart(endIndex)
                if (startIndex >= segString.size() - 1) break
                endIndex = findChainEnd(startIndex)
                val ss: SegmentString = createChain(segString, startIndex, endIndex)
                chainList.add(ss)
            }
        }

        private fun findChainStart(index: Int): Int {
            var index = index
            while (index < isBoundary.size && !isBoundary[index]) {
                index++
            }
            return index
        }

        private fun findChainEnd(index: Int): Int {
            var index = index
            index++
            while (index < isBoundary.size && isBoundary[index]) {
                index++
            }
            return index
        }

        companion object {
            private fun createChain(
                segString: SegmentString,
                startIndex: Int,
                endIndex: Int
            ): SegmentString {
                val pts = arrayOfNulls<Coordinate>(endIndex - startIndex + 1)
                var ipts = 0
                for (i in startIndex until endIndex + 1) {
                    pts[ipts++] = segString.getCoordinate(i).copy()
                }
                return BasicSegmentString(pts.requireNoNulls(), segString.data)
            }
        }
    }

    private class Segment(
        p0: Coordinate?, p1: Coordinate?,
        private val segMap: BoundaryChainMap, private val index: Int
    ) : LineSegment(
        p0!!, p1!!
    ) {
        init {
            normalize()
        }

        fun markBoundary() {
            segMap.setBoundarySegment(index)
        }
    }

    companion object {
        private fun addSegments(
            segStrings: Collection<SegmentString>, segSet: HashSet<Segment>,
            boundaryChains: Array<BoundaryChainMap?>
        ) {
            var i = 0
            for (ss in segStrings) {
                val chainMap = BoundaryChainMap(ss)
                boundaryChains[i++] = chainMap
                addSegments(ss, chainMap, segSet)
            }
        }

        private fun addSegments(
            segString: SegmentString,
            chainMap: BoundaryChainMap,
            segSet: HashSet<Segment>
        ) {
            for (i in 0 until segString.size() - 1) {
                val p0: Coordinate = segString.getCoordinate(i)
                val p1: Coordinate = segString.getCoordinate(i + 1)
                val seg = Segment(p0, p1, chainMap, i)
                if (segSet.contains(seg)) {
                    segSet.remove(seg)
                } else {
                    segSet.add(seg)
                }
            }
        }

        private fun markBoundarySegments(segSet: HashSet<Segment>) {
            for (seg in segSet) {
                seg.markBoundary()
            }
        }

        private fun extractChains(boundaryChains: Array<BoundaryChainMap?>): List<SegmentString> {
            val chainList: MutableList<SegmentString> =
                ArrayList()
            for (chainMap in boundaryChains) {
                chainMap!!.createChains(chainList)
            }
            return chainList
        }
    }
}