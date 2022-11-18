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

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.index.ItemVisitor
import org.locationtech.jts.index.SpatialIndex
import org.locationtech.jts.index.chain.MonotoneChain
import org.locationtech.jts.index.chain.MonotoneChainSelectAction
import org.locationtech.jts.index.strtree.STRtree
import org.locationtech.jts.noding.NodedSegmentString
import org.locationtech.jts.noding.SegmentString
import kotlin.jvm.JvmOverloads

/**
 * "Snaps" all [SegmentString]s in a [SpatialIndex] containing
 * [MonotoneChain]s to a given [HotPixel].
 *
 * @version 1.7
 */
class MCIndexPointSnapper(index: SpatialIndex) {
    //public static final int nSnaps = 0;
    private val index: STRtree

    /**
     * Snaps (nodes) all interacting segments to this hot pixel.
     * The hot pixel may represent a vertex of an edge,
     * in which case this routine uses the optimization
     * of not noding the vertex itself
     *
     * @param hotPixel the hot pixel to snap to
     * @param parentEdge the edge containing the vertex, if applicable, or `null`
     * @param hotPixelVertexIndex the index of the hotPixel vertex, if applicable, or -1
     * @return `true` if a node was added for this pixel
     */
    @JvmOverloads
    fun snap(
        hotPixel: HotPixel,
        parentEdge: SegmentString? = null,
        hotPixelVertexIndex: Int = -1
    ): Boolean {
        val pixelEnv = getSafeEnvelope(hotPixel)
        val hotPixelSnapAction = HotPixelSnapAction(hotPixel, parentEdge, hotPixelVertexIndex)
        index.query(pixelEnv, object : ItemVisitor {
            override fun visitItem(item: Any?) {
                val testChain = item as MonotoneChain?
                testChain!!.select(pixelEnv, hotPixelSnapAction)
            }
        }
        )
        return hotPixelSnapAction.isNodeAdded
    }

    init {
        this.index = index as STRtree
    }

    /**
     * Returns a "safe" envelope that is guaranteed to contain the hot pixel.
     * The envelope returned is larger than the exact envelope of the
     * pixel by a safe margin.
     *
     * @return an envelope which contains the hot pixel
     */
    fun getSafeEnvelope(hp: HotPixel): Envelope {
        val safeTolerance: Double = SAFE_ENV_EXPANSION_FACTOR / hp.scaleFactor
        val safeEnv: Envelope = Envelope(hp.coordinate)
        safeEnv.expandBy(safeTolerance)
        return safeEnv
    }

    class HotPixelSnapAction(
        hotPixel: HotPixel,
        parentEdge: SegmentString?,
        hotPixelVertexIndex: Int
    ) : MonotoneChainSelectAction() {
        private val hotPixel: HotPixel
        private val parentEdge: SegmentString?

        // is -1 if hotPixel is not a vertex
        private val hotPixelVertexIndex: Int

        /**
         * Reports whether the HotPixel caused a
         * node to be added in any target segmentString (including its own).
         * If so, the HotPixel must be added as a node as well.
         * @return true if a node was added in any target segmentString.
         */
        var isNodeAdded = false
            private set

        init {
            this.hotPixel = hotPixel
            this.parentEdge = parentEdge
            this.hotPixelVertexIndex = hotPixelVertexIndex
        }

        /**
         * Check if a segment of the monotone chain intersects
         * the hot pixel vertex and introduce a snap node if so.
         * Optimized to avoid noding segments which
         * contain the vertex (which otherwise
         * would cause every vertex to be noded).
         */
        override fun select(mc: MonotoneChain, startIndex: Int) {
            val ss: NodedSegmentString = mc.context as NodedSegmentString
            /**
             * Check to avoid snapping a hotPixel vertex to the its orginal vertex.
             * This method is called on segments which intersect the
             * hot pixel.
             * If either end of the segment is equal to the hot pixel
             * do not snap.
             */
            if (parentEdge != null && ss === parentEdge) {
                if (startIndex == hotPixelVertexIndex
                    || startIndex + 1 == hotPixelVertexIndex
                ) return
            }
            // records if this HotPixel caused any node to be added
            isNodeAdded = isNodeAdded or addSnappedNode(hotPixel, ss, startIndex)
        }

        /**
         * Adds a new node (equal to the snap pt) to the specified segment
         * if the segment passes through the hot pixel
         *
         * @param segStr
         * @param segIndex
         * @return true if a node was added to the segment
         */
        fun addSnappedNode(
            hotPixel: HotPixel,
            segStr: NodedSegmentString,
            segIndex: Int
        ): Boolean {
            val p0: Coordinate = segStr.getCoordinate(segIndex)
            val p1: Coordinate = segStr.getCoordinate(segIndex + 1)
            if (hotPixel.intersects(p0, p1)) {
                //System.out.println("snapped: " + snapPt);
                //System.out.println("POINT (" + snapPt.x + " " + snapPt.y + ")");
                segStr.addIntersection(hotPixel.coordinate, segIndex)
                return true
            }
            return false
        }
    }

    companion object {
        private const val SAFE_ENV_EXPANSION_FACTOR = 0.75
    }
}