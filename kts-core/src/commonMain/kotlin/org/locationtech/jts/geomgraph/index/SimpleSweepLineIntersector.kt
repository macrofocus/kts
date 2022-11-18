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
package org.locationtech.jts.geomgraph.index

/**
 * @version 1.7
 */
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geomgraph.Edge

/**
 * Finds all intersections in one or two sets of edges,
 * using a simple x-axis sweepline algorithm.
 * While still O(n^2) in the worst case, this algorithm
 * drastically improves the average-case time.
 *
 * @version 1.7
 */
class SimpleSweepLineIntersector : EdgeSetIntersector() {
    var events: MutableList<SweepLineEvent> = ArrayList()

    // statistics information
    var nOverlaps = 0
    override fun computeIntersections(
        edges: MutableList<Edge>,
        si: SegmentIntersector,
        testAllSegments: Boolean
    ) {
        if (testAllSegments) add(edges, null) else add(edges)
        computeIntersections(si)
    }

    override fun computeIntersections(
        edges0: MutableList<Edge>,
        edges1: MutableList<Edge>,
        si: SegmentIntersector
    ) {
        add(edges0, edges0)
        add(edges1, edges1)
        computeIntersections(si)
    }

    private fun add(edges: MutableList<Edge>) {
        val i: Iterator<*> = edges.iterator()
        while (i.hasNext()) {
            val edge: Edge = i.next() as Edge
            // edge is its own group
            add(edge, edge)
        }
    }

    private fun add(edges: MutableList<Edge>, edgeSet: Any?) {
        val i: Iterator<*> = edges.iterator()
        while (i.hasNext()) {
            val edge: Edge = i.next() as Edge
            add(edge, edgeSet)
        }
    }

    private fun add(edge: Edge, edgeSet: Any?) {
        val pts: Array<Coordinate> = edge.getCoordinates()
        for (i in 0 until pts.size - 1) {
            val ss: SweepLineSegment =
                SweepLineSegment(edge, i)
            val insertEvent: SweepLineEvent =
                SweepLineEvent(edgeSet, ss.minX, null)
            events.add(insertEvent)
            events.add(SweepLineEvent(ss.maxX, insertEvent))
        }
    }

    /**
     * Because DELETE events have a link to their corresponding INSERT event,
     * it is possible to compute exactly the range of events which must be
     * compared to a given INSERT event object.
     */
    private fun prepareEvents() {
        events.sort()
        // set DELETE event indexes
        for (i in events.indices) {
            val ev: SweepLineEvent = events[i]
            if (ev.isDelete) {
                ev.insertEvent!!.deleteEventIndex = i
            }
        }
    }

    private fun computeIntersections(si: SegmentIntersector) {
        nOverlaps = 0
        prepareEvents()
        for (i in events.indices) {
            val ev: SweepLineEvent = events[i]
            if (ev.isInsert) {
                processOverlaps(i, ev.deleteEventIndex, ev, si)
            }
        }
    }

    private fun processOverlaps(
        start: Int,
        end: Int,
        ev0: SweepLineEvent,
        si: SegmentIntersector
    ) {
        val ss0: SweepLineSegment =
            ev0.`object` as SweepLineSegment
        /**
         * Since we might need to test for self-intersections,
         * include current INSERT event object in list of event objects to test.
         * Last index can be skipped, because it must be a Delete event.
         */
        for (i in start until end) {
            val ev1: SweepLineEvent = events[i]
            if (ev1.isInsert) {
                val ss1: SweepLineSegment =
                    ev1.`object` as SweepLineSegment
                // don't compare edges in same group, if labels are present
                if (!ev0.isSameLabel(ev1)) {
                    ss0.computeIntersections(ss1, si)
                    nOverlaps++
                }
            }
        }
    }
}