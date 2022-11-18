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
package org.locationtech.jts.algorithm

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.legacy.Math

/**
 * Represents a homogeneous coordinate in a 2-D coordinate space.
 * In JTS [HCoordinate]s are used as a clean way
 * of computing intersections between line segments.
 *
 * @author David Skea
 * @version 1.7
 */
class HCoordinate {
    /*
  public static Coordinate OLDintersection(
      Coordinate p1, Coordinate p2,
      Coordinate q1, Coordinate q2)
      throws NotRepresentableException
  {
    HCoordinate l1 = new HCoordinate(p1, p2);
    HCoordinate l2 = new HCoordinate(q1, q2);
    HCoordinate intHCoord = new HCoordinate(l1, l2);
    Coordinate intPt = intHCoord.getCoordinate();
    return intPt;
  }
  */
    var x: Double
        // ToDo add annotation: @Throws(NotRepresentableException::class)
        get() {
            val a = field / w
            if (Math.isNaN(a) || Math.isInfinite(a)) {
                throw NotRepresentableException()
            }
            return a
        }
    var y: Double
        // ToDo add annotation: @Throws(NotRepresentableException::class)
        get() {
            val a = field / w
            if (Math.isNaN(a) || Math.isInfinite(a)) {
                throw NotRepresentableException()
            }
            return a
        }
    var w: Double

    constructor() {
        x = 0.0
        y = 0.0
        w = 1.0
    }

    constructor(_x: Double, _y: Double, _w: Double) {
        x = _x
        y = _y
        w = _w
    }

    constructor(_x: Double, _y: Double) {
        x = _x
        y = _y
        w = 1.0
    }

    constructor(p: Coordinate) {
        x = p.x
        y = p.y
        w = 1.0
    }

    constructor(p1: HCoordinate, p2: HCoordinate) {
        x = p1.y * p2.w - p2.y * p1.w
        y = p2.x * p1.w - p1.x * p2.w
        w = p1.x * p2.y - p2.x * p1.y
    }

    /**
     * Constructs a homogeneous coordinate which is the intersection of the lines
     * define by the homogenous coordinates represented by two
     * [Coordinate]s.
     *
     * @param p1
     * @param p2
     */
    constructor(p1: Coordinate, p2: Coordinate) {
        // optimization when it is known that w = 1
        x = p1.y - p2.y
        y = p2.x - p1.x
        w = p1.x * p2.y - p2.x * p1.y
    }

    constructor(p1: Coordinate, p2: Coordinate, q1: Coordinate, q2: Coordinate) {
        // unrolled computation
        val px = p1.y - p2.y
        val py = p2.x - p1.x
        val pw = p1.x * p2.y - p2.x * p1.y
        val qx = q1.y - q2.y
        val qy = q2.x - q1.x
        val qw = q1.x * q2.y - q2.x * q1.y
        x = py * qw - qy * pw
        y = qx * pw - px * qw
        w = px * qy - qx * py
    }


    // ToDo add annotation: @get:Throws(NotRepresentableException::class)
    val coordinate: Coordinate
        get() {
            val p = Coordinate()
            p.x = x
            p.y = y
            return p
        }

    companion object {
        /**
         * Computes the (approximate) intersection point between two line segments
         * using homogeneous coordinates.
         *
         * Note that this algorithm is
         * not numerically stable; i.e. it can produce intersection points which
         * lie outside the envelope of the line segments themselves.  In order
         * to increase the precision of the calculation input points should be normalized
         * before passing them to this routine.
         *
         */
        @Deprecated("use {@link Intersection#intersection(Coordinate, Coordinate, Coordinate, Coordinate)}")
        @Throws(
            NotRepresentableException::class
        )
        fun intersection(
            p1: Coordinate, p2: Coordinate,
            q1: Coordinate, q2: Coordinate
        ): Coordinate {
            // unrolled computation
            val px = p1.y - p2.y
            val py = p2.x - p1.x
            val pw = p1.x * p2.y - p2.x * p1.y
            val qx = q1.y - q2.y
            val qy = q2.x - q1.x
            val qw = q1.x * q2.y - q2.x * q1.y
            val x = py * qw - qy * pw
            val y = qx * pw - px * qw
            val w = px * qy - qx * py
            val xInt = x / w
            val yInt = y / w
            if (Math.isNaN(xInt) || (Math.isInfinite(xInt)
                        || Math.isNaN(yInt)) || Math.isInfinite(yInt)
            ) {
                throw NotRepresentableException()
            }
            return Coordinate(xInt, yInt)
        }
    }
}