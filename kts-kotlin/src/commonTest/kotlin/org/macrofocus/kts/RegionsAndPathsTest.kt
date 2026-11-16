package org.macrofocus.kts

import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.locationtech.jts.geom.Geometry
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RegionsAndPathsTest {
    private class Region(val name: String, val geometry: Geometry?)

    private val regions = listOf(
        Region("road", geometry { lineString { xy(0, 5); xy(20, 5) } }),
        Region("west", geometry { box(0.0, 0.0, 10.0, 10.0) }),
        Region("east", geometry { box(10.0, 0.0, 20.0, 10.0) }),
        Region("donut", geometry {
            polygon {
                xy(30, 0); xy(40, 0); xy(40, 10); xy(30, 10)
                hole { xy(32, 2); xy(38, 2); xy(38, 8); xy(32, 8) }
            }
        }),
        Region("well", geometry { point(50, 5) }),
        Region("nowhere", null),
    )
    private val locator = RegionLocator(regions) { it.geometry }

    @Test
    fun locatesByPriority() {
        assertEquals("west", locator.locate(5.0, 2.0)?.name)
        assertEquals("east", locator.locate(15.0, 2.0)?.name)
        // On the shared edge: the first given
        assertEquals("west", locator.locate(10.0, 2.0)?.name)
        // Lines and points are hit within the tolerance only, and come first here
        assertEquals("west", locator.locate(5.0, 5.2)?.name)
        assertEquals("road", locator.locate(5.0, 5.2, tolerance = 0.5)?.name)
        assertNull(locator.locate(50.3, 5.0))
        assertEquals("well", locator.locate(50.3, 5.0, tolerance = 0.5)?.name)
        // Not in the hole
        assertNull(locator.locate(35.0, 5.0))
        assertEquals("donut", locator.locate(31.0, 5.0)?.name)
        assertNull(locator.locate(5.0, 2.0) { it.name != "west" })
        assertEquals("east", locator.locate(10.0, 2.0) { it.name != "west" }?.name)
        assertEquals(listOf("road", "west", "east"), locator.locateAll(10.0, 5.0, tolerance = 0.1).map { it.name })
    }

    @Test
    fun locatesConcurrentlyAndAsAFlow() = runTest {
        val random = Random(5)
        val xs = DoubleArray(10_000) { random.nextDouble(-5.0, 55.0) }
        val ys = DoubleArray(10_000) { random.nextDouble(-5.0, 15.0) }
        val sequential = xs.indices.map { locator.locate(xs[it], ys[it]) }
        assertEquals(sequential, locator.locateAll(xs, ys, chunkSize = 1000))
        val flowed = xs.indices.asFlow().locateIn(locator, x = { xs[it] }, y = { ys[it] }).toList()
        assertEquals(sequential, flowed.map { it.second })
    }

    @Test
    fun distanceTo() {
        val square = geometry { box(0.0, 0.0, 10.0, 10.0) }
        assertEquals(0.0, square.distanceTo(5.0, 5.0))
        assertEquals(3.0, square.distanceTo(13.0, 5.0))
        assertEquals(5.0, square.distanceTo(13.0, 14.0))
    }

    private class Recorder : PathSink {
        val log = StringBuilder()
        override fun moveTo(x: Double, y: Double) { log.append("M${x.toInt()},${y.toInt()} ") }
        override fun lineTo(x: Double, y: Double) { log.append("L${x.toInt()},${y.toInt()} ") }
        override fun close() { log.append("Z ") }
        override fun point(x: Double, y: Double) { log.append("P${x.toInt()},${y.toInt()} ") }
    }

    @Test
    fun emitsPaths() {
        val donut = regions[3].geometry!!
        val sink = Recorder()
        assertEquals(8, donut.emitPath(sink))
        assertEquals("M30,0 L40,0 L40,10 L30,10 Z M32,2 L38,2 L38,8 L32,8 Z ", sink.log.toString())

        // North up, 10 pixels per unit, translated
        val view = ScaleTranslate(10.0, -10.0, 0.0, 100.0)
        val line = Recorder().also { regions[0].geometry!!.emitPath(it, view) }
        assertEquals("M0,50 L200,50 ", line.log.toString())

        val points = Recorder().also { geometry { multiPoint { xy(1, 2); xy(3, 4) } }.emitPath(it) }
        assertEquals("P1,2 P3,4 ", points.log.toString())
    }

    @Test
    fun decimatesAndClips() {
        // 1000 vertices on a wiggly line, viewed at 1 pixel per unit
        val wiggle = geometry { lineString { for (i in 0 until 1000) xy(i * 0.1, if (i % 2 == 0) 0.0 else 0.05) } }
        val all = wiggle.emitPath(Recorder())
        val decimated = wiggle.emitPath(Recorder(), tolerance = 1.0)
        assertEquals(1000, all)
        assertTrue(decimated in 90..110, "$decimated")
        // The last vertex is kept
        val sink = Recorder().also { wiggle.emitPath(it, tolerance = 1.0) }
        assertTrue(sink.log.endsWith("L99,0 "), sink.log.takeLast(20).toString())
        // A hole smaller than the tolerance is left out, the shell is kept
        assertEquals(4, regions[3].geometry!!.emitPath(Recorder(), ScaleTranslate(0.1, 0.1), tolerance = 1.0))
        // Clipped away
        assertEquals(0, regions[3].geometry!!.emitPath(Recorder(), clip = envelope(0.0, 0.0, 20.0, 10.0)))
    }

    @Test
    fun recordsPathsConcurrently() = runTest {
        val geometries = regions.mapNotNull { it.geometry }
        val view = ScaleTranslate.fit(geometries.envelope(), 600.0, 200.0)
        val paths = geometries.toPackedPaths(view, chunkSize = 2)
        assertEquals(geometries.size, paths.size)
        for ((g, path) in geometries.zip(paths)) {
            val direct = Recorder().also { g.emitPath(it, view) }
            val replayed = Recorder().also { path.replay(it) }
            assertEquals(direct.log.toString(), replayed.log.toString())
        }
        val bounds = paths.map { it.bounds }.reduce { a, b -> a + b }
        assertTrue(bounds.minX >= -1e-9 && bounds.maxX <= 600 + 1e-9 && bounds.minY >= -1e-9 && bounds.maxY <= 200 + 1e-9, "$bounds")
    }
}
