/*
 * Copyright (c) 2016 Vivid Solutions.
 * Copyright (c) 2020 Macrofocus GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.geom

import org.locationtech.jts.util.Assert

/**
 * Represents a single point.
 *
 * A `Point` is topologically valid if and only if:
 *
 *  * the coordinate which defines it (if any) is a valid coordinate
 * (i.e. does not have an `NaN` X or Y ordinate)
 *
 * @version 1.7
 */
open class Point : Geometry, Puntal {
    /**
     * The `Coordinate` wrapped by this `Point`.
     */
    var coordinateSequence: CoordinateSequence? = null
        private set

    /**
     * Constructs a `Point` with the given coordinate.
     *
     * @param  coordinate      the coordinate on which to base this `Point`
     * , or `null` to create the empty geometry.
     * @param  precisionModel  the specification of the grid of allowable points
     * for this `Point`
     * @param  SRID            the ID of the Spatial Reference System used by this
     * `Point`
     */
    @Deprecated("Use GeometryFactory instead")
    constructor(coordinate: Coordinate?, precisionModel: PrecisionModel, SRID: Int) : super(
        GeometryFactory(
            precisionModel,
            SRID
        )
    ) {
        init(factory.coordinateSequenceFactory.create(
            coordinate?.let { arrayOf(it) } ?: arrayOf()))
    }

    /**
     * @param  coordinates      contains the single coordinate on which to base this `Point`
     * , or `null` to create the empty geometry.
     */
    constructor(coordinates: CoordinateSequence?, factory: GeometryFactory?) : super(factory!!) {
        init(coordinates)
    }

    private fun init(coordinates: CoordinateSequence?) {
        var coordinates = coordinates
        if (coordinates == null) {
            coordinates = factory.coordinateSequenceFactory.create(arrayOf())
        }
        Assert.isTrue(coordinates.size() <= 1)
        coordinateSequence = coordinates
    }

    override val coordinates: Array<Coordinate>?
        get() {
            return if (isEmpty) arrayOf() else arrayOf(
                coordinate
            ).requireNoNulls()
        }

    override val geometryType: String
        get() = TYPENAME_POINT

    override val numPoints: Int
        get() = if (isEmpty) 0 else 1
    override val isEmpty: Boolean
        get() = coordinateSequence!!.size() == 0
    override val isSimple: Boolean
        get() = true
    override val dimension: Int
        get() = 0
    override val boundaryDimension: Int
        get() = Dimension.FALSE
    val x: Double
        get() {
            checkNotNull(coordinate) { "getX called on empty Point" }
            return coordinate!!.x
        }
    val y: Double
        get() {
            checkNotNull(coordinate) { "getY called on empty Point" }
            return coordinate!!.y
        }
    override val coordinate: Coordinate?
        get() = if (coordinateSequence!!.size() != 0) coordinateSequence!!.getCoordinate(0) else null

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

    override fun computeEnvelopeInternal(): Envelope {
        if (isEmpty) {
            return Envelope()
        }
        val env = Envelope()
        env.expandToInclude(coordinateSequence!!.getX(0), coordinateSequence!!.getY(0))
        return env
    }

    override fun equalsExact(other: Geometry?, tolerance: Double): Boolean {
        if (!isEquivalentClass(other!!)) {
            return false
        }
        if (isEmpty && other.isEmpty) {
            return true
        }
        return if (isEmpty != other.isEmpty) {
            false
        } else equal((other as Point?)!!.coordinate!!, coordinate!!, tolerance)
    }

    override fun apply(filter: CoordinateFilter?) {
        if (isEmpty) {
            return
        }
        filter!!.filter(coordinate)
    }

    override fun apply(filter: CoordinateSequenceFilter?) {
        if (isEmpty) return
        filter!!.filter(coordinateSequence!!, 0)
        if (filter.isGeometryChanged) geometryChanged()
    }

    override fun apply(filter: GeometryFilter?) {
        filter!!.filter(this)
    }

    override fun apply(filter: GeometryComponentFilter?) {
        filter!!.filter(this)
    }

    /**
     * Creates and returns a full copy of this [Point] object.
     * (including all coordinates contained by it).
     *
     * @return a clone of this instance
     */
    @Deprecated("")
    override fun clone(): Any {
        return copy()
    }

    override fun copyInternal(): Point {
        return Point(coordinateSequence!!.copy(), factory)
    }

    override fun reverse(): Point {
        return super.reverse() as Point
    }

    override fun reverseInternal(): Point {
        return factory.createPoint(coordinateSequence!!.copy())
    }

    override fun normalize() {
        // a Point is always in normalized form
    }

    override fun compareToSameClass(other: Any?): Int {
        val point = other as Point?
        return coordinate!!.compareTo(point!!.coordinate!!)
    }

    override fun compareToSameClass(other: Any?, comp: CoordinateSequenceComparator?): Int {
        val point = other as Point?
        return comp!!.compare(coordinateSequence, point!!.coordinateSequence)
    }

    override val typeCode: Int
        get() = TYPECODE_POINT

    companion object {
        private const val serialVersionUID = 4902022702746614570L
    }
}