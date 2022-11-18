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

/**
 * Removes common most-significant mantissa bits
 * from one or more [Geometry]s.
 *
 * The CommonBitsRemover "scavenges" precision
 * which is "wasted" by a large displacement of the geometry
 * from the origin.
 * For example, if a small geometry is displaced from the origin
 * by a large distance,
 * the displacement increases the significant figures in the coordinates,
 * but does not affect the *relative* topology of the geometry.
 * Thus the geometry can be translated back to the origin
 * without affecting its topology.
 * In order to compute the translation without affecting
 * the full precision of the coordinate values,
 * the translation is performed at the bit level by
 * removing the common leading mantissa bits.
 *
 * If the geometry envelope already contains the origin,
 * the translation procedure cannot be applied.
 * In this case, the common bits value is computed as zero.
 *
 * If the geometry crosses the Y axis but not the X axis
 * (and *mutatis mutandum*),
 * the common bits for Y are zero,
 * but the common bits for X are non-zero.
 *
 * @version 1.7
 */
class CommonBitsRemover {
    /**
     * The common bits of the Coordinates in the supplied Geometries.
     */
    var commonCoordinate: Coordinate? = null
        private set
    private val ccFilter = CommonCoordinateFilter()

    /**
     * Add a geometry to the set of geometries whose common bits are
     * being computed.  After this method has executed the
     * common coordinate reflects the common bits of all added
     * geometries.
     *
     * @param geom a Geometry to test for common bits
     */
    fun add(geom: Geometry) {
        geom.apply(ccFilter)
        commonCoordinate = ccFilter.commonCoordinate
    }

    /**
     * Removes the common coordinate bits from a Geometry.
     * The coordinates of the Geometry are changed.
     *
     * @param geom the Geometry from which to remove the common coordinate bits
     * @return the shifted Geometry
     */
    fun removeCommonBits(geom: Geometry): Geometry {
        if (commonCoordinate!!.x == 0.0 && commonCoordinate!!.y == 0.0) return geom
        val invCoord = Coordinate(commonCoordinate!!)
        invCoord.x = -invCoord.x
        invCoord.y = -invCoord.y
        val trans = Translater(invCoord)
        geom.apply(trans)
        geom.geometryChanged()
        return geom
    }

    /**
     * Adds the common coordinate bits back into a Geometry.
     * The coordinates of the Geometry are changed.
     *
     * @param geom the Geometry to which to add the common coordinate bits
     */
    fun addCommonBits(geom: Geometry) {
        val trans = Translater(
            commonCoordinate
        )
        geom.apply(trans)
        geom.geometryChanged()
    }

    internal class CommonCoordinateFilter : CoordinateFilter {
        private val commonBitsX: CommonBits = CommonBits()
        private val commonBitsY: CommonBits = CommonBits()
        override fun filter(coord: Coordinate?) {
            commonBitsX.add(coord!!.x)
            commonBitsY.add(coord.y)
        }

        val commonCoordinate: Coordinate
            get() = Coordinate(
                commonBitsX.common,
                commonBitsY.common
            )
    }

    internal class Translater(trans: Coordinate?) : CoordinateSequenceFilter {
        var trans: Coordinate? = null

        init {
            this.trans = trans
        }

        override fun filter(seq: CoordinateSequence?, i: Int) {
            val xp = seq!!.getOrdinate(i, 0) + trans!!.x
            val yp = seq.getOrdinate(i, 1) + trans!!.y
            seq.setOrdinate(i, 0, xp)
            seq.setOrdinate(i, 1, yp)
        }

        override val isDone: Boolean
            get() = false
        override val isGeometryChanged: Boolean
            get() = true
    }
}