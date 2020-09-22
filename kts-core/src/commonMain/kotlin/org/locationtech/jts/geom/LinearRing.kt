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

/**
 * Models an OGC SFS `LinearRing`.
 * A `LinearRing` is a [LineString] which is both closed and simple.
 * In other words,
 * the first and last coordinate in the ring must be equal,
 * and the interior of the ring must not self-intersect.
 * Either orientation of the ring is allowed.
 *
 * A ring must have either 0 or 4 or more points.
 * The first and last points must be equal (in 2D).
 * If these conditions are not met, the constructors throw
 * an [IllegalArgumentException]
 *
 * @version 1.7
 */
open class LinearRing(points: CoordinateSequence?, factory: GeometryFactory) : LineString(points, factory) {
    /**
     * Constructs a `LinearRing` with the given points.
     *
     * @param  points          points forming a closed and simple linestring, or
     * `null` or an empty array to create the empty geometry.
     * This array must not contain `null` elements.
     * @param  precisionModel  the specification of the grid of allowable points
     * for this `LinearRing`
     * @param  SRID            the ID of the Spatial Reference System used by this
     * `LinearRing`
     * @throws IllegalArgumentException if the ring is not closed, or has too few points
     *
     */
    @Deprecated("Use GeometryFactory instead")
    constructor(
        points: Array<Coordinate>, precisionModel: PrecisionModel,
        SRID: Int
    ) : this(points, GeometryFactory(precisionModel, SRID)) {
        validateConstruction()
    }

    /**
     * This method is ONLY used to avoid deprecation warnings.
     * @param points
     * @param factory
     * @throws IllegalArgumentException if the ring is not closed, or has too few points
     */
    private constructor(
        points: Array<Coordinate>,
        factory: GeometryFactory
    ) : this(factory.coordinateSequenceFactory.create(points), factory)

    private fun validateConstruction() {
        require(!(!isEmpty && !super.isClosed)) { "Points of LinearRing do not form a closed linestring" }
        require(coordinateSequence!!.size() !in 1 until MINIMUM_VALID_SIZE) {
            ("Invalid number of points in LinearRing (found "
                    + coordinateSequence!!.size() + " - must be 0 or >= 4)")
        }
    }

    /**
     * Tests whether this ring is closed.
     * Empty rings are closed by definition.
     *
     * @return true if this ring is closed
     */
    override val isClosed: Boolean
        get() {
            return if (isEmpty) {
                // empty LinearRings are closed by definition
                true
            } else super.isClosed
        }

    override fun copyInternal(): LinearRing {
        return LinearRing(coordinateSequence!!.copy(), factory)
    }

    override fun reverse(): LinearRing {
        return super.reverse() as LinearRing
    }

    public override fun reverseInternal(): LinearRing {
        val seq = coordinateSequence!!.copy()
        CoordinateSequences.reverse(seq)
        return factory.createLinearRing(seq)
    }

    companion object {
        /**
         * The minimum number of vertices allowed in a valid non-empty ring (= 4).
         * Empty rings with 0 vertices are also valid.
         */
        const val MINIMUM_VALID_SIZE = 4
        private const val serialVersionUID = -4261142084085851829L
    }

    /**
     * Constructs a `LinearRing` with the vertices
     * specified by the given [CoordinateSequence].
     *
     * @param  points  a sequence points forming a closed and simple linestring, or
     * `null` to create the empty geometry.
     *
     * @throws IllegalArgumentException if the ring is not closed, or has too few points
     */
    init {
        validateConstruction()
    }
}