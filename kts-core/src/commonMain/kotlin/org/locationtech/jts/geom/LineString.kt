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

import org.locationtech.jts.algorithm.Length
import org.locationtech.jts.operation.BoundaryOp

/**
 * Models an OGC-style `LineString`.
 * A LineString consists of a sequence of two or more vertices,
 * along with all points along the linearly-interpolated curves
 * (line segments) between each
 * pair of consecutive vertices.
 * Consecutive vertices may be equal.
 * The line segments in the line may intersect each other (in other words,
 * the linestring may "curl back" in itself and self-intersect.
 * Linestrings with exactly two identical points are invalid.
 *
 * A linestring must have either 0 or 2 or more points.
 * If these conditions are not met, the constructors throw
 * an [IllegalArgumentException]
 *
 * @version 1.7
 */
open class LineString : Geometry, Lineal {
    /**
     * The points of this `LineString`.
     */
    open var coordinateSequence: CoordinateSequence? = null
        protected set

    /**
     * Constructs a `LineString` with the given points.
     *
     * @param  points the points of the linestring, or `null`
     * to create the empty geometry. This array must not contain `null`
     * elements. Consecutive points may be equal.
     * @param  precisionModel  the specification of the grid of allowable points
     * for this `LineString`
     * @param  SRID            the ID of the Spatial Reference System used by this
     * `LineString`
     * @throws IllegalArgumentException if too few points are provided
     */

    @Deprecated("Use GeometryFactory instead ")
    constructor(
        points: Array<Coordinate>?,
        precisionModel: PrecisionModel,
        SRID: Int
    ) : super(GeometryFactory(precisionModel, SRID)) {
        init(factory.coordinateSequenceFactory.create(points))
    }

    /**
     * Constructs a `LineString` with the given points.
     *
     * @param  points the points of the linestring, or `null`
     * to create the empty geometry.
     * @throws IllegalArgumentException if too few points are provided
     */
    constructor(points: CoordinateSequence?, factory: GeometryFactory) : super(factory) {
        init(points)
    }

    private fun init(points: CoordinateSequence?) {
        var points = points
        if (points == null) {
            points = factory.coordinateSequenceFactory.create(arrayOf())
        }
        if (points.size() > 0 && points.size() < MINIMUM_VALID_SIZE) {
            throw IllegalArgumentException(
                "Invalid number of points in LineString (found "
                        + points.size() + " - must be 0 or >= " + MINIMUM_VALID_SIZE + ")"
            )
        }
        coordinateSequence = points
    }

    override val coordinates: Array<Coordinate>
        get() = coordinateSequence!!.toCoordinateArray()

    open fun getCoordinateN(n: Int): Coordinate {
        return coordinateSequence!!.getCoordinate(n)
    }

    override val coordinate: Coordinate?
        get() = if (isEmpty) null else coordinateSequence!!.getCoordinate(0)
    override val dimension: Int
        get() = 1
    override val boundaryDimension: Int
        get() = if (isClosed) {
            Dimension.FALSE
        } else 0
    override val isEmpty: Boolean
        get() = coordinateSequence!!.size() == 0
    override val numPoints: Int
        get() = coordinateSequence!!.size()

    fun getPointN(n: Int): Point {
        return factory.createPoint(coordinateSequence!!.getCoordinate(n))
    }

    val startPoint: Point?
        get() = if (isEmpty) {
            null
        } else getPointN(0)
    val endPoint: Point?
        get() = if (isEmpty) {
            null
        } else getPointN(numPoints - 1)
    open val isClosed: Boolean
        get() = if (isEmpty) {
            false
        } else getCoordinateN(0).equals2D(getCoordinateN(numPoints - 1))
    val isRing: Boolean
        get() = isClosed && isSimple
    override val geometryType: String
        get() = TYPENAME_LINESTRING

    /**
     * Returns the length of this `LineString`
     *
     * @return the length of the linestring
     */
    override val length: Double
        get() = Length.ofLine(coordinateSequence!!)

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
     * Creates a [LineString] whose coordinates are in the reverse
     * order of this objects
     *
     * @return a [LineString] with coordinates in the reverse order
     */
    override fun reverse(): LineString {
        return super.reverse() as LineString
    }

    protected override fun reverseInternal(): LineString {
        val seq = coordinateSequence!!.copy()
        CoordinateSequences.reverse(seq)
        return factory.createLineString(seq)
    }

    /**
     * Returns true if the given point is a vertex of this `LineString`.
     *
     * @param  pt  the `Coordinate` to check
     * @return     `true` if `pt` is one of this `LineString`
     * 's vertices
     */
    fun isCoordinate(pt: Coordinate?): Boolean {
        for (i in 0 until coordinateSequence!!.size()) {
            if (coordinateSequence!!.getCoordinate(i).equals(pt)) {
                return true
            }
        }
        return false
    }

    protected override fun computeEnvelopeInternal(): Envelope {
        return if (isEmpty) {
            Envelope()
        } else coordinateSequence!!.expandEnvelope(Envelope())!!
    }

    override fun equalsExact(other: Geometry?, tolerance: Double): Boolean {
        if (!isEquivalentClass(other!!)) {
            return false
        }
        val otherLineString = other as LineString
        if (coordinateSequence!!.size() != otherLineString.coordinateSequence!!.size()) {
            return false
        }
        for (i in 0 until coordinateSequence!!.size()) {
            if (!equal(
                    coordinateSequence!!.getCoordinate(i),
                    otherLineString.coordinateSequence!!.getCoordinate(i),
                    tolerance
                )
            ) {
                return false
            }
        }
        return true
    }

    override fun apply(filter: CoordinateFilter) {
        for (i in 0 until coordinateSequence!!.size()) {
            filter.filter(coordinateSequence!!.getCoordinate(i))
        }
    }

    override fun apply(filter: CoordinateSequenceFilter) {
        if (coordinateSequence!!.size() == 0) return
        for (i in 0 until coordinateSequence!!.size()) {
            filter.filter(coordinateSequence, i)
            if (filter.isDone) break
        }
        if (filter.isGeometryChanged) geometryChanged()
    }

    override fun apply(filter: GeometryFilter) {
        filter.filter(this)
    }

    override fun apply(filter: GeometryComponentFilter) {
        filter.filter(this)
    }

    /**
     * Creates and returns a full copy of this [LineString] object.
     * (including all coordinates contained by it).
     *
     * @return a clone of this instance
     */
    @Deprecated("")
    override fun clone(): Any {
        return copy()
    }

    protected override fun copyInternal(): LineString {
        return LineString(coordinateSequence!!.copy(), factory)
    }

    /**
     * Normalizes a LineString.  A normalized linestring
     * has the first point which is not equal to it's reflected point
     * less than the reflected point.
     */
    override fun normalize() {
        for (i in 0 until coordinateSequence!!.size() / 2) {
            val j = coordinateSequence!!.size() - 1 - i
            // skip equal points on both ends
            if (!coordinateSequence!!.getCoordinate(i).equals(coordinateSequence!!.getCoordinate(j))) {
                if (coordinateSequence!!.getCoordinate(i).compareTo(coordinateSequence!!.getCoordinate(j)) > 0) {
                    val copy = coordinateSequence!!.copy()
                    CoordinateSequences.reverse(copy)
                    coordinateSequence = copy
                }
                return
            }
        }
    }

    protected override fun isEquivalentClass(other: Geometry): Boolean {
        return other is LineString
    }

    public override fun compareToSameClass(o: Any?): Int {
        val line = o as LineString
        // MD - optimized implementation
        var i = 0
        var j = 0
        while (i < coordinateSequence!!.size() && j < line.coordinateSequence!!.size()) {
            val comparison = coordinateSequence!!.getCoordinate(i).compareTo(line.coordinateSequence!!.getCoordinate(j))
            if (comparison != 0) {
                return comparison
            }
            i++
            j++
        }
        if (i < coordinateSequence!!.size()) {
            return 1
        }
        return if (j < line.coordinateSequence!!.size()) {
            -1
        } else 0
    }

    override fun compareToSameClass(o: Any?, comp: CoordinateSequenceComparator): Int {
        val line = o as LineString
        return comp.compare(coordinateSequence, line.coordinateSequence)
    }

    open override val typeCode: Int
        get() = TYPECODE_LINESTRING

    companion object {
        private const val serialVersionUID = 3110669828065365560L

        /**
         * The minimum number of vertices allowed in a valid non-empty linestring.
         * Empty linestrings with 0 vertices are also valid.
         */
        const val MINIMUM_VALID_SIZE = 2
    }
}