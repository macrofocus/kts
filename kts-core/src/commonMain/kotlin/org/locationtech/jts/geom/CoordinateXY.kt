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
 * Coordinate subclass supporting XY ordinates.
 *
 * This data object is suitable for use with coordinate sequences with <tt>dimension</tt> = 2.
 *
 * The [Coordinate.z] field is visible, but intended to be ignored.
 *
 * @since 1.16
 */
class CoordinateXY : Coordinate {
    /** Default constructor  */
    constructor() : super() {}

    /**
     * Constructs a CoordinateXY instance with the given ordinates.
     *
     * @param x the X ordinate
     * @param y the Y ordinate
     */
    constructor(x: Double, y: Double) : super(x, y, NULL_ORDINATE) {}

    /**
     * Constructs a CoordinateXY instance with the x and y ordinates of the given Coordinate.
     *
     * @param coord the Coordinate providing the ordinates
     */
    constructor(coord: Coordinate) : super(coord.x, coord.y) {}

    /**
     * Constructs a CoordinateXY instance with the x and y ordinates of the given CoordinateXY.
     *
     * @param coord the CoordinateXY providing the ordinates
     */
    constructor(coord: CoordinateXY) : super(coord.x, coord.y) {}

    /**
     * Creates a copy of this CoordinateXY.
     *
     * @return a copy of this CoordinateXY
     */
    override fun copy(): CoordinateXY {
        return CoordinateXY(this)
    }

    /**
     * Create a new Coordinate of the same type as this Coordinate, but with no values.
     *
     * @return a new Coordinate
     */
    override fun create(): Coordinate {
        return CoordinateXY()
    }
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
    }

    override fun getOrdinate(ordinateIndex: Int): Double {
        when (ordinateIndex) {
            X -> return x
            Y -> return y
        }
        return Double.NaN
        // disable for now to avoid regression issues
        //throw new IllegalArgumentException("Invalid ordinate index: " + ordinateIndex);
    }

    override fun setOrdinate(ordinateIndex: Int, value: Double) {
        when (ordinateIndex) {
            X -> x = value
            Y -> y = value
            else -> throw IllegalArgumentException("Invalid ordinate index: $ordinateIndex")
        }
    }

    override fun toString(): String {
        return "($x, $y)"
    }

    companion object {
        private const val serialVersionUID = 3532307803472313082L

        /** Standard ordinate index value for X  */
        const val X = 0

        /** Standard ordinate index value for Y  */
        const val Y = 1

        /** CoordinateXY does not support Z values.  */
        const val Z = -1

        /** CoordinateXY does not support M measures.  */
        const val M = -1
    }
}