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

import org.locationtech.jts.geom.CoordinateSequence
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryCollection
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.Point
import org.locationtech.jts.geom.Polygon
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

// Geometries in longitude (x) and latitude (y), in degrees (WGS 84, EPSG:4326), on a sphere of the mean radius of the
// Earth: within 0.5 % of the ellipsoid, enough for maps, hit testing and buffers.

/** The mean radius of the Earth, in metres (IUGG). */
const val EARTH_RADIUS: Double = 6_371_008.8

private const val RADIANS = PI / 180

/** The great-circle distance in metres between two positions in degrees. */
fun haversineDistance(lon1: Double, lat1: Double, lon2: Double, lat2: Double): Double {
    val dLat = (lat2 - lat1) * RADIANS
    val dLon = (lon2 - lon1) * RADIANS
    val a = sin(dLat / 2).let { it * it } + cos(lat1 * RADIANS) * cos(lat2 * RADIANS) * sin(dLon / 2).let { it * it }
    return 2 * EARTH_RADIUS * asin(sqrt(minOf(1.0, a)))
}

/** [lon] brought into -180..180. */
fun normalizeLongitude(lon: Double): Double {
    if (lon >= -180 && lon <= 180) return lon
    val l = (lon + 180) % 360
    return (if (l < 0) l + 360 else l) - 180
}

/** The length in metres of the lines (the perimeter of the polygons) of this geometry in degrees. */
fun Geometry.geodesicLength(): Double {
    var length = 0.0
    for (seq in coordinateSequences()) {
        for (i in 1 until seq.size()) length += haversineDistance(seq.getX(i - 1), seq.getY(i - 1), seq.getX(i), seq.getY(i))
    }
    return length
}

/** The area in square metres of the polygons of this geometry in degrees, on the sphere. */
fun Geometry.geodesicArea(): Double {
    var area = 0.0
    for (polygon in extract<Polygon>()) {
        val shell = polygon.exteriorRing ?: continue
        area += abs(ringArea(shell.coordinateSequence!!))
        for (hole in polygon.holes) area -= abs(ringArea(hole.coordinateSequence!!))
    }
    return area
}

/** The signed spherical area of a closed ring: R² / 2 Σ (λ[i+1] - λ[i-1]) sin φ[i] (Chamberlain and Duquette). */
private fun ringArea(seq: CoordinateSequence): Double {
    val n = seq.size() - 1 // the last vertex repeats the first
    if (n < 3) return 0.0
    var total = 0.0
    for (i in 0 until n) {
        val previous = if (i == 0) n - 1 else i - 1
        val next = if (i == n - 1) 0 else i + 1
        total += (seq.getX(next) - seq.getX(previous)) * RADIANS * sin(seq.getY(i) * RADIANS)
    }
    return total * EARTH_RADIUS * EARTH_RADIUS / 2
}

/**
 * The envelope in degrees of the positions within [meters] of ([lon], [lat]): the box to query an index of geometries
 * in degrees for what is near a position. Its width grows towards the poles, up to the whole circle.
 */
fun geoEnvelopeAround(lon: Double, lat: Double, meters: Double): Envelope {
    val dLat = meters / EARTH_RADIUS / RADIANS
    val dLon = (dLat / cos(lat * RADIANS).coerceAtLeast(1e-6)).coerceAtMost(180.0)
    return envelope(lon - dLon, maxOf(-90.0, lat - dLat), lon + dLon, minOf(90.0, lat + dLat))
}

/** Spherical Web Mercator (EPSG:3857), in metres, as the tiles of OpenStreetMap. */
object WebMercator : ViewTransform {
    /** The latitude where the projection makes a square. */
    const val MAX_LATITUDE: Double = 85.0511287798066

    /** The half width of the square, in metres. */
    const val HALF_SIZE: Double = 20037508.342789244

    private const val R = 6_378_137.0

    /** Projects longitude and latitude into [out], the latitude clamped to ±[MAX_LATITUDE]. */
    override fun transform(x: Double, y: Double, out: MutableXY) {
        val lat = y.coerceIn(-MAX_LATITUDE, MAX_LATITUDE) * RADIANS
        out.x = R * x * RADIANS
        out.y = R * ln(tan(PI / 4 + lat / 2))
    }

    /** The inverse of [transform]. */
    val inverse: ViewTransform = ViewTransform { x, y, out ->
        out.x = x / R / RADIANS
        out.y = (2 * atan(exp(y / R)) - PI / 2) / RADIANS
    }
}

/** [this] then [next]. */
infix fun ViewTransform.then(next: ViewTransform): ViewTransform {
    val first = this
    return ViewTransform { x, y, out ->
        first.transform(x, y, out)
        next.transform(out.x, out.y, out)
    }
}

/** A copy of this geometry with every vertex mapped by [transform]. */
fun Geometry.project(transform: ViewTransform): Geometry {
    val out = MutableXY()
    return transformCoordinates {
        transform.transform(x, y, out)
        x = out.x
        y = out.y
    }
}

/** This geometry in degrees projected to [WebMercator]. */
fun Geometry.toWebMercator(): Geometry = project(WebMercator)

/** This geometry in [WebMercator] back to degrees. */
fun Geometry.fromWebMercator(): Geometry = project(WebMercator.inverse)

/**
 * This geometry with its lines and polygons that cross the antimeridian (a jump of more than 180° between two
 * vertices) cut there into parts on either side, all within -180..180, e.g. for the Fiji Islands or the Chukotka.
 * Points get their longitude normalised. Geometries that do not cross are returned as they are.
 */
fun Geometry.splitAtAntimeridian(): Geometry {
    if (!crossesAntimeridian(this) && envelopeInternal.minX >= -180 && envelopeInternal.maxX <= 180) return this
    val parts = flatten().map { part ->
        when (part) {
            is Point -> factory.createPoint(org.locationtech.jts.geom.Coordinate(normalizeLongitude(part.x), part.y))
            is LineString, is Polygon -> cutAt180(unwrap(part))
            else -> part
        }
    }
    val pieces = parts.flatMap { it.flatten() }.filter { !it.isEmpty }
    return if (this is GeometryCollection || pieces.size != 1) factory.buildGeometry(pieces) else pieces[0]
}

private fun crossesAntimeridian(geometry: Geometry): Boolean {
    for (seq in geometry.coordinateSequences()) for (i in 1 until seq.size()) if (abs(seq.getX(i) - seq.getX(i - 1)) > 180) return true
    return false
}

/** The geometry with each sequence made continuous (jumps over 180° undone), holes moved next to their shell. */
private fun unwrap(geometry: Geometry): Geometry {
    val copy = geometry.copy()
    val sequences = copy.coordinateSequences()
    var reference = Double.NaN
    for (seq in sequences) {
        var offset = 0.0
        var previous = seq.getX(0)
        for (i in 0 until seq.size()) {
            val x = seq.getX(i)
            if (i > 0) {
                if (x - previous > 180) offset -= 360 else if (previous - x > 180) offset += 360
            }
            previous = x
            seq.setOrdinate(i, CoordinateSequence.X, x + offset)
        }
        // The holes (and the following parts of a line) next to the first sequence
        val centre = (0 until seq.size()).sumOf { seq.getX(it) } / seq.size()
        if (reference.isNaN()) reference = centre else {
            val shift = round((reference - centre) / 360) * 360
            if (shift != 0.0) for (i in 0 until seq.size()) seq.setOrdinate(i, CoordinateSequence.X, seq.getX(i) + shift)
        }
    }
    copy.geometryChanged()
    return copy
}

/** The parts of an unwrapped geometry in each 360° window, moved back within -180..180. */
private fun cutAt180(geometry: Geometry): Geometry {
    val env = geometry.envelopeInternal
    if (env.minX >= -180 && env.maxX <= 180) return geometry
    val factory = geometry.factory
    val pieces = ArrayList<Geometry>()
    var window = kotlin.math.floor((env.minX + 180) / 360).toInt()
    val last = kotlin.math.floor((env.maxX + 180) / 360).toInt()
    while (window <= last) {
        val minX = -180.0 + window * 360
        val box = factory.toGeometry(envelope(minX, env.minY - 1, minX + 360, env.maxY + 1))
        // Only the parts of the same dimension: a polygon touching the cut also gives a line
        for (piece in geometry.intersection(box).flatten()) {
            if (!piece.isEmpty && piece.dimension == geometry.dimension) pieces += piece.translated(-window * 360.0, 0.0)
        }
        window++
    }
    return factory.buildGeometry(pieces)
}

/**
 * The buffer of this geometry in degrees at [meters], computed in a local equirectangular projection around its
 * centre: accurate for distances small compared to the Earth (up to a few hundred kilometres) away from the poles.
 */
fun Geometry.bufferMeters(meters: Double, parameters: BufferScope.() -> Unit = {}): Geometry {
    val centre = envelopeInternal
    val lon0 = (centre.minX + centre.maxX) / 2
    val lat0 = (centre.minY + centre.maxY) / 2
    val k = EARTH_RADIUS * RADIANS
    val kx = k * cos(lat0 * RADIANS).coerceAtLeast(1e-6)
    val local = transformCoordinates { x = (x - lon0) * kx; y = (y - lat0) * k }
    return local.buffer(meters, parameters).transformCoordinates { x = x / kx + lon0; y = y / k + lat0 }
}
