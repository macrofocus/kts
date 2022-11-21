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

import org.locationtech.jts.algorithm.Angle
import org.locationtech.jts.algorithm.Angle.diff
import org.locationtech.jts.algorithm.CGAlgorithmsDD.signOfDet2x2
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.legacy.Cloneable
import org.locationtech.jts.legacy.Math.atan2
import org.locationtech.jts.legacy.Math.cos
import org.locationtech.jts.legacy.Math.sin
import org.locationtech.jts.legacy.Math.sqrt
import org.locationtech.jts.util.Assert.shouldNeverReachHere
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlin.math.PI

/**
 * A 2-dimensional mathematical vector represented by double-precision X and Y components.
 *
 * @author mbdavis
 */
class Vector2D : Cloneable {
    /**
     * The X component of this vector.
     */
    var x: Double
        private set

    /**
     * The Y component of this vector.
     */
    var y: Double
        private set

    @JvmOverloads
    constructor(x: Double = 0.0, y: Double = 0.0) {
        this.x = x
        this.y = y
    }

    constructor(v: Vector2D) {
        x = v.x
        y = v.y
    }

    constructor(from: Coordinate, to: Coordinate) {
        x = to.x - from.x
        y = to.y - from.y
    }

    constructor(v: Coordinate) {
        x = v.x
        y = v.y
    }

    fun getComponent(index: Int): Double {
        return if (index == 0) x else y
    }

    fun add(v: Vector2D): Vector2D {
        return create(x + v.x, y + v.y)
    }

    fun subtract(v: Vector2D): Vector2D {
        return create(x - v.x, y - v.y)
    }

    /**
     * Multiplies the vector by a scalar value.
     *
     * @param d the value to multiply by
     * @return a new vector with the value v * d
     */
    fun multiply(d: Double): Vector2D {
        return create(x * d, y * d)
    }

    /**
     * Divides the vector by a scalar value.
     *
     * @param d the value to divide by
     * @return a new vector with the value v / d
     */
    fun divide(d: Double): Vector2D {
        return create(x / d, y / d)
    }

    fun negate(): Vector2D {
        return create(-x, -y)
    }

    fun length(): Double {
        return sqrt(x * x + y * y)
    }

    fun lengthSquared(): Double {
        return x * x + y * y
    }

    fun normalize(): Vector2D {
        val length = length()
        return if (length > 0.0) divide(length) else create(0.0, 0.0)
    }

    fun average(v: Vector2D): Vector2D {
        return weightedSum(v, 0.5)
    }

    /**
     * Computes the weighted sum of this vector
     * with another vector,
     * with this vector contributing a fraction
     * of <tt>frac</tt> to the total.
     *
     *
     * In other words,
     * <pre>
     * sum = frac * this + (1 - frac) * v
    </pre> *
     *
     * @param v the vector to sum
     * @param frac the fraction of the total contributed by this vector
     * @return the weighted sum of the two vectors
     */
    fun weightedSum(v: Vector2D, frac: Double): Vector2D {
        return create(
            frac * x + (1.0 - frac) * v.x,
            frac * y + (1.0 - frac) * v.y
        )
    }

    /**
     * Computes the distance between this vector and another one.
     * @param v a vector
     * @return the distance between the vectors
     */
    fun distance(v: Vector2D): Double {
        val delx = v.x - x
        val dely = v.y - y
        return sqrt(delx * delx + dely * dely)
    }

    /**
     * Computes the dot-product of two vectors
     *
     * @param v a vector
     * @return the dot product of the vectors
     */
    fun dot(v: Vector2D): Double {
        return x * v.x + y * v.y
    }

    fun angle(): Double {
        return atan2(y, x)
    }

    fun angle(v: Vector2D): Double {
        return diff(v.angle(), angle())
    }

    fun angleTo(v: Vector2D): Double {
        val a1 = angle()
        val a2 = v.angle()
        val angDel = a2 - a1

        // normalize, maintaining orientation
        if (angDel <= -PI) return angDel + Angle.PI_TIMES_2
        return if (angDel > PI) angDel - Angle.PI_TIMES_2 else angDel
    }

    fun rotate(angle: Double): Vector2D {
        val cos: Double = cos(angle)
        val sin: Double = sin(angle)
        return create(
            x * cos - y * sin,
            x * sin + y * cos
        )
    }

    /**
     * Rotates a vector by a given number of quarter-circles (i.e. multiples of 90
     * degrees or Pi/2 radians). A positive number rotates counter-clockwise, a
     * negative number rotates clockwise. Under this operation the magnitude of
     * the vector and the absolute values of the ordinates do not change, only
     * their sign and ordinate index.
     *
     * @param numQuarters
     * the number of quarter-circles to rotate by
     * @return the rotated vector.
     */
    fun rotateByQuarterCircle(numQuarters: Int): Vector2D? {
        var nQuad = numQuarters % 4
        if (numQuarters < 0 && nQuad != 0) {
            nQuad = nQuad + 4
        }
        when (nQuad) {
            0 -> return create(x, y)
            1 -> return create(-y, x)
            2 -> return create(-x, -y)
            3 -> return create(y, -x)
        }
        shouldNeverReachHere()
        return null
    }

    fun isParallel(v: Vector2D): Boolean {
        return 0.0 == signOfDet2x2(x, y, v.x, v.y).toDouble()
    }

    fun translate(coord: Coordinate): Coordinate {
        return Coordinate(x + coord.x, y + coord.y)
    }

    fun toCoordinate(): Coordinate {
        return Coordinate(x, y)
    }

    /**
     * Creates a copy of this vector
     *
     * @return a copy of this vector
     */
    override fun clone(): Any {
        return Vector2D(this)
    }

    /**
     * Gets a string representation of this vector
     *
     * @return a string representing this vector
     */
    override fun toString(): String {
        return "[$x, $y]"
    }

    /**
     * Tests if a vector <tt>o</tt> has the same values for the x and y
     * components.
     *
     * @param o
     * a <tt>Vector2D</tt> with which to do the comparison.
     * @return true if <tt>other</tt> is a <tt>Vector2D</tt> with the same
     * values for the x and y components.
     */
    override fun equals(o: Any?): Boolean {
        if (o !is Vector2D) {
            return false
        }
        val v = o
        return x == v.x && y == v.y
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
        return result
    }

    companion object {
        /**
         * Creates a new vector with given X and Y components.
         *
         * @param x the x component
         * @param y the y component
         * @return a new vector
         */
        @JvmStatic
        fun create(x: Double, y: Double): Vector2D {
            return Vector2D(x, y)
        }

        /**
         * Creates a new vector from an existing one.
         *
         * @param v the vector to copy
         * @return a new vector
         */
        fun create(v: Vector2D): Vector2D {
            return Vector2D(v)
        }

        /**
         * Creates a vector from a [Coordinate].
         *
         * @param coord the Coordinate to copy
         * @return a new vector
         */
        @JvmStatic
        fun create(coord: Coordinate): Vector2D {
            return Vector2D(coord)
        }

        /**
         * Creates a vector with the direction and magnitude
         * of the difference between the
         * <tt>to</tt> and <tt>from</tt> [Coordinate]s.
         *
         * @param from the origin Coordinate
         * @param to the destination Coordinate
         * @return a new vector
         */
        fun create(from: Coordinate, to: Coordinate): Vector2D {
            return Vector2D(from, to)
        }
    }
}