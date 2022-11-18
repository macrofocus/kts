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
package org.locationtech.jts.geomgraph

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Location
import org.locationtech.jts.legacy.map.TreeMap

/**
 * A map of nodes, indexed by the coordinate of the node
 * @version 1.7
 */
class NodeMap(nodeFact: NodeFactory) {
    //Map nodeMap = new HashMap();
    var nodeMap: MutableMap<Coordinate, Node> = TreeMap()
    var nodeFact: NodeFactory

    init {
        this.nodeFact = nodeFact
    }
    /**
     * Factory function - subclasses can override to create their own types of nodes
     */
    /*
  protected Node createNode(Coordinate coord)
  {
    return new Node(coord);
  }
  */
    /**
     * This method expects that a node has a coordinate value.
     * @param coord Coordinate
     * @return node for the provided coord
     */
    fun addNode(coord: Coordinate): Node {
        var node: Node? = nodeMap[coord] as Node?
        if (node == null) {
            node = nodeFact.createNode(coord)
            nodeMap[coord] = node
        }
        return node
    }

    fun addNode(n: Node): Node {
        val node: Node? =
            nodeMap[n.getCoordinate()] as Node?
        if (node == null) {
            nodeMap[n.getCoordinate()!!] = n
            return n
        }
        node.mergeLabel(n)
        return node
    }

    /**
     * Adds a node for the start point of this EdgeEnd
     * (if one does not already exist in this map).
     * Adds the EdgeEnd to the (possibly new) node.
     *
     * @param e EdgeEnd
     */
    fun add(e: EdgeEnd) {
        val p: Coordinate = e.coordinate!!
        val n: Node = addNode(p)
        n!!.add(e)
    }

    /**
     * Find coordinate.
     *
     * @param coord Coordinate to find
     * @return the node if found; null otherwise
     */
    fun find(coord: Coordinate?): Node? {
        return nodeMap[coord] as Node?
    }

    operator fun iterator(): Iterator<*> {
        return nodeMap.values.iterator()
    }

    fun values(): Collection<*> {
        return nodeMap.values
    }

    fun getBoundaryNodes(geomIndex: Int): Collection<*> {
        val bdyNodes: MutableCollection<Any?> = ArrayList()
        val i = iterator()
        while (i.hasNext()) {
            val node: Node = i.next() as Node
            if (node.label!!.getLocation(geomIndex) === Location.BOUNDARY) bdyNodes.add(node)
        }
        return bdyNodes
    }

//    fun print(out: java.io.PrintStream) {
//        val it = iterator()
//        while (it.hasNext()) {
//            val n: Node = it.next() as Node
//            n.print(out)
//        }
//    }
}