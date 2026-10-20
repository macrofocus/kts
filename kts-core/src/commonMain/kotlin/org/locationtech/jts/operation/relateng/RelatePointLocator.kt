/*
 * Copyright (c) 2024 Martin Davis.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.operation.relateng

import org.locationtech.jts.algorithm.BoundaryNodeRule
import org.locationtech.jts.algorithm.PointLocation
import org.locationtech.jts.algorithm.locate.IndexedPointInAreaLocator
import org.locationtech.jts.algorithm.locate.PointOnGeometryLocator
import org.locationtech.jts.algorithm.locate.SimplePointInAreaLocator
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryCollection
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.Location
import org.locationtech.jts.geom.MultiPolygon
import org.locationtech.jts.geom.Point
import org.locationtech.jts.geom.Polygon

/**
 * Locates a point on a geometry, including mixed-type collections.
 * The dimension of the containing geometry element is also determined.
 * GeometryCollections are handled with union semantics;
 * i.e. the location of a point is that location of that point
 * on the union of the elements of the collection.
 *
 * Union semantics for GeometryCollections has the following behaviours:
 *
 *  1. For a mixed-dimension (heterogeneous) collection
 * a point may lie on two geometry elements with different dimensions.
 * In this case the location on the largest-dimension element is reported.
 *  1. For a collection with overlapping or adjacent polygons,
 * points on polygon element boundaries may lie in the effective interior
 * of the collection geometry.
 *
 * Prepared mode is supported via cached spatial indexes.
 *
 * Supports specifying the [BoundaryNodeRule] to use
 * for line endpoints.
 *
 * @author Martin Davis
 */
class RelatePointLocator(
    private val geom: Geometry,
    private val isPrepared: Boolean,
    bnRule: BoundaryNodeRule
) {
    private val boundaryRule: BoundaryNodeRule = bnRule
    private var adjEdgeLocator: AdjacentEdgeLocator? = null
    private var points: MutableSet<Coordinate>? = null
    private var lines: MutableList<LineString>? = null
    private var polygons: MutableList<Geometry>? = null
    private var polyLocator: Array<PointOnGeometryLocator?>? = null
    private var lineBoundary: LinearBoundary? = null
    private var isEmpty = false

    constructor(geom: Geometry) : this(geom, false, BoundaryNodeRule.OGC_SFS_BOUNDARY_RULE)

    init {
        init(geom)
    }

    private fun init(geom: Geometry) {
        //-- cache empty status, since may be checked many times
        isEmpty = geom.isEmpty
        extractElements(geom)

        if (lines != null) {
            lineBoundary = LinearBoundary(lines!!, boundaryRule)
        }

        if (polygons != null) {
            polyLocator = arrayOfNulls(polygons!!.size)
        }
    }

    fun hasBoundary(): Boolean {
        return lineBoundary!!.hasBoundary()
    }

    private fun extractElements(geom: Geometry) {
        if (geom.isEmpty)
            return

        if (geom is Point) {
            addPoint(geom)
        } else if (geom is LineString) {
            addLine(geom)
        } else if (geom is Polygon
            || geom is MultiPolygon
        ) {
            addPolygonal(geom)
        } else if (geom is GeometryCollection) {
            for (i in 0 until geom.numGeometries) {
                val g = geom.getGeometryN(i)
                extractElements(g)
            }
        }
    }

    private fun addPoint(pt: Point) {
        if (points == null) {
            points = HashSet()
        }
        points!!.add(pt.coordinate!!)
    }

    private fun addLine(line: LineString) {
        if (lines == null) {
            lines = ArrayList()
        }
        lines!!.add(line)
    }

    private fun addPolygonal(polygonal: Geometry) {
        if (polygons == null) {
            polygons = ArrayList()
        }
        polygons!!.add(polygonal)
    }

    fun locate(p: Coordinate): Int {
        return DimensionLocation.location(locateWithDim(p))
    }

    /**
     * Locates a line endpoint, as a [DimensionLocation].
     * In a mixed-dim GC, the line end point may also lie in an area.
     * In this case the area location is reported.
     * Otherwise, the dimLoc is either LINE_BOUNDARY
     * or LINE_INTERIOR, depending on the endpoint valence
     * and the BoundaryNodeRule in place.
     *
     * @param p the line end point to locate
     * @return the dimension and location of the line end point
     */
    fun locateLineEndWithDim(p: Coordinate): Int {
        //-- if a GC with areas, check for point on area
        if (polygons != null) {
            val locPoly = locateOnPolygons(p, false, null)
            if (locPoly != Location.EXTERIOR)
                return DimensionLocation.locationArea(locPoly)
        }
        //-- not in area, so return line end location
        return if (lineBoundary!!.isBoundary(p))
            DimensionLocation.LINE_BOUNDARY
        else
            DimensionLocation.LINE_INTERIOR
    }

    /**
     * Locates a point which is known to be a node of the geometry
     * (i.e. a vertex or on an edge).
     *
     * @param p the node point to locate
     * @param parentPolygonal the polygon the point is a node of
     * @return the location of the node point
     */
    fun locateNode(p: Coordinate, parentPolygonal: Geometry?): Int {
        return DimensionLocation.location(locateNodeWithDim(p, parentPolygonal))
    }

    /**
     * Locates a point which is known to be a node of the geometry,
     * as a [DimensionLocation].
     *
     * @param p the point to locate
     * @param parentPolygonal the polygon the point is a node of
     * @return the dimension and location of the point
     */
    fun locateNodeWithDim(p: Coordinate, parentPolygonal: Geometry?): Int {
        return locateWithDim(p, true, parentPolygonal)
    }

    /**
     * Computes the topological location ([Location]) of a single point
     * in a Geometry, as well as the dimension of the geometry element the point
     * is located in (if not in the Exterior).
     * It handles both single-element and multi-element Geometries.
     * The algorithm for multi-part Geometries
     * takes into account the SFS Boundary Determination Rule.
     *
     * @param p the point to locate
     * @return the [Location] of the point relative to the input Geometry
     */
    fun locateWithDim(p: Coordinate): Int {
        return locateWithDim(p, false, null)
    }

    /**
     * Computes the topological location ([Location]) of a single point
     * in a Geometry, as well as the dimension of the geometry element the point
     * is located in (if not in the Exterior).
     * It handles both single-element and multi-element Geometries.
     * The algorithm for multi-part Geometries
     * takes into account the SFS Boundary Determination Rule.
     *
     * @param p the coordinate to locate
     * @param isNode whether the coordinate is a node (on an edge) of the geometry
     * @param polygon
     * @return the [Location] of the point relative to the input Geometry
     */
    private fun locateWithDim(p: Coordinate, isNode: Boolean, parentPolygonal: Geometry?): Int {
        if (isEmpty) return DimensionLocation.EXTERIOR

        /**
         * In a polygonal geometry a node must be on the boundary.
         * (This is not the case for a mixed collection, since
         * the node may be in the interior of a polygon.)
         */
        if (isNode && (geom is Polygon || geom is MultiPolygon))
            return DimensionLocation.AREA_BOUNDARY

        val dimLoc = computeDimLocation(p, isNode, parentPolygonal)
        return dimLoc
    }

    private fun computeDimLocation(p: Coordinate, isNode: Boolean, parentPolygonal: Geometry?): Int {
        //-- check dimensions in order of precedence
        if (polygons != null) {
            val locPoly = locateOnPolygons(p, isNode, parentPolygonal)
            if (locPoly != Location.EXTERIOR)
                return DimensionLocation.locationArea(locPoly)
        }
        if (lines != null) {
            val locLine = locateOnLines(p, isNode)
            if (locLine != Location.EXTERIOR)
                return DimensionLocation.locationLine(locLine)
        }
        if (points != null) {
            val locPt = locateOnPoints(p)
            if (locPt != Location.EXTERIOR)
                return DimensionLocation.locationPoint(locPt)
        }
        return DimensionLocation.EXTERIOR
    }

    private fun locateOnPoints(p: Coordinate): Int {
        if (points!!.contains(p)) {
            return Location.INTERIOR
        }
        return Location.EXTERIOR
    }

    private fun locateOnLines(p: Coordinate, isNode: Boolean): Int {
        if (lineBoundary != null
            && lineBoundary!!.isBoundary(p)
        ) {
            return Location.BOUNDARY
        }
        //-- must be on line, in interior
        if (isNode)
            return Location.INTERIOR

        //TODO: index the lines
        for (line in lines!!) {
            //-- have to check every line, since any/all may contain point
            val loc = locateOnLine(p, isNode, line)
            if (loc != Location.EXTERIOR)
                return loc
            //TODO: minor optimization - some BoundaryNodeRules can short-circuit
        }
        return Location.EXTERIOR
    }

    private fun locateOnLine(p: Coordinate, isNode: Boolean, l: LineString): Int {
        // bounding-box check
        if (!l.envelopeInternal.intersects(p))
            return Location.EXTERIOR

        val seq = l.coordinateSequence!!
        if (PointLocation.isOnLine(p, seq)) {
            return Location.INTERIOR
        }
        return Location.EXTERIOR
    }

    private fun locateOnPolygons(p: Coordinate, isNode: Boolean, parentPolygonal: Geometry?): Int {
        var numBdy = 0
        //TODO: use a spatial index on the polygons
        for (i in polygons!!.indices) {
            val loc = locateOnPolygonal(p, isNode, parentPolygonal, i)
            if (loc == Location.INTERIOR) {
                return Location.INTERIOR
            }
            if (loc == Location.BOUNDARY) {
                numBdy += 1
            }
        }
        if (numBdy == 1) {
            return Location.BOUNDARY
        }
        //-- check for point lying on adjacent boundaries
        else if (numBdy > 1) {
            if (adjEdgeLocator == null) {
                adjEdgeLocator = AdjacentEdgeLocator(geom)
            }
            return adjEdgeLocator!!.locate(p)
        }
        return Location.EXTERIOR
    }

    private fun locateOnPolygonal(p: Coordinate, isNode: Boolean, parentPolygonal: Geometry?, index: Int): Int {
        val polygonal = polygons!![index]
        if (isNode && parentPolygonal === polygonal) {
            return Location.BOUNDARY
        }
        val locator = getLocator(index)
        return locator.locate(p)
    }

    private fun getLocator(index: Int): PointOnGeometryLocator {
        var locator = polyLocator!![index]
        if (locator == null) {
            val polygonal = polygons!![index]
            locator = if (isPrepared)
                IndexedPointInAreaLocator(polygonal)
            else
                SimplePointInAreaLocator(polygonal)
            polyLocator!![index] = locator
        }
        return locator
    }
}
