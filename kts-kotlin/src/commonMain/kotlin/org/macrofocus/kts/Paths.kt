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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.locationtech.jts.geom.CoordinateSequence
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryCollection
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.Point
import org.locationtech.jts.geom.Polygon
import kotlin.math.abs

/**
 * Receives the outline of geometries, e.g. an adapter to a Compose `Path`, a Skia `Path` or a `GeneralPath`. The
 * rings of a polygon come as successive closed subpaths, the shell first: fill them with the even-odd rule.
 */
interface PathSink {
    fun moveTo(x: Double, y: Double)
    fun lineTo(x: Double, y: Double)

    /** Closes the current subpath (a ring). */
    fun close()

    /** A point geometry, which has no outline: ignored unless overridden. */
    fun point(x: Double, y: Double) {}
}

/** Maps world coordinates to view coordinates, writing the result in [out]. */
fun interface ViewTransform {
    fun transform(x: Double, y: Double, out: MutableXY)
}

/** The usual view transformation: `x * scaleX + translateX`, `y * scaleY + translateY` (scaleY < 0 flips north up). */
class ScaleTranslate(
    val scaleX: Double,
    val scaleY: Double,
    val translateX: Double = 0.0,
    val translateY: Double = 0.0,
) : ViewTransform {
    override fun transform(x: Double, y: Double, out: MutableXY) {
        out.x = x * scaleX + translateX
        out.y = y * scaleY + translateY
    }

    companion object {
        /** Maps [world] onto a view of [width] by [height], north up, keeping the aspect ratio, centred. */
        fun fit(world: Envelope, width: Double, height: Double): ScaleTranslate {
            val scale = minOf(width / world.width.takeIf { it > 0 }.orOne(), height / world.height.takeIf { it > 0 }.orOne())
            val cx = (world.minX + world.maxX) / 2
            val cy = (world.minY + world.maxY) / 2
            return ScaleTranslate(scale, -scale, width / 2 - cx * scale, height / 2 + cy * scale)
        }

        private fun Double?.orOne() = this ?: 1.0
    }
}

/**
 * Emits the outline of this geometry to [sink], read straight from its coordinate sequences (no Coordinate array is
 * copied), mapped by [transform] (none: world coordinates).
 *
 * @param tolerance in view units (pixels): a vertex closer than this to the last one emitted, along both axes, is
 * skipped, as are holes smaller than this. The last vertex of a line is always emitted, and a shell keeps at least two.
 * @param clip in world coordinates: the lines and polygons whose envelope does not intersect it are skipped.
 * @return the number of vertices emitted
 */
fun Geometry.emitPath(sink: PathSink, transform: ViewTransform? = null, tolerance: Double = 0.0, clip: Envelope? = null): Int =
    PathEmitter(sink, transform, tolerance, clip).emit(this)

private class PathEmitter(
    private val sink: PathSink,
    private val transform: ViewTransform?,
    private val tolerance: Double,
    private val clip: Envelope?,
) {
    private val xy = MutableXY()
    private val first = MutableXY()
    private var count = 0

    fun emit(geometry: Geometry): Int {
        visit(geometry)
        return count
    }

    private fun visit(geometry: Geometry) {
        if (geometry.isEmpty) return
        when (geometry) {
            is GeometryCollection -> for (i in 0 until geometry.numGeometries) visit(geometry.getGeometryN(i))
            is Point -> if (clip == null || clip.covers(geometry.x, geometry.y)) {
                map(geometry.x, geometry.y)
                sink.point(xy.x, xy.y)
                count++
            }
            is LineString -> if (visible(geometry)) {
                sequence(geometry.coordinateSequence!!, closed = geometry.isClosed && geometry.numPoints > 3, hole = false)
            }
            is Polygon -> if (visible(geometry)) {
                sequence(geometry.exteriorRing!!.coordinateSequence!!, closed = true, hole = false)
                for (i in 0 until geometry.getNumInteriorRing()) {
                    sequence(geometry.getInteriorRingN(i).coordinateSequence!!, closed = true, hole = true)
                }
            }
        }
    }

    private fun visible(geometry: Geometry) = clip == null || clip.intersects(geometry.envelopeInternal)

    private fun map(x: Double, y: Double) {
        if (transform == null) {
            xy.x = x
            xy.y = y
        } else transform.transform(x, y, xy)
    }

    private fun sequence(seq: CoordinateSequence, closed: Boolean, hole: Boolean) {
        // A ring repeats its first vertex at the end: close() draws that edge
        val n = if (closed) seq.size() - 1 else seq.size()
        if (n <= 0) return
        if (hole && tolerance > 0 && tooSmall(seq, n)) return
        map(seq.getX(0), seq.getY(0))
        sink.moveTo(xy.x, xy.y)
        first.x = xy.x
        first.y = xy.y
        var lastX = xy.x
        var lastY = xy.y
        var emitted = 1
        for (i in 1 until n) {
            map(seq.getX(i), seq.getY(i))
            val keep = tolerance <= 0 || abs(xy.x - lastX) >= tolerance || abs(xy.y - lastY) >= tolerance ||
                (!closed && i == n - 1) || (closed && i == n - 1 && emitted < 2)
            if (keep) {
                sink.lineTo(xy.x, xy.y)
                lastX = xy.x
                lastY = xy.y
                emitted++
            }
        }
        if (closed) sink.close()
        count += emitted
    }

    /** Whether the ring fits in a square of the tolerance in the view. */
    private fun tooSmall(seq: CoordinateSequence, n: Int): Boolean {
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        for (i in 0 until n) {
            map(seq.getX(i), seq.getY(i))
            minX = minOf(minX, xy.x); maxX = maxOf(maxX, xy.x)
            minY = minOf(minY, xy.y); maxY = maxOf(maxY, xy.y)
            if (maxX - minX >= tolerance || maxY - minY >= tolerance) return false
        }
        return true
    }
}

/**
 * A recorded path: its commands in a `ByteArray` and its coordinates in a `DoubleArray`. Recording happens once (off
 * the UI thread, see [toPackedPaths]) and [replay] costs a pass over two arrays per frame.
 */
class PackedPath internal constructor(private val commands: ByteArray, private val coordinates: DoubleArray) {
    val commandCount: Int get() = commands.size

    fun replay(sink: PathSink) {
        var c = 0
        for (command in commands) {
            when (command) {
                MOVE -> sink.moveTo(coordinates[c++], coordinates[c++])
                LINE -> sink.lineTo(coordinates[c++], coordinates[c++])
                CLOSE -> sink.close()
                POINT -> sink.point(coordinates[c++], coordinates[c++])
            }
        }
    }

    /** The bounds of the recorded coordinates (of the view, if recorded with a transformation). */
    val bounds: Envelope
        get() = Envelope().also { env -> for (i in coordinates.indices step 2) env.expandToInclude(coordinates[i], coordinates[i + 1]) }

    internal companion object {
        const val MOVE: Byte = 0
        const val LINE: Byte = 1
        const val CLOSE: Byte = 2
        const val POINT: Byte = 3
    }
}

/** A [PathSink] recording what it receives in a [PackedPath]. */
class PathRecorder : PathSink {
    private var commands = ByteArray(64)
    private var coordinates = DoubleArray(128)
    private var commandCount = 0
    private var coordinateCount = 0

    private fun add(command: Byte, x: Double, y: Double, withXY: Boolean) {
        if (commandCount == commands.size) commands = commands.copyOf(commandCount * 2)
        commands[commandCount++] = command
        if (withXY) {
            if (coordinateCount + 2 > coordinates.size) coordinates = coordinates.copyOf(coordinates.size * 2)
            coordinates[coordinateCount++] = x
            coordinates[coordinateCount++] = y
        }
    }

    override fun moveTo(x: Double, y: Double) = add(PackedPath.MOVE, x, y, true)
    override fun lineTo(x: Double, y: Double) = add(PackedPath.LINE, x, y, true)
    override fun close() = add(PackedPath.CLOSE, 0.0, 0.0, false)
    override fun point(x: Double, y: Double) = add(PackedPath.POINT, x, y, true)

    fun toPackedPath(): PackedPath = PackedPath(commands.copyOf(commandCount), coordinates.copyOf(coordinateCount))
}

/** The outline of this geometry recorded in a [PackedPath] (see [emitPath] for the parameters). */
fun Geometry.toPackedPath(transform: ViewTransform? = null, tolerance: Double = 0.0, clip: Envelope? = null): PackedPath =
    PathRecorder().also { emitPath(it, transform, tolerance, clip) }.toPackedPath()

/**
 * The [PackedPath] of each geometry, recorded concurrently on [dispatcher]: e.g. projecting and decimating the
 * regions of a map for a new zoom level without blocking the UI thread. [transform] must then be thread-safe (the
 * ones of this file are).
 */
suspend fun List<Geometry>.toPackedPaths(
    transform: ViewTransform? = null,
    tolerance: Double = 0.0,
    clip: Envelope? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    chunkSize: Int = 256,
): List<PackedPath> = coroutineScope {
    chunked(chunkSize).map { chunk ->
        async(dispatcher) { chunk.map { it.toPackedPath(transform, tolerance, clip) } }
    }.awaitAll().flatten()
}
