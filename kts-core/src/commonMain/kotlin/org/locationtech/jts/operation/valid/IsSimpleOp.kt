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

import org.locationtech.jts.algorithm.BoundaryNodeRule
import org.locationtech.jts.algorithm.LineIntersector
import org.locationtech.jts.algorithm.RobustLineIntersector
import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.CoordinateArrays.extract
import org.locationtech.jts.geom.util.LinearComponentExtracter
import org.locationtech.jts.legacy.Math.abs
import org.locationtech.jts.noding.BasicSegmentString
import org.locationtech.jts.noding.MCIndexNoder
import org.locationtech.jts.noding.SegmentIntersector
import org.locationtech.jts.noding.SegmentString
import kotlin.jvm.JvmOverloads

/**
 * Tests whether a `Geometry` is simple as defined by the OGC SFS specification.
 *
 * Simplicity is defined for each [Geometry] type as follows:
 *
 *  * **Point** geometries are simple.
 *  * **MultiPoint** geometries are simple if every point is unique
 *  * **LineString** geometries are simple if they do not self-intersect at interior points
 * (i.e. points other than the endpoints).
 * Closed linestrings which intersect only at their endpoints are simple
 * (i.e. valid **LinearRings**s.
 *  * **MultiLineString** geometries are simple if
 * their elements are simple and they intersect only at points
 * which are boundary points of both elements.
 * (The notion of boundary points can be user-specified - see below).
 *  * **Polygonal** geometries have no definition of simplicity.
 * The `isSimple` code checks if all polygon rings are simple.
 * (Note: this means that <tt>isSimple</tt> cannot be used to test
 * for *all* self-intersections in <tt>Polygon</tt>s.
 * In order to check if a <tt>Polygonal</tt> geometry has self-intersections,
 * use [Geometry.isValid]).
 *  * **GeometryCollection** geometries are simple if all their elements are simple.
 *  * Empty geometries are simple
 *
 * For [Lineal] geometries the evaluation of simplicity
 * can be customized by supplying a [BoundaryNodeRule]
 * to define how boundary points are determined.
 * The default is the SFS-standard [BoundaryNodeRule.MOD2_BOUNDARY_RULE].
 *
 * Note that under the <tt>Mod-2</tt> rule, closed <tt>LineString</tt>s (rings)
 * have no boundary.
 * This means that an intersection at the endpoints of
 * two closed LineStrings makes the geometry non-simple.
 * If it is required to test whether a set of `LineString`s touch
 * only at their endpoints, use [BoundaryNodeRule.ENDPOINT_BOUNDARY_RULE].
 * For example, this can be used to validate that a collection of lines
 * form a topologically valid linear network.
 * <P>
 * By default this class finds a single non-simple location.
 * To find all non-simple locations, set [.setFindAllLocations]
 * before calling [.isSimple], and retrieve the locations
 * via [.getNonSimpleLocations].
 * This can be used to find all intersection points in a linear network.
 *
 * @see BoundaryNodeRule
 *
 * @see Geometry.isValid
 * @version 1.7
</P> */
class IsSimpleOp @JvmOverloads constructor(
    private val inputGeom: Geometry,
    boundaryNodeRule: BoundaryNodeRule = BoundaryNodeRule.MOD2_BOUNDARY_RULE
) {
    private val isClosedEndpointsInInterior: Boolean
    private var isFindAllLocations = false
    private var isSimple = false
    private var nonSimplePts: MutableList<Coordinate>? = null
    /**
     * Creates a simplicity checker using a given [BoundaryNodeRule]
     *
     * @param inputGeom the geometry to test
     * @param boundaryNodeRule the boundary node rule to use.
     */
    /**
     * Creates a simplicity checker using the default SFS Mod-2 Boundary Node Rule
     *
     * @param geom the geometry to test
     */
    init {
        isClosedEndpointsInInterior = !boundaryNodeRule.isInBoundary(2)
    }

    /**
     * Sets whether all non-simple intersection points
     * will be found.
     *
     * @param isFindAll whether to find all non-simple points
     */
    fun setFindAllLocations(isFindAll: Boolean) {
        isFindAllLocations = isFindAll
    }

    /**
     * Tests whether the geometry is simple.
     *
     * @return true if the geometry is simple
     */
    fun isSimple(): Boolean {
        compute()
        return isSimple
    }

    /**
     * Gets the coordinate for an location where the geometry
     * fails to be simple.
     * (i.e. where it has a non-boundary self-intersection).
     *
     * @return a coordinate for the location of the non-boundary self-intersection
     * or null if the geometry is simple
     */
    val nonSimpleLocation: Coordinate?
        get() {
            compute()
            return if (nonSimplePts!!.size == 0) null else nonSimplePts!![0]
        }

    /**
     * Gets all non-simple intersection locations.
     *
     * @return a list of the coordinates of non-simple locations
     */
    val nonSimpleLocations: List<Coordinate>?
        get() {
            compute()
            return nonSimplePts
        }

    private fun compute() {
        if (nonSimplePts != null) return
        nonSimplePts = ArrayList()
        isSimple = computeSimple(inputGeom)
    }

    private fun computeSimple(geom: Geometry): Boolean {
        if (geom.isEmpty) return true
        if (geom is Point) return true
        if (geom is LineString) return isSimpleLinearGeometry(geom)
        if (geom is MultiLineString) return isSimpleLinearGeometry(geom)
        if (geom is MultiPoint) return isSimpleMultiPoint(geom)
        return if (geom is Polygonal) isSimplePolygonal(geom) else (geom as? GeometryCollection)?.let {
            isSimpleGeometryCollection(
                it
            )
        } ?: true
        // all other geometry types are simple by definition
    }

    private fun isSimpleMultiPoint(mp: MultiPoint): Boolean {
        if (mp.isEmpty) return true
        var isSimple = true
        val points: MutableSet<Coordinate> = HashSet()
        for (i in 0 until mp.numGeometries) {
            val pt = mp.getGeometryN(i) as Point
            val p = pt.coordinate
            if (points.contains(p)) {
                nonSimplePts!!.add(p!!)
                isSimple = false
                if (!isFindAllLocations) break
            } else points.add(p!!)
        }
        return isSimple
    }

    /**
     * Computes simplicity for polygonal geometries.
     * Polygonal geometries are simple if and only if
     * all of their component rings are simple.
     *
     * @param geom a Polygonal geometry
     * @return true if the geometry is simple
     */
    private fun isSimplePolygonal(geom: Geometry): Boolean {
        var isSimple = true
        val rings: List<Geometry> = LinearComponentExtracter.getLines(geom)
        for (ring in rings) {
            if (!isSimpleLinearGeometry(ring)) {
                isSimple = false
                if (!isFindAllLocations) break
            }
        }
        return isSimple
    }

    /**
     * Semantics for GeometryCollection is
     * simple iff all components are simple.
     *
     * @param geom a geometry collection
     * @return true if the geometry is simple
     */
    private fun isSimpleGeometryCollection(geom: Geometry): Boolean {
        var isSimple = true
        for (i in 0 until geom.numGeometries) {
            val comp = geom.getGeometryN(i)
            if (!computeSimple(comp)) {
                isSimple = false
                if (!isFindAllLocations) break
            }
        }
        return isSimple
    }

    private fun isSimpleLinearGeometry(geom: Geometry): Boolean {
        if (geom.isEmpty) return true
        val segStrings = extractSegmentStrings(geom)
        val segInt = NonSimpleIntersectionFinder(isClosedEndpointsInInterior, isFindAllLocations, nonSimplePts)
        val noder = MCIndexNoder()
        noder.setSegmentIntersector(segInt)
        noder.computeNodes(segStrings)
        return !segInt.hasIntersection()
    }

    private class NonSimpleIntersectionFinder(
        private val isClosedEndpointsInInterior: Boolean,
        private val isFindAll: Boolean,
        private val intersectionPts: MutableList<Coordinate>?
    ) : SegmentIntersector {
        var li: LineIntersector = RobustLineIntersector()

        /**
         * Tests whether an intersection was found.
         *
         * @return true if an intersection was found
         */
        fun hasIntersection(): Boolean {
            return intersectionPts!!.size > 0
        }

        override fun processIntersections(ss0: SegmentString, segIndex0: Int, ss1: SegmentString, segIndex1: Int) {

            // don't test a segment with itself
            val isSameSegString = ss0 === ss1
            val isSameSegment = isSameSegString && segIndex0 == segIndex1
            if (isSameSegment) return
            val hasInt = findIntersection(ss0, segIndex0, ss1, segIndex1)
            if (hasInt) {
                // found an intersection!
                intersectionPts!!.add(li.getIntersection(0))
            }
        }

        private fun findIntersection(
            ss0: SegmentString, segIndex0: Int,
            ss1: SegmentString, segIndex1: Int
        ): Boolean {
            val p00 = ss0.getCoordinate(segIndex0)
            val p01 = ss0.getCoordinate(segIndex0 + 1)
            val p10 = ss1.getCoordinate(segIndex1)
            val p11 = ss1.getCoordinate(segIndex1 + 1)
            li.computeIntersection(p00, p01, p10, p11)
            if (!li.hasIntersection()) return false
            /**
             * Check for an intersection in the interior of a segment.
             */
            val hasInteriorInt = li.isInteriorIntersection
            if (hasInteriorInt) return true
            /**
             * Check for equal segments (which will produce two intersection points).
             * These also intersect in interior points, so are non-simple.
             * (This is not triggered by zero-length segments, since they
             * are filtered out by the MC index).
             */
            val hasEqualSegments = li.intersectionNum >= 2
            if (hasEqualSegments) return true
            /**
             * Following tests assume non-adjacent segments.
             */
            val isSameSegString = ss0 === ss1
            val isAdjacentSegment = isSameSegString && abs(segIndex1 - segIndex0) <= 1
            if (isAdjacentSegment) return false
            /**
             * At this point there is a single intersection point
             * which is a vertex in each segString.
             * Classify them as endpoints or interior
             */
            val isIntersectionEndpt0 = isIntersectionEndpoint(ss0, segIndex0, li, 0)
            val isIntersectionEndpt1 = isIntersectionEndpoint(ss1, segIndex1, li, 1)
            val hasInteriorVertexInt = !(isIntersectionEndpt0 && isIntersectionEndpt1)
            if (hasInteriorVertexInt) return true
            /**
             * Both intersection vertices must be endpoints.
             * Final check is if one or both of them is interior due
             * to being endpoint of a closed ring.
             * This only applies to different lines
             * (which avoids reporting ring endpoints).
             */
            if (isClosedEndpointsInInterior && !isSameSegString) {
                val hasInteriorEndpointInt = ss0.isClosed || ss1.isClosed
                if (hasInteriorEndpointInt) return true
            }
            return false
        }

        override val isDone: Boolean
            get() = if (isFindAll) false else intersectionPts!!.size > 0

        companion object {
            /**
             * Tests whether an intersection vertex is an endpoint of a segment string.
             *
             * @param ss the segmentString
             * @param ssIndex index of segment in segmentString
             * @param li the line intersector
             * @param liSegmentIndex index of segment in intersector
             * @return true if the intersection vertex is an endpoint
             */
            private fun isIntersectionEndpoint(
                ss: SegmentString, ssIndex: Int,
                li: LineIntersector, liSegmentIndex: Int
            ): Boolean {
                val vertexIndex = intersectionVertexIndex(li, liSegmentIndex)
                /**
                 * If the vertex is the first one of the segment, check if it is the start endpoint.
                 * Otherwise check if it is the end endpoint.
                 */
                return if (vertexIndex == 0) {
                    ssIndex == 0
                } else {
                    ssIndex + 2 == ss.size()
                }
            }

            /**
             * Finds the vertex index in a segment of an intersection
             * which is known to be a vertex.
             *
             * @param li the line intersector
             * @param segmentIndex the intersection segment index
             * @return the vertex index (0 or 1) in the segment vertex of the intersection point
             */
            private fun intersectionVertexIndex(li: LineIntersector, segmentIndex: Int): Int {
                val intPt = li.getIntersection(0)
                val endPt0 = li.getEndpoint(segmentIndex, 0)
                return if (intPt.equals2D(endPt0!!)) 0 else 1
            }
        }
    }

    companion object {
        /**
         * Tests whether a geometry is simple.
         *
         * @param geom the geometry to test
         * @return true if the geometry is simple
         */
        fun isSimple(geom: Geometry): Boolean {
            val op = IsSimpleOp(geom)
            return op.isSimple()
        }

        /**
         * Gets a non-simple location in a geometry, if any.
         *
         * @param geom the input geometry
         * @return a non-simple location, or null if the geometry is simple
         */
        fun getNonSimpleLocation(geom: Geometry): Coordinate? {
            val op = IsSimpleOp(geom)
            return op.nonSimpleLocation
        }

        private fun extractSegmentStrings(geom: Geometry): List<SegmentString> {
            val segStrings: MutableList<SegmentString> = ArrayList()
            for (i in 0 until geom.numGeometries) {
                val line = geom.getGeometryN(i) as LineString
                val trimPts = trimRepeatedPoints(line.coordinates)
                if (trimPts != null) {
                    val ss: SegmentString = BasicSegmentString(trimPts, null)
                    segStrings.add(ss)
                }
            }
            return segStrings
        }

        private fun trimRepeatedPoints(pts: Array<Coordinate>): Array<Coordinate>? {
            if (pts.size <= 2) return pts
            val len = pts.size
            val hasRepeatedStart = pts[0].equals2D(pts[1])
            val hasRepeatedEnd = pts[len - 1].equals2D(pts[len - 2])
            if (!hasRepeatedStart && !hasRepeatedEnd) return pts

            //-- trim ends
            var startIndex = 0
            val startPt = pts[0]
            while (startIndex < len - 1 && startPt.equals2D(pts[startIndex + 1])) {
                startIndex++
            }
            var endIndex = len - 1
            val endPt = pts[endIndex]
            while (endIndex > 0 && endPt.equals2D(pts[endIndex - 1])) {
                endIndex--
            }
            //-- are all points identical?
            return if (endIndex - startIndex < 1) {
                null
            } else extract(pts, startIndex, endIndex)
        }
    }
}