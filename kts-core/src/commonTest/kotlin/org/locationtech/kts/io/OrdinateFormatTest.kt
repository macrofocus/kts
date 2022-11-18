package org.locationtech.kts.io

import org.locationtech.jts.io.OrdinateFormat
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals

class OrdinateFormatTest {
    @Test
    fun testLargeNumber() {
        // ensure scientific notation is not used
        checkFormat(1234567890.0, "1234567890")
    }

    @Test
    fun testVeryLargeNumber() {
        // ensure scientific notation is not used
        // note output is rounded since it exceeds double precision accuracy
        checkFormat(12345678901234567890.0, "12345678901234567000")
    }

    @Test
    fun testDecimalPoint() {
        checkFormat(1.123, "1.123")
    }

    @Test
    fun testNegative() {
        checkFormat(-1.123, "-1.123")
    }

    @Test
    fun testFractionDigits() {
        checkFormat(1.123456789012345, "1.123456789012345")
        checkFormat(0.0123456789012345, "0.0123456789012345")
    }

    @Test
    fun testLimitedFractionDigits2() {
        checkFormat(1.123456789012345, 2, "1.12")
        checkFormat(1.123456789012345, 3, "1.123")
        checkFormat(1.123456789012345, 4, "1.1235")
        checkFormat(1.123456789012345, 5, "1.12346")
        checkFormat(1.123456789012345, 6, "1.123457")
    }

    @Test
    fun testMaximumFractionDigits() {
        checkFormat(0.0000000000123456789012345, "0.0000000000123456789012345")
    }

    @Test
    fun testPi() {
        checkFormat(PI, "3.141592653589793")
    }

    @Test
    fun testNaN() {
        checkFormat(Double.NaN, "NaN")
    }

    @Test
    fun testInf() {
        checkFormat(Double.POSITIVE_INFINITY, "Inf")
        checkFormat(Double.NEGATIVE_INFINITY, "-Inf")
    }

    private fun checkFormat(d: Double, expected: String) {
        assertEquals(expected, OrdinateFormat.DEFAULT.format(d))
    }

    private fun checkFormat(d: Double, maxFractionDigits: Int, expected: String) {
        val format = OrdinateFormat.create(maxFractionDigits)
        val actual = format.format(d)
        assertEquals(expected, actual)
    }
}