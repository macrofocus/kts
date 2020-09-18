package org.locationtech.jts.algorithm

import org.locationtech.jts.geom.Coordinate
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * @version 1.7
 */
class AngleTest {
    private val TOLERANCE = 1E-5

    @Throws(Exception::class)
    @Test
    fun testAngle() {
        org.locationtech.jts.assertEquals(Angle.angle(Coordinate(10.0, 0.0)), 0.0, TOLERANCE)
        org.locationtech.jts.assertEquals(Angle.angle(Coordinate(10.0, 10.0)), PI / 4, TOLERANCE)
        org.locationtech.jts.assertEquals(Angle.angle(Coordinate(0.0, 10.0)), PI / 2, TOLERANCE)
        org.locationtech.jts.assertEquals(Angle.angle(Coordinate(-10.0, 10.0)), 0.75 * PI, TOLERANCE)
        org.locationtech.jts.assertEquals(Angle.angle(Coordinate(-10.0, 0.0)), PI, TOLERANCE)
        org.locationtech.jts.assertEquals(Angle.angle(Coordinate(-10.0, -0.1)), -3.131592986903128, TOLERANCE)
        org.locationtech.jts.assertEquals(Angle.angle(Coordinate(-10.0, -10.0)), -0.75 * PI, TOLERANCE)
    }

    @Throws(Exception::class)
    @Test
    fun testIsAcute() {
        assertEquals(Angle.isAcute(Coordinate(10.0, 0.0), Coordinate(0.0, 0.0), Coordinate(5.0, 10.0)), true)
        assertEquals(Angle.isAcute(Coordinate(10.0, 0.0), Coordinate(0.0, 0.0), Coordinate(5.0, -10.0)), true)
        // angle of 0
        assertEquals(Angle.isAcute(Coordinate(10.0, 0.0), Coordinate(0.0, 0.0), Coordinate(10.0, 0.0)), true)
        assertEquals(Angle.isAcute(Coordinate(10.0, 0.0), Coordinate(0.0, 0.0), Coordinate(-5.0, 10.0)), false)
        assertEquals(Angle.isAcute(Coordinate(10.0, 0.0), Coordinate(0.0, 0.0), Coordinate(-5.0, -10.0)), false)
    }

    @Throws(Exception::class)
    @Test
    fun testNormalizePositive() {
        org.locationtech.jts.assertEquals(Angle.normalizePositive(0.0), 0.0, TOLERANCE)
        org.locationtech.jts.assertEquals(Angle.normalizePositive(-0.5 * PI), 1.5 * PI, TOLERANCE)
        org.locationtech.jts.assertEquals(Angle.normalizePositive(-PI), PI, TOLERANCE)
        org.locationtech.jts.assertEquals(Angle.normalizePositive(-1.5 * PI), .5 * PI, TOLERANCE)
        org.locationtech.jts.assertEquals(Angle.normalizePositive(-2 * PI), 0.0, TOLERANCE)
        org.locationtech.jts.assertEquals(Angle.normalizePositive(-2.5 * PI), 1.5 * PI, TOLERANCE)
        org.locationtech.jts.assertEquals(Angle.normalizePositive(-3 * PI), PI, TOLERANCE)
        org.locationtech.jts.assertEquals(Angle.normalizePositive(-4 * PI), 0.0, TOLERANCE)
        org.locationtech.jts.assertEquals(Angle.normalizePositive(0.5 * PI), 0.5 * PI, TOLERANCE)
        org.locationtech.jts.assertEquals(Angle.normalizePositive(PI), PI, TOLERANCE)
        org.locationtech.jts.assertEquals(Angle.normalizePositive(1.5 * PI), 1.5 * PI, TOLERANCE)
        org.locationtech.jts.assertEquals(Angle.normalizePositive(2 * PI), 0.0, TOLERANCE)
        org.locationtech.jts.assertEquals(Angle.normalizePositive(2.5 * PI), 0.5 * PI, TOLERANCE)
        org.locationtech.jts.assertEquals(Angle.normalizePositive(3 * PI), PI, TOLERANCE)
        org.locationtech.jts.assertEquals(Angle.normalizePositive(4 * PI), 0.0, TOLERANCE)
    }

    @Throws(Exception::class)
    @Test
    fun testNormalize() {
        org.locationtech.jts.assertEquals(Angle.normalize(0.0), 0.0, TOLERANCE)
        org.locationtech.jts.assertEquals(Angle.normalize(-0.5 * PI), -0.5 * PI, TOLERANCE)
        org.locationtech.jts.assertEquals(Angle.normalize(-PI), PI, TOLERANCE)
        org.locationtech.jts.assertEquals(Angle.normalize(-1.5 * PI), .5 * PI, TOLERANCE)
        org.locationtech.jts.assertEquals(Angle.normalize(-2 * PI), 0.0, TOLERANCE)
        org.locationtech.jts.assertEquals(Angle.normalize(-2.5 * PI), -0.5 * PI, TOLERANCE)
        org.locationtech.jts.assertEquals(Angle.normalize(-3 * PI), PI, TOLERANCE)
        org.locationtech.jts.assertEquals(Angle.normalize(-4 * PI), 0.0, TOLERANCE)
        org.locationtech.jts.assertEquals(Angle.normalize(0.5 * PI), 0.5 * PI, TOLERANCE)
        org.locationtech.jts.assertEquals(Angle.normalize(PI), PI, TOLERANCE)
        org.locationtech.jts.assertEquals(Angle.normalize(1.5 * PI), -0.5 * PI, TOLERANCE)
        org.locationtech.jts.assertEquals(Angle.normalize(2 * PI), 0.0, TOLERANCE)
        org.locationtech.jts.assertEquals(Angle.normalize(2.5 * PI), 0.5 * PI, TOLERANCE)
        org.locationtech.jts.assertEquals(Angle.normalize(3 * PI), PI, TOLERANCE)
        org.locationtech.jts.assertEquals(Angle.normalize(4 * PI), 0.0, TOLERANCE)
    }
}