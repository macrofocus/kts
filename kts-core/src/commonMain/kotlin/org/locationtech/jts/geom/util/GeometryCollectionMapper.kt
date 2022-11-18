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
package org.locationtech.jts.geom.util

import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryCollection
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.util.GeometryMapper.MapOp

/**
 * Maps the members of a [GeometryCollection]
 * into another <tt>GeometryCollection</tt> via a defined
 * mapping function.
 *
 * @author Martin Davis
 * @author Luc Girardin
 */
class GeometryCollectionMapper(mapOp: MapOp) {
    private val mapOp: MapOp = mapOp
    fun map(gc: GeometryCollection): GeometryCollection {
        val mapped: MutableList<Geometry> = ArrayList()
        for (i in 0 until gc.numGeometries) {
            val g = mapOp.map(gc.getGeometryN(i))
            if (!g!!.isEmpty) mapped.add(g)
        }
        return gc.factory.createGeometryCollection(
            GeometryFactory.toGeometryArray(mapped)
        )
    }

    companion object {
        fun map(gc: GeometryCollection, op: MapOp): GeometryCollection {
            val mapper = GeometryCollectionMapper(op)
            return mapper.map(gc)
        }
    }

}