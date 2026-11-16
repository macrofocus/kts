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
import org.locationtech.jts.geom.CoordinateSequenceFilter
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryCollection
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.LinearRing
import org.locationtech.jts.geom.Point
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.prep.PreparedGeometry
import org.locationtech.jts.geom.prep.PreparedGeometryFactory

// --- Set operations -----------------------------------------------------------------------------------------------

/** The union of the two geometries. */
operator fun Geometry.plus(other: Geometry): Geometry = union(other)

/** This geometry without [other]. */
operator fun Geometry.minus(other: Geometry): Geometry = difference(other)

/** The intersection of the two geometries. */
infix fun Geometry.and(other: Geometry): Geometry = intersection(other)

/** The union of the two geometries. */
infix fun Geometry.or(other: Geometry): Geometry = union(other)

/** The symmetric difference of the two geometries. */
infix fun Geometry.xor(other: Geometry): Geometry = symDifference(other)

// --- Components ---------------------------------------------------------------------------------------------------

/** The [n]th component (the geometry itself for a non-collection and n = 0). */
operator fun Geometry.get(n: Int): Geometry = getGeometryN(n)

/** The components of this geometry (itself for a non-collection), as a view. */
val Geometry.parts: List<Geometry>
    get() {
        val geometry = this
        return object : AbstractList<Geometry>() {
            override val size: Int get() = geometry.numGeometries
            override fun get(index: Int): Geometry = geometry.getGeometryN(index)
        }
    }

operator fun Geometry.iterator(): Iterator<Geometry> = parts.iterator()

/** The holes of this polygon, as a view. */
val Polygon.holes: List<LinearRing>
    get() {
        val polygon = this
        return object : AbstractList<LinearRing>() {
            override val size: Int get() = polygon.getNumInteriorRing()
            override fun get(index: Int): LinearRing = polygon.getInteriorRingN(index)
        }
    }

/** The shell followed by the holes of this polygon (none if it is empty). */
val Polygon.rings: List<LinearRing> get() = listOfNotNull(exteriorRing) + holes

/** The non-collection geometries of this geometry, nested collections flattened, in depth-first order. */
fun Geometry.flatten(): List<Geometry> = ArrayList<Geometry>().also { collectAtomic(this, it) }

private fun collectAtomic(geometry: Geometry, into: MutableList<Geometry>) {
    if (geometry is GeometryCollection) {
        for (i in 0 until geometry.numGeometries) collectAtomic(geometry.getGeometryN(i), into)
    } else into += geometry
}

/** The non-collection geometries of type [G], e.g. `geometry.extract<Polygon>()`. */
inline fun <reified G : Geometry> Geometry.extract(): List<G> = flatten().filterIsInstance<G>()

// --- Coordinates --------------------------------------------------------------------------------------------------

/** The coordinate sequences of this geometry, those of the rings of polygons, in depth-first order. */
fun Geometry.coordinateSequences(): List<CoordinateSequence> = ArrayList<CoordinateSequence>().also { collectSequences(this, it) }

private fun collectSequences(geometry: Geometry, into: MutableList<CoordinateSequence>) {
    when (geometry) {
        is Point -> geometry.coordinateSequence?.let { into += it }
        is LineString -> geometry.coordinateSequence?.let { into += it }
        is Polygon -> {
            geometry.exteriorRing?.coordinateSequence?.let { into += it }
            for (i in 0 until geometry.getNumInteriorRing()) geometry.getInteriorRingN(i).coordinateSequence?.let { into += it }
        }
        is GeometryCollection -> for (i in 0 until geometry.numGeometries) collectSequences(geometry.getGeometryN(i), into)
    }
}

/**
 * Calls [action] with every vertex, without allocating a [Coordinate] (`coordinates` copies them all in an array; with
 * packed sequences it even creates them).
 */
inline fun Geometry.forEachXY(action: (x: Double, y: Double) -> Unit) {
    for (sequence in coordinateSequences()) sequence.forEachXY(action)
}

inline fun CoordinateSequence.forEachXY(action: (x: Double, y: Double) -> Unit) {
    for (i in 0 until size()) action(getX(i), getY(i))
}

/** A mutable pair of coordinates: the vertex being moved by [transformCoordinates], the output of a [ViewTransform]. */
class MutableXY(var x: Double = 0.0, var y: Double = 0.0)

/**
 * A copy of this geometry with every vertex moved by [transform], e.g. a projection:
 * ```
 * val mercator = lonLat.transformCoordinates { y = ln(tan(PI / 4 + y * PI / 360)) * 180 / PI }
 * ```
 * One [MutableXY] is reused for every vertex, so nothing is allocated per vertex. Z and M are kept.
 */
fun Geometry.transformCoordinates(transform: MutableXY.() -> Unit): Geometry {
    val copy = copy()
    val xy = MutableXY()
    copy.apply(object : CoordinateSequenceFilter {
        override fun filter(seq: CoordinateSequence?, i: Int) {
            seq!!
            xy.x = seq.getX(i)
            xy.y = seq.getY(i)
            xy.transform()
            seq.setOrdinate(i, CoordinateSequence.X, xy.x)
            seq.setOrdinate(i, CoordinateSequence.Y, xy.y)
        }

        override val isDone: Boolean get() = false
        override val isGeometryChanged: Boolean get() = true
    })
    return copy
}

operator fun Coordinate.component1(): Double = x
operator fun Coordinate.component2(): Double = y
operator fun Coordinate.component3(): Double = getZ()

operator fun Coordinate.plus(other: Coordinate): Coordinate = Coordinate(x + other.x, y + other.y)
operator fun Coordinate.minus(other: Coordinate): Coordinate = Coordinate(x - other.x, y - other.y)
operator fun Coordinate.times(factor: Double): Coordinate = Coordinate(x * factor, y * factor)

// --- Envelopes ----------------------------------------------------------------------------------------------------

/**
 * An envelope from its bounds in the usual order (the constructor of [Envelope] takes `x1, x2, y1, y2`).
 */
fun envelope(minX: Double, minY: Double, maxX: Double, maxY: Double): Envelope = Envelope(minX, maxX, minY, maxY)

operator fun Envelope.component1(): Double = minX
operator fun Envelope.component2(): Double = minY
operator fun Envelope.component3(): Double = maxX
operator fun Envelope.component4(): Double = maxY

/** Whether [coordinate] lies in this envelope or on its boundary. */
operator fun Envelope.contains(coordinate: Coordinate): Boolean = covers(coordinate)

/** The smallest envelope containing both. */
operator fun Envelope.plus(other: Envelope): Envelope = Envelope(this).apply { expandToInclude(other) }

/** The common part of both envelopes (a null envelope, see [Envelope.isNull], when they do not intersect). */
infix fun Envelope.and(other: Envelope): Envelope = intersection(other)

/** This envelope as a polygon (or a point or a line when it is degenerate). */
fun Envelope.toGeometry(factory: GeometryFactory = DefaultGeometryFactory): Geometry = factory.toGeometry(this)

// --- Collections of geometries ------------------------------------------------------------------------------------

/**
 * The union of all these geometries, by cascaded union: much faster than `reduce { a, b -> a + b }`, which unions an
 * ever growing result with each geometry. Null when there are none.
 */
fun Iterable<Geometry>.unionAll(): Geometry? = org.locationtech.jts.operation.union.UnaryUnionOp.union(toList())

/** The most specific geometry holding these: a multi polygon for polygons, a collection when they are mixed. */
fun Iterable<Geometry>.toGeometry(factory: GeometryFactory = DefaultGeometryFactory): Geometry =
    factory.buildGeometry(toList())

fun Iterable<Geometry>.toGeometryCollection(factory: GeometryFactory = DefaultGeometryFactory): GeometryCollection =
    factory.createGeometryCollection(toList().toTypedArray())

/** The envelope of all these geometries. */
fun Iterable<Geometry>.envelope(): Envelope = Envelope().also { env -> forEach { env.expandToInclude(it.envelopeInternal) } }

/** This geometry prepared for repeated predicates (`intersects`, `contains`, `covers`...) against many others. */
fun Geometry.prepare(): PreparedGeometry = PreparedGeometryFactory.prepare(this)

/**
 * The geometries intersecting [other], with [other] prepared once and every geometry first tested against its
 * envelope.
 */
fun <G : Geometry> Iterable<G>.filterIntersecting(other: Geometry): List<G> {
    val prepared = other.prepare()
    val envelope = other.envelopeInternal
    return filter { envelope.intersects(it.envelopeInternal) && prepared.intersects(it) }
}
