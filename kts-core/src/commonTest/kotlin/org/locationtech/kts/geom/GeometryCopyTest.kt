package org.locationtech.kts.geom

import org.locationtech.jts.geom.Geometry
import test.kts.GeometryTestCase
import test.kts.GeometryTestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeometryCopyTest : GeometryTestCase() {
    @Test
    fun testCopy() {
        checkCopy(read(GeometryTestData.WKT_POINT))
        checkCopy(read(GeometryTestData.WKT_LINESTRING))
        checkCopy(read(GeometryTestData.WKT_LINEARRING))
        checkCopy(read(GeometryTestData.WKT_POLY))
        checkCopy(read(GeometryTestData.WKT_MULTIPOINT))
        checkCopy(read(GeometryTestData.WKT_MULTILINESTRING))
        checkCopy(read(GeometryTestData.WKT_MULTIPOLYGON))
        checkCopy(read(GeometryTestData.WKT_GC))
    }

    private fun checkCopy(g: Geometry) {
        val SRID = 123
        g.SRID = SRID
        val DATA: Any = 999
        g.setUserData(DATA)
        val copy = g.copy()
        assertEquals(g.SRID, copy.SRID)
        assertEquals(g.getUserData(), copy.getUserData())

        //TODO: use a test which checks all ordinates of CoordinateSequences
        assertTrue(g.equalsExact(copy))
    }
}