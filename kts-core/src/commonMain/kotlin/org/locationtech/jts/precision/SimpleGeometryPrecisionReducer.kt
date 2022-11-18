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
package org.locationtech.jts.precision

import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.util.GeometryEditor

/**
 * Reduces the precision of the coordinates of a [Geometry]
 * according to the supplied [PrecisionModel], without
 * attempting to preserve valid topology.
 *
 * In the case of [Polygonal] geometries,
 * the topology of the resulting geometry may be invalid if
 * topological collapse occurs due to coordinates being shifted.
 * It is up to the client to check this and handle it if necessary.
 * Collapses may not matter for some uses.  An example
 * is simplifying the input to the buffer algorithm.
 * The buffer algorithm does not depend on the validity of the input geometry.
 *
 * @version 1.7
 *
 */
@Deprecated("use GeometryPrecisionReducer")
class SimpleGeometryPrecisionReducer(private val newPrecisionModel: PrecisionModel) {
    private var removeCollapsed = true
    private var changePrecisionModel = false

    /**
     * Sets whether the reduction will result in collapsed components
     * being removed completely, or simply being collapsed to an (invalid)
     * Geometry of the same type.
     * The default is to remove collapsed components.
     *
     * @param removeCollapsed if `true` collapsed components will be removed
     */
    fun setRemoveCollapsedComponents(removeCollapsed: Boolean) {
        this.removeCollapsed = removeCollapsed
    }

    /**
     * Sets whether the [PrecisionModel] of the new reduced Geometry
     * will be changed to be the [PrecisionModel] supplied to
     * specify the precision reduction.
     *
     * The default is to **not** change the precision model
     *
     * @param changePrecisionModel if `true` the precision model of the created Geometry will be the
     * the precisionModel supplied in the constructor.
     */
    fun setChangePrecisionModel(changePrecisionModel: Boolean) {
        this.changePrecisionModel = changePrecisionModel
    }

    fun reduce(geom: Geometry): Geometry? {
        val geomEdit: GeometryEditor = if (changePrecisionModel) {
            val newFactory = GeometryFactory(newPrecisionModel, geom.factory.sRID)
            GeometryEditor(newFactory)
        } else  // don't change geometry factory
            GeometryEditor()
        return geomEdit.edit(geom, PrecisionReducerCoordinateOperation())
    }

    private inner class PrecisionReducerCoordinateOperation : GeometryEditor.CoordinateOperation() {
        override fun edit(coordinates: Array<Coordinate>?, geom: Geometry): Array<Coordinate>? {
            if (coordinates!!.isEmpty()) return null
            val reducedCoords = arrayOfNulls<Coordinate>(
                coordinates.size
            )
            // copy coordinates and reduce
            for (i in coordinates.indices) {
                val coord = Coordinate(coordinates[i]!!)
                newPrecisionModel.makePrecise(coord)
                reducedCoords[i] = coord
            }
            // remove repeated points, to simplify returned geometry as much as possible
            val noRepeatedCoordList = CoordinateList(reducedCoords.requireNoNulls(), false)
            val noRepeatedCoords = noRepeatedCoordList.toCoordinateArray()

            /**
             * Check to see if the removal of repeated points
             * collapsed the coordinate List to an invalid length
             * for the type of the parent geometry.
             * It is not necessary to check for Point collapses, since the coordinate list can
             * never collapse to less than one point.
             * If the length is invalid, return the full-length coordinate array
             * first computed, or null if collapses are being removed.
             * (This may create an invalid geometry - the client must handle this.)
             */
            var minLength = 0
            if (geom is LineString) minLength = 2
            if (geom is LinearRing) minLength = 4
            var collapsedCoords: Array<Coordinate?>? = reducedCoords
            if (removeCollapsed) collapsedCoords = null

            // return null or orignal length coordinate array
            return if (noRepeatedCoords.size < minLength) {
                collapsedCoords?.requireNoNulls()
            } else noRepeatedCoords

            // ok to return shorter coordinate array
        }
    }

    companion object {
        /**
         * Convenience method for doing precision reduction on a single geometry,
         * with collapses removed and keeping the geometry precision model the same.
         *
         * @param g
         * @param precModel
         * @return the reduced geometry
         */
        fun reduce(g: Geometry, precModel: PrecisionModel): Geometry? {
            val reducer = SimpleGeometryPrecisionReducer(precModel)
            return reducer.reduce(g)
        }
    }
}