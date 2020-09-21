package org.locationtech.kts.algorithm

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.io.WKTReader
import kotlin.test.Test
import kotlin.test.assertTrue

class CentroidTest {
    @Throws(Exception::class)
    @Test
    fun testCentroidMultiPolygon() {
        // Verify that the computed centroid of a MultiPolygon is equivalent to the
        // area-weighted average of its components.
        val g = WKTReader(allowOldJtsCoordinateSyntax = false).read(
            "MULTIPOLYGON ((( -92.661322 36.58994900000003, -92.66132199999993 36.58994900000005, -92.66132199999993 36.589949000000004, -92.661322 36.589949, -92.661322 36.58994900000003)), (( -92.65560500000008 36.58708800000005, -92.65560499999992 36.58708800000005, -92.65560499998745 36.587087999992576, -92.655605 36.587088, -92.65560500000008 36.58708800000005 )), (( -92.65512450000065 36.586800000000466, -92.65512449999994 36.58680000000004, -92.65512449998666 36.5867999999905, -92.65512450000065 36.586800000000466 )))"
        )!!
        assertTrue(areaWeightedCentroid(g).equals2D(g.centroid.coordinate!!, TOLERANCE))
    }

    companion object {
        private const val TOLERANCE = 1e-10

        /** Compute the centroid of a geometry as an area-weighted average of the centroids
         * of its components.
         *
         * @param g a polygonal geometry
         * @return Coordinate of the geometry's centroid
         */
        private fun areaWeightedCentroid(g: Geometry): Coordinate {
            val totalArea = g.area
            var cx = 0.0
            var cy = 0.0
            for (i in 0 until g.numGeometries) {
                val component = g.getGeometryN(i)
                val areaFraction = component.area / totalArea
                val componentCentroid = component.centroid.coordinate
                cx += areaFraction * componentCentroid!!.x
                cy += areaFraction * componentCentroid.y
            }
            return Coordinate(cx, cy)
        }
    }
}