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
package org.locationtech.jts.operation.buffer.validate

import org.locationtech.jts.geom.*
import org.locationtech.jts.util.Debug
import kotlin.jvm.JvmStatic

/**
 * Validates that the result of a buffer operation
 * is geometrically correct, within a computed tolerance.
 *
 * This is a heuristic test, and may return false positive results
 * (I.e. it may fail to detect an invalid result.)
 * It should never return a false negative result, however
 * (I.e. it should never report a valid result as invalid.)
 *
 * This test may be (much) more expensive than the original
 * buffer computation.
 *
 * @author Martin Davis
 */
class BufferResultValidator(private val input: Geometry, private val distance: Double, private val result: Geometry) {
    private var isValid = true
    var errorMessage: String? = null
        private set
    var errorLocation: Coordinate? = null
        private set

    /**
     * Gets a geometry which indicates the location and nature of a validation failure.
     *
     * If the failure is due to the buffer curve being too far or too close
     * to the input, the indicator is a line segment showing the location and size
     * of the discrepancy.
     *
     * @return a geometric error indicator
     * or null if no error was found
     */
    var errorIndicator: Geometry? = null
        private set

    fun isValid(): Boolean {
        checkPolygonal()
        if (!isValid) return isValid
        checkExpectedEmpty()
        if (!isValid) return isValid
        checkEnvelope()
        if (!isValid) return isValid
        checkArea()
        if (!isValid) return isValid
        checkDistance()
        return isValid
    }

    private fun report(checkName: String) {
        if (!VERBOSE) return
        Debug.println(
            "Check " + checkName + ": "
                    + if (isValid) "passed" else "FAILED"
        )
    }

    private fun checkPolygonal() {
        if (!(result is Polygon
                    || result is MultiPolygon)
        ) isValid = false
        errorMessage = "Result is not polygonal"
        errorIndicator = result
        report("Polygonal")
    }

    private fun checkExpectedEmpty() {
        // can't check areal features
        if (input.dimension >= 2) return
        // can't check positive distances
        if (distance > 0.0) return

        // at this point can expect an empty result
        if (!result.isEmpty) {
            isValid = false
            errorMessage = "Result is non-empty"
            errorIndicator = result
        }
        report("ExpectedEmpty")
    }

    private fun checkEnvelope() {
        if (distance < 0.0) return
        var padding = distance * MAX_ENV_DIFF_FRAC
        if (padding == 0.0) padding = 0.001
        val expectedEnv = Envelope(input.envelopeInternal)
        expectedEnv.expandBy(distance)
        val bufEnv = Envelope(result.envelopeInternal)
        bufEnv.expandBy(padding)
        if (!bufEnv.contains(expectedEnv)) {
            isValid = false
            errorMessage = "Buffer envelope is incorrect"
            errorIndicator = input.factory.toGeometry(bufEnv)
        }
        report("Envelope")
    }

    private fun checkArea() {
        val inputArea = input.area
        val resultArea = result.area
        if (distance > 0.0
            && inputArea > resultArea
        ) {
            isValid = false
            errorMessage = "Area of positive buffer is smaller than input"
            errorIndicator = result
        }
        if (distance < 0.0
            && inputArea < resultArea
        ) {
            isValid = false
            errorMessage = "Area of negative buffer is larger than input"
            errorIndicator = result
        }
        report("Area")
    }

    private fun checkDistance() {
        val distValid = BufferDistanceValidator(input, distance, result)
        if (!distValid.isValid()) {
            isValid = false
            errorMessage = distValid.errorMessage
            errorLocation = distValid.errorLocation
            errorIndicator = distValid.errorIndicator
        }
        report("Distance")
    }

    companion object {
        private const val VERBOSE = false

        /**
         * Maximum allowable fraction of buffer distance the
         * actual distance can differ by.
         * 1% sometimes causes an error - 1.2% should be safe.
         */
        private const val MAX_ENV_DIFF_FRAC = .012
        @JvmStatic
        fun isValid(g: Geometry, distance: Double, result: Geometry): Boolean {
            val validator = BufferResultValidator(g, distance, result)
            return if (validator.isValid()) true else false
        }

        /**
         * Checks whether the geometry buffer is valid,
         * and returns an error message if not.
         *
         * @param g
         * @param distance
         * @param result
         * @return an appropriate error message
         * or null if the buffer is valid
         */
        fun isValidMsg(g: Geometry, distance: Double, result: Geometry): String? {
            val validator = BufferResultValidator(g, distance, result)
            return if (!validator.isValid()) validator.errorMessage else null
        }
    }
}