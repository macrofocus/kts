/*
 * Copyright (c) 2020 Martin Davis.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.geom

import org.locationtech.jts.operation.relate.RelateOp
import org.locationtech.jts.operation.relateng.RelateNG
import org.locationtech.jts.operation.relateng.RelatePredicate
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * Internal class which encapsulates the runtime switch to use RelateNG.
 *
 * This class allows the [Geometry] predicate methods to be
 * switched between the original [RelateOp] algorithm
 * and the modern [RelateNG] codebase
 * via a system property `jts.relate`.
 *
 *  * `jts.relate=old` - (default) use original RelateOp algorithm
 *  * `jts.relate=ng` - use RelateNG
 *
 * Note: in KTS the system property is not read (not available in common code);
 * use [setRelateImpl] to switch implementations.
 *
 * @author mdavis
 */
internal object GeometryRelate {
    @JvmField
    var RELATE_PROPERTY_NAME = "jts.relate"
    @JvmField
    var RELATE_PROPERTY_VALUE_NG = "ng"
    @JvmField
    var RELATE_PROPERTY_VALUE_OLD = "old"

    /**
     * Currently the old relate implementation is the default
     */
    @JvmField
    var RELATE_NG_DEFAULT = false

    private var isRelateNG = RELATE_NG_DEFAULT

    init {
//        setRelateImpl(System.getProperty(RELATE_PROPERTY_NAME))
    }

    /**
     * This function is provided primarily for unit testing.
     * It is not recommended to use it dynamically, since
     * that may result in inconsistent overlay behaviour.
     *
     * @param relateImplCode the code for the overlay method (may be null)
     */
    @JvmStatic
    fun setRelateImpl(relateImplCode: String?) {
        if (relateImplCode == null) return
        // set flag explicitly since current value may not be default
        isRelateNG = RELATE_NG_DEFAULT
        if (RELATE_PROPERTY_VALUE_NG.equals(relateImplCode, ignoreCase = true)) isRelateNG = true
    }

    @JvmStatic
    fun intersects(a: Geometry, b: Geometry): Boolean {
        if (isRelateNG) {
            return RelateNG.relate(a, b, RelatePredicate.intersects())
        }
        if (a.isGeometryCollection || b.isGeometryCollection) {
            for (i in 0 until a.numGeometries) {
                for (j in 0 until b.numGeometries) {
                    if (a.getGeometryN(i).intersects(b.getGeometryN(j))) {
                        return true
                    }
                }
            }
            return false
        }
        return RelateOp.relate(a, b).isIntersects
    }

    @JvmStatic
    fun contains(a: Geometry, b: Geometry): Boolean {
        if (isRelateNG) {
            return RelateNG.relate(a, b, RelatePredicate.contains())
        }
        // optimization - lower dimension cannot contain areas
        if (b.dimension == 2 && a.dimension < 2) {
            return false
        }
        // optimization - P cannot contain a non-zero-length L
        // Note that a point can contain a zero-length lineal geometry,
        // since the line has no boundary due to Mod-2 Boundary Rule
        if (b.dimension == 1 && a.dimension < 1 && b.length > 0.0) {
            return false
        }
        // optimization - envelope test
        if (!a.envelopeInternal.contains(b.envelopeInternal))
            return false
        return RelateOp.relate(a, b).isContains
    }

    @JvmStatic
    fun covers(a: Geometry, b: Geometry): Boolean {
        if (isRelateNG) {
            return RelateNG.relate(a, b, RelatePredicate.covers())
        }
        // optimization - lower dimension cannot cover areas
        if (b.dimension == 2 && a.dimension < 2) {
            return false
        }
        // optimization - P cannot cover a non-zero-length L
        // Note that a point can cover a zero-length lineal geometry
        if (b.dimension == 1 && a.dimension < 1 && b.length > 0.0) {
            return false
        }
        // optimization - envelope test
        if (!a.envelopeInternal.covers(b.envelopeInternal))
            return false
        // optimization for rectangle arguments
        if (a.isRectangle) {
            // since we have already tested that the test envelope is covered
            return true
        }
        return RelateOp.relate(a, b).isCovers
    }

    @JvmStatic
    fun coveredBy(a: Geometry, b: Geometry): Boolean {
        if (isRelateNG) {
            return RelateNG.relate(a, b, RelatePredicate.coveredBy())
        }
        return covers(b, a)
    }

    @JvmStatic
    fun crosses(a: Geometry, b: Geometry): Boolean {
        if (isRelateNG) {
            return RelateNG.relate(a, b, RelatePredicate.crosses())
        }
        // short-circuit test
        if (!a.envelopeInternal.intersects(b.envelopeInternal))
            return false
        return RelateOp.relate(a, b).isCrosses(a.dimension, b.dimension)
    }

    @JvmStatic
    fun disjoint(a: Geometry, b: Geometry): Boolean {
        if (isRelateNG) {
            return RelateNG.relate(a, b, RelatePredicate.disjoint())
        }
        return !intersects(a, b)
    }

    @JvmStatic
    fun equalsTopo(a: Geometry, b: Geometry): Boolean {
        if (isRelateNG) {
            return RelateNG.relate(a, b, RelatePredicate.equalsTopo())
        }
        if (a.envelopeInternal != b.envelopeInternal)
            return false
        return RelateOp.relate(a, b).isEquals(a.dimension, b.dimension)
    }

    @JvmStatic
    fun overlaps(a: Geometry, b: Geometry): Boolean {
        if (isRelateNG) {
            return RelateNG.relate(a, b, RelatePredicate.overlaps())
        }
        if (!a.envelopeInternal.intersects(b.envelopeInternal))
            return false
        return RelateOp.relate(a, b).isOverlaps(a.dimension, b.dimension)
    }

    @JvmStatic
    fun touches(a: Geometry, b: Geometry): Boolean {
        if (isRelateNG) {
            return RelateNG.relate(a, b, RelatePredicate.touches())
        }
        if (!a.envelopeInternal.intersects(b.envelopeInternal))
            return false
        return RelateOp.relate(a, b).isTouches(a.dimension, b.dimension)
    }

    @JvmStatic
    fun within(a: Geometry, b: Geometry): Boolean {
        if (isRelateNG) {
            return RelateNG.relate(a, b, RelatePredicate.within())
        }
        return contains(b, a)
    }

    @JvmStatic
    fun relate(a: Geometry, b: Geometry): IntersectionMatrix {
        if (isRelateNG) {
            return RelateNG.relate(a, b)
        }
        Geometry.checkNotGeometryCollection(a)
        Geometry.checkNotGeometryCollection(b)
        return RelateOp.relate(a, b)
    }

    @JvmStatic
    fun relate(a: Geometry, b: Geometry, intersectionPattern: String): Boolean {
        if (isRelateNG) {
            return RelateNG.relate(a, b, intersectionPattern)
        }
        Geometry.checkNotGeometryCollection(a)
        Geometry.checkNotGeometryCollection(b)
        return RelateOp.relate(a, b).matches(intersectionPattern)
    }
}
