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
package org.locationtech.jts.planargraph

import org.locationtech.jts.algorithm.Orientation.index
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Quadrant.quadrant
import org.locationtech.jts.legacy.Math.atan2

/**
 * Represents a directed edge in a [PlanarGraph]. A DirectedEdge may or
 * may not have a reference to a parent [Edge] (some applications of
 * planar graphs may not require explicit Edge objects to be created). Usually
 * a client using a `PlanarGraph` will subclass `DirectedEdge`
 * to add its own application-specific data and methods.
 *
 * @version 1.7
 */
open class DirectedEdge(
    from: Node,
    to: Node,
    directionPt: Coordinate,
    edgeDirection: Boolean
) : GraphComponent(), Comparable<Any?> {
    protected var parentEdge: Edge? = null
    protected var from: Node
    protected var to: Node
    protected var p0: Coordinate?

    /**
     * Returns a point to which an imaginary line is drawn from the from-node to
     * specify this DirectedEdge's orientation.
     */
    var directionPt: Coordinate
        protected set
    /**
     * Returns the symmetric DirectedEdge -- the other DirectedEdge associated with
     * this DirectedEdge's parent Edge.
     */
    /**
     * Sets this DirectedEdge's symmetric DirectedEdge, which runs in the opposite
     * direction.
     */
    var sym: DirectedEdge? = null // optional

    /**
     * Returns whether the direction of the parent Edge (if any) is the same as that
     * of this Directed Edge.
     */
    var edgeDirection: Boolean
        protected set

    /**
     * Returns 0, 1, 2, or 3, indicating the quadrant in which this DirectedEdge's
     * orientation lies.
     */
    var quadrant: Int
        protected set

    /**
     * Returns the angle that the start of this DirectedEdge makes with the
     * positive x-axis, in radians.
     */
    var angle: Double
        protected set

    /**
     * Constructs a DirectedEdge connecting the `from` node to the
     * `to` node.
     *
     * @param directionPt
     * specifies this DirectedEdge's direction vector
     * (determined by the vector from the `from` node
     * to `directionPt`)
     * @param edgeDirection
     * whether this DirectedEdge's direction is the same as or
     * opposite to that of the parent Edge (if any)
     */
    init {
        this.from = from
        this.to = to
        this.edgeDirection = edgeDirection
        p0 = from.coordinate
        this.directionPt = directionPt
        val dx = directionPt.x - p0!!.x
        val dy = directionPt.y - p0!!.y
        quadrant = quadrant(dx, dy)
        angle = atan2(dy, dx)
        //Assert.isTrue(! (dx == 0 && dy == 0), "EdgeEnd with identical endpoints found");
    }
    /**
     * Returns this DirectedEdge's parent Edge, or null if it has none.
     */
    /**
     * Associates this DirectedEdge with an Edge (possibly null, indicating no associated
     * Edge).
     */
    var edge: Edge?
        get() = parentEdge
        set(parentEdge) {
            this.parentEdge = parentEdge
        }

    /**
     * Returns the node from which this DirectedEdge leaves.
     */
    val fromNode: Node
        get() = from

    /**
     * Returns the node to which this DirectedEdge goes.
     */
    val toNode: Node
        get() = to

    /**
     * Returns the coordinate of the from-node.
     */
    val coordinate: Coordinate?
        get() = from.coordinate

    /**
     * Removes this directed edge from its containing graph.
     */
    fun remove() {
        sym = null
        parentEdge = null
    }

    /**
     * Tests whether this directed edge has been removed from its containing graph
     *
     * @return `true` if this directed edge is removed
     */
    override val isRemoved: Boolean
        get() = parentEdge == null

    /**
     * Returns 1 if this DirectedEdge has a greater angle with the
     * positive x-axis than b", 0 if the DirectedEdges are collinear, and -1 otherwise.
     *
     * Using the obvious algorithm of simply computing the angle is not robust,
     * since the angle calculation is susceptible to roundoff. A robust algorithm
     * is:
     *
     *  * first compare the quadrants. If the quadrants are different, it it
     * trivial to determine which vector is "greater".
     *  * if the vectors lie in the same quadrant, the robust
     * [Orientation.index]
     * function can be used to decide the relative orientation of the vectors.
     *
     */
    override operator fun compareTo(obj: Any?): Int {
        val de = obj as DirectedEdge
        return compareDirection(de)
    }

    /**
     * Returns 1 if this DirectedEdge has a greater angle with the
     * positive x-axis than b", 0 if the DirectedEdges are collinear, and -1 otherwise.
     *
     * Using the obvious algorithm of simply computing the angle is not robust,
     * since the angle calculation is susceptible to roundoff. A robust algorithm
     * is:
     *
     *  * first compare the quadrants. If the quadrants are different, it it
     * trivial to determine which vector is "greater".
     *  * if the vectors lie in the same quadrant, the robust
     * [Orientation.index]
     * function can be used to decide the relative orientation of the vectors.
     *
     */
    fun compareDirection(e: DirectedEdge): Int {
        // if the rays are in different quadrants, determining the ordering is trivial
        if (quadrant > e.quadrant) return 1
        return if (quadrant < e.quadrant) -1 else index(e.p0, e.directionPt, directionPt)
        // vectors are in the same quadrant - check relative orientation of direction vectors
        // this is > e if it is CCW of e
    }

    /**
     * Prints a detailed string representation of this DirectedEdge to the given PrintStream.
     */
//    fun print(out: java.io.PrintStream) {
//        val className: String = javaClass.getName()
//        val lastDotPos = className.lastIndexOf('.')
//        val name = className.substring(lastDotPos + 1)
//        out.print("  " + name + ": " + p0 + " - " + directionPt + " " + quadrant + ":" + angle)
//    }

    companion object {
        /**
         * Returns a List containing the parent Edge (possibly null) for each of the given
         * DirectedEdges.
         */
        fun toEdges(dirEdges: Collection<*>): MutableList<Any?> {
            val edges: MutableList<Any?> = ArrayList()
            val i = dirEdges.iterator()
            while (i.hasNext()) {
                edges.add((i.next() as DirectedEdge).parentEdge)
            }
            return edges
        }
    }
}