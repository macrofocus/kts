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
package org.locationtech.jts.geom.impl

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.CoordinateArrays
import org.locationtech.jts.geom.CoordinateArrays.dimension
import org.locationtech.jts.geom.CoordinateArrays.measures
import org.locationtech.jts.geom.CoordinateSequence
import org.locationtech.jts.geom.CoordinateSequences.toString
import org.locationtech.jts.geom.Coordinates.create
import org.locationtech.jts.geom.Coordinates.dimension
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.legacy.Serializable
import kotlin.jvm.JvmOverloads

/**
 * A [CoordinateSequence] backed by an array of [Coordinate]s.
 * This is the implementation that [Geometry]s use by default.
 * Coordinates returned by #toArray and #getCoordinate are live --
 * modifications to them are actually changing the
 * CoordinateSequence's underlying data.
 * A dimension may be specified for the coordinates in the sequence,
 * which may be 2 or 3.
 * The actual coordinates will always have 3 ordinates,
 * but the dimension is useful as metadata in some situations.
 *
 * @version 1.7
 */
class CoordinateArraySequence : CoordinateSequence, Serializable {
    /**
     * @see org.locationtech.jts.geom.CoordinateSequence.getDimension
     */
    /**
     * The actual dimension of the coordinates in the sequence.
     * Allowable values are 2, 3 or 4.
     */
    override var dimension = 3
        private set

    /**
     * The number of measures of the coordinates in the sequence.
     * Allowable values are 0 or 1.
     */
    override var measures = 0
        private set
    private lateinit var coordinates: Array<Coordinate>

    /**
     * Constructs a sequence based on the given array
     * of [Coordinate]s (the array is not copied).
     *
     * It is your responsibility to ensure the array contains Coordinates of the
     * indicated dimension and measures (See
     * [CoordinateArrays.enforceConsistency] ).
     *
     * @param coordinates the coordinate array that will be referenced.
     * @param dimension the dimension of the coordinates
     */
    /**
     * Constructs a sequence based on the given array
     * of [Coordinate]s (the
     * array is not copied).
     * The coordinate dimension defaults to 3.
     *
     * @param coordinates the coordinate array that will be referenced.
     */
    @JvmOverloads
    constructor(
        coordinates: Array<Coordinate>?,
        dimension: Int = dimension(coordinates),
        measures: Int = measures(coordinates)
    ) {
        this.dimension = dimension
        this.measures = measures
        if (coordinates == null) {
            this.coordinates = emptyArray()
        } else {
            this.coordinates = coordinates
        }
    }

    /**
     * Constructs a sequence of a given size, populated
     * with new [Coordinate]s.
     *
     * @param size the size of the sequence to create
     */
    constructor(size: Int) {
        val coordinates: Array<Coordinate?> = arrayOfNulls(size)
        for (i in 0 until size) {
            coordinates[i] = Coordinate()
        }
        this.coordinates = coordinates.requireNoNulls()
    }

    /**
     * Constructs a sequence of a given size, populated
     * with new [Coordinate]s.
     *
     * @param size the size of the sequence to create
     * @param dimension the dimension of the coordinates
     */
    constructor(size: Int, dimension: Int) {
        val coordinates: Array<Coordinate?> = arrayOfNulls(size)
        this.dimension = dimension
        for (i in 0 until size) {
            coordinates[i] = create(dimension)
        }
        this.coordinates = coordinates.requireNoNulls()
    }

    /**
     * Constructs a sequence of a given size, populated
     * with new [Coordinate]s.
     *
     * @param size the size of the sequence to create
     * @param dimension the dimension of the coordinates
     */
    constructor(size: Int, dimension: Int, measures: Int) {
        val coordinates: Array<Coordinate?> = arrayOfNulls(size)
        this.dimension = dimension
        this.measures = measures
        for (i in 0 until size) {
            coordinates[i] = createCoordinate()
        }
        this.coordinates = coordinates.requireNoNulls()
    }

    /**
     * Creates a new sequence based on a deep copy of the given [CoordinateSequence].
     * The coordinate dimension is set to equal the dimension of the input.
     *
     * @param coordSeq the coordinate sequence that will be copied.
     */
    constructor(coordSeq: CoordinateSequence?) {
        val coordinates: Array<Coordinate?>
        // NOTE: this will make a sequence of the default dimension
        if (coordSeq == null) {
            coordinates = arrayOfNulls(0)
            return
        }
        dimension = coordSeq.dimension
        measures = coordSeq.measures
        coordinates = arrayOfNulls(coordSeq.size())
        for (i in coordinates.indices) {
            coordinates[i] = coordSeq.getCoordinateCopy(i)
        }
        this.coordinates = coordinates.requireNoNulls()
    }

    /**
     * Get the Coordinate with index i.
     *
     * @param i
     * the index of the coordinate
     * @return the requested Coordinate instance
     */
    override fun getCoordinate(i: Int): Coordinate {
        return coordinates[i]
    }

    /**
     * Get a copy of the Coordinate with index i.
     *
     * @param i  the index of the coordinate
     * @return a copy of the requested Coordinate
     */
    override fun getCoordinateCopy(i: Int): Coordinate? {
        val copy = createCoordinate()
        copy!!.setCoordinate(coordinates[i]!!)
        return copy
    }

    /**
     * @see org.locationtech.jts.geom.CoordinateSequence.getX
     */
    override fun getCoordinate(index: Int, coord: Coordinate?) {
        coord!!.setCoordinate(coordinates[index])
    }

    /**
     * @see org.locationtech.jts.geom.CoordinateSequence.getX
     */
    override fun getX(index: Int): Double {
        return coordinates[index]!!.x
    }

    /**
     * @see org.locationtech.jts.geom.CoordinateSequence.getY
     */
    override fun getY(index: Int): Double {
        return coordinates[index]!!.y
    }

    /**
     * @see org.locationtech.jts.geom.CoordinateSequence.getZ
     */
    override fun getZ(index: Int): Double {
        return if (hasZ()) {
            coordinates[index]!!.z
        } else {
            Double.NaN
        }
    }

    /**
     * @see org.locationtech.jts.geom.CoordinateSequence.getM
     */
    override fun getM(index: Int): Double {
        return if (hasM()) {
            coordinates[index]!!.m
        } else {
            Double.NaN
        }
    }

    /**
     * @see org.locationtech.jts.geom.CoordinateSequence.getOrdinate
     */
    override fun getOrdinate(index: Int, ordinateIndex: Int): Double {
        return when (ordinateIndex) {
            CoordinateSequence.X -> coordinates[index]!!.x
            CoordinateSequence.Y -> coordinates[index]!!.y
            else -> coordinates[index]!!.getOrdinate(ordinateIndex)
        }
    }

    /**
     * Creates a deep copy of the Object
     *
     * @return The deep copy
     */
    @Deprecated("")
    override fun clone(): Any {
        return copy()
    }

    /**
     * Creates a deep copy of the CoordinateArraySequence
     *
     * @return The deep copy
     */
    override fun copy(): CoordinateArraySequence {
        val cloneCoordinates = arrayOfNulls<Coordinate>(size())
        for (i in coordinates.indices) {
            val duplicate = createCoordinate()
            duplicate!!.setCoordinate(coordinates[i]!!)
            cloneCoordinates[i] = duplicate
        }
        return CoordinateArraySequence(cloneCoordinates.requireNoNulls(), dimension, measures)
    }

    /**
     * Returns the size of the coordinate sequence
     *
     * @return the number of coordinates
     */
    override fun size(): Int {
        return coordinates.size
    }

    /**
     * @see org.locationtech.jts.geom.CoordinateSequence.setOrdinate
     */
    override fun setOrdinate(index: Int, ordinateIndex: Int, value: Double) {
        when (ordinateIndex) {
            CoordinateSequence.X -> coordinates[index]!!.x = value
            CoordinateSequence.Y -> coordinates[index]!!.y = value
            else -> coordinates[index]!!.setOrdinate(ordinateIndex, value)
        }
    }

    /**
     * This method exposes the internal Array of Coordinate Objects
     *
     * @return the Coordinate[] array.
     */
    override fun toCoordinateArray(): Array<Coordinate> {
        return coordinates
    }

    override fun expandEnvelope(env: Envelope): Envelope {
        for (i in coordinates.indices) {
            env.expandToInclude(coordinates[i]!!)
        }
        return env
    }

    /**
     * Returns the string Representation of the coordinate array
     *
     * @return The string
     */
    override fun toString(): String {
        return if (coordinates.isNotEmpty()) {
            val strBuilder = StringBuilder(17 * coordinates.size)
            strBuilder.append('(')
            strBuilder.append(coordinates[0])
            for (i in 1 until coordinates.size) {
                strBuilder.append(", ")
                strBuilder.append(coordinates[i])
            }
            strBuilder.append(')')
            strBuilder.toString()
        } else {
            "()"
        }
    }

    companion object {
        //With contributions from Markus Schaber [schabios@logi-track.com] 2004-03-26
        private const val serialVersionUID = -915438501601840650L
    }
}