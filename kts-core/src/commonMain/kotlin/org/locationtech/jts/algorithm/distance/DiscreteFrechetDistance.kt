/*
 * Copyright (c) 2021 Felix Obermaier.
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
package org.locationtech.jts.algorithm.distance

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.legacy.Math.doubleToLongBits
import org.locationtech.jts.legacy.Math.max
import org.locationtech.jts.legacy.Math.min
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * The Fréchet distance is a measure of similarity between curves. Thus, it can
 * be used like the Hausdorff distance.
 *
 * An analogy for the Fréchet distance taken from
 * [
 * Computing Discrete Fréchet Distance](http://www.kr.tuwien.ac.at/staff/eiter/et-archive/cdtr9464.pdf)
 * <pre>
 * A man is walking a dog on a leash: the man can move
 * on one curve, the dog on the other; both may vary their
 * speed, but backtracking is not allowed.
</pre> *
 *
 * Its metric is better than the Hausdorff distance
 * because it takes the directions of the curves into account.
 * It is possible that two curves have a small Hausdorff but a large
 * Fréchet distance.
 *
 * This implementation is base on the following optimized Fréchet distance algorithm:
 * <pre>Thomas Devogele, Maxence Esnault, Laurent Etienne. Distance discrète de Fréchet optimisée. Spatial
 * Analysis and Geomatics (SAGEO), Nov 2016, Nice, France. hal-02110055</pre>
 *
 * Several matrix storage implementations are provided
 *
 * @see [Fréchet distance](https://en.wikipedia.org/wiki/Fr%C3%A9chet_distance)
 *
 * @see [
 * Computing Discrete Fréchet Distance](http://www.kr.tuwien.ac.at/staff/eiter/et-archive/cdtr9464.pdf)
 *
 * @see [Distance discrète de Fréchet optimisée](https://hal.archives-ouvertes.fr/hal-02110055/document)
 *
 * @see [
 * Fast Discrete Fréchet Distance](https://towardsdatascience.com/fast-discrete-fr%C3%A9chet-distance-d6b422a8fb77)
 */
class DiscreteFrechetDistance
/**
 * Creates an instance of this class using the provided geometries.
 *
 * @param g0 a geometry
 * @param g1 a geometry
 */(private val g0: Geometry, private val g1: Geometry) {
    private var ptDist: PointPairDistance? = null

    /**
     * Computes the `Discrete Fréchet Distance` between the input geometries
     *
     * @return the Discrete Fréchet Distance
     */
    private fun distance(): Double {
        val coords0 = g0.coordinates
        val coords1 = g1.coordinates
        val distances = createMatrixStorage(
            coords0!!.size, coords1!!.size
        )
        val diagonal = bresenhamDiagonal(
            coords0.size, coords1.size
        )
        val distanceToPair: HashMap<Double, IntArray> = HashMap<Double, IntArray>()
        computeCoordinateDistances(coords0, coords1, diagonal, distances, distanceToPair)
        ptDist = computeFrechet(coords0!!, coords1!!, diagonal, distances, distanceToPair)
        return ptDist!!.distance
    }

    /**
     * Gets the pair of [Coordinate]s at which the distance is obtained.
     *
     * @return the pair of Coordinates at which the distance is obtained
     */
    val coordinates: Array<Coordinate>
        get() {
            if (ptDist == null) distance()
            return ptDist!!.coordinates
        }

    /**
     * Computes relevant distances between pairs of [Coordinate]s for the
     * computation of the `Discrete Fréchet Distance`.
     *
     * @param coords0 an array of `Coordinate`s.
     * @param coords1 an array of `Coordinate`s.
     * @param diagonal an array of alternating col/row index values for the diagonal of the distance matrix
     * @param distances the distance matrix
     * @param distanceToPair a lookup for coordinate pairs based on a distance
     */
    private fun computeCoordinateDistances(
        coords0: Array<Coordinate>?, coords1: Array<Coordinate>?, diagonal: IntArray,
        distances: MatrixStorage, distanceToPair: HashMap<Double, IntArray>
    ) {
        val numDiag = diagonal.size
        var maxDistOnDiag = 0.0
        var imin = 0
        var jmin = 0
        val numCoords0 = coords0!!.size
        val numCoords1 = coords1!!.size

        // First compute all the distances along the diagonal.
        // Record the maximum distance.
        run {
            var k = 0
            while (k < numDiag) {
                val i0 = diagonal[k]
                val j0 = diagonal[k + 1]
                val diagDist = coords0[i0]!!.distance(coords1[j0]!!)
                if (diagDist > maxDistOnDiag) maxDistOnDiag = diagDist
                distances[i0, j0] = diagDist
                distanceToPair.getOrPut(diagDist, {intArrayOf(i0, j0)})
                k += 2
            }
        }

        // Check for distances shorter than maxDistOnDiag along the diagonal
        var k = 0
        while (k < numDiag - 2) {

            // Decode index
            val i0 = diagonal[k]
            val j0 = diagonal[k + 1]

            // Get reference coordinates for col and row
            val coord0 = coords0[i0]
            val coord1 = coords1[j0]

            // Check for shorter distances in this row
            var i = i0 + 1
            while (i < numCoords0) {
                if (!distances.isValueSet(i, j0)) {
                    val dist = coords0[i]!!.distance(coord1!!)
                    if (dist < maxDistOnDiag || i < imin) {
                        distances[i, j0] = dist
                        distanceToPair.getOrPut(dist, {intArrayOf(i, j0)})
                    } else break
                } else break
                i++
            }
            imin = i

            // Check for shorter distances in this column
            var j = j0 + 1
            while (j < numCoords1) {
                if (!distances.isValueSet(i0, j)) {
                    val dist = coord0!!.distance(coords1[j]!!)
                    if (dist < maxDistOnDiag || j < jmin) {
                        distances[i0, j] = dist
                        distanceToPair.getOrPut(dist, {intArrayOf(i0, j)})
                    } else break
                } else break
                j++
            }
            jmin = j
            k += 2
        }

        //System.out.println(distances.toString());
    }

    /**
     * Abstract base class for storing 2d matrix data
     */
    internal abstract class MatrixStorage
    /**
     * Creates an instance of this class
     * @param numRows the number of rows
     * @param numCols the number of columns
     * @param defaultValue A default value
     */(protected val numRows: Int, protected val numCols: Int, protected val defaultValue: Double) {
        /**
         * Gets the matrix value at i, j
         * @param i the row index
         * @param j the column index
         * @return The matrix value at i, j
         */
        abstract operator fun get(i: Int, j: Int): Double

        /**
         * Sets the matrix value at i, j
         * @param i the row index
         * @param j the column index
         * @param value The matrix value to set at i, j
         */
        abstract operator fun set(i: Int, j: Int, value: Double)

        /**
         * Gets a flag indicating if the matrix has a set value, e.g. one that is different
         * than [defaultValue].
         * @param i the row index
         * @param j the column index
         * @return a flag indicating if the matrix has a set value
         */
        abstract fun isValueSet(i: Int, j: Int): Boolean /* For debugging purposes only
    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder("[");
      for (int i = 0; i < this.numRows; i++)
      {
        sb.append('[');
        for(int j = 0; j < this.numCols; j++)
        {
          if (j > 0)
            sb.append(", ");
          sb.append(String.format(Locale.ROOT, "%8.4f", get(i, j)));
        }
        sb.append(']');
        if (i < this.numRows - 1) sb.append(",\n");
      }
      sb.append(']');
      return sb.toString();
    }
     */
    }

    /**
     * Straight forward implementation of a rectangular matrix
     */
    internal class RectMatrix(numRows: Int, numCols: Int, defaultValue: Double) :
        MatrixStorage(numRows, numCols, defaultValue) {
        private val matrix: DoubleArray

        /**
         * Creates an instance of this matrix using the given number of rows and columns.
         * A default value can be specified
         *
         * @param numRows the number of rows
         * @param numCols the number of columns
         * @param defaultValue A default value
         */
        init {
            matrix = DoubleArray(numRows * numCols)
            matrix.fill(defaultValue)
        }

        override fun get(i: Int, j: Int): Double {
            return matrix[i * numCols + j]
        }

        override fun set(i: Int, j: Int, value: Double) {
            matrix[i * numCols + j] = value
        }

        override fun isValueSet(i: Int, j: Int): Boolean {
            return doubleToLongBits(get(i, j)) != doubleToLongBits(defaultValue)
        }
    }

    /**
     * A matrix implementation that adheres to the
     * [
 * Compressed sparse row format](https://en.wikipedia.org/wiki/Sparse_matrix#Compressed_sparse_row_(CSR,_CRS_or_Yale_format)).<br></br>
     * Note: Unfortunately not as fast as expected.
     */
    internal class CsrMatrix @JvmOverloads constructor(
        numRows: Int,
        numCols: Int,
        defaultValue: Double,
        expectedValues: Int = expectedValuesHeuristic(numRows, numCols)
    ) : MatrixStorage(numRows, numCols, defaultValue) {
        private var v: DoubleArray
        private val ri: IntArray
        private var ci: IntArray

        init {
            v = DoubleArray(expectedValues)
            ci = IntArray(expectedValues)
            ri = IntArray(numRows + 1)
        }

        private fun indexOf(i: Int, j: Int): Int {
            val cLow = ri[i]
            val cHigh = ri[i + 1]
            return if (cHigh <= cLow) cLow.inv() else ci.asList().binarySearch(cLow, cHigh, j)
        }

        override fun get(i: Int, j: Int): Double {

            // get the index in the vector
            val vi = indexOf(i, j)

            // if the vector index is negative, return default value
            return if (vi < 0) defaultValue else v[vi]
        }

        override fun set(i: Int, j: Int, value: Double) {

            // get the index in the vector
            var vi = indexOf(i, j)

            // do we already have a value?
            if (vi < 0) {
                // no, we don't, we need to ensure space!
                ensureCapacity(ri[numRows] + 1)

                // update row indices
                for (ii in i + 1..numRows) ri[ii] += 1

                // move and update column indices, move values
                vi = vi.inv()
                for (ii in ri[numRows] downTo vi + 1) {
                    ci[ii] = ci[ii - 1]
                    v[ii] = v[ii - 1]
                }

                // insert column index
                ci[vi] = j
            }

            // set the new value
            v[vi] = value
        }

        override fun isValueSet(i: Int, j: Int): Boolean {
            return indexOf(i, j) >= 0
        }

        /**
         * Ensures that the column index vector (ci) and value vector (v) are sufficiently large.
         * @param required the number of items to store in the matrix
         */
        private fun ensureCapacity(required: Int) {
            if (required < v.size) return
            val increment: Int = max(numRows, numCols)
            v = v.copyOf(v.size + increment)
            ci = ci.copyOf(v.size + increment)
        }

        companion object {
            /**
             * Computes an initial value for the number of expected values
             * @param numRows the number of rows
             * @param numCols the number of columns
             * @return the expected number of values in the sparse matrix
             */
            private fun expectedValuesHeuristic(numRows: Int, numCols: Int): Int {
                val max: Int = max(numRows, numCols)
                return max * max / 10
            }
        }
    }

    /**
     * A sparse matrix based on java's [HashMap].
     */
    internal class HashMapMatrix(numRows: Int, numCols: Int, defaultValue: Double) :
        MatrixStorage(numRows, numCols, defaultValue) {
        private val matrix: HashMap<Long, Double> = HashMap<Long, Double>()

        override fun get(i: Int, j: Int): Double {
            val key = i.toLong() shl 32 or j.toLong()
            return matrix.getOrElse(key, {defaultValue})
        }

        override fun set(i: Int, j: Int, value: Double) {
            val key = i.toLong() shl 32 or j.toLong()
            matrix.put(key, value)
        }

        override fun isValueSet(i: Int, j: Int): Boolean {
            val key = i.toLong() shl 32 or j.toLong()
            return matrix.containsKey(key)
        }
    }

    companion object {
        /**
         * Computes the Discrete Fréchet Distance between two [Geometry]s
         * using a `Cartesian` distance computation function.
         *
         * @param g0 the 1st geometry
         * @param g1 the 2nd geometry
         * @return the cartesian distance between {#g0} and {#g1}
         */
        @JvmStatic
        fun distance(g0: Geometry, g1: Geometry): Double {
            val dist = DiscreteFrechetDistance(g0, g1)
            return dist.distance()
        }

        /**
         * Creates a matrix to store the computed distances.
         *
         * @param rows the number of rows
         * @param cols the number of columns
         * @return a matrix storage
         */
        private fun createMatrixStorage(rows: Int, cols: Int): MatrixStorage {
            val max: Int = max(rows, cols)
            // NOTE: these constraints need to be verified
            return if (max < 1024) RectMatrix(
                rows,
                cols,
                Double.POSITIVE_INFINITY
            ) else CsrMatrix(
                rows,
                cols,
                Double.POSITIVE_INFINITY
            )
        }

        /**
         * Computes the Fréchet Distance for the given distance matrix.
         *
         * @param coords0 an array of `Coordinate`s.
         * @param coords1 an array of `Coordinate`s.
         * @param diagonal an array of alternating col/row index values for the diagonal of the distance matrix
         * @param distances the distance matrix
         * @param distanceToPair a lookup for coordinate pairs based on a distance
         */
        private fun computeFrechet(
            coords0: Array<Coordinate>?, coords1: Array<Coordinate>?, diagonal: IntArray,
            distances: MatrixStorage, distanceToPair: HashMap<Double, IntArray>
        ): PointPairDistance {
            var d = 0
            while (d < diagonal.size) {
                val i0 = diagonal[d]
                val j0 = diagonal[d + 1]
                for (i in i0 until coords0!!.size) {
                    if (distances.isValueSet(i, j0)) {
                        val dist = getMinDistanceAtCorner(distances, i, j0)
                        if (dist > distances[i, j0]) distances[i, j0] = dist
                    } else {
                        break
                    }
                }
                for (j in j0 + 1 until coords1!!.size) {
                    if (distances.isValueSet(i0, j)) {
                        val dist = getMinDistanceAtCorner(distances, i0, j)
                        if (dist > distances[i0, j]) distances[i0, j] = dist
                    } else {
                        break
                    }
                }
                d += 2
            }
            val result = PointPairDistance()
            val distance = distances[coords0!!.size - 1, coords1!!.size - 1]
            val index: IntArray = distanceToPair[distance]
                ?: throw IllegalStateException("Pair of points not recorded for computed distance")
            result.initialize(coords0[index[0]], coords1[index[1]], distance)
            return result
        }

        /**
         * Returns the minimum distance at the corner (`i, j`).
         *
         * @param matrix A sparse matrix
         * @param i the column index
         * @param j the row index
         * @return the minimum distance
         */
        private fun getMinDistanceAtCorner(matrix: MatrixStorage, i: Int, j: Int): Double {
            if (i > 0 && j > 0) {
                val d0 = matrix[i - 1, j - 1]
                val d1 = matrix[i - 1, j]
                val d2 = matrix[i, j - 1]
                return min(min(d0, d1), d2)
            }
            if (i == 0 && j == 0) return matrix[0, 0]
            return if (i == 0) matrix[0, j - 1] else matrix[i - 1, 0]

            // j == 0
        }

        /**
         * Computes the indices for the diagonal of a `numCols x numRows` grid
         * using the [
 * Bresenham line algorithm](https://en.wikipedia.org/wiki/Bresenham%27s_line_algorithm).
         *
         * @param numCols the number of columns
         * @param numRows the number of rows
         * @return a packed array of column and row indices
         */
        @JvmStatic
        fun bresenhamDiagonal(numCols: Int, numRows: Int): IntArray {
            val dim: Int = max(numCols, numRows)
            val diagXY = IntArray(2 * dim)
            val dx = numCols - 1
            val dy = numRows - 1
            var err: Int
            var i = 0
            if (numCols > numRows) {
                var y = 0
                err = 2 * dy - dx
                for (x in 0 until numCols) {
                    diagXY[i++] = x
                    diagXY[i++] = y
                    if (err > 0) {
                        y += 1
                        err -= 2 * dx
                    }
                    err += 2 * dy
                }
            } else {
                var x = 0
                err = 2 * dx - dy
                for (y in 0 until numRows) {
                    diagXY[i++] = x
                    diagXY[i++] = y
                    if (err > 0) {
                        x += 1
                        err -= 2 * dy
                    }
                    err += 2 * dx
                }
            }
            return diagXY
        }
    }
}