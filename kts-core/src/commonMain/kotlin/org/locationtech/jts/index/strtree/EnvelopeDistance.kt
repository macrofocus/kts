/*
 * Copyright (c) 2019 Martin Davis.
 * Copyright (c) 2020 Macrofocus GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.index.strtree

import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.legacy.Math
import kotlin.jvm.JvmStatic

/**
 * Functions for computing distances between [Envelope]s.
 *
 * @author mdavis
 */
object EnvelopeDistance {
    /**
     * Computes the maximum distance between the points defining two envelopes.
     * It is equal to the length of the diagonal of
     * the envelope containing both input envelopes.
     * This is a coarse upper bound on the distance between
     * geometries bounded by the envelopes.
     *
     * @param env1 an envelope
     * @param env2 an envelope
     * @return the maximum distance between the points defining the envelopes
     */
    @JvmStatic
    fun maximumDistance(env1: Envelope, env2: Envelope): Double {
        val minx = Math.min(env1.minX, env2.minX)
        val miny = Math.min(env1.minY, env2.minY)
        val maxx = Math.max(env1.maxX, env2.maxX)
        val maxy = Math.max(env1.maxY, env2.maxY)
        return distance(minx, miny, maxx, maxy)
    }

    private fun distance(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        return Math.sqrt(dx * dx + dy * dy)
    }

    /**
     * Computes the Min-Max Distance between two [Envelope]s.
     * It is equal to the minimum of the maximum distances between all pairs of
     * edge segments from the two envelopes.
     * This is the tight upper bound on the distance between
     * geometric items bounded by the envelopes.
     *
     * Theoretically this bound can be used in the R-tree nearest-neighbour branch-and-bound search
     * instead of [.maximumDistance].
     * However, little performance improvement is observed in practice.
     *
     * @param a an envelope
     * @param b an envelope
     * @return the min-max-distance between the envelopes
     */
    fun minMaxDistance(a: Envelope, b: Envelope): Double {
        val aminx = a.minX
        val aminy = a.minY
        val amaxx = a.maxX
        val amaxy = a.maxY
        val bminx = b.minX
        val bminy = b.minY
        val bmaxx = b.maxX
        val bmaxy = b.maxY
        var dist = maxDistance(aminx, aminy, aminx, amaxy, bminx, bminy, bminx, bmaxy)
        dist = Math.min(dist, maxDistance(aminx, aminy, aminx, amaxy, bminx, bminy, bmaxx, bminy))
        dist = Math.min(dist, maxDistance(aminx, aminy, aminx, amaxy, bmaxx, bmaxy, bminx, bmaxy))
        dist = Math.min(dist, maxDistance(aminx, aminy, aminx, amaxy, bmaxx, bmaxy, bmaxx, bminy))
        dist = Math.min(dist, maxDistance(aminx, aminy, amaxx, aminy, bminx, bminy, bminx, bmaxy))
        dist = Math.min(dist, maxDistance(aminx, aminy, amaxx, aminy, bminx, bminy, bmaxx, bminy))
        dist = Math.min(dist, maxDistance(aminx, aminy, amaxx, aminy, bmaxx, bmaxy, bminx, bmaxy))
        dist = Math.min(dist, maxDistance(aminx, aminy, amaxx, aminy, bmaxx, bmaxy, bmaxx, bminy))
        dist = Math.min(dist, maxDistance(amaxx, amaxy, aminx, amaxy, bminx, bminy, bminx, bmaxy))
        dist = Math.min(dist, maxDistance(amaxx, amaxy, aminx, amaxy, bminx, bminy, bmaxx, bminy))
        dist = Math.min(dist, maxDistance(amaxx, amaxy, aminx, amaxy, bmaxx, bmaxy, bminx, bmaxy))
        dist = Math.min(dist, maxDistance(amaxx, amaxy, aminx, amaxy, bmaxx, bmaxy, bmaxx, bminy))
        dist = Math.min(dist, maxDistance(amaxx, amaxy, amaxx, aminy, bminx, bminy, bminx, bmaxy))
        dist = Math.min(dist, maxDistance(amaxx, amaxy, amaxx, aminy, bminx, bminy, bmaxx, bminy))
        dist = Math.min(dist, maxDistance(amaxx, amaxy, amaxx, aminy, bmaxx, bmaxy, bminx, bmaxy))
        dist = Math.min(dist, maxDistance(amaxx, amaxy, amaxx, aminy, bmaxx, bmaxy, bmaxx, bminy))
        return dist
    }

    /**
     * Computes the maximum distance between two line segments.
     *
     * @param ax1 x ordinate of first endpoint of segment 1
     * @param ay1 y ordinate of first endpoint of segment 1
     * @param ax2 x ordinate of second endpoint of segment 1
     * @param ay2 y ordinate of second endpoint of segment 1
     * @param bx1 x ordinate of first endpoint of segment 2
     * @param by1 y ordinate of first endpoint of segment 2
     * @param bx2 x ordinate of second endpoint of segment 2
     * @param by2 y ordinate of second endpoint of segment 2
     * @return maximum distance between the segments
     */
    private fun maxDistance(
        ax1: Double, ay1: Double, ax2: Double, ay2: Double,
        bx1: Double, by1: Double, bx2: Double, by2: Double
    ): Double {
        var dist = distance(ax1, ay1, bx1, by1)
        dist = Math.max(dist, distance(ax1, ay1, bx2, by2))
        dist = Math.max(dist, distance(ax2, ay2, bx1, by1))
        dist = Math.max(dist, distance(ax2, ay2, bx2, by2))
        return dist
    }
}