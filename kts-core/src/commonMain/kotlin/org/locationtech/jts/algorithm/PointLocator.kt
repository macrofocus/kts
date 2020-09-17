/*
 * Copyright (c) 2016 Vivid Solutions.
 * Copyright (c) 2020 Macrofocus GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.algorithm

import org.locationtech.jts.algorithm.PointLocation.isOnLine
import org.locationtech.jts.algorithm.PointLocation.locateInRing
import org.locationtech.jts.geom.*

/**
 * Computes the topological ([Location])
 * of a single point to a [Geometry].
 * A [BoundaryNodeRule] may be specified
 * to control the evaluation of whether the point lies on the boundary or not
 * The default rule is to use the the *SFS Boundary Determination Rule*
 *
 * Notes:
 *
 *  * [LinearRing]s do not enclose any area - points inside the ring are still in the EXTERIOR of the ring.
 *
 * Instances of this class are not reentrant.
 *
 * @version 1.7
 */
class PointLocator {
    // default is to use OGC SFS rule
    private var boundaryRule =  //BoundaryNodeRule.ENDPOINT_BOUNDARY_RULE; 
        BoundaryNodeRule.OGC_SFS_BOUNDARY_RULE
    private var isIn // true if the point lies in or on any Geometry element
            = false
    private var numBoundaries // the number of sub-elements whose boundaries the point lies in
            = 0

    constructor()
    constructor(boundaryRule: BoundaryNodeRule?) {
        requireNotNull(boundaryRule) { "Rule must be non-null" }
        this.boundaryRule = boundaryRule
    }

    /**
     * Convenience method to test a point for intersection with
     * a Geometry
     * @param p the coordinate to test
     * @param geom the Geometry to test
     * @return `true` if the point is in the interior or boundary of the Geometry
     */
    fun intersects(p: Coordinate, geom: Geometry): Boolean {
        return locate(p, geom) != Location.EXTERIOR
    }

    /**
     * Computes the topological relationship ([Location]) of a single point
     * to a Geometry.
     * It handles both single-element
     * and multi-element Geometries.
     * The algorithm for multi-part Geometries
     * takes into account the SFS Boundary Determination Rule.
     *
     * @return the [Location] of the point relative to the input Geometry
     */
    fun locate(p: Coordinate, geom: Geometry): Int {
        if (geom.isEmpty) return Location.EXTERIOR
        if (geom is LineString) {
            return locateOnLineString(p, geom)
        } else if (geom is Polygon) {
            return locateInPolygon(p, geom)
        }
        isIn = false
        numBoundaries = 0
        computeLocation(p, geom)
        if (boundaryRule.isInBoundary(numBoundaries)) return Location.BOUNDARY
        return if (numBoundaries > 0 || isIn) Location.INTERIOR else Location.EXTERIOR
    }

    private fun computeLocation(p: Coordinate, geom: Geometry) {
        if (geom is Point) {
            updateLocationInfo(locateOnPoint(p, geom))
        }
        when (geom) {
            is LineString -> {
                updateLocationInfo(locateOnLineString(p, geom))
            }
            is Polygon -> {
                updateLocationInfo(locateInPolygon(p, geom))
            }
            is MultiLineString -> {
                for (i in 0 until geom.numGeometries) {
                    val l = geom.getGeometryN(i) as LineString
                    updateLocationInfo(locateOnLineString(p, l))
                }
            }
            is MultiPolygon -> {
                for (i in 0 until geom.numGeometries) {
                    val poly = geom.getGeometryN(i) as Polygon
                    updateLocationInfo(locateInPolygon(p, poly))
                }
            }
            is GeometryCollection -> {
                val geomi: Iterator<*> = GeometryCollectionIterator(geom)
                while (geomi.hasNext()) {
                    val g2 = geomi.next() as Geometry
                    if (g2 !== geom) computeLocation(p, g2)
                }
            }
        }
    }

    private fun updateLocationInfo(loc: Int) {
        if (loc == Location.INTERIOR) isIn = true
        if (loc == Location.BOUNDARY) numBoundaries++
    }

    private fun locateOnPoint(p: Coordinate, pt: Point): Int {
        // no point in doing envelope test, since equality test is just as fast
        val ptCoord = pt.coordinate
        return if (ptCoord!!.equals2D(p)) Location.INTERIOR else Location.EXTERIOR
    }

    private fun locateOnLineString(p: Coordinate, l: LineString): Int {
        // bounding-box check
        if (!l.envelopeInternal.intersects(p)) return Location.EXTERIOR
        val seq = l.coordinateSequence
        if (!l.isClosed) {
            if (p == seq!!.getCoordinate(0)
                || p == seq.getCoordinate(seq.size() - 1)
            ) {
                return Location.BOUNDARY
            }
        }
        return if (isOnLine(p, seq!!)) {
            Location.INTERIOR
        } else Location.EXTERIOR
    }

    private fun locateInPolygonRing(p: Coordinate, ring: LinearRing?): Int {
        // bounding-box check
        return if (!ring!!.envelopeInternal.intersects(p)) Location.EXTERIOR else locateInRing(
            p,
            ring.coordinates
        )
    }

    private fun locateInPolygon(p: Coordinate, poly: Polygon): Int {
        if (poly.isEmpty) return Location.EXTERIOR
        val shell = poly.exteriorRing
        val shellLoc = locateInPolygonRing(p, shell)
        if (shellLoc == Location.EXTERIOR) return Location.EXTERIOR
        if (shellLoc == Location.BOUNDARY) return Location.BOUNDARY
        // now test if the point lies in or on the holes
        for (i in 0 until poly.getNumInteriorRing()) {
            val hole = poly.getInteriorRingN(i)
            val holeLoc = locateInPolygonRing(p, hole)
            if (holeLoc == Location.INTERIOR) return Location.EXTERIOR
            if (holeLoc == Location.BOUNDARY) return Location.BOUNDARY
        }
        return Location.INTERIOR
    }
}