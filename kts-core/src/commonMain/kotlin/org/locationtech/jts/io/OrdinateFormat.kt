/*
 * Copyright (c) 2019 Martin Davis.
 * Copyright (c) 2020 Macrofocus GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.io

import org.locationtech.jts.legacy.Math
import org.locationtech.jts.legacy.format.DecimalFormat
import org.locationtech.jts.legacy.format.DecimalFormatSymbols
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.jvm.Synchronized

/**
 * Formats numeric values for ordinates
 * in a consistent, accurate way.
 *
 * The format has the following characteristics:
 *
 *  * It is consistent in all locales (in particular, the decimal separator is always a period)
 *  * Scientific notation is never output, even for very large numbers.
 * This means that it is possible that output can contain a large number of digits.
 *  * The maximum number of decimal places reflects the available precision
 *  * NaN values are represented as "NaN"
 *  * Inf values are represented as "Inf" or "-Inf"
 *
 * @author mdavis
 */
class OrdinateFormat {
    private var format: DecimalFormat

    /**
     * Creates an OrdinateFormat using the default maximum number of fraction digits.
     */
    constructor() {
        format = createFormat(MAX_FRACTION_DIGITS)
    }

    /**
     * Creates an OrdinateFormat using the given maximum number of fraction digits.
     *
     * @param maximumFractionDigits the maximum number of fraction digits to output
     */
    constructor(maximumFractionDigits: Int) {
        format = createFormat(maximumFractionDigits)
    }

    /**
     * Returns a string representation of the given ordinate numeric value.
     *
     * @param ord the ordinate value
     * @return the formatted number string
     */
    @Synchronized
    fun format(ord: Double): String {
        /**
         * FUTURE: If it seems better to use scientific notation
         * for very large/small numbers then this can be done here.
         */
        if (Math.isNaN(ord)) return REP_NAN
        return if (Math.isInfinite(ord)) {
            if (ord > 0) REP_POS_INF else REP_NEG_INF
        } else format.format(ord)
    }

    companion object {
        /**
         * The output representation of [Double.POSITIVE_INFINITY]
         */
        const val REP_POS_INF = "Inf"

        /**
         * The output representation of [Double.NEGATIVE_INFINITY]
         */
        const val REP_NEG_INF = "-Inf"

        /**
         * The output representation of [Double.NaN]
         */
        const val REP_NAN = "NaN"

        /**
         * The maximum number of fraction digits to support output of reasonable ordinate values.
         *
         * The default is chosen to allow representing the smallest possible IEEE-754 double-precision value,
         * although this is not expected to occur (and is not supported by other areas of the JTS code).
         */
        const val MAX_FRACTION_DIGITS = 325

        /**
         * The default formatter using the maximum number of digits in the fraction portion of a number.
         */
        @JvmField
        var DEFAULT = OrdinateFormat()

        /**
         * Creates a new formatter with the given maximum number of digits in the fraction portion of a number.
         *
         * @param maximumFractionDigits the maximum number of fraction digits to output
         * @return a formatter
         */
        @JvmStatic
        fun create(maximumFractionDigits: Int): OrdinateFormat {
            return OrdinateFormat(maximumFractionDigits)
        }

        private fun createFormat(maximumFractionDigits: Int): DecimalFormat {
            // specify decimal separator explicitly to work in all locales
            val symbols = DecimalFormatSymbols()
            symbols.decimalSeparator = '.'
            val format = DecimalFormat("0", symbols)
            format.maximumFractionDigits = maximumFractionDigits
            return format
        }
    }
}