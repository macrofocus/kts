/*
 * Copyright (c) 2016 Vivid Solutions.
 * Copyright (c) 2020 Macrofocus GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.io

import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.impl.CoordinateArraySequenceFactory.Companion.instance
import org.locationtech.jts.legacy.*
import org.locationtech.jts.util.Assert.shouldNeverReachHere
import org.locationtech.jts.util.AssertionFailedException

import kotlin.collections.ArrayList

/**
 * Converts a geometry in Well-Known Text format to a {@link Geometry}.
 * <p>
 * <code>WKTReader</code> supports
 * extracting <code>Geometry</code> objects from either {@link Reader}s or
 *  {@link String}s. This allows it to function as a parser to read <code>Geometry</code>
 *  objects from text blocks embedded in other data formats (e.g. XML). <P>
 * <p>
 *  A <code>WKTReader</code> is parameterized by a <code>GeometryFactory</code>,
 *  to allow it to create <code>Geometry</code> objects of the appropriate
 *  implementation. In particular, the <code>GeometryFactory</code>
 *  determines the <code>PrecisionModel</code> and <code>SRID</code> that is
 *  used. <P>
 *
 *  The <code>WKTReader</code> converts all input numbers to the precise
 *  internal representation.
 *
 * <h3>Notes:</h3>
 * <ul>
 * <li>Keywords are case-insensitive.
 * <li>The reader supports non-standard "LINEARRING" tags.
 * <li>The reader uses <tt>Double.parseDouble</tt> to perform the conversion of ASCII
 * numbers to floating point.  This means it supports the Java
 * syntax for floating point literals (including scientific notation).
 * </ul>
 *
 * <h3>Syntax</h3>
 * The following syntax specification describes the version of Well-Known Text
 * supported by JTS.
 * (The specification uses a syntax language similar to that used in
 * the C and Java language specifications.)
 * <p>
 * As of version 1.15, JTS can read (but not write) WKT Strings including Z, M or ZM
 * in the name of the geometry type (ex. POINT Z, LINESTRINGZM).
 * Note that it only makes the reader more flexible, but JTS could already read
 * 3D coordinates from WKT String and still can't read 4D coordinates.
 *
 * <blockquote><pre>
 * <i>WKTGeometry:</i> one of<i>
 *
 *       WKTPoint  WKTLineString  WKTLinearRing  WKTPolygon
 *       WKTMultiPoint  WKTMultiLineString  WKTMultiPolygon
 *       WKTGeometryCollection</i>
 *
 * <i>WKTPoint:</i> <b>POINT</b><i>[Dimension]</i> <b>( </b><i>Coordinate</i> <b>)</b>
 *
 * <i>WKTLineString:</i> <b>LINESTRING</b><i>[Dimension]</i> <i>CoordinateSequence</i>
 *
 * <i>WKTLinearRing:</i> <b>LINEARRING</b><i>[Dimension]</i> <i>CoordinateSequence</i>
 *
 * <i>WKTPolygon:</i> <b>POLYGON</b><i>[Dimension]</i> <i>CoordinateSequenceList</i>
 *
 * <i>WKTMultiPoint:</i> <b>MULTIPOINT</b><i>[Dimension]</i> <i>CoordinateSingletonList</i>
 *
 * <i>WKTMultiLineString:</i> <b>MULTILINESTRING</b><i>[Dimension]</i> <i>CoordinateSequenceList</i>
 *
 * <i>WKTMultiPolygon:</i>
 *         <b>MULTIPOLYGON</b><i>[Dimension]</i> <b>(</b> <i>CoordinateSequenceList {</i> , <i>CoordinateSequenceList }</i> <b>)</b>
 *
 * <i>WKTGeometryCollection: </i>
 *         <b>GEOMETRYCOLLECTION</b><i>[Dimension]</i> <b> (</b> <i>WKTGeometry {</i> , <i>WKTGeometry }</i> <b>)</b>
 *
 * <i>CoordinateSingletonList:</i>
 *         <b>(</b> <i>CoordinateSingleton {</i> <b>,</b> <i>CoordinateSingleton }</i> <b>)</b>
 *         | <b>EMPTY</b>
 *
 * <i>CoordinateSingleton:</i>
 *         <b>(</b> <i>Coordinate</i> <b>)</b>
 *         | <b>EMPTY</b>
 *
 * <i>CoordinateSequenceList:</i>
 *         <b>(</b> <i>CoordinateSequence {</i> <b>,</b> <i>CoordinateSequence }</i> <b>)</b>
 *         | <b>EMPTY</b>
 *
 * <i>CoordinateSequence:</i>
 *         <b>(</b> <i>Coordinate {</i> , <i>Coordinate }</i> <b>)</b>
 *         | <b>EMPTY</b>
 *
 * <i>Coordinate:
 *         Number Number Number<sub>opt</sub></i>
 *
 * <i>Number:</i> A Java-style floating-point number (including <tt>NaN</tt>, with arbitrary case)
 *
 * <i>Dimension:</i>
 *         <b>Z</b>|<b> Z</b>|<b>M</b>|<b> M</b>|<b>ZM</b>|<b> ZM</b>
 *
 * </pre></blockquote>
 *
 *
 *@version 1.7
 * @see WKTWriter
 */
class WKTReader
/**
 * Creates a reader that creates objects using the given
 * [GeometryFactory].
 *
 * @param  geometryFactory  the factory used to create `Geometry`s.
 */
    (private var geometryFactory: GeometryFactory = GeometryFactory()) {
    private var csFactory: CoordinateSequenceFactory = geometryFactory.coordinateSequenceFactory
    private var precisionModel: PrecisionModel = geometryFactory.precisionModel
    private var allowOldJtsCoordinateSyntax = ALLOW_OLD_JTS_COORDINATE_SYNTAX
    private var allowOldJtsMultipointSyntax = ALLOW_OLD_JTS_MULTIPOINT_SYNTAX

    fun setIsOldJtsCoordinateSyntaxAllowed(value : Boolean) {
        allowOldJtsCoordinateSyntax = value
    }

    fun setIsAllowOldJtsMultipointSyntax(value : Boolean) {
        allowOldJtsMultipointSyntax = value
    }

    /**
     * Reads a Well-Known Text representation of a [Geometry]
     * from a [String].
     *
     * @param wellKnownText
     * one or more &lt;Geometry Tagged Text&gt; strings (see the OpenGIS
     * Simple Features Specification) separated by whitespace
     * @return a `Geometry` specified by `wellKnownText`
     * @throws ParseException
     * if a parsing problem occurs
     */
    @Throws(ParseException::class)
    fun read(wellKnownText: String): Geometry? {
        val reader = StringReader(wellKnownText)
        return try {
            read(reader)
        } finally {
            reader.close()
        }
    }

    /**
     * Reads a Well-Known Text representation of a [Geometry]
     * from a [Reader].
     *
     * @param  reader           a Reader which will return a &lt;Geometry Tagged Text&gt;
     * string (see the OpenGIS Simple Features Specification)
     * @return                  a `Geometry` read from `reader`
     * @throws  ParseException  if a parsing problem occurs
     */
    @Throws(ParseException::class)
    fun read(reader: Reader): Geometry? {
        val tokenizer = createTokenizer(reader)
        return try {
            readGeometryTaggedText(tokenizer)
        } catch (e: IOException) {
            throw ParseException(e.toString())
        }
    }

    /**
     * Utility function to create the tokenizer
     * @param reader a reader
     *
     * @return a WKT Tokenizer.
     */
    private fun createTokenizer(reader: Reader): StreamTokenizer {
        val tokenizer = StreamTokenizer(reader)
        // set tokenizer to NOT parse numbers
        tokenizer.resetSyntax()
        tokenizer.wordChars('a'.toInt(), 'z'.toInt())
        tokenizer.wordChars('A'.toInt(), 'Z'.toInt())
        tokenizer.wordChars(128 + 32, 255)
        tokenizer.wordChars('0'.toInt(), '9'.toInt())
        tokenizer.wordChars('-'.toInt(), '-'.toInt())
        tokenizer.wordChars('+'.toInt(), '+'.toInt())
        tokenizer.wordChars('.'.toInt(), '.'.toInt())
        tokenizer.whitespaceChars(0, ' '.toInt())
        tokenizer.commentChar('#'.toInt())
        return tokenizer
    }

    /**
     * Reads a `Coordinate` from a stream using the given [StreamTokenizer].
     *
     *
     * All ordinate values are read, but -depending on the [CoordinateSequenceFactory] of the
     * underlying [GeometryFactory]- not necessarily all can be handled. Those are silently dropped.
     *
     * @param tokenizer the tokenizer to use
     * @param ordinateFlags a bit-mask defining the ordinates to read.
     * @param tryParen a value indicating if a starting [.L_PAREN] should be probed.
     * @return a [CoordinateSequence] of length 1 containing the read ordinate values
     * @throws  IOException     if an I/O error occurs
     * @throws  ParseException  if an unexpected token was encountered
     */
    @Throws(IOException::class, ParseException::class)
    private fun getCoordinate(
        tokenizer: StreamTokenizer,
        ordinateFlags: EnumSet<Ordinate>,
        tryParen: Boolean
    ): CoordinateSequence {
        var opened = false
        if (tryParen && isOpenerNext(tokenizer)) {
            tokenizer.nextToken()
            opened = true
        }

        // create a sequence for one coordinate

        // create a sequence for one coordinate
        val offsetM = if (ordinateFlags.contains(Ordinate.Z)) 1 else 0
        val sequence =
            csFactory.create(1, toDimension(ordinateFlags), if (ordinateFlags.contains(Ordinate.M)) 1 else 0)
        sequence.setOrdinate(0, CoordinateSequence.X, precisionModel.makePrecise(getNextNumber(tokenizer)))
        sequence.setOrdinate(0, CoordinateSequence.Y, precisionModel.makePrecise(getNextNumber(tokenizer)))

        // additionally read other vertices
        if (ordinateFlags.contains(Ordinate.Z))
            sequence.setOrdinate(0, CoordinateSequence.Z, getNextNumber(tokenizer))
        if (ordinateFlags.contains(Ordinate.M))
            sequence.setOrdinate(0, CoordinateSequence.Z + offsetM, getNextNumber(tokenizer))

        if (ordinateFlags.size == 2 && this.allowOldJtsCoordinateSyntax && isNumberNext(tokenizer)) {
            sequence.setOrdinate(0, CoordinateSequence.Z, getNextNumber(tokenizer))
        }

        // read close token if it was opened here

        // read close token if it was opened here
        if (opened) {
            getNextCloser(tokenizer)
        }

        return sequence
    }

    /**
     * Reads a `Coordinate` from a stream using the given [StreamTokenizer].
     *
     *
     * All ordinate values are read, but -depending on the [CoordinateSequenceFactory] of the
     * underlying [GeometryFactory]- not necessarily all can be handled. Those are silently dropped.
     *
     *
     *
     *
     *
     * @param tokenizer the tokenizer to use
     * @param ordinateFlags a bit-mask defining the ordinates to read.
     * @return a [CoordinateSequence] of length 1 containing the read ordinate values
     * @throws  IOException     if an I/O error occurs
     * @throws  ParseException  if an unexpected token was encountered
     */
    @Throws(IOException::class, ParseException::class)
    private fun getCoordinateSequence(
        tokenizer: StreamTokenizer,
        ordinateFlags: EnumSet<Ordinate>
    ): CoordinateSequence? {
        if (getNextEmptyOrOpener(tokenizer) == WKTConstants.EMPTY) return csFactory.create(
            0,
            toDimension(ordinateFlags),
            if (ordinateFlags.contains(Ordinate.M)) 1 else 0
        )
        val coordinates: ArrayList<CoordinateSequence> = ArrayList()
        do {
            coordinates.add(getCoordinate(tokenizer, ordinateFlags, false))
        } while (getNextCloserOrComma(tokenizer) == COMMA)
        return mergeSequences(coordinates, ordinateFlags)
    }

    /**
     * Reads a `CoordinateSequence` from a stream using the given [StreamTokenizer]
     * for an old-style JTS MultiPoint (Point coordinates not enclosed in parentheses).
     *
     *
     * All ordinate values are read, but -depending on the [CoordinateSequenceFactory] of the
     * underlying [GeometryFactory]- not necessarily all can be handled. Those are silently dropped.
     *
     * @param tokenizer the tokenizer to use
     * @param ordinateFlags a bit-mask defining the ordinates to read.
     * @param tryParen a value indicating if a starting [.L_PAREN] should be probed for each coordinate.
     * @param isReadEmptyOrOpener indicates if an opening paren or EMPTY should be scanned for
     * @return a [CoordinateSequence] of length 1 containing the read ordinate values
     *
     * @throws  IOException     if an I/O error occurs
     * @throws  ParseException  if an unexpected token was encountered
     * S
     */
    @Throws(IOException::class, ParseException::class)
    private fun getCoordinateSequenceOldMultiPoint(
        tokenizer: StreamTokenizer,
        ordinateFlags: EnumSet<Ordinate>
    ): CoordinateSequence? {
        val coordinates: ArrayList<CoordinateSequence> = ArrayList()
        do {
            coordinates.add(getCoordinate(tokenizer, ordinateFlags, true))
        } while (getNextCloserOrComma(tokenizer) == COMMA)
        return mergeSequences(coordinates, ordinateFlags)
    }

    /**
     * Computes the required dimension based on the given ordinate values.
     * It is assumed that [Ordinate.X] and [Ordinate.Y] are included.
     *
     * @param ordinateFlags the ordinate bit-mask
     * @return the number of dimensions required to store ordinates for the given bit-mask.
     */
    private fun toDimension(ordinateFlags: EnumSet<Ordinate>): Int {
        var dimension = 2
        if (ordinateFlags.contains(Ordinate.Z)) dimension++
        if (ordinateFlags.contains(Ordinate.M)) dimension++
        if (dimension == 2 && allowOldJtsCoordinateSyntax) dimension++
        return dimension
    }

    /**
     * Merges an array of one-coordinate-[CoordinateSequence]s into one [CoordinateSequence].
     *
     * @param sequences an array of coordinate sequences. Each sequence contains **exactly one** coordinate.
     * @param ordinateFlags a bit-mask of required ordinates.
     * @return a coordinate sequence containing all coordinate
     */
    private fun mergeSequences(
        sequences: ArrayList<CoordinateSequence>,
        ordinateFlags: EnumSet<Ordinate>
    ): CoordinateSequence? {
        // if the sequences array is empty or null create an empty sequence
        if (sequences == null || sequences.size == 0)
            return csFactory.create(0, toDimension(ordinateFlags))

        if (sequences.size == 1)
            return sequences.get(0)

        val mergeOrdinates: EnumSet<Ordinate>
        if (this.allowOldJtsCoordinateSyntax && ordinateFlags.size == 2) {
            mergeOrdinates = ordinateFlags.clone()
            for (i in 0 until sequences.size) {
                if (sequences.get(i).hasZ()) {
                    mergeOrdinates.add(Ordinate.Z)
                    break
                }
            }
        } else
            mergeOrdinates = ordinateFlags

        // create and fill the result sequence
        val sequence: CoordinateSequence = this.csFactory.create(
            sequences.size, toDimension(mergeOrdinates),
            if(mergeOrdinates.contains(Ordinate.M)) 1 else 0)

        val offsetM: Int = CoordinateSequence.Z + (if(mergeOrdinates.contains(Ordinate.Z)) 1 else 0)
        for (i in 0 until sequences.size) {
            val item = sequences.get (i)
            sequence.setOrdinate(i, CoordinateSequence.X, item.getOrdinate(0, CoordinateSequence.X))
            sequence.setOrdinate(i, CoordinateSequence.Y, item.getOrdinate(0, CoordinateSequence.Y))
            if (mergeOrdinates.contains(Ordinate.Z))
                sequence.setOrdinate(i, CoordinateSequence.Z, item.getOrdinate(0, CoordinateSequence.Z))
            if (mergeOrdinates.contains(Ordinate.M))
                sequence.setOrdinate(i, offsetM, item.getOrdinate(0, offsetM))
        }

        // return it
        return sequence
    }

    /**
     * Returns the next array of `Coordinate`s in the stream.
     *
     * @param  tokenizer        tokenizer over a stream of text in Well-known Text
     * format. The next element returned by the stream should be L_PAREN (the
     * beginning of "(x1 y1, x2 y2, ..., xn yn)") or EMPTY.
     * @return                  the next array of `Coordinate`s in the
     * stream, or an empty array if EMPTY is the next element returned by
     * the stream.
     * @throws  IOException     if an I/O error occurs
     * @throws  ParseException  if an unexpected token was encountered
     */
    @Deprecated("in favor of functions returning {@link CoordinateSequence}s")
    @Throws(IOException::class, ParseException::class)
    private fun getCoordinates(tokenizer: StreamTokenizer): Array<Coordinate>? {
        var nextToken = getNextEmptyOrOpener(tokenizer)
        if (nextToken == WKTConstants.EMPTY) {
            return arrayOf()
        }
        val coordinates: ArrayList<Coordinate> = ArrayList()
        coordinates.add(getPreciseCoordinate(tokenizer))
        nextToken = getNextCloserOrComma(tokenizer)
        while (nextToken.equals(COMMA)) {
            coordinates.add(getPreciseCoordinate(tokenizer))
            nextToken = getNextCloserOrComma(tokenizer)
        }
        return coordinates.toTypedArray()
    }

    /**
     * Returns the next array of `Coordinate`s in the stream.
     *
     * @param  tokenizer        tokenizer over a stream of text in Well-known Text
     * format. The next element returned by the stream should be a number.
     * @return                  the next array of `Coordinate`s in the
     * stream.
     * @throws  IOException     if an I/O error occurs
     * @throws  ParseException  if an unexpected token was encountered
     */
    @Deprecated("in favor of functions returning {@link CoordinateSequence}s")
    @Throws(IOException::class, ParseException::class)
    private fun getCoordinatesNoLeftParen(tokenizer: StreamTokenizer): Array<Coordinate?>? {
        var nextToken: String? = null
        val coordinates: ArrayList<Coordinate> = ArrayList()
        coordinates.add(getPreciseCoordinate(tokenizer))
        nextToken = getNextCloserOrComma(tokenizer)
        while (nextToken == COMMA) {
            coordinates.add(getPreciseCoordinate(tokenizer))
            nextToken = getNextCloserOrComma(tokenizer)
        }
        return coordinates.toTypedArray()
    }

    /**
     * Returns the next precise `Coordinate` in the stream.
     *
     * @param  tokenizer        tokenizer over a stream of text in Well-known Text
     * format. The next element returned by the stream should be a number.
     * @return                  the next array of `Coordinate`s in the
     * stream.
     * @throws  IOException     if an I/O error occurs
     * @throws  ParseException  if an unexpected token was encountered
     */
    @Deprecated("in favor of functions returning {@link CoordinateSequence}s")
    @Throws(IOException::class, ParseException::class)
    private fun getPreciseCoordinate(tokenizer: StreamTokenizer): Coordinate {
        val coord = Coordinate()
        coord.x = getNextNumber(tokenizer)
        coord.y = getNextNumber(tokenizer)
        if (isNumberNext(tokenizer)) {
            coord.z = getNextNumber(tokenizer)
        }
        if (isNumberNext(tokenizer)) {
            getNextNumber(tokenizer) // ignore M value
        }
        precisionModel.makePrecise(coord)
        return coord
    }

    /**
     * Tests if the next token in the stream is a number
     *
     * @param tokenizer the tokenizer
     * @return `true` if the next token is a number, otherwise `false`
     * @throws  IOException     if an I/O error occurs
     */
    @Throws(IOException::class)
    private fun isNumberNext(tokenizer: StreamTokenizer): Boolean {
        val type = tokenizer.nextToken()
        tokenizer.pushBack()
        return type == StreamTokenizer.TT_WORD
    }

    /**
     * Tests if the next token in the stream is a left opener ([.L_PAREN])
     *
     * @param tokenizer the tokenizer
     * @return `true` if the next token is a [.L_PAREN], otherwise `false`
     * @throws  IOException     if an I/O error occurs
     */
    @Throws(IOException::class)
    private fun isOpenerNext(tokenizer: StreamTokenizer): Boolean {
        val type = tokenizer.nextToken()
        tokenizer.pushBack()
        return type == '('.toInt()
    }

    /**
     * Parses the next number in the stream.
     * Numbers with exponents are handled.
     * <tt>NaN</tt> values are handled correctly, and
     * the case of the "NaN" symbol is not significant.
     *
     * @param  tokenizer        tokenizer over a stream of text in Well-known Text
     * @return                  the next number in the stream
     * @throws  ParseException  if the next token is not a valid number
     * @throws  IOException     if an I/O error occurs
     */
    @Throws(IOException::class, ParseException::class)
    private fun getNextNumber(tokenizer: StreamTokenizer): Double {
        when (tokenizer.nextToken()) {
            StreamTokenizer.TT_WORD -> {
                return if (tokenizer.sval.equals(NAN_SYMBOL, ignoreCase = true)) {
                    Double.NaN
                } else {
                    try {
                        val number = tokenizer.sval?.toDoubleOrNull()
                        if(number != null) {
                            number
                        } else {
                            throw NumberFormatException()
                        }
                    } catch (ex: NumberFormatException) {
                        throw parseErrorWithLine(tokenizer, "Invalid number: " + tokenizer.sval)
                    }
                }
            }
        }
        throw parseErrorExpected(tokenizer, "number")
    }

    /**
     * Returns the next EMPTY or L_PAREN in the stream as uppercase text.
     *
     * @return                  the next EMPTY or L_PAREN in the stream as uppercase
     * text.
     * @throws  ParseException  if the next token is not EMPTY or L_PAREN
     * @throws  IOException     if an I/O error occurs
     * @param  tokenizer        tokenizer over a stream of text in Well-known Text
     */
    @Throws(IOException::class, ParseException::class)
    private fun getNextEmptyOrOpener(tokenizer: StreamTokenizer): String? {
        var nextWord = getNextWord(tokenizer)
        when {
            nextWord.equals(WKTConstants.Z, ignoreCase = true) -> {
                //z = true;
                nextWord = getNextWord(tokenizer)
            }
            nextWord.equals(WKTConstants.M, ignoreCase = true) -> {
                //m = true;
                nextWord = getNextWord(tokenizer)
            }
            nextWord.equals(WKTConstants.ZM, ignoreCase = true) -> {
                //z = true;
                //m = true;
                nextWord = getNextWord(tokenizer)
            }
        }
        if (nextWord == WKTConstants.EMPTY || nextWord == L_PAREN) {
            return nextWord
        }
        throw parseErrorExpected(tokenizer, WKTConstants.EMPTY + " or " + L_PAREN)
    }

    /**
     * Returns the next ordinate flag information in the stream as uppercase text.
     * This can be Z, M or ZM.
     *
     * @return                  the next EMPTY or L_PAREN in the stream as uppercase
     * text.
     * @throws  ParseException  if the next token is not EMPTY or L_PAREN
     * @throws  IOException     if an I/O error occurs
     * @param  tokenizer        tokenizer over a stream of text in Well-known Text
     */
    @Throws(IOException::class, ParseException::class)
    private fun getNextOrdinateFlags(tokenizer: StreamTokenizer): EnumSet<Ordinate> {
        val result = enumSetOf(setOf(Ordinate.X, Ordinate.Y))

        val nextWord = lookAheadWord(tokenizer)!!.toUpperCaseNoLocale()
        when {
            nextWord.equals(WKTConstants.Z, ignoreCase = true) -> {
                tokenizer.nextToken()
                result.add(Ordinate.Z)
            }
            nextWord.equals(WKTConstants.M, ignoreCase = true) -> {
                tokenizer.nextToken()
                result.add(Ordinate.M)
            }
            nextWord.equals(WKTConstants.ZM, ignoreCase = true) -> {
                tokenizer.nextToken()
                result.add(Ordinate.Z)
                result.add(Ordinate.M)
            }
        }
        return result 
    }

    /**
     * Returns the next word in the stream.
     *
     * @param  tokenizer        tokenizer over a stream of text in Well-known Text
     * format. The next token must be a word.
     * @return                  the next word in the stream as uppercase text
     * @throws  ParseException  if the next token is not a word
     * @throws  IOException     if an I/O error occurs
     */
    @Throws(IOException::class, ParseException::class)
    private fun lookAheadWord(tokenizer: StreamTokenizer): String? {
        val nextWord = getNextWord(tokenizer)
        tokenizer.pushBack()
        return nextWord
    }

    /**
     * Returns the next [.R_PAREN] or [.COMMA] in the stream.
     *
     * @return                  the next R_PAREN or COMMA in the stream
     * @throws  ParseException  if the next token is not R_PAREN or COMMA
     * @throws  IOException     if an I/O error occurs
     * @param  tokenizer        tokenizer over a stream of text in Well-known Text
     */
    @Throws(IOException::class, ParseException::class)
    private fun getNextCloserOrComma(tokenizer: StreamTokenizer): String? {
        val nextWord = getNextWord(tokenizer)
        if (nextWord == COMMA || nextWord == R_PAREN) {
            return nextWord
        }
        throw parseErrorExpected(tokenizer, "$COMMA or $R_PAREN")
    }

    /**
     * Returns the next [.R_PAREN] in the stream.
     *
     * @param  tokenizer        tokenizer over a stream of text in Well-known Text
     * format. The next token must be R_PAREN.
     * @return                  the next R_PAREN in the stream
     * @throws  ParseException  if the next token is not R_PAREN
     * @throws  IOException     if an I/O error occurs
     */
    @Throws(IOException::class, ParseException::class)
    private fun getNextCloser(tokenizer: StreamTokenizer): String? {
        val nextWord = getNextWord(tokenizer)
        if (nextWord == R_PAREN) {
            return nextWord
        }
        throw parseErrorExpected(tokenizer, R_PAREN)
    }

    /**
     * Returns the next word in the stream.
     *
     * @return                  the next word in the stream as uppercase text
     * @throws  ParseException  if the next token is not a word
     * @throws  IOException     if an I/O error occurs
     * @param  tokenizer        tokenizer over a stream of text in Well-known Text
     */
    @Throws(IOException::class, ParseException::class)
    private fun getNextWord(tokenizer: StreamTokenizer): String? {
        when (tokenizer.nextToken()) {
            StreamTokenizer.TT_WORD -> {
                val word = tokenizer.sval
                return if (word.equals(WKTConstants.EMPTY, ignoreCase = true)) WKTConstants.EMPTY else word
            }
            '('.toInt() -> return L_PAREN
            ')'.toInt() -> return R_PAREN
            ','.toInt() -> return COMMA
        }
        throw parseErrorExpected(tokenizer, "word")
    }

    /**
     * Creates a formatted ParseException reporting that the current token
     * was unexpected.
     *
     * @param expected a description of what was expected
     * @throws AssertionFailedException if an invalid token is encountered
     */
    private fun parseErrorExpected(tokenizer: StreamTokenizer, expected: String): ParseException {
        // throws Asserts for tokens that should never be seen
        if (tokenizer.ttype == StreamTokenizer.TT_NUMBER) shouldNeverReachHere("Unexpected NUMBER token")
        if (tokenizer.ttype == StreamTokenizer.TT_EOL) shouldNeverReachHere("Unexpected EOL token")
        val tokenStr = tokenString(tokenizer)
        return parseErrorWithLine(tokenizer, "Expected $expected but found $tokenStr")
    }

    /**
     * Creates a formatted ParseException reporting that the current token
     * was unexpected.
     *
     * @param msg a description of what was expected
     * @throws AssertionFailedException if an invalid token is encountered
     */
    private fun parseErrorWithLine(tokenizer: StreamTokenizer, msg: String): ParseException {
        return ParseException(msg + " (line " + tokenizer.lineno() + ")")
    }

    /**
     * Gets a description of the current token type
     * @param tokenizer the tokenizer
     * @return a description of the current token
     */
    private fun tokenString(tokenizer: StreamTokenizer): String? {
        when (tokenizer.ttype) {
            StreamTokenizer.TT_NUMBER -> return "<NUMBER>"
            StreamTokenizer.TT_EOL -> return "End-of-Line"
            StreamTokenizer.TT_EOF -> return "End-of-Stream"
            StreamTokenizer.TT_WORD -> return "'" + tokenizer.sval + "'"
        }
        return "'" + tokenizer.ttype.toChar() + "'"
    }

    /**
     * Creates a `Geometry` using the next token in the stream.
     *
     * @return                  a `Geometry` specified by the next token
     * in the stream
     * @throws  ParseException  if the coordinates used to create a `Polygon`
     * shell and holes do not form closed linestrings, or if an unexpected
     * token was encountered
     * @throws  IOException     if an I/O error occurs
     * @param  tokenizer        tokenizer over a stream of text in Well-known Text
     */
    @Throws(IOException::class, ParseException::class)
    private fun readGeometryTaggedText(tokenizer: StreamTokenizer): Geometry? {
        val type: String
        val ordinateFlags = enumSetOf(setOf(Ordinate.X, Ordinate.Y))
        try {
            type = getNextWord(tokenizer)!!.toUpperCaseNoLocale()
            when {
                type.endsWith(WKTConstants.ZM) -> {
                    ordinateFlags.add(Ordinate.Z)
                    ordinateFlags.add(Ordinate.M)
                }
                type.endsWith(WKTConstants.Z) -> {
                    ordinateFlags.add(Ordinate.Z)
                }
                type.endsWith(WKTConstants.M) -> {
                    ordinateFlags.add(Ordinate.M)
                }
            }
        } catch (e: IOException) {
            return null
        } catch (e: ParseException) {
            return null
        }
        return readGeometryTaggedText(tokenizer, type, ordinateFlags)
    }

    @Throws(IOException::class, ParseException::class)
    private fun readGeometryTaggedText(
        tokenizer: StreamTokenizer,
        type: String,
        ordinateFlags: EnumSet<Ordinate>
    ): Geometry? {
        var ordinateFlags = ordinateFlags
        if (ordinateFlags.size == 2) {
            ordinateFlags = getNextOrdinateFlags(tokenizer)
        }

        // if we can create a sequence with the required dimension everything is ok, otherwise
        // we need to take a different coordinate sequence factory.
        // It would be good to not have to try/catch this but if the CoordinateSequenceFactory
        // exposed a value indicating which min/max dimension it can handle or even an
        // ordinate bit-flag.

        // if we can create a sequence with the required dimension everything is ok, otherwise
        // we need to take a different coordinate sequence factory.
        // It would be good to not have to try/catch this but if the CoordinateSequenceFactory
        // exposed a value indicating which min/max dimension it can handle or even an
        // ordinate bit-flag.
        try {
            csFactory.create(0, toDimension(ordinateFlags), if (ordinateFlags.contains(Ordinate.M)) 1 else 0)
        } catch (e: Exception) {
            geometryFactory = GeometryFactory(
                geometryFactory.precisionModel,
                geometryFactory.SRID, csFactoryXYZM
            )
        }

        when {
            type.startsWith(WKTConstants.POINT) -> {
                return readPointText(tokenizer, ordinateFlags)
            }
            type.startsWith(WKTConstants.LINESTRING) -> {
                return readLineStringText(tokenizer, ordinateFlags)
            }
            type.startsWith(WKTConstants.LINEARRING) -> {
                return readLinearRingText(tokenizer, ordinateFlags)
            }
            type.startsWith(WKTConstants.POLYGON) -> {
                return readPolygonText(tokenizer, ordinateFlags)
            }
            type.startsWith(WKTConstants.MULTIPOINT) -> {
                return readMultiPointText(tokenizer, ordinateFlags)
            }
            type.startsWith(WKTConstants.MULTILINESTRING) -> {
                return readMultiLineStringText(tokenizer, ordinateFlags)
            }
            type.startsWith(WKTConstants.MULTIPOLYGON) -> {
                return readMultiPolygonText(tokenizer, ordinateFlags)
            }
            type.startsWith(WKTConstants.GEOMETRYCOLLECTION) -> {
                return readGeometryCollectionText(tokenizer, ordinateFlags)
            }
            else -> throw parseErrorWithLine(tokenizer, "Unknown geometry type: $type")
        }
    }

    /**
     * Creates a `Point` using the next token in the stream.
     *
     * @param  tokenizer        tokenizer over a stream of text in Well-known Text
     * format. The next tokens must form a &lt;Point Text&gt;.
     * @return                  a `Point` specified by the next token in
     * the stream
     * @throws  IOException     if an I/O error occurs
     * @throws  ParseException  if an unexpected token was encountered
     */
    @Throws(IOException::class, ParseException::class)
    private fun readPointText(tokenizer: StreamTokenizer, ordinateFlags: EnumSet<Ordinate>): Point? {
        return geometryFactory.createPoint(getCoordinateSequence(tokenizer, ordinateFlags))
    }

    /**
     * Creates a `LineString` using the next token in the stream.
     *
     * @param  tokenizer        tokenizer over a stream of text in Well-known Text
     * format. The next tokens must form a &lt;LineString Text&gt;.
     * @return                  a `LineString` specified by the next
     * token in the stream
     * @throws  IOException     if an I/O error occurs
     * @throws  ParseException  if an unexpected token was encountered
     */
    @Throws(IOException::class, ParseException::class)
    private fun readLineStringText(tokenizer: StreamTokenizer, ordinateFlags: EnumSet<Ordinate>): LineString? {
        return geometryFactory.createLineString(getCoordinateSequence(tokenizer, ordinateFlags))
    }

    /**
     * Creates a `LinearRing` using the next token in the stream.
     *
     * @param  tokenizer        tokenizer over a stream of text in Well-known Text
     * format. The next tokens must form a &lt;LineString Text&gt;.
     * @return                  a `LinearRing` specified by the next
     * token in the stream
     * @throws  IOException     if an I/O error occurs
     * @throws  ParseException  if the coordinates used to create the `LinearRing`
     * do not form a closed linestring, or if an unexpected token was
     * encountered
     */
    @Throws(IOException::class, ParseException::class)
    private fun readLinearRingText(tokenizer: StreamTokenizer, ordinateFlags: EnumSet<Ordinate>): LinearRing? {
        return geometryFactory.createLinearRing(getCoordinateSequence(tokenizer, ordinateFlags))
    }

    /**
     * Creates a `MultiPoint` using the next tokens in the stream.
     *
     * @param  tokenizer        tokenizer over a stream of text in Well-known Text
     * format. The next tokens must form a &lt;MultiPoint Text&gt;.
     * @return                  a `MultiPoint` specified by the next
     * token in the stream
     * @throws  IOException     if an I/O error occurs
     * @throws  ParseException  if an unexpected token was encountered
     */
    @Throws(IOException::class, ParseException::class)
    private fun readMultiPointText(tokenizer: StreamTokenizer, ordinateFlags: EnumSet<Ordinate>): MultiPoint? {
        var nextToken = getNextEmptyOrOpener(tokenizer)
        if (nextToken == WKTConstants.EMPTY) {
            return geometryFactory.createMultiPoint(arrayOf<Point>())
        }

        // check for old-style JTS syntax (no parentheses surrounding Point coordinates) and parse it if present
        // MD 2009-02-21 - this is only provided for backwards compatibility for a few versions

        // check for old-style JTS syntax (no parentheses surrounding Point coordinates) and parse it if present
        // MD 2009-02-21 - this is only provided for backwards compatibility for a few versions
        if (allowOldJtsMultipointSyntax) {
            val nextWord = lookAheadWord(tokenizer)
            if (nextWord !== L_PAREN) {
                return geometryFactory.createMultiPoint(
                    getCoordinateSequenceOldMultiPoint(tokenizer, ordinateFlags)
                )
            }
        }

        val points: ArrayList<Point> = ArrayList()
        var point = readPointText(tokenizer, ordinateFlags)!!
        points.add(point)
        nextToken = getNextCloserOrComma(tokenizer)
        while (nextToken == COMMA) {
            point = readPointText(tokenizer, ordinateFlags)!!
            points.add(point)
            nextToken = getNextCloserOrComma(tokenizer)
        }

        return geometryFactory.createMultiPoint(points.toTypedArray())

    }

    /**
     * Creates a `Polygon` using the next token in the stream.
     *
     * @param  tokenizer        tokenizer over a stream of text in Well-known Text
     * format. The next tokens must form a &lt;Polygon Text&gt;.
     * @return                  a `Polygon` specified by the next token
     * in the stream
     * @throws  ParseException  if the coordinates used to create the `Polygon`
     * shell and holes do not form closed linestrings, or if an unexpected
     * token was encountered.
     * @throws  IOException     if an I/O error occurs
     */
    @Throws(IOException::class, ParseException::class)
    private fun readPolygonText(tokenizer: StreamTokenizer, ordinateFlags: EnumSet<Ordinate>): Polygon? {
        var nextToken = getNextEmptyOrOpener(tokenizer)
        if (nextToken == WKTConstants.EMPTY) {
            return geometryFactory.createPolygon()
        }
        val holes: ArrayList<LinearRing> = ArrayList()
        val shell = readLinearRingText(tokenizer, ordinateFlags)
        nextToken = getNextCloserOrComma(tokenizer)
        while (nextToken == COMMA) {
            val hole = readLinearRingText(tokenizer, ordinateFlags)!!
            holes.add(hole)
            nextToken = getNextCloserOrComma(tokenizer)
        }
        return geometryFactory.createPolygon(shell, holes.toTypedArray())
    }

    /**
     * Creates a `MultiLineString` using the next token in the stream.
     *
     * @param  tokenizer        tokenizer over a stream of text in Well-known Text
     * format. The next tokens must form a &lt;MultiLineString Text&gt;.
     * @return                  a `MultiLineString` specified by the
     * next token in the stream
     * @throws  IOException     if an I/O error occurs
     * @throws  ParseException  if an unexpected token was encountered
     */
    @Throws(IOException::class, ParseException::class)
    private fun readMultiLineStringText(tokenizer: StreamTokenizer, ordinateFlags: EnumSet<Ordinate>): MultiLineString? {
        var nextToken = getNextEmptyOrOpener(tokenizer)
        if (nextToken == WKTConstants.EMPTY) {
            return geometryFactory.createMultiLineString()
        }

        val lineStrings: ArrayList<LineString> = ArrayList()
        do {
            val lineString = readLineStringText(tokenizer, ordinateFlags)!!
            lineStrings.add(lineString)
            nextToken = getNextCloserOrComma(tokenizer)
        } while (nextToken == COMMA)

        return geometryFactory.createMultiLineString(lineStrings.toTypedArray())
    }

    /**
     * Creates a `MultiPolygon` using the next token in the stream.
     *
     * @param  tokenizer        tokenizer over a stream of text in Well-known Text
     * format. The next tokens must form a &lt;MultiPolygon Text&gt;.
     * @return                  a `MultiPolygon` specified by the next
     * token in the stream, or if if the coordinates used to create the
     * `Polygon` shells and holes do not form closed linestrings.
     * @throws  IOException     if an I/O error occurs
     * @throws  ParseException  if an unexpected token was encountered
     */
    @Throws(IOException::class, ParseException::class)
    private fun readMultiPolygonText(tokenizer: StreamTokenizer, ordinateFlags: EnumSet<Ordinate>): MultiPolygon? {
        var nextToken = getNextEmptyOrOpener(tokenizer)
        if (nextToken == WKTConstants.EMPTY) {
            return geometryFactory.createMultiPolygon()
        }
        val polygons: ArrayList<Polygon> = ArrayList()
        do {
            val polygon = readPolygonText(tokenizer, ordinateFlags)!!
            polygons.add(polygon)
            nextToken = getNextCloserOrComma(tokenizer)
        } while (nextToken == COMMA)

        return geometryFactory.createMultiPolygon(polygons.toTypedArray())
    }

    /**
     * Creates a `GeometryCollection` using the next token in the
     * stream.
     *
     * @param  tokenizer        tokenizer over a stream of text in Well-known Text
     * format. The next tokens must form a &lt;GeometryCollection Text&gt;.
     * @return                  a `GeometryCollection` specified by the
     * next token in the stream
     * @throws  ParseException  if the coordinates used to create a `Polygon`
     * shell and holes do not form closed linestrings, or if an unexpected
     * token was encountered
     * @throws  IOException     if an I/O error occurs
     */
    @Throws(IOException::class, ParseException::class)
    private fun readGeometryCollectionText(
        tokenizer: StreamTokenizer,
        ordinateFlags: EnumSet<Ordinate>
    ): GeometryCollection? {
        var nextToken = getNextEmptyOrOpener(tokenizer)
        if (nextToken == WKTConstants.EMPTY) {
            return geometryFactory.createGeometryCollection()
        }
        val geometries: ArrayList<Geometry> = ArrayList()
        do {
            val geometry = readGeometryTaggedText(tokenizer)!!
            geometries.add(geometry)
            nextToken = getNextCloserOrComma(tokenizer)
        } while (nextToken == COMMA)

        return geometryFactory.createGeometryCollection(geometries.toTypedArray())

    }
    
    companion object {
        private const val COMMA = ","
        private const val L_PAREN = "("
        private const val R_PAREN = ")"
        private const val NAN_SYMBOL = "NaN"
        private val csFactoryXYZM: CoordinateSequenceFactory = instance()

        /**
         * Flag indicating that the old notation of coordinates in JTS
         * is supported.
         */
        // ToDo: this is enabled by default in JTS!
        private const val ALLOW_OLD_JTS_COORDINATE_SYNTAX = false

        /**
         * Flag indicating that the old notation of MultiPoint coordinates in JTS
         * is supported.
         */
        private const val ALLOW_OLD_JTS_MULTIPOINT_SYNTAX = true
    }
}
