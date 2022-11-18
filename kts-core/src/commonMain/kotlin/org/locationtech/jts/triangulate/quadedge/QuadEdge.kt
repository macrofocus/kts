/*
 * Copyright (c) 2016 Vivid Solutions.
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.triangulate.quadedge

import org.locationtech.jts.geom.LineSegment
import org.locationtech.jts.io.WKTWriter

/**
 * A class that represents the edge data structure which implements the quadedge algebra.
 * The quadedge algebra was described in a well-known paper by Guibas and Stolfi,
 * "Primitives for the manipulation of general subdivisions and the computation of Voronoi diagrams",
 * *ACM Transactions on Graphics*, 4(2), 1985, 75-123.
 *
 *
 * Each edge object is part of a quartet of 4 edges,
 * linked via their <tt>rot</tt> references.
 * Any edge in the group may be accessed using a series of [.rot] operations.
 * Quadedges in a subdivision are linked together via their <tt>next</tt> references.
 * The linkage between the quadedge quartets determines the topology
 * of the subdivision.
 *
 *
 * The edge class does not contain separate information for vertices or faces; a vertex is implicitly
 * defined as a ring of edges (created using the <tt>next</tt> field).
 *
 * @author David Skea
 * @author Martin Davis
 */
class QuadEdge  //    private int      visitedKey = 0;
/**
 * Quadedges must be made using [makeEdge],
 * to ensure proper construction.
 */
private constructor() {
    // the dual of this edge, directed from right to left
    private var rot: QuadEdge? = null
    private var vertex // The vertex that this edge represents
            : Vertex? = null
    private var next // A reference to a connected edge
            : QuadEdge? = null
    /**
     * Gets the external data value for this edge.
     *
     * @return the data object
     */
    /**
     * Sets the external data value for this edge.
     *
     * @param data an object containing external data
     */
    var data: Any? = null

    /**
     * Gets the primary edge of this quadedge and its <tt>sym</tt>.
     * The primary edge is the one for which the origin
     * and destination coordinates are ordered
     * according to the standard [Coordinate] ordering
     *
     * @return the primary quadedge
     */
    val primary: QuadEdge?
        get() = if (orig()!!.coordinate.compareTo(dest()!!.coordinate) <= 0) this else sym()

    /**
     * Marks this quadedge as being deleted.
     * This does not free the memory used by
     * this quadedge quartet, but indicates
     * that this edge no longer participates
     * in a subdivision.
     *
     */
    fun delete() {
        rot = null
    }

    /**
     * Tests whether this edge has been deleted.
     *
     * @return true if this edge has not been deleted.
     */
    val isLive: Boolean
        get() = rot != null

    /**
     * Sets the connected edge
     *
     * @param next edge
     */
    fun setNext(next: QuadEdge?) {
        this.next = next
    }
    /***************************************************************************
     * QuadEdge Algebra
     *
     */
    /**
     * Gets the dual of this edge, directed from its right to its left.
     *
     * @return the rotated edge
     */
    fun rot(): QuadEdge? {
        return rot
    }

    /**
     * Gets the dual of this edge, directed from its left to its right.
     *
     * @return the inverse rotated edge.
     */
    fun invRot(): QuadEdge? {
        return rot!!.sym()
    }

    /**
     * Gets the edge from the destination to the origin of this edge.
     *
     * @return the sym of the edge
     */
    fun sym(): QuadEdge? {
        return rot!!.rot
    }

    /**
     * Gets the next CCW edge around the origin of this edge.
     *
     * @return the next linked edge.
     */
    fun oNext(): QuadEdge? {
        return next
    }

    /**
     * Gets the next CW edge around (from) the origin of this edge.
     *
     * @return the previous edge.
     */
    fun oPrev(): QuadEdge? {
        return rot!!.next!!.rot
    }

    /**
     * Gets the next CCW edge around (into) the destination of this edge.
     *
     * @return the next destination edge.
     */
    fun dNext(): QuadEdge? {
        return sym()!!.oNext()!!.sym()
    }

    /**
     * Gets the next CW edge around (into) the destination of this edge.
     *
     * @return the previous destination edge.
     */
    fun dPrev(): QuadEdge? {
        return invRot()!!.oNext()!!.invRot()
    }

    /**
     * Gets the CCW edge around the left face following this edge.
     *
     * @return the next left face edge.
     */
    fun lNext(): QuadEdge? {
        return invRot()!!.oNext()!!.rot()
    }

    /**
     * Gets the CCW edge around the left face before this edge.
     *
     * @return the previous left face edge.
     */
    fun lPrev(): QuadEdge? {
        return next!!.sym()
    }

    /**
     * Gets the edge around the right face ccw following this edge.
     *
     * @return the next right face edge.
     */
    fun rNext(): QuadEdge? {
        return rot!!.next!!.invRot()
    }

    /**
     * Gets the edge around the right face ccw before this edge.
     *
     * @return the previous right face edge.
     */
    fun rPrev(): QuadEdge? {
        return sym()!!.oNext()
    }
    /***********************************************************************************************
     * Data Access
     */
    /**
     * Sets the vertex for this edge's origin
     *
     * @param o the origin vertex
     */
    fun setOrig(o: Vertex?) {
        vertex = o
    }

    /**
     * Sets the vertex for this edge's destination
     *
     * @param d the destination vertex
     */
    fun setDest(d: Vertex?) {
        sym()!!.setOrig(d)
    }

    /**
     * Gets the vertex for the edge's origin
     *
     * @return the origin vertex
     */
    fun orig(): Vertex? {
        return vertex
    }

    /**
     * Gets the vertex for the edge's destination
     *
     * @return the destination vertex
     */
    fun dest(): Vertex? {
        return sym()!!.orig()
    }

    /**
     * Gets the length of the geometry of this quadedge.
     *
     * @return the length of the quadedge
     */
    val length: Double
        get() = orig()!!.coordinate.distance(dest()!!.coordinate)

    /**
     * Tests if this quadedge and another have the same line segment geometry,
     * regardless of orientation.
     *
     * @param qe a quadedge
     * @return true if the quadedges are based on the same line segment regardless of orientation
     */
    fun equalsNonOriented(qe: QuadEdge): Boolean {
        if (equalsOriented(qe)) return true
        return if (equalsOriented(qe.sym())) true else false
    }

    /**
     * Tests if this quadedge and another have the same line segment geometry
     * with the same orientation.
     *
     * @param qe a quadedge
     * @return true if the quadedges are based on the same line segment
     */
    fun equalsOriented(qe: QuadEdge?): Boolean {
        return if (orig()!!.coordinate.equals2D(qe!!.orig()!!.coordinate)
            && dest()!!.coordinate.equals2D(qe.dest()!!.coordinate)
        ) true else false
    }

    /**
     * Creates a [LineSegment] representing the
     * geometry of this edge.
     *
     * @return a LineSegment
     */
    fun toLineSegment(): LineSegment {
        return LineSegment(vertex!!.coordinate, dest()!!.coordinate)
    }

    /**
     * Converts this edge to a WKT two-point <tt>LINESTRING</tt> indicating
     * the geometry of this edge.
     *
     * @return a String representing this edge's geometry
     */
    override fun toString(): String {
        val p0 = vertex!!.coordinate
        val p1 = dest()!!.coordinate
        return WKTWriter.toLineString(p0, p1)
    }

    companion object {
        /**
         * Creates a new QuadEdge quartet from [Vertex] o to [Vertex] d.
         *
         * @param o
         * the origin Vertex
         * @param d
         * the destination Vertex
         * @return the new QuadEdge quartet
         */
        fun makeEdge(
            o: Vertex?,
            d: Vertex?
        ): QuadEdge {
            val q0 = QuadEdge()
            val q1 = QuadEdge()
            val q2 = QuadEdge()
            val q3 = QuadEdge()
            q0.rot = q1
            q1.rot = q2
            q2.rot = q3
            q3.rot = q0
            q0.setNext(q0)
            q1.setNext(q3)
            q2.setNext(q2)
            q3.setNext(q1)
            q0.setOrig(o)
            q0.setDest(d)
            return q0
        }

        /**
         * Creates a new QuadEdge connecting the destination of a to the origin of
         * b, in such a way that all three have the same left face after the
         * connection is complete. Additionally, the data pointers of the new edge
         * are set.
         *
         * @return the connected edge.
         */
        fun connect(a: QuadEdge, b: QuadEdge): QuadEdge {
            val e = makeEdge(a.dest(), b.orig())
            splice(e, a.lNext())
            splice(e.sym(), b)
            return e
        }

        /**
         * Splices two edges together or apart.
         * Splice affects the two edge rings around the origins of a and b, and, independently, the two
         * edge rings around the left faces of <tt>a</tt> and <tt>b</tt>.
         * In each case, (i) if the two rings are distinct,
         * Splice will combine them into one, or (ii) if the two are the same ring, Splice will break it
         * into two separate pieces. Thus, Splice can be used both to attach the two edges together, and
         * to break them apart.
         *
         * @param a an edge to splice
         * @param b an edge to splice
         */
        fun splice(a: QuadEdge?, b: QuadEdge?) {
            val alpha = a!!.oNext()!!.rot()
            val beta = b!!.oNext()!!.rot()
            val t1 = b.oNext()
            val t2 = a.oNext()
            val t3 = beta!!.oNext()
            val t4 = alpha!!.oNext()
            a.setNext(t1)
            b.setNext(t2)
            alpha.setNext(t3)
            beta.setNext(t4)
        }

        /**
         * Turns an edge counterclockwise inside its enclosing quadrilateral.
         *
         * @param e the quadedge to turn
         */
        fun swap(e: QuadEdge) {
            val a = e.oPrev()
            val b = e.sym()!!.oPrev()
            splice(e, a)
            splice(e.sym(), b)
            splice(e, a!!.lNext())
            splice(e.sym(), b!!.lNext())
            e.setOrig(a.dest())
            e.setDest(b.dest())
        }
    }
}