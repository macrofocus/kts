/*
 * Copyright (c) 2016 Vivid Solutions.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.linearref

import org.locationtech.jts.geom.*
import kotlin.jvm.JvmOverloads

/**
 * Builds a linear geometry ([LineString] or [MultiLineString])
 * incrementally (point-by-point).
 *
 * @version 1.7
 */
class LinearGeometryBuilder(private val geomFact: GeometryFactory) {
    private val lines: MutableList<Geometry> = ArrayList()
    private var coordList: CoordinateList? = null
    private var ignoreInvalidLines = false
    private var fixInvalidLines = false
    var lastCoordinate: Coordinate? = null
        private set

    /**
     * Allows invalid lines to be ignored rather than causing Exceptions.
     * An invalid line is one which has only one unique point.
     *
     * @param ignoreInvalidLines `true` if short lines are to be ignored
     */
    fun setIgnoreInvalidLines(ignoreInvalidLines: Boolean) {
        this.ignoreInvalidLines = ignoreInvalidLines
    }

    /**
     * Allows invalid lines to be ignored rather than causing Exceptions.
     * An invalid line is one which has only one unique point.
     *
     * @param fixInvalidLines `true` if short lines are to be ignored
     */
    fun setFixInvalidLines(fixInvalidLines: Boolean) {
        this.fixInvalidLines = fixInvalidLines
    }
    /**
     * Adds a point to the current line.
     *
     * @param pt the Coordinate to add
     */
    /**
     * Adds a point to the current line.
     *
     * @param pt the Coordinate to add
     */
    @JvmOverloads
    fun add(pt: Coordinate?, allowRepeatedPoints: Boolean = true) {
        if (coordList == null) coordList = CoordinateList()
        coordList!!.add(pt, allowRepeatedPoints)
        lastCoordinate = pt
    }

    /**
     * Terminate the current LineString.
     */
    fun endLine() {
        if (coordList == null) {
            return
        }
        if (ignoreInvalidLines && coordList!!.size < 2) {
            coordList = null
            return
        }
        val rawPts: Array<Coordinate> = coordList!!.toCoordinateArray()
        var pts = rawPts
        if (fixInvalidLines) pts = validCoordinateSequence(rawPts)
        coordList = null
        var line: LineString? = null
        try {
            line = geomFact.createLineString(pts)
        } catch (ex: IllegalArgumentException) {
            // exception is due to too few points in line.
            // only propagate if not ignoring short lines
            if (!ignoreInvalidLines) throw ex
        }
        if (line != null) lines.add(line)
    }

    private fun validCoordinateSequence(pts: Array<Coordinate>): Array<Coordinate> {
        return if (pts.size >= 2) pts else arrayOf(pts[0], pts[0])
    }

    // end last line in case it was not done by user
    val geometry: Geometry
        get() {
            // end last line in case it was not done by user
            endLine()
            return geomFact.buildGeometry(lines)
        }
}