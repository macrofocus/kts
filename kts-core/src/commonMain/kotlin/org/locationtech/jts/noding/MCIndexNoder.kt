/*
 * Copyright (c) 2016 Vivid Solutions, and others.
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

import org.locationtech.jts.index.SpatialIndex
import org.locationtech.jts.index.chain.MonotoneChain
import org.locationtech.jts.index.chain.MonotoneChainBuilder.getChains
import org.locationtech.jts.index.chain.MonotoneChainOverlapAction
import org.locationtech.jts.index.strtree.STRtree

/**
 * Nodes a set of [SegmentString]s using a index based
 * on [MonotoneChain]s and a [SpatialIndex].
 * The [SpatialIndex] used should be something that supports
 * envelope (range) queries efficiently (such as a `Quadtree`}
 * or [STRtree] (which is the default index provided).
 *
 * The noder supports using an overlap tolerance distance .
 * This allows determining segment intersection using a buffer for uses
 * involving snapping with a distance tolerance.
 *
 * @version 1.7
 */
class MCIndexNoder : SinglePassNoder {
    private val monoChains: MutableList<Any?> = ArrayList()
    val index: SpatialIndex = STRtree()
    private var idCounter = 0
    private var nodedSegStrings: Collection<*>? = null

    // statistics
    private var nOverlaps = 0
    private var overlapTolerance = 0.0

    constructor()
    constructor(si: SegmentIntersector?) : super(si)

    /**
     * Creates a new noder with a given [SegmentIntersector]
     * and an overlap tolerance distance to expand intersection tests with.
     *
     * @param si the segment intersector
     * @param overlapTolerance the expansion distance for overlap tests
     */
    constructor(si: SegmentIntersector?, overlapTolerance: Double) : super(si) {
        this.overlapTolerance = overlapTolerance
    }

    val monotoneChains: MutableList<*>
        get() = monoChains
    override val nodedSubstrings: Collection<SegmentString>
        get() = NodedSegmentString.getNodedSubstrings(nodedSegStrings)

    override fun computeNodes(inputSegStrings: Collection<SegmentString>) {
        nodedSegStrings = inputSegStrings
        val i = inputSegStrings!!.iterator()
        while (i.hasNext()) {
            add(i.next() as SegmentString)
        }
        intersectChains()
        //System.out.println("MCIndexNoder: # chain overlaps = " + nOverlaps);
    }

    private fun intersectChains() {
        val overlapAction: MonotoneChainOverlapAction = SegmentOverlapAction(segInt)
        val i: Iterator<*> = monoChains.iterator()
        while (i.hasNext()) {
            val queryChain = i.next() as MonotoneChain
            val queryEnv = queryChain.getEnvelope(overlapTolerance)
            val overlapChains = index.query(queryEnv)
            val j: Iterator<*> = overlapChains!!.iterator()
            while (j.hasNext()) {
                val testChain = j.next() as MonotoneChain
                /**
                 * following test makes sure we only compare each pair of chains once
                 * and that we don't compare a chain to itself
                 */
                if (testChain.id > queryChain.id) {
                    queryChain.computeOverlaps(testChain, overlapTolerance, overlapAction)
                    nOverlaps++
                }
                // short-circuit if possible
                if (segInt!!.isDone) return
            }
        }
    }

    private fun add(segStr: SegmentString) {
        val segChains = getChains(segStr.coordinates, segStr)
        val i: Iterator<*> = segChains.iterator()
        while (i.hasNext()) {
            val mc = i.next() as MonotoneChain
            mc.id = idCounter++
            //mc.setOverlapDistance(overlapDistance);
            index.insert(mc.getEnvelope(overlapTolerance), mc)
            monoChains.add(mc)
        }
    }

    class SegmentOverlapAction(private val si: SegmentIntersector?) : MonotoneChainOverlapAction() {
        override fun overlap(mc1: MonotoneChain, start1: Int, mc2: MonotoneChain, start2: Int) {
            val ss1: SegmentString? =
                mc1.context as SegmentString?
            val ss2: SegmentString? =
                mc2.context as SegmentString?
            si!!.processIntersections(ss1!!, start1, ss2!!, start2)
        }
    }
}