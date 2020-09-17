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
class MCIndexPointSnapper(index: SpatialIndex?) {
    //public static final int nSnaps = 0;
    private val index: STRtree? = index as STRtree?

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
    fun snap(hotPixel: HotPixel, parentEdge: SegmentString? = null, hotPixelVertexIndex: Int = -1): Boolean {
        val pixelEnv = hotPixel.safeEnvelope
        val hotPixelSnapAction = HotPixelSnapAction(hotPixel, parentEdge, hotPixelVertexIndex)
        index!!.query(pixelEnv, object : ItemVisitor {
            override fun visitItem(item: Any?) {
                val testChain = item as MonotoneChain?
                testChain!!.select(pixelEnv, hotPixelSnapAction)
            }
        }
        )
        return hotPixelSnapAction.isNodeAdded
    }

    class HotPixelSnapAction(
        private val hotPixel: HotPixel, private val parentEdge: SegmentString?, // is -1 if hotPixel is not a vertex
        private val hotPixelVertexIndex: Int
    ) : MonotoneChainSelectAction() {
        /**
         * Reports whether the HotPixel caused a node to be added in any target
         * segmentString (including its own). If so, the HotPixel must be added as a
         * node as well.
         *
         * @return true if a node was added in any target segmentString.
         */
        var isNodeAdded = false
            private set

        /**
         * Check if a segment of the monotone chain intersects
         * the hot pixel vertex and introduce a snap node if so.
         * Optimized to avoid noding segments which
         * contain the vertex (which otherwise
         * would cause every vertex to be noded).
         */
        override fun select(mc: MonotoneChain, startIndex: Int) {
            val ss = mc.context as NodedSegmentString?
            /**
             * Check to avoid snapping a hotPixel vertex to the same vertex.
             * This method is called for segments which intersects the
             * hot pixel,
             * so need to check if either end of the segment is equal to the hot pixel
             * and if so, do not snap.
             *
             * Sep 22 2012 - MD - currently do need to snap to every vertex,
             * since otherwise the testCollapse1 test in SnapRoundingTest fails.
             */
            if (parentEdge === ss) {
                // exit if hotpixel is equal to endpoint of target segment
                if (startIndex == hotPixelVertexIndex
                    || startIndex + 1 == hotPixelVertexIndex
                ) return
            }
            // snap and record if a node was created
            isNodeAdded = isNodeAdded or hotPixel.addSnappedNode(ss!!, startIndex)
        }
    }

}