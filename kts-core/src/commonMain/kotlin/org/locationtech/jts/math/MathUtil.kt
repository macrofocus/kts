/*
 * Copyright (c) 2016 Martin Davis.
 * Copyright (c) 2020 Macrofocus GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.math

import org.locationtech.jts.legacy.Math
import kotlin.jvm.JvmStatic

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

    private val LOG_10 = Math.log(10.0)

    /**
     * Computes the base-10 logarithm of a <tt>double</tt> value.
     *
     *  * If the argument is NaN or less than zero, then the result is NaN.
     *  * If the argument is positive infinity, then the result is positive infinity.
     *  * If the argument is positive zero or negative zero, then the result is negative infinity.
     *
     * @param x a positive number
     * @return the value log a, the base-10 logarithm of the input value
     */
    fun log10(x: Double): Double {
        val ln = Math.log(x)
        if (Math.isInfinite(ln)) return ln
        return if (Math.isNaN(ln)) ln else ln / LOG_10
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

    @JvmStatic
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
}