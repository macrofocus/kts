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

import org.locationtech.jts.geom.*
import kotlin.jvm.JvmOverloads
import kotlin.reflect.KClass

/**
 * Extracts the components of a given type from a [Geometry].
 *
 * @version 1.7
 */
class GeometryExtracter : GeometryFilter {
    private var geometryType: String?
    private var comps: MutableList

    /**
     * Constructs a filter with a list in which to store the elements found.
     *
     * @param clz the class of the components to extract (null means all types)
     * @param comps the list to extract into
     */
    @Deprecated("")
    constructor(clz: KClass<*>, comps: MutableList) {
        geometryType = toGeometryType(clz)
        this.comps = comps
    }

    /**
     * Constructs a filter with a list in which to store the elements found.
     *
     * @param geometryType Geometry type to extract (null means all types)
     * @param comps the list to extract into
     */
    constructor(geometryType: String?, comps: MutableList) {
        this.geometryType = geometryType
        this.comps = comps
    }

    override fun filter(geom: Geometry) {
        if (geometryType == null || isOfType(geom, geometryType!!)) comps.add(geom)
    }

    companion object {
        /**
         * Extracts the components of type <tt>clz</tt> from a [Geometry]
         * and adds them to the provided [List].
         *
         * @param geom the geometry from which to extract
         * @param list the list to add the extracted elements to
         */
        @Deprecated("Use {@link GeometryExtracter#extract(Geometry, String, List)}")
        fun extract(geom: Geometry?, clz: KClass<*>, list: MutableList?): MutableList {
            return extract(geom, toGeometryType(clz), list)
        }

        @Deprecated("")
        private fun toGeometryType(clz: KClass<*>): String {
            if (clz == null) return null else if (clz.isAssignableFrom(Point::class)) return Geometry.TYPENAME_POINT else if (clz.isAssignableFrom(
                    LineString::class
                )
            ) return Geometry.TYPENAME_LINESTRING else if (clz.isAssignableFrom(
                    LinearRing::class
                )
            ) return Geometry.TYPENAME_LINEARRING else if (clz.isAssignableFrom(
                    Polygon::class
                )
            ) return Geometry.TYPENAME_POLYGON else if (clz.isAssignableFrom(
                    MultiPoint::class
                )
            ) return Geometry.TYPENAME_MULTIPOINT else if (clz.isAssignableFrom(
                    MultiLineString::class
                )
            ) return Geometry.TYPENAME_MULTILINESTRING else if (clz.isAssignableFrom(
                    MultiPolygon::class
                )
            ) return Geometry.TYPENAME_MULTIPOLYGON else if (clz.isAssignableFrom(
                    GeometryCollection::class
                )
            ) return Geometry.TYPENAME_GEOMETRYCOLLECTION
            throw RuntimeException("Unsupported class")
        }

        /**
         * Extracts the components of <tt>geometryType</tt> from a [Geometry]
         * and adds them to the provided [List].
         *
         * @param geom the geometry from which to extract
         * @param geometryType Geometry type to extract (null means all types)
         * @param list the list to add the extracted elements to
         */
        @JvmOverloads
        fun extract(geom: Geometry, geometryType: String, list: MutableList = ArrayList()): MutableList {
            if (geom.geometryType === geometryType) {
                list.add(geom)
            } else (geom as? GeometryCollection)?.apply(GeometryExtracter(geometryType, list))
            // skip non-LineString elemental geometries
            return list
        }

        /**
         * Extracts the components of type <tt>clz</tt> from a [Geometry]
         * and returns them in a [List].
         *
         * @param geom the geometry from which to extract
         */
        @Deprecated("Use {@link GeometryExtracter#extract(Geometry, String)}")
        fun extract(geom: Geometry?, clz: KClass<*>): MutableList {
            return extract(geom, clz, ArrayList())
        }

        protected fun isOfType(geom: Geometry, geometryType: String): Boolean {
            if (geom.geometryType === geometryType) return true
            return if (geometryType === Geometry.TYPENAME_LINESTRING
                && geom.geometryType === Geometry.TYPENAME_LINEARRING
            ) true else false
        }
    }
}