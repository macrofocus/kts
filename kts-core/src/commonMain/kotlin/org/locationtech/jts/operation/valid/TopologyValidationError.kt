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
package org.locationtech.jts.operation.valid

import org.locationtech.jts.geom.Coordinate
import kotlin.jvm.JvmOverloads

/**
 * Contains information about the nature and location of a [Geometry]
 * validation error
 *
 * @version 1.7
 */
class TopologyValidationError @JvmOverloads constructor(
    /**
     * Gets the type of this error.
     *
     * @return the error type
     */
    val errorType: Int, pt: Coordinate? = null
) {

    /**
     * Returns the location of this error (on the [Geometry] containing the error).
     *
     * @return a [Coordinate] on the input geometry
     */
    var coordinate: Coordinate? = null
    /**
     * Creates a validation error with the given type and location
     *
     * @param errorType the type of the error
     * @param pt the location of the error
     */
    /**
     * Creates a validation error of the given type with a null location
     *
     * @param errorType the type of the error
     */
    init {
        if (pt != null) coordinate = pt.copy()
    }

    /**
     * Gets an error message describing this error.
     * The error message does not describe the location of the error.
     *
     * @return the error message
     */
    val message: String
        get() = errMsg[errorType]

    /**
     * Gets a message describing the type and location of this error.
     * @return the error message
     */
    override fun toString(): String {
        var locStr = ""
        if (coordinate != null) locStr = " at or near point " + coordinate
        return message + locStr
    }

    companion object {
        /**
         * Not used
         */
        @Deprecated("")
        val ERROR = 0

        /**
         * No longer used - repeated points are considered valid as per the SFS
         */
        @Deprecated("")
        val REPEATED_POINT = 1

        /**
         * Indicates that a hole of a polygon lies partially or completely in the exterior of the shell
         */
        const val HOLE_OUTSIDE_SHELL = 2

        /**
         * Indicates that a hole lies in the interior of another hole in the same polygon
         */
        const val NESTED_HOLES = 3

        /**
         * Indicates that the interior of a polygon is disjoint
         * (often caused by set of contiguous holes splitting the polygon into two parts)
         */
        const val DISCONNECTED_INTERIOR = 4

        /**
         * Indicates that two rings of a polygonal geometry intersect
         */
        const val SELF_INTERSECTION = 5

        /**
         * Indicates that a ring self-intersects
         */
        const val RING_SELF_INTERSECTION = 6

        /**
         * Indicates that a polygon component of a MultiPolygon lies inside another polygonal component
         */
        const val NESTED_SHELLS = 7

        /**
         * Indicates that a polygonal geometry contains two rings which are identical
         */
        const val DUPLICATE_RINGS = 8

        /**
         * Indicates that either
         *
         *  * a LineString contains a single point
         *  * a LinearRing contains 2 or 3 points
         *
         */
        const val TOO_FEW_POINTS = 9

        /**
         * Indicates that the `X` or `Y` ordinate of
         * a Coordinate is not a valid numeric value (e.g. [Double.NaN] )
         */
        const val INVALID_COORDINATE = 10

        /**
         * Indicates that a ring is not correctly closed
         * (the first and the last coordinate are different)
         */
        const val RING_NOT_CLOSED = 11

        /**
         * Messages corresponding to error codes
         */
        val errMsg = arrayOf(
            "Topology Validation Error",
            "Repeated Point",
            "Hole lies outside shell",
            "Holes are nested",
            "Interior is disconnected",
            "Self-intersection",
            "Ring Self-intersection",
            "Nested shells",
            "Duplicate Rings",
            "Too few distinct points in geometry component",
            "Invalid Coordinate",
            "Ring is not closed"
        )
    }
}