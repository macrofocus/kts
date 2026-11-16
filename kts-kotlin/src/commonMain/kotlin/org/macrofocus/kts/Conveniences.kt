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

import org.locationtech.jts.algorithm.construct.MaximumInscribedCircle
import org.locationtech.jts.algorithm.distance.DiscreteHausdorffDistance
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.Point
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.Polygonal
import org.locationtech.jts.linearref.LengthIndexedLine

// --- User data ----------------------------------------------------------------------------------------------------

/** The user data of this geometry if it is a [T], else null: `region.userData<Place>()`. */
inline fun <reified T> Geometry.userData(): T? = getUserData() as? T

/** This geometry with [data] as its user data. */
fun <G : Geometry> G.withUserData(data: Any?): G = apply { setUserData(data) }

// --- Labels -------------------------------------------------------------------------------------------------------

/**
 * Where to put the label of this geometry: for polygons the centre of the largest circle inside the largest polygon
 * (the pole of inaccessibility, inside even for a crescent or a ring, unlike the centroid), to within [tolerance]; for
 * lines the middle of the longest one; for points the one closest to their centroid. Null when it is empty.
 *
 * @param tolerance how close to the best centre, by default a thousandth of the size of the polygon
 */
fun Geometry.labelPoint(tolerance: Double = Double.NaN): Point? {
    if (isEmpty) return null
    return when {
        this is Polygonal -> {
            val largest = extract<Polygon>().filter { !it.isEmpty }.maxByOrNull { it.area } ?: return null
            val env = largest.envelopeInternal
            val t = if (tolerance.isNaN()) maxOf(env.width, env.height) / 1000 else tolerance
            if (t <= 0) largest.interiorPoint else MaximumInscribedCircle.getCenter(largest, t)
        }
        dimension == 1 -> {
            val longest = extract<LineString>().maxByOrNull { it.length } ?: return null
            factory.createPoint(LengthIndexedLine(longest).extractPoint(longest.length / 2))
        }
        dimension == 0 -> {
            val centroid = centroid
            extract<Point>().filter { !it.isEmpty }.minByOrNull { it.distance(centroid) }
        }
        // A mixed collection: the label of its parts of the highest dimension
        else -> factory.buildGeometry(flatten().filter { it.dimension == dimension }).labelPoint(tolerance)
    }
}

// --- Comparisons --------------------------------------------------------------------------------------------------

/**
 * Whether the two geometries have the same structure and vertices to within [tolerance], whatever the order of their
 * components, of the vertices of their rings and the direction of their lines: what tests usually mean by equal.
 */
fun Geometry.equalsNormalized(other: Geometry, tolerance: Double = 0.0): Boolean = norm().equalsExact(other.norm(), tolerance)

/**
 * How far apart the two geometries are as shapes: the largest distance from a vertex of one to the other (discrete
 * Hausdorff distance). 0 for the same shape, whatever its vertices; a tolerance for comparing results of operations.
 */
fun Geometry.shapeDistance(other: Geometry): Double = DiscreteHausdorffDistance.distance(this, other)
