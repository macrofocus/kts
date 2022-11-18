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

import org.locationtech.jts.geom.CoordinateSequences.reverse
import org.locationtech.jts.geom.LineString

/**
 * Models an OGC SFS `LinearRing`.
 * A `LinearRing` is a [LineString] which is both closed and simple.
 * In other words,
 * the first and last coordinate in the ring must be equal,
 * and the ring must not self-intersect.
 * Either orientation of the ring is allowed.
 *
 * A ring must have either 0 or 3 or more points.
 * The first and last points must be equal (in 2D).
 * If these conditions are not met, the constructors throw
 * an [IllegalArgumentException].
 * A ring with 3 points is invalid, because it is collapsed
 * and thus has a self-intersection.  It is allowed to be constructed
 * so that it can be represented, and repaired if needed.
 *
 * @version 1.7
 */
class LinearRing(points: CoordinateSequence?, factory: GeometryFactory) :
    LineString(points, factory) {
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
    ) : this(factory.coordinateSequenceFactory.create(points), factory) {
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

    private fun validateConstruction() {
        if (!isEmpty && !super.isClosed) {
            throw IllegalArgumentException("Points of LinearRing do not form a closed linestring")
        }
        if (coordinateSequence!!.size() >= 1 && coordinateSequence!!.size() < MINIMUM_VALID_SIZE) {
            throw IllegalArgumentException(
                ("Invalid number of points in LinearRing (found "
                        + coordinateSequence!!.size()) + " - must be 0 or >= " + MINIMUM_VALID_SIZE + ")"
            )
        }
    }// empty LinearRings are closed by definition

    /**
     * Tests whether this ring is closed.
     * Empty rings are closed by definition.
     *
     * @return true if this ring is closed
     */
    override val isClosed: Boolean
        get() = if (isEmpty) {
            // empty LinearRings are closed by definition
            true
        } else super.isClosed
    override val geometryType: String
        get() = TYPENAME_LINEARRING
    protected override val typeCode: Int
        protected get() = TYPECODE_LINEARRING

    protected override fun copyInternal(): LinearRing {
        return LinearRing(coordinateSequence!!.copy(), factory)
    }

    override fun reverse(): LinearRing {
        return super.reverse() as LinearRing
    }

    override fun reverseInternal(): LinearRing {
        val seq: CoordinateSequence = coordinateSequence!!.copy()
        reverse(seq)
        return factory.createLinearRing(seq)
    }

    companion object {
        /**
         * The minimum number of vertices allowed in a valid non-empty ring.
         * Empty rings with 0 vertices are also valid.
         */
        const val MINIMUM_VALID_SIZE = 3
        private const val serialVersionUID = -4261142084085851829L
    }
}