/*
 * Copyright (c) 2016 Vivid Solutions.
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.operation.distance

import org.locationtech.jts.algorithm.Distance.pointToSegment
import org.locationtech.jts.algorithm.Distance.segmentToSegment
import org.locationtech.jts.algorithm.PointLocator
import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.util.LinearComponentExtracter
import org.locationtech.jts.geom.util.PointExtracter
import org.locationtech.jts.geom.util.PolygonExtracter
import org.locationtech.jts.operation.distance.ConnectedElementLocationFilter.Companion.getLocations
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Find two points on two [Geometry]s which lie
 * within a given distance, or else are the nearest points
 * on the geometries (in which case this also
 * provides the distance between the geometries).
 *
 * The distance computation also finds a pair of points in the input geometries
 * which have the minimum distance between them.
 * If a point lies in the interior of a line segment,
 * the coordinate computed is a close
 * approximation to the exact point.
 *
 * Empty geometry collection components are ignored.
 *
 * The algorithms used are straightforward O(n^2)
 * comparisons.  This worst-case performance could be improved on
 * by using Voronoi techniques or spatial indexes.
 *
 * @version 1.7
 */
class DistanceOp @JvmOverloads constructor(g0: Geometry, g1: Geometry, val terminateDistance: Double = 0.0) {
    // input
    private val geom: Array<Geometry> = arrayOf(g0, g1)

    // working
    private val ptLocator = PointLocator()
    private var minDistanceLocation: Array<GeometryLocation?>? = null
    private var minDistance = Double.MAX_VALUE

    /**
     * Report the distance between the nearest points on the input geometries.
     *
     * @return the distance between the geometries
     * or 0 if either input geometry is empty
     * @throws IllegalArgumentException if either input geometry is null
     */
    fun distance(): Double {
        require(!(geom[0] == null || geom[1] == null)) { "null geometries are not supported" }
        if (geom[0].isEmpty || geom[1].isEmpty) return 0.0
        computeMinDistance()
        return minDistance
    }

    /**
     * Report the coordinates of the nearest points in the input geometries.
     * The points are presented in the same order as the input Geometries.
     *
     * @return a pair of [Coordinate]s of the nearest points
     */
    fun nearestPoints(): Array<Coordinate?> {
        computeMinDistance()
        return arrayOf(
            minDistanceLocation!![0]!!.coordinate,
            minDistanceLocation!![1]!!.coordinate
        )
    }

    /**
     *
     * @return a pair of [Coordinate]s of the nearest points
     */
    @Deprecated("renamed to nearestPoints")
    fun closestPoints(): Array<Coordinate?> {
        return nearestPoints()
    }

    /**
     * Report the locations of the nearest points in the input geometries.
     * The locations are presented in the same order as the input Geometries.
     *
     * @return a pair of [GeometryLocation]s for the nearest points
     */
    fun nearestLocations(): Array<GeometryLocation?>? {
        computeMinDistance()
        return minDistanceLocation
    }

    /**
     *
     * @return a pair of [GeometryLocation]s for the nearest points
     */
    @Deprecated("renamed to nearestLocations")
    fun closestLocations(): Array<GeometryLocation?>? {
        return nearestLocations()
    }

    private fun updateMinDistance(locGeom: Array<GeometryLocation?>, flip: Boolean) {
        // if not set then don't update
        if (locGeom[0] == null) return
        if (flip) {
            minDistanceLocation!![0] = locGeom[1]
            minDistanceLocation!![1] = locGeom[0]
        } else {
            minDistanceLocation!![0] = locGeom[0]
            minDistanceLocation!![1] = locGeom[1]
        }
    }

    private fun computeMinDistance() {
        // only compute once!
        if (minDistanceLocation != null) return
        minDistanceLocation = arrayOfNulls(2)
        computeContainmentDistance()
        if (minDistance <= terminateDistance) return
        computeFacetDistance()
    }

    private fun computeContainmentDistance() {
        val locPtPoly = arrayOfNulls<GeometryLocation>(2)
        // test if either geometry has a vertex inside the other
        computeContainmentDistance(0, locPtPoly)
        if (minDistance <= terminateDistance) return
        computeContainmentDistance(1, locPtPoly)
    }

    private fun computeContainmentDistance(polyGeomIndex: Int, locPtPoly: Array<GeometryLocation?>) {
        val polyGeom = geom[polyGeomIndex]
        // if no polygon then nothing to do
        if (polyGeom.dimension < 2) return
        val locationsIndex = 1 - polyGeomIndex
        val polys = PolygonExtracter.getPolygons(polyGeom)
        if (polys.isNotEmpty()) {
            val insideLocs = getLocations(geom[locationsIndex])
            computeContainmentDistance(insideLocs, polys, locPtPoly)
            if (minDistance <= terminateDistance) {
                // this assigment is determined by the order of the args in the computeInside call above
                minDistanceLocation!![locationsIndex] = locPtPoly[0]
                minDistanceLocation!![polyGeomIndex] = locPtPoly[1]
                return
            }
        }
    }

    private fun computeContainmentDistance(locs: List<*>, polys: List<*>, locPtPoly: Array<GeometryLocation?>) {
        for (i in locs.indices) {
            val loc = locs[i] as GeometryLocation
            for (j in polys.indices) {
                computeContainmentDistance(loc, polys[j] as Polygon, locPtPoly)
                if (minDistance <= terminateDistance) return
            }
        }
    }

    private fun computeContainmentDistance(
        ptLoc: GeometryLocation,
        poly: Polygon,
        locPtPoly: Array<GeometryLocation?>
    ) {
        val pt = ptLoc.coordinate
        // if pt is not in exterior, distance to geom is 0
        if (Location.EXTERIOR != ptLocator.locate(pt!!, poly)) {
            minDistance = 0.0
            locPtPoly[0] = ptLoc
            locPtPoly[1] = GeometryLocation(poly, pt)
            return
        }
    }

    /**
     * Computes distance between facets (lines and points)
     * of input geometries.
     *
     */
    private fun computeFacetDistance() {
        val locGeom = arrayOfNulls<GeometryLocation>(2)

        /**
         * Geometries are not wholely inside, so compute distance from lines and points
         * of one to lines and points of the other
         */
        val lines0 = LinearComponentExtracter.getLines(geom[0])
        val lines1 = LinearComponentExtracter.getLines(geom[1])
        val pts0 = PointExtracter.getPoints(geom[0])
        val pts1 = PointExtracter.getPoints(geom[1])

        // exit whenever minDistance goes LE than terminateDistance
        computeMinDistanceLines(lines0, lines1, locGeom)
        updateMinDistance(locGeom, false)
        if (minDistance <= terminateDistance) return
        locGeom[0] = null
        locGeom[1] = null
        computeMinDistanceLinesPoints(lines0, pts1, locGeom)
        updateMinDistance(locGeom, false)
        if (minDistance <= terminateDistance) return
        locGeom[0] = null
        locGeom[1] = null
        computeMinDistanceLinesPoints(lines1, pts0, locGeom)
        updateMinDistance(locGeom, true)
        if (minDistance <= terminateDistance) return
        locGeom[0] = null
        locGeom[1] = null
        computeMinDistancePoints(pts0, pts1, locGeom)
        updateMinDistance(locGeom, false)
    }

    private fun computeMinDistanceLines(lines0: List<*>, lines1: List<*>, locGeom: Array<GeometryLocation?>) {
        for (i in lines0.indices) {
            val line0 = lines0[i] as LineString
            for (j in lines1.indices) {
                val line1 = lines1[j] as LineString
                computeMinDistance(line0, line1, locGeom)
                if (minDistance <= terminateDistance) return
            }
        }
    }

    private fun computeMinDistancePoints(points0: List<*>, points1: List<*>, locGeom: Array<GeometryLocation?>) {
        for (i in points0.indices) {
            val pt0 = points0[i] as Point
            for (j in points1.indices) {
                val pt1 = points1[j] as Point
                val dist = pt0.coordinate!!.distance(pt1.coordinate!!)
                if (dist < minDistance) {
                    minDistance = dist
                    locGeom[0] = GeometryLocation(pt0, 0, pt0.coordinate)
                    locGeom[1] = GeometryLocation(pt1, 0, pt1.coordinate)
                }
                if (minDistance <= terminateDistance) return
            }
        }
    }

    private fun computeMinDistanceLinesPoints(
        lines: List<*>, points: List<*>,
        locGeom: Array<GeometryLocation?>
    ) {
        for (i in lines.indices) {
            val line = lines[i] as LineString
            for (j in points.indices) {
                val pt = points[j] as Point
                computeMinDistance(line, pt, locGeom)
                if (minDistance <= terminateDistance) return
            }
        }
    }

    private fun computeMinDistance(
        line0: LineString, line1: LineString,
        locGeom: Array<GeometryLocation?>
    ) {
        if (line0.envelopeInternal.distance(line1.envelopeInternal)
            > minDistance
        ) return
        val coord0 = line0.coordinates
        val coord1 = line1.coordinates
        // brute force approach!
        for (i in 0 until coord0.size - 1) {

            // short-circuit if line segment is far from line
            val segEnv0 = Envelope(coord0[i], coord0[i + 1])
            if (segEnv0.distance(line1.envelopeInternal) > minDistance) continue
            for (j in 0 until coord1.size - 1) {

                // short-circuit if line segments are far apart
                val segEnv1 = Envelope(coord1[j], coord1[j + 1])
                if (segEnv0.distance(segEnv1) > minDistance) continue
                val dist = segmentToSegment(
                    coord0[i], coord0[i + 1],
                    coord1[j], coord1[j + 1]
                )
                if (dist < minDistance) {
                    minDistance = dist
                    val seg0 = LineSegment(coord0[i], coord0[i + 1])
                    val seg1 = LineSegment(coord1[j], coord1[j + 1])
                    val closestPt = seg0.closestPoints(seg1)
                    locGeom[0] = GeometryLocation(line0, i, closestPt[0])
                    locGeom[1] = GeometryLocation(line1, j, closestPt[1])
                }
                if (minDistance <= terminateDistance) return
            }
        }
    }

    private fun computeMinDistance(
        line: LineString, pt: Point,
        locGeom: Array<GeometryLocation?>
    ) {
        if (line.envelopeInternal.distance(pt.envelopeInternal)
            > minDistance
        ) return
        val coord0 = line.coordinates
        val coord = pt.coordinate
        // brute force approach!
        for (i in 0 until coord0.size - 1) {
            val dist = pointToSegment(
                coord!!, coord0[i], coord0[i + 1]
            )
            if (dist < minDistance) {
                minDistance = dist
                val seg = LineSegment(coord0[i], coord0[i + 1])
                val segClosestPoint = seg.closestPoint(coord)
                locGeom[0] = GeometryLocation(line, i, segClosestPoint)
                locGeom[1] = GeometryLocation(pt, 0, coord)
            }
            if (minDistance <= terminateDistance) return
        }
    }

    companion object {
        /**
         * Compute the distance between the nearest points of two geometries.
         * @param g0 a [Geometry]
         * @param g1 another [Geometry]
         * @return the distance between the geometries
         */
        @JvmStatic
        fun distance(g0: Geometry, g1: Geometry): Double {
            val distOp = DistanceOp(g0, g1)
            return distOp.distance()
        }

        /**
         * Test whether two geometries lie within a given distance of each other.
         * @param g0 a [Geometry]
         * @param g1 another [Geometry]
         * @param distance the distance to test
         * @return true if g0.distance(g1) &lt;= distance
         */
        fun isWithinDistance(g0: Geometry, g1: Geometry, distance: Double): Boolean {
            // check envelope distance for a short-circuit negative result
            val envDist = g0.envelopeInternal.distance(g1.envelopeInternal)
            if (envDist > distance) return false

            // MD - could improve this further with a positive short-circuit based on envelope MinMaxDist
            val distOp = DistanceOp(g0, g1, distance)
            return distOp.distance() <= distance
        }

        /**
         * Compute the the nearest points of two geometries.
         * The points are presented in the same order as the input Geometries.
         *
         * @param g0 a [Geometry]
         * @param g1 another [Geometry]
         * @return the nearest points in the geometries
         */
        @JvmStatic
        fun nearestPoints(g0: Geometry, g1: Geometry): Array<Coordinate?> {
            val distOp = DistanceOp(g0, g1)
            return distOp.nearestPoints()
        }

        /**
         * Compute the the closest points of two geometries.
         * The points are presented in the same order as the input Geometries.
         *
         * @param g0 a [Geometry]
         * @param g1 another [Geometry]
         * @return the closest points in the geometries
         */
        @Deprecated("renamed to nearestPoints")
        fun closestPoints(g0: Geometry, g1: Geometry): Array<Coordinate?> {
            val distOp = DistanceOp(g0, g1)
            return distOp.nearestPoints()
        }
    }
    /**
     * Constructs a DistanceOp that computes the distance and nearest points between
     * the two specified geometries.
     * @param g0 a Geometry
     * @param g1 a Geometry
     * @param terminateDistance the distance on which to terminate the search
     */
}