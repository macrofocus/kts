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

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.CoordinateSequence
import org.locationtech.jts.geom.CoordinateSequenceFactory
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
import org.locationtech.jts.geom.PrecisionModel
import org.locationtech.jts.geom.impl.CoordinateArraySequenceFactory
import org.locationtech.jts.geom.impl.PackedCoordinateSequenceFactory
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@DslMarker
annotation class GeometryDsl

/** How a [GeometryFactory] made by [geometryFactory] stores the coordinates of its geometries. */
enum class CoordinateStorage {
    /** One [Coordinate] object per vertex (JTS' default, `CoordinateArraySequence`). */
    Objects,

    /** All the ordinates of a sequence in one `DoubleArray`: a fraction of the memory, and the DSL fills it without
     * allocating a [Coordinate]. */
    PackedDouble,

    /** As [PackedDouble] in a `FloatArray`: half the memory again, at single precision. */
    PackedFloat,
}

/** A [GeometryFactory] with named arguments. */
fun geometryFactory(
    srid: Int = 0,
    precisionModel: PrecisionModel = PrecisionModel(),
    storage: CoordinateStorage = CoordinateStorage.Objects,
): GeometryFactory = GeometryFactory(
    precisionModel, srid, when (storage) {
        CoordinateStorage.Objects -> CoordinateArraySequenceFactory.instance()
        CoordinateStorage.PackedDouble -> PackedCoordinateSequenceFactory.DOUBLE_FACTORY
        CoordinateStorage.PackedFloat -> PackedCoordinateSequenceFactory.FLOAT_FACTORY
    }
)

/** The factory of [geometry] when none is given. */
val DefaultGeometryFactory: GeometryFactory = GeometryFactory()

/**
 * The ordinates of a coordinate sequence being built, packed in a growable `DoubleArray`: nothing is allocated per
 * vertex, and with a packed [GeometryFactory] (see [CoordinateStorage]) the array becomes the sequence without a copy.
 *
 * The dimension is set by the first vertex ([xy], [xyz], [xym] or [xyzm]); the following ones must have the same.
 */
@GeometryDsl
open class CoordinatesScope internal constructor() {
    private var ordinates = DoubleArray(16)
    private var length = 0
    private var dimension = 0
    private var measures = 0

    /** The ordinates per vertex, 0 before the first. */
    internal val currentDimension: Int get() = dimension

    /** The number of vertices added so far. */
    val size: Int get() = if (dimension == 0) 0 else length / dimension

    fun xy(x: Double, y: Double) {
        start(2, 0)
        put(x); put(y)
    }

    fun xy(x: Int, y: Int) = xy(x.toDouble(), y.toDouble())

    fun xyz(x: Double, y: Double, z: Double) {
        start(3, 0)
        put(x); put(y); put(z)
    }

    fun xym(x: Double, y: Double, m: Double) {
        start(3, 1)
        put(x); put(y); put(m)
    }

    fun xyzm(x: Double, y: Double, z: Double, m: Double) {
        start(4, 1)
        put(x); put(y); put(z); put(m)
    }

    /** Adds the vertices of a flat array of `x0, y0, x1, y1, ...` (or of [dimension] ordinates each). */
    fun ordinates(packed: DoubleArray, dimension: Int = 2, measures: Int = 0) {
        require(packed.size % dimension == 0) { "${packed.size} ordinates are not a multiple of the dimension $dimension" }
        if (packed.isEmpty()) return
        start(dimension, measures)
        ensure(packed.size)
        packed.copyInto(ordinates, length)
        length += packed.size
    }

    /** Adds the vertices of [sequence], with its dimension. */
    fun coordinates(sequence: CoordinateSequence) {
        val n = sequence.size()
        if (n == 0) return
        val dim = sequence.dimension
        start(dim, sequence.measures)
        ensure(n * dim)
        for (i in 0 until n) for (o in 0 until dim) put(sequence.getOrdinate(i, o))
    }

    /** Adds [coordinate], in 2D unless it has a Z. */
    fun coordinate(coordinate: Coordinate) {
        val z = coordinate.getZ()
        when (if (dimension == 0) (if (z.isNaN()) 2 else 3) else dimension) {
            2 -> xy(coordinate.x, coordinate.y)
            else -> coordinates(coordinate)
        }
    }

    private fun coordinates(coordinate: Coordinate) {
        start(dimension.takeIf { it != 0 } ?: 3, measures)
        for (o in 0 until dimension) put(coordinate.getOrdinate(if (measures == 1 && o == dimension - 1) Coordinate.M else o))
    }

    /** Adds [this] as a vertex. */
    operator fun Coordinate.unaryPlus() = coordinate(this)

    /** Repeats the first vertex at the end unless it is already there (done anyway for the rings of polygons). */
    fun close() {
        val n = size
        if (n == 0) return
        val last = (n - 1) * dimension
        var closed = true
        for (o in 0 until dimension) if (ordinates[o] != ordinates[last + o]) closed = false
        if (!closed) {
            ensure(dimension)
            for (o in 0 until dimension) put(ordinates[o])
        }
    }

    private fun start(dim: Int, meas: Int) {
        if (dimension == 0) {
            dimension = dim
            measures = meas
        } else require(dimension == dim && measures == meas) {
            "A vertex of dimension $dim (measures $meas) in a sequence of dimension $dimension (measures $measures)"
        }
    }

    private fun ensure(more: Int) {
        if (length + more > ordinates.size) ordinates = ordinates.copyOf(maxOf(ordinates.size * 2, length + more))
    }

    private fun put(value: Double) {
        if (length == ordinates.size) ordinates = ordinates.copyOf(ordinates.size * 2)
        ordinates[length++] = value
    }

    internal fun toSequence(factory: CoordinateSequenceFactory): CoordinateSequence {
        val dim = if (dimension == 0) 2 else dimension
        val n = length / dim
        val packed = if (length == ordinates.size) ordinates else ordinates.copyOf(length)
        if (factory is PackedCoordinateSequenceFactory) {
            // Handed over without copying the vertices into Coordinate objects
            return factory.create(packed, dim, measures)
        }
        val sequence = factory.create(n, dim, measures)
        for (i in 0 until n) for (o in 0 until dim) sequence.setOrdinate(i, o, packed[i * dim + o])
        return sequence
    }
}

/** The shell of a polygon, given by its vertices, and its [hole]s. Rings are closed automatically. */
@GeometryDsl
class PolygonScope internal constructor(private val factory: GeometryFactory) : CoordinatesScope() {
    private val holes = ArrayList<LinearRing>()

    /** The vertices of the shell, for readability: they can as well be given directly. */
    fun shell(block: CoordinatesScope.() -> Unit) = block()

    fun hole(block: CoordinatesScope.() -> Unit) {
        holes += ring(factory, block)
    }

    fun hole(ring: LinearRing) {
        holes += ring
    }

    internal fun build(): Polygon {
        if (size == 0) return factory.createPolygon()
        close()
        return factory.createPolygon(factory.createLinearRing(toSequence(factory.coordinateSequenceFactory)), holes.toTypedArray())
    }
}

/**
 * Builds geometries with [factory]. In a [GeometryCollectionScope], [MultiPolygonScope] or [MultiLineStringScope],
 * every geometry built is also added to the collection.
 */
@GeometryDsl
open class GeometryBuilder(val factory: GeometryFactory) {
    /** Called with every geometry built. */
    protected open fun <G : Geometry> emit(geometry: G): G = geometry

    fun point(x: Double, y: Double): Point = emit(factory.createPoint(sequence(factory) { xy(x, y) }))

    fun point(x: Int, y: Int): Point = point(x.toDouble(), y.toDouble())

    fun point(block: CoordinatesScope.() -> Unit): Point = emit(factory.createPoint(sequence(factory, block)))

    fun lineString(block: CoordinatesScope.() -> Unit): LineString =
        emit(factory.createLineString(sequence(factory, block)))

    fun linearRing(block: CoordinatesScope.() -> Unit): LinearRing = emit(ring(factory, block))

    fun polygon(block: PolygonScope.() -> Unit): Polygon = emit(PolygonScope(factory).apply(block).build())

    /** An axis-aligned rectangle. */
    fun box(minX: Double, minY: Double, maxX: Double, maxY: Double): Polygon = polygon {
        xy(minX, minY); xy(maxX, minY); xy(maxX, maxY); xy(minX, maxY)
    }

    /** A regular polygon of [segments] sides approximating a circle. */
    fun circle(centerX: Double, centerY: Double, radius: Double, segments: Int = 64): Polygon {
        require(segments >= 3) { "A circle needs at least 3 segments, not $segments" }
        return polygon {
            for (i in 0 until segments) {
                val angle = 2 * PI * i / segments
                xy(centerX + radius * cos(angle), centerY + radius * sin(angle))
            }
        }
    }

    /** One point per vertex. */
    fun multiPoint(block: CoordinatesScope.() -> Unit): MultiPoint =
        emit(factory.createMultiPoint(sequence(factory, block)))

    fun multiLineString(block: MultiLineStringScope.() -> Unit): MultiLineString =
        emit(factory.createMultiLineString(MultiLineStringScope(factory).apply(block).parts.toTypedArray()))

    fun multiPolygon(block: MultiPolygonScope.() -> Unit): MultiPolygon =
        emit(factory.createMultiPolygon(MultiPolygonScope(factory).apply(block).parts.toTypedArray()))

    fun geometryCollection(block: GeometryCollectionScope.() -> Unit): GeometryCollection =
        emit(factory.createGeometryCollection(GeometryCollectionScope(factory).apply(block).parts.toTypedArray()))
}

/** The line strings of a multi line string: [lineString] or [add]. */
@GeometryDsl
class MultiLineStringScope internal constructor(private val factory: GeometryFactory) {
    internal val parts = ArrayList<LineString>()

    fun lineString(block: CoordinatesScope.() -> Unit): LineString =
        factory.createLineString(sequence(factory, block)).also { parts += it }

    fun add(lineString: LineString) {
        parts += lineString
    }
}

/** The polygons of a multi polygon: [polygon], [box], [circle] or [add]. */
@GeometryDsl
class MultiPolygonScope internal constructor(factory: GeometryFactory) {
    private val builder = GeometryBuilder(factory)
    internal val parts = ArrayList<Polygon>()

    fun polygon(block: PolygonScope.() -> Unit): Polygon = builder.polygon(block).also { parts += it }

    fun box(minX: Double, minY: Double, maxX: Double, maxY: Double): Polygon =
        builder.box(minX, minY, maxX, maxY).also { parts += it }

    fun circle(centerX: Double, centerY: Double, radius: Double, segments: Int = 64): Polygon =
        builder.circle(centerX, centerY, radius, segments).also { parts += it }

    fun add(polygon: Polygon) {
        parts += polygon
    }
}

/** The members of a geometry collection: every geometry built here, and those added with [add] or `+`. */
@GeometryDsl
class GeometryCollectionScope internal constructor(factory: GeometryFactory) : GeometryBuilder(factory) {
    internal val parts = ArrayList<Geometry>()

    override fun <G : Geometry> emit(geometry: G): G = geometry.also { parts += it }

    fun add(geometry: Geometry) {
        parts += geometry
    }

    operator fun Geometry.unaryPlus() = add(this)
}

private fun sequence(factory: GeometryFactory, block: CoordinatesScope.() -> Unit): CoordinateSequence =
    CoordinatesScope().apply(block).toSequence(factory.coordinateSequenceFactory)

private fun ring(factory: GeometryFactory, block: CoordinatesScope.() -> Unit): LinearRing {
    val scope = CoordinatesScope().apply(block)
    scope.close()
    return factory.createLinearRing(scope.toSequence(factory.coordinateSequenceFactory))
}

/**
 * Builds geometries with this factory:
 * ```
 * val shape = factory.build {
 *     polygon {
 *         xy(0, 0); xy(10, 0); xy(10, 10); xy(0, 10)
 *         hole { xy(2, 2); xy(4, 2); xy(4, 4) }
 *     }
 * }
 * ```
 */
inline fun <G> GeometryFactory.build(block: GeometryBuilder.() -> G): G = GeometryBuilder(this).block()

/** [build] with [factory], [DefaultGeometryFactory] by default. */
inline fun <G> geometry(factory: GeometryFactory = DefaultGeometryFactory, block: GeometryBuilder.() -> G): G =
    factory.build(block)

fun GeometryFactory.point(x: Double, y: Double): Point = GeometryBuilder(this).point(x, y)
fun GeometryFactory.lineString(block: CoordinatesScope.() -> Unit): LineString = GeometryBuilder(this).lineString(block)
fun GeometryFactory.linearRing(block: CoordinatesScope.() -> Unit): LinearRing = GeometryBuilder(this).linearRing(block)
fun GeometryFactory.polygon(block: PolygonScope.() -> Unit): Polygon = GeometryBuilder(this).polygon(block)
fun GeometryFactory.box(minX: Double, minY: Double, maxX: Double, maxY: Double): Polygon =
    GeometryBuilder(this).box(minX, minY, maxX, maxY)
fun GeometryFactory.circle(centerX: Double, centerY: Double, radius: Double, segments: Int = 64): Polygon =
    GeometryBuilder(this).circle(centerX, centerY, radius, segments)
fun GeometryFactory.multiPoint(block: CoordinatesScope.() -> Unit): MultiPoint = GeometryBuilder(this).multiPoint(block)
fun GeometryFactory.multiLineString(block: MultiLineStringScope.() -> Unit): MultiLineString =
    GeometryBuilder(this).multiLineString(block)
fun GeometryFactory.multiPolygon(block: MultiPolygonScope.() -> Unit): MultiPolygon =
    GeometryBuilder(this).multiPolygon(block)
fun GeometryFactory.geometryCollection(block: GeometryCollectionScope.() -> Unit): GeometryCollection =
    GeometryBuilder(this).geometryCollection(block)

/** A coordinate sequence of this factory's kind, built without allocating per vertex. */
fun CoordinateSequenceFactory.sequence(block: CoordinatesScope.() -> Unit): CoordinateSequence =
    CoordinatesScope().apply(block).toSequence(this)
