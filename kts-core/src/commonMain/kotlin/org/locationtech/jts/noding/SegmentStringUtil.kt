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

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.util.LinearComponentExtracter
import kotlin.jvm.JvmStatic

/**
 * Utility methods for processing [SegmentString]s.
 *
 * @author Martin Davis
 */
object SegmentStringUtil {
    /**
     * Extracts all linear components from a given [Geometry]
     * to [SegmentString]s.
     * The SegmentString data item is set to be the source Geometry.
     *
     * @param geom the geometry to extract from
     * @return a List of SegmentStrings
     */
    @JvmStatic
    fun extractSegmentStrings(geom: Geometry): MutableList<SegmentString> {
        return extractNodedSegmentStrings(geom)
    }

    /**
     * Extracts all linear components from a given [Geometry]
     * to [NodedSegmentString]s.
     * The SegmentString data item is set to be the source Geometry.
     *
     * @param geom the geometry to extract from
     * @return a List of NodedSegmentStrings
     */
    fun extractNodedSegmentStrings(geom: Geometry): MutableList<SegmentString> {
        val segStr: MutableList<SegmentString> = ArrayList()
        val lines: List<Geometry> = LinearComponentExtracter.getLines(geom)
        val i: Iterator<*> = lines.iterator()
        while (i.hasNext()) {
            val line = i.next() as LineString
            val pts: Array<Coordinate> = line.coordinates
            segStr.add(NodedSegmentString(pts, geom))
        }
        return segStr
    }

    /**
     * Extracts all linear components from a given [Geometry]
     * to [BasicSegmentString]s.
     * The SegmentString data item is set to be the source Geometry.
     *
     * @param geom the geometry to extract from
     * @return a List of BasicSegmentStrings
     */
    fun extractBasicSegmentStrings(geom: Geometry): MutableList<SegmentString> {
        val segStr: MutableList<SegmentString> = ArrayList()
        val lines: List<Geometry> = LinearComponentExtracter.getLines(geom)
        val i: Iterator<*> = lines.iterator()
        while (i.hasNext()) {
            val line = i.next() as LineString
            val pts: Array<Coordinate> = line.coordinates
            segStr.add(BasicSegmentString(pts, geom))
        }
        return segStr
    }

    /**
     * Converts a collection of [SegmentString]s into a [Geometry].
     * The geometry will be either a [LineString] or a [MultiLineString] (possibly empty).
     *
     * @param segStrings a collection of SegmentStrings
     * @return a LineString or MultiLineString
     */
    fun toGeometry(segStrings: Collection<*>, geomFact: GeometryFactory): Geometry? {
        val lines = arrayOfNulls<LineString>(segStrings.size)
        var index = 0
        val i = segStrings.iterator()
        while (i.hasNext()) {
            val ss: SegmentString = i.next() as SegmentString
            val line: LineString = geomFact.createLineString(ss.coordinates)
            lines[index++] = line
        }
        return if (lines.size == 1) lines[0] else geomFact.createMultiLineString(lines.requireNoNulls())
    }

    fun toString(segStrings: MutableList<SegmentString>): String {
        val buf: StringBuilder = StringBuilder()
        val i: Iterator<*> = segStrings.iterator()
        while (i.hasNext()) {
            val segStr: SegmentString =
                i.next() as SegmentString
            buf.append(segStr.toString())
            buf.append("\n")
        }
        return buf.toString()
    }
}