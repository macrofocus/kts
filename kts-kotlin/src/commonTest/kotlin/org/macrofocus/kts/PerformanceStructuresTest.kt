package org.macrofocus.kts

import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Location
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PerformanceStructuresTest {
    private fun randomBoxes(random: Random, n: Int): List<Envelope> = List(n) {
        val x = random.nextDouble(0.0, 1000.0)
        val y = random.nextDouble(0.0, 1000.0)
        envelope(x, y, x + random.nextDouble(0.0, 20.0), y + random.nextDouble(0.0, 20.0))
    }

    @Test
    fun packedRTreeFindsWhatABruteForceFinds() {
        val random = Random(42)
        for (n in listOf(0, 1, 5, 16, 17, 300, 5000)) {
            val boxes = randomBoxes(random, n)
            val tree = boxes.indices.toList().toPackedRTree { boxes[it] }
            assertEquals(n, tree.size)
            repeat(200) {
                val query = randomBoxes(random, 1)[0].apply { expandBy(random.nextDouble(0.0, 50.0)) }
                val expected = boxes.indices.filter { boxes[it].intersects(query) }.toSet()
                val found = tree.query(query)
                assertEquals(found.size, found.toSet().size, "no duplicates")
                assertEquals(expected, found.toSet(), "n = $n, query = $query")
            }
        }
    }

    @Test
    fun packedRTreeNeighborsAreTheClosest() {
        val random = Random(7)
        val points = List(2000) { random.nextDouble(0.0, 100.0) to random.nextDouble(0.0, 100.0) }
        val tree = PackedRTree.Builder<Int>().apply {
            points.forEachIndexed { i, (x, y) -> add(x, y, x, y, i) }
        }.build()
        repeat(50) {
            val x = random.nextDouble(0.0, 100.0)
            val y = random.nextDouble(0.0, 100.0)
            fun d(i: Int) = (points[i].first - x).let { it * it } + (points[i].second - y).let { it * it }
            val expected = points.indices.sortedBy { d(it) }.take(10).map { d(it) }
            assertEquals(expected, tree.neighbors(x, y, maxResults = 10).map { d(it) })
            val within = tree.neighbors(x, y, maxDistance = 5.0)
            assertEquals(points.indices.count { d(it) <= 25.0 }, within.size)
            assertTrue(tree.neighbors(x, y, maxResults = 3, filter = { it % 2 == 0 }).all { it % 2 == 0 })
        }
    }

    @Test
    fun pointLocatorAgreesWithJts() {
        val random = Random(3)
        val shape = geometry {
            multiPolygon {
                polygon {
                    for (i in 0 until 200) {
                        val angle = 2 * kotlin.math.PI * i / 200
                        val r = 40 + random.nextDouble(-10.0, 10.0)
                        xy(50 + r * kotlin.math.cos(angle), 50 + r * kotlin.math.sin(angle))
                    }
                    hole { xy(40, 40); xy(60, 40); xy(60, 60); xy(40, 60) }
                }
                box(120.0, 0.0, 130.0, 10.0)
            }
        }
        assertTrue(shape.isValid)
        val locator = shape.pointLocator()
        val jts = org.locationtech.jts.algorithm.locate.IndexedPointInAreaLocator(shape)
        val probe = org.locationtech.jts.geom.Coordinate()
        repeat(20_000) {
            probe.x = random.nextDouble(-10.0, 140.0)
            probe.y = random.nextDouble(-10.0, 110.0)
            assertEquals(jts.locate(probe), locator.locate(probe.x, probe.y), "at $probe")
        }
        // Vertices, edges and the corners of the hole are on the boundary
        assertEquals(Location.BOUNDARY, locator.locate(40.0, 40.0))
        assertEquals(Location.BOUNDARY, locator.locate(50.0, 40.0))
        assertEquals(Location.BOUNDARY, locator.locate(125.0, 10.0))
        assertEquals(Location.EXTERIOR, locator.locate(50.0, 50.0))
        assertTrue(locator.contains(125.0, 5.0))
    }
}
