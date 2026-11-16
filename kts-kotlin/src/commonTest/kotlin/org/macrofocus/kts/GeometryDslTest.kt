package org.macrofocus.kts

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryCollection
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.MultiPolygon
import org.locationtech.jts.geom.Point
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.impl.PackedCoordinateSequence
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GeometryDslTest {
    private val square = geometry { box(0.0, 0.0, 10.0, 10.0) }

    @Test
    fun polygonWithHoleClosesItsRings() {
        val polygon = geometry {
            polygon {
                xy(0, 0); xy(10, 0); xy(10, 10); xy(0, 10)
                hole { xy(2, 2); xy(4, 2); xy(4, 4); xy(2, 4) }
            }
        }
        assertEquals("POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0), (2 2, 4 2, 4 4, 2 4, 2 2))", polygon.toWkt())
        assertEquals(96.0, polygon.area)
        assertEquals(1, polygon.holes.size)
        assertEquals(2, polygon.rings.size)
        assertTrue(polygon.isValid)
    }

    @Test
    fun matchesWkt() {
        val built = geometry {
            geometryCollection {
                point(1, 2)
                lineString { xy(0, 0); xy(1, 1) }
                multiPolygon {
                    box(0.0, 0.0, 1.0, 1.0)
                    polygon { shell { xy(2, 2); xy(3, 2); xy(3, 3) } }
                }
                +wkt("POINT (5 5)")
            }
        }
        val parsed = wkt(
            "GEOMETRYCOLLECTION (POINT (1 2), LINESTRING (0 0, 1 1), " +
                "MULTIPOLYGON (((0 0, 1 0, 1 1, 0 1, 0 0)), ((2 2, 3 2, 3 3, 2 2))), POINT (5 5))"
        )
        assertTrue(built.equalsExact(parsed), built.toWkt())
        assertIs<GeometryCollection>(built)
        assertIs<Point>(built[0])
        assertIs<LineString>(built[1])
        assertIs<MultiPolygon>(built[2])
        assertEquals(listOf("Point", "LineString", "MultiPolygon", "Point"), built.parts.map { it.geometryType })
        assertEquals(3, built.extract<Polygon>().size + built.extract<LineString>().size)
    }

    @Test
    fun packedFactoryKeepsTheArray() {
        val factory = geometryFactory(storage = CoordinateStorage.PackedDouble)
        val line = factory.lineString { ordinates(doubleArrayOf(0.0, 0.0, 3.0, 4.0)) }
        assertIs<PackedCoordinateSequence.Double>(line.coordinateSequence)
        assertEquals(5.0, line.length)
        val z = factory.lineString { xyz(0.0, 0.0, 1.0); xyz(1.0, 0.0, 2.0) }
        assertEquals("LINESTRING Z(0 0 1, 1 0 2)", z.toWkt())
    }

    @Test
    fun measures() {
        val line = geometry { lineString { xym(0.0, 0.0, 7.0); xym(1.0, 1.0, 8.0) } }
        assertEquals(8.0, line.coordinateSequence!!.getM(1))
    }

    @Test
    fun mixedDimensionsAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            geometry { lineString { xy(0, 0); xyz(1.0, 1.0, 1.0) } }
        }
    }

    @Test
    fun operators() {
        val other = square.translated(5.0, 5.0)
        assertEquals(175.0, (square + other).area, 1e-9)
        assertEquals(75.0, (square - other).area, 1e-9)
        assertEquals(25.0, (square and other).area, 1e-9)
        assertEquals(150.0, (square xor other).area, 1e-9)
        assertEquals(listOf(square, other).unionAll()!!.area, (square or other).area, 1e-9)
    }

    @Test
    fun coordinatesAndEnvelopes() {
        val (x, y) = Coordinate(1.0, 2.0) + Coordinate(3.0, 4.0)
        assertEquals(4.0 to 6.0, x to y)
        val env = envelope(0.0, 1.0, 2.0, 3.0)
        val (minX, minY, maxX, maxY) = env
        assertEquals(listOf(0.0, 1.0, 2.0, 3.0), listOf(minX, minY, maxX, maxY))
        assertTrue(Coordinate(1.0, 2.0) in env)
        assertFalse(Coordinate(5.0, 2.0) in env)
        assertEquals(envelope(0.0, 0.0, 2.0, 3.0), env + envelope(0.0, 0.0, 1.0, 1.0))
    }

    @Test
    fun forEachXYVisitsEveryVertex() {
        var sum = 0.0
        var count = 0
        square.forEachXY { x, y -> sum += x + y; count++ }
        assertEquals(5, count)
        assertEquals(40.0, sum)
    }

    @Test
    fun transformations() {
        val moved = square.transformCoordinates { x += 1; y *= 2 }
        assertEquals(envelope(1.0, 0.0, 11.0, 20.0), moved.envelopeInternal)
        assertEquals(envelope(0.0, 0.0, 10.0, 10.0), square.envelopeInternal, "the original is left alone")

        val turned = square.transform { translate(-5.0, -5.0); rotate(PI / 2); translate(5.0, 5.0) }
        assertTrue(turned.norm().equalsExact(square.norm(), 1e-9), turned.toWkt())
        assertEquals(400.0, square.scaled(2.0).area, 1e-9)
    }

    @Test
    fun bufferParameters() {
        val line = geometry { lineString { xy(0, 0); xy(10, 0) } }
        val flat = line.buffer(1.0) { endCap = EndCap.Flat }
        assertEquals(20.0, flat.area, 1e-9)
        assertTrue(line.buffer(1.0).area > flat.area)
        val circle = geometry { point(0, 0) }.buffer(1.0) { quadrantSegments = 2 }
        assertEquals(9, circle.numPoints)
    }

    @Test
    fun simplifyAndFix() {
        val bowtie = wkt("POLYGON ((0 0, 10 10, 10 0, 0 10, 0 0))")
        assertFalse(bowtie.isValid)
        assertTrue(bowtie.fixed().isValid)
        val noisy = geometry { lineString { xy(0.0, 0.0); xy(5.0, 0.01); xy(10.0, 0.0) } }
        assertEquals(2, noisy.simplify(0.1).numPoints)
    }

    @Test
    fun filterIntersecting() {
        val boxes = (0 until 10).map { i -> geometry { box(i * 2.0, 0.0, i * 2.0 + 1, 1.0) } }
        assertEquals(3, boxes.filterIntersecting(geometry { box(3.5, 0.5, 8.5, 0.6) }).size)
    }
}
