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
package org.locationtech.jts.noding.snapround

import org.locationtech.jts.algorithm.LineIntersector
import org.locationtech.jts.algorithm.RobustLineIntersector
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.PrecisionModel
import org.locationtech.jts.noding.*

/**
 * Uses Snap Rounding to compute a rounded,
 * fully noded arrangement from a set of [SegmentString]s.
 * Implements the Snap Rounding technique described in
 * papers by Hobby, Guibas &amp; Marimont, and Goodrich et al.
 * Snap Rounding assumes that all vertices lie on a uniform grid;
 * hence the precision model of the input must be fixed precision,
 * and all the input vertices must be rounded to that precision.
 *
 * This implementation uses a monotone chains and a spatial index to
 * speed up the intersection tests.
 *
 * This implementation appears to be fully robust using an integer precision model.
 * It will function with non-integer precision models, but the
 * results are not 100% guaranteed to be correctly noded.
 *
 * @version 1.7
 */
class MCIndexSnapRounder(pm: PrecisionModel) : Noder {
    private val li: LineIntersector
    private val scaleFactor: Double
    private var noder: MCIndexNoder? = null
    private var pointSnapper: MCIndexPointSnapper? = null
    private var nodedSegStrings: Collection<*>? = null
    override val nodedSubstrings: Collection<Any?>
        get() = NodedSegmentString.getNodedSubstrings(nodedSegStrings)

    override fun computeNodes(inputSegmentStrings: Collection<*>) {
        nodedSegStrings = inputSegmentStrings
        noder = MCIndexNoder()
        pointSnapper = MCIndexPointSnapper(noder!!.index)
        snapRound(inputSegmentStrings, li)

        // testing purposes only - remove in final version
        //checkCorrectness(inputSegmentStrings);
    }

    private fun checkCorrectness(inputSegmentStrings: Collection<*>) {
        val resultSegStrings: Collection<*> = NodedSegmentString.getNodedSubstrings(inputSegmentStrings)
        val nv = NodingValidator(resultSegStrings)
        try {
            nv.checkValid()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    private fun snapRound(segStrings: Collection<*>, li: LineIntersector) {
        val intersections = findInteriorIntersections(segStrings, li)
        computeIntersectionSnaps(intersections)
        computeVertexSnaps(segStrings)
    }

    /**
     * Computes all interior intersections in the collection of [SegmentString]s,
     * and returns their [Coordinate]s.
     *
     * Does NOT node the segStrings.
     *
     * @return a list of Coordinates for the intersections
     */
    private fun findInteriorIntersections(segStrings: Collection<*>, li: LineIntersector): List<*>? {
        val intFinderAdder = InteriorIntersectionFinderAdder(li)
        noder!!.setSegmentIntersector(intFinderAdder)
        noder!!.computeNodes(segStrings)
        return intFinderAdder.getInteriorIntersections()
    }

    /**
     * Snaps segments to nodes created by segment intersections.
     */
    private fun computeIntersectionSnaps(snapPts: Collection<*>?) {
        val it = snapPts!!.iterator()
        while (it.hasNext()) {
            val snapPt = it.next() as Coordinate
            val hotPixel = HotPixel(snapPt, scaleFactor, li)
            pointSnapper!!.snap(hotPixel)
        }
    }

    /**
     * Snaps segments to all vertices.
     *
     * @param edges the list of segment strings to snap together
     */
    fun computeVertexSnaps(edges: Collection<*>) {
        val i0 = edges.iterator()
        while (i0.hasNext()) {
            val edge0 = i0.next() as NodedSegmentString
            computeVertexSnaps(edge0)
        }
    }

    /**
     * Snaps segments to the vertices of a Segment String.
     */
    private fun computeVertexSnaps(e: NodedSegmentString) {
        val pts0 = e.coordinates
        for (i in pts0.indices) {
            val hotPixel = HotPixel(pts0[i], scaleFactor, li)
            val isNodeAdded = pointSnapper!!.snap(hotPixel, e, i)
            // if a node is created for a vertex, that vertex must be noded too
            if (isNodeAdded) {
                e.addIntersection(pts0[i], i)
            }
        }
    }

    init {
        li = RobustLineIntersector()
        li.precisionModel = pm
        scaleFactor = pm.getScale()
    }
}