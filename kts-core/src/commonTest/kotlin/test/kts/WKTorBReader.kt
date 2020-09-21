/*
 * Copyright (c) 2019 Martin Davis
 * Copyright (c) 2020 Macrofocus GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package test.kts

import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.io.ParseException
import org.locationtech.jts.io.WKBReader
import org.locationtech.jts.io.WKBReader.Companion.hexToBytes
import org.locationtech.jts.io.WKTReader
import org.locationtech.jts.legacy.Character

class WKTorBReader(val geomFactory: GeometryFactory?) {
    fun isWKB(str: String): Boolean {
        return isHex(str, MAX_CHARS_TO_CHECK)
    }

    private fun isHex(str: String, maxCharsToTest: Int): Boolean {
        var i = 0
        while (i < maxCharsToTest && i < str.length) {
            val ch = str[i]
            if (!isHexDigit(ch)) return false
            i++
        }
        return true
    }

    private fun isHexDigit(ch: Char): Boolean {
        if (Character.isDigit(ch)) return true
        val chLow: Char = ch.toLowerCase()
        return if (chLow >= 'a' && chLow <= 'f') true else false
    }

    private val MAX_CHARS_TO_CHECK = 6

    @Throws(ParseException::class)
    fun read(geomStr: String): Geometry? {
        val trimStr = geomStr.trim { it <= ' ' }
        return if (isWKB(trimStr)) {
            readWKBHex(trimStr, geomFactory)
        } else readWKT(trimStr, geomFactory)
    }

    @Throws(ParseException::class)
    fun readWKT(wkt: String?, geomFact: GeometryFactory?): Geometry? {
        val rdr = WKTReader(geomFact!!, allowOldJtsCoordinateSyntax = false)
        rdr.setIsOldJtsCoordinateSyntaxAllowed(false)
        return rdr.read(wkt!!)
    }

    @Throws(ParseException::class)
    fun readWKBHex(wkb: String?, geomFact: GeometryFactory?): Geometry? {
        val rdr = WKBReader(geomFact!!)
        return rdr.read(hexToBytes(wkb!!))
    }

    companion object {
        fun read(geomStr: String, geomfact: GeometryFactory?): Geometry? {
            val rdr : WKTorBReader = WKTorBReader(geomfact)
            return try {
                rdr.read(geomStr)
            } catch (ex: ParseException) {
                throw RuntimeException(ex.message)
            }
        }
    }
}
