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
package org.locationtech.jts.index.strtree

import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.index.strtree.EnvelopeDistance.maximumDistance
import org.locationtech.jts.legacy.queue.PriorityQueue

/**
 * A pair of [Boundable]s, whose leaf items
 * support a distance metric between them.
 * Used to compute the distance between the members,
 * and to expand a member relative to the other
 * in order to produce new branches of the
 * Branch-and-Bound evaluation tree.
 * Provides an ordering based on the distance between the members,
 * which allows building a priority queue by minimum distance.
 *
 * @author Martin Davis
 */
class BoundablePair(
    private val boundable1: Boundable,
    private val boundable2: Boundable,
    private val itemDistance: ItemDistance
) : Comparable<Any?> {
    /**
     * Gets the minimum possible distance between the Boundables in
     * this pair.
     * If the members are both items, this will be the
     * exact distance between them.
     * Otherwise, this distance will be a lower bound on
     * the distances between the items in the members.
     *
     * @return the exact or lower bound distance for this pair
     */
    val distance: Double

    //private double maxDistance = -1.0;
    init {
        distance = distance()
    }

    /**
     * Gets one of the member [Boundable]s in the pair
     * (indexed by [0, 1]).
     *
     * @param i the index of the member to return (0 or 1)
     * @return the chosen member
     */
    fun getBoundable(i: Int): Boundable {
        return if (i == 0) boundable1 else boundable2
    }

    /**
     * Computes the maximum distance between any
     * two items in the pair of nodes.
     *
     * @return the maximum distance between items in the pair
     */
    fun maximumDistance(): Double {
        return maximumDistance(
            (boundable1.bounds as Envelope?)!!,
            (boundable2.bounds as Envelope?)!!
        )
    }

    /**
     * Computes the distance between the [Boundable]s in this pair.
     * The boundables are either composites or leaves.
     * If either is composite, the distance is computed as the minimum distance
     * between the bounds.
     * If both are leaves, the distance is computed by [.itemDistance].
     *
     * @return
     */
    private fun distance(): Double {
        // if items, compute exact distance
        return if (isLeaves) {
            itemDistance.distance(
                boundable1 as ItemBoundable,
                boundable2 as ItemBoundable
            )
        } else (boundable1.bounds as Envelope?)!!.distance(
            (boundable2.bounds as Envelope?)!!
        )
        // otherwise compute distance between bounds of boundables
    }

    /**
     * Compares two pairs based on their minimum distances
     */
    override operator fun compareTo(o: Any?): Int {
        val nd = o as BoundablePair
        if (distance < nd.distance) return -1
        return if (distance > nd.distance) 1 else 0
    }

    /**
     * Tests if both elements of the pair are leaf nodes
     *
     * @return true if both pair elements are leaf nodes
     */
    val isLeaves: Boolean
        get() = !(isComposite(boundable1) || isComposite(
            boundable2
        ))

    /**
     * For a pair which is not a leaf
     * (i.e. has at least one composite boundable)
     * computes a list of new pairs
     * from the expansion of the larger boundable
     * with distance less than minDistance
     * and adds them to a priority queue.
     *
     * Note that expanded pairs may contain
     * the same item/node on both sides.
     * This must be allowed to support distance
     * functions which have non-zero distances
     * between the item and itself (non-zero reflexive distance).
     *
     * @param priQ the priority queue to add the new pairs to
     * @param minDistance the limit on the distance between added pairs
     */
    fun expandToQueue(priQ: PriorityQueue<BoundablePair>, minDistance: Double) {
        val isComp1 = isComposite(
            boundable1
        )
        val isComp2 = isComposite(
            boundable2
        )
        /**
         * HEURISTIC: If both boundable are composite,
         * choose the one with largest area to expand.
         * Otherwise, simply expand whichever is composite.
         */
        if (isComp1 && isComp2) {
            if (area(boundable1) > area(
                    boundable2
                )
            ) {
                expand(boundable1, boundable2, false, priQ, minDistance)
                return
            } else {
                expand(boundable2, boundable1, true, priQ, minDistance)
                return
            }
        } else if (isComp1) {
            expand(boundable1, boundable2, false, priQ, minDistance)
            return
        } else if (isComp2) {
            expand(boundable2, boundable1, true, priQ, minDistance)
            return
        }
        throw IllegalArgumentException("neither boundable is composite")
    }

    private fun expand(
        bndComposite: Boundable, bndOther: Boundable, isFlipped: Boolean,
        priQ: PriorityQueue<BoundablePair>, minDistance: Double
    ) {
        val children = (bndComposite as AbstractNode).getChildBoundables()
        val i: Iterator<*> = children.iterator()
        while (i.hasNext()) {
            val child = i.next() as Boundable
            var bp: BoundablePair = if (isFlipped) {
                BoundablePair(bndOther, child, itemDistance)
            } else {
                BoundablePair(child, bndOther, itemDistance)
            }
            // only add to queue if this pair might contain the closest points
            // MD - it's actually faster to construct the object rather than called distance(child, bndOther)!
            if (bp.distance < minDistance) {
                priQ.add(bp)
            }
        }
    }

    companion object {
        fun isComposite(item: Any?): Boolean {
            return item is AbstractNode
        }

        private fun area(b: Boundable): Double {
            return (b.bounds as Envelope?)!!.area
        }
    }
}