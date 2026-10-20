/*
 * Copyright (c) 2022 Martin Davis.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.operation.relateng

import org.locationtech.jts.algorithm.RobustLineIntersector
import org.locationtech.jts.noding.SegmentIntersector
import org.locationtech.jts.noding.SegmentString

/**
 * Tests segments of [RelateSegmentString]s
 * and if they intersect adds the intersection(s)
 * to the [TopologyComputer].
 *
 * @author Martin Davis
 */
class EdgeSegmentIntersector(private val topoComputer: TopologyComputer) : SegmentIntersector {
    private val li = RobustLineIntersector()

    override val isDone: Boolean
        get() = topoComputer.isResultKnown()

    override fun processIntersections(
        e0: SegmentString, segIndex0: Int,
        e1: SegmentString, segIndex1: Int
    ) {
        // don't intersect a segment with itself
        if (e0 === e1 && segIndex0 == segIndex1) return

        val rss0 = e0 as RelateSegmentString
        val rss1 = e1 as RelateSegmentString
        //TODO: move this ordering logic to TopologyBuilder
        if (rss0.isA()) {
            addIntersections(rss0, segIndex0, rss1, segIndex1)
        } else {
            addIntersections(rss1, segIndex1, rss0, segIndex0)
        }
    }

    private fun addIntersections(
        ssA: RelateSegmentString, segIndexA: Int,
        ssB: RelateSegmentString, segIndexB: Int
    ) {
        val a0 = ssA.getCoordinate(segIndexA)
        val a1 = ssA.getCoordinate(segIndexA + 1)
        val b0 = ssB.getCoordinate(segIndexB)
        val b1 = ssB.getCoordinate(segIndexB + 1)

        li.computeIntersection(a0, a1, b0, b1)

        if (!li.hasIntersection())
            return

        for (i in 0 until li.intersectionNum) {
            val intPt = li.getIntersection(i)
            /**
             * Ensure endpoint intersections are added once only, for their canonical segments.
             * Proper intersections lie on a unique segment so do not need to be checked.
             * And it is important that the Containing Segment check not be used,
             * since due to intersection computation roundoff,
             * it is not reliable in that situation.
             */
            if (li.isProper
                || (ssA.isContainingSegment(segIndexA, intPt)
                        && ssB.isContainingSegment(segIndexB, intPt))
            ) {
                val nsa = ssA.createNodeSection(segIndexA, intPt)
                val nsb = ssB.createNodeSection(segIndexB, intPt)
                topoComputer.addIntersection(nsa, nsb)
            }
        }
    }
}
