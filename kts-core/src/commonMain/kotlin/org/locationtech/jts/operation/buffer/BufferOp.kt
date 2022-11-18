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

import org.locationtech.jts.geom.*
import org.locationtech.jts.legacy.Math.log
import org.locationtech.jts.legacy.Math.pow
import org.locationtech.jts.math.MathUtil.max
import org.locationtech.jts.noding.Noder
import org.locationtech.jts.noding.ScaledNoder
import org.locationtech.jts.noding.snapround.SnapRoundingNoder
import kotlin.jvm.JvmStatic
import kotlin.math.abs

//import debug.*;
/**
 * Computes the buffer of a geometry, for both positive and negative buffer distances.
 *
 * In GIS, the positive (or negative) buffer of a geometry is defined as
 * the Minkowski sum (or difference) of the geometry
 * with a circle of radius equal to the absolute value of the buffer distance.
 * In the CAD/CAM world buffers are known as *offset curves*.
 * In morphological analysis the
 * operation of positive and negative buffering
 * is referred to as *erosion* and *dilation*
 *
 * The buffer operation always returns a polygonal result.
 * The negative or zero-distance buffer of lines and points is always an empty [Polygon].
 *
 * Since true buffer curves may contain circular arcs,
 * computed buffer polygons are only approximations to the true geometry.
 * The user can control the accuracy of the approximation by specifying
 * the number of linear segments used to approximate arcs.
 * This is specified via [BufferParameters.setQuadrantSegments] or [.setQuadrantSegments].
 *
 * The **end cap style** of a linear buffer may be [specified][BufferParameters.setEndCapStyle]. The
 * following end cap styles are supported:
 *
 *  * [BufferParameters.CAP_ROUND] - the usual round end caps
 *  * [BufferParameters.CAP_FLAT] - end caps are truncated flat at the line ends
 *  * [BufferParameters.CAP_SQUARE] - end caps are squared off at the buffer distance beyond the line ends
 *
 *
 * The **join style** of the corners in a buffer may be [specified][BufferParameters.setJoinStyle]. The
 * following join styles are supported:
 *
 *  * [BufferParameters.JOIN_ROUND] - the usual round join
 *  * [BufferParameters.JOIN_MITRE] - corners are "sharp" (up to a [distance limit][BufferParameters.getMitreLimit])
 *  * [BufferParameters.JOIN_BEVEL] - corners are beveled (clipped off).
 *
 *
 * The buffer algorithm may perform simplification on the input to increase performance.
 * The simplification is performed a way that always increases the buffer area
 * (so that the simplified input covers the original input).
 * The degree of simplification can be [specified][BufferParameters.setSimplifyFactor],
 * with a [default][BufferParameters.DEFAULT_SIMPLIFY_FACTOR] used otherwise.
 * Note that if the buffer distance is zero then so is the computed simplify tolerance,
 * no matter what the simplify factor.
 *
 * Buffer results are always valid geometry.
 * Given this, computing a zero-width buffer of an invalid polygonal geometry is
 * an effective way to "validify" the geometry.
 * Note however that in the case of self-intersecting "bow-tie" geometries,
 * only the largest enclosed area will be retained.
 *
 * @version 1.7
 */
class BufferOp {
    private var argGeom: Geometry?
    private var distance = 0.0
    private var bufParams: BufferParameters =
        BufferParameters()
    private var resultGeometry: Geometry? = null
    private var saveException // debugging only
            : RuntimeException? = null
    private var isInvertOrientation = false

    /**
     * Initializes a buffer computation for the given geometry
     *
     * @param g the geometry to buffer
     */
    constructor(g: Geometry?) {
        argGeom = g
    }

    /**
     * Initializes a buffer computation for the given geometry
     * with the given set of parameters
     *
     * @param g the geometry to buffer
     * @param bufParams the buffer parameters to use
     */
    constructor(g: Geometry?, bufParams: BufferParameters) {
        argGeom = g
        this.bufParams = bufParams
    }

    /**
     * Specifies the end cap style of the generated buffer.
     * The styles supported are [BufferParameters.CAP_ROUND], [BufferParameters.CAP_FLAT], and [BufferParameters.CAP_SQUARE].
     * The default is CAP_ROUND.
     *
     * @param endCapStyle the end cap style to specify
     */
    fun setEndCapStyle(endCapStyle: Int) {
        bufParams.setEndCapStyle(endCapStyle)
    }

    /**
     * Sets the number of line segments in a quarter-circle
     * used to approximate angle fillets for round end caps and joins.
     *
     * @param quadrantSegments the number of segments in a fillet for a quadrant
     */
    fun setQuadrantSegments(quadrantSegments: Int) {
        bufParams.setQuadrantSegments(quadrantSegments)
    }

    /**
     * Returns the buffer computed for a geometry for a given buffer distance.
     *
     * @param distance the buffer distance
     * @return the buffer of the input geometry
     */
    fun getResultGeometry(distance: Double): Geometry {
        this.distance = distance
        computeGeometry()
        return resultGeometry!!
    }

    private fun computeGeometry() {
        bufferOriginalPrecision()
        if (resultGeometry != null) return
        val argPM = argGeom!!.factory.precisionModel
        if (argPM.type == PrecisionModel.FIXED) bufferFixedPrecision(argPM) else bufferReducedPrecision()
    }

    private fun bufferReducedPrecision() {
        // try and compute with decreasing precision
        for (precDigits in MAX_PRECISION_DIGITS downTo 0) {
            try {
                bufferReducedPrecision(precDigits)
            } catch (ex: TopologyException) {
                // update the saved exception to reflect the new input geometry
                saveException = ex
                // don't propagate the exception - it will be detected by fact that resultGeometry is null
            }
            if (resultGeometry != null) return
        }
        throw saveException!!
    }

    private fun bufferReducedPrecision(precisionDigits: Int) {
        val sizeBasedScaleFactor = precisionScaleFactor(argGeom, distance, precisionDigits)
        //    System.out.println("recomputing with precision scale factor = " + sizeBasedScaleFactor);
        val fixedPM = PrecisionModel(sizeBasedScaleFactor)
        bufferFixedPrecision(fixedPM)
    }

    private fun bufferOriginalPrecision() {
        try {
            // use fast noding by default
            val bufBuilder: BufferBuilder = createBufferBullder()
            resultGeometry = bufBuilder.buffer(argGeom, distance)
        } catch (ex: RuntimeException) {
            saveException = ex
            // don't propagate the exception - it will be detected by fact that resultGeometry is null

            // testing ONLY - propagate exception
            //throw ex;
        }
    }

    private fun createBufferBullder(): BufferBuilder {
        val bufBuilder: BufferBuilder =
            BufferBuilder(bufParams)
        bufBuilder.setInvertOrientation(isInvertOrientation)
        return bufBuilder
    }

    private fun bufferFixedPrecision(fixedPM: PrecisionModel) {
        //System.out.println("recomputing with precision scale factor = " + fixedPM);

        /*
     * Snap-Rounding provides both robustness
     * and a fixed output precision.
     * 
     * SnapRoundingNoder does not require rounded input, 
     * so could be used by itself.
     * But using ScaledNoder may be faster, since it avoids
     * rounding within SnapRoundingNoder.
     * (Note this only works for buffering, because
     * ScaledNoder may invalidate topology.)
     */
        val snapNoder: Noder = SnapRoundingNoder(PrecisionModel(1.0))
        val noder: Noder = ScaledNoder(snapNoder, fixedPM.getScale())
        val bufBuilder: BufferBuilder = createBufferBullder()
        bufBuilder.setWorkingPrecisionModel(fixedPM)
        bufBuilder.setNoder(noder)
        // this may throw an exception, if robustness errors are encountered
        resultGeometry = bufBuilder.buffer(argGeom, distance)
    }

    companion object {
        /**
         * Specifies a round line buffer end cap style.
         */
        @Deprecated("use BufferParameters")
        val CAP_ROUND: Int = BufferParameters.Companion.CAP_ROUND

        /**
         * Specifies a butt (or flat) line buffer end cap style.
         */
        @Deprecated("use BufferParameters")
        val CAP_BUTT: Int = BufferParameters.Companion.CAP_FLAT

        /**
         * Specifies a butt (or flat) line buffer end cap style.
         */
        @Deprecated("use BufferParameters")
        val CAP_FLAT: Int = BufferParameters.Companion.CAP_FLAT

        /**
         * Specifies a square line buffer end cap style.
         */
        @Deprecated("use BufferParameters")
        val CAP_SQUARE: Int = BufferParameters.Companion.CAP_SQUARE

        /**
         * A number of digits of precision which leaves some computational "headroom"
         * for floating point operations.
         *
         * This value should be less than the decimal precision of double-precision values (16).
         */
        private const val MAX_PRECISION_DIGITS = 12

        /**
         * Compute a scale factor to limit the precision of
         * a given combination of Geometry and buffer distance.
         * The scale factor is determined by
         * the number of digits of precision in the (geometry + buffer distance),
         * limited by the supplied `maxPrecisionDigits` value.
         *
         *
         * The scale factor is based on the absolute magnitude of the (geometry + buffer distance).
         * since this determines the number of digits of precision which must be handled.
         *
         * @param g the Geometry being buffered
         * @param distance the buffer distance
         * @param maxPrecisionDigits the max # of digits that should be allowed by
         * the precision determined by the computed scale factor
         *
         * @return a scale factor for the buffer computation
         */
        private fun precisionScaleFactor(
            g: Geometry?,
            distance: Double,
            maxPrecisionDigits: Int
        ): Double {
            val env = g!!.envelopeInternal
            val envMax = max(
                abs(env.maxX),
                abs(env.maxY),
                abs(env.minX),
                abs(env.minY)
            )
            val expandByDistance = if (distance > 0.0) distance else 0.0
            val bufEnvMax = envMax + 2 * expandByDistance

            // the smallest power of 10 greater than the buffer envelope
            val bufEnvPrecisionDigits: Int =
                (log(bufEnvMax) / log(10.0) + 1.0).toInt()
            val minUnitLog10 = maxPrecisionDigits - bufEnvPrecisionDigits
            return pow(10.0, minUnitLog10.toDouble())
        }
        /*
  private static double OLDprecisionScaleFactor(Geometry g,
      double distance,
    int maxPrecisionDigits)
  {
    Envelope env = g.getEnvelopeInternal();
    double envSize = Math.max(env.getHeight(), env.getWidth());
    double expandByDistance = distance > 0.0 ? distance : 0.0;
    double bufEnvSize = envSize + 2 * expandByDistance;

    // the smallest power of 10 greater than the buffer envelope
    int bufEnvLog10 = (int) (Math.log(bufEnvSize) / Math.log(10) + 1.0);
    int minUnitLog10 = bufEnvLog10 - maxPrecisionDigits;
    // scale factor is inverse of min Unit size, so flip sign of exponent
    double scaleFactor = Math.pow(10.0, -minUnitLog10);
    return scaleFactor;
  }
  */
        /**
         * Computes the buffer of a geometry for a given buffer distance.
         *
         * @param g the geometry to buffer
         * @param distance the buffer distance
         * @return the buffer of the input geometry
         */
        @JvmStatic
        fun bufferOp(g: Geometry, distance: Double): Geometry {
            val gBuf = BufferOp(g)
            //BufferDebug.saveBuffer(geomBuf);
            //BufferDebug.runCount++;
            return gBuf.getResultGeometry(distance)
        }

        /**
         * Computes the buffer for a geometry for a given buffer distance
         * and accuracy of approximation.
         *
         * @param g the geometry to buffer
         * @param distance the buffer distance
         * @param params the buffer parameters to use
         * @return the buffer of the input geometry
         */
        @JvmStatic
        fun bufferOp(
            g: Geometry?,
            distance: Double,
            params: BufferParameters
        ): Geometry? {
            val bufOp =
                BufferOp(g, params)
            return bufOp.getResultGeometry(distance)
        }

        /**
         * Computes the buffer for a geometry for a given buffer distance
         * and accuracy of approximation.
         *
         * @param g the geometry to buffer
         * @param distance the buffer distance
         * @param quadrantSegments the number of segments used to approximate a quarter circle
         * @return the buffer of the input geometry
         */
        fun bufferOp(g: Geometry, distance: Double, quadrantSegments: Int): Geometry {
            val bufOp = BufferOp(g)
            bufOp.setQuadrantSegments(quadrantSegments)
            return bufOp.getResultGeometry(distance)
        }

        /**
         * Computes the buffer for a geometry for a given buffer distance
         * and accuracy of approximation.
         *
         * @param g the geometry to buffer
         * @param distance the buffer distance
         * @param quadrantSegments the number of segments used to approximate a quarter circle
         * @param endCapStyle the end cap style to use
         * @return the buffer of the input geometry
         */
        fun bufferOp(
            g: Geometry,
            distance: Double,
            quadrantSegments: Int,
            endCapStyle: Int
        ): Geometry {
            val bufOp = BufferOp(g)
            bufOp.setQuadrantSegments(quadrantSegments)
            bufOp.setEndCapStyle(endCapStyle)
            return bufOp.getResultGeometry(distance)
        }

        /**
         * Buffers a geometry with distance zero.
         * The result can be computed using the maximum-signed-area orientation,
         * or by combining both orientations.
         *
         *
         * This can be used to fix an invalid polygonal geometry to be valid
         * (i.e. with no self-intersections).
         * For some uses (e.g. fixing the result of a simplification)
         * a better result is produced by using only the max-area orientation.
         * Other uses (e.g. fixing geometry) require both orientations to be used.
         *
         *
         * This function is for INTERNAL use only.
         *
         * @param geom the polygonal geometry to buffer by zero
         * @param isBothOrientations true if both orientations of input rings should be used
         * @return the buffered polygonal geometry
         */
        fun bufferByZero(geom: Geometry, isBothOrientations: Boolean): Geometry? {
            //--- compute buffer using maximum signed-area orientation
            val buf0 = geom.buffer(0.0)
            if (!isBothOrientations) return buf0

            //-- compute buffer using minimum signed-area orientation
            val op = BufferOp(geom)
            op.isInvertOrientation = true
            val buf0Inv = op.getResultGeometry(0.0)

            //-- the buffer results should be non-adjacent, so combining is safe
            return combine(buf0, buf0Inv)
        }

        /**
         * Combines the elements of two polygonal geometries together.
         * The input geometries must be non-adjacent, to avoid
         * creating an invalid result.
         *
         * @param poly0 a polygonal geometry (which may be empty)
         * @param poly1 a polygonal geometry (which may be empty)
         * @return a combined polygonal geometry
         */
        private fun combine(poly0: Geometry, poly1: Geometry?): Geometry? {
            // short-circuit - handles case where geometry is valid
            if (poly1!!.isEmpty) return poly0
            if (poly0.isEmpty) return poly1
            val polys: MutableList<Polygon> = ArrayList<Polygon>()
            extractPolygons(poly0, polys)
            extractPolygons(poly1, polys)
            return if (polys.size == 1) polys[0] else poly0.factory.createMultiPolygon(
                GeometryFactory.toPolygonArray(
                    polys
                )
            )
        }

        private fun extractPolygons(poly0: Geometry?, polys: MutableList<Polygon>) {
            for (i in 0 until poly0!!.numGeometries) {
                polys.add(poly0.getGeometryN(i) as Polygon)
            }
        }
    }
}