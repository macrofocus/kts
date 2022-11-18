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

import org.locationtech.jts.geom.*
import org.locationtech.jts.operation.overlayng.CoverageUnion
import org.locationtech.jts.triangulate.DelaunayTriangulationBuilder
import org.locationtech.jts.triangulate.quadedge.QuadEdge
import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision
import org.locationtech.jts.triangulate.quadedge.TriangleVisitor
import org.locationtech.jts.triangulate.tri.Tri
import org.locationtech.jts.triangulate.tri.TriangulationBuilder
import org.locationtech.jts.util.Assert

/**
 * Functions to operate on triangulations represented as
 * lists of [HullTri]s.
 *
 * @author mdavis
 */
internal object HullTriangulation {
    fun createDelaunayTriangulation(geom: Geometry?): MutableList<HullTri> {
        //TODO: implement a DT on Tris directly?
        val dt = DelaunayTriangulationBuilder()
        dt.setSites(geom)
        val subdiv: QuadEdgeSubdivision = dt.subdivision!!
        return toTris(subdiv)
    }

    private fun toTris(subdiv: QuadEdgeSubdivision): MutableList<HullTri> {
        val visitor = HullTriVisitor()
        subdiv.visitTriangles(visitor, false)
        val triList: MutableList<HullTri> = visitor.triangles
        TriangulationBuilder.build(triList)
        return triList
    }

    /**
     * Creates a polygonal geometry representing the area of a triangulation
     * which may be disconnected or contain holes.
     *
     * @param triList the triangulation
     * @param geomFactory the geometry factory to use
     * @return the area polygonal geometry
     */
    fun union(triList: List<Tri?>, geomFactory: GeometryFactory): Geometry {
        val polys: MutableList<Polygon> = ArrayList<Polygon>()
        for (tri in triList) {
            val poly: Polygon = tri!!.toPolygon(geomFactory)
            polys.add(poly)
        }
        return CoverageUnion.union(geomFactory.buildGeometry(polys))!!
    }

    /**
     * Creates a Polygon representing the area of a triangulation
     * which is connected and contains no holes.
     *
     * @param triList the triangulation
     * @param geomFactory the geometry factory to use
     * @return the area polygon
     */
    fun traceBoundaryPolygon(
        triList: List<HullTri>,
        geomFactory: GeometryFactory
    ): Geometry {
        if (triList.size == 1) {
            val tri: Tri = triList[0]
            return tri.toPolygon(geomFactory)
        }
        val pts = traceBoundary(triList)
        return geomFactory.createPolygon(pts)
    }

    /**
     * Extracts the coordinates of the edges along the boundary of a triangulation,
     * by tracing CW around the border triangles.
     * Assumption: there are at least 2 tris, they are connected,
     * and there are no holes.
     * So each tri has at least one non-boundary edge, and there is only one boundary.
     *
     * @param triList the triangulation
     * @return the points in the boundary of the triangulation
     */
    private fun traceBoundary(triList: List<HullTri>): Array<Coordinate> {
        val triStart: HullTri? = findBorderTri(triList)
        val coordList = CoordinateList()
        var tri: HullTri? = triStart
        do {
            var boundaryIndex: Int = tri!!.boundaryIndexCCW()
            //-- add border vertex
            coordList.add(tri.getCoordinate(boundaryIndex).copy(), false)
            val nextIndex: Int = Tri.next(boundaryIndex)
            //-- if next edge is also on boundary, add it and move to next
            if (tri.isBoundary(nextIndex)) {
                coordList.add(tri.getCoordinate(nextIndex).copy(), false)
                boundaryIndex = nextIndex
            }
            //-- find next border tri CCW around non-boundary edge
            tri = nextBorderTri(tri)
        } while (tri !== triStart)
        coordList.closeRing()
        return coordList.toCoordinateArray()
    }

    private fun findBorderTri(triList: List<HullTri>): HullTri? {
        for (tri in triList) {
            if (tri.isBorder) return tri
        }
        Assert.shouldNeverReachHere("No border triangles found")
        return null
    }

    fun nextBorderTri(triStart: HullTri?): HullTri? {
        var tri: HullTri? = triStart
        //-- start at first non-border edge CW
        var index: Int = Tri.next(tri!!.boundaryIndexCW())
        //-- scan CCW around vertex for next border tri
        do {
            val adjTri: HullTri =
                tri!!.getAdjacent(index) as HullTri
            if (adjTri === tri) throw IllegalStateException("No outgoing border edge found")
            index = Tri.next(adjTri.getIndex(tri))
            tri = adjTri
        } while (!tri!!.isBoundary(index))
        return tri
    }

    private class HullTriVisitor : TriangleVisitor {
        private val triList: MutableList<HullTri> =
            ArrayList<HullTri>()

        override fun visit(triEdges: Array<QuadEdge>) {
            val p0: Coordinate = triEdges[0].orig()!!.coordinate
            val p1: Coordinate = triEdges[1].orig()!!.coordinate
            val p2: Coordinate = triEdges[2].orig()!!.coordinate
            val tri: HullTri
            if (Triangle.isCCW(p0, p2, p1)) {
                tri = HullTri(p0, p2, p1)
            } else {
                tri = HullTri(p0, p1, p2)
            }
            triList.add(tri)
        }

        val triangles: MutableList<HullTri>
            get() = triList
    }
}