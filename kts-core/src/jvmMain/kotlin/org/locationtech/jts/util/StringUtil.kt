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
package org.locationtech.jts.util

import org.locationtech.jts.io.IOException
import org.locationtech.jts.io.OrdinateFormat
import org.locationtech.jts.io.StringReader
import org.locationtech.jts.util.Assert.shouldNeverReachHere
import java.io.ByteArrayOutputStream
import java.io.LineNumberReader
import java.io.PrintStream

/**
 * Utility methods for working with [String]s.
 *
 * @author Martin Davis
 */
object StringUtil {
    /**
     * Mimics the the Java SE [String.split] method.
     *
     * @param s the string to split.
     * @param separator the separator to use.
     * @return the array of split strings.
     */
    fun split(s: String, separator: String): Array<String?> {
        val separatorlen = separator.length
        val tokenList: ArrayList<String> = ArrayList()
        var tmpString = "" + s
        var pos = tmpString.indexOf(separator)
        while (pos >= 0) {
            val token = tmpString.substring(0, pos)
            tokenList.add(token)
            tmpString = tmpString.substring(pos + separatorlen)
            pos = tmpString.indexOf(separator)
        }
        if (tmpString.length > 0) tokenList.add(tmpString)
        val res = arrayOfNulls<String>(tokenList.size)
        for (i in res.indices) {
            res[i] = tokenList.get(i) as String
        }
        return res
    }

    val NEWLINE: String = java.lang.System.getProperty("line.separator")

    /**
     * Returns an throwable's stack trace
     */
    @JvmStatic
    fun getStackTrace(t: Throwable): String {
        val os: ByteArrayOutputStream = ByteArrayOutputStream()
        val ps: PrintStream = PrintStream(os)
        t.printStackTrace(ps)
        return os.toString()
    }

    fun getStackTrace(t: Throwable, depth: Int): String {
        var stackTrace = ""
        val stringReader: StringReader = StringReader(getStackTrace(t))
        val lineNumberReader: LineNumberReader = LineNumberReader(stringReader)
        for (i in 0 until depth) {
            try {
                stackTrace += lineNumberReader.readLine() + NEWLINE
            } catch (e: IOException) {
                shouldNeverReachHere()
            }
        }
        return stackTrace
    }

    /**
     * Returns a string representation of the given number,
     * using a format compatible with WKT.
     *
     * @param d a number
     * @return a string
     *
     */
    @Deprecated("use {@link OrdinateFormat}")
    fun toString(d: Double): String {
        return OrdinateFormat.DEFAULT.format(d)
    }

    fun spaces(n: Int): String {
        return chars(' ', n)
    }

    fun chars(c: Char, n: Int): String {
        val ch = CharArray(n)
        for (i in 0 until n) {
            ch[i] = c
        }
        return ch.concatToString()
    }
}