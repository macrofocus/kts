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
import org.locationtech.jts.util.Assert.isTrue

/**
 * Computes a point in the interior of an areal geometry.
 * The point will lie in the geometry interior
 * in all except certain pathological cases.
 *
 * <h2>Algorithm</h2>
 * For each input polygon:
 *
 *  * Determine a horizontal scan line on which the interior
 * point will be located.
 * To increase the chance of the scan line
 * having non-zero-width intersection with the polygon
 * the scan line Y ordinate is chosen to be near the centre of the polygon's
 * Y extent but distinct from all of vertex Y ordinates.
 *  * Compute the sections of the scan line
 * which lie in the interior of the polygon.
 *  * Choose the widest interior section
 * and take its midpoint as the interior point.
 *
 * The final interior point is chosen as
 * the one occurring in the widest interior section.
 *
 * This algorithm is a tradeoff between performance
 * and point quality (where points further from the geometry
 * boundary are considered to be higher quality)
 * Priority is given to performance.
 * This means that the computed interior point
 * may not be suitable for some uses
 * (such as label positioning).
 *
 * The algorithm handles some kinds of invalid/degenerate geometry,
 * including zero-area and self-intersecting polygons.
 *
 * Empty geometry is handled by returning a `null` point.
 *
 * <h3>KNOWN BUGS</h3>
 *
 *  * If a fixed precision model is used, in some cases this method may return
 * a point which does not lie in the interior.
 *  * If the input polygon is *extremely* narrow the computed point
 * may not lie in the interior of the polygon.
 *
 * @version 1.17
 */
class InteriorPointArea(g: Geometry) {
    /**
     * Gets the computed interior point.
     *
     * @return the coordinate of an interior point
     * or `null` if the input geometry is empty
     */
    var interiorPoint: Coordinate? = null
        private set
    private var maxWidth = -1.0

    /**
     * Creates a new interior point finder for an areal geometry.
     *
     * @param g an areal geometry
     */
    init {
        process(g)
    }

    /**
     * Processes a geometry to determine
     * the best interior point for
     * all component polygons.
     *
     * @param geom the geometry to process
     */
    private fun process(geom: Geometry) {
        if (geom.isEmpty) return
        if (geom is Polygon) {
            processPolygon(geom)
        } else if (geom is GeometryCollection) {
            val gc = geom
            for (i in 0 until gc.numGeometries) {
                process(gc.getGeometryN(i))
            }
        }
    }

    /**
     * Computes an interior point of a component Polygon
     * and updates current best interior point
     * if appropriate.
     *
     * @param polygon the polygon to process
     */
    private fun processPolygon(polygon: Polygon) {
        val intPtPoly = InteriorPointPolygon(polygon)
        intPtPoly.process()
        val width = intPtPoly.width
        if (width > maxWidth) {
            maxWidth = width
            interiorPoint = intPtPoly.interiorPoint
        }
    }

    /**
     * Computes an interior point in a single [Polygon],
     * as well as the width of the scan-line section it occurs in
     * to allow choosing the widest section occurrence.
     *
     * @author mdavis
     */
    private class InteriorPointPolygon(private val polygon: Polygon) {
        private val interiorPointY: Double = ScanLineYOrdinateFinder.getScanLineY(polygon)

        /**
         * Gets the width of the scanline section containing the interior point.
         * Used to determine the best point to use.
         *
         * @return the width
         */
        var width = 0.0
            private set

        /**
         * Gets the computed interior point.
         *
         * @return the interior point coordinate,
         * or `null` if the input geometry is empty
         */
        var interiorPoint: Coordinate? = null
            private set

        /**
         * Compute the interior point.
         *
         */
        fun process() {
            /**
             * This results in returning a null Coordinate
             */
            if (polygon.isEmpty) return
            /**
             * set default interior point in case polygon has zero area
             */
            interiorPoint = Coordinate(polygon.coordinate)
            val crossings: MutableList<Double> = ArrayList()
            scanRing(polygon.exteriorRing, crossings)
            for (i in 0 until polygon.getNumInteriorRing()) {
                scanRing(polygon.getInteriorRingN(i), crossings)
            }
            findBestMidpoint(crossings)
        }

        private fun scanRing(ring: LinearRing?, crossings: MutableList<Double>) {
            // skip rings which don't cross scan line
            if (!intersectsHorizontalLine(ring!!.envelopeInternal, interiorPointY)) return
            val seq = ring.coordinateSequence
            for (i in 1 until seq!!.size()) {
                val ptPrev = seq.getCoordinate(i - 1)
                val pt = seq.getCoordinate(i)
                addEdgeCrossing(ptPrev, pt, interiorPointY, crossings)
            }
        }

        private fun addEdgeCrossing(p0: Coordinate, p1: Coordinate, scanY: Double, crossings: MutableList<Double>) {
            // skip non-crossing segments
            if (!intersectsHorizontalLine(p0, p1, scanY)) return
            if (!isEdgeCrossingCounted(p0, p1, scanY)) return

            // edge intersects scan line, so add a crossing
            val xInt = intersection(p0, p1, scanY)
            crossings.add(xInt)
            //checkIntersectionDD(p0, p1, scanY, xInt);
        }

        /**
         * Finds the midpoint of the widest interior section.
         * Sets the [.interiorPoint] location
         * and the [.interiorSectionWidth]
         *
         * @param crossings the list of scan-line crossing X ordinates
         */
        private fun findBestMidpoint(crossings: MutableList<Double>) {
            // zero-area polygons will have no crossings
            if (crossings.size == 0) return

            // TODO: is there a better way to verify the crossings are correct?
            isTrue(0 == crossings.size % 2, "Interior Point robustness failure: odd number of scanline crossings")
            crossings.sortWith { d1: Double, d2: Double ->
                org.locationtech.jts.legacy.Math.compare(
                    d1, d2
                )
            }
            /*
       * Entries in crossings list are expected to occur in pairs representing a
       * section of the scan line interior to the polygon (which may be zero-length)
       */
            var i = 0
            while (i < crossings.size) {
                val x1 = crossings[i]
                // crossings count must be even so this should be safe
                val x2 = crossings[i + 1]
                val width = x2 - x1
                if (width > this.width) {
                    this.width = width
                    val interiorPointX = avg(x1, x2)
                    interiorPoint = Coordinate(interiorPointX, interiorPointY)
                }
                i += 2
            }
        }

        companion object {
            /**
             * Tests if an edge intersection contributes to the crossing count.
             * Some crossing situations are not counted,
             * to ensure that the list of crossings
             * captures strict inside/outside topology.
             *
             * @param p0 an endpoint of the segment
             * @param p1 an endpoint of the segment
             * @param scanY the Y-ordinate of the horizontal line
             * @return true if the edge crossing is counted
             */
            private fun isEdgeCrossingCounted(p0: Coordinate, p1: Coordinate, scanY: Double): Boolean {
                val y0 = p0.y
                val y1 = p1.y
                // skip horizontal lines
                if (y0 == y1) return false
                // handle cases where vertices lie on scan-line
                // downward segment does not include start point
                if (y0 == scanY && y1 < scanY) return false
                // upward segment does not include endpoint
                return !(y1 == scanY && y0 < scanY)
            }

            /**
             * Computes the intersection of a segment with a horizontal line.
             * The segment is expected to cross the horizontal line
             * - this condition is not checked.
             * Computation uses regular double-precision arithmetic.
             * Test seems to indicate this is as good as using DD arithmetic.
             *
             * @param p0 an endpoint of the segment
             * @param p1 an endpoint of the segment
             * @param Y  the Y-ordinate of the horizontal line
             * @return
             */
            private fun intersection(
                p0: Coordinate,
                p1: Coordinate,
                Y: Double
            ): Double {
                val x0 = p0.x
                val x1 = p1.x
                if (x0 == x1) return x0

                // Assert: segDX is non-zero, due to previous equality test
                val segDX = x1 - x0
                val segDY = p1.y - p0.y
                val m = segDY / segDX
                return x0 + (Y - p0.y) / m
            }

            /**
             * Tests if an envelope intersects a horizontal line.
             *
             * @param env the envelope to test
             * @param y the Y-ordinate of the horizontal line
             * @return true if the envelope and line intersect
             */
            private fun intersectsHorizontalLine(env: Envelope, y: Double): Boolean {
                if (y < env.minY) return false
                return y <= env.maxY
            }

            /**
             * Tests if a line segment intersects a horizontal line.
             *
             * @param p0 a segment endpoint
             * @param p1 a segment endpoint
             * @param y the Y-ordinate of the horizontal line
             * @return true if the segment and line intersect
             */
            private fun intersectsHorizontalLine(p0: Coordinate, p1: Coordinate, y: Double): Boolean {
                // both ends above?
                if (p0.y > y && p1.y > y) return false
                // both ends below?
                return !(p0.y < y && p1.y < y)
                // segment must intersect line
            } /*
    // for testing only
    private static void checkIntersectionDD(Coordinate p0, Coordinate p1, double scanY, double xInt) {
      double xIntDD = intersectionDD(p0, p1, scanY);
      System.out.println(
          ((xInt != xIntDD) ? ">>" : "")
          + "IntPt x - DP: " + xInt + ", DD: " + xIntDD 
          + "   y: " + scanY + "   " + WKTWriter.toLineString(p0, p1) );
    }

    private static double intersectionDD(Coordinate p0, Coordinate p1, double Y) {
      double x0 = p0.getX();
      double x1 = p1.getX();

      if ( x0 == x1 )
        return x0;
      
      DD segDX = DD.valueOf(x1).selfSubtract(x0);
      // Assert: segDX is non-zero, due to previous equality test
      DD segDY = DD.valueOf(p1.getY()).selfSubtract(p0.getY());
      DD m = segDY.divide(segDX);
      DD dy = DD.valueOf(Y).selfSubtract(p0.getY());
      DD dx = dy.divide(m);
      DD xInt = DD.valueOf(x0).selfAdd(dx);
      return xInt.doubleValue();
    }
  */
        }
    }

    /**
     * Finds a safe scan line Y ordinate by projecting
     * the polygon segments
     * to the Y axis and finding the
     * Y-axis interval which contains the centre of the Y extent.
     * The centre of
     * this interval is returned as the scan line Y-ordinate.
     *
     * Note that in the case of (degenerate, invalid)
     * zero-area polygons the computed Y value
     * may be equal to a vertex Y-ordinate.
     *
     * @author mdavis
     */
    private class ScanLineYOrdinateFinder(private val poly: Polygon) {
        private val centreY: Double
        private var hiY = Double.MAX_VALUE
        private var loY = -Double.MAX_VALUE

        init {

            // initialize using extremal values
            hiY = poly.envelopeInternal.maxY
            loY = poly.envelopeInternal.minY
            centreY = avg(loY, hiY)
        }

        val scanLineY: Double
            get() {
                process(poly.exteriorRing)
                for (i in 0 until poly.getNumInteriorRing()) {
                    process(poly.getInteriorRingN(i))
                }
                return avg(hiY, loY)
            }

        private fun process(line: LineString?) {
            val seq = line!!.coordinateSequence
            for (i in 0 until seq!!.size()) {
                val y = seq.getY(i)
                updateInterval(y)
            }
        }

        private fun updateInterval(y: Double) {
            if (y <= centreY) {
                if (y > loY) loY = y
            } else if (y > centreY) {
                if (y < hiY) {
                    hiY = y
                }
            }
        }

        companion object {
            fun getScanLineY(poly: Polygon): Double {
                val finder = ScanLineYOrdinateFinder(poly)
                return finder.scanLineY
            }
        }
    }

    companion object {
        /**
         * Computes an interior point for the
         * polygonal components of a Geometry.
         *
         * @param geom the geometry to compute
         * @return the computed interior point,
         * or `null` if the geometry has no polygonal components
         */
        fun getInteriorPoint(geom: Geometry): Coordinate? {
            val intPt = InteriorPointArea(geom)
            return intPt.interiorPoint
        }

        private fun avg(a: Double, b: Double): Double {
            return (a + b) / 2.0
        }
    }
}