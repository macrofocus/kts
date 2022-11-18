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
package org.locationtech.jts.operation.buffer.validate

import org.locationtech.jts.algorithm.distance.DiscreteHausdorffDistance
import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.util.LinearComponentExtracter
import org.locationtech.jts.geom.util.PolygonExtracter
import org.locationtech.jts.io.WKTWriter
import org.locationtech.jts.legacy.Math.abs
import org.locationtech.jts.operation.distance.DistanceOp
import org.locationtech.jts.util.Debug

/**
 * Validates that a given buffer curve lies an appropriate distance
 * from the input generating it.
 * Useful only for round buffers (cap and join).
 * Can be used for either positive or negative distances.
 *
 * This is a heuristic test, and may return false positive results
 * (I.e. it may fail to detect an invalid result.)
 * It should never return a false negative result, however
 * (I.e. it should never report a valid result as invalid.)
 *
 * @author mbdavis
 */
class BufferDistanceValidator(
    private val input: Geometry,
    private val bufDistance: Double,
    private val result: Geometry
) {
    private var minValidDistance = 0.0
    private var maxValidDistance = 0.0
    private var minDistanceFound = 0.0
    private var maxDistanceFound = 0.0
    private var isValid = true
    var errorMessage: String? = null
        private set
    var errorLocation: Coordinate? = null
        private set

    /**
     * Gets a geometry which indicates the location and nature of a validation failure.
     *
     *
     * The indicator is a line segment showing the location and size
     * of the distance discrepancy.
     *
     * @return a geometric error indicator
     * or null if no error was found
     */
    var errorIndicator: Geometry? = null
        private set

    fun isValid(): Boolean {
        val posDistance: Double = abs(bufDistance)
        val distDelta = MAX_DISTANCE_DIFF_FRAC * posDistance
        minValidDistance = posDistance - distDelta
        maxValidDistance = posDistance + distDelta

        // can't use this test if either is empty
        if (input.isEmpty || result.isEmpty) return true
        if (bufDistance > 0.0) {
            checkPositiveValid()
        } else {
            checkNegativeValid()
        }
        if (VERBOSE) {
            Debug.println(
                "Min Dist= " + minDistanceFound + "  err= "
                        + (1.0 - minDistanceFound / bufDistance)
                        + "  Max Dist= " + maxDistanceFound + "  err= "
                        + (maxDistanceFound / bufDistance - 1.0)
            )
        }
        return isValid
    }

    private fun checkPositiveValid() {
        val bufCurve = result.boundary
        checkMinimumDistance(input, bufCurve, minValidDistance)
        if (!isValid) return
        checkMaximumDistance(input, bufCurve!!, maxValidDistance)
    }

    private fun checkNegativeValid() {
        // Assert: only polygonal inputs can be checked for negative buffers

        // MD - could generalize this to handle GCs too
        if (!(input is Polygon
                    || input is MultiPolygon
                    || input is GeometryCollection)
        ) {
            return
        }
        val inputCurve = getPolygonLines(input)
        checkMinimumDistance(inputCurve, result, minValidDistance)
        if (!isValid) return
        checkMaximumDistance(inputCurve, result, maxValidDistance)
    }

    private fun getPolygonLines(g: Geometry): Geometry {
        val lines: MutableList<Geometry> = ArrayList()
        val lineExtracter = LinearComponentExtracter(lines)
        val polys: MutableList<Polygon> = PolygonExtracter.getPolygons(g)
        val i: Iterator<*> = polys.iterator()
        while (i.hasNext()) {
            val poly = i.next() as Polygon
            poly.apply(lineExtracter)
        }
        return g.factory.buildGeometry(lines)
    }

    /**
     * Checks that two geometries are at least a minimum distance apart.
     *
     * @param g1 a geometry
     * @param g2 a geometry
     * @param minDist the minimum distance the geometries should be separated by
     */
    private fun checkMinimumDistance(g1: Geometry, g2: Geometry?, minDist: Double) {
        val distOp = DistanceOp(g1, g2!!, minDist)
        minDistanceFound = distOp.distance()
        if (minDistanceFound < minDist) {
            isValid = false
            val pts = distOp.nearestPoints()
            errorLocation = distOp.nearestPoints()[1]
            errorIndicator = g1.factory.createLineString(pts.requireNoNulls())
            errorMessage = ("Distance between buffer curve and input is too small "
                    + "(" + minDistanceFound
                    + " at " + WKTWriter.toLineString(pts[0]!!, pts[1]!!)) + " )"
        }
    }

    /**
     * Checks that the furthest distance from the buffer curve to the input
     * is less than the given maximum distance.
     * This uses the Oriented Hausdorff distance metric.
     * It corresponds to finding
     * the point on the buffer curve which is furthest from *some* point on the input.
     *
     * @param input a geometry
     * @param bufCurve a geometry
     * @param maxDist the maximum distance that a buffer result can be from the input
     */
    private fun checkMaximumDistance(input: Geometry, bufCurve: Geometry, maxDist: Double) {
//    BufferCurveMaximumDistanceFinder maxDistFinder = new BufferCurveMaximumDistanceFinder(input);
//    maxDistanceFound = maxDistFinder.findDistance(bufCurve);
        val haus = DiscreteHausdorffDistance(bufCurve, input)
        haus.setDensifyFraction(0.25)
        maxDistanceFound = haus.orientedDistance()
        if (maxDistanceFound > maxDist) {
            isValid = false
            val pts: Array<Coordinate> = haus.coordinates
            errorLocation = pts[1]
            errorIndicator = input.factory.createLineString(pts)
            errorMessage = ("Distance between buffer curve and input is too large "
                    + "(" + maxDistanceFound
                    + " at " + WKTWriter.toLineString(pts[0], pts[1])) + ")"
        }
    } /*
  private void OLDcheckMaximumDistance(Geometry input, Geometry bufCurve, double maxDist)
  {
    BufferCurveMaximumDistanceFinder maxDistFinder = new BufferCurveMaximumDistanceFinder(input);
    maxDistanceFound = maxDistFinder.findDistance(bufCurve);
    
    
    if (maxDistanceFound > maxDist) {
      isValid = false;
      PointPairDistance ptPairDist = maxDistFinder.getDistancePoints();
      errorLocation = ptPairDist.getCoordinate(1);
      errMsg = "Distance between buffer curve and input is too large "
        + "(" + ptPairDist.getDistance()
        + " at " + ptPairDist.toString() +")";
    }
  }
  */

    companion object {
        private const val VERBOSE = false

        /**
         * Maximum allowable fraction of buffer distance the
         * actual distance can differ by.
         * 1% sometimes causes an error - 1.2% should be safe.
         */
        private const val MAX_DISTANCE_DIFF_FRAC = .012
    }
}