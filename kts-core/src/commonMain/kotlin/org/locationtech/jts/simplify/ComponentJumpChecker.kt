/*
 * Copyright (c) 2023 Martin Davis.
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

import org.locationtech.jts.algorithm.RayCrossingCounter
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.LineSegment

/**
 * Checks if simplifying (flattening) line sections or segments
 * would cause them to "jump" over other components in the geometry.
 *
 * @author mdavis
 */
class ComponentJumpChecker(taggedLines: Collection<TaggedLineString>) {

    //TODO: use a spatial index?
    private val components: Collection<TaggedLineString> = taggedLines

    /**
     * Checks if a line section jumps a component if flattened.
     *
     * Assumes start <= end.
     *
     * @param line the line containing the section being flattened
     * @param start start index of the section
     * @param end end index of the section
     * @param seg the flattening segment
     * @return true if the flattened section jumps a component
     */
    fun hasJump(line: TaggedLineString, start: Int, end: Int, seg: LineSegment): Boolean {
        val sectionEnv = computeEnvelope(line, start, end)
        for (comp in components) {
            //-- don't test component against itself
            if (comp === line) continue

            val compPt = comp.componentPoint
            if (sectionEnv.intersects(compPt)) {
                if (hasJumpAtComponent(compPt, line, start, end, seg)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Checks if two consecutive segments jumps a component if flattened.
     * The segments are assumed to be consecutive.
     * (so the seg1.p1 = seg2.p0).
     * The flattening segment must be the segment between seg1.p0 and seg2.p1.
     *
     * @param line the line containing the section being flattened
     * @param seg1 the first replaced segment
     * @param seg2 the next replaced segment
     * @param seg the flattening segment
     * @return true if the flattened segment jumps a component
     */
    fun hasJump(line: TaggedLineString, seg1: LineSegment, seg2: LineSegment, seg: LineSegment): Boolean {
        val sectionEnv = computeEnvelope(seg1, seg2)
        for (comp in components) {
            //-- don't test component against itself
            if (comp === line) continue

            val compPt = comp.componentPoint
            if (sectionEnv.intersects(compPt)) {
                if (hasJumpAtComponent(compPt, seg1, seg2, seg)) {
                    return true
                }
            }
        }
        return false
    }

    companion object {
        private fun hasJumpAtComponent(
            compPt: Coordinate,
            line: TaggedLineString,
            start: Int,
            end: Int,
            seg: LineSegment
        ): Boolean {
            val sectionCount = crossingCount(compPt, line, start, end)
            val segCount = crossingCount(compPt, seg)
            return sectionCount % 2 != segCount % 2
        }

        private fun hasJumpAtComponent(
            compPt: Coordinate,
            seg1: LineSegment,
            seg2: LineSegment,
            seg: LineSegment
        ): Boolean {
            val sectionCount = crossingCount(compPt, seg1, seg2)
            val segCount = crossingCount(compPt, seg)
            return sectionCount % 2 != segCount % 2
        }

        private fun crossingCount(compPt: Coordinate, seg: LineSegment): Int {
            val rcc = RayCrossingCounter(compPt)
            rcc.countSegment(seg.p0, seg.p1)
            return rcc.count
        }

        private fun crossingCount(compPt: Coordinate, seg1: LineSegment, seg2: LineSegment): Int {
            val rcc = RayCrossingCounter(compPt)
            rcc.countSegment(seg1.p0, seg1.p1)
            rcc.countSegment(seg2.p0, seg2.p1)
            return rcc.count
        }

        private fun crossingCount(compPt: Coordinate, line: TaggedLineString, start: Int, end: Int): Int {
            val rcc = RayCrossingCounter(compPt)
            for (i in start until end) {
                rcc.countSegment(line.getCoordinate(i), line.getCoordinate(i + 1))
            }
            return rcc.count
        }

        private fun computeEnvelope(seg1: LineSegment, seg2: LineSegment): Envelope {
            val env = Envelope()
            env.expandToInclude(seg1.p0)
            env.expandToInclude(seg1.p1)
            env.expandToInclude(seg2.p0)
            env.expandToInclude(seg2.p1)
            return env
        }

        private fun computeEnvelope(line: TaggedLineString, start: Int, end: Int): Envelope {
            val env = Envelope()
            for (i in start..end) {
                env.expandToInclude(line.getCoordinate(i))
            }
            return env
        }
    }
}
