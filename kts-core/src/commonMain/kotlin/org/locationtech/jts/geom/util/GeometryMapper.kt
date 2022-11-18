/*
 * Copyright (c) 2016 Martin Davis.
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
package org.locationtech.jts.geom.util

import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryCollection
import kotlin.jvm.JvmStatic

/**
 * Methods to map various collections
 * of [Geometry]s
 * via defined mapping functions.
 *
 * @author Martin Davis
 */
object GeometryMapper {
    /**
     * Maps the members of a [Geometry]
     * (which may be atomic or composite)
     * into another <tt>Geometry</tt> of most specific type.
     * <tt>null</tt> results are skipped.
     * In the case of hierarchical [GeometryCollection]s,
     * only the first level of members are mapped.
     *
     * @param geom the input atomic or composite geometry
     * @param op the mapping operation
     * @return a result collection or geometry of most specific type
     */
    fun map(geom: Geometry, op: MapOp): Geometry {
        val mapped: MutableList<Geometry> = ArrayList()
        for (i in 0 until geom.numGeometries) {
            val g = op.map(geom.getGeometryN(i))
            if (g != null) mapped.add(g)
        }
        return geom.factory.buildGeometry(mapped)
    }

    fun map(geoms: Collection<*>, op: MapOp): Collection<*> {
        val mapped: MutableList<Geometry> = ArrayList()
        val i = geoms.iterator()
        while (i.hasNext()) {
            val g = i.next() as Geometry
            val gr = op.map(g)
            if (gr != null) mapped.add(gr)
        }
        return mapped
    }

    /**
     * Maps the atomic elements of a [Geometry]
     * (which may be atomic or composite)
     * using a [MapOp] mapping operation
     * into an atomic <tt>Geometry</tt> or a flat collection
     * of the most specific type.
     * <tt>null</tt> and empty values returned from the mapping operation
     * are discarded.
     *
     * @param geom the geometry to map
     * @param emptyDim the dimension of empty geometry to create
     * @param op the mapping operation
     * @return the mapped result
     */
    @JvmStatic
    fun flatMap(geom: Geometry, emptyDim: Int, op: MapOp): Geometry {
        val mapped: MutableList<Geometry> = ArrayList()
        flatMap(geom, op, mapped)
        if (mapped.size == 0) {
            return geom.factory.createEmpty(emptyDim)
        }
        return if (mapped.size == 1) mapped[0] else geom.factory.buildGeometry(mapped)
    }

    private fun flatMap(geom: Geometry, op: MapOp, mapped: MutableList<Geometry>) {
        for (i in 0 until geom.numGeometries) {
            val g = geom.getGeometryN(i)
            if (g is GeometryCollection) {
                flatMap(g, op, mapped)
            } else {
                val res = op.map(g)
                if (res != null && !res.isEmpty) {
                    addFlat(res, mapped)
                }
            }
        }
    }

    private fun addFlat(geom: Geometry, geomList: MutableList<Geometry>) {
        if (geom.isEmpty) return
        if (geom is GeometryCollection) {
            for (i in 0 until geom.numGeometries) {
                addFlat(geom.getGeometryN(i), geomList)
            }
        } else {
            geomList.add(geom)
        }
    }

    /**
     * An interface for geometry functions that map a geometry input to a geometry output.
     * The output may be <tt>null</tt> if there is no valid output value for
     * the given input value.
     *
     * @author Martin Davis
     */
    interface MapOp {
        /**
         * Maps a geometry value into another value.
         *
         * @param geom the input geometry
         * @return a result geometry
         */
        fun map(geom: Geometry): Geometry?
    }
}