/*
 * Copyright (c) 2018 Vivid Solutions
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.geom

/**
 * Coordinate subclass supporting XYM ordinates.
 *
 * This data object is suitable for use with coordinate sequences with <tt>dimension</tt> = 3 and <tt>measures</tt> = 1.
 *
 * The [Coordinate.z] field is visible, but intended to be ignored.
 *
 * @since 1.16
 */
class CoordinateXYM : Coordinate {
    /** Default constructor  */
    constructor() : super() {
        m = 0.0
    }

    /**
     * Constructs a CoordinateXYM instance with the given ordinates and measure.
     *
     * @param x the X ordinate
     * @param y the Y ordinate
     * @param m the M measure value
     */
    constructor(x: Double, y: Double, m: Double) : super(x, y, NULL_ORDINATE) {
        this.m = m
    }

    /**
     * Constructs a CoordinateXYM instance with the x and y ordinates of the given Coordinate.
     *
     * @param coord the coordinate providing the ordinates
     */
    constructor(coord: Coordinate) : super(coord.x, coord.y) {
//        m = m
    }

    /**
     * Constructs a CoordinateXY instance with the x and y ordinates of the given CoordinateXYM.
     *
     * @param coord the coordinate providing the ordinates
     */
    constructor(coord: CoordinateXYM) : super(coord.x, coord.y) {
        m = coord.m
    }

    /**
     * Creates a copy of this CoordinateXYM.
     *
     * @return a copy of this CoordinateXYM
     */
    override fun copy(): CoordinateXYM {
        return CoordinateXYM(this)
    }

    /**
     * Create a new Coordinate of the same type as this Coordinate, but with no values.
     *
     * @return a new Coordinate
     */
    override fun create(): Coordinate {
        return CoordinateXYM()
    }
    /** The m-measure, if available.  */
    /** The m-measure.  */
    override var m: Double = 0.0
    /** The z-ordinate is not supported  */
    /** The z-ordinate is not supported  */
    override var z: Double
        get() = NULL_ORDINATE
        set(z) {
            throw IllegalArgumentException("CoordinateXY dimension 2 does not support z-ordinate")
        }

    override fun setCoordinate(other: Coordinate) {
        x = other.x
        y = other.y
//        z = other.z
        m = other.m
    }

    override fun getOrdinate(ordinateIndex: Int): Double {
        when (ordinateIndex) {
            X -> return x
            Y -> return y
            M -> return m
        }
        throw IllegalArgumentException("Invalid ordinate index: $ordinateIndex")
    }

    override fun setOrdinate(ordinateIndex: Int, value: Double) {
        when (ordinateIndex) {
            X -> x = value
            Y -> y = value
            M -> m = value
            else -> throw IllegalArgumentException("Invalid ordinate index: $ordinateIndex")
        }
    }

    override fun toString(): String {
        return "(" + x + ", " + y + " m=" + m + ")"
    }

    companion object {
        private const val serialVersionUID = 2842127537691165613L

        /** Standard ordinate index value for X  */
        const val X = 0

        /** Standard ordinate index value for Y  */
        const val Y = 1

        /** CoordinateXYM does not support Z values.  */
        const val Z = -1

        /**
         * Standard ordinate index value for M in XYM sequences.
         *
         *
         * This constant assumes XYM coordinate sequence definition.  Check this assumption using
         * [CoordinateSequence.getDimension] and [CoordinateSequence.getMeasures] before use.
         */
        const val M = 2
    }
}