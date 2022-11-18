/*
 * Copyright (c) 2020 Martin Davis.
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.noding.snap

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.CoordinateList
import org.locationtech.jts.math.MathUtil
import org.locationtech.jts.noding.MCIndexNoder
import org.locationtech.jts.noding.NodedSegmentString
import org.locationtech.jts.noding.Noder
import org.locationtech.jts.noding.SegmentString

/**
 * Nodes a set of segment strings
 * snapping vertices and intersection points together if
 * they lie within the given snap tolerance distance.
 * Vertices take priority over intersection points for snapping.
 * Input segment strings are generally only split at true node points
 * (i.e. the output segment strings are of maximal length in the output arrangement).
 *
 * The snap tolerance should be chosen to be as small as possible
 * while still producing a correct result.
 * It probably only needs to be small enough to eliminate
 * "nearly-coincident" segments, for which intersection points cannot be computed accurately.
 * This implies a factor of about 10e-12
 * smaller than the magnitude of the segment coordinates.
 *
 * With an appropriate snap tolerance this algorithm appears to be very robust.
 * So far no failure cases have been found,
 * given a small enough snap tolerance.
 *
 * The correctness of the output is not verified by this noder.
 * If required this can be done by [org.locationtech.jts.noding.ValidatingNoder].
 *
 * @version 1.17
 */
class SnappingNoder(private val snapTolerance: Double) : Noder {
    private val snapIndex: SnappingPointIndex = SnappingPointIndex(snapTolerance)
    private var nodedResult: List<NodedSegmentString>? = null

    /**
     * Gets the noded result.
     *
     * @return a Collection of NodedSegmentStrings representing the substrings
     */
    override val nodedSubstrings: Collection<NodedSegmentString>
        get() = nodedResult!!

    /**
     * Computes the noding of a set of [SegmentString]s.
     *
     * @param inputSegStrings a Collection of SegmentStrings
     */
    override fun computeNodes(inputSegStrings: Collection<SegmentString>) {
        val snappedSS: List<NodedSegmentString> = snapVertices(inputSegStrings)
        nodedResult = snapIntersections(snappedSS) as List<NodedSegmentString>
    }

    private fun snapVertices(segStrings: Collection<SegmentString>): List<NodedSegmentString> {
        //Stopwatch sw = new Stopwatch(); sw.start();
        seedSnapIndex(segStrings)
        val nodedStrings: MutableList<NodedSegmentString> = ArrayList<NodedSegmentString>()
        for (ss in segStrings) {
            nodedStrings.add(snapVertices(ss))
        }
        //System.out.format("Index depth = %d   Time: %s\n", snapIndex.depth(), sw.getTimeString());
        return nodedStrings
    }

    /**
     * Seeds the snap index with a small set of vertices
     * chosen quasi-randomly using a low-discrepancy sequence.
     * Seeding the snap index KdTree induces a more balanced tree.
     * This prevents monotonic runs of vertices
     * unbalancing the tree and causing poor query performance.
     *
     * @param segStrings the segStrings to be noded
     */
    private fun seedSnapIndex(segStrings: Collection<SegmentString>) {
        val SEED_SIZE_FACTOR = 100
        for (ss in segStrings) {
            val pts: Array<Coordinate> = ss.coordinates
            val numPtsToLoad = pts.size / SEED_SIZE_FACTOR
            var rand = 0.0
            for (i in 0 until numPtsToLoad) {
                rand = MathUtil.quasirandom(rand)
                val index = (pts.size * rand).toInt()
                snapIndex.snap(pts[index])
            }
        }
    }

    private fun snapVertices(ss: SegmentString): NodedSegmentString {
        val snapCoords = snap(ss.coordinates)
        return NodedSegmentString(snapCoords, ss.data)
    }

    private fun snap(coords: Array<Coordinate>): Array<Coordinate> {
        val snapCoords = CoordinateList()
        for (i in coords.indices) {
            val pt: Coordinate = snapIndex.snap(coords[i])
            snapCoords.add(pt, false)
        }
        return snapCoords.toCoordinateArray()
    }

    /**
     * Computes all interior intersections in the collection of [SegmentString]s,
     * and returns their [Coordinate]s.
     *
     * Also adds the intersection nodes to the segments.
     *
     * @return a list of Coordinates for the intersections
     */
    private fun snapIntersections(inputSS: List<NodedSegmentString>): Collection<*> {
        val intAdder: SnappingIntersectionAdder =
            SnappingIntersectionAdder(
                snapTolerance, snapIndex
            )

        /**
         * Use an overlap tolerance to ensure all
         * possible snapped intersections are found
         */
        val noder = MCIndexNoder(intAdder, 2 * snapTolerance)
        noder.computeNodes(inputSS)
        return noder.nodedSubstrings
    }
}