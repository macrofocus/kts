/*
 * Copyright (c) 2022 Martin Davis.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.coverage

import org.locationtech.jts.geom.Geometry
import kotlin.jvm.JvmStatic

/**
 * Simplifies the boundaries of the polygons in a polygonal coverage
 * while preserving the original coverage topology.
 * An area-based simplification algorithm
 * (similar to Visvalingam-Whyatt simplification)
 * is used to provide high-quality results.
 * Also supports simplifying just the inner edges in a coverage,
 * which allows simplifying "patches" without affecting their boundary.
 *
 * The amount of simplification is determined by a tolerance value,
 * which is a non-negative quantity. It equates roughly to the maximum
 * distance by which a simplified line can change from the original.
 * (In fact, it is the square root of the area tolerance used
 * in the Visvalingam-Whyatt algorithm.)
 *
 * The simplified result coverage has the following characteristics:
 *
 *  * It has the same number of polygonal geometries as the input
 *  * If the input is a valid coverage, then so is the result
 *  * Node points (inner vertices shared by three or more polygons,
 *     or boundary vertices shared by two or more) are not changed
 *  * Polygons maintain their line-adjacency (edges are never removed)
 *  * Rings are simplified to a minimum of 4 vertices, to better preserve their shape
 *  * Rings smaller than the area tolerance are removed where possible.
 *  This applies to both holes and "islands" (multipolygon elements
 *  which are disjoint or touch another polygon at a single vertex).
 *  At least one polygon is retained for each input geometry
 *  (the one with largest area).
 *
 * This class supports simplification using different distance tolerances
 * for inner and outer edges of the coverage (including no simplfication
 * using a tolerance of 0.0).
 * This allows, for example, inner simplification, which simplifies
 * only edges of the coverage which are adjacent to two polygons.
 * This allows partial simplification of a coverage, since a simplified
 * subset of a coverage still matches the remainder of the coverage.
 *
 * The class allows specifying a separate tolerance for each element of the input coverage.
 *
 * The input coverage should be valid according to [CoverageValidator].
 * Invalid coverages may be simplified, but the result will likely still be invalid.
 *
 * <h3>FUTURE WORK</h3>
 *
 * Support geodetic data by computing true geodetic area, and accepting tolerances in metres.
 *
 * @author Martin Davis
 */
class CoverageSimplifier
/**
 * Create a new coverage simplifier instance.
 *
 * @param coverage a set of polygonal geometries forming a coverage
 */(private val coverage: Array<Geometry>) {
    private var smoothWeight = CornerArea.DEFAULT_SMOOTH_WEIGHT
    private var removableSizeFactor = 1.0

    /**
     * Sets the factor applied to the area tolerance to determine
     * if small rings should be removed.
     * Larger values cause more rings to be removed.
     * A value of 0 prevents rings from being removed.
     *
     * @param removableSizeFactor the factor to determine ring size to remove
     */
    fun setRemovableRingSizeFactor(removableSizeFactor: Double) {
        var factor = removableSizeFactor
        if (factor < 0.0) factor = 0.0
        this.removableSizeFactor = factor
    }

    /**
     * Sets the weight influencing how smooth the simplification should be.
     * The weight must be between 0 and 1.
     * Larger values increase the smoothness of the simplified edges.
     *
     * @param smoothWeight a value between 0 and 1
     */
    fun setSmoothWeight(smoothWeight: Double) {
        if (smoothWeight < 0.0 || smoothWeight > 1.0) throw IllegalArgumentException("smoothWeight must be in range [0 - 1]")
        this.smoothWeight = smoothWeight
    }

    /**
     * Computes the simplified coverage using a single distance tolerance,
     * preserving the coverage topology.
     *
     * @param tolerance the simplification distance tolerance
     * @return the simplified coverage polygons
     */
    fun simplify(tolerance: Double): Array<Geometry?> {
        return simplifyEdges(tolerance, tolerance)
    }

    /**
     * Computes the simplified coverage using separate distance tolerances
     * for inner and outer edges,
     * preserving the coverage topology.
     *
     * @param toleranceInner the distance tolerance for inner edges
     * @param toleranceOuter the distance tolerance for outer edges
     * @return the simplified coverage polygons
     */
    fun simplify(toleranceInner: Double, toleranceOuter: Double): Array<Geometry?> {
        return simplifyEdges(toleranceInner, toleranceOuter)
    }

    /**
     * Computes the simplified coverage using separate distance tolerances
     * for each coverage element,
     * preserving the coverage topology.
     *
     * @param tolerances the distance tolerances for the coverage elements
     * @return the simplified coverage polygons
     */
    fun simplify(tolerances: DoubleArray): Array<Geometry?> {
        if (tolerances.size != coverage.size) throw IllegalArgumentException("number of tolerances does not match number of coverage elements")
        return simplifyEdges(tolerances)
    }

    private fun simplifyEdges(tolerances: DoubleArray): Array<Geometry?> {
        val covRings = CoverageRingEdges.create(coverage)
        val covEdges = covRings.edges
        val edges = createEdges(covEdges, tolerances)
        return simplify(covRings, covEdges, edges)
    }

    private fun createEdges(covEdges: List<CoverageEdge>, tolerances: DoubleArray): Array<TPVWSimplifier.Edge> {
        return Array(covEdges.size) { i ->
            val covEdge = covEdges[i]
            val tol = computeTolerance(covEdge, tolerances)
            createEdge(covEdge, tol)
        }
    }

    private fun computeTolerance(covEdge: CoverageEdge, tolerances: DoubleArray): Double {
        val index0 = covEdge.getAdjacentIndex(0)
        // assert: index0 >= 0
        var tolerance = tolerances[index0]

        if (covEdge.hasAdjacentIndex(1)) {
            val index1 = covEdge.getAdjacentIndex(1)
            val tol1 = tolerances[index1]
            //-- use lowest tolerance for edge
            if (tol1 < tolerance) tolerance = tol1
        }
        return tolerance
    }

    private fun simplifyEdges(toleranceInner: Double, toleranceOuter: Double): Array<Geometry?> {
        val covRings = CoverageRingEdges.create(coverage)
        val covEdges = covRings.edges
        val edges = createEdges(covEdges, toleranceInner, toleranceOuter)
        return simplify(covRings, covEdges, edges)
    }

    private fun simplify(
        covRings: CoverageRingEdges,
        covEdges: List<CoverageEdge>,
        edges: Array<TPVWSimplifier.Edge>
    ): Array<Geometry?> {
        val cornerArea = CornerArea(smoothWeight)
        TPVWSimplifier.simplify(edges, cornerArea, removableSizeFactor)
        setCoordinates(covEdges, edges)
        return covRings.buildCoverage()
    }

    private fun setCoordinates(covEdges: List<CoverageEdge>, edges: Array<TPVWSimplifier.Edge>) {
        for (i in covEdges.indices) {
            val edge = edges[i]
            if (edge.tolerance > 0) {
                covEdges[i].coordinates = edges[i].coordinates
            }
        }
    }

    companion object {
        /**
         * Simplifies the boundaries of a set of polygonal geometries forming a coverage,
         * preserving the coverage topology.
         *
         * @param coverage a set of polygonal geometries forming a coverage
         * @param tolerance the simplification tolerance
         * @return the simplified coverage polygons
         */
        @JvmStatic
        fun simplify(coverage: Array<Geometry>, tolerance: Double): Array<Geometry?> {
            val simplifier = CoverageSimplifier(coverage)
            return simplifier.simplify(tolerance)
        }

        /**
         * Simplifies the boundaries of a set of polygonal geometries forming a coverage,
         * preserving the coverage topology, using a separate tolerance
         * for each element of the coverage.
         * Coverage edges are simplified using the lowest tolerance of each adjacent
         * element.
         *
         * @param coverage a set of polygonal geometries forming a coverage
         * @param tolerances the simplification tolerances (one per input element)
         * @return the simplified coverage polygons
         */
        @JvmStatic
        fun simplify(coverage: Array<Geometry>, tolerances: DoubleArray): Array<Geometry?> {
            val simplifier = CoverageSimplifier(coverage)
            return simplifier.simplify(tolerances)
        }

        /**
         * Simplifies the inner boundaries of a set of polygonal geometries forming a coverage,
         * preserving the coverage topology.
         * Edges which form the exterior boundary of the coverage are left unchanged.
         *
         * @param coverage a set of polygonal geometries forming a coverage
         * @param tolerance the simplification tolerance
         * @return the simplified coverage polygons
         */
        @JvmStatic
        fun simplifyInner(coverage: Array<Geometry>, tolerance: Double): Array<Geometry?> {
            val simplifier = CoverageSimplifier(coverage)
            return simplifier.simplify(tolerance, 0.0)
        }

        /**
         * Simplifies the outer boundaries of a set of polygonal geometries forming a coverage,
         * preserving the coverage topology.
         * Edges in the interior of the coverage are left unchanged.
         *
         * @param coverage a set of polygonal geometries forming a coverage
         * @param tolerance the simplification tolerance
         * @return the simplified polygons
         */
        @JvmStatic
        fun simplifyOuter(coverage: Array<Geometry>, tolerance: Double): Array<Geometry?> {
            val simplifier = CoverageSimplifier(coverage)
            return simplifier.simplify(0.0, tolerance)
        }

        private fun createEdges(
            covEdges: List<CoverageEdge>,
            toleranceInner: Double,
            toleranceOuter: Double
        ): Array<TPVWSimplifier.Edge> {
            return Array(covEdges.size) { i ->
                val covEdge = covEdges[i]
                val tol = computeTolerance(covEdge, toleranceInner, toleranceOuter)
                createEdge(covEdge, tol)
            }
        }

        private fun createEdge(covEdge: CoverageEdge, tol: Double): TPVWSimplifier.Edge {
            return TPVWSimplifier.Edge(
                covEdge.coordinates, tol,
                covEdge.isFreeRing, covEdge.isRemovableRing
            )
        }

        private fun computeTolerance(covEdge: CoverageEdge, toleranceInner: Double, toleranceOuter: Double): Double {
            return if (covEdge.isInner) toleranceInner else toleranceOuter
        }
    }
}
