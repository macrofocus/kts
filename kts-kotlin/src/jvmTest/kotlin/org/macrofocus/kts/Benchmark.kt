package org.macrofocus.kts

import org.locationtech.jts.algorithm.locate.IndexedPointInAreaLocator
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.index.ItemVisitor
import org.locationtech.jts.index.hprtree.HPRtree
import org.locationtech.jts.index.strtree.STRtree
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test

/** Timings against kts-core's own structures; run with ./gradlew :kts-kotlin:jvmTest -Pkts.benchmark=true */
class Benchmark {
    private val enabled = System.getProperty("kts.benchmark") == "true"

    private fun time(label: String, rounds: Int = 5, block: () -> Long) {
        var checksum = 0L
        repeat(2) { checksum += block() } // warm-up
        val start = System.nanoTime()
        repeat(rounds) { checksum += block() }
        println("%-46s %9.2f ms  (%d)".format(label, (System.nanoTime() - start) / 1e6 / rounds, checksum))
    }

    @Test
    fun spatialIndexes() {
        if (!enabled) return
        val random = Random(1)
        val n = 1_000_000
        val boxes = DoubleArray(n * 4)
        val envelopes = Array(n) {
            val x = random.nextDouble(0.0, 10_000.0)
            val y = random.nextDouble(0.0, 10_000.0)
            val w = random.nextDouble(0.0, 5.0)
            boxes[it * 4] = x; boxes[it * 4 + 1] = y; boxes[it * 4 + 2] = x + w; boxes[it * 4 + 3] = y + w
            envelope(x, y, x + w, y + w)
        }
        val queries = Array(10_000) {
            val x = random.nextDouble(0.0, 10_000.0)
            val y = random.nextDouble(0.0, 10_000.0)
            envelope(x, y, x + 50, y + 50)
        }
        println("--- $n boxes, ${queries.size} queries of 50 x 50")
        time("build STRtree") { STRtree().apply { envelopes.forEachIndexed { i, e -> insert(e, i) }; build() }.size().toLong() }
        time("build HPRtree") { HPRtree().apply { envelopes.forEachIndexed { i, e -> insert(e, i) }; build() }.size().toLong() }
        time("build PackedRTree") {
            PackedRTree.Builder<Int>(n).apply { for (i in 0 until n) add(boxes[i * 4], boxes[i * 4 + 1], boxes[i * 4 + 2], boxes[i * 4 + 3], i) }
                .build().size.toLong()
        }
        val str = STRtree().apply { envelopes.forEachIndexed { i, e -> insert(e, i) }; build() }
        val hpr = HPRtree().apply { envelopes.forEachIndexed { i, e -> insert(e, i) }; build() }
        val packed = PackedRTree.Builder<Int>(n).apply { envelopes.forEachIndexed { i, e -> add(e, i) } }.build()
        time("query STRtree (list)") { queries.sumOf { str.query(it)!!.size.toLong() } }
        time("query STRtree (visitor)") {
            var count = 0L
            val visitor = object : ItemVisitor { override fun visitItem(item: Any?) { count++ } }
            queries.forEach { str.query(it, visitor) }
            count
        }
        time("query HPRtree (list)") { queries.sumOf { hpr.query(it).size.toLong() } }
        time("query HPRtree (visitor)") {
            var count = 0L
            val visitor = object : ItemVisitor { override fun visitItem(item: Any?) { count++ } }
            queries.forEach { hpr.query(it, visitor) }
            count
        }
        time("query PackedRTree (list)") { queries.sumOf { packed.query(it).size.toLong() } }
        time("query PackedRTree (inline)") {
            var count = 0L
            for (q in queries) packed.query(q.minX, q.minY, q.maxX, q.maxY) { count++ }
            count
        }
        time("10 nearest PackedRTree") { queries.sumOf { packed.neighbors(it.minX, it.minY, 10).size.toLong() } }
    }

    @Test
    fun pointInPolygon() {
        if (!enabled) return
        val random = Random(2)
        val vertices = 20_000
        val shape = geometry {
            polygon {
                for (i in 0 until vertices) {
                    val angle = 2 * PI * i / vertices
                    val r = 1000 + 200 * sin(angle * 37) + random.nextDouble(-20.0, 20.0)
                    xy(r * cos(angle), r * sin(angle))
                }
            }
        }
        val n = 1_000_000
        val xs = DoubleArray(n) { random.nextDouble(-1300.0, 1300.0) }
        val ys = DoubleArray(n) { random.nextDouble(-1300.0, 1300.0) }
        println("--- $n points in a polygon of $vertices vertices")
        val jts = IndexedPointInAreaLocator(shape).also { it.locate(Coordinate()) }
        val locator = shape.pointLocator()
        time("IndexedPointInAreaLocator") {
            val c = Coordinate()
            var inside = 0L
            for (i in 0 until n) { c.x = xs[i]; c.y = ys[i]; if (jts.locate(c) == 0) inside++ }
            inside
        }
        time("PointLocator") {
            var inside = 0L
            for (i in 0 until n) if (locator.contains(xs[i], ys[i])) inside++
            inside
        }
        val prepared = shape.prepare()
        val factory = shape.factory
        time("PreparedGeometry.contains(Point)", rounds = 1) {
            var inside = 0L
            for (i in 0 until n) if (prepared.contains(factory.createPoint(Coordinate(xs[i], ys[i])))) inside++
            inside
        }
        time("build PointLocator") { shape.pointLocator().locate(0.0, 0.0).toLong() }
    }

    @Test
    fun construction() {
        if (!enabled) return
        val n = 2_000_000
        println("--- a line string of $n vertices")
        time("createLineString(Array<Coordinate>)") {
            DefaultGeometryFactory.createLineString(Array(n) { Coordinate(it.toDouble(), it * 0.5) }).numPoints.toLong()
        }
        time("DSL, objects") { DefaultGeometryFactory.lineString { for (i in 0 until n) xy(i.toDouble(), i * 0.5) }.numPoints.toLong() }
        val packed = geometryFactory(storage = CoordinateStorage.PackedDouble)
        time("DSL, packed") { packed.lineString { for (i in 0 until n) xy(i.toDouble(), i * 0.5) }.numPoints.toLong() }
        val line = packed.lineString { for (i in 0 until n) xy(i.toDouble(), i * 0.5) }
        time("packed: sum over coordinates (Array)") { line.coordinates.sumOf { it.x + it.y }.toLong() }
        time("packed: sum over forEachXY") { var s = 0.0; line.forEachXY { x, y -> s += x + y }; s.toLong() }
    }
}
