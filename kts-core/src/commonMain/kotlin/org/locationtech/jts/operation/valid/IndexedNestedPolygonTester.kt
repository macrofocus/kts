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

import org.locationtech.jts.algorithm.locate.IndexedPointInAreaLocator
import org.locationtech.jts.geom.*
import org.locationtech.jts.index.SpatialIndex
import org.locationtech.jts.index.strtree.STRtree

/**
 * Tests whether a MultiPolygon has any element polygon
 * improperly nested inside another polygon, using a spatial
 * index to speed up the comparisons.
 *
 * The logic assumes that the polygons do not overlap and have no collinear segments.
 * So the polygon rings may touch at discrete points,
 * but they are properly nested, and there are no duplicate rings.
 */
internal class IndexedNestedPolygonTester(private val multiPoly: MultiPolygon) {
    private var index: SpatialIndex? = null
    private var locators: Array<IndexedPointInAreaLocator?>? = null

    /**
     * Gets a point on a nested polygon, if one exists.
     *
     * @return a point on a nested polygon, or null if none are nested
     */
    var nestedPoint: Coordinate? = null
        private set

    init {
        loadIndex()
    }

    private fun loadIndex() {
        index = STRtree()
        for (i in 0 until multiPoly.numGeometries) {
            val poly = multiPoly.getGeometryN(i) as Polygon
            val env = poly.envelopeInternal
            index!!.insert(env, i)
        }
    }

    private fun getLocator(polyIndex: Int): IndexedPointInAreaLocator {
        if (locators == null) {
            locators = arrayOfNulls(multiPoly.numGeometries)
        }
        var locator = locators!![polyIndex]
        if (locator == null) {
            locator = IndexedPointInAreaLocator(multiPoly.getGeometryN(polyIndex))
            locators!![polyIndex] = locator
        }
        return locator
    }
    /**
     * If polygon is not fully covered by candidate polygon it cannot be nested
     */
    /**
     * Tests if any polygon is improperly nested (contained) within another polygon.
     * This is invalid.
     * The nested point will be set to reflect this.
     * @return true if some polygon is nested
     */
    val isNested: Boolean
        get() {
            for (i in 0 until multiPoly.numGeometries) {
                val poly = multiPoly.getGeometryN(i) as Polygon
                val shell = poly.exteriorRing
                val results: List<Int>? = index!!.query(poly.envelopeInternal) as List<Int>?
                for (polyIndex in results!!) {
                    val possibleOuterPoly = multiPoly.getGeometryN(polyIndex) as Polygon
                    if (poly === possibleOuterPoly) continue
                    /**
                     * If polygon is not fully covered by candidate polygon it cannot be nested
                     */
                    if (!possibleOuterPoly.envelopeInternal.covers(poly.envelopeInternal)) continue
                    nestedPoint = findNestedPoint(shell, possibleOuterPoly, getLocator(polyIndex))
                    if (nestedPoint != null) return true
                }
            }
            return false
        }

    /**
     * Finds an improperly nested point, if one exists.
     *
     * @param shell the test polygon shell
     * @param possibleOuterPoly a polygon which may contain it
     * @param locator the locator for the outer polygon
     * @return a nested point, if one exists, or null
     */
    private fun findNestedPoint(
        shell: LinearRing?,
        possibleOuterPoly: Polygon, locator: IndexedPointInAreaLocator
    ): Coordinate? {
        /**
         * Try checking two points, since checking point location is fast.
         */
        val shellPt0: Coordinate = shell!!.getCoordinateN(0)
        val loc0 = locator.locate(shellPt0)
        if (loc0 == Location.EXTERIOR) return null
        if (loc0 == Location.INTERIOR) {
            return shellPt0
        }
        val shellPt1: Coordinate = shell!!.getCoordinateN(1)
        val loc1 = locator.locate(shellPt1)
        if (loc1 == Location.EXTERIOR) return null
        return if (loc1 == Location.INTERIOR) {
            shellPt1
        } else findIncidentSegmentNestedPoint(
            shell,
            possibleOuterPoly
        )
        /**
         * The shell points both lie on the boundary of
         * the polygon.
         * Nesting can be checked via the topology of the incident edges.
         */
    }

    companion object {
        /**
         * Finds a point of a shell segment which lies inside a polygon, if any.
         * The shell is assumed to touch the polygon only at shell vertices,
         * and does not cross the polygon.
         *
         * @param shell the shell to test
         * @param poly the polygon to test against
         * @return an interior segment point, or null if the shell is nested correctly
         */
        private fun findIncidentSegmentNestedPoint(shell: LinearRing, poly: Polygon): Coordinate? {
            val polyShell = poly.exteriorRing
            if (polyShell!!.isEmpty) return null
            if (!PolygonTopologyAnalyzer.isRingNested(
                    shell,
                    polyShell
                )
            ) return null
            /**
             * Check if the shell is inside a hole (if there are any).
             * If so this is valid.
             */
            for (i in 0 until poly.getNumInteriorRing()) {
                val hole = poly.getInteriorRingN(i)
                if (hole.envelopeInternal.covers(shell!!.envelopeInternal)
                    && PolygonTopologyAnalyzer.isRingNested(shell, hole)
                ) {
                    return null
                }
            }
            /**
             * The shell is contained in the polygon, but is not contained in a hole.
             * This is invalid.
             */
            return shell!!.getCoordinateN(0)
        }
    }
}