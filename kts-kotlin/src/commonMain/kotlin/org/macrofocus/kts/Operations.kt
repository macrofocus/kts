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

import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.util.AffineTransformation
import org.locationtech.jts.geom.util.GeometryFixer
import org.locationtech.jts.io.WKBReader
import org.locationtech.jts.io.WKTReader
import org.locationtech.jts.io.WKTWriter
import org.locationtech.jts.operation.buffer.BufferOp
import org.locationtech.jts.operation.buffer.BufferParameters
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier
import org.locationtech.jts.simplify.TopologyPreservingSimplifier

// --- Buffer -------------------------------------------------------------------------------------------------------

enum class EndCap(internal val code: Int) {
    Round(BufferParameters.CAP_ROUND), Flat(BufferParameters.CAP_FLAT), Square(BufferParameters.CAP_SQUARE)
}

enum class Join(internal val code: Int) {
    Round(BufferParameters.JOIN_ROUND), Mitre(BufferParameters.JOIN_MITRE), Bevel(BufferParameters.JOIN_BEVEL)
}

/** The parameters of a buffer, see [buffer]. */
@GeometryDsl
class BufferScope internal constructor() {
    internal val parameters = BufferParameters()

    /** The number of segments approximating a quarter circle (8 by default). */
    var quadrantSegments: Int
        get() = parameters.getQuadrantSegments()
        set(value) = parameters.setQuadrantSegments(value)

    var endCap: EndCap
        get() = EndCap.entries.first { it.code == parameters.getEndCapStyle() }
        set(value) = parameters.setEndCapStyle(value.code)

    var join: Join
        get() = Join.entries.first { it.code == parameters.getJoinStyle() }
        set(value) = parameters.setJoinStyle(value.code)

    /** How far a mitre join may extend, as a multiple of the distance (5 by default). */
    var mitreLimit: Double
        get() = parameters.getMitreLimit()
        set(value) = parameters.setMitreLimit(value)

    /** Whether lines are buffered on one side only: the left one for a positive distance. */
    var singleSided: Boolean
        get() = parameters.isSingleSided
        set(value) {
            parameters.isSingleSided = value
        }

    /** How much the input is simplified, as a fraction of the distance (0.01 by default). */
    var simplifyFactor: Double
        get() = parameters.simplifyFactor
        set(value) {
            parameters.simplifyFactor = value
        }
}

/**
 * The buffer of this geometry at [distance]:
 * ```
 * road.buffer(5.0) { endCap = EndCap.Flat; join = Join.Mitre }
 * ```
 */
fun Geometry.buffer(distance: Double, parameters: BufferScope.() -> Unit): Geometry =
    BufferOp(this, BufferScope().apply(parameters).parameters).getResultGeometry(distance)

// --- Affine transformations ---------------------------------------------------------------------------------------

/**
 * This geometry transformed by the composition of the transformations of [block], applied in their order:
 * ```
 * shape.transform { translate(-cx, -cy); rotate(PI / 4); scale(2.0, 2.0); translate(cx, cy) }
 * ```
 */
fun Geometry.transform(block: AffineTransformation.() -> Unit): Geometry = AffineTransformation().apply(block).transform(this)

fun Geometry.translated(dx: Double, dy: Double): Geometry = AffineTransformation().translate(dx, dy).transform(this)

/** Rotated by [theta] radians, counter-clockwise, around ([x], [y]). */
fun Geometry.rotated(theta: Double, x: Double = 0.0, y: Double = 0.0): Geometry =
    AffineTransformation().rotate(theta, x, y).transform(this)

fun Geometry.scaled(xScale: Double, yScale: Double = xScale): Geometry =
    AffineTransformation().scale(xScale, yScale).transform(this)

// --- Simplification and repair ------------------------------------------------------------------------------------

/**
 * A simplified copy of this geometry, no vertex farther than [tolerance] from the original. Unless [preserveTopology]
 * is false (Douglas-Peucker, faster), rings do not cross each other or collapse.
 */
fun Geometry.simplify(tolerance: Double, preserveTopology: Boolean = true): Geometry =
    if (preserveTopology) TopologyPreservingSimplifier.simplify(this, tolerance)
    else DouglasPeuckerSimplifier.simplify(this, tolerance)

/** This geometry if it is valid, else a valid geometry as close to it as possible (see GeometryFixer). */
fun Geometry.fixed(keepMulti: Boolean = false): Geometry = if (isValid) this else GeometryFixer.fix(this, keepMulti)

// --- Well-known text and binary -----------------------------------------------------------------------------------

/** The geometry of a well-known text, e.g. `wkt("POINT (1 2)")`. */
fun wkt(text: String, factory: GeometryFactory = DefaultGeometryFactory): Geometry = WKTReader(factory).read(text)

/** The geometry of a well-known binary. */
fun wkb(bytes: ByteArray, factory: GeometryFactory = DefaultGeometryFactory): Geometry =
    requireNotNull(WKBReader(factory).read(bytes)) { "No geometry in ${bytes.size} bytes of WKB" }

/** The geometry of a well-known binary in hexadecimal, as PostGIS prints it. */
fun wkbHex(hex: String, factory: GeometryFactory = DefaultGeometryFactory): Geometry = wkb(WKBReader.hexToBytes(hex), factory)

/** The well-known text of this geometry, indented over several lines when [formatted]. */
fun Geometry.toWkt(formatted: Boolean = false): String =
    // Up to XYZM: the writer leaves out the ordinates the geometry does not have
    WKTWriter(4).let { if (formatted) it.writeFormatted(this) else it.write(this) }
