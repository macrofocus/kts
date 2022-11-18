/*
 * Copyright (c) 2016 Vivid Solutions.
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.noding

import org.locationtech.jts.algorithm.LineIntersector
import org.locationtech.jts.algorithm.RobustLineIntersector
import org.locationtech.jts.geom.Coordinate
import kotlin.jvm.JvmOverloads

/**
 * Detects and records an intersection between two [SegmentString]s,
 * if one exists.  Only a single intersection is recorded.
 * This strategy can be configured to search for **proper intersections**.
 * In this case, the presence of *any* kind of intersection will still be recorded,
 * but searching will continue until either a proper intersection has been found
 * or no intersections are detected.
 *
 * @version 1.7
 */
class SegmentIntersectionDetector
/**
 * Creates an intersection finder using a [RobustLineIntersector].
 */ @JvmOverloads constructor(private val li: LineIntersector = RobustLineIntersector()) : SegmentIntersector {
    private var findProper = false
    private var findAllTypes = false
    private var hasIntersection = false
    private var hasProperIntersection = false
    private var hasNonProperIntersection = false

    /**
     * Gets the computed location of the intersection.
     * Due to round-off, the location may not be exact.
     *
     * @return the coordinate for the intersection location
     */
    var intersection: Coordinate? = null
        private set

    /**
     * Gets the endpoints of the intersecting segments.
     *
     * @return an array of the segment endpoints (p00, p01, p10, p11)
     */
    var intersectionSegments: Array<Coordinate?>? = null
        private set
    /**
     * Creates an intersection finder using a given LineIntersector.
     *
     * @param li the LineIntersector to use
     */
    /**
     * Sets whether processing must continue until a proper intersection is found.
     *
     * @param findProper true if processing should continue until a proper intersection is found
     */
    fun setFindProper(findProper: Boolean) {
        this.findProper = findProper
    }

    /**
     * Sets whether processing can terminate once any intersection is found.
     *
     * @param findAllTypes true if processing can terminate once any intersection is found.
     */
    fun setFindAllIntersectionTypes(findAllTypes: Boolean) {
        this.findAllTypes = findAllTypes
    }

    /**
     * Tests whether an intersection was found.
     *
     * @return true if an intersection was found
     */
    fun hasIntersection(): Boolean {
        return hasIntersection
    }

    /**
     * Tests whether a proper intersection was found.
     *
     * @return true if a proper intersection was found
     */
    fun hasProperIntersection(): Boolean {
        return hasProperIntersection
    }

    /**
     * Tests whether a non-proper intersection was found.
     *
     * @return true if a non-proper intersection was found
     */
    fun hasNonProperIntersection(): Boolean {
        return hasNonProperIntersection
    }

    /**
     * This method is called by clients
     * of the [SegmentIntersector] class to process
     * intersections for two segments of the [SegmentString]s being intersected.
     * Note that some clients (such as `MonotoneChain`s) may optimize away
     * this call for segment pairs which they have determined do not intersect
     * (e.g. by an disjoint envelope test).
     */
    override fun processIntersections(
        e0: SegmentString, segIndex0: Int,
        e1: SegmentString, segIndex1: Int
    ) {
        // don't bother intersecting a segment with itself
        if (e0 === e1 && segIndex0 == segIndex1) return
        val p00: Coordinate = e0.getCoordinate(segIndex0)
        val p01: Coordinate = e0.getCoordinate(segIndex0 + 1)
        val p10: Coordinate = e1.getCoordinate(segIndex1)
        val p11: Coordinate = e1.getCoordinate(segIndex1 + 1)
        li.computeIntersection(p00, p01, p10, p11)
        //  if (li.hasIntersection() && li.isProper()) Debug.println(li);
        if (li.hasIntersection()) {
            // System.out.println(li);

            // record intersection info
            hasIntersection = true
            val isProper = li.isProper
            if (isProper) hasProperIntersection = true
            if (!isProper) hasNonProperIntersection = true
            /**
             * If this is the kind of intersection we are searching for
             * OR no location has yet been recorded
             * save the location data
             */
            var saveLocation = true
            if (findProper && !isProper) saveLocation = false
            if (intersection == null || saveLocation) {

                // record intersection location (approximate)
                intersection = li.getIntersection(0)

                // record intersecting segments
                intersectionSegments = arrayOfNulls(4)
                intersectionSegments!![0] = p00
                intersectionSegments!![1] = p01
                intersectionSegments!![2] = p10
                intersectionSegments!![3] = p11
            }
        }
    }
    /**
     * If finding all types, we can stop
     * when both possible types have been found.
     */
    /**
     * If searching for a proper intersection, only stop if one is found
     */
    /**
     * Tests whether processing can terminate,
     * because all required information has been obtained
     * (e.g. an intersection of the desired type has been detected).
     *
     * @return true if processing can terminate
     */
    override val isDone: Boolean
        get() {
            /**
             * If finding all types, we can stop
             * when both possible types have been found.
             */
            if (findAllTypes) {
                return hasProperIntersection && hasNonProperIntersection
            }
            /**
             * If searching for a proper intersection, only stop if one is found
             */
            return if (findProper) {
                hasProperIntersection
            } else hasIntersection
        }
}