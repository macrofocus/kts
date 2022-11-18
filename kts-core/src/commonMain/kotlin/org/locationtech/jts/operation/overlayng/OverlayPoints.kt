/*
 * Copyright (c) 2019 Martin Davis.
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.operation.overlayng

import org.locationtech.jts.geom.*

/**
 * Performs an overlay operation on inputs which are both point geometries.
 *
 * Semantics are:
 *
 *  * Points are rounded to the precision model if provided
 *  * Points with identical XY values are merged to a single point
 *  * Extended ordinate values are preserved in the output,
 * apart from merging
 *  * An empty result is returned as `POINT EMPTY`
 *
 * @author Martin Davis
 */
internal class OverlayPoints(
    private val opCode: Int,
    private val geom0: Geometry?,
    private val geom1: Geometry?,
    private val pm: PrecisionModel?
) {
    private val geometryFactory: GeometryFactory = geom0!!.factory
    private var resultList: ArrayList<Point>? = null

    /**
     * Gets the result of the overlay.
     *
     * @return the overlay result
     */
    val result: Geometry?
        get() {
            val map0: Map<Coordinate, Point> = buildPointMap(
                geom0
            )
            val map1: Map<Coordinate, Point> = buildPointMap(
                geom1
            )
            resultList = ArrayList()
            when (opCode) {
                OverlayNG.INTERSECTION -> computeIntersection(
                    map0,
                    map1,
                    resultList!!
                )

                OverlayNG.UNION -> computeUnion(
                    map0,
                    map1,
                    resultList!!
                )

                OverlayNG.DIFFERENCE -> computeDifference(
                    map0,
                    map1,
                    resultList!!
                )

                OverlayNG.SYMDIFFERENCE -> {
                    computeDifference(map0, map1, resultList!!)
                    computeDifference(map1, map0, resultList!!)
                }
            }
            return if (resultList!!.isEmpty()) OverlayUtil.createEmptyResult(
                0,
                geometryFactory
            ) else geometryFactory.buildGeometry(resultList!!)
        }

    private fun computeIntersection(
        map0: Map<Coordinate, Point>, map1: Map<Coordinate, Point>,
        resultList: ArrayList<Point>
    ) {
        for ((key, value) in map0) {
            if (map1.containsKey(key)) {
                resultList.add(copyPoint(value))
            }
        }
    }

    private fun computeDifference(
        map0: Map<Coordinate, Point>, map1: Map<Coordinate, Point>,
        resultList: ArrayList<Point>
    ) {
        for ((key, value) in map0) {
            if (!map1.containsKey(key)) {
                resultList.add(copyPoint(value))
            }
        }
    }

    private fun computeUnion(
        map0: Map<Coordinate, Point>, map1: Map<Coordinate, Point>,
        resultList: ArrayList<Point>
    ) {

        // copy all A points
        for (p in map0.values) {
            resultList.add(copyPoint(p))
        }
        for ((key, value) in map1) {
            if (!map0.containsKey(key)) {
                resultList.add(copyPoint(value))
            }
        }
    }

    private fun copyPoint(pt: Point): Point {
        // if pm is floating, the point coordinate is not changed
        if (OverlayUtil.isFloating(pm)) return pt.copy() as Point

        // pm is fixed.  Round off X&Y ordinates, copy other ordinates unchanged
        val seq = pt.coordinateSequence
        val seq2 = seq!!.copy()
        seq2.setOrdinate(0, CoordinateSequence.X, pm!!.makePrecise(seq.getX(0)))
        seq2.setOrdinate(0, CoordinateSequence.Y, pm.makePrecise(seq.getY(0)))
        return geometryFactory.createPoint(seq2)
    }

    private fun buildPointMap(geom: Geometry?): HashMap<Coordinate, Point> {
        val map: HashMap<Coordinate, Point> = HashMap()
        for (i in 0 until geom!!.numGeometries) {
            val elt = geom.getGeometryN(i) as? Point
                ?: throw IllegalArgumentException("Non-point geometry input to point overlay")
            // don't add empty points
            if (elt.isEmpty) continue
            val pt = elt
            val p = roundCoord(pt, pm)
            /**
             * Only add first occurrence of a point.
             * This provides the merging semantics of overlay
             */
            if (!map.containsKey(p)) map[p] = pt
        }
        return map
    }

    companion object {
        /**
         * Performs an overlay operation on inputs which are both point geometries.
         *
         * @param geom0 the first geometry argument
         * @param geom1 the second geometry argument
         * @param opCode the code for the desired overlay operation
         * @param pm the precision model to use
         * @return the result of the overlay operation
         */
        fun overlay(opCode: Int, geom0: Geometry?, geom1: Geometry?, pm: PrecisionModel?): Geometry? {
            val overlay = OverlayPoints(opCode, geom0, geom1, pm)
            return overlay.result
        }

        /**
         * Round the key point if precision model is fixed.
         * Note: return value is only copied if rounding is performed.
         *
         * @param pt
         * @return
         */
        fun roundCoord(pt: Point, pm: PrecisionModel?): Coordinate {
            val p = pt.coordinate
            if (OverlayUtil.isFloating(pm)) return p!!
            val p2 = p!!.copy()
            pm!!.makePrecise(p2)
            return p2
        }
    }
}