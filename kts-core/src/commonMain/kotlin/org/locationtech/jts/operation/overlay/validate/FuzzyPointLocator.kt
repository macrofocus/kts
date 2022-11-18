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
package org.locationtech.jts.operation.overlay.validate

import org.locationtech.jts.algorithm.PointLocator
import org.locationtech.jts.geom.*

/**
 * Finds the most likely [Location] of a point relative to
 * the polygonal components of a geometry, using a tolerance value.
 * If a point is not clearly in the Interior or Exterior,
 * it is considered to be on the Boundary.
 * In other words, if the point is within the tolerance of the Boundary,
 * it is considered to be on the Boundary; otherwise,
 * whether it is Interior or Exterior is determined directly.
 *
 * @author Martin Davis
 * @version 1.7
 */
class FuzzyPointLocator(private val g: Geometry, private val boundaryDistanceTolerance: Double) {
    private val linework: MultiLineString
    private val ptLocator = PointLocator()
    private val seg = LineSegment()

    init {
        linework = extractLinework(g)
    }

    fun getLocation(pt: Coordinate): Int {
        return if (isWithinToleranceOfBoundary(pt)) Location.BOUNDARY else ptLocator.locate(pt, g)
        /*
    double dist = linework.distance(point);

    // if point is close to boundary, it is considered to be on the boundary
    if (dist < tolerance)
      return Location.BOUNDARY;
     */

        // now we know point must be clearly inside or outside geometry, so return actual location value
    }

    /**
     * Extracts linework for polygonal components.
     *
     * @param g the geometry from which to extract
     * @return a lineal geometry containing the extracted linework
     */
    private fun extractLinework(g: Geometry): MultiLineString {
        val extracter = PolygonalLineworkExtracter()
        g.apply(extracter)
        val linework: MutableList<LineString> = extracter.getLinework()
        val lines = GeometryFactory.toLineStringArray(linework)
        return g.factory.createMultiLineString(lines)
    }

    private fun isWithinToleranceOfBoundary(pt: Coordinate): Boolean {
        for (i in 0 until linework.numGeometries) {
            val line = linework.getGeometryN(i) as LineString
            val seq = line.coordinateSequence
            for (j in 0 until seq!!.size() - 1) {
                seq.getCoordinate(j, seg.p0)
                seq.getCoordinate(j + 1, seg.p1)
                val dist = seg.distance(pt)
                if (dist <= boundaryDistanceTolerance) return true
            }
        }
        return false
    }
}

/**
 * Extracts the LineStrings in the boundaries
 * of all the polygonal elements in the target [Geometry].
 *
 * @author Martin Davis
 */
internal class PolygonalLineworkExtracter : GeometryFilter {
    private val linework: MutableList<LineString>

    init {
        linework = ArrayList()
    }

    /**
     * Filters out all linework for polygonal elements
     */
    override fun filter(g: Geometry?) {
        if (g is Polygon) {
            val poly = g
            linework.add(poly.exteriorRing!!)
            for (i in 0 until poly.getNumInteriorRing()) {
                linework.add(poly.getInteriorRingN(i))
            }
        }
    }

    /**
     * Gets the list of polygonal linework.
     *
     * @return a List of LineStrings
     */
    fun getLinework(): MutableList<LineString> {
        return linework
    }
}