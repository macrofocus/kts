/*
 * Copyright (c) 2016 Vivid Solutions.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.kts.geom

import org.locationtech.jts.geom.*
import org.locationtech.kts.geom.CoordinateListTest
import org.locationtech.jts.geom.CoordinateSequences.copy
import org.locationtech.jts.geom.CoordinateSequences.ensureValidRing
import org.locationtech.jts.geom.CoordinateSequences.indexOf
import org.locationtech.jts.geom.CoordinateSequences.isEqual
import org.locationtech.jts.geom.CoordinateSequences.isRing
import org.locationtech.jts.geom.CoordinateSequences.minCoordinateIndex
import org.locationtech.jts.geom.CoordinateSequences.reverse
import org.locationtech.jts.geom.CoordinateSequences.scroll
import org.locationtech.kts.geom.CoordinateSequencesTest
import org.locationtech.jts.geom.impl.CoordinateArraySequenceFactory.Companion.instance
import org.locationtech.jts.geom.impl.PackedCoordinateSequenceFactory
import org.locationtech.jts.io.WKTReader
import org.locationtech.jts.legacy.Math.cos
import org.locationtech.jts.legacy.Math.pow
import org.locationtech.jts.legacy.Math.sin
import kotlin.math.PI
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * @version 1.7
 */
class CoordinateSequencesTest {
    private val precisionModel = PrecisionModel()
    private val geometryFactory = GeometryFactory(precisionModel, 0)
    var reader = WKTReader(geometryFactory)

    @Test
    fun testCopyToLargerDim() {
        val csFactory = PackedCoordinateSequenceFactory()
        val cs2D = createTestSequence(csFactory, 10, 2)
        val cs3D = csFactory.create(10, 3)
        copy(cs2D, 0, cs3D, 0, cs3D.size())
        assertTrue(isEqual(cs2D, cs3D))
    }

    @Test
    fun testCopyToSmallerDim() {
        val csFactory = PackedCoordinateSequenceFactory()
        val cs3D = createTestSequence(csFactory, 10, 3)
        val cs2D = csFactory.create(10, 2)
        copy(cs3D, 0, cs2D, 0, cs2D.size())
        assertTrue(isEqual(cs2D, cs3D))
    }

    @Test
    fun testScrollRing() {
        println("Testing scrolling of closed ring")
        doTestScrollRing(instance(), 2)
        doTestScrollRing(instance(), 3)
        doTestScrollRing(PackedCoordinateSequenceFactory.DOUBLE_FACTORY, 2)
        doTestScrollRing(PackedCoordinateSequenceFactory.DOUBLE_FACTORY, 4)
        doTestScrollRing(PackedCoordinateSequenceFactory.FLOAT_FACTORY, 2)
        doTestScrollRing(PackedCoordinateSequenceFactory.FLOAT_FACTORY, 4)
    }

    @Test
    fun testScroll() {
        println("Testing scrolling of circular string")
        doTestScroll(instance(), 2)
        doTestScroll(instance(), 3)
        doTestScroll(PackedCoordinateSequenceFactory.DOUBLE_FACTORY, 2)
        doTestScroll(PackedCoordinateSequenceFactory.DOUBLE_FACTORY, 4)
        doTestScroll(PackedCoordinateSequenceFactory.FLOAT_FACTORY, 2)
        doTestScroll(PackedCoordinateSequenceFactory.FLOAT_FACTORY, 4)
    }

    @Test
    fun testIndexOf() {
        println("Testing indexOf")
        doTestIndexOf(instance(), 2)
        doTestIndexOf(PackedCoordinateSequenceFactory.DOUBLE_FACTORY, 5)
        doTestIndexOf(PackedCoordinateSequenceFactory.FLOAT_FACTORY, 7)
    }

    @Test
    fun testMinCoordinateIndex() {
        println("Testing minCoordinateIndex")
        doTestMinCoordinateIndex(instance(), 2)
        doTestMinCoordinateIndex(PackedCoordinateSequenceFactory.DOUBLE_FACTORY, 5)
        doTestMinCoordinateIndex(PackedCoordinateSequenceFactory.FLOAT_FACTORY, 7)
    }

    @Test
    fun testIsRing() {
        println("Testing isRing")
        doTestIsRing(instance(), 2)
        doTestIsRing(PackedCoordinateSequenceFactory.DOUBLE_FACTORY, 5)
        doTestIsRing(PackedCoordinateSequenceFactory.FLOAT_FACTORY, 7)
    }

    @Test
    fun testCopy() {
        println("Testing copy")
        doTestCopy(instance(), 2)
        doTestCopy(PackedCoordinateSequenceFactory.DOUBLE_FACTORY, 5)
        doTestCopy(PackedCoordinateSequenceFactory.FLOAT_FACTORY, 7)
    }

    @Test
    fun testReverse() {
        println("Testing reverse")
        doTestReverse(instance(), 2)
        doTestReverse(PackedCoordinateSequenceFactory.DOUBLE_FACTORY, 5)
        doTestReverse(PackedCoordinateSequenceFactory.FLOAT_FACTORY, 7)
    }

    /**
     * Method used to create a [this.ordinateValues].
     * Usage: remove first 't' and run as unit test.
     * Note: When parameters are changed, some unit tests may need to be
     * changed, too.
     *
     *
     * This is especially true for the (@link testMinCoordinateIndex) test,
     * which assumes that the coordinates in the sequence are all within an
     * envelope of [Env(10, 100, 10, 100)].
     * .
     *
     */
    @Deprecated("only use to update {@link this.ordinateValues}")
    fun ttestCreateRandomOrdinates() {
        val sequence = createRandomTestSequence(
            instance(), 20,
            2, Random(7),
            Envelope(10.0, 100.0, 10.0, 100.0), PrecisionModel(100.0)
        )
        val ordinates: StringBuilder
        ordinates = StringBuilder("\tprivate static final double[][] ordinateValues = {")
        for (i in 0 until sequence.size()) {
            if (i % 6 == 0) ordinates.append("\n\t\t")
            ordinates.append('{')
            ordinates.append(sequence.getOrdinate(i, 0))
            ordinates.append(',')
            ordinates.append(sequence.getOrdinate(i, 1))
            if (i < sequence.size() - 1) ordinates.append("},") else ordinates.append('}')
        }
        ordinates.append("};")
        println(ordinates.toString())
        assertTrue(true)
    }

    companion object {
        private val ordinateValues = arrayOf(
            doubleArrayOf(75.76, 77.43),
            doubleArrayOf(41.35, 90.75),
            doubleArrayOf(73.74, 41.67),
            doubleArrayOf(20.87, 86.49),
            doubleArrayOf(17.49, 93.59),
            doubleArrayOf(67.75, 80.63),
            doubleArrayOf(63.01, 52.57),
            doubleArrayOf(32.9, 44.44),
            doubleArrayOf(79.36, 29.8),
            doubleArrayOf(38.17, 88.0),
            doubleArrayOf(19.31, 49.71),
            doubleArrayOf(57.03, 19.28),
            doubleArrayOf(63.76, 77.35),
            doubleArrayOf(45.26, 85.15),
            doubleArrayOf(51.71, 50.38),
            doubleArrayOf(92.16, 19.85),
            doubleArrayOf(64.18, 27.7),
            doubleArrayOf(64.74, 65.1),
            doubleArrayOf(80.07, 13.55),
            doubleArrayOf(55.54, 94.07)
        )

        private fun createSequenceFromOrdinates(csFactory: CoordinateSequenceFactory, dim: Int): CoordinateSequence {
            val sequence = csFactory.create(ordinateValues.size, dim)
            for (i in ordinateValues.indices) {
                sequence.setOrdinate(i, 0, ordinateValues[i][0])
                sequence.setOrdinate(i, 1, ordinateValues[i][1])
            }
            return fillNonPlanarDimensions(sequence)
        }

        private fun createTestSequence(csFactory: CoordinateSequenceFactory, size: Int, dim: Int): CoordinateSequence {
            val cs = csFactory.create(size, dim)
            // initialize with a data signature where coords look like [1, 10, 100, ...]
            for (i in 0 until size) {
                for (d in 0 until dim) {
                    cs.setOrdinate(i, d, i * pow(10.0, d.toDouble()))
                }
            }
            return cs
        }

        @Deprecated("only use to update in conjunction with {@link this.ttestCreateRandomOrdinates}")
        private fun createRandomTestSequence(
            csFactory: CoordinateSequenceFactory,
            size: Int,
            dim: Int,
            rnd: Random,
            range: Envelope,
            pm: PrecisionModel
        ): CoordinateSequence {
            val cs = csFactory.create(size, dim)
            for (i in 0 until size) {
                cs.setOrdinate(i, 0, pm.makePrecise(range.width * rnd.nextDouble() + range.minX))
                cs.setOrdinate(i, 1, pm.makePrecise(range.height * rnd.nextDouble() + range.minY))
            }
            return fillNonPlanarDimensions(cs)
        }

        private fun doTestReverse(factory: CoordinateSequenceFactory, dimension: Int) {

            // arrange
            val sequence = createSequenceFromOrdinates(factory, dimension)
            val reversed = sequence.copy()

            // act
            reverse(reversed)

            // assert
            for (i in 0 until sequence.size()) checkCoordinateAt(
                sequence,
                i,
                reversed,
                sequence.size() - i - 1,
                dimension
            )
        }

        private fun doTestCopy(factory: CoordinateSequenceFactory, dimension: Int) {

            // arrange
            val sequence = createSequenceFromOrdinates(factory, dimension)
            if (sequence.size() <= 7) {
                println(
                    "sequence has a size of " + sequence.size() + ". Execution of this test needs a sequence " +
                            "with more than 6 coordinates."
                )
                return
            }
            val fullCopy = factory.create(sequence.size(), dimension)
            val partialCopy = factory.create(sequence.size() - 5, dimension)

            // act
            copy(sequence, 0, fullCopy, 0, sequence.size())
            copy(sequence, 2, partialCopy, 0, partialCopy.size())

            // assert
            for (i in 0 until fullCopy.size()) checkCoordinateAt(sequence, i, fullCopy, i, dimension)
            for (i in 0 until partialCopy.size()) checkCoordinateAt(sequence, 2 + i, partialCopy, i, dimension)

            // ToDo test if dimensions don't match
        }

        private fun doTestIsRing(factory: CoordinateSequenceFactory, dimension: Int) {

            // arrange
            val ring = createCircle(factory, dimension, Coordinate(), 5.0)
            val noRing = createCircularString(
                factory, dimension, Coordinate(), 5.0,
                0.1, 22
            )
            val empty = createAlmostRing(factory, dimension, 0)
            val incomplete1 = createAlmostRing(factory, dimension, 1)
            val incomplete2 = createAlmostRing(factory, dimension, 2)
            val incomplete3 = createAlmostRing(factory, dimension, 3)
            val incomplete4a = createAlmostRing(factory, dimension, 4)
            val incomplete4b = ensureValidRing(factory, incomplete4a)

            // act
            val isRingRing = isRing(ring)
            val isRingNoRing = isRing(noRing)
            val isRingEmpty = isRing(empty)
            val isRingIncomplete1 = isRing(incomplete1)
            val isRingIncomplete2 = isRing(incomplete2)
            val isRingIncomplete3 = isRing(incomplete3)
            val isRingIncomplete4a = isRing(incomplete4a)
            val isRingIncomplete4b = isRing(incomplete4b!!)

            // assert
            assertTrue(isRingRing)
            assertTrue(!isRingNoRing)
            assertTrue(isRingEmpty)
            assertTrue(!isRingIncomplete1)
            assertTrue(!isRingIncomplete2)
            assertTrue(!isRingIncomplete3)
            assertTrue(!isRingIncomplete4a)
            assertTrue(isRingIncomplete4b)
        }

        private fun doTestIndexOf(factory: CoordinateSequenceFactory, dimension: Int) {

            // arrange
            val sequence = createSequenceFromOrdinates(factory, dimension)

            // act & assert
            val coordinates = sequence.toCoordinateArray()
            for (i in 0 until sequence.size()) assertEquals(i, indexOf(coordinates[i], sequence))
        }

        private fun doTestMinCoordinateIndex(factory: CoordinateSequenceFactory, dimension: Int) {
            val sequence = createSequenceFromOrdinates(factory, dimension)
            if (sequence.size() <= 6) {
                println(
                    "sequence has a size of " + sequence.size() + ". Execution of this test needs a sequence " +
                            "with more than 5 coordinates."
                )
                return
            }
            val minIndex = sequence.size() / 2
            sequence.setOrdinate(minIndex, 0, 5.0)
            sequence.setOrdinate(minIndex, 1, 5.0)
            assertEquals(minIndex, minCoordinateIndex(sequence))
            assertEquals(minIndex, minCoordinateIndex(sequence, 2, sequence.size() - 2))
        }

        private fun doTestScroll(factory: CoordinateSequenceFactory, dimension: Int) {

            // arrange
            val sequence = createCircularString(
                factory, dimension, Coordinate(20.0, 20.0), 7.0,
                0.1, 22
            )
            val scrolled = sequence.copy()

            // act
            scroll(scrolled, 12)

            // assert
            var io = 12
            for (`is` in 0 until scrolled.size() - 1) {
                checkCoordinateAt(sequence, io, scrolled, `is`, dimension)
                io++
                io %= scrolled.size()
            }
        }

        private fun doTestScrollRing(factory: CoordinateSequenceFactory, dimension: Int) {

            // arrange
            //System.out.println("Testing '" + factory.getClass().getSimpleName() + "' with dim=" +dimension );
            val sequence = createCircle(factory, dimension, Coordinate(10.0, 10.0), 9.0)
            val scrolled = sequence.copy()

            // act
            scroll(scrolled, 12)

            // assert
            var io = 12
            for (`is` in 0 until scrolled.size() - 1) {
                checkCoordinateAt(sequence, io, scrolled, `is`, dimension)
                io++
                io %= scrolled.size() - 1
            }
            checkCoordinateAt(scrolled, 0, scrolled, scrolled.size() - 1, dimension)
        }

        private fun checkCoordinateAt(
            seq1: CoordinateSequence, pos1: Int,
            seq2: CoordinateSequence, pos2: Int, dim: Int
        ) {
            assertEquals(
                seq1.getOrdinate(pos1, 0), seq2.getOrdinate(pos2, 0),
                "unexpected x-ordinate at pos $pos2"
            )
            assertEquals(
                seq1.getOrdinate(pos1, 1), seq2.getOrdinate(pos2, 1),
                "unexpected y-ordinate at pos $pos2"
                )

            // check additional ordinates
            for (j in 2 until dim) {
                assertEquals(
                    seq1.getOrdinate(pos1, j), seq2.getOrdinate(pos2, j),
                    "unexpected $j-ordinate at pos $pos2"
                )
            }
        }

        private fun createAlmostRing(factory: CoordinateSequenceFactory, dimension: Int, num: Int): CoordinateSequence {
            var num = num
            if (num > 4) num = 4
            val sequence = factory.create(num, dimension)
            if (num == 0) return fillNonPlanarDimensions(sequence)
            sequence.setOrdinate(0, 0, 10.0)
            sequence.setOrdinate(0, 0, 10.0)
            if (num == 1) return fillNonPlanarDimensions(sequence)
            sequence.setOrdinate(0, 0, 20.0)
            sequence.setOrdinate(0, 0, 10.0)
            if (num == 2) return fillNonPlanarDimensions(sequence)
            sequence.setOrdinate(0, 0, 20.0)
            sequence.setOrdinate(0, 0, 20.0)
            if (num == 3) return fillNonPlanarDimensions(sequence)
            sequence.setOrdinate(0, 0, 10.0000000000001)
            sequence.setOrdinate(0, 0, 9.9999999999999)
            return fillNonPlanarDimensions(sequence)
        }

        private fun fillNonPlanarDimensions(seq: CoordinateSequence): CoordinateSequence {
            if (seq.dimension < 3) return seq
            for (i in 0 until seq.size()) for (j in 2 until seq.dimension) seq.setOrdinate(
                i,
                j,
                i * pow(10.0, j - 1.toDouble())
            )
            return seq
        }

        private fun createCircle(
            factory: CoordinateSequenceFactory, dimension: Int,
            center: Coordinate, radius: Double
        ): CoordinateSequence {
            // Get a complete circular string
            val res = createCircularString(factory, dimension, center, radius, 0.0, 49)

            // ensure it is closed
            for (i in 0 until dimension) res.setOrdinate(48, i, res.getOrdinate(0, i))
            return res
        }

        private fun createCircularString(
            factory: CoordinateSequenceFactory, dimension: Int,
            center: Coordinate, radius: Double, startAngle: Double,
            numPoints: Int
        ): CoordinateSequence {
            val numSegmentsCircle = 48
            val angleCircle: Double = 2 * PI
            val angleStep = angleCircle / numSegmentsCircle
            val sequence = factory.create(numPoints, dimension)
            val pm = PrecisionModel(100.0)
            var angle = startAngle
            for (i in 0 until numPoints) {
                val dx: Double = cos(angle) * radius
                sequence.setOrdinate(i, 0, pm.makePrecise(center.x + dx))
                val dy: Double = sin(angle) * radius
                sequence.setOrdinate(i, 1, pm.makePrecise(center.y + dy))

                // set other ordinate values to predictable values
                for (j in 2 until dimension) sequence.setOrdinate(i, j, pow(10.0, j - 1.toDouble()) * i)
                angle += angleStep
                angle %= angleCircle
            }
            return sequence
        }
    }
}