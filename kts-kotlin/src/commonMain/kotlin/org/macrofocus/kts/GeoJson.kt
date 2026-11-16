/*
 * Copyright (c) 2026 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.macrofocus.kts

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOn
import org.locationtech.jts.geom.CoordinateSequence
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryCollection
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.LinearRing
import org.locationtech.jts.geom.MultiLineString
import org.locationtech.jts.geom.MultiPoint
import org.locationtech.jts.geom.MultiPolygon
import org.locationtech.jts.geom.Point
import org.locationtech.jts.geom.Polygon

/**
 * A GeoJSON feature: a geometry (null for none) with properties. Property values are String, Long (integers), Double,
 * Boolean, null, List and Map, as in the JSON.
 */
data class Feature(
    val geometry: Geometry?,
    val properties: Map<String, Any?> = emptyMap(),
    val id: Any? = null,
)

/** A GeoJSON syntax or structure error, with its position in the text. */
class GeoJsonException(message: String, val position: Int) : IllegalArgumentException("$message at character $position")

/**
 * Reads and writes GeoJSON (RFC 7946). The reader is hand-written for speed: it scans the text once, parses numbers
 * without substrings, and writes the positions of each line or ring straight into a packed array that becomes the
 * coordinate sequence (see [CoordinateStorage]). Members may come in any order; "bbox", "crs" and foreign members are
 * skipped.
 */
object GeoJson {
    /**
     * The geometry of a GeoJSON geometry, a Feature (its geometry, null for none) or a FeatureCollection (a collection
     * of the geometries of its features).
     */
    fun readGeometry(text: CharSequence, factory: GeometryFactory = DefaultGeometryFactory): Geometry? {
        val features = ArrayList<Feature>()
        val collection = topLevel(Parser(text, factory)) { features += it }
        return if (!collection) features.single().geometry
        else factory.createGeometryCollection(features.mapNotNull { it.geometry }.toTypedArray())
    }

    /** The features of a FeatureCollection; a Feature or a bare geometry gives one. */
    fun readFeatures(text: CharSequence, factory: GeometryFactory = DefaultGeometryFactory): List<Feature> =
        features(text, factory).toList()

    /**
     * The features, parsed one by one as the sequence is iterated: a large FeatureCollection can be processed without
     * holding all its features.
     */
    fun features(text: CharSequence, factory: GeometryFactory = DefaultGeometryFactory): Sequence<Feature> = sequence {
        topLevel(Parser(text, factory)) { yield(it) }
    }

    /**
     * Reads the top-level object, calling [onFeature] with each feature of a FeatureCollection as it is parsed, or with
     * the Feature or the geometry (as a feature) it is. Whether it was a FeatureCollection.
     */
    private inline fun topLevel(p: Parser, onFeature: (Feature) -> Unit): Boolean {
        p.expect('{')
        val top = Members()
        var collection = false
        if (!p.consume('}')) {
            do {
                val key = p.string()
                p.expect(':')
                if (key == "features") {
                    collection = true
                    p.expect('[')
                    if (!p.consume(']')) {
                        do onFeature(p.feature()) while (p.consume(','))
                        p.expect(']')
                    }
                } else p.member(top, key)
            } while (p.consume(','))
            p.expect('}')
        }
        p.end()
        if (!collection) onFeature(top.toFeature(p))
        return collection
    }

    /** [features] parsed on [dispatcher], so that a large file does not block the collector's thread. */
    fun featureFlow(
        text: CharSequence,
        factory: GeometryFactory = DefaultGeometryFactory,
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
    ): Flow<Feature> = features(text, factory).asFlow().flowOn(dispatcher)

    fun write(geometry: Geometry): String = StringBuilder().also { writeGeometry(it, geometry) }.toString()

    fun write(feature: Feature): String = StringBuilder().also { writeFeature(it, feature) }.toString()

    fun write(features: Iterable<Feature>): String = StringBuilder().apply {
        append("{\"type\":\"FeatureCollection\",\"features\":[")
        var first = true
        for (feature in features) {
            if (!first) append(',')
            first = false
            writeFeature(this, feature)
        }
        append("]}")
    }.toString()

    // --- Writing --------------------------------------------------------------------------------------------------

    private fun writeFeature(out: StringBuilder, feature: Feature) {
        out.append("{\"type\":\"Feature\"")
        if (feature.id != null) {
            out.append(",\"id\":")
            writeValue(out, feature.id)
        }
        out.append(",\"geometry\":")
        if (feature.geometry == null) out.append("null") else writeGeometry(out, feature.geometry)
        out.append(",\"properties\":")
        writeValue(out, feature.properties)
        out.append('}')
    }

    private fun writeGeometry(out: StringBuilder, g: Geometry) {
        out.append("{\"type\":\"").append(typeName(g)).append('"')
        if (g is GeometryCollection && g !is MultiPoint && g !is MultiLineString && g !is MultiPolygon) {
            out.append(",\"geometries\":[")
            for (i in 0 until g.numGeometries) {
                if (i > 0) out.append(',')
                writeGeometry(out, g.getGeometryN(i))
            }
            out.append("]}")
            return
        }
        out.append(",\"coordinates\":")
        when (g) {
            is Point -> if (g.isEmpty) out.append("[]") else writePosition(out, g.coordinateSequence!!, 0)
            is LineString -> writePositions(out, g.coordinateSequence!!)
            is Polygon -> writeRings(out, g)
            is MultiPoint -> {
                out.append('[')
                for (i in 0 until g.numGeometries) {
                    if (i > 0) out.append(',')
                    writePosition(out, (g.getGeometryN(i) as Point).coordinateSequence!!, 0)
                }
                out.append(']')
            }
            is MultiLineString -> {
                out.append('[')
                for (i in 0 until g.numGeometries) {
                    if (i > 0) out.append(',')
                    writePositions(out, (g.getGeometryN(i) as LineString).coordinateSequence!!)
                }
                out.append(']')
            }
            is MultiPolygon -> {
                out.append('[')
                for (i in 0 until g.numGeometries) {
                    if (i > 0) out.append(',')
                    writeRings(out, g.getGeometryN(i) as Polygon)
                }
                out.append(']')
            }
        }
        out.append('}')
    }

    private fun typeName(g: Geometry): String = when (g) {
        is Point -> "Point"
        is LinearRing, is LineString -> "LineString"
        is Polygon -> "Polygon"
        is MultiPoint -> "MultiPoint"
        is MultiLineString -> "MultiLineString"
        is MultiPolygon -> "MultiPolygon"
        else -> "GeometryCollection"
    }

    private fun writeRings(out: StringBuilder, polygon: Polygon) {
        out.append('[')
        if (!polygon.isEmpty) {
            writePositions(out, polygon.exteriorRing!!.coordinateSequence!!)
            for (hole in polygon.holes) {
                out.append(',')
                writePositions(out, hole.coordinateSequence!!)
            }
        }
        out.append(']')
    }

    private fun writePositions(out: StringBuilder, seq: CoordinateSequence) {
        out.append('[')
        for (i in 0 until seq.size()) {
            if (i > 0) out.append(',')
            writePosition(out, seq, i)
        }
        out.append(']')
    }

    private fun writePosition(out: StringBuilder, seq: CoordinateSequence, i: Int) {
        out.append('[')
        writeNumber(out, seq.getX(i))
        out.append(',')
        writeNumber(out, seq.getY(i))
        if (seq.hasZ()) {
            val z = seq.getZ(i)
            if (!z.isNaN()) {
                out.append(',')
                writeNumber(out, z)
            }
        }
        out.append(']')
    }

    private fun writeNumber(out: StringBuilder, value: Double) {
        require(value.isFinite()) { "GeoJSON has no $value" }
        val long = value.toLong()
        if (long.toDouble() == value && long in -999_999_999_999_999L..999_999_999_999_999L) out.append(long)
        else out.append(value.toString())
    }

    private fun writeValue(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is String -> writeString(out, value)
            is Boolean -> out.append(value)
            is Double -> if (value.isFinite()) writeNumber(out, value) else out.append("null")
            is Float -> writeValue(out, value.toDouble())
            is Number -> out.append(value.toLong())
            is Map<*, *> -> {
                out.append('{')
                var first = true
                for ((k, v) in value) {
                    if (!first) out.append(',')
                    first = false
                    writeString(out, k.toString())
                    out.append(':')
                    writeValue(out, v)
                }
                out.append('}')
            }
            is Iterable<*> -> {
                out.append('[')
                var first = true
                for (v in value) {
                    if (!first) out.append(',')
                    first = false
                    writeValue(out, v)
                }
                out.append(']')
            }
            is Array<*> -> writeValue(out, value.asList())
            is Geometry -> writeGeometry(out, value)
            else -> writeString(out, value.toString())
        }
    }

    private fun writeString(out: StringBuilder, s: String) {
        out.append('"')
        for (c in s) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> if (c < ' ') out.append("\\u").append(c.code.toString(16).padStart(4, '0')) else out.append(c)
            }
        }
        out.append('"')
    }

    // --- Reading --------------------------------------------------------------------------------------------------

    /** The members of a GeoJSON object read so far, whatever their order. */
    private class Members {
        var type: String? = null
        var coordinates: Any? = null
        var geometries: List<Geometry>? = null
        var geometry: Geometry? = null
        var properties: Map<String, Any?>? = null
        var id: Any? = null

        fun toGeometry(p: Parser): Geometry = p.build(this)

        fun toFeature(p: Parser): Feature = when (type) {
            "Feature" -> Feature(geometry, properties ?: emptyMap(), id)
            null -> throw GeoJsonException("No \"type\"", p.pos)
            else -> Feature(toGeometry(p))
        }
    }

    private class Parser(private val text: CharSequence, private val factory: GeometryFactory) {
        var pos = 0
        private val position = DoubleArray(4)

        fun fail(message: String): Nothing = throw GeoJsonException(message, pos)

        private fun skipWhitespace() {
            while (pos < text.length) {
                val c = text[pos]
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') pos++ else break
            }
        }

        private fun peek(): Char {
            skipWhitespace()
            return if (pos < text.length) text[pos] else fail("Unexpected end")
        }

        fun expect(c: Char) {
            if (peek() != c) fail("Expected '$c' but found '${text[pos]}'")
            pos++
        }

        fun consume(c: Char): Boolean {
            if (peek() == c) {
                pos++
                return true
            }
            return false
        }

        fun end() {
            skipWhitespace()
            if (pos != text.length) fail("Unexpected '${text[pos]}' after the GeoJSON")
        }

        fun feature(): Feature {
            val m = objectMembers()
            if (m.type != "Feature") fail("Expected a Feature but found ${m.type}")
            return m.toFeature(this)
        }

        private fun objectMembers(): Members {
            expect('{')
            val m = Members()
            if (!consume('}')) {
                do {
                    val key = string()
                    expect(':')
                    member(m, key)
                } while (consume(','))
                expect('}')
            }
            return m
        }

        fun member(m: Members, key: String) {
            when (key) {
                "type" -> m.type = string()
                "coordinates" -> m.coordinates = coordinates()
                "geometries" -> m.geometries = ArrayList<Geometry>().also { list ->
                    expect('[')
                    if (!consume(']')) {
                        do list += geometryObject() while (consume(','))
                        expect(']')
                    }
                }
                "geometry" -> m.geometry = if (peek() == 'n') {
                    literal("null"); null
                } else geometryObject()
                "properties" -> m.properties = properties(value())
                "id" -> m.id = value()
                else -> value()
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun properties(value: Any?): Map<String, Any?>? = when (value) {
            null -> null
            is Map<*, *> -> value as Map<String, Any?>
            else -> fail("\"properties\" must be an object or null")
        }

        private fun geometryObject(): Geometry = objectMembers().toGeometry(this)

        /** A position (DoubleArray), a list of positions (CoordinatesScope), an empty list or a list of those. */
        private fun coordinates(): Any {
            expect('[')
            if (consume(']')) return emptyList<Any>()
            val c = peek()
            if (c != '[') {
                // A position
                var n = 0
                do {
                    val v = number()
                    if (n < 4) position[n] = v
                    n++
                } while (consume(','))
                expect(']')
                if (n < 2) fail("A position needs at least 2 numbers")
                return position.copyOf(minOf(n, 3))
            }
            val first = coordinates()
            if (first is DoubleArray) {
                val scope = CoordinatesScope()
                add(scope, first, first.size)
                while (consume(',')) {
                    expect('[')
                    var n = 0
                    do {
                        val v = number()
                        if (n < 4) position[n] = v
                        n++
                    } while (consume(','))
                    expect(']')
                    if (n < 2) fail("A position needs at least 2 numbers")
                    add(scope, position, n)
                }
                expect(']')
                return scope
            }
            val list = ArrayList<Any>()
            list += first
            while (consume(',')) list += coordinates()
            expect(']')
            return list
        }

        /** Adds a position, a missing Z as NaN, an extra one (or a measure) dropped, as the first set the dimension. */
        private fun add(scope: CoordinatesScope, p: DoubleArray, n: Int) {
            when (scope.currentDimension) {
                0 -> if (n >= 3) scope.xyz(p[0], p[1], p[2]) else scope.xy(p[0], p[1])
                2 -> scope.xy(p[0], p[1])
                else -> scope.xyz(p[0], p[1], if (n >= 3) p[2] else Double.NaN)
            }
        }

        fun build(m: Members): Geometry {
            val csf = factory.coordinateSequenceFactory
            val c = m.coordinates
            fun line(any: Any?): CoordinatesScope = when (any) {
                is CoordinatesScope -> any
                is List<*> -> if (any.isEmpty()) CoordinatesScope() else fail("Expected positions")
                is DoubleArray -> CoordinatesScope().also { add(it, any, any.size) }
                else -> fail("Expected positions")
            }
            fun list(any: Any?): List<*> = when (any) {
                is List<*> -> any
                is CoordinatesScope -> fail("Expected a list of lists of positions")
                else -> fail("Expected an array of coordinates")
            }
            fun ring(any: Any?): LinearRing = factory.createLinearRing(line(any).apply { close() }.toSequence(csf))
            fun polygon(any: Any?): Polygon {
                val rings = list(any)
                if (rings.isEmpty()) return factory.createPolygon()
                return factory.createPolygon(ring(rings[0]), Array(rings.size - 1) { ring(rings[it + 1]) })
            }
            return when (m.type) {
                "Point" -> when (c) {
                    is DoubleArray -> factory.createPoint(line(c).toSequence(csf))
                    is List<*> -> if (c.isEmpty()) factory.createPoint() else fail("Expected a position")
                    else -> fail("Expected a position")
                }
                "MultiPoint" -> factory.createMultiPoint(line(c).toSequence(csf))
                "LineString" -> factory.createLineString(line(c).toSequence(csf))
                "MultiLineString" -> factory.createMultiLineString(
                    list(c).map { factory.createLineString(line(it).toSequence(csf)) }.toTypedArray()
                )
                "Polygon" -> polygon(c)
                "MultiPolygon" -> factory.createMultiPolygon(list(c).map { polygon(it) }.toTypedArray())
                "GeometryCollection" -> factory.createGeometryCollection((m.geometries ?: emptyList()).toTypedArray())
                null -> fail("A geometry without \"type\"")
                else -> fail("Unknown geometry type ${m.type}")
            }
        }

        /** Any JSON value: String, Long, Double, Boolean, null, List or Map. */
        fun value(): Any? = when (peek()) {
            '{' -> {
                pos++
                val map = LinkedHashMap<String, Any?>()
                if (!consume('}')) {
                    do {
                        val key = string()
                        expect(':')
                        map[key] = value()
                    } while (consume(','))
                    expect('}')
                }
                map
            }
            '[' -> {
                pos++
                val list = ArrayList<Any?>()
                if (!consume(']')) {
                    do list += value() while (consume(','))
                    expect(']')
                }
                list
            }
            '"' -> string()
            't' -> { literal("true"); true }
            'f' -> { literal("false"); false }
            'n' -> { literal("null"); null }
            else -> {
                val start = pos
                val d = number()
                // Integers as Long, as the JSON wrote them
                val s = text.subSequence(start, pos)
                if (s.all { it == '-' || it in '0'..'9' } && s.length < 19) s.toString().toLong() else d
            }
        }

        private fun literal(word: String) {
            skipWhitespace()
            if (pos + word.length > text.length || !text.regionMatches(pos, word, 0, word.length)) fail("Expected $word")
            pos += word.length
        }

        fun string(): String {
            expect('"')
            val start = pos
            // Fast path: no escape
            while (pos < text.length) {
                val c = text[pos]
                if (c == '"') return text.subSequence(start, pos++).toString()
                if (c == '\\') break
                pos++
            }
            val sb = StringBuilder().append(text, start, pos)
            while (pos < text.length) {
                val c = text[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= text.length) fail("Unterminated string")
                        when (val e = text[pos++]) {
                            '"', '\\', '/' -> sb.append(e)
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > text.length) fail("Truncated \\u escape")
                                sb.append(text.subSequence(pos, pos + 4).toString().toInt(16).toChar())
                                pos += 4
                            }
                            else -> fail("Invalid escape \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
            fail("Unterminated string")
        }

        /**
         * A JSON number, exact: up to 15 significant digits and a power of ten up to 22 are both exact doubles, so
         * one multiplication or division rounds correctly (Clinger's fast path); longer numbers go to toDouble().
         */
        fun number(): Double {
            skipWhitespace()
            val start = pos
            var negative = false
            if (pos < text.length && text[pos] == '-') {
                negative = true
                pos++
            }
            var mantissa = 0L
            var digits = 0
            var exponent = 0
            var any = false
            while (pos < text.length && text[pos] in '0'..'9') {
                any = true
                if (digits < 18) {
                    mantissa = mantissa * 10 + (text[pos] - '0')
                    if (mantissa != 0L) digits++
                } else exponent++
                pos++
            }
            if (pos < text.length && text[pos] == '.') {
                pos++
                while (pos < text.length && text[pos] in '0'..'9') {
                    any = true
                    if (digits < 18) {
                        mantissa = mantissa * 10 + (text[pos] - '0')
                        if (mantissa != 0L) digits++
                        exponent--
                    }
                    pos++
                }
            }
            if (!any) fail("Expected a number")
            if (pos < text.length && (text[pos] == 'e' || text[pos] == 'E')) {
                pos++
                var expNegative = false
                if (pos < text.length && (text[pos] == '+' || text[pos] == '-')) {
                    expNegative = text[pos] == '-'
                    pos++
                }
                var e = 0
                var expDigits = false
                while (pos < text.length && text[pos] in '0'..'9') {
                    if (e < 100_000) e = e * 10 + (text[pos] - '0')
                    expDigits = true
                    pos++
                }
                if (!expDigits) fail("Expected an exponent")
                exponent += if (expNegative) -e else e
            }
            if (digits <= 15 && exponent in -22..22) {
                val m = mantissa.toDouble()
                val v = if (exponent >= 0) m * POWERS_OF_TEN[exponent] else m / POWERS_OF_TEN[-exponent]
                return if (negative) -v else v
            }
            return text.subSequence(start, pos).toString().toDouble()
        }
    }

    private val POWERS_OF_TEN = DoubleArray(23).also { p ->
        p[0] = 1.0
        for (i in 1 until p.size) p[i] = p[i - 1] * 10
    }
}

/** This geometry as GeoJSON. */
fun Geometry.toGeoJson(): String = GeoJson.write(this)
