/*
 * Copyright (c) 2019 Martin Davis.
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
package org.locationtech.jts.operation.overlayng

import org.locationtech.jts.geom.*
import org.locationtech.jts.noding.Noder
import org.locationtech.jts.noding.snap.SnappingNoder
import org.locationtech.jts.noding.snapround.SnapRoundingNoder
import org.locationtech.jts.operation.overlay.OverlayOp
import kotlin.jvm.JvmStatic

/**
 * Computes the geometric overlay of two [Geometry]s,
 * using an explicit precision model to allow robust computation.
 *
 * The overlay can be used to determine any of the
 * following set-theoretic operations (boolean combinations) of the geometries:
 *
 *  * [.INTERSECTION] - all points which lie in both geometries
 *  * [.UNION] - all points which lie in at least one geometry
 *  * [.DIFFERENCE] - all points which lie in the first geometry but not the second
 *  * [.SYMDIFFERENCE] - all points which lie in one geometry but not both
 *
 * Input geometries may have different dimension.
 * Input collections must be homogeneous (all elements must have the same dimension).
 *
 * The precision model used for the computation can be supplied
 * independent of the precision model of the input geometry.
 * The main use for this is to allow using a fixed precision
 * for geometry with a floating precision model.
 * This does two things: ensures robust computation;
 * and forces the output to be validly rounded to the precision model.
 *
 * For fixed precision models noding is performed using a [SnapRoundingNoder].
 * This provides robust computation (as long as precision is limited to
 * around 13 decimal digits).
 *
 * For floating precision an [MCIndexNoder] is used.
 * This is not fully robust, so can sometimes result in
 * [TopologyException]s being thrown.
 * For robust full-precision overlay see [OverlayNGRobust].
 *
 * A custom [Noder] can be supplied.
 * This allows using a more performant noding strategy in specific cases,
 * for instance in [CoverageUnion].
 *
 * **Note:**[SnappingNoder] is used
 * it is best to specify a fairly small snap tolerance,
 * since the intersection clipping optimization can
 * interact with the snapping to alter the result.
 *
 * Optionally the overlay computation can process using strict mode
 * (via [.setStrictMode].
 * In strict mode result semantics are:
 *
 *  * Lines and Points resulting from topology collapses are not included in the result
 *  * Result geometry is homogeneous
 * for the [.INTERSECTION] and [.DIFFERENCE] operations.
 *  * Result geometry is homogeneous
 * for the [.UNION] and [.SYMDIFFERENCE] operations
 * if the inputs have the same dimension
 *
 *
 * Strict mode has the following benefits:
 *
 *  * Results are simpler
 *  * Overlay operations are chainable
 * without needing to remove lower-dimension elements
 *
 *
 * The original JTS overlay semantics corresponds to non-strict mode.
 *
 * If a robustness error occurs, a [TopologyException] is thrown.
 * These are usually caused by numerical rounding causing the noding output
 * to not be fully noded.
 * For robust computation with full-precision [OverlayNGRobust] can be used.
 *
 * @author mdavis
 *
 * @see OverlayNGRobust
 */
class OverlayNG(geom0: Geometry?, geom1: Geometry?, private val pm: PrecisionModel?, private val opCode: Int) {
    private val inputGeom: InputGeometry
    private val geomFact: GeometryFactory
    private var noder: Noder? = null
    private var isStrictMode = STRICT_MODE_DEFAULT
    private var isOptimized = true
    private var isAreaResultOnly = false
    private var isOutputEdges = false
    private var isOutputResultEdges = false
    private var isOutputNodedEdges = false

    /**
     * Creates an overlay operation on the given geometries,
     * with a defined precision model.
     * The noding strategy is determined by the precision model.
     *
     * @param geom0 the A operand geometry
     * @param geom1 the B operand geometry (may be null)
     * @param pm the precision model to use
     * @param opCode the overlay opcode
     */
    init {
        geomFact = geom0!!.factory
        inputGeom = InputGeometry(geom0, geom1)
    }

    /**
     * Creates an overlay operation on the given geometries
     * using the precision model of the geometries.
     *
     * The noder is chosen according to the precision model specified.
     *
     *  * For [PrecisionModel.FIXED]
     * a snap-rounding noder is used, and the computation is robust.
     *  * For [PrecisionModel.FLOATING]
     * a non-snapping noder is used,
     * and this computation may not be robust.
     * If errors occur a [TopologyException] is thrown.
     *
     * @param geom0 the A operand geometry
     * @param geom1 the B operand geometry (may be null)
     * @param opCode the overlay opcode
     */
    constructor(geom0: Geometry?, geom1: Geometry?, opCode: Int) : this(
        geom0,
        geom1,
        geom0!!.factory.precisionModel,
        opCode
    )

    /**
     * Creates a union of a single geometry with a given precision model.
     *
     * @param geom the geometry
     * @param pm the precision model to use
     */
    internal constructor(geom: Geometry?, pm: PrecisionModel?) : this(geom, null, pm, UNION)

    /**
     * Sets whether the overlay results are computed according to strict mode
     * semantics.
     *
     *  * Lines resulting from topology collapse are not included
     *  * Result geometry is homogeneous
     * for the [.INTERSECTION] and [.DIFFERENCE] operations.
     *  * Result geometry is homogeneous
     * for the [.UNION] and [.SYMDIFFERENCE] operations
     * if the inputs have the same dimension
     *
     * @param isStrictMode true if strict mode is to be used
     */
    fun setStrictMode(isStrictMode: Boolean) {
        this.isStrictMode = isStrictMode
    }

    /**
     * Sets whether overlay processing optimizations are enabled.
     * It may be useful to disable optimizations
     * for testing purposes.
     * Default is TRUE (optimization enabled).
     *
     * @param isOptimized whether to optimize processing
     */
    fun setOptimized(isOptimized: Boolean) {
        this.isOptimized = isOptimized
    }

    /**
     * Sets whether the result can contain only [Polygon] components.
     * This is used if it is known that the result must be an (possibly empty) area.
     *
     * @param isAreaResultOnly true if the result should contain only area components
     */
    fun setAreaResultOnly(isAreaResultOnly: Boolean) {
        this.isAreaResultOnly = isAreaResultOnly
    }
    //------ Testing options -------
    /**
     *
     * @param isOutputEdges
     */
    fun setOutputEdges(isOutputEdges: Boolean) {
        this.isOutputEdges = isOutputEdges
    }

    fun setOutputNodedEdges(isOutputNodedEdges: Boolean) {
        isOutputEdges = true
        this.isOutputNodedEdges = isOutputNodedEdges
    }

    fun setOutputResultEdges(isOutputResultEdges: Boolean) {
        this.isOutputResultEdges = isOutputResultEdges
    }

    //---------------------------------
    fun setNoder(noder: Noder?) {
        this.noder = noder
    }// handle case where both inputs are formed of edges (Lines and Polygons)
    /**
     * This is a no-op if the elevation model was not computed due to Z not present
     */// handle Point-nonPoint inputs // handle Point-Point inputs
    /**
     * The elevation model is only computed if the input geometries have Z values.
     */// handle empty inputs which determine result
    /**
     * Gets the result of the overlay operation.
     *
     * @return the result of the overlay operation.
     *
     * @throws IllegalArgumentException if the input is not supported (e.g. a mixed-dimension geometry)
     * @throws TopologyException if a robustness error occurs
     */
    val result: Geometry?
        get() {
            // handle empty inputs which determine result
            if (OverlayUtil.isEmptyResult(
                    opCode,
                    inputGeom.getGeometry(0),
                    inputGeom.getGeometry(1),
                    pm
                )
            ) {
                return createEmptyResult()
            }
            /**
             * The elevation model is only computed if the input geometries have Z values.
             */
            val elevModel: ElevationModel =
                ElevationModel.create(
                    inputGeom.getGeometry(0),
                    inputGeom.getGeometry(1)
                )
            val result: Geometry? = if (inputGeom.isAllPoints) {
                // handle Point-Point inputs
                OverlayPoints.overlay(
                    opCode,
                    inputGeom.getGeometry(0),
                    inputGeom.getGeometry(1),
                    pm
                )
            } else if (!inputGeom.isSingle && inputGeom.hasPoints()) {
                // handle Point-nonPoint inputs 
                OverlayMixedPoints.overlay(
                    opCode,
                    inputGeom.getGeometry(0),
                    inputGeom.getGeometry(1),
                    pm
                )
            } else {
                // handle case where both inputs are formed of edges (Lines and Polygons)
                computeEdgeOverlay()
            }
            /**
             * This is a no-op if the elevation model was not computed due to Z not present
             */
            elevModel.populateZ(result)
            return result
        }

    private fun computeEdgeOverlay(): Geometry? {
        val edges: List<Edge> = nodeEdges()
        val graph: OverlayGraph = buildGraph(edges)
        if (isOutputNodedEdges) {
            return OverlayUtil.toLines(graph, isOutputEdges, geomFact)
        }
        labelGraph(graph)
        //for (OverlayEdge e : graph.getEdges()) {  Debug.println(e);  }
        if (isOutputEdges || isOutputResultEdges) {
            return OverlayUtil.toLines(graph, isOutputEdges, geomFact)
        }
        val result = extractResult(opCode, graph)
        /**
         * Heuristic check on result area.
         * Catches cases where noding causes vertex to move
         * and make topology graph area "invert".
         */
        if (OverlayUtil.isFloating(pm)) {
            val isAreaConsistent: Boolean = OverlayUtil.isResultAreaConsistent(
                inputGeom.getGeometry(0),
                inputGeom.getGeometry(1),
                opCode,
                result
            )
            if (!isAreaConsistent) throw TopologyException("Result area inconsistent with overlay operation")
        }
        return result
    }

    private fun nodeEdges(): List<Edge> {
        /**
         * Node the edges, using whatever noder is being used
         */
        val nodingBuilder: EdgeNodingBuilder =
            EdgeNodingBuilder(
                pm, noder
            )
        /**
         * Optimize Intersection and Difference by clipping to the
         * result extent, if enabled.
         */
        if (isOptimized) {
            val clipEnv: Envelope? = OverlayUtil.clippingEnvelope(
                opCode, inputGeom, pm
            )
            if (clipEnv != null) nodingBuilder.setClipEnvelope(clipEnv)
        }
        val mergedEdges: List<Edge> = nodingBuilder.build(
            inputGeom.getGeometry(0),
            inputGeom.getGeometry(1)
        )
        /**
         * Record if an input geometry has collapsed.
         * This is used to avoid trying to locate disconnected edges
         * against a geometry which has collapsed completely.
         */
        inputGeom.setCollapsed(0, !nodingBuilder.hasEdgesFor(0))
        inputGeom.setCollapsed(1, !nodingBuilder.hasEdgesFor(1))
        return mergedEdges
    }

    private fun buildGraph(edges: Collection<Edge>): OverlayGraph {
        val graph: OverlayGraph =
            OverlayGraph()
        for (e in edges) {
            graph.addEdge(e.coordinates, e.createLabel())
        }
        return graph
    }

    private fun labelGraph(graph: OverlayGraph) {
        val labeller: OverlayLabeller =
            OverlayLabeller(graph, inputGeom)
        labeller.computeLabelling()
        labeller.markResultAreaEdges(opCode)
        labeller.unmarkDuplicateEdgesFromResultArea()
    }

    /**
     * Extracts the result geometry components from the fully labelled topology graph.
     *
     * This method implements the semantic that the result of an
     * intersection operation is homogeneous with highest dimension.
     * In other words,
     * if an intersection has components of a given dimension
     * no lower-dimension components are output.
     * For example, if two polygons intersect in an area,
     * no linestrings or points are included in the result,
     * even if portions of the input do meet in lines or points.
     * This semantic choice makes more sense for typical usage,
     * in which only the highest dimension components are of interest.
     *
     * @param opCode the overlay operation
     * @param graph the topology graph
     * @return the result geometry
     */
    private fun extractResult(
        opCode: Int,
        graph: OverlayGraph
    ): Geometry? {
        val isAllowMixedIntResult = !isStrictMode

        //--- Build polygons
        val resultAreaEdges: List<OverlayEdge> =
            graph.resultAreaEdges
        val polyBuilder: PolygonBuilder =
            PolygonBuilder(resultAreaEdges, geomFact)
        val resultPolyList: List<Polygon> = polyBuilder.polygons
        val hasResultAreaComponents = resultPolyList.isNotEmpty()
        var resultLineList: List<LineString>? = null
        var resultPointList: List<Point>? = null
        if (!isAreaResultOnly) {
            //--- Build lines
            val allowResultLines = (!hasResultAreaComponents
                    || isAllowMixedIntResult) || opCode == SYMDIFFERENCE || opCode == UNION
            if (allowResultLines) {
                val lineBuilder: LineBuilder =
                    LineBuilder(
                        inputGeom,
                        graph,
                        hasResultAreaComponents,
                        opCode,
                        geomFact
                    )
                lineBuilder.setStrictMode(isStrictMode)
                resultLineList = lineBuilder.getLines()
            }
            /**
             * Operations with point inputs are handled elsewhere.
             * Only an Intersection op can produce point results
             * from non-point inputs.
             */
            val hasResultComponents = hasResultAreaComponents || resultLineList!!.isNotEmpty()
            val allowResultPoints = !hasResultComponents || isAllowMixedIntResult
            if (opCode == INTERSECTION && allowResultPoints) {
                val pointBuilder: IntersectionPointBuilder =
                    IntersectionPointBuilder(graph, geomFact)
                pointBuilder.setStrictMode(isStrictMode)
                resultPointList = pointBuilder.getPoints()
            }
        }
        return if (isEmpty(resultPolyList)
            && isEmpty(resultLineList)
            && isEmpty(resultPointList)
        ) createEmptyResult() else OverlayUtil.createResultGeometry(
            resultPolyList,
            resultLineList,
            resultPointList,
            geomFact
        )
    }

    private fun createEmptyResult(): Geometry? {
        return OverlayUtil.createEmptyResult(
            OverlayUtil.resultDimension(
                opCode,
                inputGeom.getDimension(0),
                inputGeom.getDimension(1)
            ),
            geomFact
        )
    }

    companion object {
        /**
         * The code for the Intersection overlay operation.
         */
        const val INTERSECTION = OverlayOp.INTERSECTION

        /**
         * The code for the Union overlay operation.
         */
        const val UNION = OverlayOp.UNION

        /**
         * The code for the Difference overlay operation.
         */
        const val DIFFERENCE = OverlayOp.DIFFERENCE

        /**
         * The code for the Symmetric Difference overlay operation.
         */
        const val SYMDIFFERENCE = OverlayOp.SYMDIFFERENCE

        /**
         * The default setting for Strict Mode.
         *
         * The original JTS overlay semantics used non-strict result
         * semantics, including;
         * - An Intersection result can be mixed-dimension,
         * due to inclusion of intersection components of all dimensions
         * - Results can include lines caused by Area topology collapse
         */
        const val STRICT_MODE_DEFAULT = false

        /**
         * Tests whether a point with a given topological [Label]
         * relative to two geometries is contained in
         * the result of overlaying the geometries using
         * a given overlay operation.
         *
         *
         * The method handles arguments of [Location.NONE] correctly
         *
         * @param label the topological label of the point
         * @param opCode the code for the overlay operation to test
         * @return true if the label locations correspond to the overlayOpCode
         */
        fun isResultOfOpPoint(label: OverlayLabel, opCode: Int): Boolean {
            val loc0: Int = label.getLocation(0)
            val loc1: Int = label.getLocation(1)
            return isResultOfOp(opCode, loc0, loc1)
        }

        /**
         * Tests whether a point with given [Location]s
         * relative to two geometries would be contained in
         * the result of overlaying the geometries using
         * a given overlay operation.
         * This is used to determine whether components
         * computed during the overlay process should be
         * included in the result geometry.
         *
         *
         * The method handles arguments of [Location.NONE] correctly.
         *
         * @param overlayOpCode the code for the overlay operation to test
         * @param loc0 the code for the location in the first geometry
         * @param loc1 the code for the location in the second geometry
         *
         * @return true if a point with given locations is in the result of the overlay operation
         */
        fun isResultOfOp(overlayOpCode: Int, loc0: Int, loc1: Int): Boolean {
            var loc0 = loc0
            var loc1 = loc1
            if (loc0 == Location.BOUNDARY) loc0 = Location.INTERIOR
            if (loc1 == Location.BOUNDARY) loc1 = Location.INTERIOR
            when (overlayOpCode) {
                INTERSECTION -> return (loc0 == Location.INTERIOR
                        && loc1 == Location.INTERIOR)

                UNION -> return (loc0 == Location.INTERIOR
                        || loc1 == Location.INTERIOR)

                DIFFERENCE -> return (loc0 == Location.INTERIOR
                        && loc1 != Location.INTERIOR)

                SYMDIFFERENCE -> return (loc0 == Location.INTERIOR && loc1 != Location.INTERIOR || loc0 != Location.INTERIOR) && loc1 == Location.INTERIOR
            }
            return false
        }

        /**
         * Computes an overlay operation for
         * the given geometry operands, with the
         * noding strategy determined by the precision model.
         *
         * @param geom0 the first geometry argument
         * @param geom1 the second geometry argument
         * @param opCode the code for the desired overlay operation
         * @param pm the precision model to use
         * @return the result of the overlay operation
         */
        @JvmStatic
        fun overlay(
            geom0: Geometry?, geom1: Geometry?,
            opCode: Int, pm: PrecisionModel?
        ): Geometry? {
            val ov =
                OverlayNG(geom0, geom1, pm, opCode)
            return ov.result
        }

        /**
         * Computes an overlay operation on the given geometry operands,
         * using a supplied [Noder].
         *
         * @param geom0 the first geometry argument
         * @param geom1 the second geometry argument
         * @param opCode the code for the desired overlay operation
         * @param pm the precision model to use (which may be null if the noder does not use one)
         * @param noder the noder to use
         * @return the result of the overlay operation
         */
        @JvmStatic
        fun overlay(
            geom0: Geometry?,
            geom1: Geometry?,
            opCode: Int,
            pm: PrecisionModel?,
            noder: Noder?
        ): Geometry? {
            val ov =
                OverlayNG(geom0, geom1, pm, opCode)
            ov.setNoder(noder)
            return ov.result
        }

        /**
         * Computes an overlay operation on the given geometry operands,
         * using a supplied [Noder].
         *
         * @param geom0 the first geometry argument
         * @param geom1 the second geometry argument
         * @param opCode the code for the desired overlay operation
         * @param noder the noder to use
         * @return the result of the overlay operation
         */
        @JvmStatic
        fun overlay(
            geom0: Geometry?, geom1: Geometry?,
            opCode: Int, noder: Noder?
        ): Geometry? {
            val ov =
                OverlayNG(geom0, geom1, null, opCode)
            ov.setNoder(noder)
            return ov.result
        }

        /**
         * Computes an overlay operation on
         * the given geometry operands,
         * using the precision model of the geometry.
         * and an appropriate noder.
         *
         *
         * The noder is chosen according to the precision model specified.
         *
         *  * For [PrecisionModel.FIXED]
         * a snap-rounding noder is used, and the computation is robust.
         *  * For [PrecisionModel.FLOATING]
         * a non-snapping noder is used,
         * and this computation may not be robust.
         * If errors occur a [TopologyException] is thrown.
         *
         *
         *
         * @param geom0 the first argument geometry
         * @param geom1 the second argument geometry
         * @param opCode the code for the desired overlay operation
         * @return the result of the overlay operation
         */
        @JvmStatic
        fun overlay(geom0: Geometry?, geom1: Geometry?, opCode: Int): Geometry? {
            val ov = OverlayNG(geom0, geom1, opCode)
            return ov.result
        }

        /**
         * Computes a union operation on
         * the given geometry, with the supplied precision model.
         *
         *
         * The input must be a valid geometry.
         * Collections must be homogeneous.
         *
         *
         * To union an overlapping set of polygons in a more performant way use [UnaryUnionNG].
         * To union a polyonal coverage or linear network in a more performant way,
         * use [CoverageUnion].
         *
         * @param geom0 the geometry
         * @param pm the precision model to use
         * @return the result of the union operation
         *
         * @see OverlayMixedPoints
         */
        fun union(
            geom: Geometry?,
            pm: PrecisionModel?
        ): Geometry? {
            val ov =
                OverlayNG(geom, pm)
            return ov.result
        }

        /**
         * Computes a union of a single geometry using a custom noder.
         *
         *
         * The primary use of this is to support coverage union.
         * Because of this the overlay is performed using strict mode.
         *
         * @param geom the geometry to union
         * @param pm the precision model to use (maybe be null)
         * @param noder the noder to use
         * @return the result geometry
         *
         * @see CoverageUnion
         */
        fun union(
            geom: Geometry?,
            pm: PrecisionModel?,
            noder: Noder?
        ): Geometry? {
            val ov =
                OverlayNG(geom, pm)
            ov.setNoder(noder)
            ov.setStrictMode(true)
            return ov.result
        }

        private fun isEmpty(list: List<*>?): Boolean {
            return list == null || list.isEmpty()
        }
    }
}