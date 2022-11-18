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
 * <h3>KNOWN BUGS</h3>
 * This implementation is not fully robust.
 *
 * @version 1.7
 */
@Deprecated(
    """Not robust. Use {@link SnapRoundingNoder} instead.
 
  """
)
class MCIndexSnapRounder(private val pm: PrecisionModel) : Noder {
    private val li: LineIntersector
    private val scaleFactor: Double
    private var noder: MCIndexNoder? = null
    private var pointSnapper: MCIndexPointSnapper? = null
    private var nodedSegStrings: Collection<*>? = null

    init {
        li = RobustLineIntersector()
        li.precisionModel = pm
        scaleFactor = pm.getScale()
    }

    override val nodedSubstrings: Collection<SegmentString>
        get() = NodedSegmentString.getNodedSubstrings(nodedSegStrings)

    override fun computeNodes(inputSegmentStrings: Collection<SegmentString>) {
        nodedSegStrings = inputSegmentStrings
        noder = MCIndexNoder()
        pointSnapper = MCIndexPointSnapper(noder!!.index)
        snapRound(inputSegmentStrings, li)

        // testing purposes only - remove in final version
        //checkCorrectness(inputSegmentStrings);
    }

    /*
  private void checkCorrectness(Collection inputSegmentStrings)
  {
    Collection resultSegStrings = NodedSegmentString.getNodedSubstrings(inputSegmentStrings);
    NodingValidator nv = new NodingValidator(resultSegStrings);
    try {
      nv.checkValid();
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }
*/
    private fun snapRound(segStrings: Collection<SegmentString>, li: LineIntersector) {
        val intersections: MutableList<Coordinate> = findInteriorIntersections(segStrings, li)
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
    private fun findInteriorIntersections(segStrings: Collection<SegmentString>, li: LineIntersector): MutableList<Coordinate> {
        val intFinderAdder = InteriorIntersectionFinderAdder(li)
        noder!!.setSegmentIntersector(intFinderAdder)
        noder!!.computeNodes(segStrings)
        return intFinderAdder.getInteriorIntersections()
    }

    /**
     * Snaps segments to nodes created by segment intersections.
     */
    private fun computeIntersectionSnaps(snapPts: Collection<*>) {
        val it = snapPts.iterator()
        while (it.hasNext()) {
            val snapPt = it.next() as Coordinate
            val hotPixel: HotPixel =
                HotPixel(snapPt, scaleFactor)
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
            val edge0: NodedSegmentString = i0.next() as NodedSegmentString
            computeVertexSnaps(edge0)
        }
    }

    /**
     * Snaps segments to the vertices of a Segment String.
     */
    private fun computeVertexSnaps(e: NodedSegmentString) {
        val pts0: Array<Coordinate> = e.coordinates
        for (i in pts0.indices) {
            val hotPixel: HotPixel =
                HotPixel(
                    pts0[i], scaleFactor
                )
            val isNodeAdded: Boolean = pointSnapper!!.snap(hotPixel, e, i)
            // if a node is created for a vertex, that vertex must be noded too
            if (isNodeAdded) {
                e.addIntersection(pts0[i], i)
            }
        }
    }
}