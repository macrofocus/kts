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

import org.locationtech.jts.geom.*
import org.locationtech.jts.legacy.Math.isInfinite
import org.locationtech.jts.legacy.Math.isNaN

/**
 * Implements the algorithms required to compute the `isValid()` method
 * for [Geometry]s.
 * See the documentation for the various geometry types for a specification of validity.
 *
 * @version 1.7
 */
class IsValidOp
/**
 * Creates a new validator for a geometry.
 *
 * @param inputGeometry the geometry to validate
 */(
    /**
     * The geometry being validated
     */
    private val inputGeometry: Geometry
) {
    /**
     * If the following condition is TRUE JTS will validate inverted shells and exverted holes
     * (the ESRI SDE model)
     */
    private var isInvertedRingValid = false
    private var validErr: TopologyValidationError? = null

    /**
     * Sets whether polygons using **Self-Touching Rings** to form
     * holes are reported as valid.
     * If this flag is set, the following Self-Touching conditions
     * are treated as being valid:
     *
     *  * **inverted shell** - the shell ring self-touches to create a hole touching the shell
     *  * **exverted hole** - a hole ring self-touches to create two holes touching at a point
     *
     *
     *
     * The default (following the OGC SFS standard)
     * is that this condition is **not** valid (`false`).
     *
     *
     * Self-Touching Rings which disconnect the
     * the polygon interior are still considered to be invalid
     * (these are **invalid** under the SFS, and many other
     * spatial models as well).
     * This includes:
     *
     *  * exverted ("bow-tie") shells which self-touch at a single point
     *  * inverted shells with the inversion touching the shell at another point
     *  * exverted holes with exversion touching the hole at another point
     *  * inverted ("C-shaped") holes which self-touch at a single point causing an island to be formed
     *  * inverted shells or exverted holes which form part of a chain of touching rings
     * (which disconnect the interior)
     *
     *
     * @param isValid states whether geometry with this condition is valid
     */
    fun setSelfTouchingRingFormingHoleValid(isValid: Boolean) {
        isInvertedRingValid = isValid
    }

    /**
     * Tests the validity of the input geometry.
     *
     * @return true if the geometry is valid
     */
    val isValid: Boolean
        get() = isValidGeometry(inputGeometry)

    /**
     * Computes the validity of the geometry,
     * and if not valid returns the validation error for the geometry,
     * or null if the geometry is valid.
     *
     * @return the validation error, if the geometry is invalid
     * or null if the geometry is valid
     */
    val validationError: TopologyValidationError?
        get() {
            isValidGeometry(inputGeometry)
            return validErr
        }

    private fun logInvalid(code: Int, pt: Coordinate?) {
        validErr = TopologyValidationError(code, pt)
    }

    private fun hasInvalidError(): Boolean {
        return validErr != null
    }

    private fun isValidGeometry(g: Geometry): Boolean {
        validErr = null

        // empty geometries are always valid
        if (g.isEmpty) return true
        if (g is Point) return isValid(g)
        if (g is MultiPoint) return isValid(g)
        if (g is LinearRing) return isValid(g)
        if (g is LineString) return isValid(g)
        if (g is Polygon) return isValid(g)
        if (g is MultiPolygon) return isValid(g)
        if (g is GeometryCollection) return isValid(g)
        throw UnsupportedOperationException("${g::class::simpleName}")
    }

    /**
     * Tests validity of a Point.
     */
    private fun isValid(g: Point): Boolean {
        checkCoordinatesValid(g.coordinates)
        return if (hasInvalidError()) false else true
    }

    /**
     * Tests validity of a MultiPoint.
     */
    private fun isValid(g: MultiPoint): Boolean {
        checkCoordinatesValid(g.coordinates)
        return if (hasInvalidError()) false else true
    }

    /**
     * Tests validity of a LineString.
     * Almost anything goes for linestrings!
     */
    private fun isValid(g: LineString): Boolean {
        checkCoordinatesValid(g.coordinates)
        if (hasInvalidError()) return false
        checkPointSize(g, MIN_SIZE_LINESTRING)
        return if (hasInvalidError()) false else true
    }

    /**
     * Tests validity of a LinearRing.
     */
    private fun isValid(g: LinearRing): Boolean {
        checkCoordinatesValid(g.coordinates)
        if (hasInvalidError()) return false
        checkRingClosed(g)
        if (hasInvalidError()) return false
        checkRingPointSize(g)
        if (hasInvalidError()) return false
        checkRingSimple(g)
        return validErr == null
    }

    /**
     * Tests the validity of a polygon.
     * Sets the validErr flag.
     */
    private fun isValid(g: Polygon): Boolean {
        checkCoordinatesValid(g)
        if (hasInvalidError()) return false
        checkRingsClosed(g)
        if (hasInvalidError()) return false
        checkRingsPointSize(g)
        if (hasInvalidError()) return false
        val areaAnalyzer: PolygonTopologyAnalyzer =
            PolygonTopologyAnalyzer(g, isInvertedRingValid)
        checkAreaIntersections(areaAnalyzer)
        if (hasInvalidError()) return false
        checkHolesInShell(g)
        if (hasInvalidError()) return false
        checkHolesNotNested(g)
        if (hasInvalidError()) return false
        checkInteriorConnected(areaAnalyzer)
        return if (hasInvalidError()) false else true
    }

    /**
     * Tests validity of a MultiPolygon.
     *
     * @param g
     * @return
     */
    private fun isValid(g: MultiPolygon): Boolean {
        for (i in 0 until g.numGeometries) {
            val p = g.getGeometryN(i) as Polygon
            checkCoordinatesValid(p)
            if (hasInvalidError()) return false
            checkRingsClosed(p)
            if (hasInvalidError()) return false
            checkRingsPointSize(p)
            if (hasInvalidError()) return false
        }
        val areaAnalyzer: PolygonTopologyAnalyzer =
            PolygonTopologyAnalyzer(g, isInvertedRingValid)
        checkAreaIntersections(areaAnalyzer)
        if (hasInvalidError()) return false
        for (i in 0 until g.numGeometries) {
            val p = g.getGeometryN(i) as Polygon
            checkHolesInShell(p)
            if (hasInvalidError()) return false
        }
        for (i in 0 until g.numGeometries) {
            val p = g.getGeometryN(i) as Polygon
            checkHolesNotNested(p)
            if (hasInvalidError()) return false
        }
        checkShellsNotNested(g)
        if (hasInvalidError()) return false
        checkInteriorConnected(areaAnalyzer)
        return if (hasInvalidError()) false else true
    }

    /**
     * Tests validity of a GeometryCollection.
     *
     * @param gc
     * @return
     */
    private fun isValid(gc: GeometryCollection): Boolean {
        for (i in 0 until gc.numGeometries) {
            if (!isValidGeometry(gc.getGeometryN(i))) return false
        }
        return true
    }

    private fun checkCoordinatesValid(coords: Array<Coordinate>) {
        for (i in coords.indices) {
            if (!isValid(coords[i])) {
                logInvalid(
                    TopologyValidationError.INVALID_COORDINATE,
                    coords[i]
                )
                return
            }
        }
    }

    private fun checkCoordinatesValid(poly: Polygon) {
        checkCoordinatesValid(poly.exteriorRing!!.coordinates)
        if (hasInvalidError()) return
        for (i in 0 until poly.getNumInteriorRing()) {
            checkCoordinatesValid(poly.getInteriorRingN(i).coordinates)
            if (hasInvalidError()) return
        }
    }

    private fun checkRingClosed(ring: LinearRing?) {
        if (ring!!.isEmpty) return
        if (!ring!!.isClosed) {
            val pt: Coordinate? = if (ring.numPoints >= 1) ring.getCoordinateN(0) else null
            logInvalid(TopologyValidationError.RING_NOT_CLOSED, pt)
            return
        }
    }

    private fun checkRingsClosed(poly: Polygon) {
        checkRingClosed(poly.exteriorRing)
        if (hasInvalidError()) return
        for (i in 0 until poly.getNumInteriorRing()) {
            checkRingClosed(poly.getInteriorRingN(i))
            if (hasInvalidError()) return
        }
    }

    private fun checkRingsPointSize(poly: Polygon) {
        checkRingPointSize(poly.exteriorRing)
        if (hasInvalidError()) return
        for (i in 0 until poly.getNumInteriorRing()) {
            checkRingPointSize(poly.getInteriorRingN(i))
            if (hasInvalidError()) return
        }
    }

    private fun checkRingPointSize(ring: LinearRing?) {
        if (ring!!.isEmpty) return
        checkPointSize(ring, MIN_SIZE_RING)
    }

    /**
     * Check the number of non-repeated points is at least a given size.
     *
     * @param line
     * @param minSize
     */
    private fun checkPointSize(line: LineString?, minSize: Int) {
        if (!isNonRepeatedSizeAtLeast(line, minSize)) {
            val pt = if (line!!.numPoints >= 1) line.getCoordinateN(0) else null
            logInvalid(TopologyValidationError.TOO_FEW_POINTS, pt)
        }
    }

    /**
     * Test if the number of non-repeated points in a line
     * is at least a given minimum size.
     *
     * @param line the line to test
     * @param minSize the minimum line size
     * @return true if the line has the required number of non-repeated points
     */
    private fun isNonRepeatedSizeAtLeast(line: LineString?, minSize: Int): Boolean {
        var numPts = 0
        var prevPt: Coordinate? = null
        for (i in 0 until line!!.numPoints) {
            if (numPts >= minSize) return true
            val pt = line.getCoordinateN(i)
            if (prevPt == null || !pt.equals2D(prevPt)) numPts++
            prevPt = pt
        }
        return numPts >= minSize
    }

    private fun checkAreaIntersections(areaAnalyzer: PolygonTopologyAnalyzer) {
        if (areaAnalyzer.hasInvalidIntersection()) {
            logInvalid(
                areaAnalyzer.invalidCode,
                areaAnalyzer.invalidLocation
            )
            return
        }
    }

    /**
     * Check whether a ring self-intersects (except at its endpoints).
     *
     * @param ring the linear ring to check
     */
    private fun checkRingSimple(ring: LinearRing) {
        val intPt: Coordinate? =
            PolygonTopologyAnalyzer.findSelfIntersection(ring)
        if (intPt != null) {
            logInvalid(
                TopologyValidationError.RING_SELF_INTERSECTION,
                intPt
            )
        }
    }

    /**
     * Tests that each hole is inside the polygon shell.
     * This routine assumes that the holes have previously been tested
     * to ensure that all vertices lie on the shell or on the same side of it
     * (i.e. that the hole rings do not cross the shell ring).
     * Given this, a simple point-in-polygon test of a single point in the hole can be used,
     * provided the point is chosen such that it does not lie on the shell.
     *
     * @param poly the polygon to be tested for hole inclusion
     */
    private fun checkHolesInShell(poly: Polygon) {
        // skip test if no holes are present
        if (poly.getNumInteriorRing() <= 0) return
        val shell = poly.exteriorRing
        val isShellEmpty: Boolean = shell!!.isEmpty
        for (i in 0 until poly.getNumInteriorRing()) {
            val hole = poly.getInteriorRingN(i)
            if (hole.isEmpty) continue
            var invalidPt: Coordinate? = null
            if (isShellEmpty) {
                invalidPt = hole.coordinate
            } else {
                invalidPt = findHoleOutsideShellPoint(hole, shell)
            }
            if (invalidPt != null) {
                logInvalid(
                    TopologyValidationError.HOLE_OUTSIDE_SHELL,
                    invalidPt
                )
                return
            }
        }
    }

    /**
     * Checks if a polygon hole lies inside its shell
     * and if not returns a point indicating this.
     * The hole is known to be wholly inside or outside the shell,
     * so it suffices to find a single point which is interior or exterior,
     * or check the edge topology at a point on the boundary of the shell.
     *
     * @param hole the hole to test
     * @param shell the polygon shell to test against
     * @return a hole point outside the shell, or null if it is inside
     */
    private fun findHoleOutsideShellPoint(hole: LinearRing, shell: LinearRing?): Coordinate? {
        val holePt0: Coordinate = hole.getCoordinateN(0)
        /**
         * If hole envelope is not covered by shell, it must be outside
         */
        if (!shell!!.envelopeInternal.covers(hole.envelopeInternal)) //TODO: find hole pt outside shell env
            return holePt0
        return if (PolygonTopologyAnalyzer.Companion.isRingNested(
                hole,
                shell
            )
        ) null else holePt0
        //TODO: find hole point outside shell
    }

    /**
     * Checks if any polygon hole is nested inside another.
     * Assumes that holes do not cross (overlap),
     * This is checked earlier.
     *
     * @param poly the polygon with holes to test
     */
    private fun checkHolesNotNested(poly: Polygon) {
        // skip test if no holes are present
        if (poly.getNumInteriorRing() <= 0) return
        val nestedTester: IndexedNestedHoleTester =
            IndexedNestedHoleTester(poly)
        if (nestedTester.isNested) {
            logInvalid(
                TopologyValidationError.NESTED_HOLES,
                nestedTester.nestedPoint
            )
        }
    }

    /**
     * Checks that no element polygon is in the interior of another element polygon.
     *
     *
     * Preconditions:
     *
     *  * shells do not partially overlap
     *  * shells do not touch along an edge
     *  * no duplicate rings exist
     *
     * These have been confirmed by the [PolygonTopologyAnalyzer].
     */
    private fun checkShellsNotNested(mp: MultiPolygon) {
        // skip test if only one shell present
        if (mp.numGeometries <= 1) return
        val nestedTester: IndexedNestedPolygonTester =
            IndexedNestedPolygonTester(mp)
        if (nestedTester.isNested) {
            logInvalid(
                TopologyValidationError.NESTED_SHELLS,
                nestedTester.nestedPoint
            )
        }
    }

    private fun checkInteriorConnected(analyzer: PolygonTopologyAnalyzer) {
        if (analyzer.isInteriorDisconnected) {
            logInvalid(
                TopologyValidationError.DISCONNECTED_INTERIOR,
                analyzer.disconnectionLocation
            )
        }
    }

    companion object {
        private const val MIN_SIZE_LINESTRING = 2
        private const val MIN_SIZE_RING = 4

        /**
         * Tests whether a [Geometry] is valid.
         * @param geom the Geometry to test
         * @return true if the geometry is valid
         */
        fun isValid(geom: Geometry): Boolean {
            val isValidOp = IsValidOp(geom)
            return isValidOp.isValid
        }

        /**
         * Checks whether a coordinate is valid for processing.
         * Coordinates are valid if their x and y ordinates are in the
         * range of the floating point representation.
         *
         * @param coord the coordinate to validate
         * @return `true` if the coordinate is valid
         */
        fun isValid(coord: Coordinate): Boolean {
            if (isNaN(coord.x)) return false
            if (isInfinite(coord.x)) return false
            if (isNaN(coord.y)) return false
            return if (isInfinite(coord.y)) false else true
        }
    }
}