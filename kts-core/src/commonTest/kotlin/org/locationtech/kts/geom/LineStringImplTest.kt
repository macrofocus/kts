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
import org.locationtech.jts.io.WKTReader
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test for com.vividsolutions.jts.geom.impl.LineStringImpl.
 *
 * @version 1.7
 */
class LineStringImplTest {
    @JvmField
    var precisionModel: PrecisionModel = PrecisionModel(1000.0)
    @JvmField
    var geometryFactory = GeometryFactory(precisionModel, 0)
    @JvmField
    var reader = WKTReader(geometryFactory, allowOldJtsCoordinateSyntax = false)

    @Test
    @Throws(Exception::class)
    fun testIsSimple() {
        val l1 = reader.read("LINESTRING (0 0, 10 10, 10 0, 0 10, 0 0)") as LineString?
        assertTrue(!l1!!.isSimple)
        val l2 = reader.read("LINESTRING (0 0, 10 10, 10 0, 0 10)") as LineString?
        assertTrue(!l2!!.isSimple)
    }

    @Test
    @Throws(Exception::class)
    fun testIsCoordinate() {
        val l = reader.read("LINESTRING (0 0, 10 10, 10 0)") as LineString?
        assertTrue(l!!.isCoordinate(Coordinate(0.0, 0.0)))
        assertTrue(!l!!.isCoordinate(Coordinate(5.0, 0.0)))
    }

    @Test
    fun testUnclosedLinearRing() {
        try {
            geometryFactory.createLinearRing(
                arrayOf(
                    Coordinate(0.0, 0.0), Coordinate(1.0, 0.0), Coordinate(1.0, 1.0), Coordinate(2.0, 1.0)
                )
            )
            assertTrue(false)
        } catch (e: Exception) {
            assertTrue(e is IllegalArgumentException)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testEquals1() {
        val l1 = reader.read("LINESTRING(1.111 2.222, 3.333 4.444)") as LineString?
        val l2 = reader.read("LINESTRING(1.111 2.222, 3.333 4.444)") as LineString?
        assertTrue(l1!!.equals(l2))
    }

    @Test
    @Throws(Exception::class)
    fun testEquals2() {
        val l1 = reader.read("LINESTRING(1.111 2.222, 3.333 4.444)") as LineString?
        val l2 = reader.read("LINESTRING(3.333 4.444, 1.111 2.222)") as LineString?
        assertTrue(l1!!.equals(l2))
    }

    @Test
    @Throws(Exception::class)
    fun testEquals3() {
        val l1 = reader.read("LINESTRING(1.111 2.222, 3.333 4.444)") as LineString?
        val l2 = reader.read("LINESTRING(3.333 4.443, 1.111 2.222)") as LineString?
        assertTrue(!l1!!.equals(l2))
    }

    @Test
    @Throws(Exception::class)
    fun testEquals4() {
        val l1 = reader.read("LINESTRING(1.111 2.222, 3.333 4.444)") as LineString?
        val l2 = reader.read("LINESTRING(3.333 4.4445, 1.111 2.222)") as LineString?
        assertTrue(!l1!!.equals(l2))
    }

    @Test
    @Throws(Exception::class)
    fun testEquals5() {
        val l1 = reader.read("LINESTRING(1.111 2.222, 3.333 4.444)") as LineString?
        val l2 = reader.read("LINESTRING(3.333 4.4446, 1.111 2.222)") as LineString?
        assertTrue(!l1!!.equals(l2))
    }

    @Test
    @Throws(Exception::class)
    fun testEquals6() {
        val l1 = reader.read("LINESTRING(1.111 2.222, 3.333 4.444, 5.555 6.666)") as LineString?
        val l2 = reader.read("LINESTRING(1.111 2.222, 3.333 4.444, 5.555 6.666)") as LineString?
        assertTrue(l1!!.equals(l2))
    }

    @Test
    @Throws(Exception::class)
    fun testEquals7() {
        val l1 = reader.read("LINESTRING(1.111 2.222, 5.555 6.666, 3.333 4.444)") as LineString?
        val l2 = reader.read("LINESTRING(1.111 2.222, 3.333 4.444, 5.555 6.666)") as LineString?
        assertTrue(!l1!!.equals(l2))
    }

    @Test
    @Throws(Exception::class)
    fun testGetCoordinates() {
        val l = reader.read("LINESTRING(1.111 2.222, 5.555 6.666, 3.333 4.444)") as LineString?
        val coordinates = l!!.coordinates
        assertEquals(Coordinate(5.555, 6.666), coordinates[1])
    }

    @Test
    @Throws(Exception::class)
    fun testIsClosed() {
        val l = reader.read("LINESTRING EMPTY") as LineString?
        assertTrue(l!!.isEmpty)
        assertTrue(!l!!.isClosed)
        val r = geometryFactory.createLinearRing(null as CoordinateSequence?)
        assertTrue(r.isEmpty)
        assertTrue(r.isClosed)
        val m = geometryFactory.createMultiLineString(arrayOf<LineString>(l, r))
        assertTrue(!m!!.isClosed)
        val m2 = geometryFactory.createMultiLineString(arrayOf(r))
        assertTrue(!m2!!.isClosed)
    }

    @Test
    @Throws(Exception::class)
    fun testGetGeometryType() {
        val l = reader.read("LINESTRING EMPTY") as LineString?
        assertEquals("LineString", l!!.geometryType)
    }

    @Test
    @Throws(Exception::class)
    fun testEquals8() {
        val reader = WKTReader(GeometryFactory(PrecisionModel(1000.0), 0), allowOldJtsCoordinateSyntax = false)
        val l1 =
            reader.read("MULTILINESTRING((1732328800 519578384, 1732026179 519976285, 1731627364 519674014, 1731929984 519276112, 1732328800 519578384))") as MultiLineString?
        val l2 =
            reader.read("MULTILINESTRING((1731627364 519674014, 1731929984 519276112, 1732328800 519578384, 1732026179 519976285, 1731627364 519674014))") as MultiLineString?
        assertTrue(l1!!.equals(l2))
    }

    @Test
    @Throws(Exception::class)
    fun testEquals9() {
        val reader = WKTReader(GeometryFactory(PrecisionModel(1.0), 0), allowOldJtsCoordinateSyntax = false)
        val l1 =
            reader.read("MULTILINESTRING((1732328800 519578384, 1732026179 519976285, 1731627364 519674014, 1731929984 519276112, 1732328800 519578384))") as MultiLineString?
        val l2 =
            reader.read("MULTILINESTRING((1731627364 519674014, 1731929984 519276112, 1732328800 519578384, 1732026179 519976285, 1731627364 519674014))") as MultiLineString?
        assertTrue(l1!!.equals(l2))
    }

    @Test
    @Throws(Exception::class)
    fun testEquals10() {
        val reader = WKTReader(GeometryFactory(PrecisionModel(1.0), 0), allowOldJtsCoordinateSyntax = false)
        val l1 =
            reader.read("POLYGON((1732328800 519578384, 1732026179 519976285, 1731627364 519674014, 1731929984 519276112, 1732328800 519578384))")
        val l2 =
            reader.read("POLYGON((1731627364 519674014, 1731929984 519276112, 1732328800 519578384, 1732026179 519976285, 1731627364 519674014))")
        l1!!.normalize()
        l2!!.normalize()
        assertTrue(l1.equalsExact(l2))
    }

    @Test
    fun testFiveZeros() {
        val ls = GeometryFactory().createLineString(
            arrayOf(
                Coordinate(0.0, 0.0),
                Coordinate(0.0, 0.0),
                Coordinate(0.0, 0.0),
                Coordinate(0.0, 0.0),
                Coordinate(0.0, 0.0)
            )
        )
        assertTrue(ls.isClosed)
    }

    @Test
    @Throws(Exception::class)
    fun testLinearRingConstructor() {
        try {
            val ring = GeometryFactory().createLinearRing(
                arrayOf(
                    Coordinate(0.0, 0.0),
                    Coordinate(10.0, 10.0),
                    Coordinate(0.0, 0.0)
                )
            )
            assertTrue(false)
        } catch (e: IllegalArgumentException) {
            assertTrue(true)
        }
    }
}