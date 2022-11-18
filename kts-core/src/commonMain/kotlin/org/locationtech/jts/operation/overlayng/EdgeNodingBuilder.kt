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
package org.locationtech.jts.operation.overlayng

import org.locationtech.jts.algorithm.LineIntersector
import org.locationtech.jts.algorithm.Orientation.isCCW
import org.locationtech.jts.algorithm.RobustLineIntersector
import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.CoordinateArrays.removeRepeatedPoints
import org.locationtech.jts.noding.*
import org.locationtech.jts.noding.snapround.SnapRoundingNoder

/**
 * Builds a set of noded, unique, labelled Edges from
 * the edges of the two input geometries.
 *
 * It performs the following steps:
 *
 *  * Extracts input edges, and attaches topological information
 *  * if clipping is enabled, handles clipping or limiting input geometry
 *  * chooses a [Noder] based on provided precision model, unless a custom one is supplied
 *  * calls the chosen Noder, with precision model
 *  * removes any fully collapsed noded edges
 *  * builds [Edge]s and merges them
 *
 * @author mdavis
 */
internal class EdgeNodingBuilder
/**
 * Creates a new builder, with an optional custom noder.
 * If the noder is not provided, a suitable one will
 * be used based on the supplied precision model.
 *
 * @param pm the precision model to use
 * @param noder an optional custom noder to use (may be null)
 */(private val pm: PrecisionModel?, private val customNoder: Noder?) {
    var inputEdges: MutableList<NodedSegmentString> = ArrayList<NodedSegmentString>()
    private var clipEnv: Envelope? = null
    private var clipper: RingClipper? = null
    private var limiter: LineLimiter? = null
    private val hasEdges = BooleanArray(2)

    /**
     * Gets a noder appropriate for the precision model supplied.
     * This is one of:
     *
     *  * Fixed precision: a snap-rounding noder (which should be fully robust)
     *  * Floating precision: a conventional nodel (which may be non-robust).
     * In this case, a validation step is applied to the output from the noder.
     *
     * @return
     */
    private val noder: Noder
        private get() {
            if (customNoder != null) return customNoder
            return if (OverlayUtil.isFloating(pm)) createFloatingPrecisionNoder(
                IS_NODING_VALIDATED
            ) else createFixedPrecisionNoder(pm)
        }

    fun setClipEnvelope(clipEnv: Envelope) {
        this.clipEnv = clipEnv
        clipper = RingClipper(clipEnv)
        limiter = LineLimiter(clipEnv)
    }

    /**
     * Reports whether there are noded edges
     * for the given input geometry.
     * If there are none, this indicates that either
     * the geometry was empty, or has completely collapsed
     * (because it is smaller than the noding precision).
     *
     * @param geomIndex index of input geometry
     * @return true if there are edges for the geometry
     */
    fun hasEdgesFor(geomIndex: Int): Boolean {
        return hasEdges[geomIndex]
    }

    /**
     * Creates a set of labelled {Edge}s.
     * representing the fully noded edges of the input geometries.
     * Coincident edges (from the same or both geometries)
     * are merged along with their labels
     * into a single unique, fully labelled edge.
     *
     * @param geom0 the first geometry
     * @param geom1 the second geometry
     * @return the noded, merged, labelled edges
     */
    fun build(
        geom0: Geometry?,
        geom1: Geometry?
    ): List<Edge> {
        add(geom0, 0)
        add(geom1, 1)
        val nodedEdges: List<Edge> = node(inputEdges)

        return EdgeMerger.merge(nodedEdges)
    }

    /**
     * Nodes a set of segment strings and creates [Edge]s from the result.
     * The input segment strings each carry a [EdgeSourceInfo] object,
     * which is used to provide source topology info to the constructed Edges
     * (and is then discarded).
     *
     * @param segStrings
     * @return
     */
    private fun node(segStrings: List<NodedSegmentString>): List<Edge> {
        val noder = noder
        noder.computeNodes(segStrings)
        val nodedSS: Collection<SegmentString> = noder.nodedSubstrings!!

        //scanForEdges(nodedSS);
        return createEdges(nodedSS)
    }

    private fun createEdges(segStrings: Collection<SegmentString>): List<Edge> {
        val edges: MutableList<Edge> =
            ArrayList<Edge>()
        for (ss in segStrings) {
            val pts = ss!!.coordinates

            // don't create edges from collapsed lines
            if (Edge.isCollapsed(pts)) continue
            val info: EdgeSourceInfo? =
                ss.data as EdgeSourceInfo?
            /**
             * Record that a non-collapsed edge exists for the parent geometry
             */
            hasEdges[info!!.index] = true
            edges.add(Edge(ss.coordinates, info))
        }
        return edges
    }

    private fun add(g: Geometry?, geomIndex: Int) {
        if (g == null || g.isEmpty) return
        if (isClippedCompletely(g.envelopeInternal)) return
        if (g is Polygon) addPolygon(g, geomIndex) else if (g is LineString) addLine(
            g, geomIndex
        ) else if (g is MultiLineString) addCollection(g, geomIndex) else if (g is MultiPolygon) addCollection(
            g, geomIndex
        ) else if (g is GeometryCollection) addGeometryCollection(g, geomIndex, g.dimension)
        // ignore Point geometries - they are handled elsewhere
    }

    private fun addCollection(gc: GeometryCollection, geomIndex: Int) {
        for (i in 0 until gc.numGeometries) {
            val g = gc.getGeometryN(i)
            add(g, geomIndex)
        }
    }

    private fun addGeometryCollection(gc: GeometryCollection, geomIndex: Int, expectedDim: Int) {
        for (i in 0 until gc.numGeometries) {
            val g = gc.getGeometryN(i)
            // check for mixed-dimension input, which is not supported
            if (g.dimension != expectedDim) {
                throw IllegalArgumentException("Overlay input is mixed-dimension")
            }
            add(g, geomIndex)
        }
    }

    private fun addPolygon(poly: Polygon, geomIndex: Int) {
        val shell = poly.exteriorRing
        addPolygonRing(shell, false, geomIndex)
        for (i in 0 until poly.getNumInteriorRing()) {
            val hole = poly.getInteriorRingN(i)

            // Holes are topologically labelled opposite to the shell, since
            // the interior of the polygon lies on their opposite side
            // (on the left, if the hole is oriented CW)
            addPolygonRing(hole, true, geomIndex)
        }
    }

    /**
     * Adds a polygon ring to the graph.
     * Empty rings are ignored.
     */
    private fun addPolygonRing(ring: LinearRing?, isHole: Boolean, index: Int) {
        // don't add empty rings
        if (ring!!.isEmpty) return
        if (isClippedCompletely(ring!!.envelopeInternal)) return
        val pts = clip(ring)
        /**
         * Don't add edges that collapse to a point
         */
        if (pts.size < 2) {
            return
        }

        //if (pts.length < ring.getNumPoints()) System.out.println("Ring clipped: " + ring.getNumPoints() + " => " + pts.length);
        val depthDelta = computeDepthDelta(ring, isHole)
        val info: EdgeSourceInfo =
            EdgeSourceInfo(index, depthDelta, isHole)
        addEdge(pts, info)
    }

    /**
     * Tests whether a geometry (represented by its envelope)
     * lies completely outside the clip extent(if any).
     *
     * @param env the geometry envelope
     * @return true if the geometry envelope is outside the clip extent.
     */
    private fun isClippedCompletely(env: Envelope): Boolean {
        return if (clipEnv == null) false else clipEnv!!.disjoint(env)
    }

    /**
     * If a clipper is present,
     * clip the line to the clip extent.
     * Otherwise, remove duplicate points from the ring.
     *
     * If clipping is enabled, then every ring MUST
     * be clipped, to ensure that holes are clipped to
     * be inside the shell.
     * This means it is not possible to skip
     * clipping for rings with few vertices.
     *
     * @param ring the line to clip
     * @return the points in the clipped line
     */
    private fun clip(ring: LinearRing?): Array<Coordinate> {
        val pts: Array<Coordinate> = ring!!.coordinates
        val env = ring!!.envelopeInternal
        /**
         * If no clipper or ring is completely contained then no need to clip.
         * But repeated points must be removed to ensure correct noding.
         */
        return if (clipper == null || clipEnv!!.covers(env)) {
            removeRepeatedPoints(ring)
        } else clipper!!.clip(pts)
    }

    /**
     * Adds a line geometry, limiting it if enabled,
     * and otherwise removing repeated points.
     *
     * @param line the line to add
     * @param geomIndex the index of the parent geometry
     */
    private fun addLine(line: LineString, geomIndex: Int) {
        // don't add empty lines
        if (line.isEmpty) return
        if (isClippedCompletely(line.envelopeInternal)) return
        if (isToBeLimited(line)) {
            val sections = limit(line)
            for (pts in sections!!) {
                addLine(pts, geomIndex)
            }
        } else {
            val ptsNoRepeat = removeRepeatedPoints(line)
            addLine(ptsNoRepeat, geomIndex)
        }
    }

    private fun addLine(pts: Array<Coordinate>, geomIndex: Int) {
        /**
         * Don't add edges that collapse to a point
         */
        if (pts.size < 2) {
            return
        }
        val info: EdgeSourceInfo =
            EdgeSourceInfo(geomIndex)
        addEdge(pts, info)
    }

    private fun addEdge(pts: Array<Coordinate>, info: EdgeSourceInfo) {
        val ss = NodedSegmentString(pts, info)
        inputEdges.add(ss)
    }

    /**
     * Tests whether it is worth limiting a line.
     * Lines that have few vertices or are covered
     * by the clip extent do not need to be limited.
     *
     * @param line line to test
     * @return true if the line should be limited
     */
    private fun isToBeLimited(line: LineString): Boolean {
        val pts = line.coordinates
        if (limiter == null || pts.size <= MIN_LIMIT_PTS) {
            return false
        }
        val env = line.envelopeInternal
        /**
         * If line is completely contained then no need to limit
         */
        return !clipEnv!!.covers(env)
    }

    /**
     * If limiter is provided,
     * limit the line to the clip envelope.
     *
     * @param line the line to clip
     * @return the point sections in the clipped line
     */
    private fun limit(line: LineString): List<Array<Coordinate>>? {
        val pts: Array<Coordinate> = line.coordinates
        return limiter!!.limit(pts)
    }

    companion object {
        /**
         * Limiting is skipped for Lines with few vertices,
         * to avoid additional copying.
         */
        private const val MIN_LIMIT_PTS = 20

        /**
         * Indicates whether floating precision noder output is validated.
         */
        private const val IS_NODING_VALIDATED = true
        private fun createFixedPrecisionNoder(pm: PrecisionModel?): Noder {
            //Noder noder = new MCIndexSnapRounder(pm);
            //Noder noder = new SimpleSnapRounder(pm);
            return SnapRoundingNoder(pm!!)
        }

        private fun createFloatingPrecisionNoder(doValidation: Boolean): Noder {
            val mcNoder = MCIndexNoder()
            val li: LineIntersector = RobustLineIntersector()
            mcNoder.setSegmentIntersector(IntersectionAdder(li))
            var noder: Noder = mcNoder
            if (doValidation) {
                noder = ValidatingNoder(mcNoder)
            }
            return noder
        }

        /**
         * Removes any repeated points from a linear component.
         * This is required so that noding can be computed correctly.
         *
         * @param line the line to process
         * @return the points of the line with repeated points removed
         */
        private fun removeRepeatedPoints(line: LineString?): Array<Coordinate> {
            val pts = line!!.coordinates
            return removeRepeatedPoints(pts)
        }

        private fun computeDepthDelta(
            ring: LinearRing?,
            isHole: Boolean
        ): Int {
            /**
             * Compute the orientation of the ring, to
             * allow assigning side interior/exterior labels correctly.
             * JTS canonical orientation is that shells are CW, holes are CCW.
             *
             * It is important to compute orientation on the original ring,
             * since topology collapse can make the orientation computation give the wrong answer.
             */
            val isCCW: Boolean = isCCW(ring!!.coordinateSequence!!)

            /**
             * Compute whether ring is in canonical orientation or not.
             * Canonical orientation for the overlay process is
             * Shells : CW, Holes: CCW
             */
            var isOriented = true
            isOriented = if (!isHole) !isCCW else {
                isCCW
            }
            return if (isOriented) 1 else -1
        }
    }
}