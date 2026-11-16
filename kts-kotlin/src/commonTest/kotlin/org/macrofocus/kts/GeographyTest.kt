package org.macrofocus.kts

import org.locationtech.jts.geom.MultiPolygon
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GeographyTest {
    @Test
    fun distances() {
        // Zurich to Geneva: 224 km
        assertEquals(224_000.0, haversineDistance(8.5417, 47.3769, 6.1432, 46.2044), 2_000.0)
        // A quarter of a meridian
        assertEquals(EARTH_RADIUS * PI / 2, haversineDistance(0.0, 0.0, 0.0, 90.0), 1e-6)
        val line = geometry { lineString { xy(0, 0); xy(0, 90) } }
        assertEquals(EARTH_RADIUS * PI / 2, line.geodesicLength(), 1e-6)
    }

    @Test
    fun areas() {
        // The northern hemisphere: 2πR²
        val hemisphere = geometry { polygon { xy(-180, 0); xy(-60, 0); xy(60, 0); xy(180, 0); xy(180, 90); xy(-180, 90) } }
        assertEquals(2 * PI * EARTH_RADIUS * EARTH_RADIUS, hemisphere.geodesicArea(), 1e3)
        // One degree square at the equator: about 12,364 km²
        val square = geometry { box(0.0, 0.0, 1.0, 1.0) }
        assertEquals(1.2364e10, square.geodesicArea(), 1e7)
        val withHole = geometry { polygon { xy(0, 0); xy(2, 0); xy(2, 2); xy(0, 2); hole { xy(0.5, 0.5); xy(1.5, 0.5); xy(1.5, 1.5); xy(0.5, 1.5) } } }
        assertEquals(geometry { box(0.0, 0.0, 2.0, 2.0) }.geodesicArea() - geometry { box(0.5, 0.5, 1.5, 1.5) }.geodesicArea(), withHole.geodesicArea(), 1.0)
    }

    @Test
    fun webMercator() {
        val out = MutableXY()
        WebMercator.transform(180.0, WebMercator.MAX_LATITUDE, out)
        assertEquals(WebMercator.HALF_SIZE, out.x, 1e-6)
        assertEquals(WebMercator.HALF_SIZE, out.y, 1e-3)
        val zurich = geometry { point(8.5417, 47.3769) }
        val back = zurich.toWebMercator().fromWebMercator()
        assertTrue(back.equalsExact(zurich, 1e-9))
        val toView = WebMercator then ScaleTranslate(1 / WebMercator.HALF_SIZE, 1.0 / WebMercator.HALF_SIZE)
        toView.transform(-180.0, 0.0, out)
        assertEquals(-1.0, out.x, 1e-12)
    }

    @Test
    fun envelopeAround() {
        val env = geoEnvelopeAround(8.0, 60.0, 111_195.0)
        assertEquals(1.0, env.maxY - 60.0, 1e-3)
        assertEquals(2.0, env.maxX - 8.0, 1e-2) // twice as wide at 60°
    }

    @Test
    fun antimeridian() {
        // A box from 170°E to 170°W, written across the antimeridian
        val fiji = geometry { polygon { xy(170, -20); xy(-170, -20); xy(-170, -10); xy(170, -10) } }
        val split = fiji.splitAtAntimeridian()
        assertIs<MultiPolygon>(split)
        assertEquals(2, split.numGeometries)
        assertTrue(split.envelopeInternal.minX >= -180 && split.envelopeInternal.maxX <= 180)
        assertEquals(200.0, split.area, 1e-9)
        val line = geometry { lineString { xy(175, 0); xy(-175, 10) } }.splitAtAntimeridian()
        assertEquals(2, line.numGeometries)
        assertEquals(10.0 * kotlin.math.sqrt(2.0), line.length, 1e-9)
        val swiss = geometry { box(6.0, 46.0, 10.0, 48.0) }
        assertSame(swiss, swiss.splitAtAntimeridian())
    }

    @Test
    fun bufferInMeters() {
        val zurich = geometry { point(8.5417, 47.3769) }
        val disc = zurich.bufferMeters(1000.0) { quadrantSegments = 32 }
        assertTrue(abs(disc.geodesicArea() - PI * 1e6) / (PI * 1e6) < 0.01, "${disc.geodesicArea()}")
        val edge = disc.coordinates!![0]
        assertEquals(1000.0, haversineDistance(8.5417, 47.3769, edge.x, edge.y), 5.0)
    }
}
