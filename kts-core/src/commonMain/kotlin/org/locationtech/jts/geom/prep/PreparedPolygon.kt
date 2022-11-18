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

import org.locationtech.jts.algorithm.locate.IndexedPointInAreaLocator
import org.locationtech.jts.algorithm.locate.PointOnGeometryLocator
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.Polygonal
import org.locationtech.jts.noding.FastSegmentSetIntersectionFinder
import org.locationtech.jts.noding.SegmentStringUtil
import org.locationtech.jts.operation.predicate.RectangleContains
import org.locationtech.jts.operation.predicate.RectangleIntersects
import kotlin.jvm.Synchronized

/**
 * A prepared version for [Polygonal] geometries.
 * This class supports both [Polygon]s and [MultiPolygon]s.
 *
 *
 * This class does **not** support MultiPolygons which are non-valid
 * (e.g. with overlapping elements).
 *
 *
 * Instances of this class are thread-safe and immutable.
 *
 * @author mbdavis
 */
class PreparedPolygon(poly: Polygonal) : BasicPreparedGeometry(poly as Geometry) {
    private val isRectangle: Boolean = geometry.isRectangle

    // create these lazily, since they are expensive
    private var segIntFinder: FastSegmentSetIntersectionFinder? = null
    private var pia: PointOnGeometryLocator? = null

    /**
     * MD - Another option would be to use a simple scan for
     * segment testing for small geometries.
     * However, testing indicates that there is no particular advantage
     * to this approach.
     */
    /**
     * Gets the indexed intersection finder for this geometry.
     *
     * @return the intersection finder
     */
    @get:Synchronized
    val intersectionFinder: FastSegmentSetIntersectionFinder?
        get() {
            /**
             * MD - Another option would be to use a simple scan for
             * segment testing for small geometries.
             * However, testing indicates that there is no particular advantage
             * to this approach.
             */
            if (segIntFinder == null) segIntFinder =
                FastSegmentSetIntersectionFinder(SegmentStringUtil.extractSegmentStrings(geometry))
            return segIntFinder
        }

    @get:Synchronized
    val pointLocator: PointOnGeometryLocator
        get() {
            if (pia == null) pia = IndexedPointInAreaLocator(geometry)
            return pia!!
        }

    override fun intersects(g: Geometry): Boolean {
        // envelope test
        if (!envelopesIntersect(g)) return false

        // optimization for rectangles
        return if (isRectangle) {
            RectangleIntersects.intersects(
                (geometry as Polygon?)!!,
                g
            )
        } else PreparedPolygonIntersects.intersects(this, g)
    }

    override operator fun contains(g: Geometry?): Boolean {
        // short-circuit test
        if (!envelopeCovers(g!!)) return false

        // optimization for rectangles
        return if (isRectangle) {
            RectangleContains.contains(
                (geometry as Polygon?)!!,
                g
            )
        } else PreparedPolygonContains.contains(this, g)
    }

    override fun containsProperly(g: Geometry?): Boolean {
        // short-circuit test
        return if (!envelopeCovers(g!!)) false else PreparedPolygonContainsProperly.containsProperly(
            this,
            g
        )
    }

    override fun covers(g: Geometry?): Boolean {
        // short-circuit test
        if (!envelopeCovers(g!!)) return false
        // optimization for rectangle arguments
        return if (isRectangle) {
            true
        } else PreparedPolygonCovers.covers(this, g)
    }
}