/*
 * Copyright (c) 2016 Vivid Solutions.
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
import org.locationtech.jts.geom.GeometryCollection
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Polygonal

/**
 * Combines [Geometry]s
 * to produce a [GeometryCollection] of the most appropriate type.
 * Input geometries which are already collections
 * will have their elements extracted first.
 * No validation of the result geometry is performed.
 * (The only case where invalidity is possible is where [Polygonal] geometries
 * are combined and result in a self-intersection).
 *
 * @author mbdavis
 * @see GeometryFactory.buildGeometry
 */
class GeometryCombiner(geoms: Collection<*>) {
    private val geomFactory: GeometryFactory?
    private val skipEmpty = false
    private val inputGeoms: Collection<*>

    /**
     * Computes the combination of the input geometries
     * to produce the most appropriate [Geometry] or [GeometryCollection]
     *
     * @return a Geometry which is the combination of the inputs
     */
    fun combine(): Geometry? {
        val elems: MutableList<Geometry> = ArrayList()
        val i = inputGeoms.iterator()
        while (i.hasNext()) {
            val g = i.next() as Geometry
            extractElements(g, elems)
        }
        return if (elems.size == 0) {
            geomFactory?.createGeometryCollection()
        } else geomFactory!!.buildGeometry(elems)
        // return the "simplest possible" geometry
    }

    private fun extractElements(geom: Geometry?, elems: MutableList<Geometry>) {
        if (geom == null) return
        for (i in 0 until geom.numGeometries) {
            val elemGeom = geom.getGeometryN(i)
            if (skipEmpty && elemGeom.isEmpty) continue
            elems.add(elemGeom)
        }
    }

    companion object {
        /**
         * Combines a collection of geometries.
         *
         * @param geoms the geometries to combine
         * @return the combined geometry
         */
        fun combine(geoms: Collection<Geometry>): Geometry? {
            val combiner = GeometryCombiner(geoms)
            return combiner.combine()
        }

        /**
         * Combines two geometries.
         *
         * @param g0 a geometry to combine
         * @param g1 a geometry to combine
         * @return the combined geometry
         */
        fun combine(g0: Geometry, g1: Geometry): Geometry? {
            val combiner = GeometryCombiner(createList(g0, g1))
            return combiner.combine()
        }

        /**
         * Combines three geometries.
         *
         * @param g0 a geometry to combine
         * @param g1 a geometry to combine
         * @param g2 a geometry to combine
         * @return the combined geometry
         */
        fun combine(g0: Geometry, g1: Geometry, g2: Geometry): Geometry? {
            val combiner = GeometryCombiner(createList(g0, g1, g2))
            return combiner.combine()
        }

        /**
         * Creates a list from two items
         *
         * @param obj0
         * @param obj1
         * @return a List containing the two items
         */
        private fun createList(obj0: Geometry, obj1: Geometry): List<Geometry> {
            val list: MutableList<Geometry> = ArrayList()
            list.add(obj0)
            list.add(obj1)
            return list
        }

        /**
         * Creates a list from two items
         *
         * @param obj0
         * @param obj1
         * @return a List containing the two items
         */
        private fun createList(obj0: Geometry, obj1: Geometry, obj2: Geometry): List<Geometry> {
            val list: MutableList<Geometry> = ArrayList()
            list.add(obj0)
            list.add(obj1)
            list.add(obj2)
            return list
        }

        /**
         * Extracts the GeometryFactory used by the geometries in a collection
         *
         * @param geoms
         * @return a GeometryFactory
         */
        fun extractFactory(geoms: Collection<*>): GeometryFactory? {
            return if (geoms.isEmpty()) null else (geoms.iterator().next() as Geometry).factory
        }
    }

    /**
     * Creates a new combiner for a collection of geometries
     *
     * @param geoms the geometries to combine
     */
    init {
        geomFactory = extractFactory(geoms)
        inputGeoms = geoms
    }
}