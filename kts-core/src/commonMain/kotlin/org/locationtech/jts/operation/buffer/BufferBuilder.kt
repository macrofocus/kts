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
package org.locationtech.jts.operation.buffer

import org.locationtech.jts.algorithm.LineIntersector
import org.locationtech.jts.algorithm.RobustLineIntersector
import org.locationtech.jts.geom.*
import org.locationtech.jts.geomgraph.*
import org.locationtech.jts.noding.IntersectionAdder
import org.locationtech.jts.noding.MCIndexNoder
import org.locationtech.jts.noding.Noder
import org.locationtech.jts.noding.SegmentString
import org.locationtech.jts.operation.overlay.OverlayNodeFactory
import org.locationtech.jts.operation.overlay.PolygonBuilder

/**
 * @version 1.7
 */
/**
 * Builds the buffer geometry for a given input geometry and precision model.
 * Allows setting the level of approximation for circular arcs,
 * and the precision model in which to carry out the computation.
 *
 * When computing buffers in floating point double-precision
 * it can happen that the process of iterated noding can fail to converge (terminate).
 * In this case a [TopologyException] will be thrown.
 * Retrying the computation in a fixed precision
 * can produce more robust results.
 *
 * @version 1.7
 */
internal class BufferBuilder
/**
 * Creates a new BufferBuilder,
 * using the given parameters.
 *
 * @param bufParams the buffer parameters to use
 */(private val bufParams: BufferParameters) {
    private var workingPrecisionModel: PrecisionModel? = null
    private var workingNoder: Noder? = null
    private var geomFact: GeometryFactory? = null
    private var graph: PlanarGraph? = null
    private val edgeList = EdgeList()

    /**
     * Sets the precision model to use during the curve computation and noding,
     * if it is different to the precision model of the Geometry.
     * If the precision model is less than the precision of the Geometry precision model,
     * the Geometry must have previously been rounded to that precision.
     *
     * @param pm the precision model to use
     */
    fun setWorkingPrecisionModel(pm: PrecisionModel?) {
        workingPrecisionModel = pm
    }

    /**
     * Sets the [Noder] to use during noding.
     * This allows choosing fast but non-robust noding, or slower
     * but robust noding.
     *
     * @param noder the noder to use
     */
    fun setNoder(noder: Noder?) {
        workingNoder = noder
    }

    fun buffer(g: Geometry, distance: Double): Geometry {
        var precisionModel = workingPrecisionModel
        if (precisionModel == null) precisionModel = g.precisionModel

        // factory must be the same as the one used by the input
        geomFact = g.factory
        val curveBuilder = OffsetCurveBuilder(precisionModel, bufParams)
        val curveSetBuilder = OffsetCurveSetBuilder(g, distance, curveBuilder)
        val bufferSegStrList = curveSetBuilder.curves

        // short-circuit test
        if (bufferSegStrList.isEmpty()) {
            return createEmptyResultGeometry()
        }

//BufferDebug.runCount++;
//String filename = "run" + BufferDebug.runCount + "_curves";
//System.out.println("saving " + filename);
//BufferDebug.saveEdges(bufferEdgeList, filename);
// DEBUGGING ONLY
//WKTWriter wktWriter = new WKTWriter();
//Debug.println("Rings: " + wktWriter.write(convertSegStrings(bufferSegStrList.iterator())));
//wktWriter.setMaxCoordinatesPerLine(10);
//System.out.println(wktWriter.writeFormatted(convertSegStrings(bufferSegStrList.iterator())));
        computeNodedEdges(bufferSegStrList, precisionModel)
        graph = PlanarGraph(OverlayNodeFactory())
        graph!!.addEdges(edgeList.getEdges())
        val subgraphList = createSubgraphs(graph!!)
        val polyBuilder = PolygonBuilder(geomFact!!)
        buildSubgraphs(subgraphList, polyBuilder)
        val resultPolyList = polyBuilder.polygons

        // just in case...
        return if (resultPolyList.isEmpty()) {
            createEmptyResultGeometry()
        } else geomFact!!.buildGeometry(resultPolyList)
    }

    private fun getNoder(precisionModel: PrecisionModel): Noder {
        if (workingNoder != null) return workingNoder!!

        // otherwise use a fast (but non-robust) noder
        val noder = MCIndexNoder()
        val li: LineIntersector = RobustLineIntersector()
        li.precisionModel = precisionModel
        noder.setSegmentIntersector(IntersectionAdder(li))
        //    Noder noder = new IteratedNoder(precisionModel);
        return noder
        //    Noder noder = new SimpleSnapRounder(precisionModel);
//    Noder noder = new MCIndexSnapRounder(precisionModel);
//    Noder noder = new ScaledNoder(new MCIndexSnapRounder(new PrecisionModel(1.0)),
//                                  precisionModel.getScale());
    }

    private fun computeNodedEdges(bufferSegStrList: List<*>, precisionModel: PrecisionModel) {
        val noder = getNoder(precisionModel)
        noder.computeNodes(bufferSegStrList)
        val nodedSegStrings = noder.nodedSubstrings
        // DEBUGGING ONLY
//BufferDebug.saveEdges(nodedEdges, "run" + BufferDebug.runCount + "_nodedEdges");
        val i = nodedSegStrings.iterator()
        while (i.hasNext()) {
            val segStr = i.next() as SegmentString

            /**
             * Discard edges which have zero length,
             * since they carry no information and cause problems with topology building
             */
            val pts = segStr.coordinates
            if (pts.size == 2 && pts[0].equals2D(pts[1])) {
                continue
            }
            val oldLabel = segStr.getData() as Label?
            val edge = Edge(
                segStr.coordinates, Label(
                    oldLabel!!
                )
            )
            insertUniqueEdge(edge)
        }
        //saveEdges(edgeList.getEdges(), "run" + runCount + "_collapsedEdges");
    }

    /**
     * Inserted edges are checked to see if an identical edge already exists.
     * If so, the edge is not inserted, but its label is merged
     * with the existing edge.
     */
    protected fun insertUniqueEdge(e: Edge) {
//<FIX> MD 8 Oct 03  speed up identical edge lookup
        // fast lookup
        val existingEdge = edgeList.findEqualEdge(e)

        // If an identical edge already exists, simply update its label
        if (existingEdge != null) {
            val existingLabel = existingEdge.label
            var labelToMerge = e.label
            // check if new edge is in reverse direction to existing edge
            // if so, must flip the label before merging it
            if (!existingEdge.isPointwiseEqual(e)) {
                labelToMerge = Label(e.label!!)
                labelToMerge.flip()
            }
            existingLabel!!.merge(labelToMerge!!)

            // compute new depth delta of sum of edges
            val mergeDelta = depthDelta(labelToMerge)
            val existingDelta = existingEdge.depthDelta
            val newDelta = existingDelta + mergeDelta
            existingEdge.depthDelta = newDelta
        } else {   // no matching existing edge was found
            // add this new edge to the list of edges in this graph
            //e.setName(name + edges.size());
            edgeList.add(e)
            e.depthDelta = depthDelta(e.label)
        }
    }

    private fun createSubgraphs(graph: PlanarGraph): List<*> {
        val subgraphList: MutableList<BufferSubgraph> = ArrayList()
        val i = graph.getNodes().iterator()
        while (i.hasNext()) {
            val node = i.next() as Node
            if (!node.isVisited) {
                val subgraph = BufferSubgraph()
                subgraph.create(node)
                subgraphList.add(subgraph)
            }
        }
        /**
         * Sort the subgraphs in descending order of their rightmost coordinate.
         * This ensures that when the Polygons for the subgraphs are built,
         * subgraphs for shells will have been built before the subgraphs for
         * any holes they contain.
         */
        subgraphList.sortWith(reverseOrder())
        return subgraphList
    }

    /**
     * Completes the building of the input subgraphs by depth-labelling them,
     * and adds them to the PolygonBuilder.
     * The subgraph list must be sorted in rightmost-coordinate order.
     *
     * @param subgraphList the subgraphs to build
     * @param polyBuilder the PolygonBuilder which will build the final polygons
     */
    private fun buildSubgraphs(subgraphList: List<*>, polyBuilder: PolygonBuilder) {
        val processedGraphs: MutableList<Any?> = ArrayList()
        val i = subgraphList.iterator()
        while (i.hasNext()) {
            val subgraph = i.next() as BufferSubgraph
            val p = subgraph.rightmostCoordinate
            //      int outsideDepth = 0;
//      if (polyBuilder.containsPoint(p))
//        outsideDepth = 1;
            val locater = SubgraphDepthLocater(processedGraphs)
            val outsideDepth = locater.getDepth(p)
            //      try {
            subgraph.computeDepth(outsideDepth)
            //      }
//      catch (RuntimeException ex) {
//        // debugging only
//        //subgraph.saveDirEdges();
//        throw ex;
//      }
            subgraph.findResultEdges()
            processedGraphs.add(subgraph)
            polyBuilder.add(subgraph.directedEdges, subgraph.getNodes())
        }
    }

    /**
     * Gets the standard result for an empty buffer.
     * Since buffer always returns a polygonal result,
     * this is chosen to be an empty polygon.
     *
     * @return the empty result geometry
     */
    private fun createEmptyResultGeometry(): Geometry {
        return geomFact!!.createPolygon()
    }

    companion object {
        /**
         * Compute the change in depth as an edge is crossed from R to L
         */
        private fun depthDelta(label: Label?): Int {
            val lLoc = label!!.getLocation(0, Position.LEFT)
            val rLoc = label.getLocation(0, Position.RIGHT)
            if (lLoc == Location.INTERIOR && rLoc == Location.EXTERIOR) return 1 else if (lLoc == Location.EXTERIOR && rLoc == Location.INTERIOR) return -1
            return 0
        }

        private fun convertSegStrings(it: Iterator<*>): Geometry {
            val fact = GeometryFactory()
            val lines: MutableList<Geometry> = ArrayList()
            while (it.hasNext()) {
                val ss = it.next() as SegmentString
                val line = fact.createLineString(ss.coordinates)
                lines.add(line)
            }
            return fact.buildGeometry(lines)
        }
    }
}