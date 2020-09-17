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
package org.locationtech.jts.operation.valid

import org.locationtech.jts.algorithm.LineIntersector
import org.locationtech.jts.algorithm.PointLocation
import org.locationtech.jts.algorithm.RobustLineIntersector
import org.locationtech.jts.algorithm.locate.IndexedPointInAreaLocator
import org.locationtech.jts.algorithm.locate.PointOnGeometryLocator
import org.locationtech.jts.geom.*
import org.locationtech.jts.geomgraph.Edge
import org.locationtech.jts.geomgraph.EdgeIntersection
import org.locationtech.jts.geomgraph.EdgeIntersectionList
import org.locationtech.jts.geomgraph.GeometryGraph
import org.locationtech.jts.legacy.Math
import org.locationtech.jts.legacy.TreeSet
import org.locationtech.jts.util.Assert.shouldNeverReachHere
import kotlin.jvm.JvmStatic


/**
 * Implements the algorithms required to compute the `isValid()` method
 * for [Geometry]s.
 * See the documentation for the various geometry types for a specification of validity.
 *
 * @version 1.7
 */
class IsValidOp(  // the base Geometry to be validated
    private val parentGeometry: Geometry
) {
    /**
     * If the following condition is TRUE JTS will validate inverted shells and exverted holes
     * (the ESRI SDE model)
     */
    private var isSelfTouchingRingFormingHoleValid = false
    private var validErr: TopologyValidationError? = null

    /**
     * Sets whether polygons using **Self-Touching Rings** to form
     * holes are reported as valid.
     * If this flag is set, the following Self-Touching conditions
     * are treated as being valid:
     *
     *  * the shell ring self-touches to create a hole touching the shell
     *  * a hole ring self-touches to create two holes touching at a point
     *
     *
     * The default (following the OGC SFS standard)
     * is that this condition is **not** valid (`false`).
     *
     * This does not affect whether Self-Touching Rings
     * disconnecting the polygon interior are considered valid
     * (these are considered to be **invalid** under the SFS, and many other
     * spatial models as well).
     * This includes "bow-tie" shells,
     * which self-touch at a single point causing the interior to
     * be disconnected,
     * and "C-shaped" holes which self-touch at a single point causing an island to be formed.
     *
     * @param isValid states whether geometry with this condition is valid
     */
    fun setSelfTouchingRingFormingHoleValid(isValid: Boolean) {
        isSelfTouchingRingFormingHoleValid = isValid
    }

    /**
     * Computes the validity of the geometry,
     * and returns <tt>true</tt> if it is valid.
     *
     * @return true if the geometry is valid
     */
    val isValid: Boolean
        get() {
            checkValid(parentGeometry)
            return validErr == null
        }

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
            checkValid(parentGeometry)
            return validErr
        }

    private fun checkValid(g: Geometry) {
        validErr = null

        // empty geometries are always valid!
        if (g.isEmpty) return
        when (g) {
            is Point -> checkValid(g)
            is MultiPoint -> checkValid(g)
            is LinearRing -> checkValid(
                g
            )
            is LineString -> checkValid(g)
            is Polygon -> checkValid(g)
            is MultiPolygon -> checkValid(
                g
            )
            is GeometryCollection -> checkValid(g)
            else -> throw UnsupportedOperationException(g::class.simpleName)
        }
    }

    /**
     * Checks validity of a Point.
     */
    private fun checkValid(g: Point) {
        checkInvalidCoordinates(g.coordinates!!)
    }

    /**
     * Checks validity of a MultiPoint.
     */
    private fun checkValid(g: MultiPoint) {
        checkInvalidCoordinates(g.coordinates)
    }

    /**
     * Checks validity of a LineString.  Almost anything goes for linestrings!
     */
    private fun checkValid(g: LineString) {
        checkInvalidCoordinates(g.coordinates)
        if (validErr != null) return
        val graph = GeometryGraph(0, g)
        checkTooFewPoints(graph)
    }

    /**
     * Checks validity of a LinearRing.
     */
    private fun checkValid(g: LinearRing) {
        checkInvalidCoordinates(g.coordinates)
        if (validErr != null) return
        checkClosedRing(g)
        if (validErr != null) return
        val graph = GeometryGraph(0, g)
        checkTooFewPoints(graph)
        if (validErr != null) return
        val li: LineIntersector = RobustLineIntersector()
        graph.computeSelfNodes(li, true, true)
        checkNoSelfIntersectingRings(graph)
    }

    /**
     * Checks the validity of a polygon.
     * Sets the validErr flag.
     */
    private fun checkValid(g: Polygon) {
        checkInvalidCoordinates(g)
        if (validErr != null) return
        checkClosedRings(g)
        if (validErr != null) return
        val graph = GeometryGraph(0, g)
        checkTooFewPoints(graph)
        if (validErr != null) return
        checkConsistentArea(graph)
        if (validErr != null) return
        if (!isSelfTouchingRingFormingHoleValid) {
            checkNoSelfIntersectingRings(graph)
            if (validErr != null) return
        }
        checkHolesInShell(g, graph)
        if (validErr != null) return
        //SLOWcheckHolesNotNested(g);
        checkHolesNotNested(g, graph)
        if (validErr != null) return
        checkConnectedInteriors(graph)
    }

    private fun checkValid(g: MultiPolygon) {
        for (i in 0 until g.numGeometries) {
            val p = g.getGeometryN(i) as Polygon
            checkInvalidCoordinates(p)
            if (validErr != null) return
            checkClosedRings(p)
            if (validErr != null) return
        }
        val graph = GeometryGraph(0, g)
        checkTooFewPoints(graph)
        if (validErr != null) return
        checkConsistentArea(graph)
        if (validErr != null) return
        if (!isSelfTouchingRingFormingHoleValid) {
            checkNoSelfIntersectingRings(graph)
            if (validErr != null) return
        }
        for (i in 0 until g.numGeometries) {
            val p = g.getGeometryN(i) as Polygon
            checkHolesInShell(p, graph)
            if (validErr != null) return
        }
        for (i in 0 until g.numGeometries) {
            val p = g.getGeometryN(i) as Polygon
            checkHolesNotNested(p, graph)
            if (validErr != null) return
        }
        checkShellsNotNested(g, graph)
        if (validErr != null) return
        checkConnectedInteriors(graph)
    }

    private fun checkValid(gc: GeometryCollection) {
        for (i in 0 until gc.numGeometries) {
            val g = gc.getGeometryN(i)
            checkValid(g)
            if (validErr != null) return
        }
    }

    private fun checkInvalidCoordinates(coords: Array<Coordinate>) {
        for (i in coords.indices) {
            if (!isValid(coords[i])) {
                validErr = TopologyValidationError(
                    TopologyValidationError.INVALID_COORDINATE,
                    coords[i]
                )
                return
            }
        }
    }

    private fun checkInvalidCoordinates(poly: Polygon) {
        checkInvalidCoordinates(poly.exteriorRing!!.coordinates)
        if (validErr != null) return
        for (i in 0 until poly.getNumInteriorRing()) {
            checkInvalidCoordinates(poly.getInteriorRingN(i)!!.coordinates)
            if (validErr != null) return
        }
    }

    private fun checkClosedRings(poly: Polygon) {
        checkClosedRing(poly.exteriorRing)
        if (validErr != null) return
        for (i in 0 until poly.getNumInteriorRing()) {
            checkClosedRing(poly.getInteriorRingN(i))
            if (validErr != null) return
        }
    }

    private fun checkClosedRing(ring: LinearRing?) {
        if (ring!!.isEmpty) return
        if (!ring.isClosed) {
            var pt: Coordinate? = null
            if (ring.numPoints >= 1) pt = ring.getCoordinateN(0)
            validErr = TopologyValidationError(
                TopologyValidationError.RING_NOT_CLOSED,
                pt
            )
        }
    }

    private fun checkTooFewPoints(graph: GeometryGraph) {
        if (graph.hasTooFewPoints()) {
            validErr = TopologyValidationError(
                TopologyValidationError.TOO_FEW_POINTS,
                graph.invalidPoint
            )
            return
        }
    }

    /**
     * Checks that the arrangement of edges in a polygonal geometry graph
     * forms a consistent area.
     *
     * @param graph
     *
     * @see ConsistentAreaTester
     */
    private fun checkConsistentArea(graph: GeometryGraph) {
        val cat = ConsistentAreaTester(graph)
        val isValidArea = cat.isNodeConsistentArea
        if (!isValidArea) {
            validErr = TopologyValidationError(
                TopologyValidationError.SELF_INTERSECTION,
                cat.invalidPoint
            )
            return
        }
        if (cat.hasDuplicateRings()) {
            validErr = TopologyValidationError(
                TopologyValidationError.DUPLICATE_RINGS,
                cat.invalidPoint
            )
        }
    }

    /**
     * Check that there is no ring which self-intersects (except of course at its endpoints).
     * This is required by OGC topology rules (but not by other models
     * such as ESRI SDE, which allow inverted shells and exverted holes).
     *
     * @param graph the topology graph of the geometry
     */
    private fun checkNoSelfIntersectingRings(graph: GeometryGraph) {
        val i = graph.edgeIterator
        while (i.hasNext()) {
            val e = i.next() as Edge
            checkNoSelfIntersectingRing(e.getEdgeIntersectionList())
            if (validErr != null) return
        }
    }

    /**
     * Check that a ring does not self-intersect, except at its endpoints.
     * Algorithm is to count the number of times each node along edge occurs.
     * If any occur more than once, that must be a self-intersection.
     */
    private fun checkNoSelfIntersectingRing(eiList: EdgeIntersectionList) {
        val nodeSet: MutableSet<Coordinate> = TreeSet()
        var isFirst = true
        val i = eiList.iterator()
        while (i.hasNext()) {
            val ei = i.next() as EdgeIntersection
            if (isFirst) {
                isFirst = false
                continue
            }
            if (nodeSet.contains(ei.coordinate)) {
                validErr = TopologyValidationError(
                    TopologyValidationError.RING_SELF_INTERSECTION,
                    ei.coordinate
                )
                return
            } else {
                nodeSet.add(ei.coordinate)
            }
        }
    }

    /**
     * Tests that each hole is inside the polygon shell.
     * This routine assumes that the holes have previously been tested
     * to ensure that all vertices lie on the shell or on the same side of it
     * (i.e. that the hole rings do not cross the shell ring).
     * In other words, this test is only correct if the ConsistentArea test is passed first.
     * Given this, a simple point-in-polygon test of a single point in the hole can be used,
     * provided the point is chosen such that it does not lie on the shell.
     *
     * @param p the polygon to be tested for hole inclusion
     * @param graph a GeometryGraph incorporating the polygon
     */
    private fun checkHolesInShell(p: Polygon, graph: GeometryGraph) {
        // skip test if no holes are present
        if (p.getNumInteriorRing() <= 0) return
        val shell = p.exteriorRing
        val isShellEmpty = shell!!.isEmpty
        //PointInRing pir = new SimplePointInRing(shell); // testing only
        val pir: PointOnGeometryLocator = IndexedPointInAreaLocator(shell)
        for (i in 0 until p.getNumInteriorRing()) {
            val hole = p.getInteriorRingN(i)
            var holePt: Coordinate? = null
            if (hole!!.isEmpty) continue
            holePt = findPtNotNode(hole.coordinates, shell, graph)
            /**
             * If no non-node hole vertex can be found, the hole must
             * split the polygon into disconnected interiors.
             * This will be caught by a subsequent check.
             */
            if (holePt == null) return
            val outside = isShellEmpty || Location.EXTERIOR == pir.locate(holePt)
            if (outside) {
                validErr = TopologyValidationError(
                    TopologyValidationError.HOLE_OUTSIDE_SHELL,
                    holePt
                )
                return
            }
        }
    }

    /**
     * Tests that no hole is nested inside another hole.
     * This routine assumes that the holes are disjoint.
     * To ensure this, holes have previously been tested
     * to ensure that:
     *
     *  * they do not partially overlap
     * (checked by `checkRelateConsistency`)
     *  * they are not identical
     * (checked by `checkRelateConsistency`)
     *
     */
    private fun checkHolesNotNested(p: Polygon, graph: GeometryGraph) {
        // skip test if no holes are present
        if (p.getNumInteriorRing() <= 0) return
        val nestedTester = IndexedNestedRingTester(graph)
        //SimpleNestedRingTester nestedTester = new SimpleNestedRingTester(arg[0]);
        //SweeplineNestedRingTester nestedTester = new SweeplineNestedRingTester(arg[0]);
        for (i in 0 until p.getNumInteriorRing()) {
            val innerHole = p.getInteriorRingN(i)
            if (innerHole!!.isEmpty) continue
            nestedTester.add(innerHole)
        }
        val isNonNested = nestedTester.isNonNested
        if (!isNonNested) {
            validErr = TopologyValidationError(
                TopologyValidationError.NESTED_HOLES,
                nestedTester.nestedPoint
            )
        }
    }

    /**
     * Tests that no element polygon is wholly in the interior of another element polygon.
     *
     * Preconditions:
     *
     *  * shells do not partially overlap
     *  * shells do not touch along an edge
     *  * no duplicate rings exist
     *
     * This routine relies on the fact that while polygon shells may touch at one or
     * more vertices, they cannot touch at ALL vertices.
     */
    private fun checkShellsNotNested(mp: MultiPolygon, graph: GeometryGraph) {
        for (i in 0 until mp.numGeometries) {
            val p = mp.getGeometryN(i) as Polygon
            val shell = p.exteriorRing
            for (j in 0 until mp.numGeometries) {
                if (i == j) continue
                val p2 = mp.getGeometryN(j) as Polygon
                checkShellNotNested(shell, p2, graph)
                if (validErr != null) return
            }
        }
    }

    /**
     * Check if a shell is incorrectly nested within a polygon.  This is the case
     * if the shell is inside the polygon shell, but not inside a polygon hole.
     * (If the shell is inside a polygon hole, the nesting is valid.)
     *
     * The algorithm used relies on the fact that the rings must be properly contained.
     * E.g. they cannot partially overlap (this has been previously checked by
     * `checkRelateConsistency` )
     */
    private fun checkShellNotNested(shell: LinearRing?, p: Polygon, graph: GeometryGraph) {
        val shellPts: Array<Coordinate>? = shell!!.coordinates
        // test if shell is inside polygon shell
        val polyShell = p.exteriorRing
        if (polyShell!!.isEmpty) return
        val polyPts = polyShell.coordinates
        val shellPt = findPtNotNode(shellPts, polyShell, graph) ?: return
        // if no point could be found, we can assume that the shell is outside the polygon
        val insidePolyShell = PointLocation.isInRing(shellPt, polyPts)
        if (!insidePolyShell) return

        // if no holes, this is an error!
        if (p.getNumInteriorRing() <= 0) {
            validErr = TopologyValidationError(
                TopologyValidationError.NESTED_SHELLS,
                shellPt
            )
            return
        }
        /**
         * Check if the shell is inside one of the holes.
         * This is the case if one of the calls to checkShellInsideHole
         * returns a null coordinate.
         * Otherwise, the shell is not properly contained in a hole, which is an error.
         */
        var badNestedPt: Coordinate? = null
        for (i in 0 until p.getNumInteriorRing()) {
            val hole = p.getInteriorRingN(i)
            badNestedPt = checkShellInsideHole(shell, hole, graph)
            if (badNestedPt == null) return
        }
        validErr = TopologyValidationError(
            TopologyValidationError.NESTED_SHELLS,
            badNestedPt
        )
    }

    /**
     * This routine checks to see if a shell is properly contained in a hole.
     * It assumes that the edges of the shell and hole do not
     * properly intersect.
     *
     * @return `null` if the shell is properly contained, or
     * a Coordinate which is not inside the hole if it is not
     */
    private fun checkShellInsideHole(shell: LinearRing?, hole: LinearRing?, graph: GeometryGraph): Coordinate? {
        val shellPts: Array<Coordinate> = shell!!.coordinates
        val holePts: Array<Coordinate> = hole!!.coordinates
        // TODO: improve performance of this - by sorting pointlists for instance?
        val shellPt = findPtNotNode(shellPts, hole, graph)
        // if point is on shell but not hole, check that the shell is inside the hole
        if (shellPt != null) {
            val insideHole = PointLocation.isInRing(shellPt, holePts)
            if (!insideHole) {
                return shellPt
            }
        }
        val holePt = findPtNotNode(holePts, shell, graph)
        // if point is on hole but not shell, check that the hole is outside the shell
        if (holePt != null) {
            val insideShell = PointLocation.isInRing(holePt, shellPts)
            return if (insideShell) {
                holePt
            } else null
        }
        shouldNeverReachHere("points in shell and hole appear to be equal")
        return null
    }

    private fun checkConnectedInteriors(graph: GeometryGraph) {
        val cit = ConnectedInteriorTester(graph)
        if (!cit.isInteriorsConnected) validErr = TopologyValidationError(
            TopologyValidationError.DISCONNECTED_INTERIOR,
            cit.coordinate
        )
    }

    companion object {
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
         * Coordinates are valid iff their x and y ordinates are in the
         * range of the floating point representation.
         *
         * @param coord the coordinate to validate
         * @return `true` if the coordinate is valid
         */
        fun isValid(coord: Coordinate): Boolean {
            if (Math.isNaN(coord.x)) return false
            if (Math.isInfinite(coord.x)) return false
            if (Math.isNaN(coord.y)) return false
            return !Math.isInfinite(coord.y)
        }

        /**
         * Find a point from the list of testCoords
         * that is NOT a node in the edge for the list of searchCoords
         *
         * @return the point found, or `null` if none found
         */
        @JvmStatic
        fun findPtNotNode(
            testCoords: Array<Coordinate>?,
            searchRing: LinearRing?,
            graph: GeometryGraph
        ): Coordinate? {
            // find edge corresponding to searchRing.
            val searchEdge = graph.findEdge(searchRing)!!
            // find a point in the testCoords which is not a node of the searchRing
            val eiList = searchEdge.getEdgeIntersectionList()
            // somewhat inefficient - is there a better way? (Use a node map, for instance?)
            for (i in testCoords!!.indices) {
                val pt = testCoords[i]
                if (!eiList.isIntersection(pt)) return pt
            }
            return null
        }
    }
}