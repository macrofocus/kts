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
import org.locationtech.jts.geom.CoordinateSequences.ensureValidRing
import org.locationtech.jts.geom.CoordinateSequences.extend
import org.locationtech.jts.geom.CoordinateSequences.isRing
import org.locationtech.jts.legacy.Math.isNaN
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Reads a [Geometry]from a byte stream in Well-Known Binary format.
 * Supports use of an [InStream], which allows easy use
 * with arbitrary byte stream sources.
 *
 * This class reads the format describe in [WKBWriter].
 * It partially handles
 * the **Extended WKB** format used by PostGIS,
 * by parsing and storing optional SRID values.
 * If a SRID is not specified in an element geometry, it is inherited
 * from the parent's SRID.
 * The default SRID value is 0.
 *
 * Although not defined in the WKB specification, empty points
 * are handled if they are represented as a Point with `NaN` X and Y ordinates.
 *
 * The reader repairs structurally-invalid input
 * (specifically, LineStrings and LinearRings which contain
 * too few points have vertices added,
 * and non-closed rings are closed).
 *
 * The reader handles most errors caused by malformed or malicious WKB data.
 * It checks for obviously excessive values of the fields
 * `numElems`, `numRings`, and `numCoords`.
 * It also checks that the reader does not read beyond the end of the data supplied.
 * A [ParseException] is thrown if this situation is detected.
 *
 * This class is designed to support reuse of a single instance to read multiple
 * geometries. This class is not thread-safe; each thread should create its own
 * instance.
 *
 * As of version 1.15, the reader can read geometries following the OGC 06-103r4
 * Simple Features Access 1.2.1 specification,
 * which aligns with the ISO 19125 standard.
 * This format is used by Spatialite and Geopackage.
 *
 * The difference between PostGIS EWKB format and the new ISO/OGC specification is
 * that Z and M coordinates are detected with a bit mask on the higher byte in
 * the former case (0x80 for Z and 0x40 for M) while new OGC specification use
 * specific int ranges for 2D geometries, Z geometries (2D code+1000), M geometries
 * (2D code+2000) and ZM geometries (2D code+3000).
 *
 * Note that the [WKBWriter] is not changed and still writes the PostGIS EWKB
 * geometry format.
 *
 * @see WKBWriter for a formal format specification
 */
class WKBReader @JvmOverloads constructor(private val factory: GeometryFactory = GeometryFactory()) {
    private val csFactory: CoordinateSequenceFactory
    private val precisionModel: PrecisionModel = factory.precisionModel

    // default dimension - will be set on read
    private var inputDimension = 2

    /**
     * true if structurally invalid input should be reported rather than repaired.
     * At some point this could be made client-controllable.
     */
    private val isStrict = false
    private val dis: ByteOrderDataInStream = ByteOrderDataInStream()
    private var ordValues: DoubleArray? = null
    private var maxNumFieldValue = 0

    init {
        csFactory = factory.coordinateSequenceFactory
    }

    /**
     * Reads a single [Geometry] in WKB format from a byte array.
     *
     * @param bytes the byte array to read from
     * @return the geometry read
     * @throws ParseException if the WKB is ill-formed
     */
    @Throws(ParseException::class)
    fun read(bytes: ByteArray): Geometry? {
        // possibly reuse the ByteArrayInStream?
        // don't throw IOExceptions, since we are not doing any I/O
        return try {
            read(ByteArrayInStream(bytes), bytes.size / 16)
        } catch (ex: IOException) {
            throw RuntimeException("Unexpected IOException caught: " + ex.message)
        }
    }

    /**
     * Reads a [Geometry] in binary WKB format from an [InStream].
     *
     * @param is the stream to read from
     * @return the Geometry read
     * @throws IOException if the underlying stream creates an error
     * @throws ParseException if the WKB is ill-formed
     */
    @Throws(IOException::class, ParseException::class)
    fun read(`is`: InStream): Geometry {
        // can't tell size of InStream, but MAX_VALUE should be safe
        return read(`is`, Int.MAX_VALUE)
    }

    @Throws(IOException::class, ParseException::class)
    private fun read(`is`: InStream, maxCoordNum: Int): Geometry {
        /**
         * This puts an upper bound on the allowed value
         * in coordNum fields.
         * It avoids OOM exceptions due to malformed input.
         */
        maxNumFieldValue = maxCoordNum
        dis.setInStream(`is`)
        return readGeometry(0)
    }

    @Throws(IOException::class, ParseException::class)
    private fun readNumField(fieldName: String?): Int {
        // num field is unsigned int, but Java has only signed int
        val num: Int = dis.readInt()
        if (num < 0 || num > maxNumFieldValue) {
            throw ParseException("$fieldName value is too large")
        }
        return num
    }

    @Throws(IOException::class, ParseException::class)
    private fun readGeometry(SRID: Int): Geometry {

        // determine byte order
        var SRID = SRID
        val byteOrderWKB: Byte = dis.readByte()

        // always set byte order, since it may change from geometry to geometry
        if (byteOrderWKB == WKBConstants.wkbNDR) {
            dis.setOrder(ByteOrderValues.LITTLE_ENDIAN)
        } else if (byteOrderWKB == WKBConstants.wkbXDR) {
            dis.setOrder(ByteOrderValues.BIG_ENDIAN)
        } else if (isStrict) {
            throw ParseException("Unknown geometry byte order (not NDR or XDR): $byteOrderWKB")
        }
        //if not strict and not XDR or NDR, then we just use the dis default set at the
        //start of the geometry (if a multi-geometry).  This  allows WBKReader to work
        //with Spatialite native BLOB WKB, as well as other WKB variants that might just
        //specify endian-ness at the start of the multigeometry.
        val typeInt: Int = dis.readInt()

        /**
         * To get geometry type mask out EWKB flag bits,
         * and use only low 3 digits of type word.
         * This supports both EWKB and ISO/OGC.
         */
        val geometryType = (typeInt and 0xffff) % 1000

        // handle 3D and 4D WKB geometries
        // geometries with Z coordinates have the 0x80 flag (postgis EWKB)
        // or are in the 1000 range (Z) or in the 3000 range (ZM) of geometry type (ISO/OGC 06-103r4)
        val hasZ = typeInt and -0x80000000 != 0 || (typeInt and 0xffff) / 1000 == 1 || (typeInt and 0xffff) / 1000 == 3
        // geometries with M coordinates have the 0x40 flag (postgis EWKB)
        // or are in the 1000 range (M) or in the 3000 range (ZM) of geometry type (ISO/OGC 06-103r4)
        val hasM = typeInt and 0x40000000 != 0 || (typeInt and 0xffff) / 1000 == 2 || (typeInt and 0xffff) / 1000 == 3
        //System.out.println(typeInt + " - " + geometryType + " - hasZ:" + hasZ);
        inputDimension = 2 + (if (hasZ) 1 else 0) + if (hasM) 1 else 0

        // determine if SRIDs are present (EWKB only)
        val hasSRID = typeInt and 0x20000000 != 0
        if (hasSRID) {
            SRID = dis.readInt()
        }

        // only allocate ordValues buffer if necessary
        if (ordValues == null || ordValues!!.size < inputDimension) ordValues = DoubleArray(inputDimension)
        var geom: Geometry? = null
        geom = when (geometryType) {
            WKBConstants.wkbPoint -> readPoint()
            WKBConstants.wkbLineString -> readLineString()
            WKBConstants.wkbPolygon -> readPolygon()
            WKBConstants.wkbMultiPoint -> readMultiPoint(SRID)
            WKBConstants.wkbMultiLineString -> readMultiLineString(SRID)
            WKBConstants.wkbMultiPolygon -> readMultiPolygon(SRID)
            WKBConstants.wkbGeometryCollection -> readGeometryCollection(SRID)
            else -> throw ParseException("Unknown WKB type $geometryType")
        }
        setSRID(geom, SRID)
        return geom
    }

    /**
     * Sets the SRID, if it was specified in the WKB
     *
     * @param g the geometry to update
     * @return the geometry with an updated SRID value, if required
     */
    private fun setSRID(g: Geometry?, SRID: Int): Geometry? {
        if (SRID != 0) g!!.SRID = SRID
        return g
    }

    @Throws(IOException::class, ParseException::class)
    private fun readPoint(): Point {
        val pts = readCoordinateSequence(1)
        // If X and Y are NaN create a empty point
        return if (isNaN(pts.getX(0)) || isNaN(pts.getY(0))) {
            factory.createPoint()
        } else factory.createPoint(
            pts
        )
    }

    @Throws(IOException::class, ParseException::class)
    private fun readLineString(): LineString {
        val size = readNumField(FIELD_NUMCOORDS)
        val pts = readCoordinateSequenceLineString(size)
        return factory.createLineString(pts)
    }

    @Throws(IOException::class, ParseException::class)
    private fun readLinearRing(): LinearRing {
        val size = readNumField(FIELD_NUMCOORDS)
        val pts = readCoordinateSequenceRing(size)
        return factory.createLinearRing(pts)
    }

    @Throws(IOException::class, ParseException::class)
    private fun readPolygon(): Polygon {
        val numRings = readNumField(FIELD_NUMRINGS)
        var holes: Array<LinearRing?>? = null
        if (numRings > 1) holes = arrayOfNulls(numRings - 1)

        // empty polygon
        if (numRings <= 0) return factory.createPolygon()
        val shell = readLinearRing()
        for (i in 0 until numRings - 1) {
            holes!![i] = readLinearRing()
        }
        return factory.createPolygon(shell, holes?.requireNoNulls())
    }

    @Throws(IOException::class, ParseException::class)
    private fun readMultiPoint(SRID: Int): MultiPoint {
        val numGeom = readNumField(FIELD_NUMELEMS)
        val geoms = arrayOfNulls<Point>(numGeom)
        for (i in 0 until numGeom) {
            val g = readGeometry(SRID) as? Point
                ?: throw ParseException(INVALID_GEOM_TYPE_MSG + "MultiPoint")
            geoms[i] = g
        }
        return factory.createMultiPoint(geoms.requireNoNulls())
    }

    @Throws(IOException::class, ParseException::class)
    private fun readMultiLineString(SRID: Int): MultiLineString {
        val numGeom = readNumField(FIELD_NUMELEMS)
        val geoms = arrayOfNulls<LineString>(numGeom)
        for (i in 0 until numGeom) {
            val g = readGeometry(SRID) as? LineString
                ?: throw ParseException(INVALID_GEOM_TYPE_MSG + "MultiLineString")
            geoms[i] = g
        }
        return factory.createMultiLineString(geoms.requireNoNulls())
    }

    @Throws(IOException::class, ParseException::class)
    private fun readMultiPolygon(SRID: Int): MultiPolygon {
        val numGeom = readNumField(FIELD_NUMELEMS)
        val geoms = arrayOfNulls<Polygon>(numGeom)
        for (i in 0 until numGeom) {
            val g = readGeometry(SRID) as? Polygon
                ?: throw ParseException(INVALID_GEOM_TYPE_MSG + "MultiPolygon")
            geoms[i] = g
        }
        return factory.createMultiPolygon(geoms.requireNoNulls())
    }

    @Throws(IOException::class, ParseException::class)
    private fun readGeometryCollection(SRID: Int): GeometryCollection {
        val numGeom = readNumField(FIELD_NUMELEMS)
        val geoms = arrayOfNulls<Geometry>(numGeom)
        for (i in 0 until numGeom) {
            geoms[i] = readGeometry(SRID)
        }
        return factory.createGeometryCollection(geoms.requireNoNulls())
    }

    @Throws(IOException::class, ParseException::class)
    private fun readCoordinateSequence(size: Int): CoordinateSequence {
        val seq = csFactory.create(size, inputDimension)
        var targetDim = seq.dimension
        if (targetDim > inputDimension) targetDim = inputDimension
        for (i in 0 until size) {
            readCoordinate()
            for (j in 0 until targetDim) {
                seq.setOrdinate(i, j, ordValues!![j])
            }
        }
        return seq
    }

    @Throws(IOException::class, ParseException::class)
    private fun readCoordinateSequenceLineString(size: Int): CoordinateSequence {
        val seq = readCoordinateSequence(size)
        if (isStrict) return seq
        return if (seq.size() == 0 || seq.size() >= 2) seq else extend(
            csFactory,
            seq,
            2
        )
    }

    @Throws(IOException::class, ParseException::class)
    private fun readCoordinateSequenceRing(size: Int): CoordinateSequence {
        val seq = readCoordinateSequence(size)
        if (isStrict) return seq
        return if (isRing(seq)) seq else ensureValidRing(csFactory, seq)
    }

    /**
     * Reads a coordinate value with the specified dimensionality.
     * Makes the X and Y ordinates precise according to the precision model
     * in use.
     * @throws ParseException
     */
    @Throws(IOException::class, ParseException::class)
    private fun readCoordinate() {
        for (i in 0 until inputDimension) {
            if (i <= 1) {
                ordValues!![i] = precisionModel.makePrecise(dis.readDouble())
            } else {
                ordValues!![i] = dis.readDouble()
            }
        }
    }

    companion object {
        /**
         * Converts a hexadecimal string to a byte array.
         * The hexadecimal digit symbols are case-insensitive.
         *
         * @param hex a string containing hex digits
         * @return an array of bytes with the value of the hex string
         */
        @JvmStatic
        fun hexToBytes(hex: String): ByteArray {
            val byteLen = hex.length / 2
            val bytes = ByteArray(byteLen)
            for (i in 0 until hex.length / 2) {
                val i2 = 2 * i
                if (i2 + 1 > hex.length) throw IllegalArgumentException("Hex string has odd length")
                val nib1 = hexToInt(hex[i2])
                val nib0 = hexToInt(hex[i2 + 1])
                val b = ((nib1 shl 4) + nib0.toByte()).toByte()
                bytes[i] = b
            }
            return bytes
        }

        private fun hexToInt(hex: Char): Int {
            val nib = hex.digitToIntOrNull(16) ?: -1
            if (nib < 0) throw IllegalArgumentException("Invalid hex digit: '$hex'")
            return nib
        }

        private const val INVALID_GEOM_TYPE_MSG = "Invalid geometry type encountered in "
        private const val FIELD_NUMCOORDS = "numCoords"
        private val FIELD_NUMRINGS = null
        private val FIELD_NUMELEMS = null
    }
}