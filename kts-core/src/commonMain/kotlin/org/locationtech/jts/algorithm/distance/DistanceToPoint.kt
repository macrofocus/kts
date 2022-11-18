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
package org.locationtech.jts.algorithm.distance

import org.locationtech.jts.geom.*
import kotlin.jvm.JvmStatic

/**
 * Computes the Euclidean distance (L2 metric) from a [Coordinate] to a [Geometry].
 * Also computes two points on the geometry which are separated by the distance found.
 */
object DistanceToPoint {
    fun computeDistance(geom: Geometry, pt: Coordinate, ptDist: PointPairDistance) {
        if (geom is LineString) {
            computeDistance(geom, pt, ptDist)
        } else if (geom is Polygon) {
            computeDistance(geom, pt, ptDist)
        } else if (geom is GeometryCollection) {
            val gc = geom
            for (i in 0 until gc.numGeometries) {
                val g = gc.getGeometryN(i)
                computeDistance(g, pt, ptDist)
            }
        } else { // assume geom is Point
            ptDist.setMinimum(geom.coordinate!!, pt)
        }
    }

    fun computeDistance(line: LineString, pt: Coordinate, ptDist: PointPairDistance) {
        val tempSegment = LineSegment()
        val coords = line!!.coordinates
        for (i in 0 until coords.size - 1) {
            tempSegment.setCoordinates(coords[i], coords[i + 1])
            // this is somewhat inefficient - could do better
            val closestPt = tempSegment.closestPoint(pt!!)
            ptDist.setMinimum(closestPt, pt)
        }
    }

    fun computeDistance(segment: LineSegment, pt: Coordinate, ptDist: PointPairDistance) {
        val closestPt = segment.closestPoint(pt!!)
        ptDist.setMinimum(closestPt, pt)
    }

    @JvmStatic
    fun computeDistance(poly: Polygon, pt: Coordinate, ptDist: PointPairDistance) {
        computeDistance(poly.exteriorRing!!, pt, ptDist)
        for (i in 0 until poly.getNumInteriorRing()) {
            computeDistance(poly.getInteriorRingN(i), pt, ptDist)
        }
    }
}