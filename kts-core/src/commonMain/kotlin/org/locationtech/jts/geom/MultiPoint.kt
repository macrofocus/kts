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

/**
 * Models a collection of [Point]s.
 *
 * Any collection of Points is a valid MultiPoint.
 *
 * @version 1.7
 */
class MultiPoint : GeometryCollection, Puntal {
    /**
     * Constructs a `MultiPoint`.
     *
     * @param  points          the `Point`s for this `MultiPoint`
     * , or `null` or an empty array to create the empty geometry.
     * Elements may be empty `Point`s, but not `null`s.
     * @param  precisionModel  the specification of the grid of allowable points
     * for this `MultiPoint`
     * @param  SRID            the ID of the Spatial Reference System used by this
     * `MultiPoint`
     */
    @Deprecated("Use GeometryFactory instead")
    constructor(
        points: Array<Point>,
        precisionModel: PrecisionModel,
        SRID: Int
    ) : super(points as Array<Geometry>, GeometryFactory(precisionModel, SRID)) {
    }

    /**
     * @param  points          the `Point`s for this `MultiPoint`
     * , or `null` or an empty array to create the empty geometry.
     * Elements may be empty `Point`s, but not `null`s.
     */
    constructor(
        points: Array<Point>?,
        factory: GeometryFactory
    ) : super(points as Array<Geometry>?, factory) {
    }

    override val dimension: Int
        get() = 0
    override val boundaryDimension: Int
        get() = Dimension.FALSE
    override val geometryType: String
        get() = TYPENAME_MULTIPOINT

    /**
     * Gets the boundary of this geometry.
     * Zero-dimensional geometries have no boundary by definition,
     * so an empty GeometryCollection is returned.
     *
     * @return an empty GeometryCollection
     * @see Geometry.getBoundary
     */
    override val boundary: Geometry
        get() = factory.createGeometryCollection()

    override fun reverse(): MultiPoint {
        return super.reverse() as MultiPoint
    }

    protected override fun reverseInternal(): MultiPoint {
        val points: Array<Point?> =
            arrayOfNulls<Point>(this.geometries.size)
        for (i in points.indices) {
            points[i] = this.geometries.get(i).copy() as Point
        }
        return MultiPoint(points.requireNoNulls(), factory)
    }

    override fun equalsExact(other: Geometry?, tolerance: Double): Boolean {
        return if (!isEquivalentClass(other!!)) {
            false
        } else super.equalsExact(other, tolerance)
    }

    /**
     * Returns the `Coordinate` at the given position.
     *
     * @param  n  the index of the `Coordinate` to retrieve, beginning
     * at 0
     * @return    the `n`th `Coordinate`
     */
    protected fun getCoordinate(n: Int): Coordinate {
        return (geometries.get(n) as Point).coordinate!!
    }

    protected override fun copyInternal(): MultiPoint {
        val points: Array<Point?> =
            arrayOfNulls<Point>(this.geometries.size)
        for (i in points.indices) {
            points[i] = this.geometries.get(i).copy() as Point
        }
        return MultiPoint(points.requireNoNulls(), factory)
    }

    protected override val typeCode: Int
        protected get() = TYPECODE_MULTIPOINT

    companion object {
        private const val serialVersionUID = -8048474874175355449L
    }
}