/*
 * Copyright (c) 2022 Martin Davis.
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.simplify

import org.locationtech.jts.algorithm.Area.ofRing
import org.locationtech.jts.geom.*
import org.locationtech.jts.legacy.Math.abs
import org.locationtech.jts.legacy.Math.ceil
import org.locationtech.jts.math.MathUtil.clamp
import kotlin.jvm.JvmStatic

/**
 * Computes topology-preserving simplified hulls of polygonal geometry.
 * Both outer and inner hulls can be computed.
 * Outer hulls contain the input geometry and are larger in area.
 * Inner hulls are contained by the input geometry and are smaller in area.
 * In both the hull vertices are a subset of the input vertices.
 * The hull construction attempts to minimize the area difference
 * with the input geometry.
 * Hulls are generally concave if the input is.
 * Computed hulls are topology-preserving:
 * they do not contain any self-intersections or overlaps,
 * so the result polygonal geometry is valid.
 *
 * Polygons with holes and MultiPolygons are supported.
 * The result has the same geometric type and structure as the input.
 *
 * The number of vertices in the computed hull is determined by a target parameter.
 * Two parameters are supported:
 *
 *  1. **Vertex Number fraction:** the fraction of the input vertices retained in the result.
 * Value 1 produces the original geometry.
 * Smaller values produce less concave results.
 * For outer hulls, value 0 produces the convex hull (with triangles for any holes).
 * For inner hulls, value 0 produces a triangle (if no holes are present).
 *
 *  1. **Area Delta ratio:** the ratio of the change in area to the input area.
 * Value 0 produces the original geometry.
 * Larger values produce less concave results.
 *
 * The algorithm ensures that the result does not cause the target parameter
 * to be exceeded.  This allows computing outer or inner hulls
 * with a small area delta ratio as an effective way of removing
 * narrow gores and spikes.
 *
 * @author Martin Davis
 */
class PolygonHullSimplifier(private val inputGeom: Geometry, isOuter: Boolean) {
    private val isOuter: Boolean
    private var vertexNumFraction = -1.0
    private var areaDeltaRatio = -1.0
    private val geomFactory: GeometryFactory = inputGeom.factory

    /**
     * Creates a new instance
     * to compute a simplified hull of a polygonal geometry.
     * An outer or inner hull is computed
     * depending on the value of `isOuter`.
     *
     * @param inputGeom the polygonal geometry to process
     * @param isOuter indicates whether to compute an outer or inner hull
     */
    init {
        this.isOuter = isOuter
        if (inputGeom !is Polygonal) {
            throw IllegalArgumentException("Input geometry must be  polygonal")
        }
    }

    /**
     * Sets the target fraction of input vertices
     * which are retained in the result.
     * The value should be in the range [0,1].
     *
     * @param vertexNumFraction a fraction of the number of input vertices
     */
    fun setVertexNumFraction(vertexNumFraction: Double) {
        val frac = clamp(vertexNumFraction, 0.0, 1.0)
        this.vertexNumFraction = frac
    }

    /**
     * Sets the target maximum ratio of the change in area of the result to the input area.
     * The value must be 0 or greater.
     *
     * @param areaDeltaRatio a ratio of the change in area of the result
     */
    fun setAreaDeltaRatio(areaDeltaRatio: Double) {
        this.areaDeltaRatio = areaDeltaRatio
    }
    /**
     * Only outer hulls where there is more than one polygon
     * can potentially overlap.
     * Shell outer hulls could overlap adjacent shell hulls
     * or hole hulls surrounding them;
     * hole outer hulls could overlap contained shell hulls.
     *///-- handle trivial parameter values
    /**
     * Gets the result polygonal hull geometry.
     *
     * @return the polygonal geometry for the hull
     */
    val result: Geometry
        get() {
            //-- handle trivial parameter values
            if (vertexNumFraction == 1.0 || areaDeltaRatio == 0.0) {
                return inputGeom.copy()
            }
            if (inputGeom is MultiPolygon) {
                /**
                 * Only outer hulls where there is more than one polygon
                 * can potentially overlap.
                 * Shell outer hulls could overlap adjacent shell hulls
                 * or hole hulls surrounding them;
                 * hole outer hulls could overlap contained shell hulls.
                 */
                val isOverlapPossible = isOuter && inputGeom.numGeometries > 1
                return if (isOverlapPossible) {
                    computeMultiPolygonAll(inputGeom)
                } else {
                    computeMultiPolygonEach(inputGeom)
                }
            } else if (inputGeom is Polygon) {
                return computePolygon(inputGeom)
            }
            throw IllegalArgumentException("Input geometry must be polygonal")
        }

    /**
     * Computes hulls for MultiPolygon elements for
     * the cases where hulls might overlap.
     *
     * @param multiPoly the MultiPolygon to process
     * @return the hull geometry
     */
    private fun computeMultiPolygonAll(multiPoly: MultiPolygon): Geometry {
        val hullIndex: RingHullIndex = RingHullIndex()
        val nPoly = multiPoly.numGeometries
        val polyHulls: Array<List<RingHull?>?> =
            arrayOfNulls<ArrayList<RingHull>>(nPoly) as Array<List<RingHull?>?>

        //TODO: investigate if reordering input elements improves result

        //-- prepare element polygon hulls and index
        for (i in 0 until multiPoly.numGeometries) {
            val poly = multiPoly.getGeometryN(i) as Polygon
            val ringHulls: List<RingHull> = initPolygon(poly, hullIndex)
            polyHulls[i] = ringHulls
        }

        //-- compute hull polygons
        val polys: MutableList<Polygon> = ArrayList()
        for (i in 0 until multiPoly.numGeometries) {
            val poly = multiPoly.getGeometryN(i) as Polygon
            val hull = polygonHull(poly, polyHulls[i], hullIndex)
            polys.add(hull)
        }
        return geomFactory.createMultiPolygon(GeometryFactory.toPolygonArray(polys))
    }

    private fun computeMultiPolygonEach(multiPoly: MultiPolygon): Geometry {
        val polys: MutableList<Polygon> = ArrayList()
        for (i in 0 until multiPoly.numGeometries) {
            val poly = multiPoly.getGeometryN(i) as Polygon
            val hull = computePolygon(poly)
            polys.add(hull)
        }
        return geomFactory.createMultiPolygon(GeometryFactory.toPolygonArray(polys))
    }

    private fun computePolygon(poly: Polygon): Polygon {
        var hullIndex: RingHullIndex? = null

        /**
         * For a single polygon overlaps are only possible for inner hulls
         * and where holes are present.
         */
        val isOverlapPossible = !isOuter && poly.getNumInteriorRing() > 0
        if (isOverlapPossible) hullIndex = RingHullIndex()
        val hulls: List<RingHull> = initPolygon(poly, hullIndex)
        return polygonHull(poly, hulls, hullIndex)
    }

    /**
     * Create all ring hulls for the rings of a polygon,
     * so that all are in the hull index if required.
     *
     * @param poly the polygon being processed
     * @param hullIndex the hull index if present, or null
     * @return the list of ring hulls
     */
    private fun initPolygon(
        poly: Polygon,
        hullIndex: RingHullIndex?
    ): List<RingHull> {
        val hulls: MutableList<RingHull> =
            ArrayList()
        if (poly.isEmpty) return hulls
        var areaTotal = 0.0
        if (areaDeltaRatio >= 0) {
            areaTotal = ringArea(poly)
        }
        hulls.add(createRingHull(poly.exteriorRing, isOuter, areaTotal, hullIndex))
        for (i in 0 until poly.getNumInteriorRing()) {
            //Assert: interior ring is not empty
            hulls.add(createRingHull(poly.getInteriorRingN(i), !isOuter, areaTotal, hullIndex))
        }
        return hulls
    }

    private fun ringArea(poly: Polygon): Double {
        var area: Double = ofRing(poly.exteriorRing!!.coordinateSequence!!)
        for (i in 0 until poly.getNumInteriorRing()) {
            area += ofRing(poly.getInteriorRingN(i).coordinateSequence!!)
        }
        return area
    }

    private fun createRingHull(
        ring: LinearRing?,
        isOuter: Boolean,
        areaTotal: Double,
        hullIndex: RingHullIndex?
    ): RingHull {
        val ringHull: RingHull = RingHull(ring!!, isOuter)
        if (vertexNumFraction >= 0) {
            val targetVertexCount: Int = ceil(vertexNumFraction * (ring.numPoints - 1)).toInt()
            ringHull.setMinVertexNum(targetVertexCount)
        } else if (areaDeltaRatio >= 0) {
            val ringArea: Double = ofRing(ring!!.coordinateSequence!!)
            val ringWeight = ringArea / areaTotal
            val maxAreaDelta = ringWeight * areaDeltaRatio * ringArea
            ringHull.setMaxAreaDelta(maxAreaDelta)
        }
        hullIndex?.add(ringHull)
        return ringHull
    }

    private fun polygonHull(
        poly: Polygon,
        ringHulls: List<RingHull?>?,
        hullIndex: RingHullIndex?
    ): Polygon {
        if (poly.isEmpty) return geomFactory.createPolygon()
        var ringIndex = 0
        val shellHull: LinearRing = ringHulls!![ringIndex++]!!.getHull(hullIndex)
        val holeHulls: MutableList<LinearRing> = ArrayList()
        for (i in 0 until poly.getNumInteriorRing()) {
            val hull: LinearRing = ringHulls[ringIndex++]!!.getHull(hullIndex)
            //TODO: handle empty
            holeHulls.add(hull)
        }
        val resultHoles: Array<LinearRing> = GeometryFactory.toLinearRingArray(holeHulls)
        return geomFactory.createPolygon(shellHull, resultHoles)
    }

    companion object {
        /**
         * Computes a topology-preserving simplified hull of a polygonal geometry,
         * with hull shape determined by a target parameter
         * specifying the fraction of the input vertices retained in the result.
         * Larger values compute less concave results.
         * A value of 1 produces the convex hull; a value of 0 produces the original geometry.
         * Either outer or inner hulls can be computed.
         *
         * @param geom the polygonal geometry to process
         * @param isOuter indicates whether to compute an outer or inner hull
         * @param vertexNumFraction the target fraction of number of input vertices in result
         * @return the hull geometry
         */
        @JvmStatic
        fun hull(geom: Geometry, isOuter: Boolean, vertexNumFraction: Double): Geometry {
            val hull = PolygonHullSimplifier(geom, isOuter)
            hull.setVertexNumFraction(abs(vertexNumFraction))
            return hull.result
        }

        /**
         * Computes a topology-preserving simplified hull of a polygonal geometry,
         * with hull shape determined by a target parameter
         * specifying the ratio of maximum difference in area to original area.
         * Larger values compute less concave results.
         * A value of 0 produces the original geometry.
         * Either outer or inner hulls can be computed.
         *
         * @param geom the polygonal geometry to process
         * @param isOuter indicates whether to compute an outer or inner hull
         * @param areaDeltaRatio the target ratio of area difference to original area
         * @return the hull geometry
         */
        @JvmStatic
        fun hullByAreaDelta(geom: Geometry, isOuter: Boolean, areaDeltaRatio: Double): Geometry {
            val hull = PolygonHullSimplifier(geom, isOuter)
            hull.setAreaDeltaRatio(abs(areaDeltaRatio))
            return hull.result
        }
    }
}