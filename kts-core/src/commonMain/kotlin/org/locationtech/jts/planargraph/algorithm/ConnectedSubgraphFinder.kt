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
package org.locationtech.jts.planargraph.algorithm

import org.locationtech.jts.legacy.Stack
import org.locationtech.jts.legacy.empty
import org.locationtech.jts.legacy.pop
import org.locationtech.jts.legacy.push
import org.locationtech.jts.planargraph.*

/**
 * Finds all connected [Subgraph]s of a [PlanarGraph].
 *
 *
 * **Note:** uses the `isVisited` flag on the nodes.
 */
class ConnectedSubgraphFinder(private val graph: PlanarGraph) {
    val connectedSubgraphs: MutableList<Subgraph>
        get() {
            val subgraphs: MutableList<Subgraph> = ArrayList()
            GraphComponent.setVisited(graph.nodeIterator(), false)
            val i = graph.edgeIterator()
            while (i.hasNext()) {
                val e = i.next() as Edge
                val node = e.getDirEdge(0).fromNode
                if (!node.isVisited) {
                    subgraphs.add(findSubgraph(node))
                }
            }
            return subgraphs
        }

    private fun findSubgraph(node: Node): Subgraph {
        val subgraph = Subgraph(graph)
        addReachable(node, subgraph)
        return subgraph
    }

    /**
     * Adds all nodes and edges reachable from this node to the subgraph.
     * Uses an explicit stack to avoid a large depth of recursion.
     *
     * @param node a node known to be in the subgraph
     */
    private fun addReachable(startNode: Node, subgraph: Subgraph) {
        val nodeStack: Stack<Node> = ArrayList()
        nodeStack.add(startNode)
        while (!nodeStack.empty()) {
            val node = nodeStack.pop() as Node
            addEdges(node, nodeStack, subgraph)
        }
    }

    /**
     * Adds the argument node and all its out edges to the subgraph.
     * @param node the node to add
     * @param nodeStack the current set of nodes being traversed
     */
    private fun addEdges(node: Node, nodeStack: Stack<Node>, subgraph: Subgraph) {
        node.isVisited = true
        val i: Iterator<*> = node.outEdges.iterator()
        while (i.hasNext()) {
            val de = i.next() as DirectedEdge
            subgraph.add(de.edge!!)
            val toNode = de.toNode
            if (!toNode.isVisited) nodeStack.push(toNode)
        }
    }
}