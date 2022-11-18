/*
 * Copyright (c) 2021 Martin Davis.
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
package org.locationtech.jts.operation.valid

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.LinearRing
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.index.SpatialIndex
import org.locationtech.jts.index.strtree.STRtree

/**
 * Tests whether any holes of a Polygon are
 * nested inside another hole, using a spatial
 * index to speed up the comparisons.
 *
 * The logic assumes that the holes do not overlap and have no collinear segments
 * (so they are properly nested, and there are no duplicate holes).
 *
 * The situation where every vertex of a hole touches another hole
 * is invalid because either the hole is nested,
 * or else it disconnects the polygon interior.
 * This class detects the nested situation.
 * The disconnected interior situation must be checked elsewhere.
 *
 * @version 1.7
 */
internal class IndexedNestedHoleTester(private val polygon: Polygon) {
    private var index: SpatialIndex? = null

    /**
     * Gets a point on a nested hole, if one exists.
     *
     * @return a point on a nested hole, or null if none are nested
     */
    var nestedPoint: Coordinate? = null
        private set

    init {
        loadIndex()
    }

    private fun loadIndex() {
        index = STRtree()
        for (i in 0 until polygon.getNumInteriorRing()) {
            val hole = polygon.getInteriorRingN(i)
            val env = hole.envelopeInternal
            index!!.insert(env, hole)
        }
    }//TODO: find a hole point known to be inside
    /**
     * Hole is not fully covered by test hole, so cannot be nested
     */
    /**
     * Tests if any hole is nested (contained) within another hole.
     * This is invalid.
     * The nested point will be set to reflect this.
     * @return true if some hole is nested
     */
    val isNested: Boolean
        get() {
            for (i in 0 until polygon.getNumInteriorRing()) {
                val hole = polygon.getInteriorRingN(i)
                val results: List<LinearRing>? = index!!.query(hole.envelopeInternal) as List<LinearRing>?
                for (testHole in results!!) {
                    if (hole === testHole) continue
                    /**
                     * Hole is not fully covered by test hole, so cannot be nested
                     */
                    if (!testHole.envelopeInternal.covers(hole.envelopeInternal)) continue
                    if (PolygonTopologyAnalyzer.isRingNested(
                            hole,
                            testHole
                        )
                    ) {
                        //TODO: find a hole point known to be inside
                        nestedPoint = hole.getCoordinateN(0)
                        return true
                    }
                }
            }
            return false
        }
}