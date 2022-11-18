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
package org.locationtech.jts.geomgraph

import org.locationtech.jts.algorithm.BoundaryNodeRule
import org.locationtech.jts.algorithm.Orientation
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Quadrant
import org.locationtech.jts.legacy.Math.atan2
import org.locationtech.jts.util.Assert
import kotlin.jvm.JvmOverloads

/**
 * Models the end of an edge incident on a node.
 * EdgeEnds have a direction
 * determined by the direction of the ray from the initial
 * point to the next point.
 * EdgeEnds are comparable under the ordering
 * "a has a greater angle with the x-axis than b".
 * This ordering is used to sort EdgeEnds around a node.
 * @version 1.7
 */
open class EdgeEnd protected constructor(open var edge: Edge) : Comparable<Any?> {
    var label: Label? = null
    var node // the node this edge end originates at
            : Node? = null
    var coordinate: Coordinate? = null
        private set
    var directedCoordinate // points of initial line segment
            : Coordinate? = null
        private set
    var dx = 0.0
        private set
    var dy // the direction vector for this edge from its starting point
            = 0.0
        private set
    var quadrant = 0
        private set

    @JvmOverloads
    constructor(
        edge: Edge,
        p0: Coordinate,
        p1: Coordinate,
        label: Label? = null
    ) : this(edge) {
        init(p0, p1)
        this.label = label
    }

    protected fun init(p0: Coordinate, p1: Coordinate) {
        coordinate = p0
        directedCoordinate = p1
        dx = p1.x - p0.x
        dy = p1.y - p0.y
        quadrant = Quadrant.quadrant(dx, dy)
        Assert.isTrue(!(dx == 0.0 && dy == 0.0), "EdgeEnd with identical endpoints found")
    }

    override fun compareTo(obj: Any?): Int {
        val e = obj as EdgeEnd?
        return compareDirection(e)
    }

    /**
     * Implements the total order relation:
     *
     * a has a greater angle with the positive x-axis than b
     *
     * Using the obvious algorithm of simply computing the angle is not robust,
     * since the angle calculation is obviously susceptible to roundoff.
     * A robust algorithm is:
     * - first compare the quadrant.  If the quadrants
     * are different, it it trivial to determine which vector is "greater".
     * - if the vectors lie in the same quadrant, the computeOrientation function
     * can be used to decide the relative orientation of the vectors.
     *
     * @param e EdgeEnd
     * @return direction comparison
     */
    fun compareDirection(e: EdgeEnd?): Int {
        if (dx == e!!.dx && dy == e.dy) return 0
        // if the rays are in different quadrants, determining the ordering is trivial
        if (quadrant > e.quadrant) return 1
        return if (quadrant < e.quadrant) -1 else Orientation.index(
            e.coordinate,
            e.directedCoordinate,
            directedCoordinate
        )
        // vectors are in the same quadrant - check relative orientation of direction vectors
        // this is > e if it is CCW of e
    }

    open fun computeLabel(boundaryNodeRule: BoundaryNodeRule) {
        // subclasses should override this if they are using labels
    }

//    fun print(out: java.io.PrintStream) {
//        val angle: Double = atan2(dy, dx)
//        val className: String = javaClass.getName()
//        val lastDotPos = className.lastIndexOf('.')
//        val name = className.substring(lastDotPos + 1)
//        out.print("  " + name + ": " + coordinate + " - " + directedCoordinate + " " + quadrant + ":" + angle + "   " + label)
//    }

    override fun toString(): String {
        val angle: Double = atan2(dy, dx)
        val className: String = this::class.simpleName!!
        val lastDotPos = className.lastIndexOf('.')
        val name = className.substring(lastDotPos + 1)
        return "  " + name + ": " + coordinate + " - " + directedCoordinate + " " + quadrant + ":" + angle + "   " + label
    }
}