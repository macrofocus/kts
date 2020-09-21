package org.locationtech.kts.geom

import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.locationtech.kts.assertEquals
import org.locationtech.jts.io.WKTReader
import kotlin.test.Test

/**
 * @version 1.7
 */
class AreaLengthTest {
    private val TOLERANCE = 1E-5

    private val precisionModel = PrecisionModel()
    private val geometryFactory = GeometryFactory(precisionModel, 0)
    var reader = WKTReader(geometryFactory, allowOldJtsCoordinateSyntax = false)
    @Throws(Exception::class)
    @Test
    fun testLength() {
        checkLength("MULTIPOINT (220 140, 180 280)", 0.0)
        checkLength("LINESTRING (220 140, 180 280)", 145.6021977)
        checkLength("LINESTRING (0 0, 100 100)", 141.4213562373095)
        checkLength("POLYGON ((20 20, 40 20, 40 40, 20 40, 20 20))", 80.0)
        checkLength("POLYGON ((20 20, 40 20, 40 40, 20 40, 20 20), (25 35, 35 35, 35 25, 25 25, 25 35))", 120.0)
    }

    @Throws(Exception::class)
    @Test
    fun testArea() {
        checkArea("MULTIPOINT (220 140, 180 280)", 0.0)
        checkArea("LINESTRING (220 140, 180 280)", 0.0)
        checkArea("POLYGON ((20 20, 40 20, 40 40, 20 40, 20 20))", 400.0)
        checkArea("POLYGON ((20 20, 40 20, 40 40, 20 40, 20 20), (25 35, 35 35, 35 25, 25 25, 25 35))", 300.0)
    }

    @Throws(Exception::class)
    fun checkLength(wkt: String, expectedValue: Double) {
        val g = reader.read(wkt)
        val len = g!!.length
        assertEquals(expectedValue, len, TOLERANCE)
    }

    @Throws(Exception::class)
    fun checkArea(wkt: String, expectedValue: Double) {
        val g = reader.read(wkt)
        assertEquals(expectedValue, g!!.area, TOLERANCE)
    }
}