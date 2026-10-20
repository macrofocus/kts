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

import org.locationtech.jts.geom.*
import org.locationtech.jts.legacy.*
import org.locationtech.jts.util.Assert

/**
 * Computes the convex hull of a [Geometry].
 * The convex hull is the smallest convex Geometry that contains all the
 * points in the input Geometry.
 *
 * Uses the Graham Scan algorithm.
 *
 * Incorporates heuristics to optimize checking for degenerate results,
 * and to reduce the number of points processed for large inputs.
 *
 * @version 1.7
 */
open class ConvexHull(pts: Array<Coordinate>, geomFactory: GeometryFactory) {
    private val geomFactory: GeometryFactory
    private val inputPts: Array<Coordinate>

    /**
     * Create a new convex hull construction for the input [Geometry].
     */
    constructor(geometry: Geometry) : this(geometry.coordinates!!, geometry.factory)

    /**
     * Create a new convex hull construction for the input [Coordinate] array.
     */
    init {
        //-- suboptimal early uniquing - for performance testing only
        //inputPts = UniqueCoordinateArrayFilter.filterCoordinates(pts);

        inputPts = pts
        this.geomFactory = geomFactory
    }

    /**
     * Returns a [Geometry] that represents the convex hull of the input
     * geometry.
     * The returned geometry contains the minimal number of points needed to
     * represent the convex hull.  In particular, no more than two consecutive
     * points will be collinear.
     *
     * @return if the convex hull contains 3 or more points, a [Polygon];
     * 2 points, a [LineString];
     * 1 point, a [Point];
     * 0 points, an empty [GeometryCollection].
     */
    val convexHull: Geometry
        get() {
            val fewPointsGeom = createFewPointsResult()
            if (fewPointsGeom != null)
                return fewPointsGeom

            var reducedPts = inputPts
            //-- use heuristic to reduce points, if large
            if (inputPts.size > TUNING_REDUCE_SIZE) {
                reducedPts = reduce(inputPts)
            } else {
                //-- the points must be made unique
                reducedPts = extractUnique(inputPts)!!
            }
            // sort points for Graham scan.
            val sortedPts = preSort(reducedPts)

            // Use Graham scan to find convex hull.
            val cHS: Stack<Coordinate> = grahamScan(sortedPts)

            // Convert stack to an array.
            val cH = toCoordinateArray(cHS)

            // Convert array to appropriate output geometry.
            // (an empty or point result will be detected earlier)
            return lineOrPolygon(cH)
        }

    /**
     * Checks if there are <= 2 unique points,
     * which produce an obviously degenerate result.
     * If there are more points, returns null to indicate this.
     *
     * This is a fast check for an obviously degenerate result.
     * If the result is not obviously degenerate (at least 3 unique points found)
     * the full uniquing of the entire point set is
     * done only once during the reduce phase.
     *
     * @return a degenerate hull geometry, or null if the number of input points is large
     */
    private fun createFewPointsResult(): Geometry? {
        val uniquePts = extractUnique(inputPts, 2)
        return if (uniquePts == null) {
            null
        } else if (uniquePts.isEmpty()) {
            geomFactory.createGeometryCollection()
        } else if (uniquePts.size == 1) {
            geomFactory.createPoint(uniquePts[0])
        } else {
            geomFactory.createLineString(uniquePts)
        }
    }

    /**
     * An alternative to Stack.toArray, which is not present in earlier versions
     * of Java.
     */
    protected fun toCoordinateArray(stack: Stack<Coordinate>): Array<Coordinate> {
        val coordinates = arrayOfNulls<Coordinate>(stack.size)
        for (i in stack.indices) {
            val coordinate = stack[i]
            coordinates[i] = coordinate
        }
        return coordinates.requireNoNulls()
    }

    /**
     * Uses a heuristic to reduce the number of points scanned
     * to compute the hull.
     * The heuristic is to find a polygon guaranteed to
     * be in (or on) the hull, and eliminate all points inside it.
     * A quadrilateral defined by the extremal points
     * in the four orthogonal directions
     * can be used, but even more inclusive is
     * to use an octilateral defined by the points in the 8 cardinal directions.
     *
     * Note that even if the method used to determine the polygon vertices
     * is not 100% robust, this does not affect the robustness of the convex hull.
     *
     * To satisfy the requirements of the Graham Scan algorithm,
     * the returned array has at least 3 entries.
     *
     * This has the side effect of making the reduced points unique,
     * as required by the convex hull algorithm used.
     *
     * @param pts the points to reduce
     * @return the reduced list of points (at least 3)
     */
    private fun reduce(inputPts: Array<Coordinate>): Array<Coordinate> {
        //Coordinate[] polyPts = computeQuad(inputPts);
        val innerPolyPts = computeInnerOctolateralRing(inputPts)
            // unable to compute interior polygon for some reason
            ?: return inputPts

//    LinearRing ring = geomFactory.createLinearRing(polyPts);
//    System.out.println(ring);

        // add points defining polygon
        val reducedSet: MutableSet<Coordinate> = HashSet()
        for (i in innerPolyPts.indices) {
            reducedSet.add(innerPolyPts[i])
        }
        /**
         * Add all unique points not in the interior poly.
         * CGAlgorithms.isPointInRing is not defined for points exactly on the ring,
         * but this doesn't matter since the points of the interior polygon
         * are forced to be in the reduced set.
         */
        for (i in inputPts.indices) {
            if (!PointLocation.isInRing(inputPts[i], innerPolyPts)) {
                reducedSet.add(inputPts[i])
            }
        }
        val reducedPts: Array<Coordinate> = CoordinateArrays.toCoordinateArray(reducedSet)

        // ensure that computed array has at least 3 points (not necessarily unique)  
        return if (reducedPts.size < 3) padArray3(reducedPts) else reducedPts
    }

    private fun padArray3(pts: Array<Coordinate>): Array<Coordinate> {
        val pad = arrayOfNulls<Coordinate>(3)
        for (i in pad.indices) {
            if (i < pts.size) {
                pad[i] = pts[i]
            } else pad[i] = pts[0]
        }
        return pad.requireNoNulls()
    }

    /**
     * Sorts the points radially CW around the point with minimum Y and then X.
     *
     * @param pts the points to sort
     * @return the sorted points
     */
    private fun preSort(pts: Array<Coordinate>): Array<Coordinate> {
        var t: Coordinate?

        /**
         * find the lowest point in the set. If two or more points have
         * the same minimum Y coordinate choose the one with the minimum X.
         * This focal point is put in array location pts[0].
         */
        for (i in 1 until pts.size) {
            if (pts[i].y < pts[0].y || (pts[i].y == pts[0].y && pts[i].x < pts[0].x)) {
                t = pts[0]
                pts[0] = pts[i]
                pts[i] = t
            }
        }

        // sort the points radially around the focal point.
        pts.sortWith(RadialComparator(pts[0]), 1, pts.size)
//        Arrays.sort(pts, 1, pts.size, RadialComparator(pts[0]))
        return pts
    }

    /**
     * Uses the Graham Scan algorithm to compute the convex hull vertices.
     *
     * @param c a list of points, with at least 3 entries
     * @return a Stack containing the ordered points of the convex hull ring
     */
    private fun grahamScan(c: Array<Coordinate>): Stack<Coordinate> {
        var p: Coordinate
        val ps: Stack<Coordinate> = ArrayList()
        ps.push(c[0])
        ps.push(c[1])
        ps.push(c[2])
        for (i in 3 until c.size) {
            val cp = c[i]
            p = ps.pop()!!
            // check for empty stack to guard against robustness problems
            while (!ps.empty() &&
                Orientation.index(ps.peek() as Coordinate, p, cp) > 0
            ) {
                p = ps.pop()!!
            }
            ps.push(p)
            ps.push(cp)
        }
        ps.push(c[0])
        return ps
    }

    /**
     * @return    whether the three coordinates are collinear and c2 lies between
     * c1 and c3 inclusive
     */
    private fun isBetween(c1: Coordinate, c2: Coordinate?, c3: Coordinate?): Boolean {
        if (Orientation.index(c1, c2, c3) != 0) {
            return false
        }
        if (c1.x != c3!!.x) {
            if (c1.x <= c2!!.x && c2.x <= c3.x) {
                return true
            }
            if (c3.x <= c2.x && c2.x <= c1.x) {
                return true
            }
        }
        if (c1.y != c3.y) {
            if (c1.y <= c2!!.y && c2.y <= c3.y) {
                return true
            }
            if (c3.y <= c2.y && c2.y <= c1.y) {
                return true
            }
        }
        return false
    }

    private fun computeInnerOctolateralRing(inputPts: Array<Coordinate>): Array<Coordinate>? {
        val octPts = computeInnerOctolateralPts(inputPts)
        val coordList = CoordinateList()
        coordList.add(octPts, false)

        // points must all lie in a line
        if (coordList.size < 3) {
            return null
        }
        coordList.closeRing()
        return coordList.toCoordinateArray()
    }

    /**
     * Computes the extremal points of an inner octolateral.
     * Some points may be duplicates - these are collapsed later.
     *
     * @param inputPts the points to compute the octolateral for
     * @return the extremal points of the octolateral
     */
    private fun computeInnerOctolateralPts(inputPts: Array<Coordinate>): Array<Coordinate> {
        val pts = arrayOfNulls<Coordinate>(8)
        for (j in pts.indices) {
            pts[j] = inputPts[0]
        }
        for (i in 1 until inputPts.size) {
            if (inputPts[i].x < pts[0]!!.x) {
                pts[0] = inputPts[i]
            }
            if (inputPts[i].x - inputPts[i].y < pts[1]!!.x - pts[1]!!.y) {
                pts[1] = inputPts[i]
            }
            if (inputPts[i].y > pts[2]!!.y) {
                pts[2] = inputPts[i]
            }
            if (inputPts[i].x + inputPts[i].y > pts[3]!!.x + pts[3]!!.y) {
                pts[3] = inputPts[i]
            }
            if (inputPts[i].x > pts[4]!!.x) {
                pts[4] = inputPts[i]
            }
            if (inputPts[i].x - inputPts[i].y > pts[5]!!.x - pts[5]!!.y) {
                pts[5] = inputPts[i]
            }
            if (inputPts[i].y < pts[6]!!.y) {
                pts[6] = inputPts[i]
            }
            if (inputPts[i].x + inputPts[i].y < pts[7]!!.x + pts[7]!!.y) {
                pts[7] = inputPts[i]
            }
        }
        return pts.requireNoNulls()
    }

    /**
     * @param  vertices  the vertices of a linear ring, which may or may not be
     * flattened (i.e. vertices collinear)
     * @return           a 2-vertex `LineString` if the vertices are
     * collinear; otherwise, a `Polygon` with unnecessary
     * (collinear) vertices removed
     */
    private fun lineOrPolygon(coordinates: Array<Coordinate>): Geometry {
        var coordinates = coordinates
        coordinates = cleanRing(coordinates)
        if (coordinates.size == 3) {
            return geomFactory.createLineString(arrayOf(coordinates[0], coordinates[1]))
        }
        val linearRing: LinearRing = geomFactory.createLinearRing(coordinates)
        return geomFactory.createPolygon(linearRing)
    }

    /**
     * Cleans a list of points by removing interior collinear vertices.
     *
     * @param  vertices  the vertices of a linear ring, which may or may not be
     * flattened (i.e. vertices collinear)
     * @return the coordinates with unnecessary (collinear) vertices removed
     */
    private fun cleanRing(original: Array<Coordinate>): Array<Coordinate> {
        Assert.equals(original[0], original[original.size - 1])
        val cleanedRing: ArrayList<Coordinate> = ArrayList()
        var previousDistinctCoordinate: Coordinate? = null
        for (i in 0..original.size - 2) {
            val currentCoordinate = original[i]
            val nextCoordinate = original[i + 1]
            if (currentCoordinate == nextCoordinate) {
                continue
            }
            if (previousDistinctCoordinate != null
                && isBetween(previousDistinctCoordinate, currentCoordinate, nextCoordinate)
            ) {
                continue
            }
            cleanedRing.add(currentCoordinate)
            previousDistinctCoordinate = currentCoordinate
        }
        cleanedRing.add(original[original.size - 1])
        return cleanedRing.toTypedArray()
    }

    /**
     * Compares [Coordinate]s for their angle and distance
     * relative to an origin.
     * The origin is assumed to be lower in Y and then X than
     * all other point inputs.
     * The points are ordered CCW around the origin.
     *
     * @author Martin Davis
     * @version 1.7
     */
    private class RadialComparator(private val origin: Coordinate?) : Comparator<Coordinate> {
        override fun compare(p1: Coordinate, p2: Coordinate): Int {
            val comp = polarCompare(origin, p1, p2)
            return comp
        }

        companion object {
            /**
             * Given two points p and q compare them with respect to their radial
             * ordering about point o.  First checks radial ordering.
             * using a CCW orientation.
             * If the points are collinear, the comparison is based
             * on their distance to the origin.
             *
             *
             * p < q iff
             *
             *  * ang(o-p) < ang(o-q) (e.g. o-p-q is CCW)
             *  * or ang(o-p) == ang(o-q) && dist(o,p) < dist(o,q)
             *
             *
             * @param o the origin
             * @param p a point
             * @param q another point
             * @return -1, 0 or 1 depending on whether p is less than,
             * equal to or greater than q
             */
            private fun polarCompare(o: Coordinate?, p: Coordinate, q: Coordinate): Int {
                val orient: Int = Orientation.index(o!!, p, q)
                if (orient == Orientation.COUNTERCLOCKWISE) return 1
                if (orient == Orientation.CLOCKWISE) return -1

                /**
                 * The points are collinear,
                 * so compare based on distance from the origin.
                 * The points p and q are >= to the origin,
                 * so they lie in the closed half-plane above the origin.
                 * If they are not in a horizontal line,
                 * the Y ordinate can be tested to determine distance.
                 * This is more robust than computing the distance explicitly.
                 */
                if (p.y > q.y) return 1
                if (p.y < q.y) return -1

                /**
                 * The points lie in a horizontal line, which should also contain the origin
                 * (since they are collinear).
                 * Also, they must be above the origin.
                 * Use the X ordinate to determine distance.
                 */
                if (p.x > q.x) return 1
                if (p.x < q.x) return -1
                // Assert: p = q
                return 0
            }
        }
    }

    companion object {
        private const val TUNING_REDUCE_SIZE = 50

        private fun extractUnique(pts: Array<Coordinate>): Array<Coordinate>? {
            return extractUnique(pts, -1)
        }

        /**
         * Extracts unique coordinates from an array of coordinates,
         * up to an (optional) maximum count of values.
         * If more than the given maximum of unique values are found,
         * this is reported by returning `null`.
         * This avoids scanning all input points if not needed.
         * If the maximum points is not specified, all unique points are extracted.
         *
         * @param pts an array of Coordinates
         * @param maxPts the maximum number of unique points to scan, or -1
         * @return an array of unique values, or null
         */
        private fun extractUnique(pts: Array<Coordinate>, maxPts: Int): Array<Coordinate>? {
            val uniquePts: MutableSet<Coordinate> = HashSet()
            for (pt in pts) {
                uniquePts.add(pt)
                //-- if maxPts is provided, exit if more unique pts found
                if (maxPts >= 0 && uniquePts.size > maxPts) return null
            }
            return CoordinateArrays.toCoordinateArray(uniquePts)
        }
    }
}