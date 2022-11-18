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
package org.locationtech.jts.operation.buffer

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Position
import org.locationtech.jts.geom.TopologyException
import org.locationtech.jts.geomgraph.DirectedEdge
import org.locationtech.jts.geomgraph.DirectedEdgeStar
import org.locationtech.jts.geomgraph.Label
import org.locationtech.jts.geomgraph.Node
import org.locationtech.jts.legacy.*

/**
 * @version 1.7
 */
/**
 * A connected subset of the graph of
 * [DirectedEdge]s and [Node]s.
 * Its edges will generate either
 *
 *  *  a single polygon in the complete buffer, with zero or more holes, or
 *  *  one or more connected holes
 *
 *
 * @version 1.7
 */
internal class BufferSubgraph : Comparable<Any?> {
    private val finder: RightmostEdgeFinder
    private val dirEdgeList: MutableList<DirectedEdge> = ArrayList()
    private val nodes: MutableList<Node> = ArrayList()

    /**
     * Gets the rightmost coordinate in the edges of the subgraph
     */
    var rightmostCoordinate: Coordinate? = null
        private set
    private var env: Envelope? = null

    init {
        finder = RightmostEdgeFinder()
    }

    val directedEdges: MutableList<DirectedEdge>
        get() = dirEdgeList

    fun getNodes(): MutableList<Node> {
        return nodes
    }

    /**
     * Computes the envelope of the edges in the subgraph.
     * The envelope is cached after being computed.
     *
     * @return the envelope of the graph.
     */
    val envelope: Envelope?
        get() {
            if (env == null) {
                val edgeEnv = Envelope()
                val it: Iterator<*> = dirEdgeList.iterator()
                while (it.hasNext()) {
                    val dirEdge = it.next() as DirectedEdge
                    val pts = dirEdge.edge.getCoordinates()
                    for (i in 0 until pts.size - 1) {
                        edgeEnv.expandToInclude(pts[i]!!)
                    }
                }
                env = edgeEnv
            }
            return env
        }

    /**
     * Creates the subgraph consisting of all edges reachable from this node.
     * Finds the edges in the graph and the rightmost coordinate.
     *
     * @param node a node to start the graph traversal from
     */
    fun create(node: Node) {
        addReachable(node)
        finder.findEdge(dirEdgeList)
        rightmostCoordinate = finder.coordinate
    }

    /**
     * Adds all nodes and edges reachable from this node to the subgraph.
     * Uses an explicit stack to avoid a large depth of recursion.
     *
     * @param node a node known to be in the subgraph
     */
    private fun addReachable(startNode: Node) {
        val nodeStack: Stack<Node> = ArrayList()
        nodeStack.add(startNode)
        while (!nodeStack.empty()) {
            val node = nodeStack.pop() as Node
            add(node, nodeStack)
        }
    }

    /**
     * Adds the argument node and all its out edges to the subgraph
     * @param node the node to add
     * @param nodeStack the current set of nodes being traversed
     */
    private fun add(node: Node, nodeStack: Stack<Node>) {
        node.isVisited = true
        nodes.add(node)
        val i = (node.edges as DirectedEdgeStar?)!!.iterator()
        while (i.hasNext()) {
            val de = i.next() as DirectedEdge
            dirEdgeList.add(de)
            val sym = de.sym
            val symNode: Node = sym!!.node!!
            /**
             * NOTE: this is a depth-first traversal of the graph.
             * This will cause a large depth of recursion.
             * It might be better to do a breadth-first traversal.
             */
            if (!symNode.isVisited) nodeStack.push(symNode)
        }
    }

    private fun clearVisitedEdges() {
        val it: Iterator<*> = dirEdgeList.iterator()
        while (it.hasNext()) {
            val de = it.next() as DirectedEdge
            de.isVisited = false
        }
    }

    fun computeDepth(outsideDepth: Int) {
        clearVisitedEdges()
        // find an outside edge to assign depth to
        val de: DirectedEdge = finder.edge!!
        val n: Node = de.node!!
        val label: Label = de.label!!
        // right side of line returned by finder is on the outside
        de.setEdgeDepths(Position.RIGHT, outsideDepth)
        copySymDepths(de)

        //computeNodeDepth(n, de);
        computeDepths(de)
    }

    /**
     * Compute depths for all dirEdges via breadth-first traversal of nodes in graph
     * @param startEdge edge to start processing with
     */
    // <FIX> MD - use iteration & queue rather than recursion, for speed and robustness
    private fun computeDepths(startEdge: DirectedEdge?) {
        val nodesVisited: MutableSet<Node> = HashSet()
        val nodeQueue: LinkedList<Node> = LinkedList()
        val startNode: Node = startEdge!!.node!!
        nodeQueue.addLast(startNode)
        nodesVisited.add(startNode)
        startEdge!!.isVisited = true
        while (!nodeQueue.isEmpty()) {
//System.out.println(nodes.size() + " queue: " + nodeQueue.size());
            val n = nodeQueue.removeFirst() as Node
            nodesVisited.add(n)
            // compute depths around node, starting at this edge since it has depths assigned
            computeNodeDepth(n)

            // add all adjacent nodes to process queue,
            // unless the node has been visited already
            val i = (n.edges as DirectedEdgeStar?)!!.iterator()
            while (i.hasNext()) {
                val de = i.next() as DirectedEdge
                val sym = de.sym
                if (sym!!.isVisited) {
                    continue
                }
                val adjNode: Node = sym.node!!
                if (!nodesVisited.contains(adjNode)) {
                    nodeQueue.addLast(adjNode)
                    nodesVisited.add(adjNode)
                }
            }
        }
    }

    private fun computeNodeDepth(n: Node) {
        // find a visited dirEdge to start at
        var startEdge: DirectedEdge? = null
        run {
            val i = (n.edges as DirectedEdgeStar?)!!.iterator()
            while (i.hasNext()) {
                val de = i.next() as DirectedEdge
                if (de.isVisited || de.sym!!.isVisited) {
                    startEdge = de
                    break
                }
            }
        }
        // MD - testing  Result: breaks algorithm
        //if (startEdge == null) return;

        // only compute string append if assertion would fail
        if (startEdge == null) throw TopologyException("unable to find edge to compute depths at " + n.getCoordinate())
        (n.edges as DirectedEdgeStar?)!!.computeDepths(startEdge!!)

        // copy depths to sym edges
        val i = (n.edges as DirectedEdgeStar?)!!.iterator()
        while (i.hasNext()) {
            val de = i.next() as DirectedEdge
            de.isVisited = true
            copySymDepths(de)
        }
    }

    private fun copySymDepths(de: DirectedEdge?) {
        val sym = de!!.sym
        sym!!.setDepth(Position.LEFT, de.getDepth(Position.RIGHT))
        sym.setDepth(Position.RIGHT, de.getDepth(Position.LEFT))
    }

    /**
     * Find all edges whose depths indicates that they are in the result area(s).
     * Since we want polygon shells to be
     * oriented CW, choose dirEdges with the interior of the result on the RHS.
     * Mark them as being in the result.
     * Interior Area edges are the result of dimensional collapses.
     * They do not form part of the result area boundary.
     */
    fun findResultEdges() {
        val it: Iterator<*> = dirEdgeList.iterator()
        while (it.hasNext()) {
            val de = it.next() as DirectedEdge
            /**
             * Select edges which have an interior depth on the RHS
             * and an exterior depth on the LHS.
             * Note that because of weird rounding effects there may be
             * edges which have negative depths!  Negative depths
             * count as "outside".
             */
            // <FIX> - handle negative depths
            if (de.getDepth(Position.RIGHT) >= 1 && de.getDepth(Position.LEFT) <= 0 && !de.isInteriorAreaEdge) {
                de.isInResult = true
                //Debug.print("in result "); Debug.println(de);
            }
        }
    }

    /**
     * BufferSubgraphs are compared on the x-value of their rightmost Coordinate.
     * This defines a partial ordering on the graphs such that:
     *
     *
     * g1 >= g2 <==> Ring(g2) does not contain Ring(g1)
     *
     *
     * where Polygon(g) is the buffer polygon that is built from g.
     *
     *
     * This relationship is used to sort the BufferSubgraphs so that shells are guaranteed to
     * be built before holes.
     */
    override fun compareTo(o: Any?): Int {
        val graph = o as BufferSubgraph?
        if (rightmostCoordinate!!.x < graph!!.rightmostCoordinate!!.x) {
            return -1
        }
        return if (rightmostCoordinate!!.x > graph.rightmostCoordinate!!.x) {
            1
        } else 0
    } /*
// DEBUGGING only - comment out
  private static final String SAVE_DIREDGES = "saveDirEdges";
  private static int saveCount = 0;
  public void saveDirEdges()
  {
    GeometryFactory fact = new GeometryFactory();
    for (Iterator it = dirEdgeList.iterator(); it.hasNext(); ) {
      DirectedEdge de = (DirectedEdge) it.next();
      double dx = de.getDx();
      double dy = de.getDy();
      Coordinate p0 = de.getCoordinate();
      double ang = Math.atan2(dy, dx);
      Coordinate p1 = new Coordinate(
          p0.x + .4 * Math.cos(ang),
          p0.y + .4 * Math.sin(ang));
//      DebugFeature.add(SAVE_DIREDGES,
//                       fact.createLineString(new Coordinate[] { p0, p1 } ),
//                       de.getDepth(Position.LEFT) + "/" + de.getDepth(Position.RIGHT)
//                       );
    }
  String filepath = "x:\\jts\\testBuffer\\dirEdges" + saveCount++ + ".jml";
    DebugFeature.saveFeatures(SAVE_DIREDGES, filepath);
  }
  */
}