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
package org.locationtech.jts.geomgraph

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Location
import org.locationtech.jts.legacy.map.TreeMap

/**
 * A map of nodes, indexed by the coordinate of the node
 * @version 1.7
 */
class NodeMap(var nodeFact: NodeFactory) {
    //Map nodeMap = new HashMap();
    var nodeMap: MutableMap<Coordinate, Any?> = TreeMap()
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
     */
    fun addNode(coord: Coordinate): Node {
        var node = nodeMap[coord] as Node?
        if (node == null) {
            node = nodeFact.createNode(coord)
            nodeMap[coord] = node
        }
        return node
    }

    fun addNode(n: Node): Node {
        val node = nodeMap[n.coordinate] as Node?
        if (node == null) {
            nodeMap[n.coordinate] = n
            return n
        }
        node.mergeLabel(n)
        return node
    }

    /**
     * Adds a node for the start point of this EdgeEnd
     * (if one does not already exist in this map).
     * Adds the EdgeEnd to the (possibly new) node.
     */
    fun add(e: EdgeEnd) {
        val p = e.coordinate
        val n = addNode(p!!)
        n.add(e)
    }

    /**
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
            val node = i.next() as Node
            if (node.label!!.getLocation(geomIndex) == Location.BOUNDARY) bdyNodes.add(node)
        }
        return bdyNodes
    }

//    fun print(out: PrintStream?) {
//        val it = iterator()
//        while (it.hasNext()) {
//            val n = it.next() as Node
//            n.print(out!!)
//        }
//    }
}