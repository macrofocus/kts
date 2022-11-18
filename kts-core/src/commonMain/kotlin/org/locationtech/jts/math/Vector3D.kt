/*
 * Copyright (c) 2016 Martin Davis.
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
package org.locationtech.jts.math

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.legacy.Math.sqrt

/**
 * Represents a vector in 3-dimensional Cartesian space.
 *
 * @author mdavis
 */
class Vector3D {
    /**
     * Gets the X component of this vector.
     *
     * @return the value of the X component
     */
    var x: Double
        private set

    /**
     * Gets the Y component of this vector.
     *
     * @return the value of the Y component
     */
    var y: Double
        private set

    /**
     * Gets the Z component of this vector.
     *
     * @return the value of the Z component
     */
    var z: Double
        private set

    /**
     * Creates a new 3D vector from a [Coordinate]. The coordinate should have
     * the X,Y and Z ordinates specified.
     *
     * @param v the Coordinate to copy
     */
    constructor(v: Coordinate) {
        x = v.x
        y = v.y
        z = v.z
    }

    /**
     * Creates a new vector with the direction and magnitude
     * of the difference between the
     * <tt>to</tt> and <tt>from</tt> [Coordinate]s.
     *
     * @param from the origin Coordinate
     * @param to the destination Coordinate
     */
    constructor(from: Coordinate, to: Coordinate) {
        x = to.x - from.x
        y = to.y - from.y
        z = to.z - from.z
    }

    /**
     * Creates a vector with the givne components.
     *
     * @param x the X component
     * @param y the Y component
     * @param z the Z component
     */
    constructor(x: Double, y: Double, z: Double) {
        this.x = x
        this.y = y
        this.z = z
    }

    /**
     * Computes a vector which is the sum
     * of this vector and the given vector.
     *
     * @param v the vector to add
     * @return the sum of this and `v`
     */
    fun add(v: Vector3D): Vector3D {
        return create(x + v.x, y + v.y, z + v.z)
    }

    /**
     * Computes a vector which is the difference
     * of this vector and the given vector.
     *
     * @param v the vector to subtract
     * @return the difference of this and `v`
     */
    fun subtract(v: Vector3D): Vector3D {
        return create(x - v.x, y - v.y, z - v.z)
    }

    /**
     * Creates a new vector which has the same direction
     * and with length equals to the length of this vector
     * divided by the scalar value `d`.
     *
     * @param d the scalar divisor
     * @return a new vector with divided length
     */
    fun divide(d: Double): Vector3D {
        return create(x / d, y / d, z / d)
    }

    /**
     * Computes the dot-product of two vectors
     *
     * @param v a vector
     * @return the dot product of the vectors
     */
    fun dot(v: Vector3D): Double {
        return x * v.x + y * v.y + z * v.z
    }

    /**
     * Computes the length of this vector.
     *
     * @return the length of the vector
     */
    fun length(): Double {
        return sqrt(x * x + y * y + z * z)
    }

    /**
     * Computes a vector having identical direction
     * but normalized to have length 1.
     *
     * @return a new normalized vector
     */
    fun normalize(): Vector3D {
        val length = length()
        return if (length > 0.0) divide(length()) else create(
            0.0,
            0.0,
            0.0
        )
    }

    /**
     * Gets a string representation of this vector
     *
     * @return a string representing this vector
     */
    override fun toString(): String {
        return "[$x, $y, $z]"
    }

    /**
     * Tests if a vector <tt>o</tt> has the same values for the components.
     *
     * @param o a <tt>Vector3D</tt> with which to do the comparison.
     * @return true if <tt>other</tt> is a <tt>Vector3D</tt> with the same values
     * for the x and y components.
     */
    override fun equals(o: Any?): Boolean {
        if (o !is Vector3D) {
            return false
        }
        val v = o
        return x == v.x && y == v.y && z == v.z
    }

    /**
     * Gets a hashcode for this vector.
     *
     * @return a hashcode for this vector
     */
    override fun hashCode(): Int {
        // Algorithm from Effective Java by Joshua Bloch
        var result = 17
        result = 37 * result + Coordinate.hashCode(x)
        result = 37 * result + Coordinate.hashCode(y)
        result = 37 * result + Coordinate.hashCode(z)
        return result
    }

    companion object {
        /**
         * Computes the dot product of the 3D vectors AB and CD.
         *
         * @param A the start point of the first vector
         * @param B the end point of the first vector
         * @param C the start point of the second vector
         * @param D the end point of the second vector
         * @return the dot product
         */
        fun dot(A: Coordinate, B: Coordinate, C: Coordinate, D: Coordinate): Double {
            val ABx = B.x - A.x
            val ABy = B.y - A.y
            val ABz = B.z - A.z
            val CDx = D.x - C.x
            val CDy = D.y - C.y
            val CDz = D.z - C.z
            return ABx * CDx + ABy * CDy + ABz * CDz
        }

        /**
         * Creates a new vector with given X, Y and Z components.
         *
         * @param x the X component
         * @param y the Y component
         * @param z the Z component
         * @return a new vector
         */
        fun create(x: Double, y: Double, z: Double): Vector3D {
            return Vector3D(x, y, z)
        }

        /**
         * Creates a vector from a 3D [Coordinate].
         * The coordinate should have the
         * X,Y and Z ordinates specified.
         *
         * @param coord the Coordinate to copy
         * @return a new vector
         */
        fun create(coord: Coordinate): Vector3D {
            return Vector3D(coord)
        }

        /**
         * Computes the 3D dot-product of two [Coordinate]s.
         *
         * @param v1 the first vector
         * @param v2 the second vector
         * @return the dot product of the vectors
         */
        fun dot(v1: Coordinate, v2: Coordinate): Double {
            return v1.x * v2.x + v1.y * v2.y + v1.z * v2.z
        }

        /**
         * Computes the length of a vector.
         *
         * @param v a coordinate representing a 3D vector
         * @return the length of the vector
         */
        fun length(v: Coordinate): Double {
            return sqrt(v.x * v.x + v.y * v.y + v.z * v.z)
        }

        /**
         * Computes a vector having identical direction
         * but normalized to have length 1.
         *
         * @param v a coordinate representing a 3D vector
         * @return a coordinate representing the normalized vector
         */
        fun normalize(v: Coordinate): Coordinate {
            val len = length(v)
            return Coordinate(v.x / len, v.y / len, v.z / len)
        }
    }
}