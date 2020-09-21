/*
 * Copyright (c) 2016 Vivid Solutions.
 * Copyright (c) 2020 Macrofocus GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.index.quadtree

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.index.quadtree.Root
import org.locationtech.jts.legacy.Math

/**
 * A Key is a unique identifier for a node in a quadtree.
 * It contains a lower-left point and a level number. The level number
 * is the power of two for the size of the node envelope
 *
 * @version 1.7
 */
class Key(itemEnv: Envelope) {
    // the fields which make up the key
    val point = Coordinate()
    var level = 0
        private set

    // auxiliary data which is derived from the key for use in computation
    var envelope: Envelope? = null
        private set
    val centre: Coordinate
        get() = Coordinate(
            (envelope!!.minX + envelope!!.maxX) / 2,
            (envelope!!.minY + envelope!!.maxY) / 2
        )

    /**
     * return a square envelope containing the argument envelope,
     * whose extent is a power of two and which is based at a power of 2
     */
    fun computeKey(itemEnv: Envelope) {
        level = computeQuadLevel(itemEnv)
        envelope = Envelope()
        computeKey(level, itemEnv)
        // MD - would be nice to have a non-iterative form of this algorithm
        while (!envelope!!.contains(itemEnv)) {
            level += 1
            computeKey(level, itemEnv)
        }
    }

    private fun computeKey(level: Int, itemEnv: Envelope) {
        val quadSize: Double = DoubleBits.powerOf2(level)
        point.x = Math.floor(itemEnv.minX / quadSize) * quadSize
        point.y = Math.floor(itemEnv.minY / quadSize) * quadSize
        envelope!!.init(point.x, point.x + quadSize, point.y, point.y + quadSize)
    }

    companion object {
        fun computeQuadLevel(env: Envelope): Int {
            val dx = env.width
            val dy = env.height
            val dMax = if (dx > dy) dx else dy
            return DoubleBits.exponent(dMax) + 1
        }
    }

    init {
        computeKey(itemEnv)
    }
}