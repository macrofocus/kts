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
package org.locationtech.jts.triangulate.tri

import org.locationtech.jts.algorithm.Orientation
import org.locationtech.jts.algorithm.RobustLineIntersector
import org.locationtech.jts.geom.*
import org.locationtech.jts.io.WKTWriter
import org.locationtech.jts.util.Assert

/**
 * A memory-efficient representation of a triangle in a triangulation.
 * Contains three vertices, and links to adjacent Tris for each edge.
 * Tris are constructed independently, and if needed linked
 * into a triangulation using [TriangulationBuilder].
 *
 * An edge of a Tri in a triangulation is called a boundary edge
 * if it has no adjacent triangle.
 * The set of Tris containing boundary edges are called the triangulation border.
 *
 * @author Martin Davis
 */
open class Tri
/**
* Creates a triangle with the given vertices.
* The vertices should be oriented clockwise.
*
* @param p0 the first triangle vertex
* @param p1 the second triangle vertex
* @param p2 the third triangle vertex
*/ constructor (
var p0: Coordinate, protected var p1: Coordinate, protected var p2: Coordinate
//Assert.isTrue( Orientation.CLOCKWISE != Orientation.index(p0, p1, p2), "Tri is not oriented correctly");
)
{
    /**
     * triN is the adjacent triangle across the edge pN - pNN.
     * pNN is the next vertex CW from pN.
     */
    var tri0: Tri? = null
    var tri1: Tri? = null
    var tri2: Tri? = null

    /**
     * Sets the adjacent triangles.
     * The vertices of the adjacent triangles are
     * assumed to match the appropriate vertices in this triangle.
     *
     * @param tri0 the triangle adjacent to edge 0
     * @param tri1 the triangle adjacent to edge 1
     * @param tri2 the triangle adjacent to edge 2
     */
    fun setAdjacent(tri0: Tri?, tri1: Tri?, tri2: Tri?) {
        this.tri0 = tri0
        this.tri1 = tri1
        this.tri2 = tri2
    }

    /**
     * Sets the triangle adjacent to the edge originating
     * at a given vertex.
     * The vertices of the adjacent triangles are
     * assumed to match the appropriate vertices in this triangle.
     *
     * @param pt the edge start point
     * @param tri the adjacent triangle
     */
    fun setAdjacent(pt: Coordinate?, tri: Tri?) {
        val index: Int = getIndex(pt)
        setTri(index, tri)
        // TODO: validate that tri is adjacent at the edge specified
    }

    /**
     * Sets the triangle adjacent to an edge.
     * The vertices of the adjacent triangle are
     * assumed to match the appropriate vertices in this triangle.
     *
     * @param edgeIndex the edge triangle is adjacent to
     * @param tri the adjacent triangle
     */
    fun setTri(edgeIndex: Int, tri: Tri?) {
        when (edgeIndex) {
            0 -> {
                tri0 = tri
                return
            }

            1 -> {
                tri1 = tri
                return
            }

            2 -> {
                tri2 = tri
                return
            }
        }
        Assert.shouldNeverReachHere()
    }

    fun setCoordinates(p0: Coordinate, p1: Coordinate, p2: Coordinate) {
        this.p0 = p0
        this.p1 = p1
        this.p2 = p2
        //Assert.isTrue( Orientation.CLOCKWISE != Orientation.index(p0, p1, p2), "Tri is not oriented correctly");
    }

    /**
     * Spits a triangle by a point located inside the triangle.
     * Creates the three new resulting triangles with adjacent links
     * set correctly.
     * Returns the new triangle whose 0'th vertex is the splitting point.
     *
     * @param p the point to insert
     * @return the new triangle whose 0'th vertex is p
     */
    fun split(p: Coordinate): Tri {
        val tt0 = Tri(p, p0, p1)
        val tt1 = Tri(p, p1, p2)
        val tt2 = Tri(p, p2, p0)
        tt0.setAdjacent(tt2, tri0, tt1)
        tt1.setAdjacent(tt0, tri1, tt2)
        tt2.setAdjacent(tt1, tri2, tt0)
        return tt0
    }

    /**
     * Interchanges the vertices of this triangle and a neighbor
     * so that their common edge
     * becomes the the other diagonal of the quadrilateral they form.
     * Neighbour triangle links are modified accordingly.
     *
     * @param index the index of the adjacent tri to flip with
     */
    fun flip(index: Int) {
        val tri: Tri = getAdjacent(index)!!
        val index1: Int = tri.getIndex(this)
        val adj0: Coordinate = getCoordinate(index)
        val adj1: Coordinate = getCoordinate(next(index))
        val opp0: Coordinate = getCoordinate(oppVertex(index))
        val opp1: Coordinate = tri.getCoordinate(oppVertex(index1))
        flip(tri, index, index1, adj0, adj1, opp0, opp1)
    }

    fun flip(
        tri: Tri?,
        index0: Int,
        index1: Int,
        adj0: Coordinate,
        adj1: Coordinate,
        opp0: Coordinate,
        opp1: Coordinate
    ) {
        //System.out.println("Flipping: " + this + " -> " + tri);

        //validate();
        //tri.validate();
        this.setCoordinates(opp1, opp0, adj0)
        tri!!.setCoordinates(opp0, opp1, adj1)
        /**
         * Order: 0: opp0-adj0 edge, 1: opp0-adj1 edge,
         * 2: opp1-adj0 edge, 3: opp1-adj1 edge
         */
        val adjacent: Array<Tri?> = getAdjacentTris(tri, index0, index1)
        this.setAdjacent(tri, adjacent[0], adjacent[2])
        //--- update the adjacent triangles with new adjacency
        if (adjacent[2] != null) {
            adjacent[2]!!.replace(tri, this)
        }
        tri.setAdjacent(this, adjacent[3], adjacent[1])
        if (adjacent[1] != null) {
            adjacent[1]!!.replace(this, tri)
        }
        //validate();
        //tri.validate();
    }

    /**
     * Replaces an adjacent triangle with a different one.
     *
     * @param triOld an adjacent triangle
     * @param triNew the triangle to replace it with
     */
    fun replace(triOld: Tri?, triNew: Tri) {
        if (tri0 != null && tri0 === triOld) {
            tri0 = triNew
        } else if (tri1 != null && tri1 === triOld) {
            tri1 = triNew
        } else if (tri2 != null && tri2 === triOld) {
            tri2 = triNew
        }
    }

    /**
     * Computes the degree of a Tri vertex, which is the number of tris containing it.
     * This must be done by searching the entire triangulation,
     * since the containing tris may not be adjacent or edge-connected.
     *
     * @param index the vertex index
     * @param triList the triangulation
     * @return the degree of the vertex
     */
    fun degree(index: Int, triList: List<Tri>): Int {
        val v: Coordinate = getCoordinate(index)
        var degree = 0
        for (tri in triList) {
            for (i in 0..2) {
                if (v.equals2D(tri.getCoordinate(i))) degree++
            }
        }
        return degree
    }

    /**
     * Removes this tri from the triangulation containing it.
     * All links between the tri and adjacent ones are nulled.
     *
     * @param triList the triangulation
     */
    fun remove(triList: MutableList<Tri>) {
        remove()
        triList.remove(this)
    }

    /**
     * Removes this triangle from a triangulation.
     * All adjacent references and the references to this
     * Tri in the adjacent Tris are set to `null`
     */
    fun remove() {
        remove(0)
        remove(1)
        remove(2)
    }

    fun remove(index: Int) {
        val adj: Tri = getAdjacent(index) ?: return
        adj.setTri(adj.getIndex(this), null)
        setTri(index, null)
    }

    /**
     * Gets the triangles adjacent to the quadrilateral
     * formed by this triangle and an adjacent one.
     * The triangles are returned in the following order:
     *
     *
     * Order: 0: opp0-adj0 edge, 1: opp0-adj1 edge,
     * 2: opp1-adj0 edge, 3: opp1-adj1 edge
     *
     * @param tri1 an adjacent triangle
     * @param index the index of the common edge in this triangle
     * @param index1 the index of the common edge in the adjacent triangle
     * @return
     */
    fun getAdjacentTris(triAdj: Tri?, index: Int, indexAdj: Int): Array<Tri?> {
        val adj = arrayOfNulls<Tri>(4)
        adj[0] = getAdjacent(prev(index))
        adj[1] = getAdjacent(next(index))
        adj[2] = triAdj!!.getAdjacent(next(indexAdj))
        adj[3] = triAdj.getAdjacent(prev(indexAdj))
        return adj
    }

    /**
     * Validates that a tri is correct.
     * Currently just checks that orientation is CW.
     *
     * @throw IllegalArgumentException if tri is not valid
     */
    fun validate() {
        if (Orientation.CLOCKWISE != Orientation.index(p0, p1, p2)) {
            throw IllegalArgumentException("Tri is not oriented correctly")
        }
        validateAdjacent(0)
        validateAdjacent(1)
        validateAdjacent(2)
    }

    /**
     * Validates that the vertices of an adjacent linked triangle are correct.
     *
     * @param index the index of the adjacent triangle
     */
    fun validateAdjacent(index: Int) {
        val tri: Tri = getAdjacent(index) ?: return
//        assert(this.isAdjacent(tri))
//        assert(tri.isAdjacent(this))
        val e0: Coordinate = getCoordinate(index)
        val e1: Coordinate = getCoordinate(next(index))
        val indexNeighbor: Int = tri.getIndex(this)
        val n0: Coordinate = tri.getCoordinate(indexNeighbor)
        val n1: Coordinate = tri.getCoordinate(next(indexNeighbor))
        Assert.isTrue(e0.equals2D(n1), "Edge coord not equal")
        Assert.isTrue(e1.equals2D(n0), "Edge coord not equal")

        //--- check that no edges cross
        val li = RobustLineIntersector()
        for (i in 0..2) {
            for (j in 0..2) {
                val p00: Coordinate = getCoordinate(i)
                val p01: Coordinate = getCoordinate(next(i))
                val p10: Coordinate = tri.getCoordinate(j)
                val p11: Coordinate = tri.getCoordinate(next(j))
                li.computeIntersection(p00, p01, p10, p11)
//                assert(!li.isProper)
            }
        }
    }
    /**
     * Gets the start and end vertex of the edge adjacent to another triangle.
     *
     * @param neighbor
     * @return
     */
    /*
  //TODO: define when needed 
  public Coordinate[] getEdge(Tri neighbor) {
    int index = getIndex(neighbor);
    int next = next(index);

    Coordinate e0 = getCoordinate(index);
    Coordinate e1 = getCoordinate(next);
    assert (neighbor.hasCoordinate(e0));
    assert (neighbor.hasCoordinate(e1));
    int iN = neighbor.getIndex(e0);
    int iNPrev = prev(iN);
    assert (neighbor.getIndex(e1) == iNPrev);

    return new Coordinate[] { getCoordinate(index), getCoordinate(next) };
  }

  public Coordinate getEdgeStart(int i) {
    return getCoordinate(i);
  }
  
  public Coordinate getEdgeEnd(int i) {
    return getCoordinate(next(i));
  }
  
  public boolean hasCoordinate(Coordinate v) {
    if ( p0.equals(v) || p1.equals(v) || p2.equals(v) ) {
      return true;
    }
    return false;
  }
   */
    /**
     * Gets the coordinate for a vertex.
     * This is the start vertex of the edge.
     *
     * @param index the vertex (edge) index
     * @return the vertex coordinate
     */
    fun getCoordinate(index: Int): Coordinate {
        if (index == 0) {
            return p0
        }
        return if (index == 1) {
            p1
        } else p2
    }

    /**
     * Gets the index of the triangle vertex which has a given coordinate (if any).
     * This is also the index of the edge which originates at the vertex.
     *
     * @param p the coordinate to find
     * @return the vertex index, or -1 if it is not in the triangle
     */
    fun getIndex(p: Coordinate?): Int {
        if (p0.equals2D(p!!)) return 0
        if (p1.equals2D(p)) return 1
        return if (p2.equals2D(p)) 2 else -1
    }

    /**
     * Gets the edge index which a triangle is adjacent to (if any),
     * based on the adjacent triangle link.
     *
     * @param tri the tri to find
     * @return the index of the edge adjacent to the triangle, or -1 if not found
     */
    fun getIndex(tri: Tri): Int {
        if (tri0 === tri) return 0
        if (tri1 === tri) return 1
        return if (tri2 === tri) 2 else -1
    }

    /**
     * Gets the triangle adjacent to an edge.
     *
     * @param index the edge index
     * @return the adjacent triangle (may be null)
     */
    fun getAdjacent(index: Int): Tri? {
        when (index) {
            0 -> return tri0
            1 -> return tri1
            2 -> return tri2
        }
        Assert.shouldNeverReachHere()
        return null
    }

    /**
     * Tests if this tri has any adjacent tris.
     *
     * @return true if there is at least one adjacent tri
     */
    fun hasAdjacent(): Boolean {
        return (hasAdjacent(0)
                || hasAdjacent(1) || hasAdjacent(2))
    }

    /**
     * Tests if there is an adjacent triangle to an edge.
     *
     * @param index the edge index
     * @return true if there is a triangle adjacent to edge
     */
    fun hasAdjacent(index: Int): Boolean {
        return null != getAdjacent(index)
    }

    /**
     * Tests if a triangle is adjacent to some edge of this triangle.
     *
     * @param tri the triangle to test
     * @return true if the triangle is adjacent
     * @see getIndex
     */
    fun isAdjacent(tri: Tri): Boolean {
        return getIndex(tri) >= 0
    }

    /**
     * Computes the number of triangle adjacent to this triangle.
     * This is a number in the range [0,2].
     *
     * @return the number of adjacent triangles
     */
    fun numAdjacent(): Int {
        var num = 0
        if (tri0 != null) num++
        if (tri1 != null) num++
        if (tri2 != null) num++
        return num
    }

    /**
     * Tests if a tri vertex is interior.
     * A vertex of a triangle is interior if it
     * is fully surrounded by other triangles.
     *
     * @param index the vertex index
     * @return true if the vertex is interior
     */
    fun isInteriorVertex(index: Int): Boolean {
        var curr: Tri = this
        var currIndex = index
        do {
            val adj: Tri = curr.getAdjacent(currIndex) ?: return false
            val adjIndex: Int = adj.getIndex(curr)
            curr = adj
            currIndex = next(adjIndex)
        } while (curr !== this)
        return true
    }

    /**
     * Tests if a tri contains a boundary edge,
     * and thus on the border of the triangulation containing it.
     *
     * @return true if the tri is on the border of the triangulation
     */
    val isBorder: Boolean
    get() {
        return isBoundary(0) || isBoundary(1) || isBoundary(2)
    }
    /**
     * Tests if an edge is on the boundary of a triangulation.
     *
     * @param index index of an edge
     * @return true if the edge is on the boundary
     */
    fun isBoundary(index: Int): Boolean {
        return !hasAdjacent(index)
    }

    /**
     * Computes a coordinate for the midpoint of a triangle edge.
     *
     * @param edgeIndex the edge index
     * @return the midpoint of the triangle edge
     */
    fun midpoint(edgeIndex: Int): Coordinate {
        val p0 = getCoordinate(edgeIndex)
        val p1 = getCoordinate(next(edgeIndex))
        val midX = (p0.x + p1.x) / 2
        val midY = (p0.y + p1.y) / 2
        return Coordinate(midX, midY)
    }

    /**
     * Gets the area of the triangle.
     *
     * @return the area of the triangle
     */
    val area: Double
    get() {
        return Triangle.area(p0, p1, p2)
    }
    /**
     * Gets the perimeter length of the triangle.
     *
     * @return the perimeter length
     */
    val length: Double
    get() {
        return Triangle.length(p0, p1, p2)
    }
    /**
     * Gets the length of an edge of the triangle.
     *
     * @param edgeIndex the edge index
     * @return the edge length
     */
    fun getLength(edgeIndex: Int): Double {
        return getCoordinate(edgeIndex).distance(getCoordinate(next(edgeIndex)))
    }

    /**
     * Creates a [Polygon] representing this triangle.
     *
     * @param geomFact the geometry factory
     * @return a polygon
     */
    fun toPolygon(geomFact: GeometryFactory): Polygon {
        return geomFact.createPolygon(
            geomFact.createLinearRing(arrayOf(p0.copy(), p1.copy(), p2.copy(), p0.copy())), null
        )
    }

    override fun toString(): String {
        return "POLYGON ((${WKTWriter.format(p0)}, ${WKTWriter.format(p1)}, ${WKTWriter.format(p2)}, ${WKTWriter.format(p0)}))"
    }
    companion object {
    /**
     * Creates a [GeometryCollection] of [Polygon]s
     * representing the triangles in a list.
     *
     * @param tris a collection of Tris
     * @param geomFact the GeometryFactory to use
     * @return the polygons for the triangles
     */
    public open fun toGeometry(tris: Collection<Tri>, geomFact: GeometryFactory): Geometry {
        val geoms = arrayOfNulls<Geometry>(tris.size)
        var i = 0
        for (tri in tris) {
            geoms[i++] = tri.toPolygon(geomFact)
        }
        return geomFact.createGeometryCollection(geoms.requireNoNulls())
    }

    /**
     * Computes the area of a set of Tris.
     *
     * @param triList a set of Tris
     * @return the total area of the triangles
     */
    fun area(triList: List<Tri>): Double {
        var area = 0.0
        for (tri in triList) {
            area += tri.area
        }
        return area
    }

    /**
     * Validates a list of Tris.
     *
     * @param triList the tris to validate
     */
    fun validate(triList: List<Tri>) {
        for (tri in triList) {
            tri.validate()
        }
    }

    /**
     * Creates a triangle with the given vertices.
     * The vertices should be oriented clockwise.
     *
     * @param p0 the first triangle vertex
     * @param p1 the second triangle vertex
     * @param p2 the third triangle vertex
     * @return the created triangle
     */
    fun create(p0: Coordinate, p1: Coordinate, p2: Coordinate): Tri {
        return Tri(p0, p1, p2)
    }

    /**
     * Creates a triangle from an array with three vertex coordinates.
     * The vertices should be oriented clockwise.
     *
     * @param pts the array of vertex coordinates
     * @return the created triangle
     */
    fun create(pts: Array<Coordinate>): Tri {
        return Tri(pts[0], pts[1], pts[2])
    }

    /**
     * Computes the vertex or edge index which is the next one
     * (clockwise) around the triangle.
     *
     * @param index the index
     * @return the next index value
     */
    fun next(index: Int): Int {
        when (index) {
            0 -> return 1
            1 -> return 2
            2 -> return 0
        }
        return -1
    }

    /**
     * Computes the vertex or edge index which is the previous one
     * (counter-clockwise) around the triangle.
     *
     * @param index the index
     * @return the previous index value
     */
    fun prev(index: Int): Int {
        when (index) {
            0 -> return 2
            1 -> return 0
            2 -> return 1
        }
        return -1
    }

    /**
     * Gets the index of the vertex opposite an edge.
     *
     * @param edgeIndex the edge index
     * @return the index of the opposite vertex
     */
    fun oppVertex(edgeIndex: Int): Int {
        return prev(edgeIndex)
    }

    /**
     * Gets the index of the edge opposite a vertex.
     *
     * @param vertexIndex the index of the vertex
     * @return the index of the opposite edge
     */
    fun oppEdge(vertexIndex: Int): Int {
        return next(vertexIndex)
    }
}
}