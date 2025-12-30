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

expect object Math {
    fun min(a: Int, b: Int): Int

    fun min(a: Double, b: Double): Double

    fun max(a: Int, b: Int): Int

    fun max(a: Double, b: Double): Double

    fun abs(value: Int): Int

    fun abs(value: Double): Double

    fun compare(v1: Int, v2: Int): Int

    fun compare(v1: Double, v2: Double): Int

    fun round(value: Double): Long

    fun log(value: Double): Double

    fun floor(value: Double): Double

    fun ceil(value: Double): Double

    fun isNaN(value: Double): Boolean

    fun isFinite(value: Double): Boolean

    fun isInfinite(value: Double): Boolean

    fun doubleToLongBits(value: Double): Long

    fun longBitsToDouble(value: Long): Double

    fun toBinaryString(value: Long): String

    fun pow(b: Double, x: Double): Double

    fun sqrt(x: Double): Double

    fun atan2(x: Double, y: Double): Double

    fun sin(x: Double): Double

    fun cos(x: Double): Double

    fun signum(x: Double): Double
}






