/*
 * Copyright (c) 2016 Martin Davis.
 * Copyright (c) 2020 Macrofocus GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.geom.util

import org.locationtech.jts.geom.Geometry

/**
 * Methods to map various collections
 * of [Geometry]s
 * via defined mapping functions.
 *
 * @author Martin Davis
 * @author Luc Girardin
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
     * An interface for geometry functions used for mapping.
     *
     * @author Martin Davis
 * @author Luc Girardin
 */
    interface MapOp {
        /**
         * Computes a new geometry value.
         *
         * @param g the input geometry
         * @return a result geometry
         */
        fun map(g: Geometry): Geometry
    }
}