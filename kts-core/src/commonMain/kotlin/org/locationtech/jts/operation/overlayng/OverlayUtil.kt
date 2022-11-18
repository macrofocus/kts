/*
 * Copyright (c) 2020 Martin Davis.
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.operation.overlayng

import org.locationtech.jts.geom.*
import org.locationtech.jts.legacy.Math.max
import org.locationtech.jts.legacy.Math.min
import org.locationtech.jts.util.Assert

/**
 * Utility methods for overlay processing.
 *
 * @author mdavis
 */
internal object OverlayUtil {
    /**
     * A null-handling wrapper for [PrecisionModel.isFloating]
     *
     * @param pm
     * @return
     */
    fun isFloating(pm: PrecisionModel?): Boolean {
        return pm?.isFloating ?: true
    }

    /**
     * Computes a clipping envelope for overlay input geometries.
     * The clipping envelope encloses all geometry line segments which
     * might participate in the overlay, with a buffer to
     * account for numerical precision
     * (in particular, rounding due to a precision model.
     * The clipping envelope is used in both the [RingClipper]
     * and in the [LineLimiter].
     *
     * Some overlay operations (i.e. [and OverlayNG#SYMDIFFERENCE][OverlayNG.UNION]
     * cannot use clipping as an optimization,
     * since the result envelope is the full extent of the two input geometries.
     * In this case the returned
     * envelope is `null` to indicate this.
     *
     * @param opCode the overlay op code
     * @param inputGeom the input geometries
     * @param pm the precision model being used
     * @return an envelope for clipping and line limiting, or null if no clipping is performed
     */
    fun clippingEnvelope(
        opCode: Int,
        inputGeom: InputGeometry,
        pm: PrecisionModel?
    ): Envelope? {
        val resultEnv =
            resultEnvelope(opCode, inputGeom, pm) ?: return null
        val clipEnv: Envelope =
            RobustClipEnvelopeComputer.getEnvelope(
                inputGeom.getGeometry(0),
                inputGeom.getGeometry(1),
                resultEnv
            )
        return safeEnv(clipEnv, pm)
    }

    /**
     * Computes an envelope which covers the extent of the result of
     * a given overlay operation for given inputs.
     * The operations which have a result envelope smaller than the extent of the inputs
     * are:
     *
     *  * [OverlayNG.INTERSECTION]: result envelope is the intersection of the input envelopes
     *  * [OverlayNG.DIFERENCE]: result envelope is the envelope of the A input geometry
     *
     * Otherwise, `null` is returned to indicate full extent.
     *
     * @param opCode
     * @param inputGeom
     * @param pm
     * @return the result envelope, or null if the full extent
     */
    private fun resultEnvelope(
        opCode: Int,
        inputGeom: InputGeometry,
        pm: PrecisionModel?
    ): Envelope? {
        var overlapEnv: Envelope? = null
        when (opCode) {
            OverlayNG.INTERSECTION -> {
                // use safe envelopes for intersection to ensure they contain rounded coordinates
                val envA = safeEnv(inputGeom.getEnvelope(0), pm)
                val envB = safeEnv(inputGeom.getEnvelope(1), pm)
                overlapEnv = envA.intersection(envB)
            }

            OverlayNG.DIFFERENCE -> overlapEnv =
                safeEnv(inputGeom.getEnvelope(0), pm)
        }
        // return null for UNION and SYMDIFFERENCE to indicate no clipping
        return overlapEnv
    }

    /**
     * Determines a safe geometry envelope for clipping,
     * taking into account the precision model being used.
     *
     * @param env a geometry envelope
     * @param pm the precision model
     * @return a safe envelope to use for clipping
     */
    private fun safeEnv(env: Envelope, pm: PrecisionModel?): Envelope {
        val envExpandDist = safeExpandDistance(env, pm)
        val safeEnv = env.copy()
        safeEnv.expandBy(envExpandDist)
        return safeEnv
    }

    private const val SAFE_ENV_BUFFER_FACTOR = 0.1
    private const val SAFE_ENV_GRID_FACTOR = 3
    private fun safeExpandDistance(env: Envelope, pm: PrecisionModel?): Double {
        val envExpandDist: Double
        if (isFloating(pm)) {
            // if PM is FLOAT then there is no scale factor, so add 10%
            var minSize: Double = min(env.height, env.width)
            // heuristic to ensure zero-width envelopes don't cause total clipping
            if (minSize <= 0.0) {
                minSize = max(env.height, env.width)
            }
            envExpandDist = SAFE_ENV_BUFFER_FACTOR * minSize
        } else {
            // if PM is fixed, add a small multiple of the grid size
            val gridSize = 1.0 / pm!!.getScale()
            envExpandDist = SAFE_ENV_GRID_FACTOR * gridSize
        }
        return envExpandDist
    }

    /**
     * Tests if the result can be determined to be empty
     * based on simple properties of the input geometries
     * (such as whether one or both are empty,
     * or their envelopes are disjoint).
     *
     * @param opCode the overlay operation
     * @param inputGeom the input geometries
     * @return true if the overlay result is determined to be empty
     */
    fun isEmptyResult(opCode: Int, a: Geometry?, b: Geometry?, pm: PrecisionModel?): Boolean {
        when (opCode) {
            OverlayNG.INTERSECTION -> if (isEnvDisjoint(
                    a,
                    b,
                    pm
                )
            ) return true

            OverlayNG.DIFFERENCE -> if (isEmpty(a)) return true
            OverlayNG.UNION, OverlayNG.SYMDIFFERENCE -> if (isEmpty(
                    a
                ) && isEmpty(b)
            ) return true
        }
        return false
    }

    private fun isEmpty(geom: Geometry?): Boolean {
        return geom == null || geom.isEmpty
    }

    /**
     * Tests if the geometry envelopes are disjoint, or empty.
     * The disjoint test must take into account the precision model
     * being used, since geometry coordinates may shift under rounding.
     *
     * @param a a geometry
     * @param b a geometry
     * @param pm the precision model being used
     * @return true if the geometry envelopes are disjoint or empty
     */
    fun isEnvDisjoint(a: Geometry?, b: Geometry?, pm: PrecisionModel?): Boolean {
        if (isEmpty(a) || isEmpty(b)) return true
        return if (isFloating(pm)) {
            a!!.envelopeInternal.disjoint(b!!.envelopeInternal)
        } else isDisjoint(
            a!!.envelopeInternal,
            b!!.envelopeInternal,
            pm
        )
    }

    /**
     * Tests for disjoint envelopes adjusting for rounding
     * caused by a fixed precision model.
     * Assumes envelopes are non-empty.
     *
     * @param envA an envelope
     * @param envB an envelope
     * @param pm the precision model
     * @return true if the envelopes are disjoint
     */
    private fun isDisjoint(envA: Envelope, envB: Envelope, pm: PrecisionModel?): Boolean {
        if (pm!!.makePrecise(envB.minX) > pm.makePrecise(envA.maxX)) return true
        if (pm.makePrecise(envB.maxX) < pm.makePrecise(envA.minX)) return true
        if (pm.makePrecise(envB.minY) > pm.makePrecise(envA.maxY)) return true
        return pm.makePrecise(envB.maxY) < pm.makePrecise(envA.minY)
    }

    /**
     * Creates an empty result geometry of the appropriate dimension,
     * based on the given overlay operation and the dimensions of the inputs.
     * The created geometry is an atomic geometry,
     * not a collection (unless the dimension is -1,
     * in which case a `GEOMETRYCOLLECTION EMPTY` is created.)
     *
     * @param dim the dimension of the empty geometry to create
     * @param geomFact the geometry factory being used for the operation
     * @return an empty atomic geometry of the appropriate dimension
     */
    fun createEmptyResult(dim: Int, geomFact: GeometryFactory): Geometry? {
        var result: Geometry? = null
        when (dim) {
            0 -> result = geomFact.createPoint()
            1 -> result = geomFact.createLineString()
            2 -> result = geomFact.createPolygon()
            -1 -> result = geomFact.createGeometryCollection()
            else -> Assert.shouldNeverReachHere("Unable to determine overlay result geometry dimension")
        }
        return result
    }

    /**
     * Computes the dimension of the result of
     * applying the given operation to inputs
     * with the given dimensions.
     * This assumes that complete collapse does not occur.
     *
     * The result dimension is computed according to the following rules:
     *
     *  * [OverlayNG.INTERSECTION] - result has the dimension of the lowest input dimension
     *  * [OverlayNG.UNION] - result has the dimension of the highest input dimension
     *  * [OverlayNG.DIFFERENCE] - result has the dimension of the left-hand input
     *  * [OverlayNG.SYMDIFFERENCE] - result has the dimension of the highest input dimension
     * (since the Symmetric Difference is the Union of the Differences).
     *
     * @param opCode the overlay operation
     * @param dim0 dimension of the LH input
     * @param dim1 dimension of the RH input
     * @return the dimension of the result
     */
    fun resultDimension(opCode: Int, dim0: Int, dim1: Int): Int {
        var resultDimension = -1
        when (opCode) {
            OverlayNG.INTERSECTION -> resultDimension =
                min(dim0, dim1)

            OverlayNG.UNION -> resultDimension =
                max(dim0, dim1)

            OverlayNG.DIFFERENCE -> resultDimension = dim0
            OverlayNG.SYMDIFFERENCE ->
                /**
                 * This result is chosen because
                 * <pre>
                 * SymDiff = Union( Diff(A, B), Diff(B, A) )
                </pre> *
                 * and Union has the dimension of the highest-dimension argument.
                 */
                resultDimension = max(dim0, dim1)
        }
        return resultDimension
    }

    /**
     * Creates an overlay result geometry for homogeneous or mixed components.
     *
     * @param resultPolyList the list of result polygons (may be empty or null)
     * @param resultLineList the list of result lines (may be empty or null)
     * @param resultPointList the list of result points (may be empty or null)
     * @param geometryFactory the geometry factory to use
     * @return a geometry structured according to the overlay result semantics
     */
    fun createResultGeometry(
        resultPolyList: List<Polygon>?,
        resultLineList: List<LineString>?,
        resultPointList: List<Point>?,
        geometryFactory: GeometryFactory
    ): Geometry {
        val geomList: MutableList<Geometry> = ArrayList<Geometry>()

        // TODO: for mixed dimension, return collection of Multigeom for each dimension (breaking change)

        // element geometries of the result are always in the order A,L,P
        if (resultPolyList != null) geomList.addAll(resultPolyList)
        if (resultLineList != null) geomList.addAll(resultLineList)
        if (resultPointList != null) geomList.addAll(resultPointList)

        // build the most specific geometry possible
        // TODO: perhaps do this internally to give more control?
        return geometryFactory.buildGeometry(geomList)
    }

    fun toLines(
        graph: OverlayGraph,
        isOutputEdges: Boolean,
        geomFact: GeometryFactory
    ): Geometry {
        val lines: MutableList<LineString> = ArrayList<LineString>()
        for (edge in graph.getEdges()) {
            val includeEdge = isOutputEdges || edge.isInResultArea
            if (!includeEdge) continue
            //Coordinate[] pts = getCoords(nss);
            val pts: Array<Coordinate> = edge.coordinatesOriented
            val line: LineString = geomFact.createLineString(pts)
            line.setUserData(labelForResult(edge))
            lines.add(line)
        }
        return geomFact.buildGeometry(lines)
    }

    private fun labelForResult(edge: OverlayEdge): String {
        return (edge.getLabel().toString(edge.isForward)
                + if (edge.isInResultArea) " Res" else "")
    }

    /**
     * Round the key point if precision model is fixed.
     * Note: return value is only copied if rounding is performed.
     *
     * @param pt the Point to round
     * @return the rounded point coordinate, or null if empty
     */
    fun round(pt: Point, pm: PrecisionModel?): Coordinate? {
        if (pt.isEmpty) return null
        val p = pt.coordinate!!.copy()
        if (!isFloating(pm)) {
            pm!!.makePrecise(p)
        }
        return p
    }

    private const val AREA_HEURISTIC_TOLERANCE = 0.1

    /**
     * A heuristic check for overlay result correctness
     * comparing the areas of the input and result.
     * The heuristic is necessarily coarse, but it detects some obvious issues.
     * (e.g. https://github.com/locationtech/jts/issues/798)
     *
     * **Note:** - this check is only safe if the precision model is floating.
     * It should also be safe for snapping noding if the distance tolerance is reasonably small.
     * (Fixed precision models can lead to collapse causing result area to expand.)
     *
     * @param geom0 input geometry 0
     * @param geom1 input geometry 1
     * @param opCode the overlay opcode
     * @param result the overlay result
     * @return true if the result area is consistent
     */
    fun isResultAreaConsistent(geom0: Geometry?, geom1: Geometry?, opCode: Int, result: Geometry?): Boolean {
        if (geom0 == null || geom1 == null) return true
        val areaResult = result!!.area
        val areaA = geom0.area
        val areaB = geom1.area
        var isConsistent = true
        when (opCode) {
            OverlayNG.INTERSECTION -> isConsistent =
                (isLess(areaResult, areaA, AREA_HEURISTIC_TOLERANCE)
                        && isLess(areaResult, areaB, AREA_HEURISTIC_TOLERANCE))

            OverlayNG.DIFFERENCE -> isConsistent =
                (isLess(areaResult, areaA, AREA_HEURISTIC_TOLERANCE)
                        && isGreater(areaResult, areaA - areaB, AREA_HEURISTIC_TOLERANCE))

            OverlayNG.SYMDIFFERENCE -> isConsistent =
                isLess(areaResult, areaA + areaB, AREA_HEURISTIC_TOLERANCE)

            OverlayNG.UNION -> isConsistent =
                (isLess(areaA, areaResult, AREA_HEURISTIC_TOLERANCE)
                        && isLess(areaB, areaResult, AREA_HEURISTIC_TOLERANCE)
                        && isGreater(areaResult, areaA - areaB, AREA_HEURISTIC_TOLERANCE))
        }
        return isConsistent
    }

    private fun isLess(v1: Double, v2: Double, tol: Double): Boolean {
        return v1 <= v2 * (1 + tol)
    }

    private fun isGreater(v1: Double, v2: Double, tol: Double): Boolean {
        return v1 >= v2 * (1 - tol)
    }
}