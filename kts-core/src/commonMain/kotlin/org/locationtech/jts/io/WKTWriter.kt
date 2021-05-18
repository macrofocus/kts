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
import org.locationtech.jts.io.OrdinateFormat.Companion.create
import org.locationtech.jts.legacy.*
import org.locationtech.jts.util.Assert.shouldNeverReachHere
import kotlin.jvm.JvmStatic

/**
 * Writes the Well-Known Text representation of a [Geometry].
 * The Well-Known Text format is defined in the
 * OGC [
 * *Simple Features Specification for SQL*](http://www.opengis.org/techno/specs.htm).
 * See [WKTReader] for a formal specification of the format syntax.
 *
 *
 * The `WKTWriter` outputs coordinates rounded to the precision
 * model. Only the maximum number of decimal places
 * necessary to represent the ordinates to the required precision will be
 * output.
 *
 *
 * The SFS WKT spec does not define a special tag for [LinearRing]s.
 * Under the spec, rings are output as `LINESTRING`s.
 * In order to allow precisely specifying constructed geometries,
 * JTS also supports a non-standard `LINEARRING` tag which is used
 * to output LinearRings.
 *
 * @version 1.7
 * @see WKTReader
 */
class WKTWriter constructor(outputDimension: Int) {
    constructor() : this(OUTPUT_DIMENSION)

    /**
     * A filter implementation to test if a coordinate sequence actually has
     * meaningful values for an ordinate bit-pattern
     */
    private inner class CheckOrdinatesFilter(checkOrdinateFlags: EnumSet<Ordinate>) : CoordinateSequenceFilter {
        private val checkOrdinateFlags: EnumSet<Ordinate> = checkOrdinateFlags

        /**
         * Gets the evaluated ordinate bit-pattern
         *
         * @return A bit-pattern of ordinates with valid values masked by [.checkOrdinateFlags].
         */
        val outputOrdinates: EnumSet<Ordinate> = enumSetOf(Ordinate.X, Ordinate.Y)

        /** @see CoordinateSequenceFilter.isGeometryChanged
         */
        override fun filter(seq: CoordinateSequence, i: Int) {
            if (checkOrdinateFlags.contains(Ordinate.Z) && !outputOrdinates.contains(Ordinate.Z)) {
                if (!Math.isNaN(seq.getZ(i))) outputOrdinates.add(Ordinate.Z)
            }
            if (checkOrdinateFlags.contains(Ordinate.M) && !outputOrdinates.contains(Ordinate.M)) {
                if (!Math.isNaN(seq.getM(i))) outputOrdinates.add(Ordinate.M)
            }
        }

        /** @see CoordinateSequenceFilter.isGeometryChanged
         */
        override val isGeometryChanged: Boolean
            get() = false

        /** @see CoordinateSequenceFilter.isDone
         */
        override val isDone: Boolean
            get() = outputOrdinates == checkOrdinateFlags

    }

    /**
     * Gets a bit-pattern defining which ordinates should be
     * @return an ordinate bit-pattern
     * @see .setOutputOrdinates
     */
    val outputOrdinates: EnumSet<Ordinate>
    private val outputDimension: Int
    private var precisionModel: PrecisionModel? = null
    private var ordinateFormat: OrdinateFormat? = null
    private var isFormatted = false
    private var coordsPerLine = -1
    private var indentTabStr: String? = null

    /**
     * Sets whether the output will be formatted.
     *
     * @param isFormatted true if the output is to be formatted
     */
    fun setFormatted(isFormatted: Boolean) {
        this.isFormatted = isFormatted
    }

    /**
     * Sets the maximum number of coordinates per line
     * written in formatted output.
     * If the provided coordinate number is &lt;= 0,
     * coordinates will be written all on one line.
     *
     * @param coordsPerLine the number of coordinates per line to output.
     */
    fun setMaxCoordinatesPerLine(coordsPerLine: Int) {
        this.coordsPerLine = coordsPerLine
    }

    /**
     * Sets the tab size to use for indenting.
     *
     * @param size the number of spaces to use as the tab string
     * @throws IllegalArgumentException if the size is non-positive
     */
    fun setTab(size: Int) {
        require(size > 0) { "Tab count must be positive" }
        indentTabStr = stringOfChar(' ', size)
    }

    /**
     * Sets the [Ordinate] that are to be written. Possible members are:
     *
     *  * [Ordinate.X]
     *  * [Ordinate.Y]
     *  * [Ordinate.Z]
     *  * [Ordinate.M]
     *
     * Values of [Ordinate.X] and [Ordinate.Y] are always assumed and not
     * particularly checked for.
     *
     * @param outputOrdinates A set of [Ordinate] values
     */
    fun setOutputOrdinates(outputOrdinates: EnumSet<Ordinate>) {
        this.outputOrdinates.remove(Ordinate.Z)
        this.outputOrdinates.remove(Ordinate.M)
        if (outputDimension == 3) {
            if (outputOrdinates.contains(Ordinate.Z)) this.outputOrdinates.add(Ordinate.Z) else if (outputOrdinates.contains(
                    Ordinate.M
                )
            ) this.outputOrdinates.add(Ordinate.M)
        }
        if (outputDimension == 4) {
            if (outputOrdinates.contains(Ordinate.Z)) this.outputOrdinates.add(Ordinate.Z)
            if (outputOrdinates.contains(Ordinate.M)) this.outputOrdinates.add(Ordinate.M)
        }
    }

    /**
     * Sets a [PrecisionModel] that should be used on the ordinates written.
     *
     * If none/`null` is assigned, the precision model of the [Geometry.getFactory]
     * is used.
     *
     * Note: The precision model is applied to all ordinate values, not just x and y.
     * @param precisionModel
     * the flag indicating if [Coordinate.z]/{} is actually a measure value.
     */
    fun setPrecisionModel(precisionModel: PrecisionModel) {
        this.precisionModel = precisionModel
        ordinateFormat = create(precisionModel.maximumSignificantDigits)
    }

    /**
     * Converts a `Geometry` to its Well-known Text representation.
     *
     * @param  geometry  a `Geometry` to process
     * @return           a &lt;Geometry Tagged Text&gt; string (see the OpenGIS Simple
     * Features Specification)
     */
    fun write(geometry: Geometry): String {
        val sw: Writer = StringWriter()
        try {
            writeFormatted(geometry, false, sw)
        } catch (ex: IOException) {
            shouldNeverReachHere()
        }
        return sw.toString()
    }

    /**
     * Converts a `Geometry` to its Well-known Text representation.
     *
     * @param  geometry  a `Geometry` to process
     */
    @Throws(IOException::class)
    fun write(geometry: Geometry, writer: Writer) {
        // write the geometry
        writeFormatted(geometry, isFormatted, writer)
    }

    /**
     * Same as `write`, but with newlines and spaces to make the
     * well-known text more readable.
     *
     * @param  geometry  a `Geometry` to process
     * @return           a &lt;Geometry Tagged Text&gt; string (see the OpenGIS Simple
     * Features Specification), with newlines and spaces
     */
    fun writeFormatted(geometry: Geometry): String {
        val sw: Writer = StringWriter()
        try {
            writeFormatted(geometry, true, sw)
        } catch (ex: IOException) {
            shouldNeverReachHere()
        }
        return sw.toString()
    }

    /**
     * Same as `write`, but with newlines and spaces to make the
     * well-known text more readable.
     *
     * @param  geometry  a `Geometry` to process
     */
    @Throws(IOException::class)
    fun writeFormatted(geometry: Geometry, writer: Writer) {
        writeFormatted(geometry, true, writer)
    }

    /**
     * Converts a `Geometry` to its Well-known Text representation.
     *
     * @param  geometry  a `Geometry` to process
     */
    @Throws(IOException::class)
    private fun writeFormatted(geometry: Geometry, useFormatting: Boolean, writer: Writer) {
        val formatter = getFormatter(geometry)
        // append the WKT
        appendGeometryTaggedText(geometry, useFormatting, writer, formatter)
    }

    private fun getFormatter(geometry: Geometry): OrdinateFormat {
        // if present use the cached formatter
        if (ordinateFormat != null) return ordinateFormat!!

        // no precision model was specified, so use the geometry's
        val pm = geometry.precisionModel
        return createFormatter(pm)
    }

    /**
     * Converts a `Geometry` to &lt;Geometry Tagged Text&gt; format,
     * then appends it to the writer.
     *
     * @param  geometry           the `Geometry` to process
     * @param  useFormatting      flag indicating that the output should be formatted
     * @param  writer             the output writer to append to
     * @param  formatter       the `DecimalFormatter` to use to convert
     * from a precise coordinate to an external coordinate
     */
    @Throws(IOException::class)
    private fun appendGeometryTaggedText(
        geometry: Geometry, useFormatting: Boolean, writer: Writer,
        formatter: OrdinateFormat
    ) {
        // evaluate the ordinates actually present in the geometry
        val cof = CheckOrdinatesFilter(outputOrdinates)
        geometry.apply(cof)

        // Append the WKT
        appendGeometryTaggedText(
            geometry, cof.outputOrdinates, useFormatting,
            0, writer, formatter
        )
    }

    /**
     * Converts a `Geometry` to &lt;Geometry Tagged Text&gt; format,
     * then appends it to the writer.
     *
     * @param  geometry           the `Geometry` to process
     * @param  useFormatting      flag indicating that the output should be formatted
     * @param  level              the indentation level
     * @param  writer             the output writer to append to
     * @param  formatter       the `DecimalFormatter` to use to convert
     * from a precise coordinate to an external coordinate
     */
    @Throws(IOException::class)
    private fun appendGeometryTaggedText(
        geometry: Geometry, outputOrdinates: EnumSet<Ordinate>, useFormatting: Boolean,
        level: Int, writer: Writer, formatter: OrdinateFormat
    ) {
        indent(useFormatting, level, writer)
        when (geometry) {
            is Point -> {
                appendPointTaggedText(
                    geometry, outputOrdinates, useFormatting,
                    level, writer, formatter
                )
            }
            is LinearRing -> {
                appendLinearRingTaggedText(
                    geometry, outputOrdinates, useFormatting,
                    level, writer, formatter
                )
            }
            is LineString -> {
                appendLineStringTaggedText(
                    geometry, outputOrdinates, useFormatting,
                    level, writer, formatter
                )
            }
            is Polygon -> {
                appendPolygonTaggedText(
                    geometry, outputOrdinates, useFormatting,
                    level, writer, formatter
                )
            }
            is MultiPoint -> {
                appendMultiPointTaggedText(
                    geometry, outputOrdinates,
                    useFormatting, level, writer, formatter
                )
            }
            is MultiLineString -> {
                appendMultiLineStringTaggedText(
                    geometry, outputOrdinates,
                    useFormatting, level, writer, formatter
                )
            }
            is MultiPolygon -> {
                appendMultiPolygonTaggedText(
                    geometry, outputOrdinates,
                    useFormatting, level, writer, formatter
                )
            }
            is GeometryCollection -> {
                appendGeometryCollectionTaggedText(
                    geometry, outputOrdinates,
                    useFormatting, level, writer, formatter
                )
            }
            else -> {
                shouldNeverReachHere(
                    "Unsupported Geometry implementation:"
                            + geometry::class
                )
            }
        }
    }

    /**
     * Converts a `Coordinate` to &lt;Point Tagged Text&gt; format,
     * then appends it to the writer.
     *
     * @param  point           the `Point` to process
     * @param  useFormatting      flag indicating that the output should be formatted
     * @param  level              the indentation level
     * @param  writer             the output writer to append to
     * @param  formatter          the formatter to use when writing numbers
     */
    @Throws(IOException::class)
    private fun appendPointTaggedText(
        point: Point, outputOrdinates: EnumSet<Ordinate>, useFormatting: Boolean,
        level: Int, writer: Writer, formatter: OrdinateFormat
    ) {
        writer.write(WKTConstants.POINT)
        writer.write(" ")
        appendOrdinateText(outputOrdinates, writer)
        appendSequenceText(
            point.coordinateSequence, outputOrdinates, useFormatting,
            level, false, writer, formatter
        )
    }

    /**
     * Converts a `LineString` to &lt;LineString Tagged Text&gt;
     * format, then appends it to the writer.
     *
     * @param  lineString  the `LineString` to process
     * @param  useFormatting      flag indicating that the output should be formatted
     * @param  level              the indentation level
     * @param  writer             the output writer to append to
     * @param  formatter       the `DecimalFormatter` to use to convert
     * from a precise coordinate to an external coordinate
     */
    @Throws(IOException::class)
    private fun appendLineStringTaggedText(
        lineString: LineString, outputOrdinates: EnumSet<Ordinate>, useFormatting: Boolean,
        level: Int, writer: Writer, formatter: OrdinateFormat
    ) {
        writer.write(WKTConstants.LINESTRING)
        writer.write(" ")
        appendOrdinateText(outputOrdinates, writer)
        appendSequenceText(
            lineString.coordinateSequence, outputOrdinates, useFormatting,
            level, false, writer, formatter
        )
    }

    /**
     * Converts a `LinearRing` to &lt;LinearRing Tagged Text&gt;
     * format, then appends it to the writer.
     *
     * @param  linearRing  the `LinearRing` to process
     * @param  useFormatting      flag indicating that the output should be formatted
     * @param  level              the indentation level
     * @param  writer             the output writer to append to
     * @param  formatter       the `DecimalFormatter` to use to convert
     * from a precise coordinate to an external coordinate
     */
    @Throws(IOException::class)
    private fun appendLinearRingTaggedText(
        linearRing: LinearRing, outputOrdinates: EnumSet<Ordinate>, useFormatting: Boolean,
        level: Int, writer: Writer, formatter: OrdinateFormat
    ) {
        writer.write(WKTConstants.LINEARRING)
        writer.write(" ")
        appendOrdinateText(outputOrdinates, writer)
        appendSequenceText(
            linearRing.coordinateSequence, outputOrdinates, useFormatting,
            level, false, writer, formatter
        )
    }

    /**
     * Converts a `Polygon` to &lt;Polygon Tagged Text&gt; format,
     * then appends it to the writer.
     *
     * @param  polygon  the `Polygon` to process
     * @param  useFormatting      flag indicating that the output should be formatted
     * @param  level              the indentation level
     * @param  writer             the output writer to append to
     * @param  formatter       the `DecimalFormatter` to use to convert
     * from a precise coordinate to an external coordinate
     */
    @Throws(IOException::class)
    private fun appendPolygonTaggedText(
        polygon: Polygon, outputOrdinates: EnumSet<Ordinate>, useFormatting: Boolean,
        level: Int, writer: Writer, formatter: OrdinateFormat
    ) {
        writer.write(WKTConstants.POLYGON)
        writer.write(" ")
        appendOrdinateText(outputOrdinates, writer)
        appendPolygonText(
            polygon, outputOrdinates, useFormatting,
            level, false, writer, formatter
        )
    }

    /**
     * Converts a `MultiPoint` to &lt;MultiPoint Tagged Text&gt;
     * format, then appends it to the writer.
     *
     * @param  multipoint  the `MultiPoint` to process
     * @param  useFormatting      flag indicating that the output should be formatted
     * @param  level              the indentation level
     * @param  writer             the output writer to append to
     * @param  formatter       the `DecimalFormatter` to use to convert
     * from a precise coordinate to an external coordinate
     */
    @Throws(IOException::class)
    private fun appendMultiPointTaggedText(
        multipoint: MultiPoint, outputOrdinates: EnumSet<Ordinate>,
        useFormatting: Boolean, level: Int, writer: Writer,
        formatter: OrdinateFormat
    ) {
        writer.write(WKTConstants.MULTIPOINT)
        writer.write(" ")
        appendOrdinateText(outputOrdinates, writer)
        appendMultiPointText(multipoint, outputOrdinates, useFormatting, level, writer, formatter)
    }

    /**
     * Converts a `MultiLineString` to &lt;MultiLineString Tagged
     * Text&gt; format, then appends it to the writer.
     *
     * @param  multiLineString  the `MultiLineString` to process
     * @param  useFormatting      flag indicating that the output should be formatted
     * @param  level              the indentation level
     * @param  writer             the output writer to append to
     * @param  formatter       the `DecimalFormatter` to use to convert
     * from a precise coordinate to an external coordinate
     */
    @Throws(IOException::class)
    private fun appendMultiLineStringTaggedText(
        multiLineString: MultiLineString, outputOrdinates: EnumSet<Ordinate>, useFormatting: Boolean,
        level: Int, writer: Writer, formatter: OrdinateFormat
    ) {
        writer.write(WKTConstants.MULTILINESTRING)
        writer.write(" ")
        appendOrdinateText(outputOrdinates, writer)
        appendMultiLineStringText(
            multiLineString, outputOrdinates, useFormatting,
            level,  /*false, */writer, formatter
        )
    }

    /**
     * Converts a `MultiPolygon` to &lt;MultiPolygon Tagged Text&gt;
     * format, then appends it to the writer.
     *
     * @param  multiPolygon  the `MultiPolygon` to process
     * @param  useFormatting      flag indicating that the output should be formatted
     * @param  level              the indentation level
     * @param  writer             the output writer to append to
     * @param  formatter       the `DecimalFormatter` to use to convert
     * from a precise coordinate to an external coordinate
     */
    @Throws(IOException::class)
    private fun appendMultiPolygonTaggedText(
        multiPolygon: MultiPolygon, outputOrdinates: EnumSet<Ordinate>, useFormatting: Boolean,
        level: Int, writer: Writer, formatter: OrdinateFormat
    ) {
        writer.write(WKTConstants.MULTIPOLYGON)
        writer.write(" ")
        appendOrdinateText(outputOrdinates, writer)
        appendMultiPolygonText(
            multiPolygon, outputOrdinates, useFormatting,
            level, writer, formatter
        )
    }

    /**
     * Converts a `GeometryCollection` to &lt;GeometryCollection
     * Tagged Text&gt; format, then appends it to the writer.
     *
     * @param  geometryCollection  the `GeometryCollection` to process
     * @param  useFormatting      flag indicating that the output should be formatted
     * @param  level              the indentation level
     * @param  writer             the output writer to append to
     * @param  formatter       the `DecimalFormatter` to use to convert
     * from a precise coordinate to an external coordinate
     */
    @Throws(IOException::class)
    private fun appendGeometryCollectionTaggedText(
        geometryCollection: GeometryCollection, outputOrdinates: EnumSet<Ordinate>, useFormatting: Boolean,
        level: Int, writer: Writer, formatter: OrdinateFormat
    ) {
        writer.write(WKTConstants.GEOMETRYCOLLECTION)
        writer.write(" ")
        appendOrdinateText(outputOrdinates, writer)
        appendGeometryCollectionText(
            geometryCollection, outputOrdinates,
            useFormatting, level, writer, formatter
        )
    }

    /**
     * Appends the i'th coordinate from the sequence to the writer
     *
     * If the `seq` has coordinates that are [double.NAN], these are not written, even though
     * [.outputDimension] suggests this.
     *
     * @param  seq        the `CoordinateSequence` to process
     * @param  i          the index of the coordinate to write
     * @param  writer     the output writer to append to
     * @param  formatter  the formatter to use for writing ordinate values
     */
    @Throws(IOException::class)
    private fun appendCoordinate(
        seq: CoordinateSequence?, outputOrdinates: EnumSet<Ordinate>, i: Int,
        writer: Writer, formatter: OrdinateFormat
    ) {
        writer.write(
            writeNumber(seq!!.getX(i), formatter) + " " +
                    writeNumber(seq.getY(i), formatter)
        )
        if (outputOrdinates.contains(Ordinate.Z)) {
            writer.write(" ")
            writer.write(writeNumber(seq.getZ(i), formatter))
        }
        if (outputOrdinates.contains(Ordinate.M)) {
            writer.write(" ")
            writer.write(writeNumber(seq.getM(i), formatter))
        }
    }

    /**
     * Appends additional ordinate information. This function may
     *
     *  * append 'Z' if in `outputOrdinates` the
     * [Ordinate.Z] value is included
     *
     *  * append 'M' if in `outputOrdinates` the
     * [Ordinate.M] value is included
     *
     *  *  append 'ZM' if in `outputOrdinates` the
     * [Ordinate.Z] and
     * [Ordinate.M] values are included
     *
     *
     *
     * @param outputOrdinates  a bit-pattern of ordinates to write.
     * @param writer         the output writer to append to.
     * @throws IOException   if an error occurs while using the writer.
     */
    @Throws(IOException::class)
    private fun appendOrdinateText(outputOrdinates: EnumSet<Ordinate>, writer: Writer) {
        if (outputOrdinates.contains(Ordinate.Z)) writer.write(WKTConstants.Z)
        if (outputOrdinates.contains(Ordinate.M)) writer.write(WKTConstants.M)
    }

    /**
     * Appends all members of a `CoordinateSequence` to the stream. Each `Coordinate` is separated from
     * another using a colon, the ordinates of a `Coordinate` are separated by a space.
     *
     * @param  seq             the `CoordinateSequence` to process
     * @param  useFormatting   flag indicating that
     * @param  level           the indentation level
     * @param  indentFirst     flag indicating that the first `Coordinate` of the sequence should be indented for
     * better visibility
     * @param  writer          the output writer to append to
     * @param  formatter       the formatter to use for writing ordinate values.
     */
    @Throws(IOException::class)
    private fun appendSequenceText(
        seq: CoordinateSequence?, outputOrdinates: EnumSet<Ordinate>, useFormatting: Boolean,
        level: Int, indentFirst: Boolean, writer: Writer, formatter: OrdinateFormat
    ) {
        if (seq!!.size() == 0) {
            writer.write(WKTConstants.EMPTY)
        } else {
            if (indentFirst) indent(useFormatting, level, writer)
            writer.write("(")
            for (i in 0 until seq.size()) {
                if (i > 0) {
                    writer.write(", ")
                    if (coordsPerLine > 0
                        && i % coordsPerLine == 0
                    ) {
                        indent(useFormatting, level + 1, writer)
                    }
                }
                appendCoordinate(seq, outputOrdinates, i, writer, formatter)
            }
            writer.write(")")
        }
    }

    /**
     * Converts a `Polygon` to &lt;Polygon Text&gt; format, then
     * appends it to the writer.
     *
     * @param  polygon         the `Polygon` to process
     * @param  useFormatting   flag indicating that
     * @param  level           the indentation level
     * @param  indentFirst     flag indicating that the first `Coordinate` of the sequence should be indented for
     * better visibility
     * @param  writer          the output writer to append to
     * @param  formatter       the formatter to use for writing ordinate values.
     */
    @Throws(IOException::class)
    private fun appendPolygonText(
        polygon: Polygon, outputOrdinates: EnumSet<Ordinate>, useFormatting: Boolean,
        level: Int, indentFirst: Boolean, writer: Writer, formatter: OrdinateFormat
    ) {
        if (polygon.isEmpty) {
            writer.write(WKTConstants.EMPTY)
        } else {
            if (indentFirst) indent(useFormatting, level, writer)
            writer.write("(")
            appendSequenceText(
                polygon.exteriorRing!!.coordinateSequence, outputOrdinates,
                useFormatting, level, false, writer, formatter
            )
            for (i in 0 until polygon.getNumInteriorRing()) {
                writer.write(", ")
                appendSequenceText(
                    polygon.getInteriorRingN(i)!!.coordinateSequence, outputOrdinates,
                    useFormatting, level + 1, true, writer, formatter
                )
            }
            writer.write(")")
        }
    }

    /**
     * Converts a `MultiPoint` to &lt;MultiPoint Text&gt; format, then
     * appends it to the writer.
     *
     * @param  multiPoint      the `MultiPoint` to process
     * @param  useFormatting   flag indicating that
     * @param  level           the indentation level
     * @param  writer          the output writer to append to
     * @param  formatter       the formatter to use for writing ordinate values.
     */
    @Throws(IOException::class)
    private fun appendMultiPointText(
        multiPoint: MultiPoint, outputOrdinates: EnumSet<Ordinate>, useFormatting: Boolean,
        level: Int, writer: Writer, formatter: OrdinateFormat
    ) {
        if (multiPoint.isEmpty) {
            writer.write(WKTConstants.EMPTY)
        } else {
            writer.write("(")
            for (i in 0 until multiPoint.numGeometries) {
                if (i > 0) {
                    writer.write(", ")
                    indentCoords(useFormatting, i, level + 1, writer)
                }
                appendSequenceText(
                    (multiPoint.getGeometryN(i) as Point).coordinateSequence,
                    outputOrdinates, useFormatting, level, false, writer, formatter
                )
            }
            writer.write(")")
        }
    }

    /**
     * Converts a `MultiLineString` to &lt;MultiLineString Text&gt;
     * format, then appends it to the writer.
     *
     * @param  multiLineString  the `MultiLineString` to process
     * @param  useFormatting    flag indicating that
     * @param  level            the indentation level
     * //@param  indentFirst      flag indicating that the first `Coordinate` of the sequence should be indented for
     * //                         better visibility
     * @param  writer           the output writer to append to
     * @param  formatter        the formatter to use for writing ordinate values.
     */
    @Throws(IOException::class)
    private fun appendMultiLineStringText(
        multiLineString: MultiLineString, outputOrdinates: EnumSet<Ordinate>,
        useFormatting: Boolean, level: Int,  /*boolean indentFirst, */
        writer: Writer, formatter: OrdinateFormat
    ) {
        if (multiLineString.isEmpty) {
            writer.write(WKTConstants.EMPTY)
        } else {
            var level2 = level
            var doIndent = false
            writer.write("(")
            for (i in 0 until multiLineString.numGeometries) {
                if (i > 0) {
                    writer.write(", ")
                    level2 = level + 1
                    doIndent = true
                }
                appendSequenceText(
                    (multiLineString.getGeometryN(i) as LineString).coordinateSequence,
                    outputOrdinates, useFormatting, level2, doIndent, writer, formatter
                )
            }
            writer.write(")")
        }
    }

    /**
     * Converts a `MultiPolygon` to &lt;MultiPolygon Text&gt; format,
     * then appends it to the writer.
     *
     * @param  multiPolygon  the `MultiPolygon` to process
     * @param  useFormatting   flag indicating that
     * @param  level           the indentation level
     * @param  writer          the output writer to append to
     * @param  formatter       the formatter to use for writing ordinate values.
     */
    @Throws(IOException::class)
    private fun appendMultiPolygonText(
        multiPolygon: MultiPolygon, outputOrdinates: EnumSet<Ordinate>, useFormatting: Boolean,
        level: Int, writer: Writer, formatter: OrdinateFormat
    ) {
        if (multiPolygon.isEmpty) {
            writer.write(WKTConstants.EMPTY)
        } else {
            var level2 = level
            var doIndent = false
            writer.write("(")
            for (i in 0 until multiPolygon.numGeometries) {
                if (i > 0) {
                    writer.write(", ")
                    level2 = level + 1
                    doIndent = true
                }
                appendPolygonText(
                    multiPolygon.getGeometryN(i) as Polygon, outputOrdinates,
                    useFormatting, level2, doIndent, writer, formatter
                )
            }
            writer.write(")")
        }
    }

    /**
     * Converts a `GeometryCollection` to &lt;GeometryCollectionText&gt;
     * format, then appends it to the writer.
     *
     * @param  geometryCollection  the `GeometryCollection` to process
     * @param  useFormatting   flag indicating that
     * @param  level           the indentation level
     * @param  writer          the output writer to append to
     * @param  formatter       the formatter to use for writing ordinate values.
     */
    @Throws(IOException::class)
    private fun appendGeometryCollectionText(
        geometryCollection: GeometryCollection, outputOrdinates: EnumSet<Ordinate>, useFormatting: Boolean,
        level: Int, writer: Writer, formatter: OrdinateFormat
    ) {
        if (geometryCollection.isEmpty) {
            writer.write(WKTConstants.EMPTY)
        } else {
            var level2 = level
            writer.write("(")
            for (i in 0 until geometryCollection.numGeometries) {
                if (i > 0) {
                    writer.write(", ")
                    level2 = level + 1
                }
                appendGeometryTaggedText(
                    geometryCollection.getGeometryN(i), outputOrdinates,
                    useFormatting, level2, writer, formatter
                )
            }
            writer.write(")")
        }
    }

    @Throws(IOException::class)
    private fun indentCoords(useFormatting: Boolean, coordIndex: Int, level: Int, writer: Writer) {
        if (coordsPerLine <= 0
            || coordIndex % coordsPerLine != 0
        ) return
        indent(useFormatting, level, writer)
    }

    @Throws(IOException::class)
    private fun indent(useFormatting: Boolean, level: Int, writer: Writer) {
        if (!useFormatting || level <= 0) return
        writer.write("\n")
        for (i in 0 until level) {
            if(indentTabStr != null) {
                writer.write(indentTabStr!!)
            }
        }
    }

    companion object {
        /**
         * Generates the WKT for a <tt>POINT</tt>
         * specified by a [Coordinate].
         *
         * @param p0 the point coordinate
         *
         * @return the WKT
         */
        @JvmStatic
        fun toPoint(p0: Coordinate): String {
            return WKTConstants.POINT + " ( " + format(p0) + " )"
        }

        /**
         * Generates the WKT for a <tt>LINESTRING</tt>
         * specified by a [CoordinateSequence].
         *
         * @param seq the sequence to write
         *
         * @return the WKT string
         */
        @JvmStatic
        fun toLineString(seq: CoordinateSequence): String {
            val buf = StringBuilder()
            buf.append(WKTConstants.LINESTRING)
            buf.append(" ")
            if (seq.size() == 0) buf.append(WKTConstants.EMPTY) else {
                buf.append("(")
                for (i in 0 until seq.size()) {
                    if (i > 0) buf.append(", ")
                    buf.append(format(seq.getX(i), seq.getY(i)))
                }
                buf.append(")")
            }
            return buf.toString()
        }

        /**
         * Generates the WKT for a <tt>LINESTRING</tt>
         * specified by a [CoordinateSequence].
         *
         * @param coord the sequence to write
         *
         * @return the WKT string
         */
        @JvmStatic
        fun toLineString(coord: Array<Coordinate>): String {
            val buf = StringBuilder()
            buf.append(WKTConstants.LINESTRING)
            buf.append(" ")
            if (coord.isEmpty()) buf.append(WKTConstants.EMPTY) else {
                buf.append("(")
                for (i in coord.indices) {
                    if (i > 0) buf.append(", ")
                    buf.append(format(coord[i]))
                }
                buf.append(")")
            }
            return buf.toString()
        }

        /**
         * Generates the WKT for a <tt>LINESTRING</tt>
         * specified by two [Coordinate]s.
         *
         * @param p0 the first coordinate
         * @param p1 the second coordinate
         *
         * @return the WKT
         */
        @JvmStatic
        fun toLineString(p0: Coordinate, p1: Coordinate): String {
            return WKTConstants.LINESTRING + " ( " + format(p0) + ", " + format(p1) + " )"
        }

        fun format(p: Coordinate): String {
            return format(p.x, p.y)
        }

        private fun format(x: Double, y: Double): String {
            return OrdinateFormat.DEFAULT.format(x) + " " + OrdinateFormat.DEFAULT.format(y)
        }

        private const val INDENT = 2
        private const val OUTPUT_DIMENSION = 2

        /**
         * Creates the `DecimalFormat` used to write `double`s
         * with a sufficient number of decimal places.
         *
         * @param  precisionModel  the `PrecisionModel` used to determine
         * the number of decimal places to write.
         * @return                 a `DecimalFormat` that write `double`
         * s without scientific notation.
         */
        private fun createFormatter(precisionModel: PrecisionModel): OrdinateFormat {
            return create(precisionModel.maximumSignificantDigits)
        }

        /**
         * Returns a `String` of repeated characters.
         *
         * @param  ch     the character to repeat
         * @param  count  the number of times to repeat the character
         * @return        a `String` of characters
         */
        private fun stringOfChar(ch: Char, count: Int): String {
            val buf = StringBuilder(count)
            for (i in 0 until count) {
                buf.append(ch)
            }
            return buf.toString()
        }

        /**
         * Converts a `double` to a `String`, not in scientific
         * notation.
         *
         * @param  d  the `double` to convert
         * @return    the `double` as a `String`, not in
         * scientific notation
         */
        private fun writeNumber(d: Double, formatter: OrdinateFormat): String {
            return formatter.format(d)
        }
    }
    /**
     * Creates a writer that writes [Geometry]s with
     * the given output dimension (2 to 4).
     * The output follows the following rules:
     *
     *  * If the specified **output dimension is 3** and the **z is measure flag
     * is set to true**, the Z value of coordinates will be written if it is present
     * (i.e. if it is not `Double.NaN`)
     *  * If the specified **output dimension is 3** and the **z is measure flag
     * is set to false**, the Measure value of coordinates will be written if it is present
     * (i.e. if it is not `Double.NaN`)
     *  * If the specified **output dimension is 4**, the Z value of coordinates will
     * be written even if it is not present when the Measure value is present.The Measrue
     * value of coordinates will be written if it is present
     * (i.e. if it is not `Double.NaN`)
     *
     *
     * @param outputDimension the coordinate dimension to output (2 to 4)
     */
    /**
     * Creates a new WKTWriter with default settings
     */
    init {
        setTab(INDENT)
        this.outputDimension = outputDimension
        require(!(outputDimension < 2 || outputDimension > 4)) { "Invalid output dimension (must be 2 to 4)" }
        outputOrdinates = enumSetOf(Ordinate.X, Ordinate.Y)
        if (outputDimension > 2) outputOrdinates.add(Ordinate.Z)
        if (outputDimension > 3) outputOrdinates.add(Ordinate.M)
    }
}