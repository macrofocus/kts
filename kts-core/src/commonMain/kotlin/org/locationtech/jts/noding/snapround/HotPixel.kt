/*
 * Copyright (c) 2016 Vivid Solutions.
 * Copyright (c) 2020 Macrofocus GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.noding.snapround

import org.locationtech.jts.algorithm.LineIntersector
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.legacy.Math
import org.locationtech.jts.noding.NodedSegmentString
import org.locationtech.jts.util.Assert.isTrue

/**
 * Implements a "hot pixel" as used in the Snap Rounding algorithm.
 * A hot pixel contains the interior of the tolerance square and
 * the boundary
 * **minus** the top and right segments.
 *
 * The hot pixel operations are all computed in the integer domain
 * to avoid rounding problems.
 *
 * @version 1.7
 */
class HotPixel(
    /**
     * Gets the coordinate this hot pixel is based at.
     *
     * @return the coordinate of the pixel
     */
    var coordinate: Coordinate, scaleFactor: Double, li: LineIntersector
) {
    // testing only
    //  public static int nTests = 0;
    private val li: LineIntersector = li
    private val pt: Coordinate? = null
    private val ptScaled: Coordinate? = null
    private var p0Scaled: Coordinate? = null
    private var p1Scaled: Coordinate? = null
    private val scaleFactor: Double = scaleFactor
    private var minx = 0.0
    private var maxx = 0.0
    private var miny = 0.0
    private var maxy = 0.0

    /**
     * The corners of the hot pixel, in the order:
     * 10
     * 23
     */
    private val corner = arrayOfNulls<Coordinate>(4)
    private var safeEnv: Envelope? = null

    /**
     * Returns a "safe" envelope that is guaranteed to contain the hot pixel.
     * The envelope returned will be larger than the exact envelope of the
     * pixel.
     *
     * @return an envelope which contains the hot pixel
     */
    val safeEnvelope: Envelope
        get() {
            if (safeEnv == null) {
                val safeTolerance = SAFE_ENV_EXPANSION_FACTOR / scaleFactor
                safeEnv = Envelope(
                    coordinate.x - safeTolerance,
                    coordinate.x + safeTolerance,
                    coordinate.y - safeTolerance,
                    coordinate.y + safeTolerance
                )
            }
            return safeEnv!!
        }

    private fun initCorners(pt: Coordinate) {
        val tolerance = 0.5
        minx = pt.x - tolerance
        maxx = pt.x + tolerance
        miny = pt.y - tolerance
        maxy = pt.y + tolerance
        corner[0] = Coordinate(maxx, maxy)
        corner[1] = Coordinate(minx, maxy)
        corner[2] = Coordinate(minx, miny)
        corner[3] = Coordinate(maxx, miny)
    }

    private fun scale(`val`: Double): Double {
        return Math.round(`val` * scaleFactor).toDouble()
    }

    /**
     * Tests whether the line segment (p0-p1)
     * intersects this hot pixel.
     *
     * @param p0 the first coordinate of the line segment to test
     * @param p1 the second coordinate of the line segment to test
     * @return true if the line segment intersects this hot pixel
     */
    fun intersects(p0: Coordinate, p1: Coordinate): Boolean {
        if (scaleFactor == 1.0) return intersectsScaled(p0, p1)
        copyScaled(p0, p0Scaled)
        copyScaled(p1, p1Scaled)
        return intersectsScaled(p0Scaled, p1Scaled)
    }

    private fun copyScaled(p: Coordinate, pScaled: Coordinate?) {
        pScaled!!.x = scale(p.x)
        pScaled.y = scale(p.y)
    }

    private fun intersectsScaled(p0: Coordinate?, p1: Coordinate?): Boolean {
        val segMinx = Math.min(p0!!.x, p1!!.x)
        val segMaxx = Math.max(p0.x, p1.x)
        val segMiny = Math.min(p0.y, p1.y)
        val segMaxy = Math.max(p0.y, p1.y)
        val isOutsidePixelEnv = maxx < segMinx || minx > segMaxx || maxy < segMiny || miny > segMaxy
        if (isOutsidePixelEnv) return false
        val intersects = intersectsToleranceSquare(p0, p1)
        //    boolean intersectsPixelClosure = intersectsPixelClosure(p0, p1);

//    if (intersectsPixel != intersects) {
//      Debug.println("Found hot pixel intersection mismatch at " + pt);
//      Debug.println("Test segment: " + p0 + " " + p1);
//    }

/*
    if (scaleFactor != 1.0) {
      boolean intersectsScaled = intersectsScaledTest(p0, p1);
      if (intersectsScaled != intersects) {
        intersectsScaledTest(p0, p1);
//        Debug.println("Found hot pixel scaled intersection mismatch at " + pt);
//        Debug.println("Test segment: " + p0 + " " + p1);
      }
      return intersectsScaled;
    }
*/isTrue(!(isOutsidePixelEnv && intersects), "Found bad envelope test")
        //    if (isOutsideEnv && intersects) {
//      Debug.println("Found bad envelope test");
//    }
        return intersects
        //return intersectsPixelClosure;
    }

    /**
     * Tests whether the segment p0-p1 intersects the hot pixel tolerance square.
     * Because the tolerance square point set is partially open (along the
     * top and right) the test needs to be more sophisticated than
     * simply checking for any intersection.
     * However, it can take advantage of the fact that the hot pixel edges
     * do not lie on the coordinate grid.
     * It is sufficient to check if any of the following occur:
     *
     *  * a proper intersection between the segment and any hot pixel edge
     *  * an intersection between the segment and **both** the left and bottom hot pixel edges
     * (which detects the case where the segment intersects the bottom left hot pixel corner)
     *  * an intersection between a segment endpoint and the hot pixel coordinate
     *
     * @param p0
     * @param p1
     * @return
     */
    private fun intersectsToleranceSquare(p0: Coordinate?, p1: Coordinate?): Boolean {
        var intersectsLeft = false
        var intersectsBottom = false
        //System.out.println("Hot Pixel: " + WKTWriter.toLineString(corner));
        //System.out.println("Line: " + WKTWriter.toLineString(p0, p1));
        li.computeIntersection(p0, p1, corner[0], corner[1])
        if (li.isProper) return true
        li.computeIntersection(p0, p1, corner[1], corner[2])
        if (li.isProper) return true
        if (li.hasIntersection()) intersectsLeft = true
        li.computeIntersection(p0, p1, corner[2], corner[3])
        if (li.isProper) return true
        if (li.hasIntersection()) intersectsBottom = true
        li.computeIntersection(p0, p1, corner[3], corner[0])
        if (li.isProper) return true
        if (intersectsLeft && intersectsBottom) return true
        if (p0!! == coordinate) return true
        return p1!! == coordinate
    }

    /**
     * Test whether the given segment intersects
     * the closure of this hot pixel.
     * This is NOT the test used in the standard snap-rounding
     * algorithm, which uses the partially closed tolerance square
     * instead.
     * This routine is provided for testing purposes only.
     *
     * @param p0 the start point of a line segment
     * @param p1 the end point of a line segment
     * @return `true` if the segment intersects the closure of the pixel's tolerance square
     */
    private fun intersectsPixelClosure(p0: Coordinate, p1: Coordinate): Boolean {
        li.computeIntersection(p0, p1, corner[0], corner[1])
        if (li.hasIntersection()) return true
        li.computeIntersection(p0, p1, corner[1], corner[2])
        if (li.hasIntersection()) return true
        li.computeIntersection(p0, p1, corner[2], corner[3])
        if (li.hasIntersection()) return true
        li.computeIntersection(p0, p1, corner[3], corner[0])
        return li.hasIntersection()
    }

    /**
     * Adds a new node (equal to the snap pt) to the specified segment
     * if the segment passes through the hot pixel
     *
     * @param segStr
     * @param segIndex
     * @return true if a node was added to the segment
     */
    fun addSnappedNode(
        segStr: NodedSegmentString,
        segIndex: Int
    ): Boolean {
        val p0 = segStr.getCoordinate(segIndex)
        val p1 = segStr.getCoordinate(segIndex + 1)
        if (intersects(p0, p1)) {
            //System.out.println("snapped: " + snapPt);
            //System.out.println("POINT (" + snapPt.x + " " + snapPt.y + ")");
            segStr.addIntersection(coordinate, segIndex)
            return true
        }
        return false
    }

    companion object {
        private const val SAFE_ENV_EXPANSION_FACTOR = 0.75
    }

    /**
     * Creates a new hot pixel, using a given scale factor.
     * The scale factor must be strictly positive (non-zero).
     *
     * @param pt the coordinate at the centre of the pixel
     * @param scaleFactor the scaleFactor determining the pixel size.  Must be &gt; 0
     * @param li the intersector to use for testing intersection with line segments
     */
    init {
        //tolerance = 0.5;
        require(scaleFactor > 0) { "Scale factor must be non-zero" }
        if (scaleFactor != 1.0) {
            coordinate = Coordinate(scale(coordinate.x), scale(coordinate.y))
            p0Scaled = Coordinate()
            p1Scaled = Coordinate()
        }
        initCorners(coordinate)
    }
}