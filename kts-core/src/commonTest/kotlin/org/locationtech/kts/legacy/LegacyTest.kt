package org.locationtech.kts.legacy

import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.io.WKTReader
import org.locationtech.jts.legacy.Math
import org.locationtech.jts.legacy.TreeSet
import org.locationtech.jts.triangulate.polygon.PolygonTriangulator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The legacy classes as java.util and java.lang have them, on every platform (they were TODO() off the JVM). */
class LegacyTest {
    @Test
    fun treeSetHigherAndLower() {
        val set = TreeSet(naturalOrder(), listOf(5, 1, 9, 3))
        assertEquals(listOf(1, 3, 5, 9), set.toList())
        assertEquals(5, set.higher(3))
        assertEquals(5, set.higher(4))
        assertNull(set.higher(9))
        assertEquals(1, set.lower(3))
        assertEquals(3, set.lower(4))
        assertNull(set.lower(1))
    }

    @Test
    fun roundOfNaNIsZero() {
        assertEquals(0L, Math.round(Double.NaN))
        assertEquals(3L, Math.round(2.5))
        assertEquals(-2L, Math.round(-2.5))
    }

    /** A polygon with a hole is triangulated (the hole joined to the shell with TreeSet.higher and lower; B5 of the review). */
    @Test
    fun polygonWithAHoleIsTriangulated() {
        val polygon = WKTReader(GeometryFactory()).read("POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0), (3 3, 7 3, 7 7, 3 7, 3 3))")
        val triangles = PolygonTriangulator.triangulate(polygon)
        assertEquals(8, triangles.numGeometries)
        assertEquals(polygon.area, triangles.area, 1e-9)
    }
}
