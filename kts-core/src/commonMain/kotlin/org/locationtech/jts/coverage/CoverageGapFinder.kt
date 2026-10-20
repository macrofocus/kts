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

import org.locationtech.jts.algorithm.construct.MaximumInscribedCircle
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.LinearRing
import org.locationtech.jts.geom.util.PolygonExtracter
import kotlin.jvm.JvmStatic

/**
 * Finds gaps in a polygonal coverage.
 * Gaps are holes in the coverage which are narrower than a given width.
 *
 * The coverage should be valid according to [CoverageValidator].
 * If this is not the case, some gaps may not be reported, or the invocation may fail.
 *
 * This is a more accurate way of identifying gaps
 * than using [CoverageValidator.setGapWidth].
 * Gaps which separate the coverage into two disjoint regions are not detected.
 * Gores are not identified as gaps.
 *
 * @author mdavis
 */
class CoverageGapFinder
/**
 * Creates a new polygonal coverage gap finder.
 *
 * @param coverage a set of polygons forming a polygonal coverage
 */(private val coverage: Array<Geometry>) {

    /**
     * Finds gaps in the coverage.
     * Returns lines indicating the locations of the gaps.
     *
     * @param gapWidth the maximum width of gap to detect
     * @return a geometry indicating the locations of gaps (which is empty if no gaps were found), or null if the coverage was empty
     */
    fun findGaps(gapWidth: Double): Geometry? {
        val union = CoverageUnion.union(coverage) ?: return null
        val polygons = PolygonExtracter.getPolygons(union)

        val gapLines: MutableList<Geometry> = ArrayList()
        for (poly in polygons) {
            for (i in 0 until poly.getNumInteriorRing()) {
                val hole = poly.getInteriorRingN(i)
                if (isGap(hole, gapWidth)) {
                    gapLines.add(copyLine(hole))
                }
            }
        }
        return union.factory.buildGeometry(gapLines)
    }

    private fun copyLine(hole: LinearRing): LineString {
        val pts = hole.coordinates
        return hole.factory.createLineString(pts)
    }

    private fun isGap(hole: LinearRing, gapWidth: Double): Boolean {
        val holePoly: Geometry = hole.factory.createPolygon(hole)
        //-- guard against bad input
        if (gapWidth <= 0.0) return false

        val tolerance = gapWidth / 100
        //TODO: improve MIC class to allow short-circuiting when radius is larger than a value
        val line = MaximumInscribedCircle.getRadiusLine(holePoly, tolerance)
        val width = line.length * 2
        return width <= gapWidth
    }

    companion object {
        /**
         * Finds gaps in a polygonal coverage.
         * Returns lines indicating the locations of the gaps.
         *
         * @param coverage a set of polygons forming a polygonal coverage
         * @param gapWidth the maximum width of gap to detect
         * @return a geometry indicating the locations of gaps (which is empty if no gaps were found), or null if the coverage was empty
         */
        @JvmStatic
        fun findGaps(coverage: Array<Geometry>, gapWidth: Double): Geometry? {
            val finder = CoverageGapFinder(coverage)
            return finder.findGaps(gapWidth)
        }
    }
}
