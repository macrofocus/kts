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
package org.locationtech.jts.geom

import junit.framework.TestCase
import junit.textui.TestRunner
import org.junit.Test
import org.locationtech.jts.io.WKTReader

/**
 * @version 1.7
 */
class AreaLengthTest(name: String?) : TestCase(name) {
    private val precisionModel = PrecisionModel()
    private val geometryFactory = GeometryFactory(precisionModel, 0)
    var reader = WKTReader(geometryFactory)
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

    companion object {
        private const val TOLERANCE = 1E-5
        @JvmStatic
        fun main(args: Array<String>) {
            TestRunner.run(AreaLengthTest::class.java)
        }
    }
}