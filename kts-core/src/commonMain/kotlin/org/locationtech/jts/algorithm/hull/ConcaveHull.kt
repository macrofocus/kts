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
package org.locationtech.jts.algorithm.hull

import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.legacy.Math.sqrt
import org.locationtech.jts.legacy.queue.PriorityQueue
import org.locationtech.jts.triangulate.tri.Tri
import org.locationtech.jts.triangulate.tri.Tri.Companion.next
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Constructs a concave hull of a set of points.
 * A concave hull is a concave or convex polygon containing all the input points,
 * whose vertices are a subset of the vertices in the input.
 * A given set of points has a sequence of hulls of increasing concaveness,
 * determined by a numeric target parameter.
 *
 * The concave hull is constructed by removing the longest outer edges
 * of the Delaunay Triangulation of the points,
 * until the target criterion parameter is reached.
 *
 * The target criteria are:
 *
 *  * **Maximum Edge Length** - the length of the longest edge of the hull is no larger
 * than this value.
 *  * **Maximum Edge Length Ratio** - determine the Maximum Edge Length
 * as a fraction of the difference between the longest and shortest edge lengths
 * in the Delaunay Triangulation.
 * This normalizes the **Maximum Edge Length** to be scale-free.
 * A value of 1 produces the convex hull; a value of 0 produces maximum concaveness.
 *
 * The preferred criterion is the **Maximum Edge Length Ratio**, since it is
 * scale-free and local (so that no assumption needs to be made about the
 * total amount of concaveness present).
 * Other length criteria can be used by setting the Maximum Edge Length directly.
 * For example, use a length relative  to the longest edge length
 * in the Minimum Spanning Tree of the point set.
 * Or, use a length derived from the [.uniformGridEdgeLength] value.
 *
 * The computed hull is always a single connected [Polygon]
 * (unless it is degenerate, in which case it will be a [Point] or a [LineString]).
 * This constraint may cause the concave hull to fail to meet the target criterion.
 *
 * Optionally the concave hull can be allowed to contain holes.
 * Note that when using the area-based criterion
 * this may result in substantially slower computation.
 *
 * @author Martin Davis
 */
class ConcaveHull(private val inputGeometry: Geometry) {
    private var maxEdgeLength = 0.0
    private var maxEdgeLengthRatio = -1.0
    private var isHolesAllowed = false
    private val geomFactory: GeometryFactory = inputGeometry.factory

    /**
     * Sets the target maximum edge length for the concave hull.
     * The length value must be zero or greater.
     *
     *  * The value 0.0 produces the concave hull of smallest area
     * that is still connected.
     *  * Larger values produce less concave results.
     * A value equal or greater than the longest Delaunay Triangulation edge length
     * produces the convex hull.
     *
     * The [.uniformGridEdgeLength] value may be used as
     * the basis for estimating an appropriate target maximum edge length.
     *
     * @param edgeLength a non-negative length
     *
     * @see .uniformGridEdgeLength
     */
    fun setMaximumEdgeLength(edgeLength: Double) {
        if (edgeLength < 0) throw IllegalArgumentException("Edge length must be non-negative")
        maxEdgeLength = edgeLength
        maxEdgeLengthRatio = -1.0
    }

    /**
     * Sets the target maximum edge length ratio for the concave hull.
     * The edge length ratio is a fraction of the difference
     * between the longest and shortest edge lengths
     * in the Delaunay Triangulation of the input points.
     * It is a value in the range 0 to 1.
     *
     *  * The value 0.0 produces a concave hull of minimum area
     * that is still connected.
     *  * The value 1.0 produces the convex hull.
     *
     * @param edgeLengthRatio a length factor value between 0 and 1
     */
    fun setMaximumEdgeLengthRatio(edgeLengthRatio: Double) {
        if (edgeLengthRatio < 0 || edgeLengthRatio > 1) throw IllegalArgumentException("Edge length ratio must be in range [0,1]")
        maxEdgeLengthRatio = edgeLengthRatio
    }

    /**
     * Sets whether holes are allowed in the concave hull polygon.
     *
     * @param isHolesAllowed true if holes are allowed in the result
     */
    fun setHolesAllowed(isHolesAllowed: Boolean) {
        this.isHolesAllowed = isHolesAllowed
    }

    /**
     * Gets the computed concave hull.
     *
     * @return the concave hull
     */
    val hull: Geometry
        get() {
            if (inputGeometry.isEmpty) {
                return geomFactory.createPolygon()
            }
            val triList: MutableList<HullTri> =
                HullTriangulation.createDelaunayTriangulation(inputGeometry)
            if (maxEdgeLengthRatio >= 0) {
                maxEdgeLength = computeTargetEdgeLength(
                    triList,
                    maxEdgeLengthRatio
                )
            }
            if (triList.isEmpty()) return inputGeometry.convexHull()!!
            computeHull(triList)
            return toGeometry(triList, geomFactory)
        }

    /**
     * Computes the concave hull using edge length as the target criterion.
     * The erosion is done in two phases: first the border, then any
     * internal holes (if required).
     * This allows an fast connection check to be used
     * when eroding holes,
     * which makes this much more efficient than the area-based algorithm.
     *
     * @param triList
     */
    private fun computeHull(triList: MutableList<HullTri>) {
        computeHullBorder(triList)
        if (isHolesAllowed) {
            computeHullHoles(triList)
        }
    }

    private fun computeHullBorder(triList: List<HullTri>) {
        val queue: PriorityQueue<HullTri> = createBorderQueue(triList)
        // remove tris in order of decreasing size (edge length)
        while (!queue.isEmpty()) {
            val tri: HullTri = queue.poll()!!
            if (isBelowLengthThreshold(tri)) break
            if (isRemovableBorder(tri)) {
                //-- the non-null adjacents are now on the border
                val adj0: HullTri? =
                    tri.getAdjacent(0) as HullTri?
                val adj1: HullTri? =
                    tri.getAdjacent(1) as HullTri?
                val adj2: HullTri? =
                    tri.getAdjacent(2) as HullTri?
                tri!!.remove(triList as MutableList<Tri>)

                //-- add border adjacents to queue
                addBorderTri(adj0, queue)
                addBorderTri(adj1, queue)
                addBorderTri(adj2, queue)
            }
        }
    }

    private fun createBorderQueue(triList: List<HullTri?>): PriorityQueue<HullTri> {
        val queue: PriorityQueue<HullTri> =
            PriorityQueue<HullTri>()
        for (tri in triList) {
            //-- add only border triangles which could be eroded
            // (if tri has only 1 adjacent it can't be removed because that would isolate a vertex)
            if (tri!!.numAdjacent() !== 2) continue
            tri!!.setSizeToBoundary()
            queue.add(tri)
        }
        return queue
    }

    /**
     * Adds a Tri to the queue.
     * Only add tris with a single border edge,
     * sice otherwise that would risk isolating a vertex.
     * Sets the ordering size to the length of the border edge.
     *
     * @param tri the Tri to add
     * @param queue the priority queue to add to
     */
    private fun addBorderTri(
        tri: HullTri?,
        queue: PriorityQueue<HullTri>
    ) {
        if (tri == null) return
        if (tri.numAdjacent() !== 2) return
        tri.setSizeToBoundary()
        queue.add(tri)
    }

    private fun isBelowLengthThreshold(tri: HullTri): Boolean {
        return tri.lengthOfBoundary() < maxEdgeLength
    }

    private fun computeHullHoles(triList: MutableList<HullTri>) {
        val candidateHoles: MutableList<HullTri> =
            findCandidateHoles(triList, maxEdgeLength)
        // remove tris in order of decreasing size (edge length)
        for (tri in candidateHoles) {
            if (tri!!.isRemoved
                || tri.isBorder
                || tri!!.hasBoundaryTouch()
            ) continue
            removeHole(triList, tri)
        }
    }

    /**
     * Erodes a hole starting at a given triangle,
     * and eroding all adjacent triangles with boundary edge length above target.
     * @param triList the triangulation
     * @param triHole triangle which is a hole
     */
    private fun removeHole(
        triList: MutableList<HullTri>,
        triHole: HullTri?
    ) {
        val queue: PriorityQueue<HullTri> =
            PriorityQueue<HullTri>()
        queue.add(triHole!!)
        while (!queue.isEmpty()) {
            val tri: HullTri = queue.poll()!!
            if (tri !== triHole && isBelowLengthThreshold(tri)) break
            if (tri === triHole || isRemovableHole(tri)) {
                //-- the non-null adjacents are now on the border
                val adj0: HullTri =
                    tri.getAdjacent(0) as HullTri
                val adj1: HullTri =
                    tri.getAdjacent(1) as HullTri
                val adj2: HullTri =
                    tri.getAdjacent(2) as HullTri
                tri.remove(triList as MutableList<Tri>)

                //-- add border adjacents to queue
                addBorderTri(adj0, queue)
                addBorderTri(adj1, queue)
                addBorderTri(adj2, queue)
            }
        }
    }

    private fun isRemovableBorder(tri: HullTri): Boolean {
        /**
         * Tri must have exactly 2 adjacent tris (i.e. a single boundary edge).
         * If it it has only 0 or 1 adjacent then removal would remove a vertex.
         * If it has 3 adjacent then it is not on border.
         */
        return if (tri.numAdjacent() !== 2) false else !tri.isConnecting
        /**
         * The tri cannot be removed if it is connecting, because
         * this would create more than one result polygon.
         */
    }

    private fun isRemovableHole(tri: HullTri): Boolean {
        /**
         * Tri must have exactly 2 adjacent tris (i.e. a single boundary edge).
         * If it it has only 0 or 1 adjacent then removal would remove a vertex.
         * If it has 3 adjacent then it is not connected to hole.
         */
        return if (tri.numAdjacent() !== 2) false else !tri.hasBoundaryTouch()
        /**
         * Ensure removal does not disconnect hull area.
         * This is a fast check which ensure holes and boundary
         * do not touch at single points.
         * (But it is slightly over-strict, since it prevents
         * any touching holes.)
         */
    }

    private fun toGeometry(
        triList: List<HullTri>,
        geomFactory: GeometryFactory
    ): Geometry {
        return if (!isHolesAllowed) {
            HullTriangulation.traceBoundaryPolygon(triList, geomFactory)
        } else HullTriangulation.union(triList, geomFactory)
        //-- in case holes are present use union (slower but handles holes)
    }

    companion object {
        /**
         * Computes the approximate edge length of
         * a uniform square grid having the same number of
         * points as a geometry and the same area as its convex hull.
         * This value can be used to determine a suitable length threshold value
         * for computing a concave hull.
         * A value from 2 to 4 times the uniform grid length
         * seems to produce reasonable results.
         *
         * @param geom a geometry
         * @return the approximate uniform grid length
         */
        fun uniformGridEdgeLength(geom: Geometry): Double {
            val areaCH = geom.convexHull()!!.area
            val numPts = geom.numPoints
            return sqrt(areaCH / numPts)
        }
        /**
         * Computes a concave hull of the vertices in a geometry
         * using the target criterion of maximum edge length,
         * and optionally allowing holes.
         *
         * @param geom the input geometry
         * @param maxLength the target maximum edge length
         * @param isHolesAllowed whether holes are allowed in the result
         * @return the concave hull
         */
        /**
         * Computes a concave hull of the vertices in a geometry
         * using the target criterion of maximum edge length.
         *
         * @param geom the input geometry
         * @param maxLength the target maximum edge length
         * @return the concave hull
         */
        @JvmOverloads
        @JvmStatic
        fun concaveHullByLength(geom: Geometry, maxLength: Double, isHolesAllowed: Boolean = false): Geometry {
            val hull = ConcaveHull(geom)
            hull.setMaximumEdgeLength(maxLength)
            hull.setHolesAllowed(isHolesAllowed)
            return hull.hull
        }
        /**
         * Computes a concave hull of the vertices in a geometry
         * using the target criterion of maximum edge length factor,
         * and optionally allowing holes.
         * The edge length factor is a fraction of the length difference
         * between the longest and shortest edges
         * in the Delaunay Triangulation of the input points.
         *
         * @param geom the input geometry
         * @param maxLength the target maximum edge length
         * @param isHolesAllowed whether holes are allowed in the result
         * @return the concave hull
         */
        /**
         * Computes a concave hull of the vertices in a geometry
         * using the target criterion of maximum edge length ratio.
         * The edge length ratio is a fraction of the length difference
         * between the longest and shortest edges
         * in the Delaunay Triangulation of the input points.
         *
         * @param geom the input geometry
         * @param lengthRatio the target edge length factor
         * @return the concave hull
         */
        @JvmOverloads
        @JvmStatic
        fun concaveHullByLengthRatio(geom: Geometry, lengthRatio: Double, isHolesAllowed: Boolean = false): Geometry {
            val hull = ConcaveHull(geom)
            hull.setMaximumEdgeLengthRatio(lengthRatio)
            hull.setHolesAllowed(isHolesAllowed)
            return hull.hull
        }

        private fun computeTargetEdgeLength(
            triList: List<HullTri?>,
            edgeLengthRatio: Double
        ): Double {
            if (edgeLengthRatio == 0.0) return 0.0
            var maxEdgeLen = -1.0
            var minEdgeLen = -1.0
            for (tri in triList) {
                for (i in 0..2) {
                    val len: Double = tri!!.getCoordinate(i).distance(tri!!.getCoordinate(next(i)))
                    if (len > maxEdgeLen) maxEdgeLen = len
                    if (minEdgeLen < 0 || len < minEdgeLen) minEdgeLen = len
                }
            }
            //-- if ratio = 1 ensure all edges are included
            return if (edgeLengthRatio == 1.0) 2 * maxEdgeLen else edgeLengthRatio * (maxEdgeLen - minEdgeLen) + minEdgeLen
        }

        /**
         * Finds tris which may be the start of holes.
         * Only tris which have a long enough edge and which do not touch the current hull
         * boundary are included.
         * This avoids the risk of disconnecting the result polygon.
         * The list is sorted in decreasing order of edge length.
         *
         * @param triList
         * @param minEdgeLen minimum length of edges to consider
         * @return
         */
        private fun findCandidateHoles(
            triList: List<HullTri>,
            minEdgeLen: Double
        ): MutableList<HullTri> {
            val candidates: MutableList<HullTri> =
                ArrayList<HullTri>()
            for (tri in triList) {
                if (tri.size < minEdgeLen) continue
                val isTouchingBoundary = tri.isBorder || tri!!.hasBoundaryTouch()
                if (!isTouchingBoundary) {
                    candidates.add(tri)
                }
            }
            // sort by HullTri comparator - longest edge length first
            candidates.sort()
            return candidates
        }
    }
}