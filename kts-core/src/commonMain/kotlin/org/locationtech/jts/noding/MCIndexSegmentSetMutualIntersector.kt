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

import org.locationtech.jts.index.SpatialIndex
import org.locationtech.jts.index.chain.MonotoneChain
import org.locationtech.jts.index.chain.MonotoneChainBuilder.getChains
import org.locationtech.jts.index.chain.MonotoneChainOverlapAction
import org.locationtech.jts.index.strtree.STRtree

/**
 * Intersects two sets of [SegmentString]s using a index based
 * on [MonotoneChain]s and a [SpatialIndex].
 *
 * Thread-safe and immutable.
 *
 * @version 1.7
 */
class MCIndexSegmentSetMutualIntersector(baseSegStrings: Collection<*>) : SegmentSetMutualIntersector {
    /**
     * The [SpatialIndex] used should be something that supports
     * envelope (range) queries efficiently (such as a
     * [org.locationtech.jts.index.quadtree.Quadtree]
     * or [STRtree].
     */
    private val index = STRtree()

    /**
     * Constructs a new intersector for a given set of [SegmentString]s.
     *
     * @param baseSegStrings the base segment strings to intersect
     */
    init {
        initBaseSegments(baseSegStrings)
    }

    /**
     * Gets the index constructed over the base segment strings.
     *
     * NOTE: To retain thread-safety, treat returned value as immutable!
     *
     * @return the constructed index
     */
    fun getIndex(): SpatialIndex {
        return index
    }

    private fun initBaseSegments(segStrings: Collection<*>) {
        val i = segStrings.iterator()
        while (i.hasNext()) {
            addToIndex(i.next() as SegmentString)
        }
        // build index to ensure thread-safety
        index.build()
    }

    private fun addToIndex(segStr: SegmentString) {
        val segChains: MutableList<MonotoneChain> = getChains(segStr.coordinates, segStr)
        val i: Iterator<*> = segChains.iterator()
        while (i.hasNext()) {
            val mc = i.next() as MonotoneChain
            index.insert(mc.envelope, mc)
        }
    }

    /**
     * Calls [SegmentIntersector.processIntersections]
     * for all *candidate* intersections between
     * the given collection of SegmentStrings and the set of indexed segments.
     *
     * @param segStrings set of segments to intersect
     * @param segInt segment intersector to use
     */
    override fun process(segStrings: Collection<*>?, segInt: SegmentIntersector?) {
        val monoChains: MutableList<MonotoneChain> = ArrayList()
        val i = segStrings!!.iterator()
        while (i.hasNext()) {
            addToMonoChains(i.next() as SegmentString, monoChains)
        }
        intersectChains(monoChains, segInt!!)
        //    System.out.println("MCIndexBichromaticIntersector: # chain overlaps = " + nOverlaps);
//    System.out.println("MCIndexBichromaticIntersector: # oct chain overlaps = " + nOctOverlaps);
    }

    private fun addToMonoChains(segStr: SegmentString, monoChains: MutableList<MonotoneChain>) {
        val segChains: MutableList<MonotoneChain> = getChains(segStr.coordinates, segStr)
        val i: Iterator<*> = segChains.iterator()
        while (i.hasNext()) {
            val mc = i.next() as MonotoneChain
            monoChains.add(mc)
        }
    }

    private fun intersectChains(monoChains: MutableList<MonotoneChain>, segInt: SegmentIntersector) {
        val overlapAction: MonotoneChainOverlapAction = SegmentOverlapAction(segInt)
        val i: Iterator<*> = monoChains.iterator()
        while (i.hasNext()) {
            val queryChain = i.next() as MonotoneChain
            val overlapChains: MutableList<MonotoneChain>? = index.query(queryChain.envelope) as MutableList<MonotoneChain>?
            val j: Iterator<*> = overlapChains!!.iterator()
            while (j.hasNext()) {
                val testChain = j.next() as MonotoneChain
                queryChain.computeOverlaps(testChain, overlapAction)
                if (segInt.isDone) return
            }
        }
    }

    class SegmentOverlapAction(private val si: SegmentIntersector? = null) : MonotoneChainOverlapAction() {
        override fun overlap(mc1: MonotoneChain, start1: Int, mc2: MonotoneChain, start2: Int) {
            val ss1: SegmentString? =
                mc1.context as SegmentString?
            val ss2: SegmentString? =
                mc2.context as SegmentString?
            si!!.processIntersections(ss1!!, start1, ss2!!, start2)
        }
    }
}