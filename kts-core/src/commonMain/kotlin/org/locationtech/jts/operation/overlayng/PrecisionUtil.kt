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
import org.locationtech.jts.io.OrdinateFormat
import org.locationtech.jts.legacy.Math.abs
import org.locationtech.jts.legacy.Math.log
import org.locationtech.jts.legacy.Math.pow
import org.locationtech.jts.math.MathUtil.max

/**
 * Functions for computing precision model scale factors
 * that ensure robust geometry operations.
 * In particular, these can be used to
 * automatically determine appropriate scale factors for operations
 * using limited-precision noding (such as [OverlayNG]).
 *
 * WARNING: the `inherentScale` and `robustScale`
 * functions can be very slow, due to the method used to determine
 * number of decimal places of a number.
 * These are not recommended for production use.
 *
 * @author Martin Davis
 */
object PrecisionUtil {
    /**
     * A number of digits of precision which leaves some computational "headroom"
     * to ensure robust evaluation of certain double-precision floating point geometric operations.
     *
     * This value should be less than the maximum decimal precision of double-precision values (16).
     */
    var MAX_ROBUST_DP_DIGITS = 14

    /**
     * Determines a precision model to
     * use for robust overlay operations.
     * The precision scale factor is chosen to maximize
     * output precision while avoiding round-off issues.
     *
     *
     * NOTE: this is a heuristic determination, so is not guaranteed to
     * eliminate precision issues.
     *
     *
     * WARNING: this is very slow.
     *
     * @param a a geometry
     * @param b a geometry
     * @return a suitable precision model for overlay
     */
    fun robustPM(a: Geometry, b: Geometry?): PrecisionModel {
        val scale = robustScale(a, b)
        return PrecisionModel(scale)
    }

    /**
     * Computes a safe scale factor for a numeric value.
     * A safe scale factor ensures that rounded
     * number has no more than [.MAX_ROBUST_DP_DIGITS]
     * digits of precision.
     *
     * @param value a numeric value
     * @return a safe scale factor for the value
     */
    fun safeScale(value: Double): Double {
        return precisionScale(value, MAX_ROBUST_DP_DIGITS)
    }

    /**
     * Computes a safe scale factor for a geometry.
     * A safe scale factor ensures that the rounded
     * ordinates have no more than [.MAX_ROBUST_DP_DIGITS]
     * digits of precision.
     *
     * @param geom a geometry
     * @return a safe scale factor for the geometry ordinates
     */
    fun safeScale(geom: Geometry): Double {
        return safeScale(maxBoundMagnitude(geom.envelopeInternal))
    }

    /**
     * Computes a safe scale factor for two geometries.
     * A safe scale factor ensures that the rounded
     * ordinates have no more than [.MAX_ROBUST_DP_DIGITS]
     * digits of precision.
     *
     * @param a a geometry
     * @param b a geometry (which may be null)
     * @return a safe scale factor for the geometry ordinates
     */
    fun safeScale(a: Geometry?, b: Geometry?): Double {
        var maxBnd =
            maxBoundMagnitude(a!!.envelopeInternal)
        if (b != null) {
            val maxBndB =
                maxBoundMagnitude(b.envelopeInternal)
            maxBnd = org.locationtech.jts.legacy.Math.max(maxBnd, maxBndB)
        }
        return safeScale(maxBnd)
    }

    /**
     * Determines the maximum magnitude (absolute value) of the bounds of an
     * of an envelope.
     * This is equal to the largest ordinate value
     * which must be accommodated by a scale factor.
     *
     * @param env an envelope
     * @return the value of the maximum bound magnitude
     */
    private fun maxBoundMagnitude(env: Envelope): Double {
        return max(
            abs(env.maxX),
            abs(env.maxY),
            abs(env.minX),
            abs(env.minY)
        )
    }
    // TODO: move to PrecisionModel?
    /**
     * Computes the scale factor which will
     * produce a given number of digits of precision (significant digits)
     * when used to round the given number.
     *
     *
     * For example: to provide 5 decimal digits of precision
     * for the number 123.456 the precision scale factor is 100;
     * for 3 digits of precision the scale factor is 1;
     * for 2 digits of precision the scale factor is 0.1.
     *
     *
     * Rounding to the scale factor can be performed with [PrecisionModel.round]
     *
     * @param value a number to be rounded
     * @param precisionDigits the number of digits of precision required
     * @return scale factor which provides the required number of digits of precision
     *
     * @see PrecisionModel.round
     */
    private fun precisionScale(
        value: Double, precisionDigits: Int
    ): Double {
        // the smallest power of 10 greater than the value
        val magnitude: Int = (log(value) / log(10.0) + 1.0).toInt()
        val precDigits = precisionDigits - magnitude
        return pow(10.0, precDigits.toDouble())
    }

    /**
     * Computes the inherent scale of a number.
     * The inherent scale is the scale factor for rounding
     * which preserves **all** digits of precision
     * (significant digits)
     * present in the numeric value.
     * In other words, it is the scale factor which does not
     * change the numeric value when rounded:
     * <pre>
     * num = round( num, inherentScale(num) )
    </pre> *
     *
     * @param value a number
     * @return the inherent scale factor of the number
     */
    fun inherentScale(value: Double): Double {
        val numDec = numberOfDecimals(value)
        return pow(10.0, numDec.toDouble())
    }

    /**
     * Computes the inherent scale of a geometry.
     * The inherent scale is the scale factor for rounding
     * which preserves **all** digits of precision
     * (significant digits)
     * present in the geometry ordinates.
     *
     *
     * This is the maximum inherent scale
     * of all ordinate values in the geometry.
     *
     *
     * WARNING: this is very slow.
     *
     * @param geom geometry
     * @return inherent scale of a geometry
     */
    fun inherentScale(geom: Geometry): Double {
        val scaleFilter = InherentScaleFilter()
        geom.apply(scaleFilter)
        return scaleFilter.scale
    }

    /**
     * Computes the inherent scale of two geometries.
     * The inherent scale is the scale factor for rounding
     * which preserves **all** digits of precision
     * (significant digits)
     * present in the geometry ordinates.
     *
     *
     * This is the maximum inherent scale
     * of all ordinate values in the geometries.
     *
     *
     * WARNING: this is very slow.
     *
     * @param a a geometry
     * @param b a geometry
     * @return the inherent scale factor of the two geometries
     */
    fun inherentScale(a: Geometry, b: Geometry?): Double {
        var scale = inherentScale(a)
        if (b != null) {
            val scaleB = inherentScale(b)
            scale = org.locationtech.jts.legacy.Math.max(scale, scaleB)
        }
        return scale
    }
    /*
  // this doesn't work
  private static int BADnumDecimals(double value) {
    double val = Math.abs(value);
    double frac = val - Math.floor(val);
    int numDec = 0;
    while (frac > 0 && numDec < MAX_PRECISION_DIGITS) {
      double mul10 = 10 * frac;
      frac = mul10 - Math.floor(mul10);
      numDec ++;
    }
    return numDec;
  }
  */
    /**
     * Determines the
     * number of decimal places represented in a double-precision
     * number (as determined by Java).
     * This uses the Java double-precision print routine
     * to determine the number of decimal places,
     * This is likely not optimal for performance,
     * but should be accurate and portable.
     *
     * @param value a numeric value
     * @return the number of decimal places in the value
     */
    private fun numberOfDecimals(value: Double): Int {
        /**
         * Ensure that scientific notation is NOT used
         * (it would skew the number of fraction digits)
         */
        val s = OrdinateFormat.DEFAULT.format(value)
        if (s.endsWith(".0")) return 0
        val len = s.length
        val decIndex = s.indexOf('.')
        return if (decIndex <= 0) 0 else len - decIndex - 1
    }

    /**
     * Determines a precision model to
     * use for robust overlay operations for one geometry.
     * The precision scale factor is chosen to maximize
     * output precision while avoiding round-off issues.
     *
     *
     * NOTE: this is a heuristic determination, so is not guaranteed to
     * eliminate precision issues.
     *
     *
     * WARNING: this is very slow.
     *
     * @param a a geometry
     * @return a suitable precision model for overlay
     */
    fun robustPM(a: Geometry): PrecisionModel {
        val scale = robustScale(a)
        return PrecisionModel(scale)
    }

    /**
     * Determines a scale factor which maximizes
     * the digits of precision and is
     * safe to use for overlay operations.
     * The robust scale is the minimum of the
     * inherent scale and the safe scale factors.
     *
     *
     * WARNING: this is very slow.
     *
     * @param a a geometry
     * @param b a geometry
     * @return a scale factor for use in overlay operations
     */
    fun robustScale(a: Geometry, b: Geometry?): Double {
        val inherentScale = inherentScale(a, b)
        val safeScale = safeScale(a, b)
        return robustScale(inherentScale, safeScale)
    }

    /**
     * Determines a scale factor which maximizes
     * the digits of precision and is
     * safe to use for overlay operations.
     * The robust scale is the minimum of the
     * inherent scale and the safe scale factors.
     *
     * @param a a geometry
     * @return a scale factor for use in overlay operations
     */
    fun robustScale(a: Geometry): Double {
        val inherentScale = inherentScale(a)
        val safeScale = safeScale(a)
        return robustScale(inherentScale, safeScale)
    }

    private fun robustScale(inherentScale: Double, safeScale: Double): Double {
        /**
         * Use safe scale if lower,
         * since it is important to preserve some precision for robustness
         */
        return if (inherentScale <= safeScale) {
            inherentScale
        } else safeScale
        //System.out.println("Scale = " + scale);
    }

    /**
     * Applies the inherent scale calculation
     * to every ordinate in a geometry.
     *
     *
     * WARNING: this is very slow.
     *
     * @author Martin Davis
     */
    private class InherentScaleFilter : CoordinateFilter {
        var scale = 0.0
            private set

        override fun filter(coord: Coordinate?) {
            updateScaleMax(coord!!.x)
            updateScaleMax(coord.y)
        }

        private fun updateScaleMax(value: Double) {
            val scaleVal = inherentScale(value)
            if (scaleVal > scale) {
                //System.out.println("Value " + value + " has scale: " + scaleVal);
                scale = scaleVal
            }
        }
    }
}