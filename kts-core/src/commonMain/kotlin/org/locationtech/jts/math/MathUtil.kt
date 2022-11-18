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

import org.locationtech.jts.legacy.Math.floor
import org.locationtech.jts.legacy.Math.isInfinite
import org.locationtech.jts.legacy.Math.isNaN
import org.locationtech.jts.legacy.Math.log
import org.locationtech.jts.legacy.Math.sqrt
import kotlin.jvm.JvmOverloads

/**
 * Various utility functions for mathematical and numerical operations.
 *
 * @author mbdavis
 */
object MathUtil {
    /**
     * Clamps a <tt>double</tt> value to a given range.
     * @param x the value to clamp
     * @param min the minimum value of the range
     * @param max the maximum value of the range
     * @return the clamped value
     */
    fun clamp(x: Double, min: Double, max: Double): Double {
        if (x < min) return min
        return if (x > max) max else x
    }

    /**
     * Clamps an <tt>int</tt> value to a given range.
     * @param x the value to clamp
     * @param min the minimum value of the range
     * @param max the maximum value of the range
     * @return the clamped value
     */
    fun clamp(x: Int, min: Int, max: Int): Int {
        if (x < min) return min
        return if (x > max) max else x
    }

    /**
     * Clamps an integer to a given maximum limit.
     *
     * @param x the value to clamp
     * @param max the maximum value
     * @return the clamped value
     */
    fun clampMax(x: Int, max: Int): Int {
        return if (x > max) max else x
    }

    /**
     * Computes the ceiling function of the dividend of two integers.
     *
     * @param num the numerator
     * @param denom the denominator
     * @return the ceiling of num / denom
     */
    fun ceil(num: Int, denom: Int): Int {
        val div = num / denom
        return if (div * denom >= num) div else div + 1
    }

    private val LOG_10: Double = log(10.0)

    /**
     * Computes the base-10 logarithm of a <tt>double</tt> value.
     *
     *  * If the argument is NaN or less than zero, then the result is NaN.
     *  * If the argument is positive infinity, then the result is positive infinity.
     *  * If the argument is positive zero or negative zero, then the result is negative infinity.
     *
     *
     * @param x a positive number
     * @return the value log a, the base-10 logarithm of the input value
     */
    fun log10(x: Double): Double {
        val ln: Double = log(x)
        if (isInfinite(ln)) return ln
        return if (isNaN(ln)) ln else ln / LOG_10
    }

    /**
     * Computes an index which wraps around a given maximum value.
     * For values &gt;= 0, this is equals to <tt>val % max</tt>.
     * For values &lt; 0, this is equal to <tt>max - (-val) % max</tt>
     *
     * @param index the value to wrap
     * @param max the maximum value (or modulus)
     * @return the wrapped index
     */
    fun wrap(index: Int, max: Int): Int {
        return if (index < 0) {
            max - -index % max
        } else index % max
    }

    /**
     * Computes the average of two numbers.
     *
     * @param x1 a number
     * @param x2 a number
     * @return the average of the inputs
     */
    fun average(x1: Double, x2: Double): Double {
        return (x1 + x2) / 2.0
    }

    fun max(v1: Double, v2: Double, v3: Double): Double {
        var max = v1
        if (v2 > max) max = v2
        if (v3 > max) max = v3
        return max
    }

    fun max(v1: Double, v2: Double, v3: Double, v4: Double): Double {
        var max = v1
        if (v2 > max) max = v2
        if (v3 > max) max = v3
        if (v4 > max) max = v4
        return max
    }

    fun min(v1: Double, v2: Double, v3: Double, v4: Double): Double {
        var min = v1
        if (v2 < min) min = v2
        if (v3 < min) min = v3
        if (v4 < min) min = v4
        return min
    }

    /**
     * The inverse of the Golden Ratio phi.
     */
    val PHI_INV: Double = (sqrt(5.0) - 1.0) / 2.0
    /**
     * Generates a quasi-random sequence of numbers in the range [0,1].
     * They are produced by an additive recurrence with constant .
     * <pre>
     * R() :  t<sub>n</sub> = { t<sub>0</sub> + n },  n = 1,2,3,...
    </pre> *
     * When  is irrational this produces a
     * [Low discrepancy sequence](https://en.wikipedia.org/wiki/Low-discrepancy_sequence#Additive_recurrence)
     * which is more evenly
     * distributed than random numbers.
     *
     *
     * The sequence is initialized by calling it
     * with any positive fractional number. 0 works well for most uses.
     *
     * @param curr the current number in the sequence
     * @param alpha the sequence additive constant
     * @return the next value in the sequence
     */
    /**
     * Generates a quasi-random sequence of numbers in the range [0,1].
     * They are produced by an additive recurrence with 1/ as the constant.
     * This produces a low-discrepancy sequence which is more evenly
     * distribute than random numbers.
     *
     *
     * See [Wikipedia: Low-discrepancy Sequences - Additive Recurrence](https://en.wikipedia.org/wiki/Low-discrepancy_sequence#Additive_recurrence).
     *
     *
     * The sequence is initialized by calling it
     * with any positive fractional number; 0 works well for most uses.
     *
     * @param curr the current number in the sequence
     * @return the next value in the sequence
     */
    @JvmOverloads
    fun quasirandom(curr: Double, alpha: Double = PHI_INV): Double {
        val next = curr + alpha
        return if (next < 1) next else next - floor(next)
    }
}