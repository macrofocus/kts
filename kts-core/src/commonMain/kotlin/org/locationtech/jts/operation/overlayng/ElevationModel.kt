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
import org.locationtech.jts.legacy.Math.isNaN
import org.locationtech.jts.math.MathUtil.clamp
import kotlin.jvm.JvmStatic

/**
 * A simple elevation model used to populate missing Z values
 * in overlay results.
 *
 * The model divides the extent of the input geometry(s)
 * into an NxM grid.
 * The default grid size is 3x3.
 * If the input has no extent in the X or Y dimension,
 * that dimension is given grid size 1.
 * The elevation of each grid cell is computed as the average of the Z values
 * of the input vertices in that cell (if any).
 * If a cell has no input vertices within it, it is assigned
 * the average elevation over all cells.
 *
 * If no input vertices have Z values, the model does not assign a Z value.
 *
 * The elevation of an arbitrary location is determined as the
 * Z value of the nearest grid cell.
 *
 * An elevation model can be used to populate missing Z values
 * in an overlay result geometry.
 *
 * @author Martin Davis
 */
internal class ElevationModel(private val extent: Envelope, private var numCellX: Int, private var numCellY: Int) {
    private val cellSizeX: Double = extent.width / numCellX
    private val cellSizeY: Double
    private val cells: Array<Array<ElevationCell?>>
    private var isInitialized = false
    private var hasZValue = false
    private var averageZ = Double.NaN

    /**
     * Creates a new elevation model covering an extent by a grid of given dimensions.
     *
     * @param extent the XY extent to cover
     * @param numCellX the number of grid cells in the X dimension
     * @param numCellY the number of grid cells in the Y dimension
     */
    init {
        cellSizeY = extent.height / numCellY
        if (cellSizeX <= 0.0) {
            numCellX = 1
        }
        if (cellSizeY <= 0.0) {
            numCellY = 1
        }
        cells = Array(
            numCellX
        ) { arrayOfNulls(numCellY) }
    }

    /**
     * Updates the model using the Z values of a given geometry.
     *
     * @param geom the geometry to scan for Z values
     */
    fun add(geom: Geometry) {
        geom.apply(object : CoordinateSequenceFilter {
            private var hasZ = true
            override fun filter(seq: CoordinateSequence?, i: Int) {
                if (!seq!!.hasZ()) {
                    hasZ = false
                    return
                }
                val z = seq.getOrdinate(i, Coordinate.Z)
                add(
                    seq.getOrdinate(i, Coordinate.X),
                    seq.getOrdinate(i, Coordinate.Y),
                    z
                )
            }

            // no need to scan if no Z present
            override val isDone: Boolean
                get() =// no need to scan if no Z present
                    !hasZ
            override val isGeometryChanged: Boolean
                get() = false
        })
    }

    protected fun add(x: Double, y: Double, z: Double) {
        if (isNaN(z)) return
        hasZValue = true
        val cell = getCell(x, y, true)
        cell!!.add(z)
    }

    private fun init() {
        isInitialized = true
        var numCells = 0
        var sumZ = 0.0
        for (i in cells.indices) {
            for (j in cells[0].indices) {
                val cell = cells[i][j]
                if (cell != null) {
                    cell.compute()
                    numCells++
                    sumZ += cell.z
                }
            }
        }
        averageZ = Double.NaN
        if (numCells > 0) {
            averageZ = sumZ / numCells
        }
    }

    /**
     * Gets the model Z value at a given location.
     * If the location lies outside the model grid extent,
     * this returns the Z value of the nearest grid cell.
     * If the model has no elevation computed (i.e. due
     * to empty input), the value is returned as [Double.NaN].
     *
     * @param x the x ordinate of the location
     * @param y the y ordinate of the location
     * @return the computed model Z value
     */
    fun getZ(x: Double, y: Double): Double {
        if (!isInitialized) init()
        val cell = getCell(x, y, false) ?: return averageZ
        return cell.z
    }

    /**
     * Computes Z values for any missing Z values in a geometry,
     * using the computed model.
     * If the model has no Z value, or the geometry coordinate dimension
     * does not include Z, the geometry is not updated.
     *
     * @param geom the geometry to populate Z values for
     */
    fun populateZ(geom: Geometry?) {
        // short-circuit if no Zs are present in model
        if (!hasZValue) return
        if (!isInitialized) init()
        geom!!.apply(object : CoordinateSequenceFilter {
            override var isDone = false
                private set

            override fun filter(seq: CoordinateSequence?, i: Int) {
                if (!seq!!.hasZ()) {
                    // if no Z then short-circuit evaluation
                    isDone = true
                    return
                }
                // if Z not populated then assign using model
                if (isNaN(seq.getZ(i))) {
                    val z = getZ(
                        seq.getOrdinate(i, Coordinate.X),
                        seq.getOrdinate(i, Coordinate.Y)
                    )
                    seq.setOrdinate(i, Coordinate.Z, z)
                }
            }

            // geometry extent is not changed
            override val isGeometryChanged: Boolean
                get() =// geometry extent is not changed
                    false
        })
    }

    private fun getCell(x: Double, y: Double, isCreateIfMissing: Boolean): ElevationCell? {
        var ix = 0
        if (numCellX > 1) {
            ix = ((x - extent.minX) / cellSizeX).toInt()
            ix = clamp(ix, 0, numCellX - 1)
        }
        var iy = 0
        if (numCellY > 1) {
            iy = ((y - extent.minY) / cellSizeY).toInt()
            iy = clamp(iy, 0, numCellY - 1)
        }
        var cell = cells[ix][iy]
        if (isCreateIfMissing && cell == null) {
            cell = ElevationCell()
            cells[ix][iy] = cell
        }
        return cell
    }

    internal class ElevationCell {
        private var numZ = 0
        private var sumZ = 0.0
        var z = 0.0
            private set

        fun add(z: Double) {
            numZ++
            sumZ += z
        }

        fun compute() {
            z = Double.NaN
            if (numZ > 0) z = sumZ / numZ
        }
    }

    companion object {
        private const val DEFAULT_CELL_NUM = 3

        /**
         * Creates an elevation model from two geometries (which may be null).
         *
         * @param geom1 an input geometry
         * @param geom2 an input geometry, or null
         * @return the elevation model computed from the geometries
         */
        @JvmStatic
        fun create(geom1: Geometry?, geom2: Geometry?): ElevationModel {
            val extent = geom1!!.envelopeInternal.copy()
            if (geom2 != null) {
                extent.expandToInclude(geom2.envelopeInternal)
            }
            val model = ElevationModel(extent, DEFAULT_CELL_NUM, DEFAULT_CELL_NUM)
            if (geom1 != null) model.add(geom1)
            if (geom2 != null) model.add(geom2)
            return model
        }
    }
}