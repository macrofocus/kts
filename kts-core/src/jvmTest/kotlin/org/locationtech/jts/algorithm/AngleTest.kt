/*
 * Copyright (c) 2016 Vivid Solutions.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.algorithm

import junit.framework.TestCase
import junit.textui.TestRunner
import org.junit.Test
import org.locationtech.jts.algorithm.Angle.angle
import org.locationtech.jts.algorithm.Angle.isAcute
import org.locationtech.jts.algorithm.Angle.normalize
import org.locationtech.jts.algorithm.Angle.normalizePositive
import org.locationtech.jts.geom.Coordinate

/**
 * @version 1.7
 */
class AngleTest(name: String?) : TestCase(name) {
    @Throws(Exception::class)
    @Test
    fun testAngle() {
        assertEquals(angle(Coordinate(10.0, 0.0)), 0.0, TOLERANCE)
        assertEquals(angle(Coordinate(10.0, 10.0)), Math.PI / 4, TOLERANCE)
        assertEquals(angle(Coordinate(0.0, 10.0)), Math.PI / 2, TOLERANCE)
        assertEquals(angle(Coordinate(-10.0, 10.0)), 0.75 * Math.PI, TOLERANCE)
        assertEquals(angle(Coordinate(-10.0, 0.0)), Math.PI, TOLERANCE)
        assertEquals(angle(Coordinate(-10.0, -0.1)), -3.131592986903128, TOLERANCE)
        assertEquals(angle(Coordinate(-10.0, -10.0)), -0.75 * Math.PI, TOLERANCE)
    }

    @Throws(Exception::class)
    @Test
    fun testIsAcute() {
        TestCase.assertEquals(isAcute(Coordinate(10.0, 0.0), Coordinate(0.0, 0.0), Coordinate(5.0, 10.0)), true)
        TestCase.assertEquals(isAcute(Coordinate(10.0, 0.0), Coordinate(0.0, 0.0), Coordinate(5.0, -10.0)), true)
        // angle of 0
        TestCase.assertEquals(isAcute(Coordinate(10.0, 0.0), Coordinate(0.0, 0.0), Coordinate(10.0, 0.0)), true)
        TestCase.assertEquals(isAcute(Coordinate(10.0, 0.0), Coordinate(0.0, 0.0), Coordinate(-5.0, 10.0)), false)
        TestCase.assertEquals(isAcute(Coordinate(10.0, 0.0), Coordinate(0.0, 0.0), Coordinate(-5.0, -10.0)), false)
    }

    @Throws(Exception::class)
    @Test
    fun testNormalizePositive() {
        TestCase.assertEquals(normalizePositive(0.0), 0.0, TOLERANCE)
        TestCase.assertEquals(normalizePositive(-0.5 * Math.PI), 1.5 * Math.PI, TOLERANCE)
        TestCase.assertEquals(normalizePositive(-Math.PI), Math.PI, TOLERANCE)
        TestCase.assertEquals(normalizePositive(-1.5 * Math.PI), .5 * Math.PI, TOLERANCE)
        TestCase.assertEquals(normalizePositive(-2 * Math.PI), 0.0, TOLERANCE)
        TestCase.assertEquals(normalizePositive(-2.5 * Math.PI), 1.5 * Math.PI, TOLERANCE)
        TestCase.assertEquals(normalizePositive(-3 * Math.PI), Math.PI, TOLERANCE)
        TestCase.assertEquals(normalizePositive(-4 * Math.PI), 0.0, TOLERANCE)
        TestCase.assertEquals(normalizePositive(0.5 * Math.PI), 0.5 * Math.PI, TOLERANCE)
        TestCase.assertEquals(normalizePositive(Math.PI), Math.PI, TOLERANCE)
        TestCase.assertEquals(normalizePositive(1.5 * Math.PI), 1.5 * Math.PI, TOLERANCE)
        TestCase.assertEquals(normalizePositive(2 * Math.PI), 0.0, TOLERANCE)
        TestCase.assertEquals(normalizePositive(2.5 * Math.PI), 0.5 * Math.PI, TOLERANCE)
        TestCase.assertEquals(normalizePositive(3 * Math.PI), Math.PI, TOLERANCE)
        TestCase.assertEquals(normalizePositive(4 * Math.PI), 0.0, TOLERANCE)
    }

    @Throws(Exception::class)
    @Test
    fun testNormalize() {
        TestCase.assertEquals(normalize(0.0), 0.0, TOLERANCE)
        TestCase.assertEquals(normalize(-0.5 * Math.PI), -0.5 * Math.PI, TOLERANCE)
        TestCase.assertEquals(normalize(-Math.PI), Math.PI, TOLERANCE)
        TestCase.assertEquals(normalize(-1.5 * Math.PI), .5 * Math.PI, TOLERANCE)
        TestCase.assertEquals(normalize(-2 * Math.PI), 0.0, TOLERANCE)
        TestCase.assertEquals(normalize(-2.5 * Math.PI), -0.5 * Math.PI, TOLERANCE)
        TestCase.assertEquals(normalize(-3 * Math.PI), Math.PI, TOLERANCE)
        TestCase.assertEquals(normalize(-4 * Math.PI), 0.0, TOLERANCE)
        TestCase.assertEquals(normalize(0.5 * Math.PI), 0.5 * Math.PI, TOLERANCE)
        TestCase.assertEquals(normalize(Math.PI), Math.PI, TOLERANCE)
        TestCase.assertEquals(normalize(1.5 * Math.PI), -0.5 * Math.PI, TOLERANCE)
        TestCase.assertEquals(normalize(2 * Math.PI), 0.0, TOLERANCE)
        TestCase.assertEquals(normalize(2.5 * Math.PI), 0.5 * Math.PI, TOLERANCE)
        TestCase.assertEquals(normalize(3 * Math.PI), Math.PI, TOLERANCE)
        TestCase.assertEquals(normalize(4 * Math.PI), 0.0, TOLERANCE)
    }

    companion object {
        private const val TOLERANCE = 1E-5
        @JvmStatic
        fun main(args: Array<String>) {
            TestRunner.run(AngleTest::class.java)
        }
    }
}