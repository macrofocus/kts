/*
 * Copyright (c) 2020 Martin Davis.
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.geom

import org.locationtech.jts.geom.util.GeometryCollectionMapper
import org.locationtech.jts.geom.util.GeometryMapper
import org.locationtech.jts.operation.overlay.OverlayOp
import org.locationtech.jts.operation.overlay.snap.SnapIfNeededOverlayOp
import org.locationtech.jts.operation.overlayng.OverlayNGRobust
import org.locationtech.jts.operation.union.UnaryUnionOp
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * Internal class which encapsulates the runtime switch to use OverlayNG,
 * and some additional extensions for optimization and GeometryCollection handling.
 *
 * This class allows the [Geometry] overlay methods to be
 * switched between the original algorithm and the modern OverlayNG codebase
 * via a system property `jts.overlay`.
 *
 *  * `jts.overlay=old` - (default) use original overlay algorithm
 *  * `jts.overlay=ng` - use OverlayNG
 *
 * @author mdavis
 */
internal object GeometryOverlay {
    @JvmField
    var OVERLAY_PROPERTY_NAME = "jts.overlay"
    @JvmField
    public var OVERLAY_PROPERTY_VALUE_NG = "ng"
    @JvmField
    var OVERLAY_PROPERTY_VALUE_OLD = "old"

    /**
     * Currently the original JTS overlay implementation is the default
     */
    var OVERLAY_NG_DEFAULT = false
    private var isOverlayNG = OVERLAY_NG_DEFAULT

    init {
//        setOverlayImpl(System.getProperty(OVERLAY_PROPERTY_NAME))
    }

    /**
     * This function is provided primarily for unit testing.
     * It is not recommended to use it dynamically, since
     * that may result in inconsistent overlay behaviour.
     *
     * @param overlayImplCode the code for the overlay method (may be null)
     */
    @JvmStatic
    fun setOverlayImpl(overlayImplCode: String?) {
        if (overlayImplCode == null) return
        // set flag explicitly since current value may not be default
        isOverlayNG = OVERLAY_NG_DEFAULT
        if (OVERLAY_PROPERTY_VALUE_NG.equals(overlayImplCode, ignoreCase = true)) isOverlayNG = true
    }

    private fun overlay(
        a: Geometry,
        b: Geometry,
        opCode: Int
    ): Geometry {
        return if (isOverlayNG) {
            OverlayNGRobust.overlay(a, b, opCode)!!
        } else {
            SnapIfNeededOverlayOp.overlayOp(a, b, opCode)
        }
    }

    fun difference(
        a: Geometry,
        b: Geometry
    ): Geometry {
        // special case: if A.isEmpty ==> empty; if B.isEmpty ==> A
        if (a.isEmpty) return OverlayOp.createEmptyResult(OverlayOp.DIFFERENCE, a, b, a.factory)
        if (b.isEmpty) return a.copy()
        Geometry.checkNotGeometryCollection(a)
        Geometry.checkNotGeometryCollection(b)
        return overlay(a, b, OverlayOp.DIFFERENCE)
    }

    fun intersection(
        a: Geometry,
        b: Geometry
    ): Geometry {
        /**
         * TODO: MD - add optimization for P-A case using Point-In-Polygon
         */
        // special case: if one input is empty ==> empty
        if (a.isEmpty || b.isEmpty) return OverlayOp.createEmptyResult(OverlayOp.INTERSECTION, a, b, a.factory)

        // compute for GCs
        // (An inefficient algorithm, but will work)
        // TODO: improve efficiency of computation for GCs
        if (a.isGeometryCollection) {
            val g2: Geometry = b
            return GeometryCollectionMapper.map(
                (a as GeometryCollection),
                object : GeometryMapper.MapOp {
                    override fun map(g: Geometry): Geometry {
                        return g.intersection(g2)
                    }
                })
        }

        // No longer needed since GCs are handled by previous code
        //checkNotGeometryCollection(this);
        //checkNotGeometryCollection(other);
        return overlay(a, b, OverlayOp.INTERSECTION)
    }

    fun symDifference(
        a: Geometry,
        b: Geometry
    ): Geometry {
        // handle empty geometry cases
        if (a.isEmpty || b.isEmpty) {
            // both empty - check dimensions
            if (a.isEmpty && b.isEmpty) return OverlayOp.createEmptyResult(
                OverlayOp.SYMDIFFERENCE,
                a,
                b,
                a.factory
            )

            // special case: if either input is empty ==> result = other arg
            if (a.isEmpty) return b.copy()
            if (b.isEmpty) return a.copy()
        }
        Geometry.checkNotGeometryCollection(a)
        Geometry.checkNotGeometryCollection(b)
        return overlay(a, b, OverlayOp.SYMDIFFERENCE)
    }

    fun union(
        a: Geometry,
        b: Geometry
    ): Geometry {
        // handle empty geometry cases
        if (a.isEmpty || b.isEmpty) {
            if (a.isEmpty && b.isEmpty) return OverlayOp.createEmptyResult(OverlayOp.UNION, a, b, a.factory)

            // special case: if either input is empty ==> other input
            if (a.isEmpty) return b.copy()
            if (b.isEmpty) return a.copy()
        }

        // TODO: optimize if envelopes of geometries do not intersect
        Geometry.checkNotGeometryCollection(a)
        Geometry.checkNotGeometryCollection(b)
        return overlay(a, b, OverlayOp.UNION)
    }

    fun union(a: Geometry): Geometry? {
        return if (isOverlayNG) {
            OverlayNGRobust.union(a)
        } else {
            UnaryUnionOp.union(a)!!
        }
    }
}