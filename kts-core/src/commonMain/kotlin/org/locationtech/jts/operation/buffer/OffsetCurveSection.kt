/*
 * Copyright (c) 2021 Martin Davis.
 * Copyright (c) 2024 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.operation.buffer

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.CoordinateList
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LineString

/**
 * Models a section of a raw offset curve,
 * starting at a given location along the raw curve.
 * The location is a decimal number, with the integer part
 * containing the segment index and the fractional part
 * giving the fractional distance along the segment.
 * The location of the last section segment
 * is also kept, to allow optimizing joining sections together.
 *
 * @author mdavis
 */
internal class OffsetCurveSection(
    private val sectionPts: Array<Coordinate>,
    private val location: Double,
    private val locLast: Double
) : Comparable<OffsetCurveSection> {

    private val coordinates: Array<Coordinate>
        get() = sectionPts

    private fun isEndInSameSegment(nextLoc: Double): Boolean {
        val segIndex = locLast.toInt()
        val nextIndex = nextLoc.toInt()
        return segIndex == nextIndex
    }

    /**
     * Orders sections by their location along the raw offset curve.
     */
    override fun compareTo(other: OffsetCurveSection): Int {
        return location.compareTo(other.location)
    }

    companion object {
        fun toGeometry(sections: MutableList<OffsetCurveSection>, geomFactory: GeometryFactory): Geometry {
            if (sections.size == 0) return geomFactory.createLineString()
            if (sections.size == 1) return geomFactory.createLineString(sections[0].coordinates)

            //-- sort sections in order along the offset curve
            sections.sort()
            val lines = Array<LineString>(sections.size) { i ->
                geomFactory.createLineString(sections[i].coordinates)
            }
            return geomFactory.createMultiLineString(lines)
        }

        /**
         * Joins section coordinates into a LineString.
         * Join vertices which lie in the same raw curve segment
         * are removed, to simplify the result linework.
         *
         * @param sections the sections to join
         * @param geomFactory the geometry factory to use
         * @return the simplified linestring for the joined sections
         */
        fun toLine(sections: MutableList<OffsetCurveSection>, geomFactory: GeometryFactory): Geometry {
            if (sections.size == 0) return geomFactory.createLineString()
            if (sections.size == 1) return geomFactory.createLineString(sections[0].coordinates)

            //-- sort sections in order along the offset curve
            sections.sort()
            val pts = CoordinateList()
            var removeStartPt = false
            for (i in sections.indices) {
                val section = sections[i]
                var removeEndPt = false
                if (i < sections.size - 1) {
                    val nextStartLoc = sections[i + 1].location
                    removeEndPt = section.isEndInSameSegment(nextStartLoc)
                }
                val sectionPts = section.coordinates
                for (j in sectionPts.indices) {
                    if ((removeStartPt && j == 0) || (removeEndPt && j == sectionPts.size - 1)) continue
                    pts.add(sectionPts[j], false)
                }
                removeStartPt = removeEndPt
            }
            return geomFactory.createLineString(pts.toCoordinateArray())
        }

        fun create(srcPts: Array<Coordinate>, start: Int, end: Int, loc: Double, locLast: Double): OffsetCurveSection {
            var len = end - start + 1
            if (end <= start) len = srcPts.size - start + end
            val sectionPts = Array(len) { i ->
                val index = (start + i) % (srcPts.size - 1)
                srcPts[index].copy()
            }
            return OffsetCurveSection(sectionPts, loc, locLast)
        }
    }
}
