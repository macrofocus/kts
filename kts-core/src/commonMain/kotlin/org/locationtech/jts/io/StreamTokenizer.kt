/*
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Some of the code has been derived from Android 4.2.2, which came with the following license:
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.locationtech.jts.io

import kotlin.experimental.and
import kotlin.experimental.or

/**
 * Parses a stream into a set of defined tokens, one at a time. The different
 * types of tokens that can be found are numbers, identifiers, quoted strings,
 * and different comment styles. The class can be used for limited processing
 * of source code of programming languages like Java, although it is nowhere
 * near a full parser.
 */
class StreamTokenizer private constructor() {
    /**
     * Contains a number if the current token is a number (`ttype` ==
     * `TT_NUMBER`).
     */
    var nval = 0.0

    /**
     * Contains a string if the current token is a word (`ttype` ==
     * `TT_WORD`).
     */
    var sval: String? = null

    /**
     * After calling `nextToken()`, `ttype` contains the type of
     * token that has been read. When a single character is read, its value
     * converted to an integer is stored in `ttype`. For a quoted string,
     * the value is the quoted character. Otherwise, its value is one of the
     * following:
     *
     *  *  `TT_WORD` - the token is a word.
     *  *  `TT_NUMBER` - the token is a number.
     *  *  `TT_EOL` - the end of line has been reached. Depends on
     * whether `eolIsSignificant` is `true`.
     *  *  `TT_EOF` - the end of the stream has been reached.
     *
     */
    var ttype = TT_UNKNOWN

    /**
     * Internal character meanings, 0 implies TOKEN_ORDINARY
     */
    private val tokenTypes = ByteArray(256)
    private var lineNumber = 1
    private var forceLowercase = false
    private var isEOLSignificant = false
    private var slashStarComments = false
    private var slashSlashComments = false
    private var pushBackToken = false
    private var lastCr = false

    /* One of these will have the stream */
    private var inReader: Reader? = null
    private var peekChar = -2

    /**
     * Constructs a new `StreamTokenizer` with `r` as source reader.
     * The tokenizer's initial state is as follows:
     *
     *  * All byte values 'A' through 'Z', 'a' through 'z', and '&#92;u00A0'
     * through '&#92;u00FF' are considered to be alphabetic.
     *  * All byte values '&#92;u0000' through '&#92;u0020' are considered to
     * be white space. '/' is a comment character.
     *  * Single quote '\'' and double quote '"' are string quote characters.
     *
     *  * Numbers are parsed.
     *  * End of lines are considered to be white space rather than separate
     * tokens.
     *  * C-style and C++-style comments are not recognized.
     *
     * @param r
     * the source reader from which to parse tokens.
     */
    constructor(r: Reader?) : this() {
        if (r == null) {
            throw NullPointerException("r == null")
        }
        inReader = r
    }

    /**
     * Specifies that the character `ch` shall be treated as a comment
     * character.
     *
     * @param ch
     * the character to be considered a comment character.
     */
    fun commentChar(ch: Int) {
        if (ch >= 0 && ch < tokenTypes.size) {
            tokenTypes[ch] = TOKEN_COMMENT
        }
    }

    /**
     * Specifies whether the end of a line is significant and should be returned
     * as `TT_EOF` in `ttype` by this tokenizer.
     *
     * @param flag
     * `true` if EOL is significant, `false` otherwise.
     */
    fun eolIsSignificant(flag: Boolean) {
        isEOLSignificant = flag
    }

    /**
     * Returns the current line number.
     *
     * @return this tokenizer's current line number.
     */
    fun lineno(): Int {
        return lineNumber
    }

    /**
     * Specifies whether word tokens should be converted to lower case when they
     * are stored in `sval`.
     *
     * @param flag
     * `true` if `sval` should be converted to lower
     * case, `false` otherwise.
     */
    fun lowerCaseMode(flag: Boolean) {
        forceLowercase = flag
    }

    /**
     * Parses the next token from this tokenizer's source stream or reader. The
     * type of the token is stored in the `ttype` field, additional
     * information may be stored in the `nval` or `sval` fields.
     *
     * @return the value of `ttype`.
     * @throws IOException
     * if an I/O error occurs while parsing the next token.
     */
    @Throws(IOException::class)
    fun nextToken(): Int {
        if (pushBackToken) {
            pushBackToken = false
            if (ttype != TT_UNKNOWN) {
                return ttype
            }
        }
        sval = null // Always reset sval to null
        var currentChar = if (peekChar == -2) read() else peekChar
        if (lastCr && currentChar == '\n'.code) {
            lastCr = false
            currentChar = read()
        }
        if (currentChar == -1) {
            return TT_EOF.also { ttype = it }
        }
        var currentType = if (currentChar > 255) TOKEN_WORD else tokenTypes[currentChar]
        while (currentType and TOKEN_WHITE != 0.toByte()) {
            /**
             * Skip over white space until we hit a new line or a real token
             */
            if (currentChar == '\r'.code) {
                lineNumber++
                if (isEOLSignificant) {
                    lastCr = true
                    peekChar = -2
                    return TT_EOL.also { ttype = it }
                }
                if (read().also { currentChar = it } == '\n'.code) {
                    currentChar = read()
                }
            } else if (currentChar == '\n'.code) {
                lineNumber++
                if (isEOLSignificant) {
                    peekChar = -2
                    return TT_EOL.also { ttype = it }
                }
                currentChar = read()
            } else {
                // Advance over this white space character and try again.
                currentChar = read()
            }
            if (currentChar == -1) {
                return TT_EOF.also { ttype = it }
            }
            currentType = if (currentChar > 255) TOKEN_WORD else tokenTypes[currentChar]
        }
        /**
         * Check for digits before checking for words since digits can be
         * contained within words.
         */
        if (currentType and TOKEN_DIGIT != 0.toByte()) {
            val digits = StringBuilder(20)
            var haveDecimal = false
            val checkJustNegative = currentChar == '-'.toInt()
            while (true) {
                if (currentChar == '.'.toInt()) {
                    haveDecimal = true
                }
                digits.append(currentChar.toChar())
                currentChar = read()
                if ((currentChar < '0'.toInt() || currentChar > '9'.toInt())
                    && (haveDecimal || currentChar != '.'.toInt())
                ) {
                    break
                }
            }
            peekChar = currentChar
            if (checkJustNegative && digits.length == 1) {
                // Didn't get any other digits other than '-'
                return '-'.also { ttype = it.toInt() }.toInt()
            }
            nval = try {
                val number = digits.toString().toDoubleOrNull()
                number ?: throw NumberFormatException()
            } catch (e: NumberFormatException) {
                // Unsure what to do, will write test.
                0.0
            }
            return TT_NUMBER.also { ttype = it }
        }
        // Check for words
        if (currentType and TOKEN_WORD != 0.toByte()) {
            val word = StringBuilder(20)
            while (true) {
                word.append(currentChar.toChar())
                currentChar = read()
                if (currentChar == -1
                    || currentChar < 256 && tokenTypes[currentChar] and (TOKEN_WORD or TOKEN_DIGIT) == 0.toByte()
                ) {
                    break
                }
            }
            peekChar = currentChar
            sval = word.toString()
            if (forceLowercase) {
                sval = sval!!.toLowerCase()
            }
            return TT_WORD.also { ttype = it }
        }
        // Check for quoted character
        if (currentType == TOKEN_QUOTE) {
            val matchQuote = currentChar
            val quoteString = StringBuilder()
            var peekOne = read()
            while (peekOne >= 0 && peekOne != matchQuote && peekOne != '\r'.toInt() && peekOne != '\n'.toInt()) {
                var readPeek = true
                if (peekOne == '\\'.toInt()) {
                    var c1 = read()
                    // Check for quoted octal IE: \377
                    if (c1 <= '7'.toInt() && c1 >= '0'.toInt()) {
                        var digitValue = c1 - '0'.toInt()
                        c1 = read()
                        if (c1 > '7'.toInt() || c1 < '0'.toInt()) {
                            readPeek = false
                        } else {
                            digitValue = digitValue * 8 + (c1 - '0'.toInt())
                            c1 = read()
                            // limit the digit value to a byte
                            if (digitValue > 31 || c1 > '7'.toInt() || c1 < '0'.toInt()) {
                                readPeek = false
                            } else {
                                digitValue = digitValue * 8 + (c1 - '0'.toInt())
                            }
                        }
                        peekOne = if (!readPeek) {
                            // We've consumed one to many
                            quoteString.append(digitValue.toChar())
                            c1
                        } else {
                            digitValue
                        }
                    } else {
                        peekOne = when (c1.toChar()) {
                            'a' -> 0x7
                            'b' -> 0x8
                            'f' -> 0xc
                            'n' -> 0xA
                            'r' -> 0xD
                            't' -> 0x9
                            'v' -> 0xB
                            else -> c1
                        }
                    }
                }
                if (readPeek) {
                    quoteString.append(peekOne.toChar())
                    peekOne = read()
                }
            }
            if (peekOne == matchQuote) {
                peekOne = read()
            }
            peekChar = peekOne
            ttype = matchQuote
            sval = quoteString.toString()
            return ttype
        }
        // Do comments, both "//" and "/*stuff*/"
        if (currentChar == '/'.toInt() && (slashSlashComments || slashStarComments)) {
            if (read().also { currentChar = it } == '*'.toInt() && slashStarComments) {
                var peekOne = read()
                while (true) {
                    currentChar = peekOne
                    peekOne = read()
                    if (currentChar == -1) {
                        peekChar = -1
                        return TT_EOF.also { ttype = it }
                    }
                    if (currentChar == '\r'.toInt()) {
                        if (peekOne == '\n'.toInt()) {
                            peekOne = read()
                        }
                        lineNumber++
                    } else if (currentChar == '\n'.toInt()) {
                        lineNumber++
                    } else if (currentChar == '*'.toInt() && peekOne == '/'.toInt()) {
                        peekChar = read()
                        return nextToken()
                    }
                }
            } else if (currentChar == '/'.toInt() && slashSlashComments) {
                // Skip to EOF or new line then return the next token
                while (read().also {
                        currentChar = it
                    } >= 0 && currentChar != '\r'.toInt() && currentChar != '\n'.toInt()) {
                    // Intentionally empty
                }
                peekChar = currentChar
                return nextToken()
            } else if (currentType != TOKEN_COMMENT) {
                // Was just a slash by itself
                peekChar = currentChar
                return '/'.also { ttype = it.toInt() }.toInt()
            }
        }
        // Check for comment character
        if (currentType == TOKEN_COMMENT) {
            // Skip to EOF or new line then return the next token
            while (read().also {
                    currentChar = it
                } >= 0 && currentChar != '\r'.toInt() && currentChar != '\n'.toInt()) {
                // Intentionally empty
            }
            peekChar = currentChar
            return nextToken()
        }
        peekChar = read()
        return currentChar.also { ttype = it }
    }

    /**
     * Specifies that the character `ch` shall be treated as an ordinary
     * character by this tokenizer. That is, it has no special meaning as a
     * comment character, word component, white space, string delimiter or
     * number.
     *
     * @param ch
     * the character to be considered an ordinary character.
     */
    fun ordinaryChar(ch: Int) {
        if (ch >= 0 && ch < tokenTypes.size) {
            tokenTypes[ch] = 0
        }
    }

    /**
     * Specifies that the characters in the range from `low` to `hi`
     * shall be treated as an ordinary character by this tokenizer. That is,
     * they have no special meaning as a comment character, word component,
     * white space, string delimiter or number.
     *
     * @param low
     * the first character in the range of ordinary characters.
     * @param hi
     * the last character in the range of ordinary characters.
     */
    fun ordinaryChars(low: Int, hi: Int) {
        var low = low
        var hi = hi
        if (low < 0) {
            low = 0
        }
        if (hi > tokenTypes.size) {
            hi = tokenTypes.size - 1
        }
        for (i in low..hi) {
            tokenTypes[i] = 0
        }
    }

    /**
     * Specifies that this tokenizer shall parse numbers.
     */
    fun parseNumbers() {
        var i = '0'.code
        while (i <= '9'.code) {
            tokenTypes[i] = tokenTypes[i] or TOKEN_DIGIT
            i++
        }
        tokenTypes['.'.toInt()] = tokenTypes['.'.code] or TOKEN_DIGIT
        tokenTypes['-'.toInt()] = tokenTypes['-'.code] or TOKEN_DIGIT
    }

    /**
     * Indicates that the current token should be pushed back and returned again
     * the next time `nextToken()` is called.
     */
    fun pushBack() {
        pushBackToken = true
    }

    /**
     * Specifies that the character `ch` shall be treated as a quote
     * character.
     *
     * @param ch
     * the character to be considered a quote character.
     */
    fun quoteChar(ch: Int) {
        if (ch >= 0 && ch < tokenTypes.size) {
            tokenTypes[ch] = TOKEN_QUOTE
        }
    }

    @Throws(IOException::class)
    private fun read(): Int {
        // Call the read for the appropriate stream
        return inReader!!.read()
    }

    /**
     * Specifies that all characters shall be treated as ordinary characters.
     */
    fun resetSyntax() {
        for (i in 0..255) {
            tokenTypes[i] = 0
        }
    }

    /**
     * Specifies whether "slash-slash" (C++-style) comments shall be recognized.
     * This kind of comment ends at the end of the line.
     *
     * @param flag
     * `true` if `//` should be recognized as the start
     * of a comment, `false` otherwise.
     */
    fun slashSlashComments(flag: Boolean) {
        slashSlashComments = flag
    }

    /**
     * Specifies whether "slash-star" (C-style) comments shall be recognized.
     * Slash-star comments cannot be nested and end when a star-slash
     * combination is found.
     *
     * @param flag
     * `true` if `/ *` should be recognized as the start
     * of a comment, `false` otherwise.
     */
    fun slashStarComments(flag: Boolean) {
        slashStarComments = flag
    }

    /**
     * Returns the state of this tokenizer in a readable format.
     *
     * @return the current state of this tokenizer.
     */
    override fun toString(): String {
        // Values determined through experimentation
        val result = StringBuilder()
        result.append("Token[")
        when (ttype) {
            TT_EOF -> result.append("EOF")
            TT_EOL -> result.append("EOL")
            TT_NUMBER -> {
                result.append("n=")
                result.append(nval)
            }
            TT_WORD -> result.append(sval)
            else -> if (ttype == TT_UNKNOWN || tokenTypes[ttype] == TOKEN_QUOTE) {
                result.append(sval)
            } else {
                result.append('\'')
                result.append(ttype.toChar())
                result.append('\'')
            }
        }
        result.append("], line ")
        result.append(lineNumber)
        return result.toString()
    }

    /**
     * Specifies that the characters in the range from `low` to `hi`
     * shall be treated as whitespace characters by this tokenizer.
     *
     * @param low
     * the first character in the range of whitespace characters.
     * @param hi
     * the last character in the range of whitespace characters.
     */
    fun whitespaceChars(low: Int, hi: Int) {
        var low = low
        var hi = hi
        if (low < 0) {
            low = 0
        }
        if (hi > tokenTypes.size) {
            hi = tokenTypes.size - 1
        }
        for (i in low..hi) {
            tokenTypes[i] = TOKEN_WHITE
        }
    }

    /**
     * Specifies that the characters in the range from `low` to `hi`
     * shall be treated as word characters by this tokenizer. A word consists of
     * a word character followed by zero or more word or number characters.
     *
     * @param low
     * the first character in the range of word characters.
     * @param hi
     * the last character in the range of word characters.
     */
    fun wordChars(low: Int, hi: Int) {
        var low = low
        var hi = hi
        if (low < 0) {
            low = 0
        }
        if (hi > tokenTypes.size) {
            hi = tokenTypes.size - 1
        }
        for (i in low..hi) {
            tokenTypes[i] = tokenTypes[i] or TOKEN_WORD
        }
    }

    companion object {
        /**
         * The constant representing the end of the stream.
         */
        const val TT_EOF = -1

        /**
         * The constant representing the end of the line.
         */
        const val TT_EOL = '\n'.code

        /**
         * The constant representing a number token.
         */
        const val TT_NUMBER = -2

        /**
         * The constant representing a word token.
         */
        const val TT_WORD = -3

        /**
         * Internal representation of unknown state.
         */
        private const val TT_UNKNOWN = -4
        private const val TOKEN_COMMENT: Byte = 1
        private const val TOKEN_QUOTE: Byte = 2
        private const val TOKEN_WHITE: Byte = 4
        private const val TOKEN_WORD: Byte = 8
        private const val TOKEN_DIGIT: Byte = 16
    }

    /**
     * Private constructor to initialize the default values according to the
     * specification.
     */
    init {
        /*
         * Initialize the default state per specification. All byte values 'A'
         * through 'Z', 'a' through 'z', and '\u00A0' through '\u00FF' are
         * considered to be alphabetic.
         */
        wordChars('A'.toInt(), 'Z'.toInt())
        wordChars('a'.toInt(), 'z'.toInt())
        wordChars(160, 255)
        /**
         * All byte values '\u0000' through '\u0020' are considered to be white
         * space.
         */
        whitespaceChars(0, 32)
        /**
         * '/' is a comment character. Single quote '\'' and double quote '"'
         * are string quote characters.
         */
        commentChar('/'.toInt())
        quoteChar('"'.toInt())
        quoteChar('\''.toInt())
        /**
         * Numbers are parsed.
         */
        parseNumbers()
        /**
         * Ends of lines are treated as white space, not as separate tokens.
         * C-style and C++-style comments are not recognized. These are the
         * defaults and are not needed in constructor.
         */
    }
}