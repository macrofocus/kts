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
import org.locationtech.kts.assertEquals
import org.locationtech.kts.geom.CoordinateListTest
import org.locationtech.kts.geom.CoordinateSequencesTest
import org.locationtech.kts.geom.CoordinateTest
import org.locationtech.jts.geom.Envelope.Companion.intersects
import org.locationtech.kts.geom.EnvelopeTest
import org.locationtech.jts.io.ParseException
import org.locationtech.jts.io.WKTReader
import kotlin.jvm.JvmField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * @version 1.7
 */
class EnvelopeTest {
    private val precisionModel: PrecisionModel = PrecisionModel(1.0)
    private val geometryFactory = GeometryFactory(
        precisionModel,
        0
    )
    @JvmField
	var reader = WKTReader(geometryFactory, allowOldJtsCoordinateSyntax = false)

    @Test
    @Throws(Exception::class)
    fun testEverything() {
        val e1 = Envelope()
        assertTrue(e1.isNull)
        assertEquals(0.0, e1.width, 1E-3)
        assertEquals(0.0, e1.height, 1E-3)
        e1.expandToInclude(100.0, 101.0)
        e1.expandToInclude(200.0, 202.0)
        e1.expandToInclude(150.0, 151.0)
        assertEquals(200.0, e1.maxX, 1E-3)
        assertEquals(202.0, e1.maxY, 1E-3)
        assertEquals(100.0, e1.minX, 1E-3)
        assertEquals(101.0, e1.minY, 1E-3)
        assertTrue(e1.contains(120.0, 120.0))
        assertTrue(e1.contains(120.0, 101.0))
        assertTrue(!e1.contains(120.0, 100.0))
        assertEquals(101.0, e1.height, 1E-3)
        assertEquals(100.0, e1.width, 1E-3)
        assertTrue(!e1.isNull)
        val e2 = Envelope(499.0, 500.0, 500.0, 501.0)
        assertTrue(!e1.contains(e2))
        assertTrue(!e1.intersects(e2))
        e1.expandToInclude(e2)
        assertTrue(e1.contains(e2))
        assertTrue(e1.intersects(e2))
        assertEquals(500.0, e1.maxX, 1E-3)
        assertEquals(501.0, e1.maxY, 1E-3)
        assertEquals(100.0, e1.minX, 1E-3)
        assertEquals(101.0, e1.minY, 1E-3)
        val e3 = Envelope(300.0, 700.0, 300.0, 700.0)
        assertTrue(!e1.contains(e3))
        assertTrue(e1.intersects(e3))
        val e4 = Envelope(300.0, 301.0, 300.0, 301.0)
        assertTrue(e1.contains(e4))
        assertTrue(e1.intersects(e4))
    }

    @Test
    fun testIntersects() {
        checkIntersectsPermuted(1.0, 1.0, 2.0, 2.0, 2.0, 2.0, 3.0, 3.0, true)
        checkIntersectsPermuted(1.0, 1.0, 2.0, 2.0, 3.0, 3.0, 4.0, 4.0, false)
    }

    @Test
    fun testIntersectsEmpty() {
        assertTrue(!Envelope(-5.0, 5.0, -5.0, 5.0).intersects(Envelope()))
        assertTrue(!Envelope().intersects(Envelope(-5.0, 5.0, -5.0, 5.0)))
        assertTrue(!Envelope().intersects(Envelope(100.0, 101.0, 100.0, 101.0)))
        assertTrue(!Envelope(100.0, 101.0, 100.0, 101.0).intersects(Envelope()))
    }

    @Test
    fun testDisjointEmpty() {
        assertTrue(Envelope(-5.0, 5.0, -5.0, 5.0).disjoint(Envelope()))
        assertTrue(Envelope().disjoint(Envelope(-5.0, 5.0, -5.0, 5.0)))
        assertTrue(Envelope().disjoint(Envelope(100.0, 101.0, 100.0, 101.0)))
        assertTrue(Envelope(100.0, 101.0, 100.0, 101.0).disjoint(Envelope()))
    }

    @Test
    fun testContainsEmpty() {
        assertTrue(!Envelope(-5.0, 5.0, -5.0, 5.0).contains(Envelope()))
        assertTrue(!Envelope().contains(Envelope(-5.0, 5.0, -5.0, 5.0)))
        assertTrue(!Envelope().contains(Envelope(100.0, 101.0, 100.0, 101.0)))
        assertTrue(!Envelope(100.0, 101.0, 100.0, 101.0).contains(Envelope()))
    }

    @Test
    fun testExpandToIncludeEmpty() {
        assertEquals(
            Envelope(-5.0, 5.0, -5.0, 5.0), expandToInclude(
                Envelope(
                    -5.0,
                    5.0, -5.0, 5.0
                ), Envelope()
            )
        )
        assertEquals(
            Envelope(-5.0, 5.0, -5.0, 5.0), expandToInclude(
                Envelope(),
                Envelope(-5.0, 5.0, -5.0, 5.0)
            )
        )
        assertEquals(
            Envelope(100.0, 101.0, 100.0, 101.0), expandToInclude(
                Envelope(), Envelope(100.0, 101.0, 100.0, 101.0)
            )
        )
        assertEquals(
            Envelope(100.0, 101.0, 100.0, 101.0), expandToInclude(
                Envelope(100.0, 101.0, 100.0, 101.0), Envelope()
            )
        )
    }

    private fun expandToInclude(a: Envelope, b: Envelope): Envelope {
        a.expandToInclude(b)
        return a
    }

    @Test
    fun testEmpty() {
        assertEquals(0.0, Envelope().height, 0.0)
        assertEquals(0.0, Envelope().width, 0.0)
        assertEquals(Envelope(), Envelope())
        val e = Envelope(100.0, 101.0, 100.0, 101.0)
        e.init(Envelope())
        assertEquals(Envelope(), e)
    }

    @Test
    @Throws(Exception::class)
    fun testAsGeometry() {
        assertTrue(
            geometryFactory.createPoint(null as Coordinate?).getEnvelope()
                .isEmpty
        )
        val g = geometryFactory.createPoint(Coordinate(5.0, 6.0))
            .getEnvelope()
        assertTrue(!g.isEmpty)
        assertTrue(g is Point)
        val p = g as Point
        assertEquals(5.0, p.x, 1E-1)
        assertEquals(6.0, p.y, 1E-1)
        val l = reader.read("LINESTRING(10 10, 20 20, 30 40)") as LineString?
        val g2 = l!!.getEnvelope()
        assertTrue(!g2.isEmpty)
        assertTrue(g2 is Polygon)
        val poly = g2 as Polygon
        poly.normalize()
        assertEquals(5, poly.exteriorRing!!.numPoints)
        assertEquals(
            Coordinate(10.0, 10.0), poly.exteriorRing!!.getCoordinateN(
                0
            )
        )
        assertEquals(
            Coordinate(10.0, 40.0), poly.exteriorRing!!.getCoordinateN(
                1
            )
        )
        assertEquals(
            Coordinate(30.0, 40.0), poly.exteriorRing!!.getCoordinateN(
                2
            )
        )
        assertEquals(
            Coordinate(30.0, 10.0), poly.exteriorRing!!.getCoordinateN(
                3
            )
        )
        assertEquals(
            Coordinate(10.0, 10.0), poly.exteriorRing!!.getCoordinateN(
                4
            )
        )
    }

    @Test
    @Throws(Exception::class)
    fun testSetToNull() {
        val e1 = Envelope()
        assertTrue(e1.isNull)
        e1.expandToInclude(5.0, 5.0)
        assertTrue(!e1.isNull)
        e1.setToNull()
        assertTrue(e1.isNull)
    }

    @Test
    @Throws(Exception::class)
    fun testEquals() {
        val e1 = Envelope(1.0, 2.0, 3.0, 4.0)
        val e2 = Envelope(1.0, 2.0, 3.0, 4.0)
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
        val e3 = Envelope(1.0, 2.0, 3.0, 5.0)
        assertTrue(!e1.equals(e3))
        assertTrue(e1.hashCode() != e3.hashCode())
        e1.setToNull()
        assertTrue(!e1.equals(e2))
        assertTrue(e1.hashCode() != e2.hashCode())
        e2.setToNull()
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    @Test
    fun testEquals2() {
        assertTrue(Envelope().equals(Envelope()))
        assertTrue(Envelope(1.0, 2.0, 1.0, 2.0).equals(Envelope(1.0, 2.0, 1.0, 2.0)))
        assertTrue(!Envelope(1.0, 2.0, 1.5, 2.0).equals(Envelope(1.0, 2.0, 1.0, 2.0)))
    }

    @Test
    @Throws(Exception::class)
    fun testCopyConstructor() {
        val e1 = Envelope(1.0, 2.0, 3.0, 4.0)
        val e2 = Envelope(e1)
        assertEquals(1.0, e2.minX, 1E-5)
        assertEquals(2.0, e2.maxX, 1E-5)
        assertEquals(3.0, e2.minY, 1E-5)
        assertEquals(4.0, e2.maxY, 1E-5)
    }

    @Test
    @Throws(Exception::class)
    fun testCopy() {
        val e1 = Envelope(1.0, 2.0, 3.0, 4.0)
        val e2 = e1.copy()
        assertEquals(1.0, e2.minX, 1E-5)
        assertEquals(2.0, e2.maxX, 1E-5)
        assertEquals(3.0, e2.minY, 1E-5)
        assertEquals(4.0, e2.maxY, 1E-5)
        val eNull = Envelope()
        val eNullCopy = eNull.copy()
        assertTrue(eNullCopy.isNull)
    }

    @Test
    @Throws(Exception::class)
    fun testGeometryFactoryCreateEnvelope() {
        checkExpectedEnvelopeGeometry("POINT (0 0)")
        checkExpectedEnvelopeGeometry("POINT (100 13)")
        checkExpectedEnvelopeGeometry("LINESTRING (0 0, 0 10)")
        checkExpectedEnvelopeGeometry("LINESTRING (0 0, 10 0)")
        val poly10 = "POLYGON ((0 10, 10 10, 10 0, 0 0, 0 10))"
        checkExpectedEnvelopeGeometry(poly10)
        checkExpectedEnvelopeGeometry(
            "LINESTRING (0 0, 10 10)",
            poly10
        )
        checkExpectedEnvelopeGeometry(
            "POLYGON ((5 10, 10 6, 5 0, 0 6, 5 10))",
            poly10
        )
    }

    @Test
    fun testMetrics() {
        val env = Envelope(0.0, 4.0, 0.0, 3.0)
        assertEquals(env.width, 4.0)
        assertEquals(env.height, 3.0)
        assertEquals(env.diameter, 5.0)
    }

    @Test
    fun testEmptyMetrics() {
        val env = Envelope()
        assertEquals(env.width, 0.0)
        assertEquals(env.height, 0.0)
        assertEquals(env.diameter, 0.0)
    }

    private fun checkIntersectsPermuted(
        a1x: Double,
        a1y: Double,
        a2x: Double,
        a2y: Double,
        b1x: Double,
        b1y: Double,
        b2x: Double,
        b2y: Double,
        expected: Boolean
    ) {
        checkIntersects(a1x, a1y, a2x, a2y, b1x, b1y, b2x, b2y, expected)
        checkIntersects(a1x, a2y, a2x, a1y, b1x, b1y, b2x, b2y, expected)
        checkIntersects(a1x, a1y, a2x, a2y, b1x, b2y, b2x, b1y, expected)
        checkIntersects(a1x, a2y, a2x, a1y, b1x, b2y, b2x, b1y, expected)
    }

    private fun checkIntersects(
        a1x: Double,
        a1y: Double,
        a2x: Double,
        a2y: Double,
        b1x: Double,
        b1y: Double,
        b2x: Double,
        b2y: Double,
        expected: Boolean
    ) {
        val a = Envelope(a1x, a2x, a1y, a2y)
        val b = Envelope(b1x, b2x, b1y, b2y)
        assertEquals(expected, a.intersects(b))
        assertEquals(expected, !a.disjoint(b))
        val a1 = Coordinate(a1x, a1y)
        val a2 = Coordinate(a2x, a2y)
        val b1 = Coordinate(b1x, b1y)
        val b2 = Coordinate(b2x, b2y)
        assertEquals(expected, intersects(a1, a2, b1, b2))
        assertEquals(expected, a.intersects(b1, b2))
    }

    @Throws(ParseException::class)
    fun checkExpectedEnvelopeGeometry(wktInput: String?, wktEnvGeomExpected: String? = wktInput) {
        val input = reader.read(wktInput!!)
        val envGeomExpected = reader.read(wktEnvGeomExpected!!)
        val env = input!!.envelopeInternal
        val envGeomActual = geometryFactory.toGeometry(env)
        val isEqual = envGeomActual.equalsNorm(envGeomExpected)
        assertTrue(isEqual)
    }

    @Test
    fun testCompareTo() {
        checkCompareTo(0, Envelope(), Envelope())
        checkCompareTo(0, Envelope(1.0, 2.0, 1.0, 2.0), Envelope(1.0, 2.0, 1.0, 2.0))
        checkCompareTo(1, Envelope(2.0, 3.0, 1.0, 2.0), Envelope(1.0, 2.0, 1.0, 2.0))
        checkCompareTo(-1, Envelope(1.0, 2.0, 1.0, 2.0), Envelope(2.0, 3.0, 1.0, 2.0))
        checkCompareTo(1, Envelope(1.0, 2.0, 1.0, 3.0), Envelope(1.0, 2.0, 1.0, 2.0))
        checkCompareTo(1, Envelope(2.0, 3.0, 1.0, 3.0), Envelope(1.0, 3.0, 1.0, 2.0))
    }

    fun checkCompareTo(expected: Int, env1: Envelope, env2: Envelope) {
        assertTrue(expected == env1.compareTo(env2))
        assertTrue(-expected == env2.compareTo(env1))
    }
}