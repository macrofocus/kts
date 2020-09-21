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
import org.locationtech.kts.geom.GeometryCopyTest
import org.locationtech.jts.geom.GeometryFactory.Companion.toLineStringArray
import org.locationtech.jts.geom.GeometryFactory.Companion.toPointArray
import org.locationtech.jts.geom.GeometryFactory.Companion.toPolygonArray
import org.locationtech.kts.geom.GeometryImplTest
import org.locationtech.jts.io.ParseException
import org.locationtech.jts.io.WKTReader
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * @version 1.7
 */
class GeometryImplTest {
    @JvmField
    var precisionModel: PrecisionModel = PrecisionModel(1.0)
    @JvmField
    var geometryFactory = GeometryFactory(precisionModel, 0)
    @JvmField
    var reader = WKTReader(geometryFactory, allowOldJtsCoordinateSyntax = false)
    @JvmField
    var readerFloat = WKTReader(allowOldJtsCoordinateSyntax = false)

    @Test
    @Throws(Exception::class)
    fun testComparable() {
        val point = reader.read("POINT EMPTY")!!
        val lineString = reader.read("LINESTRING EMPTY")!!
        val linearRing = reader.read("LINEARRING EMPTY")!!
        val polygon = reader.read("POLYGON EMPTY")!!
        val mpoint = reader.read("MULTIPOINT EMPTY")!!
        val mlineString = reader.read("MULTILINESTRING EMPTY")!!
        val mpolygon = reader.read("MULTIPOLYGON EMPTY")!!
        val gc = reader.read("GEOMETRYCOLLECTION EMPTY")!!
        val geometries = arrayOf(
            gc,
            mpolygon,
            mlineString,
            mpoint,
            polygon,
            linearRing,
            lineString,
            point
        )
        val geometriesExpectedOrder = arrayOf(
            point,
            mpoint,
            lineString,
            linearRing,
            mlineString,
            polygon,
            mpolygon,
            gc
        )
        geometries.sort()
        assertTrue(geometries.contentEquals(geometriesExpectedOrder))
    }

    @Test
    @Throws(Exception::class)
    fun testPolygonRelate() {
        val bigPolygon = reader.read(
            "POLYGON ((0 0, 0 50, 50 50, 50 0, 0 0))"
        )
        val smallPolygon = reader.read(
            "POLYGON ((10 10, 10 30, 30 30, 30 10, 10 10))"
        )
        assertTrue(bigPolygon!!.contains(smallPolygon!!))
    }

    @Throws(Exception::class)
    fun testEmptyGeometryCentroid() {
        assertTrue(reader.read("POINT EMPTY")!!.centroid.isEmpty)
        assertTrue(reader.read("POLYGON EMPTY")!!.centroid.isEmpty)
        assertTrue(reader.read("LINESTRING EMPTY")!!.centroid.isEmpty)
        assertTrue(reader.read("GEOMETRYCOLLECTION EMPTY")!!.centroid.isEmpty)
        assertTrue(reader.read("GEOMETRYCOLLECTION(GEOMETRYCOLLECTION EMPTY, GEOMETRYCOLLECTION EMPTY)")!!.centroid.isEmpty)
        assertTrue(reader.read("MULTIPOLYGON EMPTY")!!.centroid.isEmpty)
        assertTrue(reader.read("MULTILINESTRING EMPTY")!!.centroid.isEmpty)
        assertTrue(reader.read("MULTIPOINT EMPTY")!!.centroid.isEmpty)
    }

    @Test
    @Throws(Exception::class)
    fun testNoOutgoingDirEdgeFound() {
        doTestFromCommcast2003AtYahooDotCa(reader)
    }

    // ToDo: Test not passing with Kotlin
    //    public void testOutOfMemoryError() throws Exception {
    //        doTestFromCommcast2003AtYahooDotCa(new WKTReader());
    //    }
    @Test
    @Throws(Exception::class)
    fun testDepthMismatchAssertionFailedException() {
        //register@robmeek.com reported an assertion failure 
        //("depth mismatch at (160.0, 300.0, Nan)") [Jon Aquino 10/28/2003]
        reader
            .read(
                "MULTIPOLYGON (((100 300, 100 400, 200 400, 200 300, 100 300)),"
                        + "((160 300, 160 400, 260 400, 260 300, 160 300)),"
                        + "((160 300, 160 200, 260 200, 260 300, 160 300)))"
            )!!.buffer(0.0)
    }

    @Throws(ParseException::class)
    private fun doTestFromCommcast2003AtYahooDotCa(reader: WKTReader) {
        readerFloat.read(
            "POLYGON ((708653.498611049 2402311.54647056, 708708.895756966 2402203.47250014, 708280.326454234 2402089.6337791, 708247.896591321 2402252.48269854, 708367.379593851 2402324.00761653, 708248.882609455 2402253.07294874, 708249.523621829 2402244.3124463, 708261.854734465 2402182.39086576, 708262.818392579 2402183.35452387, 708653.498611049 2402311.54647056))"
        )!!
            .intersection(
                reader.read(
                    "POLYGON ((708258.754920656 2402197.91172757, 708257.029447455 2402206.56901508, 708652.961095455 2402312.65463437, 708657.068786251 2402304.6356364, 708258.754920656 2402197.91172757))"
                )!!
            )
    }

    @Test
    @Throws(Exception::class)
    fun testEquals() {
        val g = reader.read("POLYGON ((0 0, 0 50, 50 50, 50 0, 0 0))")
        val same = reader.read("POLYGON ((0 0, 0 50, 50 50, 50 0, 0 0))")
        val differentStart = reader.read(
            "POLYGON ((0 50, 50 50, 50 0, 0 0, 0 50))"
        )
        val differentFourth = reader.read(
            "POLYGON ((0 0, 0 50, 50 50, 50 -99, 0 0))"
        )
        val differentSecond = reader.read(
            "POLYGON ((0 0, 0 99, 50 50, 50 0, 0 0))"
        )
        doTestEquals(g, same, true, true, true, true)
        doTestEquals(g, differentStart, true, false, false, true)
        doTestEquals(g, differentFourth, false, false, false, false)
        doTestEquals(g, differentSecond, false, false, false, false)
    }

    private fun doTestEquals(
        a: Geometry?, b: Geometry?, equalsGeometry: Boolean,
        equalsObject: Boolean, equalsExact: Boolean, equalsHash: Boolean
    ) {
        assertEquals(equalsGeometry, a!!.equals(b))
        assertEquals(equalsObject, a!!.equals(b as Any?))
        assertEquals(equalsExact, a!!.equalsExact(b!!))
        assertEquals(equalsHash, a.hashCode() == b.hashCode())
    }

    @Test
    @Throws(Exception::class)
    fun testInvalidateEnvelope() {
        val g = reader.read("POLYGON ((0 0, 0 50, 50 50, 50 0, 0 0))")
        assertEquals(Envelope(0.0, 50.0, 0.0, 50.0), g!!.envelopeInternal)
        g!!.apply(object : CoordinateFilter {
            override fun filter(coord: Coordinate?) {
                coord!!.x = coord.x + 1
                coord.y = coord.y + 1
            }
        })
        assertEquals(Envelope(0.0, 50.0, 0.0, 50.0), g.envelopeInternal)
        g.geometryChanged()
        assertEquals(Envelope(1.0, 51.0, 1.0, 51.0), g.envelopeInternal)
    }

    @Test
    @Throws(Exception::class)
    fun testEquals1() {
        val polygon1 = reader.read(
            "POLYGON ((0 0, 0 50, 50 50, 50 0, 0 0))"
        )
        val polygon2 = reader.read(
            "POLYGON ((50 50, 50 0, 0 0, 0 50, 50 50))"
        )
        assertTrue(polygon1!!.equals(polygon2))
    }

    @Test
    @Throws(Exception::class)
    fun testEqualsWithNull() {
        val polygon = reader.read("POLYGON ((0 0, 0 50, 50 50, 50 0, 0 0))")
        assertTrue(!polygon!!.equals(null))
        val g: Any? = null
        assertTrue(!polygon!!.equals(g))
    }

    //  public void testEquals2() throws Exception {
    //    Geometry lineString = reader.read("LINESTRING(0 0, 0 50, 50 50, 50 0, 0 0)");
    //    Geometry geometryCollection = reader.read("GEOMETRYCOLLECTION ( LINESTRING(0 0  , 0  50), "
    //                                                                 + "LINESTRING(0 50 , 50 50), "
    //                                                                 + "LINESTRING(50 50, 50 0 ), "
    //                                                                 + "LINESTRING(50 0 , 0  0 ) )");
    //    assertTrue(lineString.equals(geometryCollection));
    //  }
    @Test
    @Throws(Exception::class)
    fun testEqualsExactForLinearRings() {
        val x = geometryFactory.createLinearRing(
            arrayOf(
                Coordinate(0.0, 0.0), Coordinate(100.0, 0.0),
                Coordinate(100.0, 100.0), Coordinate(0.0, 0.0)
            )
        )
        val somethingExactlyEqual = geometryFactory.createLinearRing(
            arrayOf(
                Coordinate(0.0, 0.0), Coordinate(100.0, 0.0),
                Coordinate(100.0, 100.0), Coordinate(0.0, 0.0)
            )
        )
        val somethingNotEqualButSameClass = geometryFactory.createLinearRing(
            arrayOf(
                Coordinate(0.0, 0.0), Coordinate(100.0, 0.0),
                Coordinate(100.0, 555.0), Coordinate(0.0, 0.0)
            )
        )
        val sameClassButEmpty = geometryFactory.createLinearRing(null as CoordinateSequence?)
        val anotherSameClassButEmpty = geometryFactory.createLinearRing(null as CoordinateSequence?)
        val collectionFactory: CollectionFactory = object : CollectionFactory {
            override fun createCollection(geometries: Array<Geometry>): Geometry {
                return geometryFactory.createMultiLineString(
                    toLineStringArray(geometries.toList())
                )!!
            }
        }
        doTestEqualsExact(
            x, somethingExactlyEqual,
            somethingNotEqualButSameClass, sameClassButEmpty,
            anotherSameClassButEmpty, collectionFactory
        )

        //    LineString somethingEqualButNotExactly = geometryFactory.createLineString(new Coordinate[] {
        //          new Coordinate(0, 0), new Coordinate(100, 0), new Coordinate(100, 100),
        //          new Coordinate(0, 0) });
        //
        //    doTestEqualsExact(x, somethingExactlyEqual, somethingEqualButNotExactly,
        //          somethingNotEqualButSameClass);
    }

    @Test
    @Throws(Exception::class)
    fun testEqualsExactForLineStrings() {
        val x = geometryFactory.createLineString(
            arrayOf(
                Coordinate(0.0, 0.0), Coordinate(100.0, 0.0),
                Coordinate(100.0, 100.0)
            )
        )
        val somethingExactlyEqual = geometryFactory.createLineString(
            arrayOf(
                Coordinate(0.0, 0.0), Coordinate(100.0, 0.0),
                Coordinate(100.0, 100.0)
            )
        )
        val somethingNotEqualButSameClass = geometryFactory.createLineString(
            arrayOf(
                Coordinate(0.0, 0.0), Coordinate(100.0, 0.0),
                Coordinate(100.0, 555.0)
            )
        )
        val sameClassButEmpty: LineString = geometryFactory.createLineString(null as Array<Coordinate>?)
        val anotherSameClassButEmpty: LineString = geometryFactory.createLineString(null as Array<Coordinate>?)
        val collectionFactory: CollectionFactory = object : CollectionFactory {
            override fun createCollection(geometries: Array<Geometry>): Geometry {
                return geometryFactory.createMultiLineString(toLineStringArray(geometries.toList()))!!
            }
        }
        doTestEqualsExact(
            x, somethingExactlyEqual,
            somethingNotEqualButSameClass, sameClassButEmpty,
            anotherSameClassButEmpty, collectionFactory
        )
        val collectionFactory2: CollectionFactory = object : CollectionFactory {
            override fun createCollection(geometries: Array<Geometry>): Geometry {
                return geometryFactory.createMultiLineString(toLineStringArray(geometries.toList()))!!
            }
        }
        doTestEqualsExact(
            x, somethingExactlyEqual,
            somethingNotEqualButSameClass, sameClassButEmpty,
            anotherSameClassButEmpty, collectionFactory2
        )
    }

    @Test
    @Throws(Exception::class)
    fun testEqualsExactForPoints() {
        val x = geometryFactory.createPoint(Coordinate(100.0, 100.0))
        val somethingExactlyEqual = geometryFactory.createPoint(
            Coordinate(
                100.0, 100.0
            )
        )
        val somethingNotEqualButSameClass = geometryFactory.createPoint(
            Coordinate(
                999.0, 100.0
            )
        )
        val sameClassButEmpty = geometryFactory.createPoint(null as Coordinate?)
        val anotherSameClassButEmpty = geometryFactory.createPoint(null as Coordinate?)
        val collectionFactory: CollectionFactory = object : CollectionFactory {
            override fun createCollection(geometries: Array<Geometry>): Geometry {
                return geometryFactory.createMultiPoint(toPointArray(geometries.toList()))
            }
        }
        doTestEqualsExact(
            x, somethingExactlyEqual,
            somethingNotEqualButSameClass, sameClassButEmpty,
            anotherSameClassButEmpty, collectionFactory
        )
    }

    @Test
    @Throws(Exception::class)
    fun testEqualsExactForPolygons() {
        val x = reader.read(
            "POLYGON ((0 0, 0 50, 50 50, 50 0, 0 0))"
        ) as Polygon
        val somethingExactlyEqual = reader.read(
            "POLYGON ((0 0, 0 50, 50 50, 50 0, 0 0))"
        ) as Polygon
        val somethingNotEqualButSameClass = reader.read(
            "POLYGON ((50 50, 50 0, 0 0, 0 50, 50 50))"
        ) as Polygon
        val sameClassButEmpty = reader.read("POLYGON EMPTY") as Polygon
        val anotherSameClassButEmpty = reader.read(
            "POLYGON EMPTY"
        ) as Polygon
        val collectionFactory: CollectionFactory = object : CollectionFactory {
            override fun createCollection(geometries: Array<Geometry>): Geometry {
                return geometryFactory.createMultiPolygon(toPolygonArray(geometries.toList()))
            }
        }
        doTestEqualsExact(
            x, somethingExactlyEqual,
            somethingNotEqualButSameClass, sameClassButEmpty,
            anotherSameClassButEmpty, collectionFactory
        )
    }

    @Test
    @Throws(Exception::class)
    fun testEqualsExactForGeometryCollections() {
        val polygon1: Geometry = reader.read(
            "POLYGON ((0 0, 0 50, 50 50, 50 0, 0 0))"
        ) as Polygon
        val polygon2: Geometry = reader.read(
            "POLYGON ((50 50, 50 0, 0 0, 0 50, 50 50))"
        ) as Polygon
        val x = geometryFactory.createGeometryCollection(
            arrayOf<Geometry>(
                polygon1, polygon2
            )
        )
        val somethingExactlyEqual = geometryFactory.createGeometryCollection(
            arrayOf<Geometry>(
                polygon1, polygon2
            )
        )
        val somethingNotEqualButSameClass = geometryFactory.createGeometryCollection(
            arrayOf<Geometry>(
                polygon2
            )
        )
        val sameClassButEmpty = geometryFactory.createGeometryCollection(null)
        val anotherSameClassButEmpty = geometryFactory.createGeometryCollection(null)
        val collectionFactory: CollectionFactory = object : CollectionFactory {
            override fun createCollection(geometries: Array<Geometry>): Geometry {
                return geometryFactory.createGeometryCollection(geometries)
            }
        }
        doTestEqualsExact(
            x, somethingExactlyEqual,
            somethingNotEqualButSameClass, sameClassButEmpty,
            anotherSameClassButEmpty, collectionFactory
        )
    }

    @Test
    @Throws(Exception::class)
    fun testGeometryCollectionIntersects1() {
        val gc0 = reader.read(
            "GEOMETRYCOLLECTION ( POINT(0 0) )"
        )
        val gc1 = reader.read(
            "GEOMETRYCOLLECTION ( LINESTRING(0 0, 1 1) )"
        )
        val gc2 = reader.read(
            "GEOMETRYCOLLECTION ( LINESTRING(1 0, 0 1) )"
        )
        assertTrue(gc0!!.intersects(gc1!!))
        assertTrue(gc1!!.intersects(gc2!!))
        assertTrue(!gc0!!.intersects(gc2!!))
        // symmetric
        assertTrue(gc1!!.intersects(gc0!!))
        assertTrue(gc2!!.intersects(gc1!!))
        assertTrue(!gc2!!.intersects(gc0!!))
    }

    @Throws(Exception::class)
    fun testGeometryCollectionIntersects2() {
        val gc0 = reader.read(
            "POINT(0 0)"
        )
        val gc1 = reader.read(
            "GEOMETRYCOLLECTION ( LINESTRING(0 0, 1 1) )"
        )
        val gc2 = reader.read(
            "LINESTRING(1 0, 0 1)"
        )
        assertTrue(gc0!!.intersects(gc1!!))
        assertTrue(gc1!!.intersects(gc2!!))
        // symmetric
        assertTrue(gc1!!.intersects(gc0!!))
        assertTrue(gc2!!.intersects(gc1!!))
    }

    @Test
    @Throws(Exception::class)
    fun testGeometryCollectionIntersects3() {
        val gc0 = reader.read(
            "GEOMETRYCOLLECTION ( POINT(0 0), LINESTRING(1 1, 2 2) )"
        )
        val gc1 = reader.read(
            "GEOMETRYCOLLECTION ( POINT(15 15) )"
        )
        val gc2 = reader.read(
            "GEOMETRYCOLLECTION ( LINESTRING(0 0, 2 0), POLYGON((10 10, 20 10, 20 20, 10 20, 10 10)))"
        )
        assertTrue(gc0!!.intersects(gc2!!))
        assertTrue(!gc0!!.intersects(gc1!!))
        assertTrue(gc1!!.intersects(gc2!!))
        // symmetric
        assertTrue(gc2!!.intersects(gc0!!))
        assertTrue(!gc1!!.intersects(gc0!!))
        assertTrue(gc2!!.intersects(gc1!!))
    }

    @Throws(Exception::class)
    private fun doTestEqualsExact(
        x: Geometry,
        somethingExactlyEqual: Geometry,
        somethingNotEqualButSameClass: Geometry,
        sameClassButEmpty: Geometry?,
        anotherSameClassButEmpty: Geometry?,
        collectionFactory: CollectionFactory
    ) {
        val emptyDifferentClass: Geometry
        emptyDifferentClass = if (x is Point) {
            geometryFactory.createGeometryCollection(null)
        } else {
            geometryFactory.createPoint(null as Coordinate?)
        }
        val somethingEqualButNotExactly: Geometry = geometryFactory.createGeometryCollection(
            arrayOf<Geometry>(
                x
            )
        )
        doTestEqualsExact(
            x, somethingExactlyEqual,
            collectionFactory.createCollection(arrayOf(x)),
            somethingNotEqualButSameClass
        )
        doTestEqualsExact(
            sameClassButEmpty, anotherSameClassButEmpty,
            emptyDifferentClass, x
        )
        /**
         * Test comparison of non-empty versus empty.
         */
        doTestEqualsExact(
            x, somethingExactlyEqual,
            sameClassButEmpty, sameClassButEmpty
        )
        doTestEqualsExact(
            collectionFactory.createCollection(arrayOf(x, x)),
            collectionFactory.createCollection(arrayOf(x, somethingExactlyEqual)),
            somethingEqualButNotExactly,
            collectionFactory.createCollection(arrayOf(x, somethingNotEqualButSameClass))
        )
    }

    @Throws(Exception::class)
    private fun doTestEqualsExact(
        x: Geometry?,
        somethingExactlyEqual: Geometry?,
        somethingEqualButNotExactly: Geometry?,
        somethingNotEqualButSameClass: Geometry?
    ) {
        val differentClass: Geometry?
        differentClass = if (x is Point) {
            reader.read(
                "POLYGON ((0 0, 0 50, 50 43949, 50 0, 0 0))"
            )
        } else {
            reader.read("POINT ( 2351 1563 )")
        }
        assertTrue(x!!.equalsExact(x))
        assertTrue(x!!.equalsExact(somethingExactlyEqual!!))
        assertTrue(somethingExactlyEqual!!.equalsExact(x!!))
        assertTrue(!x!!.equalsExact(somethingEqualButNotExactly!!))
        assertTrue(!somethingEqualButNotExactly!!.equalsExact(x!!))
        assertTrue(!x!!.equalsExact(somethingEqualButNotExactly!!))
        assertTrue(!somethingEqualButNotExactly!!.equalsExact(x!!))
        assertTrue(!x!!.equalsExact(differentClass!!))
        assertTrue(!differentClass!!.equalsExact(x!!))
    }

    private interface CollectionFactory {
        fun createCollection(geometries: Array<Geometry>): Geometry
    }
}