/*
 * Copyright (c) 2019 Martin Davis.
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.operation.union

import org.locationtech.jts.geom.*
import org.locationtech.jts.util.Assert.shouldNeverReachHere

/**
 * Extracts atomic elements from
 * input geometries or collections,
 * recording the dimension found.
 * Empty geometries are discarded since they
 * do not contribute to the result of [UnaryUnionOp].
 *
 * @author Martin Davis
 * @author Luc Girardin
 */
internal class InputExtracter : GeometryFilter {
    /**
     * Gets the geometry factory from the extracted geometry,
     * if there is one.
     * If an empty collection was extracted, will return `null`.
     *
     * @return a geometry factory, or null if one could not be determined
     */
    var factory: GeometryFactory? = null
        private set
    private val polygons: MutableList<Polygon> = ArrayList()
    private val lines: MutableList<LineString> = ArrayList()
    private val points: MutableList<Point> = ArrayList()
    /**
     * Gets the maximum dimension extracted.
     *
     * @return the maximum extracted dimension
     */
    /**
     * The default dimension for an empty GeometryCollection
     */
    var dimension = Dimension.FALSE
        private set

    /**
     * Tests whether there were any non-empty geometries extracted.
     *
     * @return true if there is a non-empty geometry present
     */
    val isEmpty: Boolean
        get() = (polygons.isEmpty()
                && lines.isEmpty()
                && points.isEmpty())

    /**
     * Gets the extracted atomic geometries of the given dimension `dim`.
     *
     * @param dim the dimension of geometry to return
     * @return a list of the extracted geometries of dimension dim.
     */
    fun getExtract(dim: Int): List<Geometry>? {
        when (dim) {
            0 -> return points
            1 -> return lines
            2 -> return polygons
        }
        shouldNeverReachHere("Invalid dimension: $dim")
        return null
    }

    private fun add(geoms: Collection<Geometry>) {
        for (geom in geoms) {
            add(geom)
        }
    }

    private fun add(geom: Geometry) {
        if (factory == null) factory = geom.factory
        geom.apply(this)
    }

    override fun filter(geom: Geometry?) {
        recordDimension(geom!!.dimension)
        if (geom is GeometryCollection) {
            return
        }
        /**
         * Don't keep empty geometries
         */
        if (geom.isEmpty) return
        when (geom) {
            is Polygon -> {
                polygons.add(geom)
                return
            }
            is LineString -> {
                lines.add(geom)
                return
            }
            is Point -> {
                points.add(geom)
                return
            }
            else -> shouldNeverReachHere("Unhandled geometry type: " + geom.geometryType)
        }
    }

    private fun recordDimension(dim: Int) {
        if (dim > dimension) dimension = dim
    }

    companion object {
        /**
         * Extracts elements from a collection of geometries.
         *
         * @param geoms a collection of geometries
         * @return an extracter over the geometries
         */
        fun extract(geoms: Collection<Geometry>): InputExtracter {
            val extracter = InputExtracter()
            extracter.add(geoms)
            return extracter
        }

        /**
         * Extracts elements from a geometry.
         *
         * @param geoms a geometry to extract from
         * @return an extracter over the geometry
         */
        fun extract(geom: Geometry): InputExtracter {
            val extracter = InputExtracter()
            extracter.add(geom)
            return extracter
        }
    }
}