package org.macrofocus.kts

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.MultiPolygon
import org.locationtech.jts.geom.Point
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.impl.PackedCoordinateSequence
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeoJsonAndMoreTest {
    private val collection = """
        {"type": "FeatureCollection", "bbox": [0, 0, 10, 10], "features": [
          {"type": "Feature", "id": 7, "properties": {"name": "Zürich \"Altstadt\"", "pop": 434008, "share": 0.25,
             "tags": ["old", true, null], "meta": {"a": 1}},
           "geometry": {"coordinates": [[[0, 0], [10, 0], [10, 10], [0, 10], [0, 0]], [[2, 2], [4, 2], [4, 4], [2, 2]]],
                        "type": "Polygon"}},
          {"type": "Feature", "geometry": null, "properties": null},
          {"geometry": {"type": "LineString", "coordinates": [[1.5, -2.25e1, 100], [3E2, 4.000000000000001]]},
           "properties": {}, "type": "Feature"}
        ]}
    """.trimIndent()

    @Test
    fun readsFeatures() {
        val features = GeoJson.readFeatures(collection)
        assertEquals(3, features.size)
        val first = features[0]
        assertEquals(7L, first.id)
        assertEquals("Zürich \"Altstadt\"", first.properties["name"])
        assertEquals(434008L, first.properties["pop"])
        assertEquals(0.25, first.properties["share"])
        assertEquals(listOf("old", true, null), first.properties["tags"])
        assertEquals(mapOf("a" to 1L), first.properties["meta"])
        val polygon = assertIs<Polygon>(first.geometry)
        assertEquals(1, polygon.holes.size)
        assertEquals(98.0, polygon.area)
        assertNull(features[1].geometry)
        val line = assertIs<LineString>(features[2].geometry)
        assertEquals(-22.5, line.coordinateSequence!!.getY(0))
        assertEquals(100.0, line.coordinateSequence!!.getZ(0))
        assertEquals(4.000000000000001, line.coordinateSequence!!.getY(1))
        assertTrue(line.coordinateSequence!!.getZ(1).isNaN())
    }

    @Test
    fun readsGeometries() {
        assertIs<Point>(GeoJson.readGeometry("""{"type":"Point","coordinates":[1,2]}"""))
        assertTrue(GeoJson.readGeometry("""{"type":"Point","coordinates":[]}""")!!.isEmpty)
        val mp = GeoJson.readGeometry("""{"type":"MultiPolygon","coordinates":[[[[0,0],[1,0],[1,1],[0,0]]],[[[5,5],[6,5],[6,6],[5,5]]]]}""")
        assertIs<MultiPolygon>(mp)
        assertEquals(2, mp.numGeometries)
        val gc = GeoJson.readGeometry("""{"type":"GeometryCollection","geometries":[{"type":"Point","coordinates":[1,2]},{"type":"MultiPoint","coordinates":[[1,2],[3,4]]}]}""")!!
        assertEquals(2, gc.numGeometries)
        assertEquals(3, GeoJson.readGeometry(collection)!!.numGeometries + 1) // the null geometry is left out
        val packed = GeoJson.readGeometry("""{"type":"LineString","coordinates":[[1,2],[3,4]]}""", geometryFactory(storage = CoordinateStorage.PackedDouble))
        assertIs<PackedCoordinateSequence.Double>((packed as LineString).coordinateSequence)
        assertFailsWith<GeoJsonException> { GeoJson.readGeometry("""{"type":"Point","coordinates":[1,]}""") }
        assertFailsWith<GeoJsonException> { GeoJson.readGeometry("""{"type":"Blob","coordinates":[1,2]}""") }
    }

    @Test
    fun roundTrips() {
        val random = Random(9)
        val shapes = listOf(
            wkt("POINT (1 2)"),
            wkt("LINESTRING Z (0 0 1, 1.5 -2 2)"),
            wkt("POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0), (2 2, 4 2, 4 4, 2 2))"),
            wkt("MULTIPOINT ((1 2), (3 4))"),
            wkt("MULTILINESTRING ((0 0, 1 1), (2 2, 3 3))"),
            wkt("MULTIPOLYGON (((0 0, 1 0, 1 1, 0 0)), ((5 5, 6 5, 6 6, 5 5)))"),
            wkt("GEOMETRYCOLLECTION (POINT (1 2), LINESTRING (0 0, 1 1))"),
            geometry { lineString { repeat(100) { xy(random.nextDouble(-180.0, 180.0), random.nextDouble(-90.0, 90.0)) } } },
        )
        for (shape in shapes) {
            val text = shape.toGeoJson()
            val back = GeoJson.readGeometry(text)!!
            assertTrue(back.equalsExact(shape), "$text -> ${back.toWkt()}")
        }
        val features = listOf(Feature(shapes[2], mapOf("name" to "a\nb", "n" to 3, "x" to 1.5, "list" to listOf(1, "two")), id = "f1"), Feature(null))
        val back = GeoJson.readFeatures(GeoJson.write(features))
        assertEquals("a\nb", back[0].properties["name"])
        assertEquals(3L, back[0].properties["n"])
        assertEquals(listOf(1L, "two"), back[0].properties["list"])
        assertEquals("f1", back[0].id)
        assertNull(back[1].geometry)
    }

    @Test
    fun streamsFeatures() = runTest {
        val big = GeoJson.write((0 until 1000).map { Feature(geometry { point(it, it) }, mapOf("i" to it)) })
        // Lazy: only the first features are parsed
        assertEquals(listOf(0L, 1L, 2L), GeoJson.features(big).take(3).map { it.properties["i"] }.toList())
        assertEquals(1000, GeoJson.featureFlow(big).toList().size)
    }

    @Test
    fun spatialJoins() = runTest {
        val random = Random(11)
        val districts = (0 until 10).flatMap { i -> (0 until 10).map { j -> geometry { box(i * 10.0, j * 10.0, i * 10.0 + 10, j * 10.0 + 10) } } }
        val points = List(2000) { geometry { point(random.nextDouble(0.0, 100.0), random.nextDouble(0.0, 100.0)) } }
        val pairs = spatialJoin(points, districts, SpatialPredicate.Within)
        assertEquals(points.size, pairs.size) // no point on an edge with these random numbers
        assertTrue(pairs.all { (p, d) -> d.covers(p) })
        val concurrent = spatialJoinConcurrently(points, districts, SpatialPredicate.Within, chunkSize = 100, leftGeometry = { it }, rightGeometry = { it })
        assertEquals(pairs, concurrent)
        val flowed = spatialJoinFlow(points, districts, SpatialPredicate.Within, chunkSize = 100, leftGeometry = { it }, rightGeometry = { it }).toList()
        assertEquals(pairs.toSet(), flowed.toSet())
        // Boxes touching their neighbours: 100 boxes, 180 shared edges, 162 shared corners (both directions, plus itself)
        val touching = spatialJoin(districts, districts, SpatialPredicate.Touches)
        assertEquals(2 * (180 + 162), touching.size)

        val trees = districts.toPackedRTree()
        var count = 0
        trees.join(points.toPackedRTree()) { _, _ -> count++ }
        assertEquals(points.size, count)
    }

    @Test
    fun concurrentUnion() = runTest {
        val tiles = (0 until 40).flatMap { i -> (0 until 40).map { j -> geometry { box(i.toDouble(), j.toDouble(), i + 1.0, j + 1.0) } } }
        val union = tiles.unionAllConcurrently(groupSize = 64)!!
        assertEquals(1600.0, union.area, 1e-9)
        assertIs<Polygon>(union)
        assertEquals(0.0, union.shapeDistance(tiles.unionAll()!!), 1e-9)
    }

    @Test
    fun conveniences() {
        val tagged = geometry { point(1, 2) }.withUserData("Zürich")
        assertEquals("Zürich", tagged.userData<String>())
        assertNull(tagged.userData<Int>())

        // A C shape: its centroid is outside, its label point inside
        val c = geometry { polygon { xy(0, 0); xy(10, 0); xy(10, 2); xy(2, 2); xy(2, 8); xy(10, 8); xy(10, 10); xy(0, 10) } }
        assertTrue(!c.contains(c.centroid))
        assertTrue(c.contains(c.labelPoint()!!))
        assertEquals(geometry { point(5, 0) }, geometry { lineString { xy(0, 0); xy(10, 0) } }.labelPoint())
        assertNull(geometry { polygon { } }.labelPoint())

        val square = geometry { box(0.0, 0.0, 1.0, 1.0) }
        val rotated = wkt("POLYGON ((1 1, 1 0, 0 0, 0 1, 1 1))")
        assertTrue(square.equalsNormalized(rotated))
        assertEquals(0.0, square.shapeDistance(rotated))
        assertEquals(1.0, square.shapeDistance(square.translated(1.0, 0.0)), 1e-12)
    }

    @Serializable
    private data class Document(
        @Serializable(with = GeometryGeoJsonSerializer::class) val shape: Geometry,
        @Serializable(with = GeometryWktSerializer::class) val outline: Geometry,
        @Serializable(with = EnvelopeSerializer::class) val bounds: Envelope,
    )

    @Test
    fun serializers() {
        val square = geometry { box(0.0, 0.0, 1.0, 1.0) }
        val document = Document(square, square, square.envelopeInternal)
        val json = Json.encodeToString(Document.serializer(), document)
        // The bounds print as [0.0,0.0,1.0,1.0] on the JVM and [0,0,1,1] in JavaScript
        assertTrue(
            json.startsWith(
                """{"shape":{"type":"Polygon","coordinates":[[[0,0],[1,0],[1,1],[0,1],[0,0]]]},""" +
                    """"outline":"POLYGON ((0 0, 1 0, 1 1, 0 1, 0 0))","bounds":["""
            ), json
        )
        val back = Json.decodeFromString(Document.serializer(), json)
        assertTrue(back.shape.equalsExact(square) && back.outline.equalsExact(square))
        assertEquals(square.envelopeInternal, back.bounds)
    }
}
