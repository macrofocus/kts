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
package org.locationtech.jts.operation.buffer

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.CoordinateArrays.reverse
import org.locationtech.jts.geom.Position
import org.locationtech.jts.geom.PrecisionModel
import org.locationtech.jts.legacy.Math.abs

/**
 * Computes the raw offset curve for a
 * single [Geometry] component (ring, line or point).
 * A raw offset curve line is not noded -
 * it may contain self-intersections (and usually will).
 * The final buffer polygon is computed by forming a topological graph
 * of all the noded raw curves and tracing outside contours.
 * The points in the raw curve are rounded
 * to a given [PrecisionModel].
 *
 * @version 1.7
 */
class OffsetCurveBuilder(
    private val precisionModel: PrecisionModel,
    bufParams: BufferParameters
) {
    private var distance = 0.0
    private val bufParams: BufferParameters

    init {
        this.bufParams = bufParams
    }

    /**
     * Gets the buffer parameters being used to generate the curve.
     *
     * @return the buffer parameters being used
     */
    val bufferParameters: BufferParameters
        get() = bufParams

    /**
     * This method handles single points as well as LineStrings.
     * LineStrings are assumed **not** to be closed (the function will not
     * fail for closed lines, but will generate superfluous line caps).
     *
     * @param inputPts the vertices of the line to offset
     * @param distance the offset distance
     *
     * @return a Coordinate array representing the curve
     * or null if the curve is empty
     */
    fun getLineCurve(
        inputPts: Array<Coordinate>,
        distance: Double
    ): Array<Coordinate>? {
        this.distance = distance
        if (isLineOffsetEmpty(distance)) return null
        val posDistance: Double = abs(distance)
        val segGen: OffsetSegmentGenerator = getSegGen(posDistance)
        if (inputPts.size <= 1) {
            computePointCurve(inputPts[0], segGen)
        } else {
            if (bufParams.isSingleSided) {
                val isRightSide = distance < 0.0
                computeSingleSidedBufferCurve(inputPts, isRightSide, segGen)
            } else computeLineBufferCurve(inputPts, segGen)
        }
        return segGen.coordinates
    }

    /**
     * Tests whether the offset curve for line or point geometries
     * at the given offset distance is empty (does not exist).
     * This is the case if:
     *
     *  * the distance is zero,
     *  * the distance is negative, except for the case of singled-sided buffers
     *
     * @param distance the offset curve distance
     * @return true if the offset curve is empty
     */
    fun isLineOffsetEmpty(distance: Double): Boolean {
        // a zero width buffer of a line or point is empty
        if (distance == 0.0) return true
        // a negative width buffer of a line or point is empty,
        // except for single-sided buffers, where the sign indicates the side
        return if (distance < 0.0 && !bufParams.isSingleSided) true else false
    }

    /**
     * This method handles the degenerate cases of single points and lines,
     * as well as valid rings.
     *
     * @param inputPts the coordinates of the ring (must not contain repeated points)
     * @param side side the side [Position] of the ring on which to construct the buffer line
     * @param distance the positive distance at which to create the offset
     * @return a Coordinate array representing the curve,
     * or null if the curve is empty
     */
    fun getRingCurve(inputPts: Array<Coordinate>, side: Int, distance: Double): Array<Coordinate>? {
        this.distance = distance
        if (inputPts.size <= 2) return getLineCurve(inputPts, distance)

        // optimize creating ring for for zero distance
        if (distance == 0.0) {
            return copyCoordinates(inputPts)
        }
        val segGen: OffsetSegmentGenerator = getSegGen(distance)
        computeRingBufferCurve(inputPts, side, segGen)
        return segGen.coordinates
    }

    fun getOffsetCurve(inputPts: Array<Coordinate>, distance: Double): Array<Coordinate>? {
        this.distance = distance

        // a zero width offset curve is empty
        if (distance == 0.0) return null
        val isRightSide = distance < 0.0
        val posDistance: Double = abs(distance)
        val segGen: OffsetSegmentGenerator = getSegGen(posDistance)
        if (inputPts.size <= 1) {
            computePointCurve(inputPts[0], segGen)
        } else {
            computeOffsetCurve(inputPts, isRightSide, segGen)
        }
        val curvePts: Array<Coordinate> = segGen.coordinates
        // for right side line is traversed in reverse direction, so have to reverse generated line
        if (isRightSide) reverse(curvePts)
        return curvePts
    }

    private fun getSegGen(distance: Double): OffsetSegmentGenerator {
        return OffsetSegmentGenerator(precisionModel, bufParams, distance)
    }

    /**
     * Computes the distance tolerance to use during input
     * line simplification.
     *
     * @param distance the buffer distance
     * @return the simplification tolerance
     */
    private fun simplifyTolerance(bufDistance: Double): Double {
        return bufDistance * bufParams.simplifyFactor
    }

    private fun computePointCurve(
        pt: Coordinate?,
        segGen: OffsetSegmentGenerator
    ) {
        when (bufParams.getEndCapStyle()) {
            BufferParameters.CAP_ROUND -> segGen.createCircle(pt)
            BufferParameters.CAP_SQUARE -> segGen.createSquare(pt)
        }
    }

    private fun computeLineBufferCurve(
        inputPts: Array<Coordinate>,
        segGen: OffsetSegmentGenerator
    ) {
        val distTol = simplifyTolerance(distance)

        //--------- compute points for left side of line
        // Simplify the appropriate side of the line before generating
        val simp1 = BufferInputLineSimplifier.simplify(inputPts, distTol)
        // MD - used for testing only (to eliminate simplification)
//    Coordinate[] simp1 = inputPts;
        val n1 = simp1.size - 1
        segGen.initSideSegments(simp1[0], simp1[1], Position.LEFT)
        for (i in 2..n1) {
            segGen.addNextSegment(simp1[i], true)
        }
        segGen.addLastSegment()
        // add line cap for end of line
        segGen.addLineEndCap(simp1[n1 - 1], simp1[n1])

        //---------- compute points for right side of line
        // Simplify the appropriate side of the line before generating
        val simp2 = BufferInputLineSimplifier.simplify(inputPts, -distTol)
        // MD - used for testing only (to eliminate simplification)
//    Coordinate[] simp2 = inputPts;
        val n2 = simp2.size - 1

        // since we are traversing line in opposite order, offset position is still LEFT
        segGen.initSideSegments(simp2[n2], simp2[n2 - 1], Position.LEFT)
        for (i in n2 - 2 downTo 0) {
            segGen.addNextSegment(simp2[i], true)
        }
        segGen.addLastSegment()
        // add line cap for start of line
        segGen.addLineEndCap(simp2[1], simp2[0])
        segGen.closeRing()
    }

    private fun computeSingleSidedBufferCurve(
        inputPts: Array<Coordinate>,
        isRightSide: Boolean,
        segGen: OffsetSegmentGenerator
    ) {
        val distTol = simplifyTolerance(distance)
        if (isRightSide) {
            // add original line
            segGen.addSegments(inputPts, true)

            //---------- compute points for right side of line
            // Simplify the appropriate side of the line before generating
            val simp2 = BufferInputLineSimplifier.simplify(inputPts, -distTol)
            // MD - used for testing only (to eliminate simplification)
            //    Coordinate[] simp2 = inputPts;
            val n2 = simp2.size - 1

            // since we are traversing line in opposite order, offset position is still LEFT
            segGen.initSideSegments(simp2[n2], simp2[n2 - 1], Position.LEFT)
            segGen.addFirstSegment()
            for (i in n2 - 2 downTo 0) {
                segGen.addNextSegment(simp2[i], true)
            }
        } else {
            // add original line
            segGen.addSegments(inputPts, false)

            //--------- compute points for left side of line
            // Simplify the appropriate side of the line before generating
            val simp1 = BufferInputLineSimplifier.simplify(inputPts, distTol)
            // MD - used for testing only (to eliminate simplification)
//      Coordinate[] simp1 = inputPts;
            val n1 = simp1.size - 1
            segGen.initSideSegments(simp1[0], simp1[1], Position.LEFT)
            segGen.addFirstSegment()
            for (i in 2..n1) {
                segGen.addNextSegment(simp1[i], true)
            }
        }
        segGen.addLastSegment()
        segGen.closeRing()
    }

    private fun computeOffsetCurve(
        inputPts: Array<Coordinate>,
        isRightSide: Boolean,
        segGen: OffsetSegmentGenerator
    ) {
        val distTol = simplifyTolerance(abs(distance))
        if (isRightSide) {
            //---------- compute points for right side of line
            // Simplify the appropriate side of the line before generating
            val simp2 = BufferInputLineSimplifier.simplify(inputPts, -distTol)
            // MD - used for testing only (to eliminate simplification)
            //    Coordinate[] simp2 = inputPts;
            val n2 = simp2.size - 1

            // since we are traversing line in opposite order, offset position is still LEFT
            segGen.initSideSegments(simp2[n2], simp2[n2 - 1], Position.LEFT)
            segGen.addFirstSegment()
            for (i in n2 - 2 downTo 0) {
                segGen.addNextSegment(simp2[i], true)
            }
        } else {
            //--------- compute points for left side of line
            // Simplify the appropriate side of the line before generating
            val simp1 = BufferInputLineSimplifier.simplify(inputPts, distTol)
            // MD - used for testing only (to eliminate simplification)
//      Coordinate[] simp1 = inputPts;
            val n1 = simp1.size - 1
            segGen.initSideSegments(simp1[0], simp1[1], Position.LEFT)
            segGen.addFirstSegment()
            for (i in 2..n1) {
                segGen.addNextSegment(simp1[i], true)
            }
        }
        segGen.addLastSegment()
    }

    private fun computeRingBufferCurve(
        inputPts: Array<Coordinate>,
        side: Int,
        segGen: OffsetSegmentGenerator
    ) {
        // simplify input line to improve performance
        var distTol = simplifyTolerance(distance)
        // ensure that correct side is simplified
        if (side == Position.RIGHT) distTol = -distTol
        val simp = BufferInputLineSimplifier.simplify(inputPts, distTol)
        //    Coordinate[] simp = inputPts;
        val n = simp.size - 1
        segGen.initSideSegments(simp[n - 1], simp[0], side)
        for (i in 1..n) {
            val addStartPoint = i != 1
            segGen.addNextSegment(simp[i], addStartPoint)
        }
        segGen.closeRing()
    }

    companion object {
        private fun copyCoordinates(pts: Array<Coordinate>): Array<Coordinate> {
            val copy = arrayOfNulls<Coordinate>(pts.size)
            for (i in copy.indices) {
                copy[i] = Coordinate(pts[i]!!)
            }
            return copy.requireNoNulls()
        }
    }
}