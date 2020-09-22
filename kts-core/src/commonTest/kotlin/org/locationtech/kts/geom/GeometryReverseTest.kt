package org.locationtech.kts.geom

import org.locationtech.jts.geom.*
import org.locationtech.jts.legacy.Math
import test.kts.GeometryTestCase
import test.kts.GeometryTestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeometryReverseTest : GeometryTestCase() {
    @Test
    fun testReverse() {
        checkReverse(read(GeometryTestData.WKT_POINT))
        checkReverse(read(GeometryTestData.WKT_LINESTRING))
        checkReverse(read(GeometryTestData.WKT_LINEARRING))
        checkReverse(read(GeometryTestData.WKT_POLY))
        checkReverse(read(GeometryTestData.WKT_MULTIPOINT))
        checkReverse(read(GeometryTestData.WKT_MULTILINESTRING))
        checkReverse(read(GeometryTestData.WKT_MULTIPOLYGON))
        checkReverse(read(GeometryTestData.WKT_GC))
    }

    private fun checkReverse(g: Geometry) {
        val SRID = 123
        g.SRID = SRID

        //User data left out for now
        //Object DATA = new Integer(999);
        //g.setUserData(DATA);
        val reverse = g.reverse()
        assertTrue(g.geometryType === reverse.geometryType, g.geometryType + ": Geometry types are not the same")
        assertEquals(g.SRID, reverse.SRID, g.geometryType + ": Geometry.getSRID() values are not the same")
        assertTrue(checkSequences(g, reverse), g.geometryType + ": Sequences are not opposite")
    }

    private fun checkSequences(g1: Geometry, g2: Geometry): Boolean {
        val numGeometries = g1.numGeometries
        if (numGeometries != g2.numGeometries) return false
        for (i in 0 until numGeometries) {
            val gt1 = g1.getGeometryN(i)
            val gt2 = g2.getGeometryN(i)
            if (gt1.geometryType !== gt2.geometryType) return false
            if (gt1 is Point) {
                if (!checkSequences(gt1.coordinateSequence, (gt2 as Point).coordinateSequence)) return false
            } else if (gt1 is LineString) {
                if (!checkSequences(gt1.coordinateSequence, (gt2 as LineString).coordinateSequence)) return false
            } else if (gt1 is Polygon) {
                val pt1 = gt1
                val pt2 = gt2 as Polygon
                if (!checkSequences(
                        pt1.exteriorRing!!.coordinateSequence,
                        pt2.exteriorRing!!.coordinateSequence
                    )
                ) return false
                for (k in 0 until pt1.getNumInteriorRing()) {
                    if (!checkSequences(
                            pt1.getInteriorRingN(k)!!.coordinateSequence,
                            pt2.getInteriorRingN(k)!!.coordinateSequence
                        )
                    ) return false
                }
            } else {
                return false
            }
        }
        return true
    }

    private fun checkSequences(c1: CoordinateSequence?, c2: CoordinateSequence?): Boolean {
        if (c1!!.size() != c2!!.size()) return false
        if (c1.getDimension() != c2.getDimension()) return false
        if (c1.measures != c2.measures) return false
        for (i in 0 until c1.size()) {
            val j = c1.size() - i - 1
            for (k in 0 until c1.getDimension()) if (c1.getOrdinate(i, k) != c2.getOrdinate(
                    j,
                    k
                )
            ) if (!(Math.isNaN(
                    c1.getOrdinate(i, k)
                ) && Math.isNaN(c2.getOrdinate(j, k)))
            ) return false
        }
        return true
    }
}