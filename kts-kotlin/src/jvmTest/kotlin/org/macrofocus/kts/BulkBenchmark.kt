package org.macrofocus.kts

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.prep.PreparedGeometry
import org.locationtech.jts.index.strtree.STRtree
import kotlin.random.Random
import kotlin.test.Test

/** Timings of the bulk operations; run with ./gradlew :kts-kotlin:jvmTest -Pkts.benchmark=true --tests '*Benchmark*' */
class BulkBenchmark {
    private val enabled = System.getProperty("kts.benchmark") == "true"

    private fun time(label: String, rounds: Int = 3, block: () -> Long) {
        var checksum = 0L
        checksum += block() // warm-up
        val start = System.nanoTime()
        repeat(rounds) { checksum += block() }
        println("%-52s %9.1f ms  (%d)".format(label, (System.nanoTime() - start) / 1e6 / rounds, checksum))
    }

    /** A 50 x 50 grid of cells of 10 x 10, each side cut in 50 segments: 2,500 polygons of 200 vertices. */
    private val cells: List<Polygon> by lazy {
        (0 until 50).flatMap { i ->
            (0 until 50).map { j ->
                geometry {
                    polygon {
                        val x0 = i * 10.0
                        val y0 = j * 10.0
                        for (k in 0 until 50) xy(x0 + k * 0.2, y0)
                        for (k in 0 until 50) xy(x0 + 10, y0 + k * 0.2)
                        for (k in 0 until 50) xy(x0 + 10 - k * 0.2, y0 + 10)
                        for (k in 0 until 50) xy(x0, y0 + 10 - k * 0.2)
                    }
                }
            }
        }
    }

    @Test
    fun regions() {
        if (!enabled) return
        val random = Random(1)
        val n = 1_000_000
        val xs = DoubleArray(n) { random.nextDouble(-10.0, 510.0) }
        val ys = DoubleArray(n) { random.nextDouble(-10.0, 510.0) }
        println("--- $n points among ${cells.size} polygons of 200 vertices")
        class Place(val polygon: Polygon) {
            val prepared: PreparedGeometry by lazy { polygon.prepare() }
        }
        val places = cells.map { Place(it) }
        val str = STRtree().apply { places.forEach { insert(it.polygon.envelopeInternal, it) } }
        val factory = DefaultGeometryFactory
        time("STRtree + PreparedGeometry.intersects(Point)", rounds = 1) {
            var found = 0L
            for (i in 0 until n) {
                @Suppress("UNCHECKED_CAST")
                val candidates = str.query(envelope(xs[i], ys[i], xs[i], ys[i])) as List<Place>
                val point = factory.createPoint(Coordinate(xs[i], ys[i]))
                if (candidates.any { it.prepared.intersects(point) }) found++
            }
            found
        }
        val locator = cells.toRegionLocator()
        time("RegionLocator.locate") {
            var found = 0L
            for (i in 0 until n) if (locator.locate(xs[i], ys[i]) != null) found++
            found
        }
        time("RegionLocator.locateAll (concurrent)") { runBlocking { locator.locateAll(xs, ys).count { it != null }.toLong() } }
    }

    @Test
    fun geoJson() {
        if (!enabled) return
        val text = GeoJson.write(cells.mapIndexed { i, c -> Feature(c, mapOf("id" to i, "name" to "cell $i")) })
        println("--- GeoJSON of ${cells.size} features, ${text.length / 1_000_000.0} MB")
        time("kotlinx Json.parseToJsonElement (no geometry)") { (Json.parseToJsonElement(text) as JsonObject).size.toLong() }
        time("GeoJson.readFeatures") { GeoJson.readFeatures(text).size.toLong() }
        val packed = geometryFactory(storage = CoordinateStorage.PackedDouble)
        time("GeoJson.readFeatures, packed") { GeoJson.readFeatures(text, packed).size.toLong() }
        time("GeoJson.write") { GeoJson.write(cells.map { Feature(it) }).length.toLong() }
    }

    @Test
    fun bulk() {
        if (!enabled) return
        val random = Random(2)
        val points = List(200_000) { geometry { point(random.nextDouble(0.0, 500.0), random.nextDouble(0.0, 500.0)) } }
        println("--- join of ${points.size} points and ${cells.size} polygons; union and paths of the polygons")
        time("spatialJoin") { spatialJoin(points, cells, SpatialPredicate.Within).size.toLong() }
        time("spatialJoinConcurrently") {
            runBlocking { spatialJoinConcurrently(points, cells, SpatialPredicate.Within, leftGeometry = { it }, rightGeometry = { it }).size.toLong() }
        }
        time("unionAll", rounds = 1) { cells.unionAll()!!.numPoints.toLong() }
        time("unionAllConcurrently", rounds = 1) { runBlocking { cells.unionAllConcurrently()!!.numPoints.toLong() } }
        val view = ScaleTranslate.fit(cells.envelope(), 1000.0, 1000.0)
        time("toPackedPath, sequential") { cells.sumOf { it.toPackedPath(view, tolerance = 0.5).commandCount.toLong() } }
        time("toPackedPaths (concurrent)") { runBlocking { cells.toPackedPaths(view, tolerance = 0.5).sumOf { it.commandCount.toLong() } } }
        time("toPackedPath, no decimation") { cells.sumOf { it.toPackedPath(view).commandCount.toLong() } }
    }
}
