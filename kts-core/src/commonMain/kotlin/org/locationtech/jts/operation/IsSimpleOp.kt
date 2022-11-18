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
package org.locationtech.jts.operation

import org.locationtech.jts.algorithm.BoundaryNodeRule
import org.locationtech.jts.algorithm.LineIntersector
import org.locationtech.jts.algorithm.RobustLineIntersector
import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.util.LinearComponentExtracter
import org.locationtech.jts.geomgraph.Edge
import org.locationtech.jts.geomgraph.EdgeIntersection
import org.locationtech.jts.geomgraph.GeometryGraph
import org.locationtech.jts.legacy.TreeSet
import org.locationtech.jts.legacy.map.TreeMap

/**
 * Tests whether a `Geometry` is simple.
 * In general, the SFS specification of simplicity
 * follows the rule:
 *
 *  *  A Geometry is simple if and only if the only self-intersections are at
 * boundary points.
 *
 *
 * Simplicity is defined for each [Geometry] type as follows:
 *
 *  * **Polygonal** geometries are simple by definition, so
 * `isSimple` trivially returns true.
 * (Note: this means that <tt>isSimple</tt> cannot be used to test
 * for (invalid) self-intersections in <tt>Polygon</tt>s.
 * In order to check if a <tt>Polygonal</tt> geometry has self-intersections,
 * use [Geometry.isValid]).
 *  * **Linear** geometries are simple if they do *not* self-intersect at interior points
 * (i.e. points other than boundary points).
 * This is equivalent to saying that no two linear components satisfy the SFS [Geometry.touches]
 * predicate.
 *  * **Zero-dimensional (point)** geometries are simple if and only if they have no
 * repeated points.
 *  * **Empty** geometries are *always* simple, by definition
 *
 * For [Lineal] geometries the evaluation of simplicity
 * can be customized by supplying a [BoundaryNodeRule]
 * to define how boundary points are determined.
 * The default is the SFS-standard [BoundaryNodeRule.MOD2_BOUNDARY_RULE].
 * Note that under the <tt>Mod-2</tt> rule, closed <tt>LineString</tt>s (rings)
 * will never satisfy the <tt>touches</tt> predicate at their endpoints, since these are
 * interior points, not boundary points.
 * If it is required to test whether a set of `LineString`s touch
 * only at their endpoints, use `IsSimpleOp` with [BoundaryNodeRule.ENDPOINT_BOUNDARY_RULE].
 * For example, this can be used to validate that a set of lines form a topologically valid
 * linear network.
 *
 * @see BoundaryNodeRule
 *
 * @version 1.7
 *
 */
@Deprecated("Replaced by org.locationtech.jts.operation.valid.IsSimpleOp")
class IsSimpleOp {
    private var inputGeom: Geometry? = null
    private var isClosedEndpointsInInterior = true

    /**
     * Gets a coordinate for the location where the geometry
     * fails to be simple.
     * (i.e. where it has a non-boundary self-intersection).
     * [.isSimple] must be called before this method is called.
     *
     * @return a coordinate for the location of the non-boundary self-intersection
     * or null if the geometry is simple
     */
    var nonSimpleLocation: Coordinate? = null
        private set

    /**
     * Creates a simplicity checker using the default SFS Mod-2 Boundary Node Rule
     *
     */
    @Deprecated("use IsSimpleOp(Geometry)")
    constructor()

    /**
     * Creates a simplicity checker using the default SFS Mod-2 Boundary Node Rule
     *
     * @param geom the geometry to test
     */
    constructor(geom: Geometry?) {
        inputGeom = geom
    }

    /**
     * Creates a simplicity checker using a given [BoundaryNodeRule]
     *
     * @param geom the geometry to test
     * @param boundaryNodeRule the rule to use.
     */
    constructor(geom: Geometry?, boundaryNodeRule: BoundaryNodeRule) {
        inputGeom = geom
        isClosedEndpointsInInterior = !boundaryNodeRule.isInBoundary(2)
    }

    /**
     * Tests whether the geometry is simple.
     *
     * @return true if the geometry is simple
     */
    val isSimple: Boolean
        get() {
            nonSimpleLocation = null
            return computeSimple(inputGeom)
        }

    private fun computeSimple(geom: Geometry?): Boolean {
        nonSimpleLocation = null
        if (geom!!.isEmpty) return true
        if (geom is LineString) return isSimpleLinearGeometry(geom)
        if (geom is MultiLineString) return isSimpleLinearGeometry(geom)
        if (geom is MultiPoint) return isSimpleMultiPoint(geom)
        return if (geom is Polygonal) isSimplePolygonal(geom) else (geom as? GeometryCollection)?.let {
            isSimpleGeometryCollection(
                it
            )
        } ?: true
        // all other geometry types are simple by definition
    }

    /**
     * Reports whether a [LineString] is simple.
     *
     * @param geom the lineal geometry to test
     * @return true if the geometry is simple
     */
    @Deprecated("use isSimple()")
    fun isSimple(geom: LineString): Boolean {
        return isSimpleLinearGeometry(geom)
    }

    /**
     * Reports whether a [MultiLineString] geometry is simple.
     *
     * @param geom the lineal geometry to test
     * @return true if the geometry is simple
     */
    @Deprecated("use isSimple()")
    fun isSimple(geom: MultiLineString): Boolean {
        return isSimpleLinearGeometry(geom)
    }

    /**
     * A MultiPoint is simple if it has no repeated points
     */
    @Deprecated("use isSimple()")
    fun isSimple(mp: MultiPoint): Boolean {
        return isSimpleMultiPoint(mp)
    }

    private fun isSimpleMultiPoint(mp: MultiPoint): Boolean {
        if (mp.isEmpty) return true
        val points: MutableSet<Coordinate> = TreeSet()
        for (i in 0 until mp.numGeometries) {
            val pt = mp.getGeometryN(i) as Point
            val p = pt.coordinate
            if (points.contains(p)) {
                nonSimpleLocation = p
                return false
            }
            points.add(p!!)
        }
        return true
    }

    /**
     * Computes simplicity for polygonal geometries.
     * Polygonal geometries are simple if and only if
     * all of their component rings are simple.
     *
     * @param geom a Polygonal geometry
     * @return true if the geometry is simple
     */
    private fun isSimplePolygonal(geom: Geometry): Boolean {
        val rings: List<Geometry> = LinearComponentExtracter.getLines(geom)
        val i: Iterator<*> = rings.iterator()
        while (i.hasNext()) {
            val ring = i.next() as LinearRing
            if (!isSimpleLinearGeometry(ring)) return false
        }
        return true
    }

    /**
     * Semantics for GeometryCollection is
     * simple if all components are simple.
     *
     * @param geom
     * @return true if the geometry is simple
     */
    private fun isSimpleGeometryCollection(geom: Geometry): Boolean {
        for (i in 0 until geom.numGeometries) {
            val comp = geom.getGeometryN(i)
            if (!computeSimple(comp)) return false
        }
        return true
    }

    private fun isSimpleLinearGeometry(geom: Geometry): Boolean {
        if (geom.isEmpty) return true
        val graph = GeometryGraph(0, geom)
        val li: LineIntersector = RobustLineIntersector()
        val si = graph.computeSelfNodes(li, true)
        // if no self-intersection, must be simple
        if (!si.hasIntersection()) return true
        if (si.hasProperIntersection()) {
            nonSimpleLocation = si.properIntersectionPoint
            return false
        }
        if (hasNonEndpointIntersection(graph)) return false
        if (isClosedEndpointsInInterior) {
            if (hasClosedEndpointIntersection(graph)) return false
        }
        return true
    }

    /**
     * For all edges, check if there are any intersections which are NOT at an endpoint.
     * The Geometry is not simple if there are intersections not at endpoints.
     */
    private fun hasNonEndpointIntersection(graph: GeometryGraph): Boolean {
        val i: Iterator<*> = graph.getEdgeIterator()
        while (i.hasNext()) {
            val e = i.next() as Edge
            val maxSegmentIndex = e.getMaximumSegmentIndex()
            val eiIt = e.getEdgeIntersectionList().iterator()
            while (eiIt.hasNext()) {
                val ei = eiIt.next() as EdgeIntersection
                if (!ei.isEndPoint(maxSegmentIndex)) {
                    nonSimpleLocation = ei.getCoordinate()
                    return true
                }
            }
        }
        return false
    }

    private class EndpointInfo(var coordinate: Coordinate?) {
        var isClosed = false
        var degree = 0
        fun addEndpoint(isClosed: Boolean) {
            degree++
            this.isClosed = this.isClosed or isClosed
        }
    }

    /**
     * Tests that no edge intersection is the endpoint of a closed line.
     * This ensures that closed lines are not touched at their endpoint,
     * which is an interior point according to the Mod-2 rule
     * To check this we compute the degree of each endpoint.
     * The degree of endpoints of closed lines
     * must be exactly 2.
     */
    private fun hasClosedEndpointIntersection(graph: GeometryGraph): Boolean {
        val endPoints: MutableMap<Coordinate, EndpointInfo?> = TreeMap()
        run {
            val i: Iterator<*> = graph.getEdgeIterator()
            while (i.hasNext()) {
                val e = i.next() as Edge
                val isClosed = e.isClosed()
                val p0 = e.getCoordinate(0)
                addEndpoint(endPoints, p0, isClosed)
                val p1 = e.getCoordinate(e.getNumPoints() - 1)
                addEndpoint(endPoints, p1, isClosed)
            }
        }
        val i: Iterator<*> = endPoints.values.iterator()
        while (i.hasNext()) {
            val eiInfo = i.next() as EndpointInfo
            if (eiInfo.isClosed && eiInfo.degree != 2) {
                nonSimpleLocation = eiInfo.coordinate
                return true
            }
        }
        return false
    }

    /**
     * Add an endpoint to the map, creating an entry for it if none exists
     */
    private fun addEndpoint(endPoints: MutableMap<Coordinate, EndpointInfo?>, p: Coordinate, isClosed: Boolean) {
        var eiInfo = endPoints[p]
        if (eiInfo == null) {
            eiInfo = EndpointInfo(p)
            endPoints[p] = eiInfo
        }
        eiInfo.addEndpoint(isClosed)
    }
}