/*
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Some of the code has been derived from GWT 2.9.0, which came with the following license:
 *
 * Copyright 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.locationtech.jts.legacy.format

import org.locationtech.jts.legacy.Math

class DecimalFormat(s: String, val symbols: DecimalFormatSymbols) {
    // The multiplier for use in percent, per mille, etc.
    val multiplier = 1.0

    /**
     * Holds the current decimal position during one call to
     * [.format].
     */
    var decimalPosition = 0

    /**
     * Forces the decimal separator to always appear in a formatted number.
     */
    val decimalSeparatorAlwaysShown = false

    /**
     * Holds the current digits length during one call to
     * [.format].
     */
    var digitsLength = 0

    /**
     * Holds the current exponent during one call to
     * [.format].
     */
    // ToDo: Transient incompatible with native
//    @Transient
    private var exponent = 0

    private val minimumFractionDigits = 0

    var maximumFractionDigits = 3 // invariant, >= minFractionDigits.
//    var maximumFractionalDigits: Int = 325

    private val minimumIntegerDigits = 1

    // True to force the use of exponential (i.e. scientific) notation.
    private val useExponentialNotation = false

    fun format(number: Double): String {
        var number = number
//        return ord.toString()

        if (Math.isNaN(number)) {
            return symbols.NaN
        }
        val isNegative = (number < 0.0
                || number == 0.0 && 1 / number < 0.0)
        if (isNegative) {
            number = -number
        }
        val buf = StringBuilder()
        if (Math.isInfinite(number)) {
            buf.append(if (isNegative) symbols.negativePrefix else symbols.positivePrefix)
            buf.append(symbols.infinity)
            buf.append(if (isNegative) symbols.negativeSuffix else symbols.positiveSuffix)
            return buf.toString()
        }
        number *= multiplier
        var scale: Int = toScaledString(buf, number)

        // pre-round value to deal with .15 being represented as .149999... etc
        // check at 3 more digits than will be required in the output

        // pre-round value to deal with .15 being represented as .149999... etc
        // check at 3 more digits than will be required in the output
        val preRound = buf.length + scale + maximumFractionDigits + 3
        if (preRound > 0 && preRound < buf.length && buf[preRound] == '9') {
            propagateCarry(buf, preRound - 1)
            scale += buf.length - preRound
            buf.deleteRange(preRound, preRound + buf.length)
        }

        format(isNegative, buf, scale)
        return buf.toString()

    }

    /**
     * Appends a scaled string representation to a buffer, returning the scale
     * (which is the number of places to the right of the end of the string the
     * decimal point should be moved -- i.e., 3.5 would be added to the buffer
     * as "35" and a returned scale of -1).
     *
     * @param buf
     * @param val
     * @return scale to apply to the result
     */
    // @VisibleForTesting
    fun toScaledString(buf: StringBuilder, `val`: Double): Int {
        val startLen = buf.length
        buf.append(toPrecision(`val`, 20))
        var scale = 0

        // remove exponent if present, adjusting scale
        var expIdx = buf.indexOf("e", startLen)
        if (expIdx < 0) {
            expIdx = buf.indexOf("E", startLen)
        }
        if (expIdx >= 0) {
            var expDigits = expIdx + 1
            if (expDigits < buf.length && buf[expDigits] == '+') {
                ++expDigits
            }
            if (expDigits < buf.length) {
                scale = buf.substring(expDigits).toInt()
            }
            buf.deleteRange(expIdx, expIdx + buf.length)
        }

        // remove decimal point if present, adjusting scale
        val dot = buf.indexOf(".", startLen)
        if (dot >= 0) {
            buf.deleteAt(dot)
            scale -= buf.length - dot
        }
        return scale
    }

    /**
     * Remove excess leading zeros or add some if we don't have enough.
     *
     * @param digits
     */
    private fun processLeadingZeros(digits: StringBuilder) {
        // make sure we have enough trailing zeros
        if (decimalPosition > digitsLength) {
            while (digitsLength < decimalPosition) {
                digits.append('0')
                ++digitsLength
            }
        }
        if (!useExponentialNotation) {
            // make sure we have the right number of leading zeros
            if (decimalPosition < minimumIntegerDigits) {
                // add leading zeros
                val prefix = StringBuilder()
                while (decimalPosition < minimumIntegerDigits) {
                    prefix.append('0')
                    ++decimalPosition
                    ++digitsLength
                }
                digits.insert(0, prefix)
            } else if (decimalPosition > minimumIntegerDigits) {
                // trim excess leading zeros
                var strip: Int = decimalPosition - minimumIntegerDigits
                for (i in 0 until strip) {
                    if (digits[i] != '0') {
                        strip = i
                        break
                    }
                }
                if (strip > 0) {
                    digits.deleteRange(0, strip)
                    digitsLength -= strip
                    decimalPosition -= strip
                }
            }
        }
    }

    /**
     * Propagate a carry from incrementing the `i+1`'th digit.
     *
     * @param digits
     * @param i digit to start incrementing
     */
    private fun propagateCarry(digits: StringBuilder, i: Int) {
        var i = i
        var carry = true
        while (carry && i >= 0) {
            val digit = digits[i]
            if (digit == '9') {
                // set this to zero and keep going
                digits.set(i--, '0')
            } else {
                digits.set(i, (digit.toInt() + 1).toChar())
                carry = false
            }
        }
        if (carry) {
            // ran off the front, prepend a 1
            digits.insert(0, '1')
            ++decimalPosition
            ++digitsLength
        }
    }

    /**
     * Format a number with its significant digits already represented in string
     * form.  This is done so both double and BigInteger/Decimal formatting can
     * share code without requiring all users to pay the code size penalty for
     * BigDecimal/etc.
     *
     * Example values passed in:
     *
     *  * -13e2
     * <br></br>`isNegative=true, digits="13", scale=2`
     *  * 3.14158
     * <br></br>`isNegative=false, digits="314158", scale=-5`
     *  * .0001
     * <br></br>`isNegative=false, digits="1" ("0001" would be ok), scale=-4`
     *
     * @param isNegative true if the value to be formatted is negative
     * @param digits a StringBuilder containing just the significant digits in
     * the value to be formatted, the formatted result will be left here
     * @param scale the number of places to the right the decimal point should
     * be moved in the digit string -- negative means the value contains
     * fractional digits
     */
    protected fun format(isNegative: Boolean, digits: StringBuilder, scale: Int) {
        //        val groupingSeparator: Char
//        if (isCurrencyFormat) {
//            decimalSeparator = numberConstants.monetarySeparator().charAt(0)
//            groupingSeparator = numberConstants.monetaryGroupingSeparator().charAt(0)
//        } else {
        val decimalSeparator: Char = symbols.decimalSeparator
//            groupingSeparator = numberConstants.groupingSeparator().charAt(0)
//        }
//
//        // Set these transient fields, which will be adjusted/used by the routines
//        // called in this method.
        exponent = 0
        digitsLength = digits.length
        decimalPosition = digitsLength + scale
//        var useExponent: Boolean = this.useExponentialNotation
//        val currentGroupingSize: Int = this.groupingSize
//        if (decimalPosition > 1024) {
//            // force really large numbers to be in exponential form
//            useExponent = true
//        }
//        if (useExponent) {
//            computeExponent(digits)
//        }
        processLeadingZeros(digits)
        roundValue(digits)
//        insertGroupingSeparators(digits, groupingSeparator, currentGroupingSize)
        adjustFractionDigits(digits)
        addZeroAndDecimal(digits, decimalSeparator)
//        if (useExponent) {
//            addExponent(digits)
//            // the above call has invalidated digitsLength == digits.length()
//        }
//        val zeroChar: Char = numberConstants.zeroDigit().charAt(0)
//        if (zeroChar != '0') {
//            localizeDigits(digits, zeroChar)
//        }
//
//        // add prefix/suffix
        digits.insert(0, if (isNegative) symbols.negativePrefix else symbols.positivePrefix)
        digits.append(if (isNegative) symbols.negativeSuffix else symbols.positiveSuffix)
    }

    /**
     * @param digits
     * @param decimalSeparator
     */
    private fun addZeroAndDecimal(digits: StringBuilder, decimalSeparator: Char) {
        // add zero and decimal point if required
        if (digitsLength == 0) {
            digits.insert(0, '0')
            ++decimalPosition
            ++digitsLength
        }
        if (decimalPosition < digitsLength || decimalSeparatorAlwaysShown) {
            digits.insert(decimalPosition, decimalSeparator)
            ++digitsLength
        }
    }

    /**
     * Adjust the fraction digits, adding trailing zeroes if necessary or removing
     * excess trailing zeroes.
     *
     * @param digits
     */
    private fun adjustFractionDigits(digits: StringBuilder) {
        // adjust fraction digits as required
        val requiredDigits: Int = decimalPosition + minimumFractionDigits
        if (digitsLength < requiredDigits) {
            // add trailing zeros
            while (digitsLength < requiredDigits) {
                digits.append('0')
                ++digitsLength
            }
        } else {
            // remove excess trailing zeros
            var toRemove = decimalPosition + maximumFractionDigits
            if (toRemove > digitsLength) {
                toRemove = digitsLength
            }
            while (toRemove > requiredDigits
                && digits[toRemove - 1] == '0'
            ) {
                --toRemove
            }
            if (toRemove < digitsLength) {
                digits.deleteRange(toRemove, toRemove + digitsLength)
                digitsLength = toRemove
            }
        }
    }

    /**
     * Round the value at the requested place, propagating any carry backward.
     *
     * @param digits
     */
    private fun roundValue(digits: StringBuilder) {
        // TODO(jat): other rounding modes?
        if (digitsLength > decimalPosition + maximumFractionDigits
            && digits[decimalPosition + maximumFractionDigits] >= '5'
        ) {
            val i = decimalPosition + maximumFractionDigits - 1
            propagateCarry(digits, i)
        }
    }

    /**
     * Convert a double to a string with `digits` precision.  The resulting
     * string may still be in exponential notation.
     *
     * @param d double value
     * @param digits number of digits of precision to include
     * @return non-localized string representation of `d`
     */
    private fun toPrecision(d: Double, digits: Int): String? {
        return d.toString()
//        return d.round(digits).toString()
//        return d.toPrecision(digits);
    }

//    fun Double.round(decimals: Int): Double {
//        var multiplier = 1.0
//        repeat(decimals) { multiplier *= 10 }
//        return Math.round(this * multiplier) / multiplier
//    }
}