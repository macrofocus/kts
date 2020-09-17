/*
 * Copyright (c) 2016 Vivid Solutions.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.geom

import junit.framework.TestCase
import junit.textui.TestRunner
import org.locationtech.jts.geom.CoordinateArrays.dimension
import org.locationtech.jts.geom.CoordinateArrays.enforceConsistency
import org.locationtech.jts.geom.CoordinateArrays.envelope
import org.locationtech.jts.geom.CoordinateArrays.equals
import org.locationtech.jts.geom.CoordinateArrays.intersection
import org.locationtech.jts.geom.CoordinateArrays.measures
import org.locationtech.jts.geom.CoordinateArrays.ptNotInList
import org.locationtech.jts.geom.CoordinateArrays.scroll

/**
 * Unit tests for [CoordinateArrays]
 *
 * @author Martin Davis
 * @version 1.7
 */
class CoordinateArraysTest(name: String?) : TestCase(name) {
    fun testPtNotInList1() {
        assertTrue(
            ptNotInList(
                arrayOf(Coordinate(1.0, 1.0), Coordinate(2.0, 2.0), Coordinate(3.0, 3.0)),
                arrayOf(Coordinate(1.0, 1.0), Coordinate(1.0, 2.0), Coordinate(1.0, 3.0))
            )!!.equals2D(Coordinate(2.0, 2.0))
        )
    }

    fun testPtNotInList2() {
        assertTrue(
            ptNotInList(
                arrayOf(Coordinate(1.0, 1.0), Coordinate(2.0, 2.0), Coordinate(3.0, 3.0)),
                arrayOf(Coordinate(1.0, 1.0), Coordinate(2.0, 2.0), Coordinate(3.0, 3.0))
            ) == null
        )
    }

    fun testEnvelope1() {
        assertEquals(envelope(COORDS_1), Envelope(1.0, 3.0, 1.0, 3.0))
    }

    fun testEnvelopeEmpty() {
        assertEquals(envelope(COORDS_EMPTY), Envelope())
    }

    fun testIntersection_envelope1() {
        assertTrue(
            equals(
                intersection(COORDS_1, Envelope(1.0, 2.0, 1.0, 2.0)), arrayOf(Coordinate(1.0, 1.0), Coordinate(2.0, 2.0))
            )
        )
    }

    fun testIntersection_envelopeDisjoint() {
        assertTrue(
            equals(
                intersection(COORDS_1, Envelope(10.0, 20.0, 10.0, 20.0)), COORDS_EMPTY
            )
        )
    }

    fun testIntersection_empty_envelope() {
        assertTrue(
            equals(
                intersection(COORDS_EMPTY, Envelope(1.0, 2.0, 1.0, 2.0)), COORDS_EMPTY
            )
        )
    }

    fun testIntersection_coords_emptyEnvelope() {
        assertTrue(
            equals(
                intersection(COORDS_1, Envelope()), COORDS_EMPTY
            )
        )
    }

    fun testScrollRing() {
        // arrange
        val sequence = createCircle(Coordinate(10.0, 10.0), 9.0)
        val scrolled = createCircle(Coordinate(10.0, 10.0), 9.0)

        // act
        scroll(scrolled, 12)

        // assert
        var io = 12
        for (`is` in 0 until scrolled.size - 1) {
            checkCoordinateAt(sequence, io, scrolled, `is`)
            io++
            io %= scrolled.size - 1
        }
        checkCoordinateAt(scrolled, 0, scrolled, scrolled.size - 1)
    }

    fun testScroll() {
        // arrange
        val sequence = createCircularString(
            Coordinate(20.0, 20.0), 7.0,
            0.1, 22
        )
        val scrolled = createCircularString(
            Coordinate(20.0, 20.0), 7.0,
            0.1, 22
        )

        // act
        scroll(scrolled, 12)

        // assert
        var io = 12
        for (`is` in 0 until scrolled.size - 1) {
            checkCoordinateAt(sequence, io, scrolled, `is`)
            io++
            io %= scrolled.size
        }
    }

    fun testEnforceConsistency() {
        val array = arrayOf<Coordinate>(
            Coordinate(1.0, 1.0, 0.0),
            CoordinateXYM(2.0, 2.0, 1.0)
        )
        val array2 = arrayOf<Coordinate>(
            CoordinateXY(1.0, 1.0),
            CoordinateXY(2.0, 2.0)
        )
        // process into array with dimension 4 and measures 1
        enforceConsistency(array)
        assertEquals(3, dimension(array))
        assertEquals(1, measures(array))
        enforceConsistency(array2)
        var fixed = enforceConsistency(array2, 2, 0)
        assertSame(fixed, array2) // no processing required
        fixed = enforceConsistency(array, 3, 0)
        assertTrue(fixed != array) // copied into new array
        assertTrue(array[0] !== fixed[0]) // processing needed to CoordinateXYZM
        assertTrue(array[1] !== fixed[1]) // processing needed to CoordinateXYZM
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            TestRunner.run(CoordinateArraysTest::class.java)
        }

        private val COORDS_1 = arrayOf(Coordinate(1.0, 1.0), Coordinate(2.0, 2.0), Coordinate(3.0, 3.0))
        private val COORDS_EMPTY = arrayOf<Coordinate>()
        private fun checkCoordinateAt(
            seq1: Array<Coordinate>, pos1: Int,
            seq2: Array<Coordinate>, pos2: Int
        ) {
            val c1 = seq1[pos1]
            val c2 = seq2[pos2]
            assertEquals("unexpected x-ordinate at pos $pos2", c1.x, c2.x)
            assertEquals("unexpected y-ordinate at pos $pos2", c1.y, c2.y)
        }

        private fun createCircle(center: Coordinate, radius: Double): Array<Coordinate> {
            // Get a complete circular string
            val res = createCircularString(center, radius, 0.0, 49)

            // ensure it is closed
            res[48] = res[0].copy()
            return res
        }

        private fun createCircularString(
            center: Coordinate, radius: Double, startAngle: Double,
            numPoints: Int
        ): Array<Coordinate> {
            val numSegmentsCircle = 48
            val angleCircle = 2 * Math.PI
            val angleStep = angleCircle / numSegmentsCircle
            val sequence = arrayOfNulls<Coordinate>(numPoints)
            val pm = PrecisionModel(1000.0)
            var angle = startAngle
            for (i in 0 until numPoints) {
                val dx = Math.cos(angle) * radius
                val dy = Math.sin(angle) * radius
                sequence[i] = CoordinateXY(pm.makePrecise(center.x + dx), pm.makePrecise(center.y + dy))
                angle += angleStep
                angle %= angleCircle
            }
            return sequence.requireNoNulls()
        }
    }
}