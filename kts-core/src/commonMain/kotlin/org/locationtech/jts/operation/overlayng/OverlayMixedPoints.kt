/*
 * Copyright (c) 2020 Martin Davis.
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.operation.overlayng

import org.locationtech.jts.algorithm.locate.IndexedPointInAreaLocator
import org.locationtech.jts.algorithm.locate.PointOnGeometryLocator
import org.locationtech.jts.geom.*
import org.locationtech.jts.util.Assert

/**
 * Computes an overlay where one input is Point(s) and one is not.
 * This class supports overlay being used as an efficient way
 * to find points within or outside a polygon.
 *
 * Input semantics are:
 *
 *  * Duplicates are removed from Point output
 *  * Non-point output is rounded and noded using the given precision model
 *
 * Output semantics are:
 *
 * <ii>An empty result is an empty atomic geometry
 * with dimension determined by the inputs and the operation,
 * as per overlay semantics *
</ii> *
 * For efficiency the following optimizations are used:
 *
 *  * Input points are not included in the noding of the non-point input geometry
 * (in particular, they do not participate in snap-rounding if that is used).
 *  * If the non-point input geometry is not included in the output
 * it is not rounded and noded.  This means that points
 * are compared to the non-rounded geometry.
 * This will be apparent in the result.
 *
 * @author Martin Davis
 */
internal class OverlayMixedPoints(
    private val opCode: Int,
    geom0: Geometry?,
    geom1: Geometry?,
    private val pm: PrecisionModel?
) {
    private var geomPoint: Geometry? = null
    private var geomNonPointInput: Geometry? = null
    private val geometryFactory: GeometryFactory
    private var isPointRHS = false
    private var geomNonPoint: Geometry? = null
    private var geomNonPointDim = 0
    private var locator: PointOnGeometryLocator? = null
    private val resultDim: Int

    init {
        geometryFactory = geom0!!.factory
        resultDim = OverlayUtil.resultDimension(
            opCode,
            geom0.dimension,
            geom1!!.dimension
        )

        // name the dimensional geometries
        if (geom0.dimension == 0) {
            geomPoint = geom0
            geomNonPointInput = geom1
            isPointRHS = false
        } else {
            geomPoint = geom1
            geomNonPointInput = geom0
            isPointRHS = true
        }
    }// UNION and SYMDIFFERENCE have same output

    // reduce precision of non-point input, if required
    val result: Geometry?
        get() {
            // reduce precision of non-point input, if required
            geomNonPoint = prepareNonPoint(geomNonPointInput)
            geomNonPointDim = geomNonPoint!!.dimension
            locator = createLocator(geomNonPoint)
            val coords = extractCoordinates(geomPoint, pm)
            when (opCode) {
                OverlayNG.INTERSECTION -> return computeIntersection(
                    coords
                )

                OverlayNG.UNION, OverlayNG.SYMDIFFERENCE ->       // UNION and SYMDIFFERENCE have same output
                    return computeUnion(coords)

                OverlayNG.DIFFERENCE -> return computeDifference(
                    coords
                )
            }
            Assert.shouldNeverReachHere("Unknown overlay op code")
            return null
        }

    private fun createLocator(geomNonPoint: Geometry?): PointOnGeometryLocator {
        return if (geomNonPointDim == 2) {
            IndexedPointInAreaLocator(geomNonPoint)
        } else {
            IndexedPointOnLineLocator(geomNonPoint)
        }
    }

    private fun prepareNonPoint(geomInput: Geometry?): Geometry? {
        // if non-point not in output no need to node it
        return if (resultDim == 0) {
            geomInput
        } else OverlayNG.union(geomNonPointInput, pm)

        // Node and round the non-point geometry for output
    }

    private fun computeIntersection(coords: Array<Coordinate>): Geometry? {
        return createPointResult(findPoints(true, coords))
    }

    private fun computeUnion(coords: Array<Coordinate>): Geometry {
        val resultPointList = findPoints(false, coords)
        var resultLineList: List<LineString>? = null
        if (geomNonPointDim == 1) {
            resultLineList = extractLines(geomNonPoint)
        }
        var resultPolyList: List<Polygon>? = null
        if (geomNonPointDim == 2) {
            resultPolyList = extractPolygons(geomNonPoint)
        }
        return OverlayUtil.createResultGeometry(
            resultPolyList,
            resultLineList,
            resultPointList,
            geometryFactory
        )
    }

    private fun computeDifference(coords: Array<Coordinate>): Geometry? {
        return if (isPointRHS) {
            copyNonPoint()
        } else createPointResult(findPoints(false, coords))
    }

    private fun createPointResult(points: List<Point>): Geometry? {
        if (points.isEmpty()) {
            return geometryFactory.createEmpty(0)
        } else if (points.size == 1) {
            return points[0]
        }
        val pointsArray = GeometryFactory.toPointArray(points)
        return geometryFactory.createMultiPoint(pointsArray)
    }

    private fun findPoints(isCovered: Boolean, coords: Array<Coordinate>): List<Point> {
        val resultCoords: MutableSet<Coordinate> = HashSet<Coordinate>()
        // keep only points contained
        for (coord in coords) {
            if (hasLocation(isCovered, coord)) {
                // copy coordinate to avoid aliasing
                resultCoords.add(coord.copy())
            }
        }
        return createPoints(resultCoords)
    }

    private fun createPoints(coords: Set<Coordinate>): List<Point> {
        val points: MutableList<Point> = ArrayList<Point>()
        for (coord in coords) {
            val point = geometryFactory.createPoint(coord)
            points.add(point)
        }
        return points
    }

    private fun hasLocation(isCovered: Boolean, coord: Coordinate): Boolean {
        val isExterior = Location.EXTERIOR == locator!!.locate(coord)
        return if (isCovered) {
            !isExterior
        } else isExterior
    }

    /**
     * Copy the non-point input geometry if not
     * already done by precision reduction process.
     *
     * @return a copy of the non-point geometry
     */
    private fun copyNonPoint(): Geometry? {
        return if (geomNonPointInput !== geomNonPoint) geomNonPoint else geomNonPoint!!.copy()
    }

    companion object {
        fun overlay(opCode: Int, geom0: Geometry?, geom1: Geometry?, pm: PrecisionModel?): Geometry? {
            val overlay = OverlayMixedPoints(opCode, geom0, geom1, pm)
            return overlay.result
        }

        private fun extractCoordinates(points: Geometry?, pm: PrecisionModel?): Array<Coordinate> {
            val coords = CoordinateList()
            val n = points!!.numGeometries
            for (i in 0 until n) {
                val point = points.getGeometryN(i) as Point
                if (point.isEmpty) continue
                val coord: Coordinate? = OverlayUtil.round(point, pm)
                coords.add(coord, true)
            }
            return coords.toCoordinateArray()
        }

        private fun extractPolygons(geom: Geometry?): List<Polygon> {
            val list: MutableList<Polygon> = ArrayList<Polygon>()
            for (i in 0 until geom!!.numGeometries) {
                val poly = geom.getGeometryN(i) as Polygon
                if (!poly.isEmpty) {
                    list.add(poly)
                }
            }
            return list
        }

        private fun extractLines(geom: Geometry?): List<LineString> {
            val list: MutableList<LineString> = ArrayList<LineString>()
            for (i in 0 until geom!!.numGeometries) {
                val line = geom.getGeometryN(i) as LineString
                if (!line.isEmpty) {
                    list.add(line)
                }
            }
            return list
        }
    }
}