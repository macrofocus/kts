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
package org.locationtech.jts.algorithm.distance

import org.locationtech.jts.geom.*
import org.locationtech.jts.legacy.Math.round
import kotlin.jvm.JvmStatic

/**
 * An algorithm for computing a distance metric
 * which is an approximation to the Hausdorff Distance
 * based on a discretization of the input [Geometry].
 * The algorithm computes the Hausdorff distance restricted to discrete points
 * for one of the geometries.
 * The points can be either the vertices of the geometries (the default),
 * or the geometries with line segments densified by a given fraction.
 * Also determines two points of the Geometries which are separated by the computed distance.
 *
 *
 * This algorithm is an approximation to the standard Hausdorff distance.
 * Specifically,
 * <pre>
 * for all geometries a, b:    DHD(a, b) &lt;= HD(a, b)
</pre> *
 * The approximation can be made as close as needed by densifying the input geometries.
 * In the limit, this value will approach the true Hausdorff distance:
 * <pre>
 * DHD(A, B, densifyFactor) -&gt; HD(A, B) as densifyFactor -&gt; 0.0
</pre> *
 * The default approximation is exact or close enough for a large subset of useful cases.
 * Examples of these are:
 *
 *  * computing distance between Linestrings that are roughly parallel to each other,
 * and roughly equal in length.  This occurs in matching linear networks.
 *  * Testing similarity of geometries.
 *
 * An example where the default approximation is not close is:
 * <pre>
 * A = LINESTRING (0 0, 100 0, 10 100, 10 100)
 * B = LINESTRING (0 100, 0 10, 80 10)
 *
 * DHD(A, B) = 22.360679774997898
 * HD(A, B) ~= 47.8
</pre> *
 */
class DiscreteHausdorffDistance(private val g0: Geometry, private val g1: Geometry) {
    private val ptDist = PointPairDistance()

    /**
     * Value of 0.0 indicates that no densification should take place
     */
    private var densifyFrac = 0.0

    /**
     * Sets the fraction by which to densify each segment.
     * Each segment will be (virtually) split into a number of equal-length
     * subsegments, whose fraction of the total length is closest
     * to the given fraction.
     *
     * @param densifyFrac
     */
    fun setDensifyFraction(densifyFrac: Double) {
        if (densifyFrac > 1.0
            || densifyFrac <= 0.0
        ) throw IllegalArgumentException("Fraction is not in range (0.0 - 1.0]")
        this.densifyFrac = densifyFrac
    }

    fun distance(): Double {
        compute(g0, g1)
        return ptDist.distance
    }

    fun orientedDistance(): Double {
        computeOrientedDistance(g0, g1, ptDist)
        return ptDist.distance
    }

    val coordinates: Array<Coordinate>
        get() = ptDist.coordinates

    private fun compute(g0: Geometry, g1: Geometry) {
        computeOrientedDistance(g0, g1, ptDist)
        computeOrientedDistance(g1, g0, ptDist)
    }

    private fun computeOrientedDistance(discreteGeom: Geometry, geom: Geometry, ptDist: PointPairDistance) {
        val distFilter = MaxPointDistanceFilter(geom)
        discreteGeom.apply(distFilter)
        ptDist.setMaximum(distFilter.maxPointDistance)
        if (densifyFrac > 0) {
            val fracFilter = MaxDensifiedByFractionDistanceFilter(geom, densifyFrac)
            discreteGeom.apply(fracFilter)
            ptDist.setMaximum(fracFilter.maxPointDistance)
        }
    }

    class MaxPointDistanceFilter(private val geom: Geometry) : CoordinateFilter {
        val maxPointDistance = PointPairDistance()
        private val minPtDist = PointPairDistance()
        override fun filter(pt: Coordinate?) {
            minPtDist.initialize()
            DistanceToPoint.computeDistance(geom, pt!!, minPtDist)
            maxPointDistance.setMaximum(minPtDist)
        }
    }

    class MaxDensifiedByFractionDistanceFilter(private val geom: Geometry, fraction: Double) :
        CoordinateSequenceFilter {
        val maxPointDistance = PointPairDistance()
        private val minPtDist = PointPairDistance()
        private var numSubSegs = 0

        init {
            numSubSegs = round(1.0 / fraction).toInt()
        }

        override fun filter(seq: CoordinateSequence?, index: Int) {
            /**
             * This logic also handles skipping Point geometries
             */
            if (index == 0) return
            val p0 = seq!!.getCoordinate(index - 1)
            val p1 = seq.getCoordinate(index)
            val delx = (p1.x - p0.x) / numSubSegs
            val dely = (p1.y - p0.y) / numSubSegs
            for (i in 0 until numSubSegs) {
                val x = p0.x + i * delx
                val y = p0.y + i * dely
                val pt = Coordinate(x, y)
                minPtDist.initialize()
                DistanceToPoint.computeDistance(geom, pt, minPtDist)
                maxPointDistance.setMaximum(minPtDist)
            }
        }

        override val isGeometryChanged: Boolean
            get() = false
        override val isDone: Boolean
            get() = false
    }

    companion object {
        fun distance(g0: Geometry, g1: Geometry): Double {
            val dist = DiscreteHausdorffDistance(g0, g1)
            return dist.distance()
        }

        @JvmStatic
        fun distance(g0: Geometry, g1: Geometry, densifyFrac: Double): Double {
            val dist = DiscreteHausdorffDistance(g0, g1)
            dist.setDensifyFraction(densifyFrac)
            return dist.distance()
        }
    }
}