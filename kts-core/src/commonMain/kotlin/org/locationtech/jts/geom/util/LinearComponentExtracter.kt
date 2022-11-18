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
import org.locationtech.jts.geom.GeometryComponentFilter
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.LinearRing

/**
 * Extracts all the 1-dimensional ([LineString]) components from a [Geometry].
 * For polygonal geometries, this will extract all the component [LinearRing]s.
 * If desired, `LinearRing`s can be forced to be returned as `LineString`s.
 *
 * @version 1.7
 */
class LinearComponentExtracter : GeometryComponentFilter {
    private var lines: MutableCollection<Geometry>
    private var isForcedToLineString = false

    /**
     * Constructs a LineExtracterFilter with a list in which to store LineStrings found.
     */
    constructor(lines: MutableCollection<Geometry>) {
        this.lines = lines
    }

    /**
     * Constructs a LineExtracterFilter with a list in which to store LineStrings found.
     */
    constructor(lines: MutableCollection<Geometry>, isForcedToLineString: Boolean) {
        this.lines = lines
        this.isForcedToLineString = isForcedToLineString
    }

    /**
     * Indicates that LinearRing components should be
     * converted to pure LineStrings.
     *
     * @param isForcedToLineString true if LinearRings should be converted to LineStrings
     */
    fun setForceToLineString(isForcedToLineString: Boolean) {
        this.isForcedToLineString = isForcedToLineString
    }

    override fun filter(geom: Geometry) {
        if (isForcedToLineString && geom is LinearRing) {
            val line = geom.factory.createLineString(geom.coordinateSequence)
            lines.add(line)
            return
        }
        // if not being forced, and this is a linear component
        if (geom is LineString) lines.add(geom)

        // else this is not a linear component, so skip it
    }

    companion object {
        /**
         * Extracts the linear components from a single [Geometry]
         * and adds them to the provided [Collection].
         *
         * @param geoms the collection of geometries from which to extract linear components
         * @param lines the collection to add the extracted linear components to
         * @return the collection of linear components (LineStrings or LinearRings)
         */
        fun getLines(geoms: Collection<Geometry>, lines: MutableCollection<Geometry>): Collection<Geometry> {
            val i = geoms.iterator()
            while (i.hasNext()) {
                val g = i.next()
                getLines(g, lines)
            }
            return lines
        }

        /**
         * Extracts the linear components from a single [Geometry]
         * and adds them to the provided [Collection].
         *
         * @param geoms the Collection of geometries from which to extract linear components
         * @param lines the collection to add the extracted linear components to
         * @param forceToLineString true if LinearRings should be converted to LineStrings
         * @return the collection of linear components (LineStrings or LinearRings)
         */
        fun getLines(geoms: Collection<Geometry>, lines: MutableCollection<Geometry>, forceToLineString: Boolean): Collection<Geometry> {
            val i = geoms.iterator()
            while (i.hasNext()) {
                val g = i.next()
                getLines(g, lines, forceToLineString)
            }
            return lines
        }

        /**
         * Extracts the linear components from a single [Geometry]
         * and adds them to the provided [Collection].
         *
         * @param geom the geometry from which to extract linear components
         * @param lines the Collection to add the extracted linear components to
         * @return the Collection of linear components (LineStrings or LinearRings)
         */
        fun getLines(geom: Geometry, lines: MutableCollection<Geometry>): Collection<Geometry> {
            if (geom is LineString) {
                lines.add(geom)
            } else {
                geom.apply(LinearComponentExtracter(lines))
            }
            return lines
        }

        /**
         * Extracts the linear components from a single [Geometry]
         * and adds them to the provided [Collection].
         *
         * @param geom the geometry from which to extract linear components
         * @param lines the Collection to add the extracted linear components to
         * @param forceToLineString true if LinearRings should be converted to LineStrings
         * @return the Collection of linear components (LineStrings or LinearRings)
         */
        fun getLines(geom: Geometry, lines: MutableCollection<Geometry>, forceToLineString: Boolean): Collection<*> {
            geom.apply(LinearComponentExtracter(lines, forceToLineString))
            return lines
        }

        /**
         * Extracts the linear components from a single geometry.
         * If more than one geometry is to be processed, it is more
         * efficient to create a single [LinearComponentExtracter] instance
         * and pass it to multiple geometries.
         *
         * @param geom the geometry from which to extract linear components
         * @return the list of linear components
         */
        fun getLines(geom: Geometry): List<Geometry> {
            return getLines(geom, false)
        }

        /**
         * Extracts the linear components from a single geometry.
         * If more than one geometry is to be processed, it is more
         * efficient to create a single [LinearComponentExtracter] instance
         * and pass it to multiple geometries.
         *
         * @param geom the geometry from which to extract linear components
         * @param forceToLineString true if LinearRings should be converted to LineStrings
         * @return the list of linear components
         */
        fun getLines(geom: Geometry, forceToLineString: Boolean): List<Geometry> {
            val lines: MutableList<Geometry> = ArrayList()
            geom.apply(LinearComponentExtracter(lines, forceToLineString))
            return lines
        }

        /**
         * Extracts the linear components from a single [Geometry]
         * and returns them as either a [LineString] or [MultiLineString].
         *
         * @param geom the geometry from which to extract
         * @return a linear geometry
         */
        fun getGeometry(geom: Geometry): Geometry {
            return geom.factory.buildGeometry(getLines(geom))
        }

        /**
         * Extracts the linear components from a single [Geometry]
         * and returns them as either a [LineString] or [MultiLineString].
         *
         * @param geom the geometry from which to extract
         * @param forceToLineString true if LinearRings should be converted to LineStrings
         * @return a linear geometry
         */
        fun getGeometry(geom: Geometry, forceToLineString: Boolean): Geometry {
            return geom.factory.buildGeometry(getLines(geom, forceToLineString))
        }
    }
}