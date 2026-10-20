/*
 * Copyright (c) 2022 Martin Davis.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.coverage

import org.locationtech.jts.algorithm.PolygonNodeTopology
import org.locationtech.jts.algorithm.RobustLineIntersector
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.LineSegment
import org.locationtech.jts.noding.SegmentIntersector
import org.locationtech.jts.noding.SegmentString

/**
 * Detects invalid coverage topology where ring segments interact.
 * The inputs to [processIntersections]
 * must be [CoverageRing]s.
 * If an invalid situation is detected the input target segment is
 * marked invalid using [CoverageRing.markInvalid].
 *
 * This class assumes it is used with [SegmentSetMutualIntersector],
 * so that segments in the same ring are not evaluated.
 *
 * @author Martin Davis
 */
class InvalidSegmentDetector : SegmentIntersector {

    private var distanceTol = 0.0

    /**
     * Creates an invalid segment detector.
     */
    constructor()

    constructor(distanceTol: Double) {
        this.distanceTol = distanceTol
    }

    /**
     * Process interacting segments.
     * The input order is important.
     * The adjacent segment is first, the target is second.
     * The inputs must be [CoverageRing]s.
     */
    override fun processIntersections(ssAdj: SegmentString, iAdj: Int, ssTarget: SegmentString, iTarget: Int) {
        // note the source of the edges is important
        val target = ssTarget as CoverageRing
        val adj = ssAdj as CoverageRing

        //-- Assert: rings are not equal (because this is used with SegmentSetMutualIntersector)

        //-- skip target segments with known status
        if (target.isKnown(iTarget)) return

        val t0 = target.getCoordinate(iTarget)
        val t1 = target.getCoordinate(iTarget + 1)
        val adj0 = adj.getCoordinate(iAdj)
        val adj1 = adj.getCoordinate(iAdj + 1)
        // System.out.println("checking target= " + WKTWriter.toLineString(t0, t1)
        //  + "   adj= " + WKTWriter.toLineString(adj0, adj1));

        //-- skip zero-length segments
        if (t0.equals2D(t1) || adj0.equals2D(adj1)) return
        if (isEqual(t0, t1, adj0, adj1)) return

        /*
        //-- skip segments beyond distance tolerance
        if (distanceTol < Distance.segmentToSegment(t0, t1, adj0, adj1)) {
          return;
        }
        */

        val isInvalid = isInvalid(t0, t1, adj0, adj1, adj, iAdj)
        if (isInvalid) {
            target.markInvalid(iTarget)
        }
    }

    private fun isEqual(t0: Coordinate, t1: Coordinate, adj0: Coordinate, adj1: Coordinate): Boolean {
        if (t0.equals2D(adj0) && t1.equals2D(adj1)) return true
        if (t0.equals2D(adj1) && t1.equals2D(adj0)) return true
        return false
    }

    private fun isInvalid(
        tgt0: Coordinate, tgt1: Coordinate,
        adj0: Coordinate, adj1: Coordinate, adj: CoverageRing, indexAdj: Int
    ): Boolean {
        //-- segments that are collinear (but not matching) or are interior are invalid
        if (isCollinearOrInterior(tgt0, tgt1, adj0, adj1, adj, indexAdj)) return true

        //-- segments which are nearly parallel for a significant length are invalid
        if (distanceTol > 0 && isNearlyParallel(tgt0, tgt1, adj0, adj1, distanceTol)) return true

        return false
    }

    /**
     * Checks if the segments are collinear, or if the target segment
     * intersects the interior of the adjacent ring.
     * Segments which are collinear must be non-equal and hence invalid,
     * since matching segments have already been marked as valid and
     * are not passed to this code.
     *
     * @param tgt0
     * @param tgt1
     * @param adj0
     * @param adj1
     * @return
     */
    private fun isCollinearOrInterior(
        tgt0: Coordinate, tgt1: Coordinate,
        adj0: Coordinate, adj1: Coordinate, adj: CoverageRing, indexAdj: Int
    ): Boolean {
        val li = RobustLineIntersector()
        li.computeIntersection(tgt0, tgt1, adj0, adj1)

        //-- segments do not interact
        if (!li.hasIntersection()) return false

        //-- If the segments are collinear, they do not match, so are invalid.
        if (li.intersectionNum == 2) {
            //TODO: assert segments are not equal?
            return true
        }

        //-- target segment crosses, or segments touch at non-endpoint
        if (li.isProper || li.isInteriorIntersection) {
            return true
        }

        /**
         * At this point the segments have a single intersection point
         * which is an endpoint of both segments.
         *
         * Check if the target segment lies in the interior of the adj ring.
         */
        val intVertex = li.getIntersection(0)
        return isInteriorSegment(intVertex, tgt0, tgt1, adj, indexAdj)
    }

    private fun isInteriorSegment(
        intVertex: Coordinate, tgt0: Coordinate, tgt1: Coordinate,
        adj: CoverageRing, indexAdj: Int
    ): Boolean {
        //-- find target segment endpoint which is not the intersection point
        val tgtEnd = if (intVertex.equals2D(tgt0)) tgt1 else tgt0

        //-- find adjacent-ring vertices on either side of intersection vertex
        var adjPrev = adj.findVertexPrev(indexAdj, intVertex)
        var adjNext = adj.findVertexNext(indexAdj, intVertex)

        //-- don't check if test segment is equal to either corner segment
        if (tgtEnd.equals2D(adjPrev) || tgtEnd.equals2D(adjNext)) {
            return false
        }

        //-- if needed, re-orient corner to have interior on right
        if (!adj.isInteriorOnRight) {
            val temp = adjPrev
            adjPrev = adjNext
            adjNext = temp
        }

        return PolygonNodeTopology.isInteriorSegment(intVertex, adjPrev, adjNext, tgtEnd)
    }

    override val isDone: Boolean
        // process all intersections
        get() = false

    companion object {
        private fun isNearlyParallel(
            p00: Coordinate, p01: Coordinate,
            p10: Coordinate, p11: Coordinate, distanceTol: Double
        ): Boolean {
            val line0 = LineSegment(p00, p01)
            val line1 = LineSegment(p10, p11)
            val proj0 = line0.project(line1) ?: return false
            val proj1 = line1.project(line0) ?: return false

            if (proj0.length <= distanceTol
                || proj1.length <= distanceTol
            ) return false

            if (proj0.p0.distance(proj1.p1) < proj0.p0.distance(proj1.p0)) {
                proj1.reverse()
            }
            return proj0.p0.distance(proj1.p0) <= distanceTol
                    && proj0.p1.distance(proj1.p1) <= distanceTol
        }
    }
}
