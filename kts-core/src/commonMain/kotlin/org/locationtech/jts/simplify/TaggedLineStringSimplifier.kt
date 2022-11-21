/*
 * Copyright (c) 2016 Vivid Solutions.
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
package org.locationtech.jts.simplify

import org.locationtech.jts.algorithm.LineIntersector
import org.locationtech.jts.algorithm.RobustLineIntersector
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.LineSegment

/**
 * Simplifies a TaggedLineString, preserving topology
 * (in the sense that no new intersections are introduced).
 * Uses the recursive Douglas-Peucker algorithm.
 *
 * @author Martin Davis
 * @version 1.7
 */
class TaggedLineStringSimplifier constructor(
    inputIndex: LineSegmentIndex,
    outputIndex: LineSegmentIndex
) {
    private val li: LineIntersector = RobustLineIntersector()
    private var inputIndex: LineSegmentIndex = LineSegmentIndex()
    private var outputIndex: LineSegmentIndex = LineSegmentIndex()
    private var line: org.locationtech.jts.simplify.TaggedLineString? = null
    private lateinit var linePts: Array<Coordinate>
    private var distanceTolerance = 0.0

    init {
        this.inputIndex = inputIndex
        this.outputIndex = outputIndex
    }

    /**
     * Sets the distance tolerance for the simplification.
     * All vertices in the simplified geometry will be within this
     * distance of the original geometry.
     *
     * @param distanceTolerance the approximation tolerance to use
     */
    fun setDistanceTolerance(distanceTolerance: Double) {
        this.distanceTolerance = distanceTolerance
    }

    /**
     * Simplifies the given [TaggedLineString]
     * using the distance tolerance specified.
     *
     * @param line the linestring to simplify
     */
    fun simplify(line: org.locationtech.jts.simplify.TaggedLineString) {
        this.line = line
        linePts = line.parent.coordinates
        simplifySection(0, linePts.size - 1, 0)
    }

    private fun simplifySection(i: Int, j: Int, depth: Int) {
        var depth = depth
        depth += 1
        val sectionIndex = IntArray(2)
        if (i + 1 == j) {
            val newSeg: LineSegment = line!!.getSegment(i)!!
            line!!.addToResult(newSeg)
            // leave this segment in the input index, for efficiency
            return
        }
        var isValidToSimplify = true
        /**
         * Following logic ensures that there is enough points in the output line.
         * If there is already more points than the minimum, there's nothing to check.
         * Otherwise, if in the worst case there wouldn't be enough points,
         * don't flatten this segment (which avoids the worst case scenario)
         */
        if (line!!.resultSize < line!!.minimumSize) {
            val worstCaseSize = depth + 1
            if (worstCaseSize < line!!.minimumSize) isValidToSimplify = false
        }
        val distance = DoubleArray(1)
        val furthestPtIndex = findFurthestPoint(linePts, i, j, distance)
        // flattening must be less than distanceTolerance
        if (distance[0] > distanceTolerance) isValidToSimplify = false
        // test if flattened section would cause intersection
        val candidateSeg = LineSegment()
        candidateSeg.p0 = linePts[i]
        candidateSeg.p1 = linePts[j]
        sectionIndex[0] = i
        sectionIndex[1] = j
        if (hasBadIntersection(line, sectionIndex, candidateSeg)) isValidToSimplify = false
        if (isValidToSimplify) {
            val newSeg = flatten(i, j)
            line!!.addToResult(newSeg)
            return
        }
        simplifySection(i, furthestPtIndex, depth)
        simplifySection(furthestPtIndex, j, depth)
    }

    private fun findFurthestPoint(pts: Array<Coordinate>, i: Int, j: Int, maxDistance: DoubleArray): Int {
        val seg = LineSegment()
        seg.p0 = pts[i]
        seg.p1 = pts[j]
        var maxDist = -1.0
        var maxIndex = i
        for (k in i + 1 until j) {
            val midPt = pts[k]
            val distance = seg.distance(midPt)
            if (distance > maxDist) {
                maxDist = distance
                maxIndex = k
            }
        }
        maxDistance[0] = maxDist
        return maxIndex
    }

    /**
     * Flattens a section of the line between
     * indexes `start` and `end`,
     * replacing them with a line between the endpoints.
     * The input and output indexes are updated
     * to reflect this.
     *
     * @param start the start index of the flattened section
     * @param end the end index of the flattened section
     * @return the new segment created
     */
    private fun flatten(start: Int, end: Int): LineSegment {
        // make a new segment for the simplified geometry
        val p0 = linePts[start]
        val p1 = linePts[end]
        val newSeg = LineSegment(p0, p1)
        // update the indexes
        remove(line, start, end)
        outputIndex.add(newSeg)
        return newSeg
    }

    private fun hasBadIntersection(
        parentLine: org.locationtech.jts.simplify.TaggedLineString?,
        sectionIndex: IntArray,
        candidateSeg: LineSegment
    ): Boolean {
        if (hasBadOutputIntersection(candidateSeg)) return true
        return if (hasBadInputIntersection(parentLine, sectionIndex, candidateSeg)) true else false
    }

    private fun hasBadOutputIntersection(candidateSeg: LineSegment): Boolean {
        val querySegs: MutableList<*> = outputIndex.query(candidateSeg)
        val i: Iterator<*> = querySegs.iterator()
        while (i.hasNext()) {
            val querySeg = i.next() as LineSegment
            if (hasInteriorIntersection(querySeg, candidateSeg)) {
                return true
            }
        }
        return false
    }

    private fun hasBadInputIntersection(
        parentLine: org.locationtech.jts.simplify.TaggedLineString?,
        sectionIndex: IntArray,
        candidateSeg: LineSegment
    ): Boolean {
        val querySegs: MutableList<*> = inputIndex.query(candidateSeg)
        val i: Iterator<*> = querySegs.iterator()
        while (i.hasNext()) {
            val querySeg: org.locationtech.jts.simplify.TaggedLineSegment =
                i.next() as org.locationtech.jts.simplify.TaggedLineSegment
            if (hasInteriorIntersection(querySeg, candidateSeg)) {
                if (isInLineSection(parentLine, sectionIndex, querySeg)) {
                    continue
                }
                return true
            }
        }
        return false
    }

    private fun hasInteriorIntersection(seg0: LineSegment, seg1: LineSegment): Boolean {
        li.computeIntersection(seg0.p0, seg0.p1, seg1.p0, seg1.p1)
        return li.isInteriorIntersection
    }

    /**
     * Remove the segs in the section of the line
     * @param line
     * @param pts
     * @param sectionStartIndex
     * @param sectionEndIndex
     */
    private fun remove(
        line: org.locationtech.jts.simplify.TaggedLineString?,
        start: Int, end: Int
    ) {
        for (i in start until end) {
            val seg: TaggedLineSegment = line!!.getSegment(i)!!
            inputIndex.remove(seg)
        }
    }

    companion object {
        /**
         * Tests whether a segment is in a section of a TaggedLineString
         * @param line
         * @param sectionIndex
         * @param seg
         * @return
         */
        private fun isInLineSection(
            line: TaggedLineString?,
            sectionIndex: IntArray,
            seg: TaggedLineSegment
        ): Boolean {
            // not in this line
            if (seg.parent !== line!!.parent) return false
            val segIndex: Int = seg.index
            return if (segIndex >= sectionIndex[0] && segIndex < sectionIndex[1]) true else false
        }
    }
}