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
package org.locationtech.jts.geom.impl

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.CoordinateSequence
import org.locationtech.jts.geom.CoordinateSequenceFactory
import org.locationtech.jts.geom.Coordinates
import org.locationtech.jts.legacy.Serializable
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads

/**
 * Builds packed array coordinate sequences.
 * The array data type can be either
 * `double` or `float`,
 * and defaults to `double`.
 */
class PackedCoordinateSequenceFactory @JvmOverloads constructor(
    /**
     * Gets the type of packed coordinate sequence this factory builds, either
     * [PackedCoordinateSequenceFactory.FLOAT] or
     * [PackedCoordinateSequenceFactory.DOUBLE]
     *
     * @return the type of packed array built
     */
    val type: Int = DOUBLE
) : CoordinateSequenceFactory,
    Serializable {

    /**
     * @see CoordinateSequenceFactory.create
     */
    override fun create(coordinates: Array<Coordinate>?): CoordinateSequence {
        var dimension = DEFAULT_DIMENSION
        var measures = DEFAULT_MEASURES
        if (coordinates != null && coordinates.isNotEmpty() && coordinates[0] != null) {
            val first = coordinates[0]
            dimension = Coordinates.dimension(first)
            measures = Coordinates.measures(first)
        }
        return if (type == DOUBLE) {
            PackedCoordinateSequence.Double(coordinates, dimension, measures)
        } else {
            PackedCoordinateSequence.Float(coordinates, dimension, measures)
        }
    }

    /**
     * @see CoordinateSequenceFactory.create
     */
    override fun create(coordSeq: CoordinateSequence): CoordinateSequence {
        val dimension = coordSeq.dimension
        val measures = coordSeq.measures
        return if (type == DOUBLE) {
            PackedCoordinateSequence.Double(
                coordSeq.toCoordinateArray(),
                dimension,
                measures
            )
        } else {
            PackedCoordinateSequence.Float(coordSeq.toCoordinateArray(), dimension, measures)
        }
    }
    /**
     * Creates a packed coordinate sequence of type [.DOUBLE]
     * from the provided array
     * using the given coordinate dimension and measure count.
     *
     * @param packedCoordinates the array containing coordinate values
     * @param dimension the coordinate dimension
     * @param measures the coordinate measure count
     * @return a packed coordinate sequence of type [.DOUBLE]
     */
    /**
     * Creates a packed coordinate sequence of type [.DOUBLE]
     * from the provided array
     * using the given coordinate dimension and a measure count of 0.
     *
     * @param packedCoordinates the array containing coordinate values
     * @param dimension the coordinate dimension
     * @return a packed coordinate sequence of type [.DOUBLE]
     */
    @JvmOverloads
    fun create(packedCoordinates: DoubleArray?, dimension: Int, measures: Int = DEFAULT_MEASURES): CoordinateSequence {
        return if (type == DOUBLE) {
            PackedCoordinateSequence.Double(packedCoordinates!!, dimension, measures)
        } else {
            PackedCoordinateSequence.Float(packedCoordinates!!, dimension, measures)
        }
    }
    /**
     * Creates a packed coordinate sequence of type [.FLOAT]
     * from the provided array.
     *
     * @param packedCoordinates the array containing coordinate values
     * @param dimension the coordinate dimension
     * @param measures the coordinate measure count
     * @return a packed coordinate sequence of type [.FLOAT]
     */
    /**
     * Creates a packed coordinate sequence of type [.FLOAT]
     * from the provided array.
     *
     * @param packedCoordinates the array containing coordinate values
     * @param dimension the coordinate dimension
     * @return a packed coordinate sequence of type [.FLOAT]
     */
    @JvmOverloads
    fun create(packedCoordinates: FloatArray?, dimension: Int, measures: Int = DEFAULT_MEASURES): CoordinateSequence {
        return if (type == DOUBLE) {
            PackedCoordinateSequence.Double(packedCoordinates!!, dimension, measures)
        } else {
            PackedCoordinateSequence.Float(packedCoordinates!!, dimension, measures)
        }
    }

    /**
     * @see CoordinateSequenceFactory.create
     */
    override fun create(size: Int, dimension: Int): CoordinateSequence {
        return if (type == DOUBLE) {
            PackedCoordinateSequence.Double(
                size,
                dimension,
                DEFAULT_MEASURES
            )
        } else {
            PackedCoordinateSequence.Float(
                size,
                dimension,
                DEFAULT_MEASURES
            )
        }
    }

    /**
     * @see CoordinateSequenceFactory.create
     */
    override fun create(size: Int, dimension: Int, measures: Int): CoordinateSequence {
        return if (type == DOUBLE) {
            PackedCoordinateSequence.Double(size, dimension, measures)
        } else {
            PackedCoordinateSequence.Float(size, dimension, measures)
        }
    }

    companion object {
        private const val serialVersionUID = -3558264771905224525L

        /**
         * Type code for arrays of type `double`.
         */
        const val DOUBLE = 0

        /**
         * Type code for arrays of type `float`.
         */
        const val FLOAT = 1

        /**
         * A factory using array type [.DOUBLE]
         */
        @JvmField
        val DOUBLE_FACTORY = PackedCoordinateSequenceFactory(DOUBLE)

        /**
         * A factory using array type [.FLOAT]
         */
        @JvmField
        val FLOAT_FACTORY = PackedCoordinateSequenceFactory(FLOAT)
        private const val DEFAULT_MEASURES = 0
        private const val DEFAULT_DIMENSION = 3
    }
    /**
     * Creates a new PackedCoordinateSequenceFactory
     * of the given type.
     * Acceptable type values are
     * [PackedCoordinateSequenceFactory.FLOAT]or
     * [PackedCoordinateSequenceFactory.DOUBLE]
     */
    /**
     * Creates a new PackedCoordinateSequenceFactory
     * of type DOUBLE.
     */
}