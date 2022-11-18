/*
 * Copyright (c) 2016 Vivid Solutions.
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
package org.locationtech.jts.triangulate.quadedge

import org.locationtech.jts.algorithm.PointLocation.isInRing
import org.locationtech.jts.geom.*

/**
 * Models a triangle formed from [QuadEdge]s in a [QuadEdgeSubdivision]
 * which forms a triangulation. The class provides methods to access the
 * topological and geometric properties of the triangle and its neighbours in
 * the triangulation. Triangle vertices are ordered in CCW orientation in the
 * structure.
 *
 *
 * QuadEdgeTriangles support having an external data attribute attached to them.
 * Alternatively, this class can be subclassed and attributes can
 * be defined in the subclass.  Subclasses will need to define
 * their own <tt>BuilderVisitor</tt> class
 * and <tt>createOn</tt> method.
 *
 * @author Martin Davis
 * @version 1.0
 */
class QuadEdgeTriangle(edge: Array<QuadEdge>) {
    private var edge: Array<QuadEdge>?
    /**
     * Gets the external data value for this triangle.
     *
     * @return the data object
     */
    /**
     * Sets the external data value for this triangle.
     *
     * @param data an object containing external data
     */
    var data: Any? = null

    /**
     * Creates a new triangle from the given edges.
     *
     * @param edge an array of the edges of the triangle in CCW order
     */
    init {
        this.edge = edge.copyOf()
        // link the quadedges back to this triangle
        for (i in 0..2) {
            edge[i].data = this
        }
    }

    fun kill() {
        edge = null
    }

    val isLive: Boolean
        get() = edge != null
    val edges: Array<QuadEdge>?
        get() = edge

    fun getEdge(i: Int): QuadEdge {
        return edge!![i]
    }

    fun getVertex(i: Int): Vertex {
        return edge!![i].orig()!!
    }

    /**
     * Gets the vertices for this triangle.
     *
     * @return a new array containing the triangle vertices
     */
    val vertices: Array<Vertex?>
        get() {
            val vert = arrayOfNulls<Vertex>(3)
            for (i in 0..2) {
                vert[i] = getVertex(i)
            }
            return vert
        }

    fun getCoordinate(i: Int): Coordinate {
        return edge!![i].orig()!!.coordinate
    }

    /**
     * Gets the index for the given edge of this triangle
     *
     * @param e
     * a QuadEdge
     * @return the index of the edge in this triangle
     * or -1 if the edge is not an edge of this triangle
     */
    fun getEdgeIndex(e: QuadEdge): Int {
        for (i in 0..2) {
            if (edge!![i] === e) return i
        }
        return -1
    }

    /**
     * Gets the index for the edge that starts at vertex v.
     *
     * @param v
     * the vertex to find the edge for
     * @return the index of the edge starting at the vertex
     * or -1 if the vertex is not in the triangle
     */
    fun getEdgeIndex(v: Vertex): Int {
        for (i in 0..2) {
            if (edge!![i].orig() == v) return i
        }
        return -1
    }

    fun getEdgeSegment(i: Int, seg: LineSegment) {
        seg.p0 = edge!![i].orig()!!.coordinate
        val nexti = (i + 1) % 3
        seg.p1 = edge!![nexti].orig()!!.coordinate
    }

    val coordinates: Array<Coordinate?>
        get() {
            val pts = arrayOfNulls<Coordinate>(4)
            for (i in 0..2) {
                pts[i] = edge!![i].orig()!!.coordinate
            }
            pts[3] = Coordinate(pts[0]!!)
            return pts
        }

    operator fun contains(pt: Coordinate?): Boolean {
        val ring = coordinates
        return isInRing(pt!!, ring.requireNoNulls())
    }

    fun getGeometry(fact: GeometryFactory): Polygon {
        val ring: LinearRing = fact.createLinearRing(coordinates.requireNoNulls())
        return fact.createPolygon(ring)
    }

    override fun toString(): String {
        return getGeometry(GeometryFactory()).toString()
    }

    /**
     * Tests whether this triangle is adjacent to the outside of the subdivision.
     *
     * @return true if the triangle is adjacent to the subdivision exterior
     */
    val isBorder: Boolean
        get() {
            for (i in 0..2) {
                if (getAdjacentTriangleAcrossEdge(i) == null) return true
            }
            return false
        }

    fun isBorder(i: Int): Boolean {
        return getAdjacentTriangleAcrossEdge(i) == null
    }

    fun getAdjacentTriangleAcrossEdge(edgeIndex: Int): QuadEdgeTriangle? {
        return getEdge(edgeIndex).sym()!!.data as QuadEdgeTriangle?
    }

    fun getAdjacentTriangleEdgeIndex(i: Int): Int {
        return getAdjacentTriangleAcrossEdge(i)!!.getEdgeIndex(getEdge(i).sym()!!)
    }

    /**
     * Gets the triangles which are adjacent (include) to a
     * given vertex of this triangle.
     *
     * @param vertexIndex the vertex to query
     * @return a list of the vertex-adjacent triangles
     */
    fun getTrianglesAdjacentToVertex(vertexIndex: Int): MutableList<QuadEdgeTriangle> {
        // Assert: isVertex
        val adjTris: MutableList<QuadEdgeTriangle> = ArrayList()
        val start: QuadEdge = getEdge(vertexIndex)
        var qe: QuadEdge = start
        do {
            val adjTri = qe.data as QuadEdgeTriangle
            if (adjTri != null) {
                adjTris.add(adjTri)
            }
            qe = qe.oNext()!!
        } while (qe !== start)
        return adjTris
    }

    /**
     * Gets the neighbours of this triangle. If there is no neighbour triangle,
     * the array element is `null`
     *
     * @return an array containing the 3 neighbours of this triangle
     */
    val neighbours: Array<QuadEdgeTriangle?>
        get() {
            val neigh = arrayOfNulls<QuadEdgeTriangle>(3)
            for (i in 0..2) {
                neigh[i] = getEdge(i).sym()!!.data as QuadEdgeTriangle
            }
            return neigh
        }

    private class QuadEdgeTriangleBuilderVisitor : TriangleVisitor {
        private val triangles: MutableList<QuadEdgeTriangle> = ArrayList()
        override fun visit(edges: Array<QuadEdge>) {
            triangles.add(QuadEdgeTriangle(edges))
        }

        fun getTriangles(): MutableList<QuadEdgeTriangle> {
            return triangles
        }
    }

    companion object {
        /**
         * Creates [QuadEdgeTriangle]s for all facets of a
         * [QuadEdgeSubdivision] representing a triangulation.
         * The <tt>data</tt> attributes of the [QuadEdge]s in the subdivision
         * will be set to point to the triangle which contains that edge.
         * This allows tracing the neighbour triangles of any given triangle.
         *
         * @param subdiv
         * the QuadEdgeSubdivision to create the triangles on.
         * @return a List of the created QuadEdgeTriangles
         */
        fun createOn(subdiv: QuadEdgeSubdivision): MutableList<QuadEdgeTriangle> {
            val visitor = QuadEdgeTriangleBuilderVisitor()
            subdiv.visitTriangles(visitor, false)
            return visitor.getTriangles()
        }

        /**
         * Tests whether the point pt is contained in the triangle defined by 3
         * [Vertex]es.
         *
         * @param tri
         * an array containing at least 3 Vertexes
         * @param pt
         * the point to test
         * @return true if the point is contained in the triangle
         */
        fun contains(tri: Array<Vertex>, pt: Coordinate?): Boolean {
            val ring = arrayOf(
                tri[0].coordinate,
                tri[1].coordinate, tri[2].coordinate, tri[0].coordinate
            )
            return isInRing(pt!!, ring)
        }

        /**
         * Tests whether the point pt is contained in the triangle defined by 3
         * [QuadEdge]es.
         *
         * @param tri
         * an array containing at least 3 QuadEdges
         * @param pt
         * the point to test
         * @return true if the point is contained in the triangle
         */
        fun contains(tri: Array<QuadEdge>, pt: Coordinate?): Boolean {
            val ring = arrayOf(
                tri[0].orig()!!.coordinate,
                tri[1].orig()!!.coordinate, tri[2].orig()!!.coordinate,
                tri[0].orig()!!.coordinate
            )
            return isInRing(pt!!, ring)
        }

        fun toPolygon(v: Array<Vertex>): Geometry {
            val ringPts =
                arrayOf(
                    v[0].coordinate,
                    v[1].coordinate, v[2].coordinate, v[0].coordinate
                )
            val fact = GeometryFactory()
            val ring = fact.createLinearRing(ringPts)
            return fact.createPolygon(ring)
        }

        fun toPolygon(e: Array<QuadEdge>): Geometry {
            val ringPts =
                arrayOf(
                    e[0].orig()!!.coordinate,
                    e[1].orig()!!.coordinate, e[2].orig()!!.coordinate,
                    e[0].orig()!!.coordinate
                )
            val fact = GeometryFactory()
            val ring = fact.createLinearRing(ringPts)
            return fact.createPolygon(ring)
        }

        /**
         * Finds the next index around the triangle. Index may be an edge or vertex
         * index.
         *
         * @param index
         * @return the next index
         */
        fun nextIndex(index: Int): Int {
            var index = index
            return (index + 1) % 3.also { index = it }
        }
    }
}