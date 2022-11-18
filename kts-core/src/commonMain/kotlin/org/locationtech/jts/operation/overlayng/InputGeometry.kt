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

import org.locationtech.jts.algorithm.locate.IndexedPointInAreaLocator
import org.locationtech.jts.algorithm.locate.PointOnGeometryLocator
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.Location

/**
 * Manages the input geometries for an overlay operation.
 * The second geometry is allowed to be null,
 * to support for instance precision reduction.
 *
 * @author Martin Davis
 */
internal class InputGeometry(geomA: Geometry?, geomB: Geometry?) {
    //private static final PointLocator ptLocator = new PointLocator();
    private var geom = arrayOfNulls<Geometry>(2)
    private var ptLocatorA: PointOnGeometryLocator? = null
    private var ptLocatorB: PointOnGeometryLocator? = null
    private val isCollapsed = BooleanArray(2)

    init {
        geom = arrayOf(geomA, geomB)
    }

    val isSingle: Boolean
        get() = geom[1] == null

    fun getDimension(index: Int): Int {
        return if (geom[index] == null) -1 else geom[index]!!.dimension
    }

    fun getGeometry(geomIndex: Int): Geometry? {
        return geom[geomIndex]
    }

    fun getEnvelope(geomIndex: Int): Envelope {
        return geom[geomIndex]!!.envelopeInternal
    }

    fun isEmpty(geomIndex: Int): Boolean {
        return geom[geomIndex]!!.isEmpty
    }

    fun isArea(geomIndex: Int): Boolean {
        return geom[geomIndex] != null && geom[geomIndex]!!.dimension == 2
    }

    /**
     * Gets the index of an input which is an area,
     * if one exists.
     * Otherwise returns -1.
     * If both inputs are areas, returns the index of the first one (0).
     *
     * @return the index of an area input, or -1
     */
    val areaIndex: Int
        get() {
            if (getDimension(0) == 2) return 0
            return if (getDimension(1) == 2) 1 else -1
        }

    fun isLine(geomIndex: Int): Boolean {
        return getDimension(geomIndex) == 1
    }

    val isAllPoints: Boolean
        get() = getDimension(0) == 0 && geom[1] != null && getDimension(1) == 0

    fun hasPoints(): Boolean {
        return getDimension(0) == 0 || getDimension(1) == 0
    }

    /**
     * Tests if an input geometry has edges.
     * This indicates that topology needs to be computed for them.
     *
     * @param geomIndex
     * @return true if the input geometry has edges
     */
    fun hasEdges(geomIndex: Int): Boolean {
        return geom[geomIndex] != null && geom[geomIndex]!!.dimension > 0
    }

    /**
     * Determines the location within an area geometry.
     * This allows disconnected edges to be fully
     * located.
     *
     * @param geomIndex the index of the geometry
     * @param pt the coordinate to locate
     * @return the location of the coordinate
     *
     * @see Location
     */
    fun locatePointInArea(geomIndex: Int, pt: Coordinate?): Int {
        // Assert: only called if dimension(geomIndex) = 2
        if (isCollapsed[geomIndex]) return Location.EXTERIOR


        //return ptLocator.locate(pt, geom[geomIndex]);

        //*
        // this check is required because IndexedPointInAreaLocator can't handle empty polygons
        if (getGeometry(geomIndex)!!.isEmpty
            || isCollapsed[geomIndex]
        ) return Location.EXTERIOR
        val ptLocator = getLocator(geomIndex)
        return ptLocator.locate(pt!!)
        //*/
    }

    private fun getLocator(geomIndex: Int): PointOnGeometryLocator {
        return if (geomIndex == 0) {
            if (ptLocatorA == null) ptLocatorA =
                IndexedPointInAreaLocator(getGeometry(geomIndex))
            ptLocatorA!!
        } else {
            if (ptLocatorB == null) ptLocatorB =
                IndexedPointInAreaLocator(getGeometry(geomIndex))
            ptLocatorB!!
        }
    }

    fun setCollapsed(geomIndex: Int, isGeomCollapsed: Boolean) {
        isCollapsed[geomIndex] = isGeomCollapsed
    }
}