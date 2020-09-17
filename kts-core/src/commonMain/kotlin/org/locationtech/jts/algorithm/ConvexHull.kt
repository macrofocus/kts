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

import org.locationtech.jts.algorithm.Orientation.index
import org.locationtech.jts.algorithm.PointLocation.isInRing
import org.locationtech.jts.geom.*
import org.locationtech.jts.legacy.*
import org.locationtech.jts.util.Assert.equals
import org.locationtech.jts.util.UniqueCoordinateArrayFilter

/**
 * Computes the convex hull of a {@link Geometry}.
 * The convex hull is the smallest convex Geometry that contains all the
 * points in the input Geometry.
 * <p>
 * Uses the Graham Scan algorithm.
 *
 *@version 1.7
 */
open class ConvexHull {
    private var inputPts: Array<Coordinate>
    private var geomFactory: GeometryFactory? = null

    /**
     * Create a new convex hull construction for the input [Geometry].
     */
    constructor(geometry: Geometry) {
        inputPts = extractCoordinates(geometry)
        geomFactory = geometry.factory
    }

    /**
     * Create a new convex hull construction for the input [Coordinate] array.
     */
    constructor(pts: Array<Coordinate>, geomFactory: GeometryFactory?) {
        inputPts = UniqueCoordinateArrayFilter.filterCoordinates(pts)
        this.geomFactory = geomFactory
    }

    private fun extractCoordinates(geom: Geometry): Array<Coordinate> {
        val filter = UniqueCoordinateArrayFilter()
        geom.apply(filter)
        return filter.coordinates
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
    fun getConvexHull(): Geometry? {
        if (inputPts.isEmpty()) {
            return geomFactory!!.createGeometryCollection()
        }
        if (inputPts.size == 1) {
            return geomFactory!!.createPoint(inputPts[0])
        }
        if (inputPts.size == 2) {
            return geomFactory!!.createLineString(inputPts)
        }
        var reducedPts = inputPts
        // use heuristic to reduce points, if large
        if (inputPts.size > 50) {
            reducedPts = reduce(inputPts)
        }
        // sort points for Graham scan.
        val sortedPts = preSort(reducedPts)

        // Use Graham scan to find convex hull.
        val cHS: Stack<Coordinate> = grahamScan(sortedPts)

        // Convert stack to an array.
        val cH = toCoordinateArray(cHS)

        // Convert array to appropriate output geometry.
        return lineOrPolygon(cH)
    }

    /**
     * An alternative to Stack.toArray, which is not present in earlier versions
     * of Java.
     */
    protected fun toCoordinateArray(stack: Stack<Coordinate>): Array<Coordinate> {
        val coordinates = arrayOfNulls<Coordinate>(stack.size)
        for (i in stack.indices) {
            val coordinate = stack.get(i)
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
     * @param pts the points to reduce
     * @return the reduced list of points (at least 3)
     */
    private fun reduce(inputPts: Array<Coordinate>): Array<Coordinate> {
        //Coordinate[] polyPts = computeQuad(inputPts);
        val polyPts = computeOctRing(inputPts) ?: return inputPts
        //Coordinate[] polyPts = null;

        // unable to compute interior polygon for some reason

//    LinearRing ring = geomFactory.createLinearRing(polyPts);
//    System.out.println(ring);

        // add points defining polygon
        val reducedSet: TreeSet<Coordinate> = TreeSet()
        for (i in polyPts.indices) {
            reducedSet.add(polyPts[i])
        }
        /**
         * Add all unique points not in the interior poly.
         * CGAlgorithms.isPointInRing is not defined for points actually on the ring,
         * but this doesn't matter since the points of the interior polygon
         * are forced to be in the reduced set.
         */
        for (i in inputPts.indices) {
            if (!isInRing(inputPts[i], polyPts)) {
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

    private fun preSort(pts: Array<Coordinate>): Array<Coordinate> {
        var t: Coordinate?

        // find the lowest point in the set. If two or more points have
        // the same minimum y coordinate choose the one with the minimu x.
        // This focal point is put in array location pts[0].
        for (i in 1 until pts.size) {
            if (pts[i].y < pts[0].y || pts[i].y == pts[0].y && pts[i].x < pts[0].x) {
                t = pts[0]
                pts[0] = pts[i]
                pts[i] = t
            }
        }

        // sort the points radially around the focal point.
        pts.sortWith(RadialComparator(pts[0]), 1, pts.size)
//        java.util.Arrays.sort(pts, 1, pts.size, RadialComparator(pts[0]))

        //radialSort(pts);
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
            p = ps.pop()!!
            // check for empty stack to guard against robustness problems
            while (!ps.empty() &&
                index((ps.peek() as Coordinate), p, c[i]) > 0
            ) {
                p = ps.pop()!!
            }
            ps.push(p)
            ps.push(c[i])
        }
        ps.push(c[0])
        return ps
    }

    /**
     * @return    whether the three coordinates are collinear and c2 lies between
     * c1 and c3 inclusive
     */
    private fun isBetween(c1: Coordinate, c2: Coordinate?, c3: Coordinate?): Boolean {
        if (index(c1, c2!!, c3!!) != 0) {
            return false
        }
        if (c1.x != c3.x) {
            if (c1.x <= c2.x && c2.x <= c3.x) {
                return true
            }
            if (c3.x <= c2.x && c2.x <= c1.x) {
                return true
            }
        }
        if (c1.y != c3.y) {
            if (c1.y <= c2.y && c2.y <= c3.y) {
                return true
            }
            if (c3.y <= c2.y && c2.y <= c1.y) {
                return true
            }
        }
        return false
    }

    private fun computeOctRing(inputPts: Array<Coordinate>): Array<Coordinate>? {
        val octPts = computeOctPts(inputPts)
        val coordList = CoordinateList()
        coordList.add(octPts, false)

        // points must all lie in a line
        if (coordList.size < 3) {
            return null
        }
        coordList.closeRing()
        return coordList.toCoordinateArray()
    }

    private fun computeOctPts(inputPts: Array<Coordinate>): Array<Coordinate> {
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

/*
  // MD - no longer used, but keep for reference purposes
  private Coordinate[] computeQuad(Coordinate[] inputPts) {
    BigQuad bigQuad = bigQuad(inputPts);

    // Build a linear ring defining a big poly.
    ArrayList bigPoly = new ArrayList();
    bigPoly.add(bigQuad.westmost);
    if (! bigPoly.contains(bigQuad.northmost)) {
      bigPoly.add(bigQuad.northmost);
    }
    if (! bigPoly.contains(bigQuad.eastmost)) {
      bigPoly.add(bigQuad.eastmost);
    }
    if (! bigPoly.contains(bigQuad.southmost)) {
      bigPoly.add(bigQuad.southmost);
    }
    // points must all lie in a line
    if (bigPoly.size() < 3) {
      return null;
    }
    // closing point
    bigPoly.add(bigQuad.westmost);

    Coordinate[] bigPolyArray = CoordinateArrays.toCoordinateArray(bigPoly);

    return bigPolyArray;
  }

  private BigQuad bigQuad(Coordinate[] pts) {
    BigQuad bigQuad = new BigQuad();
    bigQuad.northmost = pts[0];
    bigQuad.southmost = pts[0];
    bigQuad.westmost = pts[0];
    bigQuad.eastmost = pts[0];
    for (int i = 1; i < pts.length; i++) {
      if (pts[i].getX() < bigQuad.westmost.getX()) {
        bigQuad.westmost = pts[i];
      }
      if (pts[i].getX() > bigQuad.eastmost.getX()) {
        bigQuad.eastmost = pts[i];
      }
      if (pts[i].getY() < bigQuad.southmost.getY()) {
        bigQuad.southmost = pts[i];
      }
      if (pts[i].getY() > bigQuad.northmost.getY()) {
        bigQuad.northmost = pts[i];
      }
    }
    return bigQuad;
  }

  private static class BigQuad {
    public Coordinate northmost;
    public Coordinate southmost;
    public Coordinate westmost;
    public Coordinate eastmost;
  }
  */

    /*
  // MD - no longer used, but keep for reference purposes
  private Coordinate[] computeQuad(Coordinate[] inputPts) {
    BigQuad bigQuad = bigQuad(inputPts);

    // Build a linear ring defining a big poly.
    ArrayList bigPoly = new ArrayList();
    bigPoly.add(bigQuad.westmost);
    if (! bigPoly.contains(bigQuad.northmost)) {
      bigPoly.add(bigQuad.northmost);
    }
    if (! bigPoly.contains(bigQuad.eastmost)) {
      bigPoly.add(bigQuad.eastmost);
    }
    if (! bigPoly.contains(bigQuad.southmost)) {
      bigPoly.add(bigQuad.southmost);
    }
    // points must all lie in a line
    if (bigPoly.size() < 3) {
      return null;
    }
    // closing point
    bigPoly.add(bigQuad.westmost);

    Coordinate[] bigPolyArray = CoordinateArrays.toCoordinateArray(bigPoly);

    return bigPolyArray;
  }

  private BigQuad bigQuad(Coordinate[] pts) {
    BigQuad bigQuad = new BigQuad();
    bigQuad.northmost = pts[0];
    bigQuad.southmost = pts[0];
    bigQuad.westmost = pts[0];
    bigQuad.eastmost = pts[0];
    for (int i = 1; i < pts.length; i++) {
      if (pts[i].getX() < bigQuad.westmost.getX()) {
        bigQuad.westmost = pts[i];
      }
      if (pts[i].getX() > bigQuad.eastmost.getX()) {
        bigQuad.eastmost = pts[i];
      }
      if (pts[i].getY() < bigQuad.southmost.getY()) {
        bigQuad.southmost = pts[i];
      }
      if (pts[i].getY() > bigQuad.northmost.getY()) {
        bigQuad.northmost = pts[i];
      }
    }
    return bigQuad;
  }

  private static class BigQuad {
    public Coordinate northmost;
    public Coordinate southmost;
    public Coordinate westmost;
    public Coordinate eastmost;
  }
  */
    /**
     * @param  vertices  the vertices of a linear ring, which may or may not be
     * flattened (i.e. vertices collinear)
     * @return           a 2-vertex `LineString` if the vertices are
     * collinear; otherwise, a `Polygon` with unnecessary
     * (collinear) vertices removed
     */
    private fun lineOrPolygon(coordinates: Array<Coordinate>): Geometry? {
        var coordinates = coordinates
        coordinates = cleanRing(coordinates)
        if (coordinates.size == 3) {
            return geomFactory!!.createLineString(arrayOf(coordinates[0], coordinates[1]))
            //      return new LineString(new Coordinate[]{coordinates[0], coordinates[1]},
//          geometry.getPrecisionModel(), geometry.getSRID());
        }
        val linearRing: LinearRing = geomFactory!!.createLinearRing(coordinates)
        return geomFactory!!.createPolygon(linearRing)
    }

    /**
     * @param  vertices  the vertices of a linear ring, which may or may not be
     * flattened (i.e. vertices collinear)
     * @return           the coordinates with unnecessary (collinear) vertices
     * removed
     */
    private fun cleanRing(original: Array<Coordinate>): Array<Coordinate> {
        equals(original[0], original[original.size - 1])
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
        val cleanedRingCoordinates = arrayOfNulls<Coordinate>(cleanedRing.size)
        // ToDo: is making a copy really necessary?
        return cleanedRing.toTypedArray().copyOf()
    }


    /**
     * Compares [Coordinate]s for their angle and distance
     * relative to an origin.
     *
     * @author Martin Davis
 * @author Luc Girardin
 * @version 1.7
     */
    private class RadialComparator(private val origin: Coordinate?) : Comparator<Coordinate> {
        override fun compare(o1: Coordinate, o2: Coordinate): Int {
            return polarCompare(origin, o1, o2)
        }

        companion object {
            /**
             * Given two points p and q compare them with respect to their radial
             * ordering about point o.  First checks radial ordering.
             * If points are collinear, the comparison is based
             * on their distance to the origin.
             *
             * p < q iff
             *
             *  * ang(o-p) < ang(o-q) (e.g. o-p-q is CCW)
             *  * or ang(o-p) == ang(o-q) && dist(o,p) < dist(o,q)
             *
             * @param o the origin
             * @param p a point
             * @param q another point
             * @return -1, 0 or 1 depending on whether p is less than,
             * equal to or greater than q
             */
            private fun polarCompare(o: Coordinate?, p: Coordinate, q: Coordinate): Int {
                val dxp = p.x - o!!.x
                val dyp = p.y - o.y
                val dxq = q.x - o.x
                val dyq = q.y - o.y

/*
      // MD - non-robust
      int result = 0;
      double alph = Math.atan2(dxp, dyp);
      double beta = Math.atan2(dxq, dyq);
      if (alph < beta) {
        result = -1;
      }
      if (alph > beta) {
        result = 1;
      }
      if (result !=  0) return result;
      // */
                val orient = index(o, p, q)
                if (orient == Orientation.COUNTERCLOCKWISE) return 1
                if (orient == Orientation.CLOCKWISE) return -1

                // points are collinear - check distance
                val op = dxp * dxp + dyp * dyp
                val oq = dxq * dxq + dyq * dyq
                if (op < oq) {
                    return -1
                }
                return if (op > oq) {
                    1
                } else 0
            }
        }
    }

}