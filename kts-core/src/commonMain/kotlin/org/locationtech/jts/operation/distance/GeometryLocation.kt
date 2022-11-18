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
package org.locationtech.jts.operation.distance

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.io.WKTWriter

/**
 * Represents the location of a point on a Geometry.
 * Maintains both the actual point location
 * (which may not be exact, if the point is not a vertex)
 * as well as information about the component
 * and segment index where the point occurs.
 * Locations inside area Geometrys will not have an associated segment index,
 * so in this case the segment index will have the sentinel value of
 * [.INSIDE_AREA].
 *
 * @version 1.7
 */
class GeometryLocation(component: Geometry?, segIndex: Int, pt: Coordinate?) {
    /**
     * Returns the geometry component on (or in) which this location occurs.
     */
    var geometryComponent: Geometry? = null

    /**
     * Returns the segment index for this location. If the location is inside an
     * area, the index will have the value [.INSIDE_AREA];
     *
     * @return the segment index for the location, or INSIDE_AREA
     */
    val segmentIndex: Int

    /**
     * Returns the [Coordinate] of this location.
     */
    var coordinate: Coordinate? = null

    /**
     * Constructs a GeometryLocation specifying a point on a geometry, as well as the
     * segment that the point is on
     * (or [.INSIDE_AREA] if the point is not on a segment).
     *
     * @param component the component of the geometry containing the point
     * @param segIndex the segment index of the location, or INSIDE_AREA
     * @param pt the coordinate of the location
     */
    init {
        geometryComponent = component
        segmentIndex = segIndex
        coordinate = pt
    }

    /**
     * Constructs a GeometryLocation specifying a point inside an area geometry.
     *
     * @param component the component of the geometry containing the point
     * @param pt the coordinate of the location
     */
    constructor(component: Geometry?, pt: Coordinate?) : this(component, INSIDE_AREA, pt) {}

    /**
     * Tests whether this location represents a point inside an area geometry.
     */
    val isInsideArea: Boolean
        get() = segmentIndex == INSIDE_AREA

    override fun toString(): String {
        return (geometryComponent!!.geometryType
                + "[" + segmentIndex + "]"
                + "-" + WKTWriter.toPoint(coordinate!!))
    }

    companion object {
        /**
         * A special value of segmentIndex used for locations inside area geometries.
         * These locations are not located on a segment,
         * and thus do not have an associated segment index.
         */
        const val INSIDE_AREA = -1
    }
}