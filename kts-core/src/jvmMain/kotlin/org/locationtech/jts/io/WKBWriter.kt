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
import org.locationtech.jts.util.Assert

/**
 * Writes a [Geometry] into Well-Known Binary format.
 * Supports use of an [OutStream], which allows easy use
 * with arbitrary byte stream sinks.
 *
 * The WKB format is specified in the
 * OGC <A HREF="http://portal.opengeospatial.org/files/?artifact_id=829">*Simple Features for SQL
 * specification*</A> (section 3.3.2.6).
 *
 * There are a few cases which are not specified in the standard.
 * The implementation uses a representation which is compatible with
 * other common spatial systems (notably, PostGIS).
 *
 *  * [LinearRing]s are written as [LineString]s
 *  * Empty geometries are output as follows:
 *
 *  * **Point**: a `WKBPoint` with `NaN` ordinate values
 *  * **LineString**: a `WKBLineString` with zero points
 *  * **Polygon**: a `WKBPolygon` with zero rings
 *  * **Multigeometries**: a `WKBMulti` geometry of appropriate type with zero elements
 *  * **GeometryCollections**: a `WKBGeometryCollection` with zero elements
 *
 *
 * This implementation supports the **Extended WKB** standard.
 * Extended WKB allows writing 3-dimensional coordinates
 * and the geometry SRID value.
 * The presence of 3D coordinates is indicated
 * by setting the high bit of the <tt>wkbType</tt> word.
 * The presence of a SRID is indicated
 * by setting the third bit of the <tt>wkbType</tt> word.
 * EWKB format is upward-compatible with the original SFS WKB format.
 *
 * SRID output is optimized, if specified.
 * Only the top-level geometry has the SRID included.
 * This assumes that all geometries in a collection have the same SRID as
 * the collection (which is the JTS convention).
 *
 * This class supports reuse of a single instance to read multiple
 * geometries. This class is not thread-safe; each thread should create its own
 * instance.
 *
 * <h3>Syntax</h3>
 * The following syntax specification describes the version of Well-Known Binary
 * supported by JTS.
 *
 * *The specification uses a syntax language similar to that used in
 * the C language.  Bitfields are specified from high-order to low-order bits.*
 *
 * <blockquote><pre>
 *
 * **byte** = 1 byte
 * **uint32** = 32 bit unsigned integer (4 bytes)
 * **double** = double precision number (8 bytes)
 *
 * abstract Point { }
 *
 * Point2D extends Point {
 * **double** x;
 * **double** y;
 * }
 *
 * Point3D extends Point {
 * **double** x;
 * **double** y;
 * **double** z;
 * }
 *
 * LinearRing {
 * **uint32** numPoints;
 * Point points[numPoints];
 * }
 *
 * enum wkbGeometryType {
 * wkbPoint = 1,
 * wkbLineString = 2,
 * wkbPolygon = 3,
 * wkbMultiPoint = 4,
 * wkbMultiLineString = 5,
 * wkbMultiPolygon = 6,
 * wkbGeometryCollection = 7
 * }
 *
 * enum byteOrder {
 * wkbXDR = 0,	// Big Endian
 * wkbNDR = 1 	// Little Endian
 * }
 *
 * WKBType {
 * **uint32** wkbGeometryType : 8; // values from enum wkbGeometryType
 * }
 *
 * EWKBType {
 * **uint32** is3D : 1; 	// 0 = 2D, 1 = 3D
 * **uint32** noData1 : 1;
 * **uint32** hasSRID : 1;  	// 0, no, 1 = yes
 * **uint32** noData2 : 21;
 * **uint32** wkbGeometryType : 8; // values from enum wkbGeometryType
 * }
 *
 * abstract WKBGeometry {
 * **byte** byteOrder;		// values from enum byteOrder
 * EWKBType wkbType
 * [ **uint32** srid; ] 	// only if hasSRID = yes
 * }
 *
 * WKBPoint extends WKBGeometry {
 * Point point;
 * }
 *
 * WKBLineString extends WKBGeometry {
 * **uint32** numCoords;
 * Point points[numCoords];
 * }
 *
 * WKBPolygon extends WKBGeometry {
 * **uint32** numRings;
 * LinearRing rings[numRings];
 * }
 *
 * WKBMultiPoint extends WKBGeometry {
 * **uint32** numElems;
 * WKBPoint elems[numElems];
 * }
 *
 * WKBMultiLineString extends WKBGeometry {
 * **uint32** numElems;
 * WKBLineString elems[numElems];
 * }
 *
 * wkbMultiPolygon extends WKBGeometry {
 * **uint32** numElems;
 * WKBPolygon elems[numElems];
 * }
 *
 * WKBGeometryCollection extends WKBGeometry {
 * **uint32** numElems;
 * WKBGeometry elems[numElems];
 * }
 *
</pre></blockquote> *
 * @see WKBReader
 */
class WKBWriter @JvmOverloads constructor(
    val outputDimension: Int = 2,
    byteOrder: Int = ByteOrderValues.BIG_ENDIAN,
    includeSRID: Boolean = false
) {
    private val byteOrder: Int
    private var includeSRID = false
    private val byteArrayOS: java.io.ByteArrayOutputStream = java.io.ByteArrayOutputStream()
    private val byteArrayOutStream: OutStream = OutputStreamOutStream(byteArrayOS)

    // holds output data values
    private val buf = ByteArray(8)

    /**
     * Creates a writer that writes [Geometry]s with
     * the given dimension (2 or 3) for output coordinates
     * and [ByteOrderValues.BIG_ENDIAN] byte order. This constructor also
     * takes a flag to control whether srid information will be
     * written.
     * If the input geometry has a smaller coordinate dimension,
     * coordinates will be padded with [Coordinate.NULL_ORDINATE].
     *
     * @param outputDimension the coordinate dimension to output (2 or 3)
     * @param includeSRID indicates whether SRID should be written
     */
    constructor(outputDimension: Int, includeSRID: Boolean) : this(
        outputDimension,
        ByteOrderValues.BIG_ENDIAN,
        includeSRID
    ) {
    }
    /**
     * Creates a writer that writes [Geometry]s with
     * the given dimension (2 or 3) for output coordinates
     * and byte order. This constructor also takes a flag to
     * control whether srid information will be written.
     * If the input geometry has a small coordinate dimension,
     * coordinates will be padded with [Coordinate.NULL_ORDINATE].
     *
     * @param outputDimension the coordinate dimension to output (2 or 3)
     * @param byteOrder the byte ordering to use
     * @param includeSRID indicates whether SRID should be written
     */
    /**
     * Creates a writer that writes [Geometry]s with
     * the given dimension (2 or 3) for output coordinates
     * and byte order
     * If the input geometry has a small coordinate dimension,
     * coordinates will be padded with [Coordinate.NULL_ORDINATE].
     *
     * @param outputDimension the coordinate dimension to output (2 or 3)
     * @param byteOrder the byte ordering to use
     */
    /**
     * Creates a writer that writes [Geometry]s with
     * output dimension = 2 and BIG_ENDIAN byte order
     */
    /**
     * Creates a writer that writes [Geometry]s with
     * the given dimension (2 or 3) for output coordinates
     * and [ByteOrderValues.BIG_ENDIAN] byte order.
     * If the input geometry has a small coordinate dimension,
     * coordinates will be padded with [Coordinate.NULL_ORDINATE].
     *
     * @param outputDimension the coordinate dimension to output (2 or 3)
     */
    init {
        this.byteOrder = byteOrder
        this.includeSRID = includeSRID
        if (outputDimension < 2 || outputDimension > 3) throw IllegalArgumentException("Output dimension must be 2 or 3")
    }

    /**
     * Writes a [Geometry] into a byte array.
     *
     * @param geom the geometry to write
     * @return the byte array containing the WKB
     */
    fun write(geom: Geometry?): ByteArray {
        try {
            byteArrayOS.reset()
            write(geom, byteArrayOutStream)
        } catch (ex: java.io.IOException) {
            throw RuntimeException("Unexpected IO exception: " + ex.message)
        }
        return byteArrayOS.toByteArray()
    }

    /**
     * Writes a [Geometry] to an [OutStream].
     *
     * @param geom the geometry to write
     * @param os the out stream to write to
     * @throws IOException if an I/O error occurs
     */
    @Throws(java.io.IOException::class)
    fun write(geom: Geometry?, os: OutStream) {
        if (geom is Point) writePoint(geom, os) else if (geom is LineString) writeLineString(
            geom, os
        ) else if (geom is Polygon) writePolygon(geom, os) else if (geom is MultiPoint) writeGeometryCollection(
            WKBConstants.wkbMultiPoint,
            geom, os
        ) else if (geom is MultiLineString) writeGeometryCollection(
            WKBConstants.wkbMultiLineString,
            geom, os
        ) else if (geom is MultiPolygon) writeGeometryCollection(
            WKBConstants.wkbMultiPolygon,
            geom, os
        ) else if (geom is GeometryCollection) writeGeometryCollection(
            WKBConstants.wkbGeometryCollection,
            geom, os
        ) else {
            Assert.shouldNeverReachHere("Unknown Geometry type")
        }
    }

    @Throws(java.io.IOException::class)
    private fun writePoint(pt: Point, os: OutStream) {
        writeByteOrder(os)
        writeGeometryType(WKBConstants.wkbPoint, pt, os)
        if (pt.coordinateSequence!!.size() == 0) {
            // write empty point as NaNs (extension to OGC standard)
            writeNaNs(outputDimension, os)
        } else {
            writeCoordinateSequence(pt.coordinateSequence, false, os)
        }
    }

    @Throws(java.io.IOException::class)
    private fun writeLineString(line: LineString, os: OutStream) {
        writeByteOrder(os)
        writeGeometryType(WKBConstants.wkbLineString, line, os)
        writeCoordinateSequence(line.coordinateSequence, true, os)
    }

    @Throws(java.io.IOException::class)
    private fun writePolygon(poly: Polygon, os: OutStream) {
        writeByteOrder(os)
        writeGeometryType(WKBConstants.wkbPolygon, poly, os)
        //--- write empty polygons with no rings (OCG extension)
        if (poly.isEmpty) {
            writeInt(0, os)
            return
        }
        writeInt(poly.getNumInteriorRing() + 1, os)
        writeCoordinateSequence(poly.exteriorRing!!.coordinateSequence, true, os)
        for (i in 0 until poly.getNumInteriorRing()) {
            writeCoordinateSequence(
                poly.getInteriorRingN(i).coordinateSequence, true,
                os
            )
        }
    }

    @Throws(java.io.IOException::class)
    private fun writeGeometryCollection(
        geometryType: Int, gc: GeometryCollection,
        os: OutStream
    ) {
        writeByteOrder(os)
        writeGeometryType(geometryType, gc, os)
        writeInt(gc.numGeometries, os)
        val originalIncludeSRID = includeSRID
        includeSRID = false
        for (i in 0 until gc.numGeometries) {
            write(gc.getGeometryN(i), os)
        }
        includeSRID = originalIncludeSRID
    }

    @Throws(java.io.IOException::class)
    private fun writeByteOrder(os: OutStream) {
        if (byteOrder == ByteOrderValues.LITTLE_ENDIAN) buf[0] = WKBConstants.wkbNDR else buf[0] = WKBConstants.wkbXDR
        os.write(buf, 1)
    }

    @Throws(java.io.IOException::class)
    private fun writeGeometryType(geometryType: Int, g: Geometry, os: OutStream) {
        val flag3D = if (outputDimension == 3) -0x80000000 else 0
        var typeInt = geometryType or flag3D
        typeInt = typeInt or if (includeSRID) 0x20000000 else 0
        writeInt(typeInt, os)
        if (includeSRID) {
            writeInt(g.SRID, os)
        }
    }

    @Throws(java.io.IOException::class)
    private fun writeInt(intValue: Int, os: OutStream) {
        ByteOrderValues.putInt(intValue, buf, byteOrder)
        os.write(buf, 4)
    }

    @Throws(java.io.IOException::class)
    private fun writeCoordinateSequence(seq: CoordinateSequence?, writeSize: Boolean, os: OutStream) {
        if (writeSize) writeInt(seq!!.size(), os)
        for (i in 0 until seq!!.size()) {
            writeCoordinate(seq, i, os)
        }
    }

    @Throws(java.io.IOException::class)
    private fun writeCoordinate(seq: CoordinateSequence?, index: Int, os: OutStream) {
        ByteOrderValues.putDouble(seq!!.getX(index), buf, byteOrder)
        os.write(buf, 8)
        ByteOrderValues.putDouble(seq.getY(index), buf, byteOrder)
        os.write(buf, 8)

        // only write 3rd dim if caller has requested it for this writer
        if (outputDimension >= 3) {
            // if 3rd dim is requested, only write it if the CoordinateSequence provides it
            var ordVal = Coordinate.NULL_ORDINATE
            if (seq.dimension >= 3) {
                ordVal = seq.getOrdinate(index, 2)
            }
            ByteOrderValues.putDouble(ordVal, buf, byteOrder)
            os.write(buf, 8)
        }
    }

    @Throws(java.io.IOException::class)
    private fun writeNaNs(numNaNs: Int, os: OutStream) {
        for (i in 0 until numNaNs) {
            ByteOrderValues.putDouble(Double.NaN, buf, byteOrder)
            os.write(buf, 8)
        }
    }

    companion object {
        /**
         * Converts a byte array to a hexadecimal string.
         *
         * @param bytes
         * @return a string of hexadecimal digits
         *
         */
        @Deprecated("")
        fun bytesToHex(bytes: ByteArray): String {
            return toHex(bytes)
        }

        /**
         * Converts a byte array to a hexadecimal string.
         *
         * @param bytes a byte array
         * @return a string of hexadecimal digits
         */
        @JvmStatic
        fun toHex(bytes: ByteArray): String {
            val buf: StringBuilder = StringBuilder()
            for (i in bytes.indices) {
                val b = bytes[i]
                buf.append(toHexDigit(b.toInt() shr 4 and 0x0F))
                buf.append(toHexDigit(b.toInt() and 0x0F))
            }
            return buf.toString()
        }

        private fun toHexDigit(n: Int): Char {
            if (n < 0 || n > 15) throw IllegalArgumentException("Nibble value out of range: $n")
            return if (n <= 9) ('0'.code + n).toChar() else ('A'.code + (n - 10)).toChar()
        }
    }
}