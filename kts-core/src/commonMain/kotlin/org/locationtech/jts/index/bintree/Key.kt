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
package org.locationtech.jts.index.bintree

import org.locationtech.jts.index.quadtree.DoubleBits
import org.locationtech.jts.legacy.Math.floor

/**
 * A Key is a unique identifier for a node in a tree.
 * It contains a lower-left point and a level number. The level number
 * is the power of two for the size of the node envelope
 *
 * @version 1.7
 */
class Key(interval: Interval) {
    // the fields which make up the key
    var point = 0.0
        private set
    var level = 0
        private set

    // auxiliary data which is derived from the key for use in computation
    private var interval: Interval? = null

    init {
        computeKey(interval)
    }

    fun getInterval(): Interval? {
        return interval
    }

    /**
     * return a square envelope containing the argument envelope,
     * whose extent is a power of two and which is based at a power of 2
     */
    fun computeKey(itemInterval: Interval) {
        level = computeLevel(itemInterval)
        interval = Interval()
        computeInterval(level, itemInterval)
        // MD - would be nice to have a non-iterative form of this algorithm
        while (!interval!!.contains(itemInterval)) {
            level += 1
            computeInterval(level, itemInterval)
        }
    }

    private fun computeInterval(level: Int, itemInterval: Interval) {
        val size: Double = DoubleBits.powerOf2(level)
        //double size = pow2.power(level);
        point = floor(itemInterval.min / size) * size
        interval!!.init(point, point + size)
    }

    companion object {
        fun computeLevel(interval: Interval): Int {
            val dx: Double = interval.width
            //int level = BinaryPower.exponent(dx) + 1;
            return DoubleBits.exponent(dx) + 1
        }
    }
}