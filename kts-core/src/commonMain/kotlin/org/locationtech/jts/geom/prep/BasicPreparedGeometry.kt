/*
 * Copyright (c) 2016 Vivid Solutions.
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
package org.locationtech.jts.geom.prep

import org.locationtech.jts.algorithm.PointLocator
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.util.ComponentCoordinateExtracter

/**
 * A base class for [PreparedGeometry] subclasses.
 * Contains default implementations for methods, which simply delegate
 * to the equivalent [Geometry] methods.
 * This class may be used as a "no-op" class for Geometry types
 * which do not have a corresponding [PreparedGeometry] implementation.
 *
 * @author Martin Davis
 */
open class BasicPreparedGeometry(final override val geometry: Geometry) : PreparedGeometry {
    private val representativePts // List<Coordinate>
            : MutableList<Coordinate>

    init {
        representativePts = ComponentCoordinateExtracter.getCoordinates(geometry)
    }//TODO wrap in unmodifiable?

    /**
     * Gets the list of representative points for this geometry.
     * One vertex is included for every component of the geometry
     * (i.e. including one for every ring of polygonal geometries).
     *
     * Do not modify the returned list!
     *
     * @return a List of Coordinate
     */
    val representativePoints: MutableList<Coordinate>
        get() =//TODO wrap in unmodifiable?
            representativePts

    /**
     * Tests whether any representative of the target geometry
     * intersects the test geometry.
     * This is useful in A/A, A/L, A/P, L/P, and P/P cases.
     *
     * @param testGeom the test geometry
     * @return true if any component intersects the areal test geometry
     */
    fun isAnyTargetComponentInTest(testGeom: Geometry?): Boolean {
        val locator = PointLocator()
        val i: Iterator<*> = representativePts.iterator()
        while (i.hasNext()) {
            val p = i.next() as Coordinate
            if (locator.intersects(p, testGeom!!)) return true
        }
        return false
    }

    /**
     * Determines whether a Geometry g interacts with
     * this geometry by testing the geometry envelopes.
     *
     * @param g a Geometry
     * @return true if the envelopes intersect
     */
    protected fun envelopesIntersect(g: Geometry): Boolean {
        return if (!geometry.envelopeInternal.intersects(g.envelopeInternal)) false else true
    }

    /**
     * Determines whether the envelope of
     * this geometry covers the Geometry g.
     *
     * @param g a Geometry
     * @return true if g is contained in this envelope
     */
    protected fun envelopeCovers(g: Geometry): Boolean {
        return if (!geometry.envelopeInternal.covers(g.envelopeInternal)) false else true
    }

    /**
     * Default implementation.
     */
    override operator fun contains(g: Geometry?): Boolean {
        return geometry.contains(g!!)
    }

    /**
     * Default implementation.
     */
    override fun containsProperly(g: Geometry?): Boolean {
        // since raw relate is used, provide some optimizations

        // short-circuit test
        return if (!geometry.envelopeInternal.contains(g!!.envelopeInternal)) false else geometry.relate(g!!, "T**FF*FF*")

        // otherwise, compute using relate mask
    }

    /**
     * Default implementation.
     */
    override fun coveredBy(g: Geometry?): Boolean {
        return geometry.coveredBy(g!!)
    }

    /**
     * Default implementation.
     */
    override fun covers(g: Geometry?): Boolean {
        return geometry.covers(g!!)
    }

    /**
     * Default implementation.
     */
    override fun crosses(g: Geometry?): Boolean {
        return geometry.crosses(g!!)
    }

    /**
     * Standard implementation for all geometries.
     * Supports [GeometryCollection]s as input.
     */
    override fun disjoint(g: Geometry): Boolean {
        return !intersects(g)
    }

    /**
     * Default implementation.
     */
    override fun intersects(g: Geometry): Boolean {
        return geometry.intersects(g)
    }

    /**
     * Default implementation.
     */
    override fun overlaps(g: Geometry?): Boolean {
        return geometry.overlaps(g!!)
    }

    /**
     * Default implementation.
     */
    override fun touches(g: Geometry?): Boolean {
        return geometry.touches(g!!)
    }

    /**
     * Default implementation.
     */
    override fun within(g: Geometry?): Boolean {
        return geometry.within(g!!)
    }

    override fun toString(): String {
        return geometry.toString()
    }
}