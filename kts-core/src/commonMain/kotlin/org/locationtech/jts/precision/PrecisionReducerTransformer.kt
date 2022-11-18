/*
 * Copyright (c) 2021 Martin Davis.
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
package org.locationtech.jts.precision

import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.util.GeometryTransformer
import org.locationtech.jts.operation.overlayng.PrecisionReducer.reducePrecision

/**
 * A transformer to reduce the precision of geometry in a
 * topologically valid way.
 * Repeated points are removed.
 * If geometry elements collapse below their valid length,
 * they may be removed
 * by specifying `isRemoveCollapsed` `true`.
 *
 * @author mdavis
 */
internal class PrecisionReducerTransformer(private val targetPM: PrecisionModel, private val isRemoveCollapsed: Boolean = false) :
    GeometryTransformer() {

    override fun transformCoordinates(
        coordinates: CoordinateSequence?, parent: Geometry?
    ): CoordinateSequence? {
        if (coordinates!!.size() == 0) return null
        var coordsReduce = reduceCompress(coordinates)

        /**
         * Check if the removal of repeated points collapsed the coordinate
         * list to an invalid size for the type of the parent geometry. It is not
         * necessary to check for Point collapses, since the coordinate list can
         * never collapse to less than one point. If the size is invalid, return
         * the full-size coordinate array first computed, or null if collapses are
         * being removed. (This may create an invalid geometry - the client must
         * handle this.)
         */
        var minSize = 0
        if (parent is LineString) minSize = 2
        if (parent is LinearRing) minSize = LinearRing.MINIMUM_VALID_SIZE
        /**
         * Handle collapse. If specified return null so parent geometry is removed or empty,
         * otherwise extend to required length.
         */
        if (coordsReduce.size < minSize) {
            if (isRemoveCollapsed) {
                return null
            }
            coordsReduce = extend(coordsReduce, minSize)
        }
        return factory!!.coordinateSequenceFactory.create(coordsReduce)
    }

    private fun extend(coords: Array<Coordinate>, minLength: Int): Array<Coordinate> {
        if (coords.size >= minLength) return coords
        val exCoords = arrayOfNulls<Coordinate>(minLength)
        for (i in exCoords.indices) {
            val iSrc = if (i < coords.size) i else coords.size - 1
            exCoords[i] = coords[iSrc]!!.copy()
        }
        return exCoords.requireNoNulls()
    }

    private fun reduceCompress(coordinates: CoordinateSequence): Array<Coordinate> {
        val noRepeatCoordList = CoordinateList()
        // copy coordinates and reduce
        for (i in 0 until coordinates.size()) {
            val coord = coordinates.getCoordinate(i).copy()
            targetPM.makePrecise(coord)
            noRepeatCoordList.add(coord, false)
        }
        // remove repeated points, to simplify geometry as much as possible
        return noRepeatCoordList.toCoordinateArray()
    }

    override fun transformPolygon(geom: Polygon, parent: Geometry?): Geometry {
        return reduceArea(geom)
    }

    override fun transformMultiPolygon(geom: MultiPolygon, parent: Geometry?): Geometry {
        return reduceArea(geom)
    }

    private fun reduceArea(geom: Geometry): Geometry {
        return reducePrecision(geom, targetPM)
    }

    companion object {
        fun reduce(geom: Geometry?, targetPM: PrecisionModel, isRemoveCollapsed: Boolean): Geometry {
            val trans = PrecisionReducerTransformer(targetPM, isRemoveCollapsed)
            return trans.transform(geom!!)
        }
    }
}