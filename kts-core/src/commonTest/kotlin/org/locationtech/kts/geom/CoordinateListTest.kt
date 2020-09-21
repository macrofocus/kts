package org.locationtech.kts.geom

import org.locationtech.jts.geom.*
import org.locationtech.kts.geom.CoordinateListTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CoordinateListTest {
    @Test
    fun testForward() {
        checkValue(coordList(0.0, 0.0, 1.0, 1.0, 2.0, 2.0).toCoordinateArray(true), 0.0, 0.0, 1.0, 1.0, 2.0, 2.0)
    }

    @Test
    fun testReverse() {
        checkValue(coordList(0.0, 0.0, 1.0, 1.0, 2.0, 2.0).toCoordinateArray(false), 2.0, 2.0, 1.0, 1.0, 0.0, 0.0)
    }

    @Test
    fun testReverseEmpty() {
        checkValue(coordList().toCoordinateArray(false))
    }

    private fun checkValue(coordArray: Array<Coordinate>, vararg ords: Double) {
        assertEquals(coordArray.size * 2, ords.size)
        var i = 0
        while (i < coordArray.size) {
            val pt = coordArray[i]
            assertEquals(pt.x, ords[2 * i])
            assertEquals(pt.y, ords[2 * i + 1])
            i += 2
        }
    }

    private fun coordList(vararg ords: Double): CoordinateList {
        val cl = CoordinateList()
        var i = 0
        while (i < ords.size) {
            cl.add(Coordinate(ords[i], ords[i + 1]), false)
            i += 2
        }
        return cl
    }
}