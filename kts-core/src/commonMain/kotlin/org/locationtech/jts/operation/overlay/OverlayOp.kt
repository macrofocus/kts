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
package org.locationtech.jts.operation.overlay

import org.locationtech.jts.algorithm.PointLocator
import org.locationtech.jts.geom.*
import org.locationtech.jts.geomgraph.*
import org.locationtech.jts.legacy.Math.max
import org.locationtech.jts.legacy.Math.min
import org.locationtech.jts.operation.GeometryGraphOperation
import org.locationtech.jts.util.Assert

/**
 * Computes the geometric overlay of two [Geometry]s.  The overlay
 * can be used to determine any boolean combination of the geometries.
 *
 * @version 1.7
 */
class OverlayOp(g0: Geometry, g1: Geometry?) : GeometryGraphOperation(g0, g1!!) {
    private val ptLocator = PointLocator()
    private val geomFact: GeometryFactory
    private var resultGeom: Geometry? = null

    /**
     * Gets the graph constructed to compute the overlay.
     *
     * @return the overlay graph
     */
    val graph: PlanarGraph
    private val edgeList = EdgeList()
    private var resultPolyList: List<Geometry> = ArrayList()
    private var resultLineList: List<Geometry> = ArrayList()
    private var resultPointList: List<Geometry> = ArrayList()

    /**
     * Constructs an instance to compute a single overlay operation
     * for the given geometries.
     *
     * @param g0 the first geometry argument
     * @param g1 the second geometry argument
     */
    init {
        graph = PlanarGraph(OverlayNodeFactory())
        /**
         * Use factory of primary geometry.
         * Note that this does NOT handle mixed-precision arguments
         * where the second arg has greater precision than the first.
         */
        geomFact = g0.factory
    }

    /**
     * Gets the result of the overlay for a given overlay operation.
     *
     *
     * Note: this method can be called once only.
     *
     * @param overlayOpCode the overlay operation to perform
     * @return the compute result geometry
     * @throws TopologyException if a robustness problem is encountered
     */
    fun getResultGeometry(overlayOpCode: Int): Geometry? {
        computeOverlay(overlayOpCode)
        return resultGeom
    }

    private fun computeOverlay(opCode: Int) {
        // copy points from input Geometries.
        // This ensures that any Point geometries
        // in the input are considered for inclusion in the result set
        copyPoints(0)
        copyPoints(1)

        // node the input Geometries
        arg.get(0).computeSelfNodes(li, false)
        arg.get(1).computeSelfNodes(li, false)

        // compute intersections between edges of the two input geometries
        arg.get(0).computeEdgeIntersections(arg.get(1), li, true)
        val baseSplitEdges: MutableList<Edge> = ArrayList()
        arg.get(0).computeSplitEdges(baseSplitEdges)
        arg.get(1).computeSplitEdges(baseSplitEdges)
        val splitEdges: MutableList<Edge> = baseSplitEdges
        // add the noded edges to this result graph
        insertUniqueEdges(baseSplitEdges)
        computeLabelsFromDepths()
        replaceCollapsedEdges()

//Debug.println(edgeList);
        /**
         * Check that the noding completed correctly.
         *
         * This test is slow, but necessary in order to catch robustness failure
         * situations.
         * If an exception is thrown because of a noding failure,
         * then snapping will be performed, which will hopefully avoid the problem.
         * In the future hopefully a faster check can be developed.
         *
         */
        EdgeNodingValidator.checkValid(edgeList.getEdges())
        graph.addEdges(edgeList.getEdges())
        computeLabelling()
        //Debug.printWatch();
        labelIncompleteNodes()
        //Debug.printWatch();
//nodeMap.print(System.out);
        /**
         * The ordering of building the result Geometries is important.
         * Areas must be built before lines, which must be built before points.
         * This is so that lines which are covered by areas are not included
         * explicitly, and similarly for points.
         */
        findResultAreaEdges(opCode)
        cancelDuplicateResultEdges()
        val polyBuilder: PolygonBuilder =
            PolygonBuilder(geomFact)
        polyBuilder.add(graph)
        resultPolyList = polyBuilder.polygons
        val lineBuilder = LineBuilder(this, geomFact, ptLocator)
        resultLineList = lineBuilder.build(opCode)
        val pointBuilder = PointBuilder(this, geomFact, ptLocator)
        resultPointList = pointBuilder.build(opCode)

        // gather the results from all calculations into a single Geometry for the result set
        resultGeom = computeGeometry(resultPointList, resultLineList, resultPolyList, opCode)
    }

    private fun insertUniqueEdges(edges: List<*>) {
        val i = edges.iterator()
        while (i.hasNext()) {
            val e = i.next() as Edge
            insertUniqueEdge(e)
        }
    }

    /**
     * Insert an edge from one of the noded input graphs.
     * Checks edges that are inserted to see if an
     * identical edge already exists.
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
            val depth = existingEdge.getDepth()
            // if this is the first duplicate found for this edge, initialize the depths
            ///*
            if (depth.isNull) {
                depth.add(existingLabel!!)
            }
            //*/
            depth.add(labelToMerge!!)
            existingLabel!!.merge(labelToMerge)
            //Debug.print("inserted edge: "); Debug.println(e);
//Debug.print("existing edge: "); Debug.println(existingEdge);
        } else {  // no matching existing edge was found
            // add this new edge to the list of edges in this graph
            //e.setName(name + edges.size());
            //e.getDepth().add(e.getLabel());
            edgeList.add(e)
        }
    }
    /**
     * If either of the GeometryLocations for the existing label is
     * exactly opposite to the one in the labelToMerge,
     * this indicates a dimensional collapse has happened.
     * In this case, convert the label for that Geometry to a Line label
     */
    /* NOT NEEDED?
  private void checkDimensionalCollapse(Label labelToMerge, Label existingLabel)
  {
    if (existingLabel.isArea() && labelToMerge.isArea()) {
      for (int i = 0; i < 2; i++) {
        if (! labelToMerge.isNull(i)
            &&  labelToMerge.getLocation(i, Position.LEFT)  == existingLabel.getLocation(i, Position.RIGHT)
            &&  labelToMerge.getLocation(i, Position.RIGHT) == existingLabel.getLocation(i, Position.LEFT) )
        {
          existingLabel.toLine(i);
        }
      }
    }
  }
  */
    /**
     * Update the labels for edges according to their depths.
     * For each edge, the depths are first normalized.
     * Then, if the depths for the edge are equal,
     * this edge must have collapsed into a line edge.
     * If the depths are not equal, update the label
     * with the locations corresponding to the depths
     * (i.e. a depth of 0 corresponds to a Location of EXTERIOR,
     * a depth of 1 corresponds to INTERIOR)
     */
    private fun computeLabelsFromDepths() {
        val it = edgeList.iterator()
        while (it.hasNext()) {
            val e = it.next() as Edge
            val lbl = e.label
            val depth = e.getDepth()
            /**
             * Only check edges for which there were duplicates,
             * since these are the only ones which might
             * be the result of dimensional collapses.
             */
            if (!depth.isNull) {
                depth.normalize()
                for (i in 0..1) {
                    if (!lbl!!.isNull(i) && lbl.isArea() && !depth.isNull(i)) {
                        /**
                         * if the depths are equal, this edge is the result of
                         * the dimensional collapse of two or more edges.
                         * It has the same location on both sides of the edge,
                         * so it has collapsed to a line.
                         */
                        if (depth.getDelta(i) == 0) {
                            lbl.toLine(i)
                        } else {
                            /**
                             * This edge may be the result of a dimensional collapse,
                             * but it still has different locations on both sides.  The
                             * label of the edge must be updated to reflect the resultant
                             * side locations indicated by the depth values.
                             */
                            Assert.isTrue(
                                !depth.isNull(i, Position.LEFT),
                                "depth of LEFT side has not been initialized"
                            )
                            lbl.setLocation(i, Position.LEFT, depth.getLocation(i, Position.LEFT))
                            Assert.isTrue(
                                !depth.isNull(i, Position.RIGHT),
                                "depth of RIGHT side has not been initialized"
                            )
                            lbl.setLocation(i, Position.RIGHT, depth.getLocation(i, Position.RIGHT))
                        }
                    }
                }
            }
        }
    }

    /**
     * If edges which have undergone dimensional collapse are found,
     * replace them with a new edge which is a L edge
     */
    private fun replaceCollapsedEdges() {
        val newEdges: MutableList<Edge> = ArrayList()
        val it = edgeList.iterator()
        while (it.hasNext()) {
            val e = it.next() as Edge
            if (e.isCollapsed()) {
//Debug.print(e);
                it.remove()
                newEdges.add(e.getCollapsedEdge())
            }
        }
        edgeList.addAll(newEdges)
    }

    /**
     * Copy all nodes from an arg geometry into this graph.
     * The node label in the arg geometry overrides any previously computed
     * label for that argIndex.
     * (E.g. a node may be an intersection node with
     * a previously computed label of BOUNDARY,
     * but in the original arg Geometry it is actually
     * in the interior due to the Boundary Determination Rule)
     */
    private fun copyPoints(argIndex: Int) {
        val i: Iterator<*> = arg.get(argIndex).getNodeIterator()
        while (i.hasNext()) {
            val graphNode = i.next() as Node
            val newNode = graph.addNode(graphNode.getCoordinate()!!)
            newNode!!.setLabel(argIndex, graphNode.label!!.getLocation(argIndex))
        }
    }

    /**
     * Compute initial labelling for all DirectedEdges at each node.
     * In this step, DirectedEdges will acquire a complete labelling
     * (i.e. one with labels for both Geometries)
     * only if they
     * are incident on a node which has edges for both Geometries
     */
    private fun computeLabelling() {
        val nodeit = graph.getNodes().iterator()
        while (nodeit.hasNext()) {
            val node = nodeit.next() as Node
            //if (node.getCoordinate().equals(new Coordinate(222, 100)) ) Debug.addWatch(node.getEdges());
            node.edges!!.computeLabelling(arg)
        }
        mergeSymLabels()
        updateNodeLabelling()
    }

    /**
     * For nodes which have edges from only one Geometry incident on them,
     * the previous step will have left their dirEdges with no labelling for the other
     * Geometry.  However, the sym dirEdge may have a labelling for the other
     * Geometry, so merge the two labels.
     */
    private fun mergeSymLabels() {
        val nodeit = graph.getNodes().iterator()
        while (nodeit.hasNext()) {
            val node = nodeit.next() as Node
            (node.edges as DirectedEdgeStar?)!!.mergeSymLabels()
        }
    }

    private fun updateNodeLabelling() {
        // update the labels for nodes
        // The label for a node is updated from the edges incident on it
        // (Note that a node may have already been labelled
        // because it is a point in one of the input geometries)
        val nodeit = graph.getNodes().iterator()
        while (nodeit.hasNext()) {
            val node = nodeit.next() as Node
            val lbl = (node.edges as DirectedEdgeStar?)!!.getLabel()
            node.label!!.merge(lbl!!)
        }
    }

    /**
     * Incomplete nodes are nodes whose labels are incomplete.
     * (e.g. the location for one Geometry is null).
     * These are either isolated nodes,
     * or nodes which have edges from only a single Geometry incident on them.
     *
     * Isolated nodes are found because nodes in one graph which don't intersect
     * nodes in the other are not completely labelled by the initial process
     * of adding nodes to the nodeList.
     * To complete the labelling we need to check for nodes that lie in the
     * interior of edges, and in the interior of areas.
     *
     *
     * When each node labelling is completed, the labelling of the incident
     * edges is updated, to complete their labelling as well.
     */
    private fun labelIncompleteNodes() {
        // int nodeCount = 0;
        val ni = graph.getNodes().iterator()
        while (ni.hasNext()) {
            val n = ni.next() as Node
            val label = n.label
            if (n.isIsolated) {
                // nodeCount++;
                if (label!!.isNull(0)) labelIncompleteNode(n, 0) else labelIncompleteNode(n, 1)
            }
            // now update the labelling for the DirectedEdges incident on this node
            (n.edges as DirectedEdgeStar?)!!.updateLabelling(label!!)
        }
        /*
    int nPoly0 = arg[0].getGeometry().getNumGeometries();
    int nPoly1 = arg[1].getGeometry().getNumGeometries();
    System.out.println("# isolated nodes= " + nodeCount 
    		+ "   # poly[0] = " + nPoly0
    		+ "   # poly[1] = " + nPoly1);
    */
    }

    /**
     * Label an isolated node with its relationship to the target geometry.
     */
    private fun labelIncompleteNode(n: Node, targetIndex: Int) {
        val loc = ptLocator.locate(n.getCoordinate()!!, arg[targetIndex].getGeometry()!!)

        // MD - 2008-10-24 - experimental for now
//    int loc = arg[targetIndex].locate(n.getCoordinate());
        n.label!!.setLocation(targetIndex, loc)
    }

    /**
     * Find all edges whose label indicates that they are in the result area(s),
     * according to the operation being performed.  Since we want polygon shells to be
     * oriented CW, choose dirEdges with the interior of the result on the RHS.
     * Mark them as being in the result.
     * Interior Area edges are the result of dimensional collapses.
     * They do not form part of the result area boundary.
     */
    private fun findResultAreaEdges(opCode: Int) {
        val it = graph.getEdgeEnds().iterator()
        while (it.hasNext()) {
            val de = it.next() as DirectedEdge
            // mark all dirEdges with the appropriate label
            val label = de.label
            if (label!!.isArea()
                && !de.isInteriorAreaEdge
                && isResultOfOp(
                    label.getLocation(0, Position.RIGHT),
                    label.getLocation(1, Position.RIGHT),
                    opCode
                )
            ) {
                de.isInResult = true
                //Debug.print("in result "); Debug.println(de);
            }
        }
    }

    /**
     * If both a dirEdge and its sym are marked as being in the result, cancel
     * them out.
     */
    private fun cancelDuplicateResultEdges() {
        // remove any dirEdges whose sym is also included
        // (they "cancel each other out")
        val it = graph.getEdgeEnds().iterator()
        while (it.hasNext()) {
            val de = it.next() as DirectedEdge
            val sym = de.sym
            if (de.isInResult && sym!!.isInResult) {
                de.isInResult = false
                sym.isInResult = false
                //Debug.print("cancelled "); Debug.println(de); Debug.println(sym);
            }
        }
    }

    /**
     * Tests if a point node should be included in the result or not.
     *
     * @param coord the point coordinate
     * @return true if the coordinate point is covered by a result Line or Area geometry
     */
    fun isCoveredByLA(coord: Coordinate): Boolean {
        if (isCovered(coord, resultLineList)) return true
        return if (isCovered(coord, resultPolyList)) true else false
    }

    /**
     * Tests if an L edge should be included in the result or not.
     *
     * @param coord the point coordinate
     * @return true if the coordinate point is covered by a result Area geometry
     */
    fun isCoveredByA(coord: Coordinate): Boolean {
        return if (isCovered(coord, resultPolyList)) true else false
    }

    /**
     * @return true if the coord is located in the interior or boundary of
     * a geometry in the list.
     */
    private fun isCovered(coord: Coordinate, geomList: List<*>?): Boolean {
        val it = geomList!!.iterator()
        while (it.hasNext()) {
            val geom = it.next() as Geometry
            val loc = ptLocator.locate(coord, geom)
            if (loc != Location.EXTERIOR) return true
        }
        return false
    }

    private fun computeGeometry(
        resultPointList: List<Geometry>,
        resultLineList: List<Geometry>,
        resultPolyList: List<Geometry>,
        opcode: Int
    ): Geometry {
        val geomList: MutableList<Geometry> = ArrayList()
        // element geometries of the result are always in the order P,L,A
        geomList.addAll(resultPointList)
        geomList.addAll(resultLineList)
        geomList.addAll(resultPolyList)

        //*
        return if (geomList.isEmpty()) createEmptyResult(
            opcode,
            arg[0].getGeometry()!!,
            arg[1].getGeometry()!!,
            geomFact
        ) else geomFact.buildGeometry(geomList)
        //*/

        // build the most specific geometry possible
    }

    companion object {
        /**
         * The spatial functions supported by this class.
         * These operations implement various boolean combinations of the resultants of the overlay.
         */
        /**
         * The code for the Intersection overlay operation.
         */
        const val INTERSECTION = 1

        /**
         * The code for the Union overlay operation.
         */
        const val UNION = 2

        /**
         * The code for the Difference overlay operation.
         */
        const val DIFFERENCE = 3

        /**
         * The code for the Symmetric Difference overlay operation.
         */
        const val SYMDIFFERENCE = 4

        /**
         * Computes an overlay operation for
         * the given geometry arguments.
         *
         * @param geom0 the first geometry argument
         * @param geom1 the second geometry argument
         * @param opCode the code for the desired overlay operation
         * @return the result of the overlay operation
         * @throws TopologyException if a robustness problem is encountered
         */
        fun overlayOp(
            geom0: Geometry,
            geom1: Geometry?,
            opCode: Int
        ): Geometry? {
            val gov =
                OverlayOp(geom0, geom1)
            return gov.getResultGeometry(opCode)
        }

        /**
         * Tests whether a point with a given topological [Label]
         * relative to two geometries is contained in
         * the result of overlaying the geometries using
         * a given overlay operation.
         *
         *
         * The method handles arguments of [Location.NONE] correctly
         *
         * @param label the topological label of the point
         * @param opCode the code for the overlay operation to test
         * @return true if the label locations correspond to the overlayOpCode
         */
        fun isResultOfOp(label: Label, opCode: Int): Boolean {
            val loc0 = label.getLocation(0)
            val loc1 = label.getLocation(1)
            return isResultOfOp(loc0, loc1, opCode)
        }

        /**
         * Tests whether a point with given [Location]s
         * relative to two geometries is contained in
         * the result of overlaying the geometries using
         * a given overlay operation.
         *
         *
         * The method handles arguments of [Location.NONE] correctly
         *
         * @param loc0 the code for the location in the first geometry
         * @param loc1 the code for the location in the second geometry
         * @param overlayOpCode the code for the overlay operation to test
         * @return true if the locations correspond to the overlayOpCode
         */
        fun isResultOfOp(loc0: Int, loc1: Int, overlayOpCode: Int): Boolean {
            var loc0 = loc0
            var loc1 = loc1
            if (loc0 == Location.BOUNDARY) loc0 = Location.INTERIOR
            if (loc1 == Location.BOUNDARY) loc1 = Location.INTERIOR
            when (overlayOpCode) {
                INTERSECTION -> return (loc0 == Location.INTERIOR
                        && loc1 == Location.INTERIOR)

                UNION -> return (loc0 == Location.INTERIOR
                        || loc1 == Location.INTERIOR)

                DIFFERENCE -> return (loc0 == Location.INTERIOR
                        && loc1 != Location.INTERIOR)

                SYMDIFFERENCE -> return loc0 == Location.INTERIOR && loc1 != Location.INTERIOR || loc0 != Location.INTERIOR && loc1 == Location.INTERIOR
            }
            return false
        }

        /**
         * Creates an empty result geometry of the appropriate dimension,
         * based on the given overlay operation and the dimensions of the inputs.
         * The created geometry is always an atomic geometry,
         * not a collection.
         *
         *
         * The empty result is constructed using the following rules:
         *
         *  * [.INTERSECTION] - result has the dimension of the lowest input dimension
         *  * [.UNION] - result has the dimension of the highest input dimension
         *  * [.DIFFERENCE] - result has the dimension of the left-hand input
         *  * [.SYMDIFFERENCE] - result has the dimension of the highest input dimension
         * (since the symmetric Difference is the union of the differences).
         *
         *
         * @param overlayOpCode the code for the overlay operation being performed
         * @param a an input geometry
         * @param b an input geometry
         * @param geomFact the geometry factory being used for the operation
         * @return an empty atomic geometry of the appropriate dimension
         */
        fun createEmptyResult(overlayOpCode: Int, a: Geometry, b: Geometry, geomFact: GeometryFactory): Geometry {
            var result: Geometry? = null
            val resultDim = resultDimension(overlayOpCode, a, b)
            /**
             * Handles resultSDim = -1, although should not happen
             */
            return geomFact.createEmpty(resultDim).also { result = it }
        }

        private fun resultDimension(opCode: Int, g0: Geometry, g1: Geometry): Int {
            val dim0 = g0.dimension
            val dim1 = g1.dimension
            var resultDimension = -1
            when (opCode) {
                INTERSECTION -> resultDimension = min(dim0, dim1)
                UNION -> resultDimension = max(dim0, dim1)
                DIFFERENCE -> resultDimension = dim0
                SYMDIFFERENCE ->
                    /**
                     * This result is chosen because
                     * <pre>
                     * SymDiff = Union(Diff(A, B), Diff(B, A)
                    </pre> *
                     * and Union has the dimension of the highest-dimension argument.
                     */
                    resultDimension = max(dim0, dim1)
            }
            return resultDimension
        }
    }
}