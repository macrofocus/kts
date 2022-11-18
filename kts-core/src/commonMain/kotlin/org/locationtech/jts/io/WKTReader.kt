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
package org.locationtech.jts.io

import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.impl.CoordinateArraySequenceFactory
import org.locationtech.jts.legacy.*
import org.locationtech.jts.util.Assert
import kotlin.jvm.JvmOverloads

/**
 * Converts a geometry in Well-Known Text format to a [Geometry].
 *
 * `WKTReader` supports
 * extracting `Geometry` objects from either [Reader]s or
 * [String]s. This allows it to function as a parser to read `Geometry`
 * objects from text blocks embedded in other data formats (e.g. XML). <P>
</P> *
 * A `WKTReader` is parameterized by a `GeometryFactory`,
 * to allow it to create `Geometry` objects of the appropriate
 * implementation. In particular, the `GeometryFactory`
 * determines the `PrecisionModel` and `SRID` that is
 * used. <P>
 *
 * The `WKTReader` converts all input numbers to the precise
 * internal representation.
</P> *
 * As of version 1.15, JTS can read (but not write) WKT syntax
 * which specifies coordinate dimension Z, M or ZM as modifiers (e.g. POINT Z)
 * or in the name of the geometry type (e.g. LINESTRINGZM).
 * If the coordinate dimension is specified it will be set in the created geometry.
 * If the coordinate dimension is not specified, the default behaviour is to
 * create XYZ geometry (this is backwards compatible with older JTS versions).
 * This can be altered to create XY geometry by
 * calling [.setIsOldJtsCoordinateSyntaxAllowed].
 *
 * A reader can be set to ensure the input is structurally valid
 * by calling [.setFixStructure].
 * This ensures that geometry can be constructed without errors due to missing coordinates.
 * The created geometry may still be topologically invalid.
 *
 * <h3>Notes:</h3>
 *
 *  * Keywords are case-insensitive.
 *  * The reader supports non-standard "LINEARRING" tags.
 *  * The reader uses <tt>Double.parseDouble</tt> to perform the conversion of ASCII
 * numbers to floating point.  This means it supports the Java
 * syntax for floating point literals (including scientific notation).
 *
 * <h3>Syntax</h3>
 * The following syntax specification describes the version of Well-Known Text
 * supported by JTS.
 * (The specification uses a syntax language similar to that used in
 * the C and Java language specifications.)
 *
 * <blockquote><pre>
 * *WKTGeometry:* one of*
 *
 * WKTPoint  WKTLineString  WKTLinearRing  WKTPolygon
 * WKTMultiPoint  WKTMultiLineString  WKTMultiPolygon
 * WKTGeometryCollection*
 *
 * *WKTPoint:* **POINT***[Dimension]* **( ***Coordinate* **)**
 *
 * *WKTLineString:* **LINESTRING***[Dimension]* *CoordinateSequence*
 *
 * *WKTLinearRing:* **LINEARRING***[Dimension]* *CoordinateSequence*
 *
 * *WKTPolygon:* **POLYGON***[Dimension]* *CoordinateSequenceList*
 *
 * *WKTMultiPoint:* **MULTIPOINT***[Dimension]* *CoordinateSingletonList*
 *
 * *WKTMultiLineString:* **MULTILINESTRING***[Dimension]* *CoordinateSequenceList*
 *
 * *WKTMultiPolygon:*
 * **MULTIPOLYGON***[Dimension]* **(** *CoordinateSequenceList {* , *CoordinateSequenceList }* **)**
 *
 * *WKTGeometryCollection: *
 * **GEOMETRYCOLLECTION***[Dimension]* ** (** *WKTGeometry {* , *WKTGeometry }* **)**
 *
 * *CoordinateSingletonList:*
 * **(** *CoordinateSingleton {* **,** *CoordinateSingleton }* **)**
 * | **EMPTY**
 *
 * *CoordinateSingleton:*
 * **(** *Coordinate* **)**
 * | **EMPTY**
 *
 * *CoordinateSequenceList:*
 * **(** *CoordinateSequence {* **,** *CoordinateSequence }* **)**
 * | **EMPTY**
 *
 * *CoordinateSequence:*
 * **(** *Coordinate {* , *Coordinate }* **)**
 * | **EMPTY**
 *
 * *Coordinate:
 * Number Number Number<sub>opt</sub> Number<sub>opt</sub>*
 *
 * *Number:* A Java-style floating-point number (including <tt>NaN</tt>, with arbitrary case)
 *
 * *Dimension:*
 * **Z**|** Z**|**M**|** M**|**ZM**|** ZM**
 *
</pre></blockquote> *
 *
 * @version 1.7
 * @see WKTWriter
 */
class WKTReader @JvmOverloads constructor(private var geometryFactory: GeometryFactory = GeometryFactory()) {
    private val csFactory: CoordinateSequenceFactory = geometryFactory.coordinateSequenceFactory
    private val precisionModel: PrecisionModel
    private var isAllowOldJtsCoordinateSyntax = ALLOW_OLD_JTS_COORDINATE_SYNTAX
    private var isAllowOldJtsMultipointSyntax = ALLOW_OLD_JTS_MULTIPOINT_SYNTAX
    private var isFixStructure = false
    /**
     * Creates a reader that creates objects using the given
     * [GeometryFactory].
     *
     * @param  geometryFactory  the factory used to create `Geometry`s.
     */
    /**
     * Creates a reader that creates objects using the default [GeometryFactory].
     */
    init {
        precisionModel = geometryFactory.precisionModel
    }

    /**
     * Sets a flag indicating, that coordinates may have 3 ordinate values even though no Z or M ordinate indicator
     * is present. The default value is [.ALLOW_OLD_JTS_COORDINATE_SYNTAX].
     *
     * @param value a boolean value
     */
    fun setIsOldJtsCoordinateSyntaxAllowed(value: Boolean) {
        isAllowOldJtsCoordinateSyntax = value
    }

    /**
     * Sets a flag indicating, that point coordinates in a MultiPoint geometry must not be enclosed in paren.
     * The default value is [.ALLOW_OLD_JTS_MULTIPOINT_SYNTAX]
     * @param value a boolean value
     */
    fun setIsOldJtsMultiPointSyntaxAllowed(value: Boolean) {
        isAllowOldJtsMultipointSyntax = value
    }

    /**
     * Sets a flag indicating that the structure of input geometry should be fixed
     * so that the geometry can be constructed without error.
     * This involves adding coordinates if the input coordinate sequence is shorter than required.
     *
     * @param isFixStructure true if the input structure should be fixed
     *
     * @see LinearRing.MINIMUM_VALID_SIZE
     */
    fun setFixStructure(isFixStructure: Boolean) {
        this.isFixStructure = isFixStructure
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
    fun read(wellKnownText: String): Geometry {
        val reader: StringReader = StringReader(wellKnownText)
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
    fun read(reader: Reader): Geometry {
        val tokenizer: StreamTokenizer = createTokenizer(reader)
        return try {
            readGeometryTaggedText(tokenizer)
        } catch (e: IOException) {
            throw ParseException(e.toString())
        }
    }

    /**
     * Reads a `Coordinate` from a stream using the given [StreamTokenizer].
     *
     * All ordinate values are read, but -depending on the [CoordinateSequenceFactory] of the
     * underlying [GeometryFactory]- not necessarily all can be handled. Those are silently dropped.
     *
     * @param tokenizer the tokenizer to use
     * @param ordinateFlags a bit-mask defining the ordinates to read.
     * @param tryParen a value indicating if a starting [.L_PAREN] should be probed.
     * @return a [Coordinate] of appropriate dimension containing the read ordinate values
     * @throws  IOException     if an I/O error occurs
     * @throws  ParseException  if an unexpected token was encountered
     */
    @Throws(IOException::class, ParseException::class)
    private fun getCoordinate(
        tokenizer: StreamTokenizer,
        ordinateFlags: EnumSet<Ordinate>,
        tryParen: Boolean
    ): Coordinate {
        var opened = false
        if (tryParen && isOpenerNext(tokenizer)) {
            tokenizer.nextToken()
            opened = true
        }

        // create a sequence for one coordinate
        val offsetM = if (ordinateFlags.contains(Ordinate.Z)) 1 else 0
        val coord = createCoordinate(ordinateFlags)
        coord.setOrdinate(CoordinateSequence.X, precisionModel.makePrecise(getNextNumber(tokenizer)))
        coord.setOrdinate(CoordinateSequence.Y, precisionModel.makePrecise(getNextNumber(tokenizer)))

        // additionally read other vertices
        if (ordinateFlags.contains(Ordinate.Z)) coord.setOrdinate(CoordinateSequence.Z, getNextNumber(tokenizer))
        if (ordinateFlags.contains(Ordinate.M)) coord.setOrdinate(
            CoordinateSequence.Z + offsetM,
            getNextNumber(tokenizer)
        )
        if (ordinateFlags.size == 2 && isAllowOldJtsCoordinateSyntax && isNumberNext(tokenizer)) {
            coord.setOrdinate(CoordinateSequence.Z, getNextNumber(tokenizer))
        }

        // read close token if it was opened here
        if (opened) {
            getNextCloser(tokenizer)
        }
        return coord
    }

    private fun createCoordinate(ordinateFlags: EnumSet<Ordinate>): Coordinate {
        val hasZ: Boolean = ordinateFlags.contains(Ordinate.Z)
        val hasM: Boolean = ordinateFlags.contains(Ordinate.M)
        if (hasZ && hasM) return CoordinateXYZM()
        if (hasM) return CoordinateXYM()
        return if (hasZ || isAllowOldJtsCoordinateSyntax) Coordinate() else CoordinateXY()
    }

    /**
     * Reads a `Coordinate` from a stream using the given [StreamTokenizer].
     *
     * All ordinate values are read, but -depending on the [CoordinateSequenceFactory] of the
     * underlying [GeometryFactory]- not necessarily all can be handled. Those are silently dropped.
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
        ordinateFlags: EnumSet<Ordinate>,
        minSize: Int,
        isRing: Boolean
    ): CoordinateSequence {
        if (getNextEmptyOrOpener(tokenizer) == WKTConstants.EMPTY) return createCoordinateSequenceEmpty(ordinateFlags)
        val coordinates: MutableList<Coordinate> = ArrayList()
        do {
            coordinates.add(getCoordinate(tokenizer, ordinateFlags, false))
        } while (getNextCloserOrComma(tokenizer) == COMMA)
        if (isFixStructure) {
            fixStructure(coordinates, minSize, isRing)
        }
        val coordArray = coordinates.toTypedArray()
        return csFactory.create(coordArray)
    }

    @Throws(IOException::class, ParseException::class)
    private fun createCoordinateSequenceEmpty(ordinateFlags: EnumSet<Ordinate>): CoordinateSequence {
        return csFactory.create(0, toDimension(ordinateFlags), if (ordinateFlags.contains(Ordinate.M)) 1 else 0)
    }

    /**
     * Reads a `CoordinateSequence` from a stream using the given [StreamTokenizer]
     * for an old-style JTS MultiPoint (Point coordinates not enclosed in parentheses).
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
    ): CoordinateSequence {
        val coordinates: MutableList<Coordinate> = ArrayList()
        do {
            coordinates.add(getCoordinate(tokenizer, ordinateFlags, true))
        } while (getNextCloserOrComma(tokenizer) == COMMA)
        val coordArray = coordinates.toTypedArray()
        return csFactory.create(coordArray)
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
        if (dimension == 2 && isAllowOldJtsCoordinateSyntax) dimension++
        return dimension
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
        val type: Int = tokenizer.nextToken()
        when (type) {
            StreamTokenizer.TT_WORD -> {
                return if (tokenizer.sval.equals(NAN_SYMBOL, ignoreCase = true)) {
                    Double.NaN
                } else {
                    try {
                        tokenizer.sval!!.toDouble()
                    } catch (ex: NumberFormatException) {
                        throw parseErrorWithLine(tokenizer, "Invalid number: " + tokenizer.sval)
                    }
                }
            }
        }
        throw parseErrorExpected(tokenizer, "number")
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
    private fun getNextCloser(tokenizer: StreamTokenizer): String {
        val nextWord = getNextWord(tokenizer)
        if (nextWord == R_PAREN) {
            return nextWord
        }
        throw parseErrorExpected(tokenizer, R_PAREN)
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
    private fun readGeometryTaggedText(tokenizer: StreamTokenizer): Geometry {
        val ordinateFlags: EnumSet<Ordinate> = enumSetOf(Ordinate.X, Ordinate.Y)
        val type: String = getNextWord(tokenizer).uppercase()
        if (type.endsWith(WKTConstants.ZM)) {
            ordinateFlags.add(Ordinate.Z)
            ordinateFlags.add(Ordinate.M)
        } else if (type.endsWith(WKTConstants.Z)) {
            ordinateFlags.add(Ordinate.Z)
        } else if (type.endsWith(WKTConstants.M)) {
            ordinateFlags.add(Ordinate.M)
        }
        return readGeometryTaggedText(tokenizer, type, ordinateFlags)
    }

    @Throws(IOException::class, ParseException::class)
    private fun readGeometryTaggedText(
        tokenizer: StreamTokenizer,
        type: String,
        ordinateFlags: EnumSet<Ordinate>
    ): Geometry {
        var ordinateFlags: EnumSet<Ordinate> = ordinateFlags
        if (ordinateFlags.size == 2) {
            ordinateFlags = getNextOrdinateFlags(tokenizer)
        }

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
                geometryFactory.sRID, csFactoryXYZM
            )
        }
        if (isTypeName(tokenizer, type, WKTConstants.POINT)) {
            return readPointText(tokenizer, ordinateFlags)
        } else if (isTypeName(tokenizer, type, WKTConstants.LINESTRING)) {
            return readLineStringText(tokenizer, ordinateFlags)
        } else if (isTypeName(tokenizer, type, WKTConstants.LINEARRING)) {
            return readLinearRingText(tokenizer, ordinateFlags)
        } else if (isTypeName(tokenizer, type, WKTConstants.POLYGON)) {
            return readPolygonText(tokenizer, ordinateFlags)
        } else if (isTypeName(tokenizer, type, WKTConstants.MULTIPOINT)) {
            return readMultiPointText(tokenizer, ordinateFlags)
        } else if (isTypeName(tokenizer, type, WKTConstants.MULTILINESTRING)) {
            return readMultiLineStringText(tokenizer, ordinateFlags)
        } else if (isTypeName(tokenizer, type, WKTConstants.MULTIPOLYGON)) {
            return readMultiPolygonText(tokenizer, ordinateFlags)
        } else if (isTypeName(tokenizer, type, WKTConstants.GEOMETRYCOLLECTION)) {
            return readGeometryCollectionText(tokenizer, ordinateFlags)
        }
        throw parseErrorWithLine(tokenizer, "Unknown geometry type: $type")
    }

    @Throws(ParseException::class)
    private fun isTypeName(tokenizer: StreamTokenizer, type: String, typeName: String): Boolean {
        if (!type.startsWith(typeName)) return false
        val modifiers = type.substring(typeName.length)
        val isValidMod =
            modifiers.length <= 2 && (modifiers.isEmpty() || modifiers == WKTConstants.Z || modifiers == WKTConstants.M || modifiers == WKTConstants.ZM)
        if (!isValidMod) {
            throw parseErrorWithLine(tokenizer, "Invalid dimension modifiers: $type")
        }
        return true
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
    private fun readPointText(
        tokenizer: StreamTokenizer,
        ordinateFlags: EnumSet<Ordinate>
    ): Point {
        return geometryFactory.createPoint(getCoordinateSequence(tokenizer, ordinateFlags, 1, false))
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
    private fun readLineStringText(
        tokenizer: StreamTokenizer,
        ordinateFlags: EnumSet<Ordinate>
    ): LineString {
        return geometryFactory.createLineString(
            getCoordinateSequence(
                tokenizer,
                ordinateFlags,
                LineString.MINIMUM_VALID_SIZE,
                false
            )
        )
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
    private fun readLinearRingText(
        tokenizer: StreamTokenizer,
        ordinateFlags: EnumSet<Ordinate>
    ): LinearRing {
        return geometryFactory.createLinearRing(
            getCoordinateSequence(
                tokenizer,
                ordinateFlags,
                LinearRing.MINIMUM_VALID_SIZE,
                true
            )
        )
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
    private fun readMultiPointText(
        tokenizer: StreamTokenizer,
        ordinateFlags: EnumSet<Ordinate>
    ): MultiPoint {
        var nextToken = getNextEmptyOrOpener(tokenizer)
        if (nextToken == WKTConstants.EMPTY) {
            return geometryFactory.createMultiPoint(emptyArray<Point>())
        }

        // check for old-style JTS syntax (no parentheses surrounding Point coordinates) and parse it if present
        // MD 2009-02-21 - this is only provided for backwards compatibility for a few versions
        if (isAllowOldJtsMultipointSyntax) {
            val nextWord = lookAheadWord(tokenizer)
            if (nextWord !== L_PAREN && nextWord !== WKTConstants.EMPTY) {
                return geometryFactory.createMultiPoint(
                    getCoordinateSequenceOldMultiPoint(tokenizer, ordinateFlags)
                )
            }
        }
        val points: MutableList<Point> = ArrayList()
        var point = readPointText(tokenizer, ordinateFlags)
        points.add(point)
        nextToken = getNextCloserOrComma(tokenizer)
        while (nextToken == COMMA) {
            point = readPointText(tokenizer, ordinateFlags)
            points.add(point)
            nextToken = getNextCloserOrComma(tokenizer)
        }
        val array = arrayOfNulls<Point>(points.size)
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
    private fun readPolygonText(
        tokenizer: StreamTokenizer,
        ordinateFlags: EnumSet<Ordinate>
    ): Polygon {
        var nextToken = getNextEmptyOrOpener(tokenizer)
        if (nextToken == WKTConstants.EMPTY) {
            return geometryFactory.createPolygon(createCoordinateSequenceEmpty(ordinateFlags))
        }
        val holes: MutableList<LinearRing> = ArrayList()
        val shell = readLinearRingText(tokenizer, ordinateFlags)
        nextToken = getNextCloserOrComma(tokenizer)
        while (nextToken == COMMA) {
            val hole = readLinearRingText(tokenizer, ordinateFlags)
            holes.add(hole)
            nextToken = getNextCloserOrComma(tokenizer)
        }
        val array = arrayOfNulls<LinearRing>(holes.size)
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
    private fun readMultiLineStringText(
        tokenizer: StreamTokenizer,
        ordinateFlags: EnumSet<Ordinate>
    ): MultiLineString {
        var nextToken = getNextEmptyOrOpener(tokenizer)
        if (nextToken == WKTConstants.EMPTY) {
            return geometryFactory.createMultiLineString()
        }
        val lineStrings: MutableList<LineString> = ArrayList()
        do {
            val lineString = readLineStringText(tokenizer, ordinateFlags)
            lineStrings.add(lineString)
            nextToken = getNextCloserOrComma(tokenizer)
        } while (nextToken == COMMA)
        val array = arrayOfNulls<LineString>(lineStrings.size)
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
    private fun readMultiPolygonText(
        tokenizer: StreamTokenizer,
        ordinateFlags: EnumSet<Ordinate>
    ): MultiPolygon {
        var nextToken = getNextEmptyOrOpener(tokenizer)
        if (nextToken == WKTConstants.EMPTY) {
            return geometryFactory.createMultiPolygon()
        }
        val polygons: MutableList<Polygon> = ArrayList()
        do {
            val polygon = readPolygonText(tokenizer, ordinateFlags)
            polygons.add(polygon)
            nextToken = getNextCloserOrComma(tokenizer)
        } while (nextToken == COMMA)
        val array = arrayOfNulls<Polygon>(polygons.size)
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
    ): GeometryCollection {
        var nextToken = getNextEmptyOrOpener(tokenizer)
        if (nextToken == WKTConstants.EMPTY) {
            return geometryFactory.createGeometryCollection()
        }
        val geometries: MutableList<Geometry> = ArrayList()
        do {
            val geometry = readGeometryTaggedText(tokenizer)
            geometries.add(geometry)
            nextToken = getNextCloserOrComma(tokenizer)
        } while (nextToken == COMMA)
        val array = arrayOfNulls<Geometry>(geometries.size)
        return geometryFactory.createGeometryCollection(geometries.toTypedArray())
    }

    companion object {
        private const val COMMA = ","
        private const val L_PAREN = "("
        private const val R_PAREN = ")"
        private const val NAN_SYMBOL = "NaN"
        private val csFactoryXYZM: CoordinateSequenceFactory = CoordinateArraySequenceFactory.instance()

        /**
         * Flag indicating that the old notation of coordinates in JTS
         * is supported.
         */
        private const val ALLOW_OLD_JTS_COORDINATE_SYNTAX = true

        /**
         * Flag indicating that the old notation of MultiPoint coordinates in JTS
         * is supported.
         */
        private const val ALLOW_OLD_JTS_MULTIPOINT_SYNTAX = true

        /**
         * Utility function to create the tokenizer
         * @param reader a reader
         *
         * @return a WKT Tokenizer.
         */
        private fun createTokenizer(reader: Reader): StreamTokenizer {
            val tokenizer: StreamTokenizer = StreamTokenizer(reader)
            // set tokenizer to NOT parse numbers
            tokenizer.resetSyntax()
            tokenizer.wordChars('a'.code, 'z'.code)
            tokenizer.wordChars('A'.code, 'Z'.code)
            tokenizer.wordChars(128 + 32, 255)
            tokenizer.wordChars('0'.code, '9'.code)
            tokenizer.wordChars('-'.code, '-'.code)
            tokenizer.wordChars('+'.code, '+'.code)
            tokenizer.wordChars('.'.code, '.'.code)
            tokenizer.whitespaceChars(0, ' '.code)
            tokenizer.commentChar('#'.code)
            return tokenizer
        }

        private fun fixStructure(coords: MutableList<Coordinate>, minSize: Int, isRing: Boolean) {
            if (coords.size == 0) return
            if (isRing && !isClosed(coords)) {
                coords.add(coords[0].copy())
            }
            while (coords.size < minSize) {
                coords.add(coords[coords.size - 1].copy())
            }
        }

        private fun isClosed(coords: List<Coordinate>): Boolean {
            if (coords.isEmpty()) return true
            return !(coords.size == 1
                    || !coords[0].equals2D(coords[coords.size - 1]))
        }

        /**
         * Tests if the next token in the stream is a number
         *
         * @param tokenizer the tokenizer
         * @return `true` if the next token is a number, otherwise `false`
         * @throws  IOException     if an I/O error occurs
         */
//        @Throws(IOException::class)
        private fun isNumberNext(tokenizer: StreamTokenizer): Boolean {
            val type: Int = tokenizer.nextToken()
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
            val type: Int = tokenizer.nextToken()
            tokenizer.pushBack()
            return type == '('.code
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
        private fun getNextEmptyOrOpener(tokenizer: StreamTokenizer): String {
            var nextWord = getNextWord(tokenizer)
            if (nextWord.equals(WKTConstants.Z, ignoreCase = true)) {
                //z = true;
                nextWord = getNextWord(tokenizer)
            } else if (nextWord.equals(WKTConstants.M, ignoreCase = true)) {
                //m = true;
                nextWord = getNextWord(tokenizer)
            } else if (nextWord.equals(WKTConstants.ZM, ignoreCase = true)) {
                //z = true;
                //m = true;
                nextWord = getNextWord(tokenizer)
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
            val result: EnumSet<Ordinate> = enumSetOf(Ordinate.X, Ordinate.Y)
            val nextWord = lookAheadWord(tokenizer).uppercase()
            if (nextWord.equals(WKTConstants.Z, ignoreCase = true)) {
                tokenizer.nextToken()
                result.add(Ordinate.Z)
            } else if (nextWord.equals(WKTConstants.M, ignoreCase = true)) {
                tokenizer.nextToken()
                result.add(Ordinate.M)
            } else if (nextWord.equals(WKTConstants.ZM, ignoreCase = true)) {
                tokenizer.nextToken()
                result.add(Ordinate.Z)
                result.add(Ordinate.M)
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
        private fun lookAheadWord(tokenizer: StreamTokenizer): String {
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
        private fun getNextCloserOrComma(tokenizer: StreamTokenizer): String {
            val nextWord = getNextWord(tokenizer)
            if (nextWord == COMMA || nextWord == R_PAREN) {
                return nextWord
            }
            throw parseErrorExpected(tokenizer, COMMA + " or " + R_PAREN)
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
        private fun getNextWord(tokenizer: StreamTokenizer): String {
            val type: Int = tokenizer.nextToken()
            when (type) {
                StreamTokenizer.TT_WORD -> {
                    val word: String = tokenizer.sval!!
                    return if (word.equals(WKTConstants.EMPTY, ignoreCase = true)) WKTConstants.EMPTY else word
                }

                '('.code -> return L_PAREN
                ')'.code -> return R_PAREN
                ','.code -> return COMMA
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
        private fun parseErrorExpected(
            tokenizer: StreamTokenizer,
            expected: String
        ): ParseException {
            // throws Asserts for tokens that should never be seen
            if (tokenizer.ttype == StreamTokenizer.TT_NUMBER) Assert.shouldNeverReachHere("Unexpected NUMBER token")
            if (tokenizer.ttype == StreamTokenizer.TT_EOL) Assert.shouldNeverReachHere("Unexpected EOL token")
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
        private fun parseErrorWithLine(
            tokenizer: StreamTokenizer,
            msg: String
        ): ParseException {
            return ParseException(msg + " (line " + tokenizer.lineno() + ")")
        }

        /**
         * Gets a description of the current token type
         * @param tokenizer the tokenizer
         * @return a description of the current token
         */
        private fun tokenString(tokenizer: StreamTokenizer): String {
            when (tokenizer.ttype) {
                StreamTokenizer.TT_NUMBER -> return "<NUMBER>"
                StreamTokenizer.TT_EOL -> return "End-of-Line"
                StreamTokenizer.TT_EOF -> return "End-of-Stream"
                StreamTokenizer.TT_WORD -> return "'" + tokenizer.sval + "'"
            }
            return "'" + tokenizer.ttype.toChar() + "'"
        }
    }
}