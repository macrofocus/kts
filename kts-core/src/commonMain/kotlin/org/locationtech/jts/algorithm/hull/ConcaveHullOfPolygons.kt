/*
 * Copyright (c) 2022 Martin Davis.
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

import org.locationtech.jts.geom.*
import org.locationtech.jts.legacy.pop
import org.locationtech.jts.operation.overlayng.CoverageUnion
import org.locationtech.jts.triangulate.polygon.ConstrainedDelaunayTriangulator
import org.locationtech.jts.triangulate.tri.Tri
import kotlin.jvm.JvmOverloads

/**
 * Constructs a concave hull of a set of polygons, respecting
 * the polygons as constraints.
 * A concave hull is a concave or convex polygon containing all the input polygons,
 * whose vertices are a subset of the vertices in the input.
 * A given set of polygons has a sequence of hulls of increasing concaveness,
 * determined by a numeric target parameter.
 * The computed hull "fills the gap" between the polygons,
 * and does not intersect their interior.
 *
 * The concave hull is constructed by removing the longest outer edges
 * of the constrained Delaunay Triangulation of the space between the polygons,
 * until the target criterion parameter is reached.
 *
 * The target criteria are:
 *
 *  * **Maximum Edge Length** - the length of the longest edge between the polygons is no larger
 * than this value.
 *  * **Maximum Edge Length Ratio** - determine the Maximum Edge Length
 * as a fraction of the difference between the longest and shortest edge lengths
 * between the polygons.
 * This provides a scale-free parameter.
 * A value of 1 produces the convex hull; a value of 0 produces the original polygons.
 *
 * Optionally the concave hull can be allowed to contain holes,
 * via [.setHolesAllowed].
 *
 * The hull can be specified as being "tight", via [.setTight].
 * This causes the result to follow the outer boundaries of the input polygons.
 *
 * Instead of the complete hull, the "fill area" between the input polygons
 * can be computed using [.getFill].
 *
 * The input polygons must form a valid [MultiPolygon]
 * (i.e. they must be non-overlapping and non-edge-adjacent).
 * If needed, a set of possibly-overlapping Polygons
 * can be converted to a valid MultiPolygon
 * by using [Geometry.union];
 *
 * @author Martin Davis
 */
class ConcaveHullOfPolygons(polygons: Geometry?) {
    private val inputPolygons: Geometry
    private var maxEdgeLength = 0.0
    private var maxEdgeLengthRatio = NOT_SPECIFIED.toDouble()
    private var isHolesAllowed = false
    private var isTight = false
    private val geomFactory: GeometryFactory
    private var polygonRings: Array<LinearRing>? = null
    private var hullTris: MutableSet<Tri>? = null
    private var borderTriQue: ArrayDeque<Tri>? = null

    /**
     * Records the edge index of the longest border edge for border tris,
     * so it can be tested for length and possible removal.
     */
    private val borderEdgeMap: MutableMap<Tri, Int> = HashMap<Tri, Int>()

    /**
     * Creates a new instance for a given geometry.
     *
     * @param geom the input geometry
     */
    init {
        if (!(polygons is Polygon || polygons is MultiPolygon)) {
            throw IllegalArgumentException("Input must be polygonal")
        }
        inputPolygons = polygons
        geomFactory = inputPolygons.factory
    }

    /**
     * Sets the target maximum edge length for the concave hull.
     * The length value must be zero or greater.
     *
     *  * The value 0.0 produces the input polygons.
     *  * Larger values produce less concave results.
     * Above a certain large value the result is the convex hull of the input.
     *
     *
     * The edge length ratio provides a scale-free parameter which
     * is intended to produce similar concave results for a variety of inputs.
     *
     * @param edgeLength a non-negative length
     */
    fun setMaximumEdgeLength(edgeLength: Double) {
        if (edgeLength < 0) throw IllegalArgumentException("Edge length must be non-negative")
        maxEdgeLength = edgeLength
        maxEdgeLengthRatio = NOT_SPECIFIED.toDouble()
    }

    /**
     * Sets the target maximum edge length ratio for the concave hull.
     * The edge length ratio is a fraction of the difference
     * between the longest and shortest edge lengths
     * in the Delaunay Triangulation of the area between the input polygons.
     * (Roughly speaking, it is a fraction of the difference between
     * the shortest and longest distances between the input polygons.)
     * It is a value in the range 0 to 1.
     *
     *  * The value 0.0 produces the original input polygons.
     *  * The value 1.0 produces the convex hull.
     *
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
     * Sets whether the boundary of the hull polygon is kept
     * tight to the outer edges of the input polygons.
     *
     * @param isTight true if the boundary is kept tight
     */
    fun setTight(isTight: Boolean) {
        this.isTight = isTight
    }

    /**
     * Gets the computed concave hull.
     *
     * @return the concave hull
     */
    val hull: Geometry
        get() {
            if (inputPolygons.isEmpty) {
                return createEmptyHull()
            }
            buildHullTris()
            return createHullGeometry(hullTris, true)
        }

    /**
     * Gets the concave fill, which is the area between the input polygons,
     * subject to the concaveness control parameter.
     *
     * @return the concave fill
     */
    val fill: Geometry
        get() {
            isTight = true
            if (inputPolygons.isEmpty) {
                return createEmptyHull()
            }
            buildHullTris()
            return createHullGeometry(hullTris, false)
        }

    private fun createEmptyHull(): Geometry {
        return geomFactory.createPolygon()
    }

    private fun buildHullTris() {
        polygonRings = extractShellRings(inputPolygons)
        val frame = createFrame(inputPolygons.envelopeInternal, polygonRings!!, geomFactory)
        val cdt = ConstrainedDelaunayTriangulator(frame)
        val tris: List<Tri> = cdt.triangles!!
        //System.out.println(tris);
        val framePts: Array<Coordinate> = frame.exteriorRing!!.coordinates
        if (maxEdgeLengthRatio >= 0) {
            maxEdgeLength = computeTargetEdgeLength(tris, framePts, maxEdgeLengthRatio)
        }
        hullTris = removeFrameCornerTris(tris, framePts)
        removeBorderTris()
        if (isHolesAllowed) removeHoleTris()
    }

    private fun removeFrameCornerTris(tris: List<Tri>, frameCorners: Array<Coordinate>): MutableSet<Tri> {
        val hullTris: MutableSet<Tri> = HashSet<Tri>()
        borderTriQue = ArrayDeque<Tri>()
        for (tri in tris) {
            val index = vertexIndex(tri, frameCorners)
            val isFrameTri = index != NOT_FOUND
            if (isFrameTri) {
                /**
                 * Frame tris are adjacent to at most one border tri,
                 * which is opposite the frame corner vertex.
                 * Or, the opposite tri may be another frame tri,
                 * which is not added as a border tri.
                 */
                val oppIndex: Int = Tri.oppEdge(index)
                val oppTri: Tri = tri.getAdjacent(oppIndex)!!
                val isBorderTri = oppTri != null && !isFrameTri(oppTri, frameCorners)
                if (isBorderTri) {
                    addBorderTri(tri, oppIndex)
                }
                //-- remove the frame tri
                tri.remove()
            } else {
                hullTris.add(tri)
                //System.out.println(tri);
            }
        }
        return hullTris
    }

    private fun removeBorderTris() {
        while (!borderTriQue!!.isEmpty()) {
            val tri: Tri = borderTriQue!!.pop()!!
            //-- tri might have been removed already
            if (!hullTris!!.contains(tri)) {
                continue
            }
            if (isRemovable(tri)) {
                addBorderTris(tri)
                removeBorderTri(tri)
                //System.out.println(tri);
            }
        }
    }

    private fun removeHoleTris() {
        while (true) {
            val holeTri: Tri = findHoleSeedTri(hullTris) ?: return
            addBorderTris(holeTri)
            removeBorderTri(holeTri)
            removeBorderTris()
        }
    }

    private fun findHoleSeedTri(tris: Set<Tri>?): Tri? {
        for (tri in tris!!) {
            if (isHoleSeedTri(tri)) return tri
        }
        return null
    }

    private fun isHoleSeedTri(tri: Tri): Boolean {
        if (isBorderTri(tri)) return false
        for (i in 0..2) {
            if (tri.hasAdjacent(i)
                && tri.getLength(i) > maxEdgeLength
            ) return true
        }
        return false
    }

    private fun isBorderTri(tri: Tri): Boolean {
        for (i in 0..2) {
            if (!tri.hasAdjacent(i)) return true
        }
        return false
    }

    private fun isRemovable(tri: Tri): Boolean {
        //-- remove non-bridging tris if keeping hull boundary tight
        if (isTight && isTouchingSinglePolygon(tri)) return true

        //-- check if outside edge is longer than threshold
        if (borderEdgeMap.containsKey(tri)) {
            val borderEdgeIndex = borderEdgeMap[tri]!!
            val edgeLen: Double = tri.getLength(borderEdgeIndex)
            if (edgeLen > maxEdgeLength) return true
        }
        return false
    }

    /**
     * Tests whether a triangle touches a single polygon at all vertices.
     * If so, it is a candidate for removal if the hull polygon
     * is being kept tight to the outer boundary of the input polygons.
     * Tris which touch more than one polygon are called "bridging".
     *
     * @param tri
     * @return true if the tri touches a single polygon
     */
    private fun isTouchingSinglePolygon(tri: Tri): Boolean {
        val envTri = envelope(tri)
        for (ring in polygonRings!!) {
            //-- optimization heuristic: a touching tri must be in ring envelope
            if (ring!!.envelopeInternal.intersects(envTri)) {
                if (hasAllVertices(ring, tri)) return true
            }
        }
        return false
    }

    private fun addBorderTris(tri: Tri) {
        addBorderTri(tri, 0)
        addBorderTri(tri, 1)
        addBorderTri(tri, 2)
    }

    /**
     * Adds an adjacent tri to the current border.
     * The adjacent edge is recorded as the border edge for the tri.
     * Note that only edges adjacent to another tri can become border edges.
     * Since constraint-adjacent edges do not have an adjacent tri,
     * they can never be on the border and thus will not be removed
     * due to being shorter than the length threshold.
     * The tri containing them may still be removed via another edge, however.
     *
     * @param tri the tri adjacent to the tri to be added to the border
     * @param index the index of the adjacent tri
     */
    private fun addBorderTri(tri: Tri, index: Int) {
        val adj: Tri = tri.getAdjacent(index) ?: return
        borderTriQue!!.add(adj)
        val borderEdgeIndex: Int = adj.getIndex(tri)
        borderEdgeMap[adj] = borderEdgeIndex
    }

    private fun removeBorderTri(tri: Tri) {
        tri.remove()
        hullTris!!.remove(tri)
        borderEdgeMap.remove(tri)
    }

    private fun createHullGeometry(
        hullTris: Set<Tri>?,
        isIncludeInput: Boolean
    ): Geometry {
        if (!isIncludeInput && hullTris!!.isEmpty()) return createEmptyHull()

        //-- union triangulation
        val triCoverage: Geometry = Tri.toGeometry(hullTris!!, geomFactory)
        //System.out.println(triCoverage);
        val fillGeometry: Geometry = CoverageUnion.union(triCoverage)!!
        if (!isIncludeInput) {
            return fillGeometry
        }
        if (fillGeometry.isEmpty) {
            return inputPolygons.copy()
        }
        //-- union with input polygons
        val geoms =
            arrayOf<Geometry>(fillGeometry, inputPolygons)
        val geomColl = geomFactory.createGeometryCollection(geoms)
        return CoverageUnion.union(geomColl)!!
    }

    companion object {
        /**
         * Computes a concave hull of set of polygons
         * using the target criterion of maximum edge length,
         * and allowing control over whether the hull boundary is tight
         * and can contain holes.
         *
         * @param polygons the input polygons
         * @param maxLength the target maximum edge length
         * @param isTight true if the hull should be tight to the outside of the polygons
         * @param isHolesAllowed true if holes are allowed in the hull polygon
         * @return the concave hull
         */
        /**
         * Computes a concave hull of set of polygons
         * using the target criterion of maximum edge length.
         *
         * @param polygons the input polygons
         * @param maxLength the target maximum edge length
         * @return the concave hull
         */
        @JvmOverloads
        fun concaveHullByLength(
            polygons: Geometry?, maxLength: Double,
            isTight: Boolean = false, isHolesAllowed: Boolean = false
        ): Geometry {
            val hull = ConcaveHullOfPolygons(polygons)
            hull.setMaximumEdgeLength(maxLength)
            hull.setHolesAllowed(isHolesAllowed)
            hull.setTight(isTight)
            return hull.hull
        }
        /**
         * Computes a concave hull of set of polygons
         * using the target criterion of maximum edge length ratio,
         * and allowing control over whether the hull boundary is tight
         * and can contain holes.
         *
         * @param polygons the input polygons
         * @param lengthRatio the target maximum edge length ratio
         * @param isTight true if the hull should be tight to the outside of the polygons
         * @param isHolesAllowed true if holes are allowed in the hull polygon
         * @return the concave hull
         */
        /**
         * Computes a concave hull of set of polygons
         * using the target criterion of maximum edge length ratio.
         *
         * @param polygons the input polygons
         * @param lengthRatio the target maximum edge length ratio
         * @return the concave hull
         */
        @JvmOverloads
        fun concaveHullByLengthRatio(
            polygons: Geometry?, lengthRatio: Double,
            isTight: Boolean = false, isHolesAllowed: Boolean = false
        ): Geometry {
            val hull = ConcaveHullOfPolygons(polygons)
            hull.setMaximumEdgeLengthRatio(lengthRatio)
            hull.setHolesAllowed(isHolesAllowed)
            hull.setTight(isTight)
            return hull.hull
        }

        /**
         * Computes a concave fill area between a set of polygons,
         * using the target criterion of maximum edge length.
         *
         * @param polygons the input polygons
         * @param maxLength the target maximum edge length
         * @return the concave fill
         */
        fun concaveFillByLength(polygons: Geometry?, maxLength: Double): Geometry {
            val hull = ConcaveHullOfPolygons(polygons)
            hull.setMaximumEdgeLength(maxLength)
            return hull.fill
        }

        /**
         * Computes a concave fill area between a set of polygons,
         * using the target criterion of maximum edge length ratio.
         *
         * @param polygons the input polygons
         * @param lengthRatio the target maximum edge length ratio
         * @return the concave fill
         */
        fun concaveFillByLengthRatio(polygons: Geometry?, lengthRatio: Double): Geometry {
            val hull = ConcaveHullOfPolygons(polygons)
            hull.setMaximumEdgeLengthRatio(lengthRatio)
            return hull.fill
        }

        private const val FRAME_EXPAND_FACTOR = 4
        private const val NOT_SPECIFIED = -1
        private const val NOT_FOUND = -1
        private fun computeTargetEdgeLength(
            triList: List<Tri>,
            frameCorners: Array<Coordinate>,
            edgeLengthRatio: Double
        ): Double {
            if (edgeLengthRatio == 0.0) return 0.0
            var maxEdgeLen = -1.0
            var minEdgeLen = -1.0
            for (tri in triList) {
                //-- don't include frame triangles
                if (isFrameTri(tri, frameCorners)) continue
                for (i in 0..2) {
                    //-- constraint edges are not used to determine ratio
                    if (!tri.hasAdjacent(i)) continue
                    val len: Double = tri.getLength(i)
                    if (len > maxEdgeLen) maxEdgeLen = len
                    if (minEdgeLen < 0 || len < minEdgeLen) minEdgeLen = len
                }
            }
            //-- if ratio = 1 ensure all edges are included
            return if (edgeLengthRatio == 1.0) 2 * maxEdgeLen else edgeLengthRatio * (maxEdgeLen - minEdgeLen) + minEdgeLen
        }

        private fun isFrameTri(
            tri: Tri,
            frameCorners: Array<Coordinate>
        ): Boolean {
            val index =
                vertexIndex(tri, frameCorners)
            return index >= 0
        }

        /**
         * Get the tri vertex index of some point in a list,
         * or -1 if none are vertices.
         *
         * @param tri the tri to test for containing a point
         * @param pts the points to test
         * @return the vertex index of a point, or -1
         */
        private fun vertexIndex(tri: Tri, pts: Array<Coordinate>): Int {
            for (p in pts) {
                val index: Int = tri.getIndex(p)
                if (index >= 0) return index
            }
            return NOT_FOUND
        }

        private fun hasAllVertices(ring: LinearRing, tri: Tri): Boolean {
            for (i in 0..2) {
                val v: Coordinate = tri.getCoordinate(i)
                if (!hasVertex(ring, v)) {
                    return false
                }
            }
            return true
        }

        private fun hasVertex(ring: LinearRing, v: Coordinate): Boolean {
            for (i in 1 until ring.numPoints) {
                if (v.equals2D(ring.getCoordinateN(i))) {
                    return true
                }
            }
            return false
        }

        private fun envelope(tri: Tri): Envelope {
            val env = Envelope(tri.getCoordinate(0), tri.getCoordinate(1))
            env.expandToInclude(tri.getCoordinate(2))
            return env
        }

        /**
         * Creates a rectangular "frame" around the input polygons,
         * with the input polygons as holes in it.
         * The frame is large enough that the constrained Delaunay triangulation
         * of it should contain the convex hull of the input as edges.
         * The frame corner triangles can be removed to produce a
         * triangulation of the space around and between the input polygons.
         *
         * @param polygonsEnv
         * @param polygonRings
         * @param geomFactory
         * @return the frame polygon
         */
        private fun createFrame(
            polygonsEnv: Envelope,
            polygonRings: Array<LinearRing>,
            geomFactory: GeometryFactory
        ): Polygon {
            val diam = polygonsEnv.diameter
            val envFrame = polygonsEnv.copy()
            envFrame.expandBy(FRAME_EXPAND_FACTOR * diam)
            val frameOuter =
                geomFactory.toGeometry(envFrame) as Polygon
            val shell =
                frameOuter.exteriorRing!!.copy() as LinearRing
            return geomFactory.createPolygon(shell, polygonRings)
        }

        private fun extractShellRings(polygons: Geometry): Array<LinearRing> {
            val rings = arrayOfNulls<LinearRing>(polygons.numGeometries)
            for (i in 0 until polygons.numGeometries) {
                val consPoly = polygons.getGeometryN(i) as Polygon
                rings[i] = consPoly.exteriorRing!!.copy() as LinearRing
            }
            return rings.requireNoNulls()
        }
    }
}