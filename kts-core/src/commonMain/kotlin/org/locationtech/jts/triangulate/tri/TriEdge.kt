/*
 * Copyright (c) 2021 Martin Davis.
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.triangulate.tri

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.io.WKTWriter

/**
 * Represents an edge in a [Tri],
 * to be used as a key for looking up Tris
 * while building a triangulation.
 * The edge value is normalized to allow lookup
 * of adjacent triangles.
 *
 * @author mdavis
 */
internal class TriEdge(var p0: Coordinate, var p1: Coordinate) {
    init {
        normalize()
    }

    private fun normalize() {
        if (p0 < p1) {
            val tmp = p0
            p0 = p1
            p1 = tmp
        }
    }

    override fun hashCode(): Int {
        var result = 17
        result = 37 * result + Coordinate.hashCode(p0.x)
        result = 37 * result + Coordinate.hashCode(p1.x)
        result = 37 * result + Coordinate.hashCode(p0.y)
        result = 37 * result + Coordinate.hashCode(p1.y)
        return result
    }

    override fun equals(arg: Any?): Boolean {
        if (arg !is TriEdge) return false
        val other = arg
        return p0 == other.p0 && p1 == other.p1
    }

    override fun toString(): String {
        return WKTWriter.toLineString(arrayOf(p0, p1))
    }
}