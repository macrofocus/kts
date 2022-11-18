/*
 * Copyright (c) 2019 Martin Davis.
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
package org.locationtech.jts.noding.snapround

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.CoordinateList
import org.locationtech.jts.geom.PrecisionModel
import org.locationtech.jts.index.kdtree.KdNode
import org.locationtech.jts.index.kdtree.KdNodeVisitor
import org.locationtech.jts.noding.MCIndexNoder
import org.locationtech.jts.noding.NodedSegmentString
import org.locationtech.jts.noding.Noder
import org.locationtech.jts.noding.SegmentString

/**
 * Uses Snap Rounding to compute a rounded,
 * fully noded arrangement from a set of [SegmentString]s,
 * in a performant way, and avoiding unnecessary noding.
 *
 * Implements the Snap Rounding technique described in
 * the papers by Hobby, Guibas &amp; Marimont, and Goodrich et al.
 * Snap Rounding enforces that all output vertices lie on a uniform grid,
 * which is determined by the provided [PrecisionModel].
 *
 * Input vertices do not have to be rounded to the grid beforehand;
 * this is done during the snap-rounding process.
 * In fact, rounding cannot be done a priori,
 * since rounding vertices by themselves can distort the rounded topology
 * of the arrangement (i.e. by moving segments away from hot pixels
 * that would otherwise intersect them, or by moving vertices
 * across segments).
 *
 * To minimize the number of introduced nodes,
 * the Snap-Rounding Noder avoids creating nodes
 * at edge vertices if there is no intersection or snap at that location.
 * However, if two different input edges contain identical segments,
 * each of the segment vertices will be noded.
 * This still provides fully-noded output.
 * This is the same behaviour provided by other noders,
 * such as [MCIndexNoder] and [org.locationtech.jts.noding.snap.SnappingNoder].
 *
 * @version 1.7
 */
class SnapRoundingNoder(private val pm: PrecisionModel) : Noder {
    private val pixelIndex: HotPixelIndex
    private var snappedResult: List<NodedSegmentString>? = null

    init {
        pixelIndex = HotPixelIndex(pm)
    }

    /**
     * @return a Collection of NodedSegmentStrings representing the substrings
     */
    override val nodedSubstrings: Collection<SegmentString>
        get() = NodedSegmentString.getNodedSubstrings(snappedResult)

    /**
     * Computes the nodes in the snap-rounding line arrangement.
     * The nodes are added to the [NodedSegmentString]s provided as the input.
     *
     * @param inputSegmentStrings a Collection of NodedSegmentStrings
     */
    override fun computeNodes(inputSegmentStrings: Collection<SegmentString>) {
        snappedResult = snapRound(inputSegmentStrings as Collection<NodedSegmentString>)
    }

    private fun snapRound(segStrings: Collection<NodedSegmentString>): List<NodedSegmentString> {
        /**
         * Determine hot pixels for intersections and vertices.
         * This is done BEFORE the input lines are rounded,
         * to avoid distorting the line arrangement
         * (rounding can cause vertices to move across edges).
         */
        addIntersectionPixels(segStrings)
        addVertexPixels(segStrings)
        return computeSnaps(segStrings)
    }

    /**
     * Detects interior intersections in the collection of [SegmentString]s,
     * and adds nodes for them to the segment strings.
     * Also creates HotPixel nodes for the intersection points.
     *
     * @param segStrings the input NodedSegmentStrings
     */
    private fun addIntersectionPixels(segStrings: Collection<NodedSegmentString>) {
        /**
         * nearness tolerance is a small fraction of the grid size.
         */
        val snapGridSize = 1.0 / pm.getScale()
        val nearnessTol = snapGridSize / NEARNESS_FACTOR
        val intAdder: SnapRoundingIntersectionAdder =
            SnapRoundingIntersectionAdder(nearnessTol)
        val noder = MCIndexNoder(intAdder, nearnessTol)
        noder.computeNodes(segStrings)
        val intPts: List<Coordinate> = intAdder.getIntersections()
        pixelIndex.addNodes(intPts)
    }

    /**
     * Creates HotPixels for each vertex in the input segStrings.
     * The HotPixels are not marked as nodes, since they will
     * only be nodes in the final line arrangement
     * if they interact with other segments (or they are already
     * created as intersection nodes).
     *
     * @param segStrings the input NodedSegmentStrings
     */
    private fun addVertexPixels(segStrings: Collection<NodedSegmentString>) {
        for (nss in segStrings) {
            val pts: Array<Coordinate> = nss.coordinates
            pixelIndex.add(pts)
        }
    }

    private fun round(pt: Coordinate): Coordinate {
        val p2 = pt.copy()
        pm.makePrecise(p2)
        return p2
    }

    /**
     * Gets a list of the rounded coordinates.
     * Duplicate (collapsed) coordinates are removed.
     *
     * @param pts the coordinates to round
     * @return array of rounded coordinates
     */
    private fun round(pts: Array<Coordinate>): Array<Coordinate> {
        val roundPts = CoordinateList()
        for (i in pts.indices) {
            roundPts.add(round(pts[i]), false)
        }
        return roundPts.toCoordinateArray()
    }

    /**
     * Computes new segment strings which are rounded and contain
     * intersections added as a result of snapping segments to snap points (hot pixels).
     *
     * @param segStrings segments to snap
     * @return the snapped segment strings
     */
    private fun computeSnaps(segStrings: Collection<NodedSegmentString>): List<NodedSegmentString> {
        val snapped: MutableList<NodedSegmentString> = ArrayList<NodedSegmentString>()
        for (ss in segStrings) {
            val snappedSS: NodedSegmentString? = computeSegmentSnaps(ss)
            if (snappedSS != null) snapped.add(snappedSS)
        }
        /**
         * Some intersection hot pixels may have been marked as nodes in the previous
         * loop, so add nodes for them.
         */
        for (ss in snapped) {
            addVertexNodeSnaps(ss)
        }
        return snapped
    }

    /**
     * Add snapped vertices to a segment string.
     * If the segment string collapses completely due to rounding,
     * null is returned.
     *
     * @param ss the segment string to snap
     * @return the snapped segment string, or null if it collapses completely
     */
    private fun computeSegmentSnaps(ss: NodedSegmentString): NodedSegmentString? {
        //Coordinate[] pts = ss.getCoordinates();
        /**
         * Get edge coordinates, including added intersection nodes.
         * The coordinates are now rounded to the grid,
         * in preparation for snapping to the Hot Pixels
         */
        val pts: Array<Coordinate> = ss.getNodedCoordinates()
        val ptsRound = round(pts)

        // if complete collapse this edge can be eliminated
        if (ptsRound.size <= 1) return null

        // Create new nodedSS to allow adding any hot pixel nodes
        val snapSS = NodedSegmentString(ptsRound, ss.data)
        var snapSSindex = 0
        for (i in 0 until pts.size - 1) {
            val currSnap: Coordinate = snapSS.getCoordinate(snapSSindex)

            /**
             * If the segment has collapsed completely, skip it
             */
            val p1 = pts[i + 1]
            val p1Round = round(p1)
            if (p1Round.equals2D(currSnap)) continue
            val p0 = pts[i]
            /**
             * Add any Hot Pixel intersections with *original* segment to rounded segment.
             * (It is important to check original segment because rounding can
             * move it enough to intersect other hot pixels not intersecting original segment)
             */
            snapSegment(p0, p1, snapSS, snapSSindex)
            snapSSindex++
        }
        return snapSS
    }

    /**
     * Snaps a segment in a segmentString to HotPixels that it intersects.
     *
     * @param p0 the segment start coordinate
     * @param p1 the segment end coordinate
     * @param ss the segment string to add intersections to
     * @param segIndex the index of the segment
     */
    private fun snapSegment(p0: Coordinate, p1: Coordinate, ss: NodedSegmentString, segIndex: Int) {
        pixelIndex.query(p0, p1, object : KdNodeVisitor {
            override fun visit(node: KdNode?) {
                val hp: HotPixel = node!!.data as HotPixel
                /**
                 * If the hot pixel is not a node, and it contains one of the segment vertices,
                 * then that vertex is the source for the hot pixel.
                 * To avoid over-noding a node is not added at this point.
                 * The hot pixel may be subsequently marked as a node,
                 * in which case the intersection will be added during the final vertex noding phase.
                 */
                if (!hp.isNode) {
                    if (hp.intersects(p0) || hp.intersects(p1)) return
                }
                /**
                 * Add a node if the segment intersects the pixel.
                 * Mark the HotPixel as a node (since it may not have been one before).
                 * This ensures the vertex for it is added as a node during the final vertex noding phase.
                 */
                if (hp.intersects(p0, p1)) {
                    //System.out.println("Added intersection: " + hp.getCoordinate());
                    ss.addIntersection(hp.coordinate, segIndex)
                    hp.setToNode()
                }
            }
        })
    }

    /**
     * Add nodes for any vertices in hot pixels that were
     * added as nodes during segment noding.
     *
     * @param ss a noded segment string
     */
    private fun addVertexNodeSnaps(ss: NodedSegmentString) {
        val pts: Array<Coordinate> = ss.coordinates
        for (i in 1 until pts.size - 1) {
            val p0 = pts[i]
            snapVertexNode(p0, ss, i)
        }
    }

    private fun snapVertexNode(p0: Coordinate, ss: NodedSegmentString, segIndex: Int) {
        pixelIndex.query(p0, p0, object : KdNodeVisitor {
            override fun visit(node: KdNode?) {
                val hp: HotPixel = node!!.data as HotPixel
                /**
                 * If vertex pixel is a node, add it.
                 */
                if (hp.isNode && hp.coordinate.equals2D(p0)) {
                    ss.addIntersection(p0, segIndex)
                }
            }
        })
    }

    companion object {
        /**
         * The division factor used to determine
         * nearness distance tolerance for intersection detection.
         */
        private const val NEARNESS_FACTOR = 100
    }
}