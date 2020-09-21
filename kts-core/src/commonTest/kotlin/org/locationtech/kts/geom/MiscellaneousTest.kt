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
import org.locationtech.jts.geom.GeometryFactory.Companion.toPointArray
import org.locationtech.jts.io.WKTReader
import org.locationtech.jts.legacy.Math
import kotlin.jvm.JvmField
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * @version 1.7
 */
class MiscellaneousTest {
    @JvmField
    var precisionModel: PrecisionModel = PrecisionModel(1.0)
    @JvmField
    var geometryFactory = GeometryFactory(precisionModel, 0)
    @JvmField
    var reader = WKTReader(geometryFactory)
    @Throws(Exception::class)
    fun testEnvelopeCloned() {
        val a = reader.read("LINESTRING(0 0, 10 10)")
        //Envelope is lazily initialized [Jon Aquino]
        a!!.envelopeInternal
        assertTrue(a.envelopeInternal !== a.copy().envelopeInternal)
    }

    @Throws(Exception::class)
    fun testCreateEmptyGeometry() {
        assertTrue(geometryFactory.createPoint(null as Coordinate?).isEmpty)
        assertTrue(geometryFactory.createLinearRing(arrayOf()).isEmpty)
        assertTrue(geometryFactory.createLineString(arrayOf()).isEmpty)
        assertTrue(geometryFactory.createPolygon(geometryFactory.createLinearRing(arrayOf()), arrayOf()).isEmpty)
        assertTrue(geometryFactory.createMultiPolygon(arrayOf()).isEmpty)
        assertTrue(geometryFactory.createMultiLineString(arrayOf())!!.isEmpty)
        assertTrue(geometryFactory.createMultiPoint(arrayOf<Point>()).isEmpty)
        assertTrue(geometryFactory.createPoint(null as Coordinate?).isSimple)
        assertTrue(geometryFactory.createLinearRing(arrayOf()).isSimple)
        /**
         * @todo Enable when #isSimple implemented
         */
//    assertTrue(geometryFactory.createLineString(new Coordinate[] { }).isSimple());
//    assertTrue(geometryFactory.createPolygon(geometryFactory.createLinearRing(new Coordinate[] { }), new LinearRing[] { }).isSimple());
//    assertTrue(geometryFactory.createMultiPolygon(new Polygon[] { }).isSimple());
//    assertTrue(geometryFactory.createMultiLineString(new LineString[] { }).isSimple());
//    assertTrue(geometryFactory.createMultiPoint(new Point[] { }).isSimple());
        assertTrue(geometryFactory.createPoint(null as Coordinate?).boundary.isEmpty)
        assertTrue(geometryFactory.createLinearRing(arrayOf()).boundary.isEmpty)
        assertTrue(geometryFactory.createLineString(arrayOf()).boundary.isEmpty)
        assertTrue(
            geometryFactory.createPolygon(
                geometryFactory.createLinearRing(arrayOf()),
                arrayOf()
            ).boundary!!.isEmpty
        )
        assertTrue(geometryFactory.createMultiPolygon(arrayOf()).boundary!!.isEmpty)
        assertTrue(geometryFactory.createMultiLineString(arrayOf())!!.boundary!!.isEmpty)
        assertTrue(geometryFactory.createMultiPoint(arrayOf<Point>()).boundary!!.isEmpty)
        assertTrue(geometryFactory.createLinearRing(null as CoordinateSequence?).isEmpty)
        assertTrue(geometryFactory.createLineString(null as Array<Coordinate>?).isEmpty)
        assertTrue(geometryFactory.createPolygon(null, null).isEmpty)
        assertTrue(geometryFactory.createMultiPolygon(null).isEmpty)
        assertTrue(geometryFactory.createMultiLineString(null as Array<LineString>?)!!.isEmpty)
        assertTrue(geometryFactory.createMultiPoint(null as Array<Point>?).isEmpty)
        assertEquals(-1, geometryFactory.createPoint(null as Coordinate?).boundaryDimension)
        assertEquals(-1, geometryFactory.createLinearRing(null as CoordinateSequence?).boundaryDimension)
        assertEquals(0, geometryFactory.createLineString(null as Array<Coordinate>?).boundaryDimension)
        assertEquals(1, geometryFactory.createPolygon(null, null).boundaryDimension)
        assertEquals(1, geometryFactory.createMultiPolygon(null).boundaryDimension)
        assertEquals(0, geometryFactory.createMultiLineString(null as Array<LineString>?)!!.boundaryDimension)
        assertEquals(-1, geometryFactory.createMultiPoint(null as Array<Point>?).boundaryDimension)
        assertEquals(0, geometryFactory.createPoint(null as Coordinate?).numPoints)
        assertEquals(0, geometryFactory.createLinearRing(null as CoordinateSequence?).numPoints)
        assertEquals(0, geometryFactory.createLineString(null as Array<Coordinate>?).numPoints)
        assertEquals(0, geometryFactory.createPolygon(null, null).numPoints)
        assertEquals(0, geometryFactory.createMultiPolygon(null).numPoints)
        assertEquals(0, geometryFactory.createMultiLineString(null as Array<LineString>?)!!.numPoints)
        assertEquals(0, geometryFactory.createMultiPoint(null as Array<Point>?).numPoints)
        assertEquals(0, geometryFactory.createPoint(null as Coordinate?).coordinates!!.size)
        assertEquals(0, geometryFactory.createLinearRing(null as CoordinateSequence?).coordinates.size)
        assertEquals(0, geometryFactory.createLineString(null as Array<Coordinate>?).coordinates.size)
        assertEquals(0, geometryFactory.createPolygon(null, null).coordinates.size)
        assertEquals(0, geometryFactory.createMultiPolygon(null).coordinates.size)
        assertEquals(0, geometryFactory.createMultiLineString(null as Array<LineString>?)!!.coordinates.size)
        assertEquals(0, geometryFactory.createMultiPoint(null as Array<Point>?).coordinates.size)
    }

    @Throws(Exception::class)
    fun testBoundaryOfEmptyGeometry() {
        assertTrue(geometryFactory.createPoint(null as Coordinate?).boundary::class == GeometryCollection::class)
        assertTrue(geometryFactory.createLinearRing(arrayOf()).boundary::class == MultiPoint::class)
        assertTrue(geometryFactory.createLineString(arrayOf()).boundary::class == MultiPoint::class)
        assertTrue(
            geometryFactory.createPolygon(
                geometryFactory.createLinearRing(arrayOf()),
                arrayOf()
            ).boundary!!::class == MultiLineString::class
        )
        assertTrue(geometryFactory.createMultiPolygon(arrayOf()).boundary!!::class == MultiLineString::class)
        assertTrue(geometryFactory.createMultiLineString(arrayOf())!!.boundary!!::class == MultiPoint::class)
        assertTrue(geometryFactory.createMultiPoint(arrayOf<Point>()).boundary!!::class == GeometryCollection::class)
        try {
            geometryFactory.createGeometryCollection(arrayOf()).boundary
            assertTrue(false)
        } catch (e: IllegalArgumentException) {
        }
    }

    fun testToPointArray() {
        val list: ArrayList<Point> = ArrayList()
        list.add(geometryFactory.createPoint(Coordinate(0.0, 0.0)))
        list.add(geometryFactory.createPoint(Coordinate(10.0, 0.0)))
        list.add(geometryFactory.createPoint(Coordinate(10.0, 10.0)))
        list.add(geometryFactory.createPoint(Coordinate(0.0, 10.0)))
        list.add(geometryFactory.createPoint(Coordinate(0.0, 0.0)))
        val points = toPointArray(list)
        org.locationtech.kts.assertEquals(10.0, points[1].x, 1E-1)
        org.locationtech.kts.assertEquals(0.0, points[1].y, 1E-1)
    }

    @Throws(Exception::class)
    fun testPolygonGetCoordinates() {
        val p = reader.read(
            "POLYGON ( (0 0, 100 0, 100 100, 0 100, 0 0), "
                    + "          (20 20, 20 80, 80 80, 80 20, 20 20)) "
        ) as Polygon?
        val coordinates = p!!.coordinates
        assertEquals(10, p.numPoints)
        assertEquals(10, coordinates.size)
        assertEquals(Coordinate(0.0, 0.0), coordinates[0])
        assertEquals(Coordinate(20.0, 20.0), coordinates[9])
    }

    @Throws(Exception::class)
    fun testEmptyPoint() {
        val p = geometryFactory.createPoint(null as Coordinate?)
        assertEquals(0, p.dimension)
        assertEquals(Envelope(), p.envelopeInternal)
        assertTrue(p.isSimple)
        try {
            p.x
            assertTrue(false)
        } catch (e1: IllegalStateException) {
        }
        try {
            p.y
            assertTrue(false)
        } catch (e2: IllegalStateException) {
        }
        assertEquals("POINT EMPTY", p.toString())
        assertEquals("POINT EMPTY", p.toText())
    }

    @Throws(Exception::class)
    fun testEmptyLineString() {
        val l: LineString = geometryFactory.createLineString(null as Array<Coordinate>?)
        assertEquals(1, l.dimension)
        assertEquals(Envelope(), l.envelopeInternal)
        /**
         * @todo Enable when #isSimple implemented
         */
//    assertTrue(l.isSimple());
        assertEquals(null, l.startPoint)
        assertEquals(null, l.endPoint)
        assertTrue(!l.isClosed)
        assertTrue(!l.isRing)
    }

    @Throws(Exception::class)
    fun testEmptyLinearRing() {
        val l: LineString = geometryFactory.createLinearRing(null as CoordinateSequence?)
        assertEquals(1, l.dimension)
        assertEquals(Envelope(), l.envelopeInternal)
        assertTrue(l.isSimple)
        assertEquals(null, l.startPoint)
        assertEquals(null, l.endPoint)
        assertTrue(l.isClosed)
        assertTrue(l.isRing)
    }

    @Throws(Exception::class)
    fun testEmptyPolygon() {
        val p = geometryFactory.createPolygon(null, null)
        assertEquals(2, p.dimension)
        assertEquals(Envelope(), p.envelopeInternal)
        assertTrue(p.isSimple)
    }

    @Throws(Exception::class)
    fun testEmptyGeometryCollection() {
        val g = geometryFactory.createGeometryCollection(null)
        assertEquals(-1, g.dimension)
        assertEquals(Envelope(), g.envelopeInternal)
        assertTrue(g.isSimple)
    }

    @Throws(Exception::class)
    fun testEmptyMultiPoint() {
        val g: MultiPoint = geometryFactory.createMultiPoint(null as Array<Point>?)
        assertEquals(0, g.dimension)
        assertEquals(Envelope(), g.envelopeInternal)
        /**
         * @todo Enable when #isSimple implemented
         */
//    assertTrue(g.isSimple());
    }

    @Throws(Exception::class)
    fun testEmptyMultiLineString() {
        val g = geometryFactory.createMultiLineString(null as Array<LineString>?)
        assertEquals(1, g!!.dimension)
        assertEquals(Envelope(), g!!.envelopeInternal)
        /**
         * @todo Enable when #isSimple implemented
         */
//    assertTrue(g.isSimple());
        assertTrue(!g!!.isClosed)
    }

    @Throws(Exception::class)
    fun testEmptyMultiPolygon() {
        val g = geometryFactory.createMultiPolygon(null)
        assertEquals(2, g.dimension)
        assertEquals(Envelope(), g.envelopeInternal)
        assertTrue(g.isSimple)
    }

    @Throws(Exception::class)
    fun testGetGeometryType() {
        val g: GeometryCollection = geometryFactory.createMultiPolygon(null)
        assertEquals("MultiPolygon", g.geometryType)
    }

    @Throws(Exception::class)
    fun testMultiPolygonIsSimple1() {
        val g = reader.read("MULTIPOLYGON (((10 10, 10 20, 20 20, 20 15, 10 10)), ((60 60, 70 70, 80 60, 60 60)))")
        assertTrue(g!!.isSimple)
    }

    @Throws(Exception::class)
    fun testPointIsSimple() {
        val g = reader.read("POINT (10 10)")
        assertTrue(g!!.isSimple)
    }

    @Throws(Exception::class)
    fun testPointGetBoundary() {
        val g = reader.read("POINT (10 10)")
        assertTrue(g!!.boundary!!.isEmpty)
    }

    /**
     * @todo Enable when #isSimple implemented
     */
    //  public void testMultiPointIsSimple1() throws Exception {
    //    Geometry g = reader.read("MULTIPOINT(10 10, 20 20, 30 30)");
    //    assertTrue(g.isSimple());
    //  }
    @Throws(Exception::class)
    fun testMultiPointGetBoundary() {
        val g = reader.read("MULTIPOINT(10 10, 20 20, 30 30)")
        assertTrue(g!!.boundary!!.isEmpty)
    }
    /**
     * @todo Enable when #isSimple implemented
     */
    //  public void testMultiPointIsSimple2() throws Exception {
    //    Geometry g = reader.read("MULTIPOINT(10 10, 30 30, 30 30)");
    //    assertTrue(! g.isSimple());
    //  }
    /**
     * @todo Enable when #isSimple implemented
     */
    //  public void testLineStringIsSimple1() throws Exception {
    //    Geometry g = reader.read("LINESTRING(10 10, 20 10, 15 20)");
    //    assertTrue(g.isSimple());
    //  }
    @Throws(Exception::class)
    fun testLineStringGetBoundary1() {
        val g = reader.read("LINESTRING(10 10, 20 10, 15 20)") as LineString?
        assertTrue(g!!.boundary is MultiPoint)
        val boundary = g!!.boundary as MultiPoint
        assertTrue(boundary.getGeometryN(0).equals(g.startPoint))
        assertTrue(boundary.getGeometryN(1).equals(g.endPoint))
    }

    @Throws(Exception::class)
    fun testLineStringGetBoundary2() {
        val g = reader.read("LINESTRING(10 10, 20 10, 15 20, 10 10)") as LineString?
        assertTrue(g!!.boundary.isEmpty)
    }

    /**
     * @todo Enable when #isSimple implemented
     */
    //  public void testLineStringIsSimple2() throws Exception {
    //    Geometry g = reader.read("LINESTRING(10 10, 20 10, 15 20, 15 0)");
    //    assertTrue(! g.isSimple());
    //  }
    @Throws(Exception::class)
    fun testLinearRingIsSimple() {
        val coordinates = arrayOf(
            Coordinate(10.0, 10.0, 0.0),
            Coordinate(10.0, 20.0, 0.0),
            Coordinate(20.0, 20.0, 0.0),
            Coordinate(20.0, 15.0, 0.0),
            Coordinate(10.0, 10.0, 0.0)
        )
        val linearRing = geometryFactory.createLinearRing(coordinates)
        assertTrue(linearRing.isSimple)
    }

    @Throws(Exception::class)
    fun testPolygonIsSimple() {
        val g = reader.read("POLYGON((10 10, 10 20, 202 0, 20 15, 10 10))")
        assertTrue(g!!.isSimple)
    }

    @Throws(Exception::class)
    fun testPolygonGetBoundary() {
        val g = reader.read(
            "POLYGON("
                    + "(0 0, 40 0, 40 40, 0 40, 0 0),"
                    + "(10 10, 30 10, 30 30, 10 30, 10 10))"
        )
        val b = reader.read(
            "MULTILINESTRING("
                    + "(0 0, 40 0, 40 40, 0 40, 0 0),"
                    + "(10 10, 30 10, 30 30, 10 30, 10 10))"
        )
        assertTrue(b!!.equalsExact(g!!.boundary!!))
    }

    @Throws(Exception::class)
    fun testMultiPolygonGetBoundary1() {
        val g = reader.read(
            "MULTIPOLYGON("
                    + "(  (0 0, 40 0, 40 40, 0 40, 0 0),"
                    + "   (10 10, 30 10, 30 30, 10 30, 10 10)  ),"
                    + "(  (200 200, 210 200, 210 210, 200 200) )  )"
        )
        val b = reader.read(
            "MULTILINESTRING("
                    + "(0 0, 40 0, 40 40, 0 40, 0 0),"
                    + "(10 10, 30 10, 30 30, 10 30, 10 10),"
                    + "(200 200, 210 200, 210 210, 200 200))"
        )
        assertTrue(b!!.equalsExact(g!!.boundary!!))
    }

    @Throws(Exception::class)
    fun testMultiPolygonIsSimple2() {
        val g = reader.read(
            "MULTIPOLYGON("
                    + "((10 10, 10 20, 20 20, 20 15, 10 10)), "
                    + "((60 60, 70 70, 80 60, 60 60))  )"
        )
        assertTrue(g!!.isSimple)
    }
    //  public void testGeometryCollectionIsSimple1() throws Exception {
    //    Geometry g = reader.read("GEOMETRYCOLLECTION("
    //          + "LINESTRING(0 0,  100 0),"
    //          + "LINESTRING(0 10, 100 10))");
    //    assertTrue(g.isSimple());
    //  }
    //  public void testGeometryCollectionIsSimple2() throws Exception {
    //    Geometry g = reader.read("GEOMETRYCOLLECTION("
    //          + "LINESTRING(0 0,  100 0),"
    //          + "LINESTRING(50 0, 100 10))");
    //    assertTrue(! g.isSimple());
    //  }
    /**
     * @todo Enable when #isSimple implemented
     */
    //  public void testMultiLineStringIsSimple1() throws Exception {
    //    Geometry g = reader.read("MULTILINESTRING("
    //          + "(0 0,  100 0),"
    //          + "(0 10, 100 10))");
    //    assertTrue(g.isSimple());
    //  }
    /**
     * @todo Enable when #isSimple implemented
     */
    //  public void testMultiLineStringIsSimple2() throws Exception {
    //    Geometry g = reader.read("MULTILINESTRING("
    //          + "(0 0,  100 0),"
    //          + "(50 0, 100 10))");
    //    assertTrue(! g.isSimple());
    //  }
    @Throws(Exception::class)
    fun testMultiLineStringGetBoundary1() {
        val g = reader.read(
            "MULTILINESTRING("
                    + "(0 0,  100 0, 50 50),"
                    + "(50 50, 50 -50))"
        )
        val m = reader.read("MULTIPOINT(0 0, 50 -50)")
        assertTrue(m!!.equalsExact(g!!.boundary!!))
    }

    @Throws(Exception::class)
    fun testMultiLineStringGetBoundary2() {
        val g = reader.read(
            "MULTILINESTRING("
                    + "(0 0,  100 0, 50 50),"
                    + "(50 50, 50 0))"
        )
        val m = reader.read("MULTIPOINT(0 0, 50 0)")
        assertTrue(m!!.equalsExact(g!!.boundary!!))
    }

    //  public void testGeometryCollectionGetBoundary1() throws Exception {
    //    Geometry g = reader.read("GEOMETRYCOLLECTION("
    //          + "POLYGON((0 0, 100 0, 100 100, 0 100, 0 0)),"
    //          + "LINESTRING(200 100, 200 0))");
    //    Geometry b = reader.read("GEOMETRYCOLLECTION("
    //          + "LINESTRING(0 0, 100 0, 100 100, 0 100, 0 0),"
    //          + "LINESTRING(200 100, 200 0))");
    //    assertEquals(b, g.getBoundary());
    //    assertTrue(! g.equals(g.getBoundary()));
    //  }
    //  public void testGeometryCollectionGetBoundary2() throws Exception {
    //    Geometry g = reader.read("GEOMETRYCOLLECTION("
    //          + "POLYGON((0 0, 100 0, 100 100, 0 100, 0 0)),"
    //          + "LINESTRING(50 50, 60 60))");
    //    Geometry b = reader.read("GEOMETRYCOLLECTION("
    //          + "LINESTRING(0 0, 100 0, 100 100, 0 100, 0 0))");
    //    assertEquals(b, g.getBoundary());
    //  }
    //  public void testGeometryCollectionGetBoundary3() throws Exception {
    //    Geometry g = reader.read("GEOMETRYCOLLECTION("
    //          + "POLYGON((0 0, 100 0, 100 100, 0 100, 0 0)),"
    //          + "LINESTRING(50 50, 150 50))");
    //    Geometry b = reader.read("GEOMETRYCOLLECTION("
    //          + "LINESTRING(0 0, 100 0, 100 100, 0 100, 0 0),"
    //          + "POINT(150 50))");
    //    assertEquals(b, g.getBoundary());
    //  }
    fun testCoordinateNaN() {
        val c1 = Coordinate()
        assertTrue(!Math.isNaN(c1.x))
        assertTrue(!Math.isNaN(c1.y))
        assertTrue(Math.isNaN(c1.z))
        val c2 = Coordinate(3.0, 4.0)
        org.locationtech.kts.assertEquals(3.0, c2.x, 1E-10)
        org.locationtech.kts.assertEquals(4.0, c2.y, 1E-10)
        assertTrue(Math.isNaN(c2.z))
        assertEquals(c1, c1)
        assertEquals(c2, c2)
        assertTrue(!c1.equals(c2))
        assertEquals(Coordinate(), Coordinate(0.0, 0.0))
        assertEquals(Coordinate(3.0, 5.0), Coordinate(3.0, 5.0))
        assertEquals(Coordinate(3.0, 5.0, Double.NaN), Coordinate(3.0, 5.0, Double.NaN))
        assertTrue(Coordinate(3.0, 5.0, 0.0).equals(Coordinate(3.0, 5.0, Double.NaN)))
    }

    fun testPredicatesReturnFalseForEmptyGeometries() {
        val p1 = GeometryFactory().createPoint(null as Coordinate?)
        val p2 = GeometryFactory().createPoint(Coordinate(5.0, 5.0))
        assertEquals(false, p1.equals(p2))
        assertEquals(true, p1.disjoint(p2))
        assertEquals(false, p1.intersects(p2))
        assertEquals(false, p1.touches(p2))
        assertEquals(false, p1.crosses(p2))
        assertEquals(false, p1.within(p2))
        assertEquals(false, p1.contains(p2))
        assertEquals(false, p1.overlaps(p2))
        assertEquals(false, p2.equals(p1))
        assertEquals(true, p2.disjoint(p1))
        assertEquals(false, p2.intersects(p1))
        assertEquals(false, p2.touches(p1))
        assertEquals(false, p2.crosses(p1))
        assertEquals(false, p2.within(p1))
        assertEquals(false, p2.contains(p1))
        assertEquals(false, p2.overlaps(p1))
    }
}