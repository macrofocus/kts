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
package org.locationtech.jts.algorithm

import org.locationtech.jts.algorithm.Distance.pointToSegment
import org.locationtech.jts.algorithm.Orientation.index
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Envelope.Companion.intersects

/**
 * @version 1.7
 */
/**
 * A robust version of [LineIntersector].
 *
 * @version 1.7
 */
class RobustLineIntersector : LineIntersector() {
    override fun computeIntersection(p: Coordinate?, p1: Coordinate?, p2: Coordinate?) {
        isProper = false
        // do between check first, since it is faster than the orientation test
        if (intersects(p1!!, p2!!, p!!)) {
            if (index(p1, p2, p) == 0
                && index(p2, p1, p) == 0
            ) {
                isProper = true
                if (p == p1 || p == p2) {
                    isProper = false
                }
                intersectionNum = POINT_INTERSECTION
                return
            }
        }
        intersectionNum = NO_INTERSECTION
    }

    override fun computeIntersect(
        p1: Coordinate?, p2: Coordinate?,
        q1: Coordinate?, q2: Coordinate?
    ): Int {
        isProper = false

        // first try a fast test to see if the envelopes of the lines intersect
        if (!intersects(p1!!, p2!!, q1!!, q2!!)) return NO_INTERSECTION

        // for each endpoint, compute which side of the other segment it lies
        // if both endpoints lie on the same side of the other segment,
        // the segments do not intersect
        val Pq1 = index(p1, p2, q1)
        val Pq2 = index(p1, p2, q2)
        if (Pq1 > 0 && Pq2 > 0 || Pq1 < 0 && Pq2 < 0) {
            return NO_INTERSECTION
        }
        val Qp1 = index(q1, q2, p1)
        val Qp2 = index(q1, q2, p2)
        if (Qp1 > 0 && Qp2 > 0 || Qp1 < 0 && Qp2 < 0) {
            return NO_INTERSECTION
        }
        val collinear = Pq1 == 0 && Pq2 == 0 && Qp1 == 0 && Qp2 == 0
        if (collinear) {
            return computeCollinearIntersection(p1, p2, q1, q2)
        }
        /**
         * At this point we know that there is a single intersection point
         * (since the lines are not collinear).
         */
        /**
         * Check if the intersection is an endpoint. If it is, copy the endpoint as
         * the intersection point. Copying the point rather than computing it
         * ensures the point has the exact value, which is important for
         * robustness. It is sufficient to simply check for an endpoint which is on
         * the other line, since at this point we know that the inputLines must
         * intersect.
         */
        if (Pq1 == 0 || Pq2 == 0 || Qp1 == 0 || Qp2 == 0) {
            isProper = false
            /**
             * Check for two equal endpoints.
             * This is done explicitly rather than by the orientation tests
             * below in order to improve robustness.
             *
             * [An example where the orientation tests fail to be consistent is
             * the following (where the true intersection is at the shared endpoint
             * POINT (19.850257749638203 46.29709338043669)
             *
             * LINESTRING ( 19.850257749638203 46.29709338043669, 20.31970698357233 46.76654261437082 )
             * and
             * LINESTRING ( -48.51001596420236 -22.063180333403878, 19.850257749638203 46.29709338043669 )
             *
             * which used to produce the INCORRECT result: (20.31970698357233, 46.76654261437082, NaN)
             *
             */
            if (p1.equals2D(q1)
                || p1.equals2D(q2)
            ) {
                intPt[0] = p1
            } else if (p2.equals2D(q1)
                || p2.equals2D(q2)
            ) {
                intPt[0] = p2
            } else if (Pq1 == 0) {
                intPt[0] = Coordinate(q1)
            } else if (Pq2 == 0) {
                intPt[0] = Coordinate(q2)
            } else if (Qp1 == 0) {
                intPt[0] = Coordinate(p1)
            } else if (Qp2 == 0) {
                intPt[0] = Coordinate(p2)
            }
        } else {
            isProper = true
            intPt[0] = intersection(p1, p2, q1, q2)
        }
        return POINT_INTERSECTION
    }

    private fun computeCollinearIntersection(
        p1: Coordinate?, p2: Coordinate?,
        q1: Coordinate?, q2: Coordinate?
    ): Int {
        val p1q1p2 = intersects(p1!!, p2!!, q1!!)
        val p1q2p2 = intersects(p1, p2, q2!!)
        val q1p1q2 = intersects(q1, q2, p1)
        val q1p2q2 = intersects(q1, q2, p2)
        if (p1q1p2 && p1q2p2) {
            intPt[0] = q1
            intPt[1] = q2
            return COLLINEAR_INTERSECTION
        }
        if (q1p1q2 && q1p2q2) {
            intPt[0] = p1
            intPt[1] = p2
            return COLLINEAR_INTERSECTION
        }
        if (p1q1p2 && q1p1q2) {
            intPt[0] = q1
            intPt[1] = p1
            return if (q1 == p1 && !p1q2p2 && !q1p2q2) POINT_INTERSECTION else COLLINEAR_INTERSECTION
        }
        if (p1q1p2 && q1p2q2) {
            intPt[0] = q1
            intPt[1] = p2
            return if (q1 == p2 && !p1q2p2 && !q1p1q2) POINT_INTERSECTION else COLLINEAR_INTERSECTION
        }
        if (p1q2p2 && q1p1q2) {
            intPt[0] = q2
            intPt[1] = p1
            return if (q2 == p1 && !p1q1p2 && !q1p2q2) POINT_INTERSECTION else COLLINEAR_INTERSECTION
        }
        if (p1q2p2 && q1p2q2) {
            intPt[0] = q2
            intPt[1] = p2
            return if (q2 == p2 && !p1q1p2 && !q1p1q2) POINT_INTERSECTION else COLLINEAR_INTERSECTION
        }
        return NO_INTERSECTION
    }

    /**
     * This method computes the actual value of the intersection point.
     * To obtain the maximum precision from the intersection calculation,
     * the coordinates are normalized by subtracting the minimum
     * ordinate values (in absolute value).  This has the effect of
     * removing common significant digits from the calculation to
     * maintain more bits of precision.
     */
    private fun intersection(
        p1: Coordinate?, p2: Coordinate?, q1: Coordinate?, q2: Coordinate?
    ): Coordinate? {
        var intPt = intersectionSafe(p1, p2, q1, q2)

        /*
    // TESTING ONLY
    Coordinate intPtDD = CGAlgorithmsDD.intersection(p1, p2, q1, q2);
    double dist = intPt.distance(intPtDD);
    System.out.println(intPt + " - " + intPtDD + " dist = " + dist);
    //intPt = intPtDD;
    */
        /**
         * Due to rounding it can happen that the computed intersection is
         * outside the envelopes of the input segments.  Clearly this
         * is inconsistent.
         * This code checks this condition and forces a more reasonable answer
         *
         * MD - May 4 2005 - This is still a problem.  Here is a failure case:
         *
         * LINESTRING (2089426.5233462777 1180182.3877339689, 2085646.6891757075 1195618.7333999649)
         * LINESTRING (1889281.8148903656 1997547.0560044837, 2259977.3672235999 483675.17050843034)
         * int point = (2097408.2633752143,1144595.8008114607)
         *
         * MD - Dec 14 2006 - This does not seem to be a failure case any longer
         */
        if (!isInSegmentEnvelopes(intPt)) {
//      System.out.println("Intersection outside segment envelopes: " + intPt);

            // compute a safer result
            // copy the coordinate, since it may be rounded later
            intPt = Coordinate(nearestEndpoint(p1, p2, q1, q2)!!)
            //    intPt = CentralEndpointIntersector.getIntersection(p1, p2, q1, q2);

//      System.out.println("Segments: " + this);
//      System.out.println("Snapped to " + intPt);
//      checkDD(p1, p2, q1, q2, intPt);
        }
        if (precisionModel != null) {
            precisionModel!!.makePrecise(intPt!!)
        }
        return intPt
    }

    private fun checkDD(
        p1: Coordinate, p2: Coordinate, q1: Coordinate,
        q2: Coordinate, intPt: Coordinate
    ) {
        val intPtDD = CGAlgorithmsDD.intersection(p1, p2, q1, q2)
        val isIn = isInSegmentEnvelopes(intPtDD)
        println("DD in env = $isIn  --------------------- $intPtDD")
        if (intPt.distance(intPtDD!!) > 0.0001) {
            println("Distance = " + intPt.distance(intPtDD))
        }
    }

    /**
     * Computes a segment intersection using homogeneous coordinates.
     * Round-off error can cause the raw computation to fail,
     * (usually due to the segments being approximately parallel).
     * If this happens, a reasonable approximation is computed instead.
     *
     * @param p1 a segment endpoint
     * @param p2 a segment endpoint
     * @param q1 a segment endpoint
     * @param q2 a segment endpoint
     * @return the computed intersection point
     */
    private fun intersectionSafe(p1: Coordinate?, p2: Coordinate?, q1: Coordinate?, q2: Coordinate?): Coordinate? {
        var intPt = Intersection.intersection(p1!!, p2!!, q1!!, q2!!)
        if (intPt == null) intPt = nearestEndpoint(p1, p2, q1, q2)
        //     System.out.println("Snapped to " + intPt);
        return intPt
    }

    /**
     * Tests whether a point lies in the envelopes of both input segments.
     * A correctly computed intersection point should return `true`
     * for this test.
     * Since this test is for debugging purposes only, no attempt is
     * made to optimize the envelope test.
     *
     * @return `true` if the input point lies within both input segment envelopes
     */
    private fun isInSegmentEnvelopes(intPt: Coordinate?): Boolean {
        val env0 = Envelope(inputLines[0][0]!!, inputLines[0][1]!!)
        val env1 = Envelope(inputLines[1][0]!!, inputLines[1][1]!!)
        return env0.contains(intPt!!) && env1.contains(intPt)
    }

    companion object {
        /**
         * Finds the endpoint of the segments P and Q which
         * is closest to the other segment.
         * This is a reasonable surrogate for the true
         * intersection points in ill-conditioned cases
         * (e.g. where two segments are nearly coincident,
         * or where the endpoint of one segment lies almost on the other segment).
         *
         * This replaces the older CentralEndpoint heuristic,
         * which chose the wrong endpoint in some cases
         * where the segments had very distinct slopes
         * and one endpoint lay almost on the other segment.
         *
         * @param p1 an endpoint of segment P
         * @param p2 an endpoint of segment P
         * @param q1 an endpoint of segment Q
         * @param q2 an endpoint of segment Q
         * @return the nearest endpoint to the other segment
         */
        private fun nearestEndpoint(
            p1: Coordinate?, p2: Coordinate?,
            q1: Coordinate?, q2: Coordinate?
        ): Coordinate? {
            var nearestPt = p1
            var minDist = pointToSegment(p1!!, q1!!, q2!!)
            var dist = pointToSegment(p2!!, q1, q2)
            if (dist < minDist) {
                minDist = dist
                nearestPt = p2
            }
            dist = pointToSegment(q1, p1, p2)
            if (dist < minDist) {
                minDist = dist
                nearestPt = q1
            }
            dist = pointToSegment(q2, p1, p2)
            if (dist < minDist) {
                minDist = dist
                nearestPt = q2
            }
            return nearestPt
        }
    }
}