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
package org.locationtech.jts.operation

import org.locationtech.jts.algorithm.BoundaryNodeRule
import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.CoordinateArrays.toCoordinateArray
import org.locationtech.jts.legacy.map.TreeMap
import kotlin.jvm.JvmOverloads

/**
 * Computes the boundary of a [Geometry].
 * Allows specifying the [BoundaryNodeRule] to be used.
 * This operation will always return a [Geometry] of the appropriate
 * dimension for the boundary (even if the input geometry is empty).
 * The boundary of zero-dimensional geometries (Points) is
 * always the empty [GeometryCollection].
 *
 * @author Martin Davis
 * @author Luc Girardin
 * @version 1.7
 */
class BoundaryOp @JvmOverloads constructor(
    private val geom: Geometry,
    bnRule: BoundaryNodeRule = BoundaryNodeRule.MOD2_BOUNDARY_RULE
) {
    private val geomFact: GeometryFactory = geom.factory
    private val bnRule: BoundaryNodeRule = bnRule

    /**
     * Gets the computed boundary.
     *
     * @return the boundary geometry
     */
    val boundary: Geometry?
        get() {
            if (geom is LineString) return boundaryLineString(geom)
            return if (geom is MultiLineString) boundaryMultiLineString(geom) else geom.boundary
        }
    private val emptyMultiPoint: MultiPoint
        private get() = geomFact.createMultiPoint()

    private fun boundaryMultiLineString(mLine: MultiLineString): Geometry? {
        if (geom.isEmpty) {
            return emptyMultiPoint
        }
        val bdyPts = computeBoundaryCoordinates(mLine)

        // return Point or MultiPoint
        return if (bdyPts.size == 1) {
            geomFact.createPoint(bdyPts[0])
        } else geomFact.createMultiPointFromCoords(bdyPts)
        // this handles 0 points case as well
    }

    /*
// MD - superseded
  private Coordinate[] computeBoundaryFromGeometryGraph(MultiLineString mLine)
  {
    GeometryGraph g = new GeometryGraph(0, mLine, bnRule);
    Coordinate[] bdyPts = g.getBoundaryPoints();
    return bdyPts;
  }
*/
    private var endpointMap: MutableMap<Coordinate, Any?>? = null
    private fun computeBoundaryCoordinates(mLine: MultiLineString): Array<Coordinate> {
        val bdyPts: MutableList<Any?> = ArrayList()
        endpointMap = TreeMap()
        for (i in 0 until mLine.numGeometries) {
            val line = mLine.getGeometryN(i) as LineString
            if (line.numPoints == 0) continue
            addEndpoint(line.getCoordinateN(0))
            addEndpoint(line.getCoordinateN(line.numPoints - 1))
        }
        val it: Iterator<*> = endpointMap!!.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next() as Map.Entry<*, *>
            val counter = entry.value as Counter
            val valence = counter.count
            if (bnRule.isInBoundary(valence)) {
                bdyPts.add(entry.key)
            }
        }
        return toCoordinateArray(bdyPts)
    }

    private fun addEndpoint(pt: Coordinate) {
        var counter = endpointMap!![pt] as Counter?
        if (counter == null) {
            counter = Counter()
            endpointMap!![pt] = counter
        }
        counter.count++
    }

    private fun boundaryLineString(line: LineString): Geometry? {
        if (geom.isEmpty) {
            return emptyMultiPoint
        }
        if (line.isClosed) {
            // check whether endpoints of valence 2 are on the boundary or not
            val closedEndpointOnBoundary = bnRule.isInBoundary(2)
            return if (closedEndpointOnBoundary) {
                line.startPoint
            } else {
                geomFact.createMultiPoint()
            }
        }
        return geomFact.createMultiPoint(
            arrayOf(
                line.startPoint!!,
                line.endPoint!!
            )
        )
    }

    companion object {
        /**
         * Computes a geometry representing the boundary of a geometry.
         *
         * @param g the input geometry
         * @return the computed boundary
         */
        fun getBoundary(g: Geometry): Geometry? {
            val bop = BoundaryOp(g)
            return bop.boundary
        }

        /**
         * Computes a geometry representing the boundary of a geometry,
         * using an explicit [BoundaryNodeRule].
         *
         * @param g the input geometry
         * @param bnRule the Boundary Node Rule to use
         * @return the computed boundary
         */
        fun getBoundary(g: Geometry, bnRule: BoundaryNodeRule): Geometry? {
            val bop = BoundaryOp(g, bnRule)
            return bop.boundary
        }
    }
    /**
     * Creates a new instance for the given geometry.
     *
     * @param geom the input geometry
     * @param bnRule the Boundary Node Rule to use
     */
}

/**
 * Stores an integer count, for use as a Map entry.
 *
 * @author Martin Davis
 * @author Luc Girardin
 * @version 1.7
 */
internal class Counter {
    /**
     * The value of the count
     */
    var count = 0
}