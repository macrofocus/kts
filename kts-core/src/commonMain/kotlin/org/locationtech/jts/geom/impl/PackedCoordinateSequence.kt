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

import org.locationtech.jts.geom.*
import org.locationtech.jts.legacy.Serializable
import org.locationtech.jts.legacy.SoftReference
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads
import kotlin.jvm.Transient

/**
 * A [CoordinateSequence] implementation based on a packed arrays.
 * In this implementation, [Coordinate]s returned by #toArray and #get are copies
 * of the internal values.
 * To change the actual values, use the provided setters.
 *
 * For efficiency, created Coordinate arrays
 * are cached using a soft reference.
 * The cache is cleared each time the coordinate sequence contents are
 * modified through a setter method.
 *
 * @version 1.7
 */
open abstract class PackedCoordinateSequence protected constructor(
//    @JvmField
    override var dimension: Int,
    override val measures: Int) :
    CoordinateSequence,
    Serializable {

//    open fun getDimension() : Int {
//        return dimension
//    }

    /**
     * @see CoordinateSequence.getDimension
     */
    /**
     * The dimensions of the coordinates held in the packed array
     */
//    override var dimension: Int
//        protected set
    /**
     * @see CoordinateSequence.getMeasures
     */
    /**
     * The number of measures of the coordinates held in the packed array.
     */
//    override var measures: Int
//        protected set

    /**
     * A soft reference to the Coordinate[] representation of this sequence.
     * Makes repeated coordinate array accesses more efficient.
     */
    @Transient
    protected var coordRef: SoftReference<Array<Coordinate>>? = null

    /**
     * @see CoordinateSequence.getCoordinate
     */
    override fun getCoordinate(i: Int): Coordinate {
        val coords = cachedCoords
        return coords?.get(i) ?: getCoordinateInternal(i)
    }

    /**
     * @see CoordinateSequence.getCoordinate
     */
    override fun getCoordinateCopy(i: Int): Coordinate? {
        return getCoordinateInternal(i)
    }

    /**
     * @see CoordinateSequence.getCoordinate
     */
    override fun getCoordinate(i: Int, coord: Coordinate?) {
        coord!!.x = getOrdinate(i, 0)
        coord.y = getOrdinate(i, 1)
        if (hasZ()) {
            coord.z = getZ(i)
        }
        if (hasM()) {
            coord.m = getM(i)
        }
    }

    /**
     * @see CoordinateSequence.toCoordinateArray
     */
    override fun toCoordinateArray(): Array<Coordinate> {
        val coords = cachedCoords
        // testing - never cache
        if (coords != null) return coords
        val c: Array<Coordinate?> = arrayOfNulls(size())
        for (i in c.indices) {
            c[i] = getCoordinateInternal(i)
        }
        val referent = c.requireNoNulls()
        coordRef = SoftReference(referent)
        return referent
    }// System.out.print("-");

    // System.out.print("-");
    private val cachedCoords: Array<Coordinate>?
        private get() = if (coordRef != null) {
            val coords = coordRef!!.get()
            if (coords != null) {
                coords
            } else {
                // System.out.print("-");
                coordRef = null
                null
            }
        } else {
            // System.out.print("-");
            null
        }

    /**
     * @see CoordinateSequence.getX
     */
    override fun getX(index: Int): kotlin.Double {
        return getOrdinate(index, 0)
    }

    /**
     * @see CoordinateSequence.getY
     */
    override fun getY(index: Int): kotlin.Double {
        return getOrdinate(index, 1)
    }

    /**
     * @see CoordinateSequence.getOrdinate
     */
    abstract override fun getOrdinate(index: Int, ordinateIndex: Int): kotlin.Double

    /**
     * Sets the first ordinate of a coordinate in this sequence.
     *
     * @param index  the coordinate index
     * @param value  the new ordinate value
     */
    fun setX(index: Int, value: kotlin.Double) {
        coordRef = null
        setOrdinate(index, 0, value)
    }

    /**
     * Sets the second ordinate of a coordinate in this sequence.
     *
     * @param index  the coordinate index
     * @param value  the new ordinate value
     */
    fun setY(index: Int, value: kotlin.Double) {
        coordRef = null
        setOrdinate(index, 1, value)
    }

    override fun toString(): String {
        return CoordinateSequences.toString(this)
    }

    //    @Throws(java.io.ObjectStreamException::class)
    protected fun readResolve(): Any {
        coordRef = null
        return this
    }

    /**
     * Returns a Coordinate representation of the specified coordinate, by always
     * building a new Coordinate object
     *
     * @param index  the coordinate index
     * @return  the [Coordinate] at the given index
     */
    protected abstract fun getCoordinateInternal(index: Int): Coordinate

    /**
     * @see Object.clone
     * @see CoordinateSequence.clone
     */
    @Deprecated("")
    abstract override fun clone(): Any
    abstract override fun copy(): PackedCoordinateSequence

    /**
     * Sets the ordinate of a coordinate in this sequence.
     * <br></br>
     * Warning: for performance reasons the ordinate index is not checked
     * - if it is over dimensions you may not get an exception but a meaningless value.
     *
     * @param index
     * the coordinate index
     * @param ordinate
     * the ordinate index in the coordinate, 0 based, smaller than the
     * number of dimensions
     * @param value
     * the new ordinate value
     */
    abstract override fun setOrdinate(index: Int, ordinate: Int, value: kotlin.Double)

    /**
     * Packed coordinate sequence implementation based on doubles
     */
    class Double : PackedCoordinateSequence {
        /**
         * Gets the underlying array containing the coordinate values.
         *
         * @return the array of coordinate values
         */
        /**
         * The packed coordinate array
         */
        var rawCoordinates: DoubleArray

        /**
         * Builds a new packed coordinate sequence
         *
         * @param coords  an array of <c>double</c> values that contains the ordinate values of the sequence
         * @param dimension the total number of ordinates that make up a [Coordinate] in this sequence.
         * @param measures the number of measure-ordinates each [Coordinate] in this sequence has.
         */
        constructor(coords: DoubleArray, dimension: Int, measures: Int) : super(dimension, measures) {
            require(coords.size % dimension == 0) {
                ("Packed array does not contain "
                        + "an integral number of coordinates")
            }
            rawCoordinates = coords
        }

        /**
         * Builds a new packed coordinate sequence out of a float coordinate array
         *
         * @param coords  an array of <c>float</c> values that contains the ordinate values of the sequence
         * @param dimension the total number of ordinates that make up a [Coordinate] in this sequence.
         * @param measures the number of measure-ordinates each [Coordinate] in this sequence has.
         */
        constructor(coords: FloatArray, dimension: Int, measures: Int) : super(dimension, measures) {
            rawCoordinates = DoubleArray(coords.size)
            for (i in coords.indices) {
                rawCoordinates[i] = coords[i].toDouble()
            }
        }
        /**
         * Builds a new packed coordinate sequence out of a coordinate array
         *
         * @param coordinates an array of [Coordinate]s
         * @param dimension the total number of ordinates that make up a [Coordinate] in this sequence.
         * @param measures the number of measure-ordinates each [Coordinate] in this sequence has.
         */
        /**
         * Builds a new packed coordinate sequence out of a coordinate array
         *
         * @param coordinates an array of [Coordinate]s
         */
        /**
         * Builds a new packed coordinate sequence out of a coordinate array
         *
         * @param coordinates an array of [Coordinate]s
         * @param dimension the total number of ordinates that make up a [Coordinate] in this sequence.
         */
        @JvmOverloads
        constructor(coordinates: Array<Coordinate>?, dimension: Int = 3, measures: Int = 0) : super(
            dimension,
            measures
        ) {
            var coordinates = coordinates
            if (coordinates == null) coordinates = arrayOf()
            rawCoordinates = DoubleArray(coordinates.size * this.dimension)
            for (i in coordinates.indices) {
                val offset = i * dimension
                rawCoordinates[offset] = coordinates[i].x
                rawCoordinates[offset + 1] = coordinates[i].y
                if (dimension >= 3) rawCoordinates[offset + 2] = coordinates[i].getOrdinate(2) // Z or M
                if (dimension >= 4) rawCoordinates[offset + 3] = coordinates[i].getOrdinate(3) // M
            }
        }

        /**
         * Builds a new empty packed coordinate sequence of a given size and dimension
         *
         * @param size the number of coordinates in this sequence
         * @param dimension the total number of ordinates that make up a [Coordinate] in this sequence.
         * @param measures the number of measure-ordinates each [Coordinate] in this sequence has.
         */
        constructor(size: Int, dimension: Int, measures: Int) : super(dimension, measures) {
            rawCoordinates = DoubleArray(size * this.dimension)
        }

        /**
         * @see PackedCoordinateSequence.getCoordinate
         */
        public override fun getCoordinateInternal(i: Int): Coordinate {
            val x = rawCoordinates[i * dimension]
            val y = rawCoordinates[i * dimension + 1]
            if (dimension == 2 && measures == 0) {
                return CoordinateXY(x, y)
            } else if (dimension == 3 && measures == 0) {
                val z = rawCoordinates[i * dimension + 2]
                return Coordinate(x, y, z)
            } else if (dimension == 3 && measures == 1) {
                val m = rawCoordinates[i * dimension + 2]
                return CoordinateXYM(x, y, m)
            } else if (dimension == 4 && measures == 1) {
                val z = rawCoordinates[i * dimension + 2]
                val m = rawCoordinates[i * dimension + 3]
                return CoordinateXYZM(x, y, z, m)
            }
            return Coordinate(x, y)
        }

        /**
         * @see CoordinateSequence.size
         */
        override fun size(): Int {
            return rawCoordinates.size / dimension
        }

        /**
         * @see Object.clone
         * @see PackedCoordinateSequence.clone
         */
        @Deprecated("")
        override fun clone(): Any {
            return copy()
        }

        /**
         * @see PackedCoordinateSequence.size
         */
        override fun copy(): Double {
            val clone = rawCoordinates.copyOf(rawCoordinates.size)
            return Double(clone, dimension, measures)
        }

        /**
         * @see PackedCoordinateSequence.getOrdinate
         */
        override fun getOrdinate(index: Int, ordinate: Int): kotlin.Double {
            return rawCoordinates[index * dimension + ordinate]
        }

        /**
         * @see PackedCoordinateSequence.setOrdinate
         */
        override fun setOrdinate(index: Int, ordinate: Int, value: kotlin.Double) {
            coordRef = null
            rawCoordinates[index * dimension + ordinate] = value
        }

        /**
         * @see CoordinateSequence.expandEnvelope
         */
        override fun expandEnvelope(env: Envelope): Envelope {
            var i = 0
            while (i < rawCoordinates.size) {
                env.expandToInclude(rawCoordinates[i], rawCoordinates[i + 1])
                i += dimension
            }
            return env
        }

        companion object {
            private const val serialVersionUID = 5777450686367912719L
        }
    }

    /**
     * Packed coordinate sequence implementation based on floats
     */
    class Float : PackedCoordinateSequence {
        /**
         * Gets the underlying array containing the coordinate values.
         *
         * @return the array of coordinate values
         */
        /**
         * The packed coordinate array
         */
        var rawCoordinates: FloatArray

        /**
         * Constructs a packed coordinate sequence from an array of `float`s
         *
         * @param coords  an array of <c>float</c> values that contains the ordinate values of the sequence
         * @param dimension the total number of ordinates that make up a [Coordinate] in this sequence.
         * @param measures the number of measure-ordinates each [Coordinate] in this sequence has.
         */
        constructor(coords: FloatArray, dimension: Int, measures: Int) : super(dimension, measures) {
            require(coords.size % dimension == 0) {
                ("Packed array does not contain "
                        + "an integral number of coordinates")
            }
            rawCoordinates = coords
        }

        /**
         * Constructs a packed coordinate sequence from an array of `double`s
         *
         * @param coords  an array of <c>double</c> values that contains the ordinate values of the sequence
         * @param dimension the total number of ordinates that make up a [Coordinate] in this sequence.
         * @param measures the number of measure-ordinates each [Coordinate] in this sequence has.
         */
        constructor(coords: DoubleArray, dimension: Int, measures: Int) : super(dimension, measures) {
            rawCoordinates = FloatArray(coords.size)
            for (i in coords.indices) {
                rawCoordinates[i] = coords[i].toFloat()
            }
        }
        /**
         * Constructs a packed coordinate sequence out of a coordinate array
         *
         * @param coordinates an array of [Coordinate]s
         * @param dimension the total number of ordinates that make up a [Coordinate] in this sequence.
         * @param measures the number of measure-ordinates each [Coordinate] in this sequence has.
         */
        /**
         * Builds a new packed coordinate sequence out of a coordinate array
         *
         * @param coordinates an array of [Coordinate]s
         * @param dimension the total number of ordinates that make up a [Coordinate] in this sequence.
         */
        @JvmOverloads
        constructor(coordinates: Array<Coordinate>?, dimension: Int, measures: Int = 0) : super(dimension, measures) {
            var coordinates = coordinates
            if (coordinates == null) coordinates = arrayOf()
            rawCoordinates = FloatArray(coordinates.size * dimension)
            for (i in coordinates.indices) {
                val offset = i * dimension
                rawCoordinates[offset] = coordinates[i].x.toFloat()
                rawCoordinates[offset + 1] = coordinates[i].y.toFloat()
                if (dimension >= 3) rawCoordinates[offset + 2] = coordinates[i].getOrdinate(2).toFloat() // Z or M
                if (dimension >= 4) rawCoordinates[offset + 3] = coordinates[i].getOrdinate(3).toFloat() // M
            }
        }

        /**
         * Constructs an empty packed coordinate sequence of a given size and dimension
         *
         * @param size the number of coordinates in this sequence
         * @param dimension the total number of ordinates that make up a [Coordinate] in this sequence.
         * @param measures the number of measure-ordinates each [Coordinate] in this sequence has.
         */
        constructor(size: Int, dimension: Int, measures: Int) : super(dimension, measures) {
            rawCoordinates = FloatArray(size * this.dimension)
        }

        /**
         * @see PackedCoordinateSequence.getCoordinate
         */
        public override fun getCoordinateInternal(i: Int): Coordinate {
            val x = rawCoordinates[i * dimension].toDouble()
            val y = rawCoordinates[i * dimension + 1].toDouble()
            if (dimension == 2 && measures == 0) {
                return CoordinateXY(x, y)
            } else if (dimension == 3 && measures == 0) {
                val z = rawCoordinates[i * dimension + 2].toDouble()
                return Coordinate(x, y, z)
            } else if (dimension == 3 && measures == 1) {
                val m = rawCoordinates[i * dimension + 2].toDouble()
                return CoordinateXYM(x, y, m)
            } else if (dimension == 4 && measures == 1) {
                val z = rawCoordinates[i * dimension + 2].toDouble()
                val m = rawCoordinates[i * dimension + 3].toDouble()
                return CoordinateXYZM(x, y, z, m)
            }
            return Coordinate(x, y)
        }

        /**
         * @see CoordinateSequence.size
         */
        override fun size(): Int {
            return rawCoordinates.size / dimension
        }

        /**
         * @see Object.clone
         * @see PackedCoordinateSequence.clone
         */
        @Deprecated("")
        override fun clone(): Any {
            return copy()
        }

        /**
         * @see PackedCoordinateSequence.copy
         */
        override fun copy(): Float {
            val clone = rawCoordinates.copyOf(rawCoordinates.size)
            return Float(clone, dimension, measures)
        }

        /**
         * @see PackedCoordinateSequence.getOrdinate
         */
        override fun getOrdinate(index: Int, ordinate: Int): kotlin.Double {
            return rawCoordinates[index * dimension + ordinate].toDouble()
        }

        /**
         * @see PackedCoordinateSequence.setOrdinate
         */
        override fun setOrdinate(index: Int, ordinate: Int, value: kotlin.Double) {
            coordRef = null
            rawCoordinates[index * dimension + ordinate] = value.toFloat()
        }

        /**
         * @see CoordinateSequence.expandEnvelope
         */
        override fun expandEnvelope(env: Envelope): Envelope {
            var i = 0
            while (i < rawCoordinates.size) {
                env.expandToInclude(rawCoordinates[i].toDouble(), rawCoordinates[i + 1].toDouble())
                i += dimension
            }
            return env
        }

        companion object {
            private const val serialVersionUID = -2902252401427938986L
        }
    }

    companion object {
        private const val serialVersionUID = -3151899011275603L
    }

    /**
     * Creates an instance of this class
     * @param dimension the total number of ordinates that make up a [Coordinate] in this sequence.
     * @param measures the number of measure-ordinates each [Coordinate] in this sequence has.
     */
    init {
        require(dimension - measures >= 2) { "Must have at least 2 spatial dimensions" }
//        this.dimension = dimension
//        this.measures = measures
    }
}