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
package org.locationtech.jts.operation.overlay.validate

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.Location
import org.locationtech.jts.legacy.Math.min
import org.locationtech.jts.operation.overlay.OverlayOp
import org.locationtech.jts.operation.overlay.snap.GeometrySnapper
import org.locationtech.jts.util.Debug

/**
 * Validates that the result of an overlay operation is
 * geometrically correct, within a determined tolerance.
 * Uses fuzzy point location to find points which are
 * definitely in either the interior or exterior of the result
 * geometry, and compares these results with the expected ones.
 *
 * This algorithm is only useful where the inputs are polygonal.
 * This is a heuristic test, and may return false positive results
 * (I.e. it may fail to detect an invalid result.)
 * It should never return a false negative result, however
 * (I.e. it should never report a valid result as invalid.)
 *
 * @author Martin Davis
 * @version 1.7
 * @see OverlayOp
 */
class OverlayResultValidator(a: Geometry, b: Geometry, result: Geometry) {
    private val geom: Array<Geometry>
    private val locFinder: Array<FuzzyPointLocator>
    private val location = IntArray(3)
    var invalidLocation: Coordinate? = null
        private set
    private var boundaryDistanceTolerance = TOLERANCE
    private val testCoords: MutableList<Coordinate> = ArrayList()

    init {
        /**
         * The tolerance to use needs to depend on the size of the geometries.
         * It should not be more precise than double-precision can support.
         */
        boundaryDistanceTolerance = computeBoundaryDistanceTolerance(a, b)
        geom = arrayOf(a, b, result)
        locFinder = arrayOf(
            FuzzyPointLocator(geom[0], boundaryDistanceTolerance),
            FuzzyPointLocator(geom[1], boundaryDistanceTolerance),
            FuzzyPointLocator(geom[2], boundaryDistanceTolerance)
        )
    }

    fun isValid(overlayOp: Int): Boolean {
        addTestPts(geom[0])
        addTestPts(geom[1])

        /*
    System.out.println("OverlayResultValidator: " + isValid);
    System.out.println("G0");
    System.out.println(geom[0]);
    System.out.println("G1");
    System.out.println(geom[1]);
    System.out.println("Result");
    System.out.println(geom[2]);
    */return checkValid(overlayOp)
    }

    private fun addTestPts(g: Geometry) {
        val ptGen = OffsetPointGenerator(g)
        testCoords.addAll(ptGen.getPoints(5 * boundaryDistanceTolerance))
    }

    private fun checkValid(overlayOp: Int): Boolean {
        for (i in testCoords.indices) {
            val pt = testCoords[i]
            if (!checkValid(overlayOp, pt)) {
                invalidLocation = pt
                return false
            }
        }
        return true
    }

    private fun checkValid(overlayOp: Int, pt: Coordinate): Boolean {
        location[0] = locFinder[0].getLocation(pt)
        location[1] = locFinder[1].getLocation(pt)
        location[2] = locFinder[2].getLocation(pt)
        /**
         * If any location is on the Boundary, can't deduce anything, so just return true
         */
        return if (hasLocation(
                location,
                Location.BOUNDARY
            )
        ) true else isValidResult(overlayOp, location)
    }

    private fun isValidResult(overlayOp: Int, location: IntArray): Boolean {
        val expectedInterior: Boolean = OverlayOp.isResultOfOp(location[0], location[1], overlayOp)
        val resultInInterior = location[2] == Location.INTERIOR
        // MD use simpler: boolean isValid = (expectedInterior == resultInInterior);
        val isValid = !(expectedInterior xor resultInInterior)
        if (!isValid) reportResult(overlayOp, location, expectedInterior)
        return isValid
    }

    private fun reportResult(overlayOp: Int, location: IntArray, expectedInterior: Boolean) {
        Debug.println(
            "Overlay result invalid - A:" + Location.toLocationSymbol(location[0])
                    + " B:" + Location.toLocationSymbol(location[1])
                    + " expected:" + (if (expectedInterior) 'i' else 'e')
                    + " actual:" + Location.toLocationSymbol(location[2])
        )
    }

    companion object {
        fun isValid(a: Geometry, b: Geometry, overlayOp: Int, result: Geometry): Boolean {
            val validator = OverlayResultValidator(a, b, result)
            return validator.isValid(overlayOp)
        }

        private fun computeBoundaryDistanceTolerance(g0: Geometry, g1: Geometry): Double {
            return min(
                GeometrySnapper.computeSizeBasedSnapTolerance(g0),
                GeometrySnapper.computeSizeBasedSnapTolerance(g1)
            )
        }

        private const val TOLERANCE = 0.000001
        private fun hasLocation(location: IntArray, loc: Int): Boolean {
            for (i in 0..2) {
                if (location[i] == loc) return true
            }
            return false
        }
    }
}