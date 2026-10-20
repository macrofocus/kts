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
package org.locationtech.jts.coverage

import org.locationtech.jts.geom.CoordinateSequence
import org.locationtech.jts.geom.CoordinateSequenceFilter
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.LineSegment
import kotlin.jvm.JvmStatic

/**
 * Finds coverage segments which occur in only a single coverage element.
 * In a valid coverage, these are exactly the line segments which lie
 * on the boundary of the coverage.
 *
 * In an invalid coverage, segments might occur in 3 or more elements.
 * This situation is not detected.
 *
 * @author mdavis
 */
class CoverageBoundarySegmentFinder(private val boundarySegs: MutableSet<LineSegment>) : CoordinateSequenceFilter {

    override fun filter(seq: CoordinateSequence?, i: Int) {
        //-- final point does not start a segment
        if (i >= seq!!.size() - 1) return
        val seg = createSegment(seq, i)
        /**
         * Records segments with an odd number of occurrences.
         * In a valid coverage each segment can occur only 1 or 2 times.
         * This does not detect invalid situations, where a segment might occur 3 or more times.
         */
        if (boundarySegs.contains(seg)) {
            boundarySegs.remove(seg)
        } else {
            boundarySegs.add(seg)
        }
    }

    override val isDone: Boolean
        get() = false

    override val isGeometryChanged: Boolean
        get() = false

    companion object {
        @JvmStatic
        fun findBoundarySegments(geoms: Array<Geometry>): MutableSet<LineSegment> {
            val segs: MutableSet<LineSegment> = HashSet()
            val finder = CoverageBoundarySegmentFinder(segs)
            for (geom in geoms) {
                geom.apply(finder)
            }
            return segs
        }

        @JvmStatic
        fun isBoundarySegment(boundarySegs: Set<LineSegment>, seq: CoordinateSequence, i: Int): Boolean {
            val seg = createSegment(seq, i)
            return boundarySegs.contains(seg)
        }

        private fun createSegment(seq: CoordinateSequence, i: Int): LineSegment {
            val seg = LineSegment(seq.getCoordinate(i), seq.getCoordinate(i + 1))
            seg.normalize()
            return seg
        }
    }
}
