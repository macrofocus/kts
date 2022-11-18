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
package org.locationtech.jts.geom

import org.locationtech.jts.operation.BoundaryOp

/**
 * Models a collection of [LineString]s.
 *
 * Any collection of LineStrings is a valid MultiLineString.
 *
 * @version 1.7
 */
class MultiLineString : GeometryCollection, Lineal {
    /**
     * Constructs a `MultiLineString`.
     *
     * @param  lineStrings     the `LineString`s for this `MultiLineString`
     * , or `null` or an empty array to create the empty geometry.
     * Elements may be empty `LineString`s, but not `null`
     * s.
     * @param  precisionModel  the specification of the grid of allowable points
     * for this `MultiLineString`
     * @param  SRID            the ID of the Spatial Reference System used by this
     * `MultiLineString`
     */
    @Deprecated("Use GeometryFactory instead")
    constructor(
        lineStrings: Array<LineString>,
        precisionModel: PrecisionModel,
        SRID: Int
    ) : super(lineStrings as Array<Geometry>, GeometryFactory(precisionModel, SRID))

    /**
     * @param lineStrings
     * the `LineString`s for this `MultiLineString`,
     * or `null` or an empty array to create the empty
     * geometry. Elements may be empty `LineString`s,
     * but not `null`s.
     */
    constructor(lineStrings: Array<LineString>?, factory: GeometryFactory) : super(
        lineStrings as Array<Geometry>?,
        factory
    )

    override val dimension: Int
        get() = 1
    override val boundaryDimension: Int
        get() = if (isClosed) {
            Dimension.FALSE
        } else 0
    override val geometryType: String
        get() = TYPENAME_MULTILINESTRING
    val isClosed: Boolean
        get() {
            if (isEmpty) {
                return false
            }
            for (element in geometries) {
                if (!(element as LineString).isClosed) {
                    return false
                }
            }
            return true
        }

    /**
     * Gets the boundary of this geometry.
     * The boundary of a lineal geometry is always a zero-dimensional geometry (which may be empty).
     *
     * @return the boundary geometry
     * @see Geometry.getBoundary
     */
    override val boundary: Geometry?
        get() = BoundaryOp(this).boundary

    /**
     * Creates a [MultiLineString] in the reverse
     * order to this object.
     * Both the order of the component LineStrings
     * and the order of their coordinate sequences
     * are reversed.
     *
     * @return a [MultiLineString] in the reverse order
     */
    override fun reverse(): MultiLineString {
        return super.reverse() as MultiLineString
    }

    override fun reverseInternal(): MultiLineString {
        val lineStrings: Array<LineString?> = arrayOfNulls(this.geometries.size)
        for (i in lineStrings.indices) {
            lineStrings[i] = this.geometries[i].reverse() as LineString
        }
        return MultiLineString(lineStrings.requireNoNulls(), factory)
    }

    override fun copyInternal(): MultiLineString {
        val lineStrings: Array<LineString?> = arrayOfNulls(this.geometries.size)
        for (i in lineStrings.indices) {
            lineStrings[i] = this.geometries[i].copy() as LineString
        }
        return MultiLineString(lineStrings.requireNoNulls(), factory)
    }

    override fun equalsExact(other: Geometry?, tolerance: Double): Boolean {
        return if (!isEquivalentClass(other!!)) {
            false
        } else super.equalsExact(other, tolerance)
    }

    override val typeCode: Int
        protected get() = TYPECODE_MULTILINESTRING

    companion object {
        private const val serialVersionUID = 8166665132445433741L
    }
}