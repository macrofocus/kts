/*
 * Copyright (c) 2016 Vivid Solutions.
 * Copyright (c) 2020 Macrofocus GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.algorithm

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.CoordinateSequence
import kotlin.jvm.JvmStatic

/**
 * Functions to compute the orientation of basic geometric structures
 * including point triplets (triangles) and rings.
 * Orientation is a fundamental property of planar geometries
 * (and more generally geometry on two-dimensional manifolds).
 *
 * Orientation is notoriously subject to numerical precision errors
 * in the case of collinear or nearly collinear points.
 * JTS uses extended-precision arithmetic to increase
 * the robustness of the computation.
 *
 * @author Martin Davis
 * @author Luc Girardin
 */
object Orientation {
    /**
     * A value that indicates an orientation of clockwise, or a right turn.
     */
    const val CLOCKWISE = -1

    /**
     * A value that indicates an orientation of clockwise, or a right turn.
     */
    const val RIGHT = CLOCKWISE

    /**
     * A value that indicates an orientation of counterclockwise, or a left turn.
     */
    const val COUNTERCLOCKWISE = 1

    /**
     * A value that indicates an orientation of counterclockwise, or a left turn.
     */
    const val LEFT = COUNTERCLOCKWISE

    /**
     * A value that indicates an orientation of collinear, or no turn (straight).
     */
    const val COLLINEAR = 0

    /**
     * A value that indicates an orientation of collinear, or no turn (straight).
     */
    const val STRAIGHT = COLLINEAR

    /**
     * Returns the orientation index of the direction of the point `q` relative to
     * a directed infinite line specified by `p1-p2`.
     * The index indicates whether the point lies to the [.LEFT] or [.RIGHT]
     * of the line, or lies on it [.COLLINEAR].
     * The index also indicates the orientation of the triangle formed by the three points
     * ( [.COUNTERCLOCKWISE], [.CLOCKWISE], or [.STRAIGHT] )
     *
     * @param p1 the origin point of the line vector
     * @param p2 the final point of the line vector
     * @param q the point to compute the direction to
     *
     * @return -1 ( [.CLOCKWISE] or [.RIGHT] ) if q is clockwise (right) from p1-p2;
     * 1 ( [.COUNTERCLOCKWISE] or [.LEFT] ) if q is counter-clockwise (left) from p1-p2;
     * 0 ( [.COLLINEAR] or [.STRAIGHT] ) if q is collinear with p1-p2
     */
    @JvmStatic
    fun index(p1: Coordinate, p2: Coordinate, q: Coordinate): Int {
        /*
     * MD - 9 Aug 2010 It seems that the basic algorithm is slightly orientation
     * dependent, when computing the orientation of a point very close to a
     * line. This is possibly due to the arithmetic in the translation to the
     * origin.
     * 
     * For instance, the following situation produces identical results in spite
     * of the inverse orientation of the line segment:
     * 
     * Coordinate p0 = new Coordinate(219.3649559090992, 140.84159161824724);
     * Coordinate p1 = new Coordinate(168.9018919682399, -5.713787599646864);
     * 
     * Coordinate p = new Coordinate(186.80814046338352, 46.28973405831556); int
     * orient = orientationIndex(p0, p1, p); int orientInv =
     * orientationIndex(p1, p0, p);
     * 
     * A way to force consistent results is to normalize the orientation of the
     * vector using the following code. However, this may make the results of
     * orientationIndex inconsistent through the triangle of points, so it's not
     * clear this is an appropriate patch.
     * 
     */
        return CGAlgorithmsDD.orientationIndex(p1, p2, q)

        // testing only
        //return ShewchuksDeterminant.orientationIndex(p1, p2, q);
        // previous implementation - not quite fully robust
        //return RobustDeterminant.orientationIndex(p1, p2, q);
    }

    /**
     * Computes whether a ring defined by an array of [Coordinate]s is
     * oriented counter-clockwise.
     *
     *  * The list of points is assumed to have the first and last points equal.
     *  * This will handle coordinate lists which contain repeated points.
     *
     * This algorithm is **only** guaranteed to work with valid rings. If the
     * ring is invalid (e.g. self-crosses or touches), the computed result may not
     * be correct.
     *
     * @param ring
     * an array of Coordinates forming a ring
     * @return true if the ring is oriented counter-clockwise.
     * @throws IllegalArgumentException
     * if there are too few points to determine orientation (&lt; 4)
     */
    @JvmStatic
    fun isCCW(ring: Array<Coordinate>): Boolean {
        // # of points without closing endpoint
        val nPts = ring.size - 1
        // sanity check
        require(nPts >= 3) { "Ring has fewer than 4 points, so orientation cannot be determined" }

        // find highest point
        var hiPt = ring[0]
        var hiIndex = 0
        for (i in 1..nPts) {
            val p = ring[i]
            if (p.y > hiPt.y) {
                hiPt = p
                hiIndex = i
            }
        }

        // find distinct point before highest point
        var iPrev = hiIndex
        do {
            iPrev -= 1
            if (iPrev < 0) iPrev = nPts
        } while (ring[iPrev].equals2D(hiPt) && iPrev != hiIndex)

        // find distinct point after highest point
        var iNext = hiIndex
        do {
            iNext = (iNext + 1) % nPts
        } while (ring[iNext].equals2D(hiPt) && iNext != hiIndex)
        val prev = ring[iPrev]
        val next = ring[iNext]

        /*
     * This check catches cases where the ring contains an A-B-A configuration
     * of points. This can happen if the ring does not contain 3 distinct points
     * (including the case where the input array has fewer than 4 elements), or
     * it contains coincident line segments.
     */if (prev.equals2D(hiPt) || next.equals2D(hiPt) || prev.equals2D(next)) return false
        val disc = index(prev, hiPt, next)

        /*
     * If disc is exactly 0, lines are collinear. There are two possible cases:
     * (1) the lines lie along the x axis in opposite directions (2) the lines
     * lie on top of one another
     * 
     * (1) is handled by checking if next is left of prev ==> CCW (2) will never
     * happen if the ring is valid, so don't check for it (Might want to assert
     * this)
     */
        val isCCW: Boolean
        isCCW = if (disc == 0) {
            // poly is CCW if prev x is right of next x
            prev.x > next.x
        } else {
            // if area is positive, points are ordered CCW
            disc > 0
        }
        return isCCW
    }

    /**
     * Computes whether a ring defined by an [CoordinateSequence] is
     * oriented counter-clockwise.
     *
     *  * The list of points is assumed to have the first and last points equal.
     *  * This will handle coordinate lists which contain repeated points.
     *
     * This algorithm is **only** guaranteed to work with valid rings. If the
     * ring is invalid (e.g. self-crosses or touches), the computed result may not
     * be correct.
     *
     * @param ring
     * a CoordinateSequence forming a ring
     * @return true if the ring is oriented counter-clockwise.
     * @throws IllegalArgumentException
     * if there are too few points to determine orientation (&lt; 4)
     */
    @JvmStatic
    fun isCCW(ring: CoordinateSequence): Boolean {
        // # of points without closing endpoint
        val nPts = ring.size() - 1
        // sanity check
        require(nPts >= 3) { "Ring has fewer than 4 points, so orientation cannot be determined" }

        // find highest point
        var hiPt = ring.getCoordinate(0)
        var hiIndex = 0
        for (i in 1..nPts) {
            val p = ring.getCoordinate(i)
            if (p.y > hiPt.y) {
                hiPt = p
                hiIndex = i
            }
        }

        // find distinct point before highest point
        var prev: Coordinate?
        var iPrev = hiIndex
        do {
            iPrev -= 1
            if (iPrev < 0) iPrev = nPts
            prev = ring.getCoordinate(iPrev)
        } while (prev!!.equals2D(hiPt) && iPrev != hiIndex)

        // find distinct point after highest point
        var next: Coordinate?
        var iNext = hiIndex
        do {
            iNext = (iNext + 1) % nPts
            next = ring.getCoordinate(iNext)
        } while (next!!.equals2D(hiPt) && iNext != hiIndex)

        /*
     * This check catches cases where the ring contains an A-B-A configuration
     * of points. This can happen if the ring does not contain 3 distinct points
     * (including the case where the input array has fewer than 4 elements), or
     * it contains coincident line segments.
     */if (prev.equals2D(hiPt) || next.equals2D(hiPt) || prev.equals2D(next)) return false
        val disc = index(prev, hiPt, next)

        /*
     * If disc is exactly 0, lines are collinear. There are two possible cases:
     * (1) the lines lie along the x axis in opposite directions (2) the lines
     * lie on top of one another
     *
     * (1) is handled by checking if next is left of prev ==> CCW (2) will never
     * happen if the ring is valid, so don't check for it (Might want to assert
     * this)
     */
        val isCCW: Boolean
        isCCW = if (disc == 0) {
            // poly is CCW if prev x is right of next x
            prev.x > next.x
        } else {
            // if area is positive, points are ordered CCW
            disc > 0
        }
        return isCCW
    }
}