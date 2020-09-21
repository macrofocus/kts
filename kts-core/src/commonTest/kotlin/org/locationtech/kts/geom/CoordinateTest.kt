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
package org.locationtech.kts.geom

import org.locationtech.jts.geom.*
import org.locationtech.kts.geom.CoordinateListTest
import org.locationtech.kts.geom.CoordinateSequencesTest
import org.locationtech.kts.geom.CoordinateTest
import org.locationtech.jts.legacy.Math
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class CoordinateTest {
    @Test
    fun testConstructor3D() {
        val c = Coordinate(350.2, 4566.8, 5266.3)
        assertEquals(c.x, 350.2)
        assertEquals(c.y, 4566.8)
        assertEquals(c.z, 5266.3)
    }

    @Test
    fun testConstructor2D() {
        val c = Coordinate(350.2, 4566.8)
        assertEquals(c.x, 350.2)
        assertEquals(c.y, 4566.8)
        assertEquals(c.z, Coordinate.NULL_ORDINATE)
    }

    @Test
    fun testDefaultConstructor() {
        val c = Coordinate()
        assertEquals(c.x, 0.0)
        assertEquals(c.y, 0.0)
        assertEquals(c.z, Coordinate.NULL_ORDINATE)
    }

    @Test
    fun testCopyConstructor3D() {
        val orig = Coordinate(350.2, 4566.8, 5266.3)
        val c = Coordinate(orig)
        assertEquals(c.x, 350.2)
        assertEquals(c.y, 4566.8)
        assertEquals(c.z, 5266.3)
    }

    @Test
    fun testSetCoordinate() {
        val orig = Coordinate(350.2, 4566.8, 5266.3)
        val c = Coordinate()
        c.setCoordinate(orig)
        assertEquals(c.x, 350.2)
        assertEquals(c.y, 4566.8)
        assertEquals(c.z, 5266.3)
    }

    @Test
    fun testGetOrdinate() {
        val c = Coordinate(350.2, 4566.8, 5266.3)
        assertEquals(c.getOrdinate(Coordinate.X), 350.2)
        assertEquals(c.getOrdinate(Coordinate.Y), 4566.8)
        assertEquals(c.getOrdinate(Coordinate.Z), 5266.3)
    }

    @Test
    fun testSetOrdinate() {
        val c = Coordinate()
        c.setOrdinate(Coordinate.X, 111.0)
        c.setOrdinate(Coordinate.Y, 222.0)
        c.setOrdinate(Coordinate.Z, 333.0)
        assertEquals(c.getOrdinate(Coordinate.X), 111.0)
        assertEquals(c.getOrdinate(Coordinate.Y), 222.0)
        assertEquals(c.getOrdinate(Coordinate.Z), 333.0)
    }

    @Test
    fun testEquals() {
        val c1 = Coordinate(1.0, 2.0, 3.0)
        val s = "Not a coordinate"
        assertTrue(!c1.equals(s))
        val c2 = Coordinate(1.0, 2.0, 3.0)
        assertTrue(c1.equals2D(c2))
        val c3 = Coordinate(1.0, 22.0, 3.0)
        assertTrue(!c1.equals2D(c3))
    }

    @Test
    fun testEquals2D() {
        val c1 = Coordinate(1.0, 2.0, 3.0)
        val c2 = Coordinate(1.0, 2.0, 3.0)
        assertTrue(c1.equals2D(c2))
        val c3 = Coordinate(1.0, 22.0, 3.0)
        assertTrue(!c1.equals2D(c3))
    }

    @Test
    fun testEquals3D() {
        val c1 = Coordinate(1.0, 2.0, 3.0)
        val c2 = Coordinate(1.0, 2.0, 3.0)
        assertTrue(c1.equals3D(c2))
        val c3 = Coordinate(1.0, 22.0, 3.0)
        assertTrue(!c1.equals3D(c3))
    }

    @Test
    fun testEquals2DWithinTolerance() {
        val c = Coordinate(100.0, 200.0, 50.0)
        val aBitOff = Coordinate(100.1, 200.1, 50.0)
        assertTrue(c.equals2D(aBitOff, 0.2))
    }

    @Test
    fun testEqualsInZ() {
        val c = Coordinate(100.0, 200.0, 50.0)
        val withSameZ = Coordinate(100.1, 200.1, 50.1)
        assertTrue(c.equalInZ(withSameZ, 0.2))
    }

    @Test
    fun testCompareTo() {
        val lowest = Coordinate(10.0, 100.0, 50.0)
        val highest = Coordinate(20.0, 100.0, 50.0)
        val equalToHighest = Coordinate(20.0, 100.0, 50.0)
        val higherStill = Coordinate(20.0, 200.0, 50.0)
        assertEquals(-1, lowest.compareTo(highest))
        assertEquals(1, highest.compareTo(lowest))
        assertEquals(-1, highest.compareTo(higherStill))
        assertEquals(0, highest.compareTo(equalToHighest))
    }

    @Test
    fun testToString() {
        val expectedResult = "(100.0, 200.0, 50.0)"
        val actualResult = Coordinate(100.0, 200.0, 50.0).toString()
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun testClone() {
        val c = Coordinate(100.0, 200.0, 50.0)
        val clone = c.clone() as Coordinate
        assertTrue(c.equals3D(clone))
    }

    @Test
    fun testDistance() {
        val coord1 = Coordinate(0.0, 0.0, 0.0)
        val coord2 = Coordinate(100.0, 200.0, 50.0)
        val distance = coord1.distance(coord2)
        org.locationtech.kts.assertEquals(distance, 223.60679774997897, 0.00001)
    }

    @Test
    fun testDistance3D() {
        val coord1 = Coordinate(0.0, 0.0, 0.0)
        val coord2 = Coordinate(100.0, 200.0, 50.0)
        val distance = coord1.distance3D(coord2)
        org.locationtech.kts.assertEquals(distance, 229.128784747792, 0.000001)
    }

    @Test
    fun testCoordinateXY() {
        var xy: Coordinate = CoordinateXY()
        checkZUnsupported(xy)
        checkMUnsupported(xy)
        xy = CoordinateXY(1.0, 1.0) // 2D
        var coord = Coordinate(xy) // copy
        assertEquals(xy, coord)
        assertTrue(!xy.equalInZ(coord, 0.000001))
        coord = Coordinate(1.0, 1.0, 1.0) // 2.5d
        xy = CoordinateXY(coord) // copy
        assertEquals(xy, coord)
        assertTrue(!xy.equalInZ(coord, 0.000001))
    }

    @Test
    fun testCoordinateXYM() {
        var xym: Coordinate = CoordinateXYM()
        checkZUnsupported(xym)
        xym.m = 1.0
        assertEquals(1.0, xym.m)
        var coord = Coordinate(xym) // copy
        assertEquals(xym, coord)
        assertTrue(!xym.equalInZ(coord, 0.000001))
        coord = Coordinate(1.0, 1.0, 1.0) // 2.5d
        xym = CoordinateXYM(coord) // copy
        assertEquals(xym, coord)
        assertTrue(!xym.equalInZ(coord, 0.000001))
    }

    @Test
    fun testCoordinateXYZM() {
        var xyzm: Coordinate = CoordinateXYZM()
        xyzm.z = 1.0
        assertEquals(1.0, xyzm.z)
        xyzm.m = 1.0
        assertEquals(1.0, xyzm.m)
        var coord = Coordinate(xyzm) // copy
        assertEquals(xyzm, coord)
        assertTrue(xyzm.equalInZ(coord, 0.000001))
        assertTrue(Math.isNaN(coord.m))
        coord = Coordinate(1.0, 1.0, 1.0) // 2.5d
        xyzm = CoordinateXYZM(coord) // copy
        assertEquals(xyzm, coord)
        assertTrue(xyzm.equalInZ(coord, 0.000001))
    }

    @Test
    fun testCoordinateHash() {
        doTestCoordinateHash(true, Coordinate(1.0, 2.0), Coordinate(1.0, 2.0))
        doTestCoordinateHash(false, Coordinate(1.0, 2.0), Coordinate(3.0, 4.0))
        doTestCoordinateHash(false, Coordinate(1.0, 2.0), Coordinate(1.0, 4.0))
        doTestCoordinateHash(false, Coordinate(1.0, 2.0), Coordinate(3.0, 2.0))
        doTestCoordinateHash(false, Coordinate(1.0, 2.0), Coordinate(2.0, 1.0))
    }

    private fun doTestCoordinateHash(equal: Boolean, a: Coordinate, b: Coordinate) {
        assertEquals(equal, a.equals(b))
        assertEquals(equal, a.hashCode() == b.hashCode())
    }

    /**
     * Confirm the z field is not supported by getZ and setZ.
     */
    private fun checkZUnsupported(coord: Coordinate) {
        try {
            coord.z = 0.0
            fail(coord::class.simpleName + " does not support Z")
        } catch (expected: IllegalArgumentException) {
        }
        assertTrue(Math.isNaN(coord.z))
        // ToDo: No longer possible to access the z field directly in Kotlin
//      coord.z = 0.0;                      // field still public
        assertTrue(Math.isNaN(coord.z), "z field not used") // but not used
    }

    /**
     * Confirm the z field is not supported by getZ and setZ.
     */
    private fun checkMUnsupported(coord: Coordinate) {
        try {
            coord.m = 0.0
            fail(coord::class.simpleName + " does not support M")
        } catch (expected: IllegalArgumentException) {
        }
        assertTrue(Math.isNaN(coord.m))
    }
}