/*
 * Copyright (c) 2016 Vivid Solutions.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package test.kts

import org.locationtech.kts.assertEquals
import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.GeometryFactory.Companion.toGeometryArray
import org.locationtech.jts.geom.impl.CoordinateArraySequenceFactory.Companion.instance
import org.locationtech.jts.geom.impl.PackedCoordinateSequenceFactory
import org.locationtech.jts.io.Ordinate
import org.locationtech.jts.io.ParseException
import org.locationtech.jts.io.WKTReader
import org.locationtech.jts.legacy.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A base class for Geometry tests which provides various utility methods.
 *
 * @author mbdavis
 */
abstract class GeometryTestCase protected constructor(
    coordinateSequenceFactory: CoordinateSequenceFactory? = instance()
) {
    val geomFactory: GeometryFactory
    val readerWKT: WKTReader
    protected fun checkEqual(expected: Geometry, actual: Geometry) {
        val actualNorm = actual.norm()
        val expectedNorm = expected.norm()
        val equal = actualNorm.equalsExact(expectedNorm)
        if (!equal) {
            println(
                "FAIL - Expected = " + expectedNorm
                        + " actual = " + actualNorm
            )
        }
        assertTrue(equal)
    }

    protected fun checkEqual(expected: Geometry, actual: Geometry, tolerance: Double) {
        val actualNorm = actual.norm()
        val expectedNorm = expected.norm()
        val equal = actualNorm.equalsExact(expectedNorm, tolerance)
        if (!equal) {
            println(
                "FAIL - Expected = " + expectedNorm
                        + " actual = " + actualNorm
            )
        }
        assertTrue(equal)
    }

    protected fun checkEqual(expected: Collection<Geometry>, actual: Collection<Geometry>) {
        checkEqual(toGeometryCollection(expected), toGeometryCollection(actual))
    }

    fun toGeometryCollection(geoms: Collection<Geometry>): GeometryCollection {
        return geomFactory.createGeometryCollection(toGeometryArray(geoms))
    }

    protected fun checkEqualXY(expected: Coordinate, actual: Coordinate) {
        assertEquals(expected.x, actual.x, "Coordinate X")
        assertEquals(expected.y, actual.y, "Coordinate Y")
    }

    protected fun checkEqualXY(message: String, expected: Coordinate, actual: Coordinate) {
        assertEquals(expected.x, actual.x, "$message X")
        assertEquals(expected.y, actual.y, "$message Y")
    }

    protected fun checkEqualXY(expected: Coordinate, actual: Coordinate, tolerance: Double) {
        assertEquals(expected.x, actual.x, tolerance, "Coordinate X")
        assertEquals(expected.y, actual.y, tolerance, "Coordinate Y")
    }

    protected fun checkEqualXY(message: String, expected: Coordinate, actual: Coordinate, tolerance: Double) {
        assertEquals(expected.x, actual.x, tolerance, "$message X")
        assertEquals(expected.y, actual.y, tolerance, "$message Y")
    }

    protected fun readList(wkt: Array<String>): List<*> {
        val geometries: ArrayList<Any?> = ArrayList<Any?>(wkt.size)
        for (i in wkt.indices) {
            geometries.add(read(wkt[i]))
        }
        return geometries
    }

    fun read(wkt: String): Geometry {
        //return read(readerWKT, wkt);
        return WKTorBReader.read(wkt, geomFactory)!!
    }

    companion object {
        /**
         * Reads a [Geometry] from a WKT string using a custom [GeometryFactory].
         *
         * @param geomFactory the custom factory to use
         * @param wkt the WKT string
         * @return the geometry read
         */
        protected fun read(geomFactory: GeometryFactory?, wkt: String?): Geometry? {
            val reader = WKTReader(geomFactory!!)
            return try {
                reader.read(wkt!!)
            } catch (e: ParseException) {
                throw RuntimeException(e.message)
            }
        }

        fun read(reader: WKTReader, wkt: String?): Geometry? {
            return try {
                reader.read(wkt!!)
            } catch (e: ParseException) {
                throw RuntimeException(e.message)
            }
        }

        fun readList(reader: WKTReader, wkt: Array<String?>): List<*> {
            val geometries: ArrayList<Any?> = ArrayList<Any?>(wkt.size)
            for (i in wkt.indices) {
                geometries.add(read(reader, wkt[i]))
            }
            return geometries
        }

        /**
         * Gets a [WKTReader] to read geometries from WKT with expected ordinates.
         *
         * @param ordinateFlags a set of expected ordinates
         * @return a `WKTReader`
         */
        fun getWKTReader(ordinateFlags: EnumSet<Ordinate>): WKTReader {
            return getWKTReader(ordinateFlags, PrecisionModel())
        }

        /**
         * Gets a [WKTReader] to read geometries from WKT with expected ordinates.
         *
         * @param ordinateFlags a set of expected ordinates
         * @param scale         a scale value to create a [PrecisionModel]
         *
         * @return a `WKTReader`
         */
        fun getWKTReader(ordinateFlags: EnumSet<Ordinate>, scale: Double): WKTReader {
            return getWKTReader(ordinateFlags, PrecisionModel(scale))
        }

        /**
         * Gets a [WKTReader] to read geometries from WKT with expected ordinates.
         *
         * @param ordinateFlags a set of expected ordinates
         * @param precisionModel a precision model
         *
         * @return a `WKTReader`
         */
        fun getWKTReader(ordinateFlags: EnumSet<Ordinate>, precisionModel: PrecisionModel?): WKTReader {
            val result: WKTReader
            if (!ordinateFlags.contains(Ordinate.X)) ordinateFlags.add(Ordinate.X)
            if (!ordinateFlags.contains(Ordinate.Y)) ordinateFlags.add(Ordinate.Y)
            if (ordinateFlags.size == 2) {
                result = WKTReader(GeometryFactory(precisionModel!!, 0, instance()))
                result.setIsOldJtsCoordinateSyntaxAllowed(false)
            } else if (ordinateFlags.contains(Ordinate.Z)) result = WKTReader(
                GeometryFactory(
                    precisionModel!!,
                    0,
                    instance()
                )
            ) else if (ordinateFlags.contains(Ordinate.M)) {
                result = WKTReader(
                    GeometryFactory(
                        precisionModel!!, 0,
                        PackedCoordinateSequenceFactory.DOUBLE_FACTORY
                    )
                )
                result.setIsOldJtsCoordinateSyntaxAllowed(false)
            } else result =
                WKTReader(GeometryFactory(precisionModel!!, 0, PackedCoordinateSequenceFactory.DOUBLE_FACTORY))
            return result
        }
        /**
         * Checks two [CoordinateSequence]s for equality. The following items are checked:
         *
         *  * size * dimension * ordinate values with `tolerance`
         *
         *
         * @param seq1 a sequence
         * @param seq2 another sequence
         * @return `true` if both sequences are equal
         */
        /**
         * Checks two [CoordinateSequence]s for equality. The following items are checked:
         *
         *  * size * dimension * ordinate values
         *
         *
         * @param seq1 a sequence
         * @param seq2 another sequence
         * @return `true` if both sequences are equal
         */
        fun checkEqual(seq1: CoordinateSequence, seq2: CoordinateSequence, tolerance: Double = 0.0): Boolean {
            return if (seq1.dimension != seq2.dimension) false else checkEqual(seq1, seq2, seq1.dimension, tolerance)
        }
        /**
         * Checks two [CoordinateSequence]s for equality. The following items are checked:
         *
         *  * size * dimension up to `dimension` * ordinate values with `tolerance`
         *
         *
         * @param seq1 a sequence
         * @param seq2 another sequence
         * @return `true` if both sequences are equal
         */
        /**
         * Checks two [CoordinateSequence]s for equality. The following items are checked:
         *
         *  * size * dimension up to `dimension` * ordinate values
         *
         *
         * @param seq1 a sequence
         * @param seq2 another sequence
         * @return `true` if both sequences are equal
         */
        fun checkEqual(
            seq1: CoordinateSequence?,
            seq2: CoordinateSequence?,
            dimension: Int,
            tolerance: Double = 0.0
        ): Boolean {
            if (seq1 != null && seq2 == null) return false
            if (seq1 == null && seq2 != null) return false
            if (seq1!!.size() != seq2!!.size()) return false
            require(seq1.dimension >= dimension) { "dimension too high for seq1" }
            require(seq2.dimension >= dimension) { "dimension too high for seq2" }
            for (i in 0 until seq1.size()) {
                for (j in 0 until dimension) {
                    val val1 = seq1.getOrdinate(i, j)
                    val val2 = seq2.getOrdinate(i, j)
                    if (Math.isNaN(val1)) {
                        if (!Math.isNaN(val2)) return false
                    } else if (Math.abs(val1 - val2) > tolerance) return false
                }
            }
            return true
        }

        /**
         * Gets a [CoordinateSequenceFactory] that can create sequences
         * for ordinates defined in the provided bit-pattern.
         * @param ordinateFlags a bit-pattern of ordinates
         * @return a `CoordinateSequenceFactory`
         */
        fun getCSFactory(ordinateFlags: EnumSet<Ordinate>): CoordinateSequenceFactory {
            return if (ordinateFlags.contains(Ordinate.M)) PackedCoordinateSequenceFactory.DOUBLE_FACTORY else instance()
        }
    }

    init {
        geomFactory = GeometryFactory(coordinateSequenceFactory!!)
        readerWKT = WKTReader(geomFactory)
    }
}