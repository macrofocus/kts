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

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.CoordinateArrays
import org.locationtech.jts.geom.CoordinateList
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.LineSegment
import org.locationtech.jts.geom.LinearRing
import org.locationtech.jts.geom.MultiPolygon
import org.locationtech.jts.geom.Polygon
import kotlin.jvm.JvmStatic

/**
 * Models a polygonal coverage as a set of unique [CoverageEdge]s,
 * linked to the parent rings in the coverage polygons.
 * Each edge has either one or two parent rings, depending on whether
 * it is an inner or outer edge of the coverage.
 * The source coverage is represented as a array of polygonal geometries
 * (either [Polygon]s or [MultiPolygon]s).
 *
 * Coverage edges are found by identifying vertices which are nodes in the coverage,
 * splitting edges at nodes, and then identifying unique edges.
 * The unique edges are associated to their parent ring (in order),
 * to allow reforming the coverage polygons.
 *
 * @author Martin Davis
 */
class CoverageRingEdges(private val coverage: Array<Geometry>) {
    private val ringEdgesMap: MutableMap<LinearRing, List<CoverageEdge>> = HashMap()
    val edges: MutableList<CoverageEdge> = ArrayList()

    init {
        build()
    }

    private fun build() {
        val nodes = findMultiRingNodes(coverage)
        val boundarySegs = CoverageBoundarySegmentFinder.findBoundarySegments(coverage)
        nodes.addAll(findBoundaryNodes(boundarySegs))
        val uniqueEdgeMap = HashMap<LineSegment, CoverageEdge>()
        for (i in coverage.indices) {
            //-- geom is a Polygon or MultiPolygon
            val geom = coverage[i]
            val indexLargest = findLargestPolygonIndex(geom)
            for (ipoly in 0 until geom.numGeometries) {
                val poly = geom.getGeometryN(ipoly) as Polygon

                //-- skip empty elements. Missing elements are copied in result
                if (poly.isEmpty) continue

                //-- largest polygon is the primary one, which is never removed
                val isPrimary = ipoly == indexLargest

                //-- extract shell
                val shell = poly.exteriorRing!!
                addRingEdges(i, shell, isPrimary, nodes, boundarySegs, uniqueEdgeMap)
                //-- extract holes
                for (ihole in 0 until poly.getNumInteriorRing()) {
                    val hole = poly.getInteriorRingN(ihole)
                    //-- skip empty holes. Missing rings are copied in result
                    if (hole.isEmpty) continue
                    //-- holes are never primary
                    addRingEdges(i, hole, false, nodes, boundarySegs, uniqueEdgeMap)
                }
            }
        }
    }

    private fun findLargestPolygonIndex(geom: Geometry): Int {
        if (geom is Polygon) return 0
        var indexLargest = -1
        var areaLargest = -1.0
        for (ipoly in 0 until geom.numGeometries) {
            val poly = geom.getGeometryN(ipoly) as Polygon
            val area = poly.area
            if (area > areaLargest) {
                areaLargest = area
                indexLargest = ipoly
            }
        }
        return indexLargest
    }

    private fun addRingEdges(
        index: Int, ring: LinearRing, isPrimary: Boolean, nodes: MutableSet<Coordinate>, boundarySegs: Set<LineSegment>,
        uniqueEdgeMap: HashMap<LineSegment, CoverageEdge>
    ) {
        addBoundaryInnerNodes(ring, boundarySegs, nodes)
        val ringEdges = extractRingEdges(index, ring, isPrimary, uniqueEdgeMap, nodes)
        if (ringEdges != null) ringEdgesMap[ring] = ringEdges
    }

    /**
     * Detects nodes occurring at vertices which are between a boundary segment
     * and an inner (shared) segment.
     * These occur where two polygons are adjacent at the coverage boundary
     * (this is not detected by [findMultiRingNodes]).
     *
     * @param ring
     * @param boundarySegs
     * @param nodes
     */
    private fun addBoundaryInnerNodes(ring: LinearRing, boundarySegs: Set<LineSegment>, nodes: MutableSet<Coordinate>) {
        val seq = ring.coordinateSequence!!
        val isBdyLast = CoverageBoundarySegmentFinder.isBoundarySegment(boundarySegs, seq, seq.size() - 2)
        var isBdyPrev = isBdyLast
        for (i in 0 until seq.size() - 1) {
            val isBdy = CoverageBoundarySegmentFinder.isBoundarySegment(boundarySegs, seq, i)
            if (isBdy != isBdyPrev) {
                val nodePt = seq.getCoordinate(i)
                nodes.add(nodePt)
            }
            isBdyPrev = isBdy
        }
    }

    /**
     * Extracts the [CoverageEdge]s for a ring.
     * @param index
     *
     * @param ring
     * @param isRetained true if the ring is retained (must not be removed)
     * @param uniqueEdgeMap
     * @param nodes
     * @return null if the ring has too few distinct vertices
     */
    private fun extractRingEdges(
        index: Int, ring: LinearRing,
        isPrimary: Boolean, uniqueEdgeMap: HashMap<LineSegment, CoverageEdge>,
        nodes: Set<Coordinate>
    ): List<CoverageEdge>? {
        // System.out.println(ring);
        val ringEdges: MutableList<CoverageEdge> = ArrayList()

        var pts = ring.coordinates
        pts = CoordinateArrays.removeRepeatedPoints(pts)
        //-- if compacted ring is too short, don't process it
        if (pts.size < 3) return null

        val first = findNextNodeIndex(pts, -1, nodes)
        if (first < 0) {
            //-- ring does not contain a node, so edge is entire ring
            val edge = createEdge(pts, -1, -1, index, isPrimary, uniqueEdgeMap)
            ringEdges.add(edge)
        } else {
            var start = first
            var end = start
            //-- two-node edges are always primary
            var isEdgePrimary = true
            do {
                end = findNextNodeIndex(pts, start, nodes)
                //-- a single-node ring is only retained if specified
                if (end == start) {
                    isEdgePrimary = isPrimary
                }
                val edge = createEdge(pts, start, end, index, isEdgePrimary, uniqueEdgeMap)
                //  System.out.println(ringEdges.size() + " : " + edge);
                ringEdges.add(edge)
                start = end
            } while (end != first)
        }
        return ringEdges
    }

    /**
     * Creates or updates an edge for the given ring or ring section.
     *
     * @param ring ring to create edge for
     * @param start start index of ring section; -1 indicates edge is entire ring
     * @param end end index of ring section
     * @param index
     * @param isPrimary whether this ring is a primary ring
     * @param uniqueEdgeMap map of edges
     * @return the CoverageEdge for the ring or portion of ring
     */
    private fun createEdge(
        ring: Array<Coordinate>, start: Int, end: Int, index: Int, isPrimary: Boolean,
        uniqueEdgeMap: HashMap<LineSegment, CoverageEdge>
    ): CoverageEdge {
        val edge: CoverageEdge
        val edgeKey = if (end == start) CoverageEdge.key(ring) else CoverageEdge.key(ring, start, end)
        val existing = uniqueEdgeMap[edgeKey]
        if (existing != null) {
            edge = existing
            //-- update shared attributes
            edge.setPrimary(isPrimary)
        } else {
            edge = if (start < 0) {
                CoverageEdge.createEdge(ring, isPrimary)
            } else {
                CoverageEdge.createEdge(ring, start, end, isPrimary)
            }
            uniqueEdgeMap[edgeKey] = edge
            edges.add(edge)
        }
        edge.addIndex(index)
        edge.incRingCount()
        return edge
    }

    private fun findNextNodeIndex(ring: Array<Coordinate>, start: Int, nodes: Set<Coordinate>): Int {
        var index = start
        var isScanned0 = false
        do {
            index = next(index, ring)
            if (index == 0) {
                if (start < 0 && isScanned0) return -1
                isScanned0 = true
            }
            val pt = ring[index]
            if (nodes.contains(pt)) {
                return index
            }
        } while (index != start)
        return -1
    }

    /**
     * Finds nodes in a coverage at vertices which are shared by 3 or more rings.
     *
     * @param coverage a list of polygonal geometries
     * @return the set of nodes contained in 3 or more rings
     */
    private fun findMultiRingNodes(coverage: Array<Geometry>): MutableSet<Coordinate> {
        val vertexRingCount = VertexRingCounter.count(coverage)
        val nodes: MutableSet<Coordinate> = HashSet()
        for ((v, count) in vertexRingCount) {
            if (count >= 3) {
                nodes.add(v)
            }
        }
        return nodes
    }

    /**
     * Finds nodes occurring between boundary segments.
     * Nodes on boundaries occur at vertices which have
     * 3 or more incident boundary segments.
     * This detects situations where two rings touch only at a vertex
     * (i.e. two polygons touch, or a polygon shell touches a hole)
     * These nodes lie in only 2 adjacent rings,
     * so are not detected by [findMultiRingNodes].
     *
     * @param boundarySegments
     * @return a set of vertices which are nodes where two rings touch
     */
    private fun findBoundaryNodes(boundarySegments: Set<LineSegment>): Set<Coordinate> {
        val counter: MutableMap<Coordinate, Int> = HashMap()
        for (seg in boundarySegments) {
            counter[seg.p0] = (counter[seg.p0] ?: 0) + 1
            counter[seg.p1] = (counter[seg.p1] ?: 0) + 1
        }
        return counter.entries
            .filter { e -> e.value > 2 }
            .map { it.key }.toSet()
    }

    /**
     * Recreates the polygon coverage from the current edge values.
     *
     * @return an array of polygonal geometries representing the coverage
     */
    fun buildCoverage(): Array<Geometry?> {
        return Array(coverage.size) { i -> buildPolygonal(coverage[i]) }
    }

    private fun buildPolygonal(geom: Geometry): Geometry? {
        return if (geom is MultiPolygon) {
            buildMultiPolygon(geom)
        } else {
            buildPolygon(geom as Polygon)
        }
    }

    private fun buildMultiPolygon(geom: MultiPolygon): Geometry {
        val polyList: MutableList<Polygon> = ArrayList()
        for (i in 0 until geom.numGeometries) {
            val poly = buildPolygon(geom.getGeometryN(i) as Polygon)
            if (poly != null) {
                polyList.add(poly)
            }
        }
        if (polyList.size == 1) {
            return polyList[0]
        }
        val polys = polyList.toTypedArray()
        return geom.factory.createMultiPolygon(polys)
    }

    /**
     *
     * @param polygon
     * @return null if the polygon has been removed
     */
    private fun buildPolygon(polygon: Polygon): Polygon? {
        val shell = buildRing(polygon.exteriorRing!!) ?: return null
        if (polygon.getNumInteriorRing() == 0) {
            return polygon.factory.createPolygon(shell)
        }
        val holeList: MutableList<LinearRing> = ArrayList()
        for (i in 0 until polygon.getNumInteriorRing()) {
            val hole = polygon.getInteriorRingN(i)
            val newHole = buildRing(hole)
            if (newHole != null) {
                holeList.add(newHole)
            }
        }
        //LinearRing holes[] = new LinearRing[polygon.getNumInteriorRing()];
        val holes = holeList.toTypedArray()
        return polygon.factory.createPolygon(shell, holes)
    }

    private fun buildRing(ring: LinearRing): LinearRing? {
        //-- if ring is not in map, must have been invalid.  Just copy original
        val ringEdges = ringEdgesMap[ring] ?: return ring.copy() as LinearRing

        val isRemoved = ringEdges.size == 1
                && ringEdges[0].coordinates.isEmpty()
        if (isRemoved) return null

        val ptsList = CoordinateList()
        for (i in ringEdges.indices) {
            val lastPt = if (ptsList.size > 0)
                ptsList.getCoordinate(ptsList.size - 1)
            else
                null
            val dir = isEdgeDirForward(ringEdges, i, lastPt)
            ptsList.add(ringEdges[i].coordinates, false, dir)
        }
        val pts = ptsList.toCoordinateArray()
        return ring.factory.createLinearRing(pts)
    }

    private fun isEdgeDirForward(ringEdges: List<CoverageEdge>, index: Int, prevPt: Coordinate?): Boolean {
        val size = ringEdges.size
        if (size <= 1) return true
        if (index == 0) {
            //-- if only 2 edges, first one can keep orientation
            if (size == 2) return true
            val endPt0 = ringEdges[0].endCoordinate
            return endPt0.equals2D(ringEdges[1].startCoordinate)
                    || endPt0.equals2D(ringEdges[1].endCoordinate)
        }
        //-- previous point determines required orientation
        return prevPt!!.equals2D(ringEdges[index].startCoordinate)
    }

    companion object {
        /**
         * Create a new instance for a given coverage.
         *
         * @param coverage the set of polygonal geometries in the coverage
         * @return the edges of the coverage
         */
        @JvmStatic
        fun create(coverage: Array<Geometry>): CoverageRingEdges {
            return CoverageRingEdges(coverage)
        }

        private fun next(index: Int, ring: Array<Coordinate>): Int {
            var index = index
            index = index + 1
            if (index >= ring.size - 1) index = 0
            return index
        }
    }
}
