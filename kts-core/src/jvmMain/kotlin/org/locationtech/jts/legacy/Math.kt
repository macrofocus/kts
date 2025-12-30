/*
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.legacy

import kotlin.math.pow

actual object Math {
    actual fun min(a: Int, b: Int): Int {
        return kotlin.math.min(a, b)
    }

    actual fun min(a: Double, b: Double): Double {
        return kotlin.math.min(a, b)
    }

    actual fun max(a: Int, b: Int): Int {
        return kotlin.math.max(a, b)
    }

    actual fun max(a: Double, b: Double): Double {
        return kotlin.math.max(a, b)
    }

    actual fun abs(value: Int): Int {
        return kotlin.math.abs(value)
    }

    actual fun abs(value: Double): Double {
        return kotlin.math.abs(value)
    }

    actual fun compare(v1: Int, v2: Int): Int {
        return v1.compareTo(v2)
    }

    actual fun compare(v1: Double, v2: Double): Int {
        return v1.compareTo(v2)
    }

    actual fun round(value: Double): Long {
        return java.lang.Math.round(value)
    }

    actual fun log(value: Double): Double {
        return java.lang.Math.log(value)
    }

    actual fun floor(value: Double): Double {
        return kotlin.math.floor(value)
    }

    actual fun ceil(value: Double): Double {
        return kotlin.math.ceil(value)
    }

    actual fun isNaN(value: Double): Boolean {
        return value.isNaN()
    }

    actual fun isFinite(value: Double): Boolean {
        return value.isFinite()
    }

    actual fun isInfinite(value: Double): Boolean {
        return value.isInfinite()
    }

    actual fun doubleToLongBits(value: Double): Long {
        return value.toRawBits()
    }

    actual fun longBitsToDouble(value: Long): Double {
        return Double.Companion.fromBits(value)
    }

    actual fun toBinaryString(value: Long): String {
        return value.toULong().toString(radix = 2)
    }

    actual fun pow(b: Double, x: Double): Double {
        return b.pow(x)
    }


    actual fun sqrt(x: Double): Double {
        return java.lang.Math.sqrt(x)
    }

    actual fun atan2(x: Double, y: Double): Double {
        return java.lang.Math.atan2(x, y)
    }

    actual fun sin(x: Double): Double {
        return java.lang.Math.sin(x)
    }

    actual fun cos(x: Double): Double {
        return java.lang.Math.cos(x)
    }

    actual fun signum(x: Double): Double {
        return java.lang.Math.signum(x)
    }
}






