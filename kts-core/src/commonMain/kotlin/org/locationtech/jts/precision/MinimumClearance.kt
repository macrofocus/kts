/*
 * Copyright (c) 2016 Martin Davis.
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
package org.locationtech.jts.precision

import org.locationtech.jts.algorithm.Distance
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.LineSegment
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.index.strtree.ItemBoundable
import org.locationtech.jts.index.strtree.ItemDistance
import org.locationtech.jts.index.strtree.STRtree
import org.locationtech.jts.operation.distance.FacetSequence
import org.locationtech.jts.operation.distance.FacetSequenceTreeBuilder
import kotlin.jvm.JvmStatic

/**
 * Computes the Minimum Clearance of a [Geometry].
 *
 * The **Minimum Clearance** is a measure of
 * what magnitude of perturbation of
 * the vertices of a geometry can be tolerated
 * before the geometry becomes topologically invalid.
 * The smaller the Minimum Clearance distance,
 * the less vertex perturbation the geometry can tolerate
 * before becoming invalid.
 *
 * The concept was introduced by Thompson and Van Oosterom
 * [TV06], based on earlier work by Milenkovic [Mi88].
 *
 * The Minimum Clearance of a geometry G
 * is defined to be the value *r*
 * such that "the movement of all points by a distance
 * of *r* in any direction will
 * guarantee to leave the geometry valid" [TV06].
 * An equivalent constructive definition [Mi88] is that
 * *r* is the largest value such:
 *
 *  1. No two distinct vertices of G are closer than *r*
 *  1. No vertex of G is closer than *r* to an edge of G
 * of which the vertex is not an endpoint
 *
 * The following image shows an example of the Minimum Clearance
 * of a simple polygon.
 *
 * <center><img src='doc-files/minClearance.png' alt='minimum clearance'></img></center>
 *
 * If G has only a single vertex (i.e. is a
 * [Point]), the value of the minimum clearance
 * is [Double.MAX_VALUE].
 *
 * If G is a [Puntal] or [Lineal] geometry,
 * then in fact no amount of perturbation
 * will render the geometry invalid.
 * In this case a Minimum Clearance is still computed
 * based on the vertex and segment distances
 * according to the constructive definition.
 *
 * It is possible for no Minimum Clearance to exist.
 * For instance, a [MultiPoint] with all members identical
 * has no Minimum Clearance
 * (i.e. no amount of perturbation will cause
 * the member points to become non-identical).
 * Empty geometries also have no such distance.
 * The lack of a meaningful MinimumClearance distance is detected
 * and suitable values are returned by
 * [.getDistance] and [.getLine].
 *
 * The computation of Minimum Clearance utilizes
 * the [STRtree.nearestNeighbour]
 * method to provide good performance even for
 * large inputs.
 *
 * An interesting note is that for the case of [MultiPoint]s,
 * the computed Minimum Clearance line
 * effectively determines the Nearest Neighbours in the collection.
 *
 * <h3>References</h3>
 *
 *  * [Mi88] Milenkovic, V. J.,
 * *Verifiable implementations of geometric algorithms
 * using finite precision arithmetic*.
 * in Artificial Intelligence, 377-401. 1988
 *  * [TV06] Thompson, Rod and van Oosterom, Peter,
 * *Interchange of Spatial Data-Inhibiting Factors*,
 * Agile 2006, Visegrad, Hungary. 2006
 *
 * @author Martin Davis
 */
class MinimumClearance
/**
 * Creates an object to compute the Minimum Clearance
 * for the given Geometry
 *
 * @param geom the input geometry
 */(private val inputGeom: Geometry) {
    private var minClearance = 0.0
    private var minClearancePts: Array<Coordinate?>? = null

    /**
     * Gets the Minimum Clearance distance.
     *
     * If no distance exists
     * (e.g. in the case of two identical points)
     * <tt>Double.MAX_VALUE</tt> is returned.
     *
     * @return the value of the minimum clearance distance
     * or <tt>Double.MAX_VALUE</tt> if no Minimum Clearance distance exists
     */
    val distance: Double
        get() {
            compute()
            return minClearance
        }// return empty line string if no min pts where found

    /**
     * Gets a LineString containing two points
     * which are at the Minimum Clearance distance.
     *
     * If no distance could be found
     * (e.g. in the case of two identical points)
     * <tt>LINESTRING EMPTY</tt> is returned.
     *
     * @return the value of the minimum clearance distance
     * or <tt>LINESTRING EMPTY</tt> if no Minimum Clearance distance exists
     */
    val line: LineString
        get() {
            compute()
            // return empty line string if no min pts where found
            return if (minClearancePts == null || minClearancePts!![0] == null) inputGeom.factory.createLineString() else inputGeom.factory.createLineString(
                minClearancePts?.requireNoNulls()
            )
        }

    private fun compute() {
        // already computed
        if (minClearancePts != null) return

        // initialize to "No Distance Exists" state
        minClearancePts = arrayOfNulls(2)
        minClearance = Double.MAX_VALUE

        // handle empty geometries
        if (inputGeom.isEmpty) {
            return
        }
        val geomTree: STRtree = FacetSequenceTreeBuilder.build(inputGeom)
        val nearest = geomTree.nearestNeighbour(MinClearanceDistance())
        val mcd = MinClearanceDistance()
        minClearance = mcd.distance(
            nearest!![0] as FacetSequence,
            nearest[1] as FacetSequence
        )
        minClearancePts = mcd.coordinates
    }

    /**
     * Implements the MinimumClearance distance function:
     *
     *  * dist(p1, p2) =
     *
     *  * p1 != p2 : p1.distance(p2)
     *  * p1 == p2 : Double.MAX
     *
     *  * dist(p, seg) =
     *
     *  * p != seq.p1 && p != seg.p2 : seg.distance(p)
     *  * ELSE : Double.MAX
     *
     * Also computes the values of the nearest points, if any.
     *
     * @author Martin Davis
     */
    private class MinClearanceDistance : ItemDistance {
        private var minDist = Double.MAX_VALUE
        val coordinates = arrayOfNulls<Coordinate>(2)
        override fun distance(b1: ItemBoundable, b2: ItemBoundable): Double {
            val fs1: FacetSequence = b1!!.item as FacetSequence
            val fs2: FacetSequence = b2!!.item as FacetSequence
            minDist = Double.MAX_VALUE
            return distance(fs1, fs2)
        }

        fun distance(fs1: FacetSequence, fs2: FacetSequence): Double {
            // compute MinClearance distance metric
            vertexDistance(fs1, fs2)
            if (fs1.size() === 1 && fs2.size() === 1) return minDist
            if (minDist <= 0.0) return minDist
            segmentDistance(fs1, fs2)
            if (minDist <= 0.0) return minDist
            segmentDistance(fs2, fs1)
            return minDist
        }

        private fun vertexDistance(fs1: FacetSequence, fs2: FacetSequence): Double {
            for (i1 in 0 until fs1.size()) {
                for (i2 in 0 until fs2.size()) {
                    val p1: Coordinate = fs1.getCoordinate(i1)
                    val p2: Coordinate = fs2.getCoordinate(i2)
                    if (!p1.equals2D(p2)) {
                        val d = p1.distance(p2)
                        if (d < minDist) {
                            minDist = d
                            coordinates[0] = p1
                            coordinates[1] = p2
                            if (d == 0.0) return d
                        }
                    }
                }
            }
            return minDist
        }

        private fun segmentDistance(fs1: FacetSequence, fs2: FacetSequence): Double {
            for (i1 in 0 until fs1.size()) {
                for (i2 in 1 until fs2.size()) {
                    val p: Coordinate = fs1.getCoordinate(i1)
                    val seg0: Coordinate = fs2.getCoordinate(i2 - 1)
                    val seg1: Coordinate = fs2.getCoordinate(i2)
                    if (!(p.equals2D(seg0) || p.equals2D(seg1))) {
                        val d = Distance.pointToSegment(p, seg0, seg1)
                        if (d < minDist) {
                            minDist = d
                            updatePts(p, seg0, seg1)
                            if (d == 0.0) return d
                        }
                    }
                }
            }
            return minDist
        }

        private fun updatePts(p: Coordinate, seg0: Coordinate, seg1: Coordinate) {
            coordinates[0] = p
            val seg = LineSegment(seg0, seg1)
            coordinates[1] = Coordinate(seg.closestPoint(p))
        }
    }

    companion object {
        /**
         * Computes the Minimum Clearance distance for
         * the given Geometry.
         *
         * @param g the input geometry
         * @return the Minimum Clearance distance
         */
        @JvmStatic
        fun getDistance(g: Geometry): Double {
            val rp = MinimumClearance(g)
            return rp.distance
        }

        /**
         * Gets a LineString containing two points
         * which are at the Minimum Clearance distance
         * for the given Geometry.
         *
         * @param g the input geometry
         * @return the value of the minimum clearance distance
         * or <tt>LINESTRING EMPTY</tt> if no Minimum Clearance distance exists
         */
        fun getLine(g: Geometry): Geometry {
            val rp = MinimumClearance(g)
            return rp.line
        }
    }
}