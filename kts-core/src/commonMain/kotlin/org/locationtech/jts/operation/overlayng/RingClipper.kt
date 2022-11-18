/*
 * Copyright (c) 2019 Martin Davis.
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
package org.locationtech.jts.operation.overlayng

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.CoordinateList
import org.locationtech.jts.geom.Envelope

/**
 * Clips rings of points to a rectangle.
 * Uses a variant of Cohen-Sutherland clipping.
 *
 * In general the output is not topologically valid.
 * In particular, the output may contain coincident non-noded line segments
 * along the clip rectangle sides.
 * However, the output is sufficiently well-structured
 * that it can be used as input to the [OverlayNG] algorithm
 * (which is able to process coincident linework due
 * to the need to handle topology collapse under precision reduction).
 *
 * Because of the likelihood of creating
 * extraneous line segments along the clipping rectangle sides,
 * this class is not suitable for clipping linestrings.
 *
 * The clipping envelope should be generated using [RobustClipEnvelopeComputer],
 * to ensure that intersecting line segments are not perturbed
 * by clipping.
 * This is required to ensure that the overlay of the
 * clipped geometry is robust and correct (i.e. the same as
 * if clipping was not used).
 *
 * @see LineLimiter
 *
 * @author Martin Davis
 */
class RingClipper(private val clipEnv: Envelope) {
    private val clipEnvMinY: Double = clipEnv.minY
    private val clipEnvMaxY: Double
    private val clipEnvMinX: Double
    private val clipEnvMaxX: Double

    /**
     * Creates a new clipper for the given envelope.
     *
     * @param clipEnv the clipping envelope
     */
    init {
        clipEnvMaxY = clipEnv.maxY
        clipEnvMinX = clipEnv.minX
        clipEnvMaxX = clipEnv.maxX
    }

    /**
     * Clips a list of points to the clipping rectangle box.
     *
     * @param pts
     * @return clipped pts array
     */
    fun clip(pts: Array<Coordinate>): Array<Coordinate> {
        var pts = pts
        for (edgeIndex in 0..3) {
            val closeRing = edgeIndex == 3
            pts = clipToBoxEdge(pts, edgeIndex, closeRing)
            if (pts.isEmpty()) return pts
        }
        return pts
    }

    /**
     * Clips line to the axis-parallel line defined by a single box edge.
     *
     * @param pts
     * @param edgeIndex
     * @param closeRing
     * @return
     */
    private fun clipToBoxEdge(pts: Array<Coordinate>, edgeIndex: Int, closeRing: Boolean): Array<Coordinate> {
        // TODO: is it possible to avoid copying array 4 times?
        val ptsClip = CoordinateList()
        var p0 = pts[pts.size - 1]
        for (i in pts.indices) {
            val p1 = pts[i]
            if (isInsideEdge(p1, edgeIndex)) {
                if (!isInsideEdge(p0, edgeIndex)) {
                    val intPt = intersection(p0, p1, edgeIndex)
                    ptsClip.add(intPt, false)
                }
                // TODO: avoid copying so much?
                ptsClip.add(p1!!.copy(), false)
            } else if (isInsideEdge(p0, edgeIndex)) {
                val intPt = intersection(p0, p1, edgeIndex)
                ptsClip.add(intPt, false)
            }
            // else p0-p1 is outside box, so it is dropped
            p0 = p1
        }

        // add closing point if required
        if (closeRing && ptsClip.size > 0) {
            val start: Coordinate = ptsClip[0]
            if (!start.equals2D(ptsClip[ptsClip.size - 1])) {
                ptsClip.add(start.copy())
            }
        }
        return ptsClip.toCoordinateArray()
    }

    /**
     * Computes the intersection point of a segment
     * with an edge of the clip box.
     * The segment must be known to intersect the edge.
     *
     * @param a first endpoint of the segment
     * @param b second endpoint of the segment
     * @param edgeIndex index of box edge
     * @return the intersection point with the box edge
     */
    private fun intersection(a: Coordinate?, b: Coordinate?, edgeIndex: Int): Coordinate {
        val intPt: Coordinate = when (edgeIndex) {
            BOX_BOTTOM -> Coordinate(
                intersectionLineY(a, b, clipEnvMinY),
                clipEnvMinY
            )

            BOX_RIGHT -> Coordinate(
                clipEnvMaxX,
                intersectionLineX(a, b, clipEnvMaxX)
            )

            BOX_TOP -> Coordinate(
                intersectionLineY(a, b, clipEnvMaxY),
                clipEnvMaxY
            )

            BOX_LEFT -> Coordinate(
                clipEnvMinX,
                intersectionLineX(a, b, clipEnvMinX)
            )

            else -> Coordinate(clipEnvMinX, intersectionLineX(a, b, clipEnvMinX))
        }
        return intPt
    }

    private fun intersectionLineY(a: Coordinate?, b: Coordinate?, y: Double): Double {
        val m = (b!!.x - a!!.x) / (b.y - a.y)
        val intercept = (y - a.y) * m
        return a.x + intercept
    }

    private fun intersectionLineX(a: Coordinate?, b: Coordinate?, x: Double): Double {
        val m = (b!!.y - a!!.y) / (b.x - a.x)
        val intercept = (x - a.x) * m
        return a.y + intercept
    }

    private fun isInsideEdge(p: Coordinate?, edgeIndex: Int): Boolean {
        var isInside = false
        isInside = when (edgeIndex) {
            BOX_BOTTOM -> p!!.y > clipEnvMinY
            BOX_RIGHT -> p!!.x < clipEnvMaxX
            BOX_TOP -> p!!.y < clipEnvMaxY
            BOX_LEFT -> p!!.x > clipEnvMinX
            else -> p!!.x > clipEnvMinX
        }
        return isInside
    }

    companion object {
        private const val BOX_LEFT = 3
        private const val BOX_TOP = 2
        private const val BOX_RIGHT = 1
        private const val BOX_BOTTOM = 0
    }
}