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
package org.locationtech.jts.index.kdtree

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.CoordinateList
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.legacy.pop
import org.locationtech.jts.legacy.push
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * An implementation of a
 * [KD-Tree](https://en.wikipedia.org/wiki/K-d_tree)
 * over two dimensions (X and Y).
 * KD-trees provide fast range searching and fast lookup for point data.
 * The tree is built dynamically by inserting points.
 * The tree supports queries by range and for point equality.
 * For querying an internal stack is used instead of recursion to avoid overflow.
 *
 * This implementation supports detecting and snapping points which are closer
 * than a given distance tolerance.
 * If the same point (up to tolerance) is inserted
 * more than once, it is snapped to the existing node.
 * In other words, if a point is inserted which lies
 * within the tolerance of a node already in the index,
 * it is snapped to that node.
 * When an inserted point is snapped to a node then a new node is not created
 * but the count of the existing node is incremented.
 * If more than one node in the tree is within tolerance of an inserted point,
 * the closest and then lowest node is snapped to.
 *
 * The structure of a KD-Tree depends on the order of insertion of the points.
 * A tree may become unbalanced if the inserted points are coherent
 * (e.g. monotonic in one or both dimensions).
 * A perfectly balanced tree has depth of only log2(N),
 * but an unbalanced tree may be much deeper.
 * This has a serious impact on query efficiency.
 * One solution to this is to randomize the order of points before insertion
 * (e.g. by using [Fisher-Yates shuffling](https://en.wikipedia.org/wiki/Fisher%E2%80%93Yates_shuffle)).
 *
 * @author David Skea
 * @author Martin Davis
 */
class KdTree
/**
 * Creates a new instance of a KdTree with a snapping tolerance of 0.0. (I.e.
 * distinct points will *not* be snapped)
 */ @JvmOverloads constructor(private val tolerance: Double = 0.0) {
    private var root: KdNode? = null
    private var numberOfNodes: Long = 0
    /**
     * Creates a new instance of a KdTree, specifying a snapping distance
     * tolerance. Points which lie closer than the tolerance to a point already in
     * the tree will be treated as identical to the existing point.
     *
     * @param tolerance
     * the tolerance distance for considering two points equal
     */
    /**
     * Gets the root node of this tree.
     *
     * @return the root node of the tree
     */
    fun getRoot(): KdNode? {
        return root
    }

    /**
     * Tests whether the index contains any items.
     *
     * @return true if the index does not contain any items
     */
    val isEmpty: Boolean
        get() = root == null
    /**
     * Inserts a new point into the kd-tree.
     *
     * @param p
     * the point to insert
     * @param data
     * a data item for the point
     * @return returns a new KdNode if a new point is inserted, else an existing
     * node is returned with its counter incremented. This can be checked
     * by testing returnedNode.getCount() &gt; 1.
     */
    /**
     * Inserts a new point in the kd-tree, with no data.
     *
     * @param p
     * the point to insert
     * @return the kdnode containing the point
     */
    @JvmOverloads
    fun insert(p: Coordinate, data: Any? = null): KdNode {
        if (root == null) {
            root = KdNode(p, data)
            return root!!
        }
        /**
         * Check if the point is already in the tree, up to tolerance.
         * If tolerance is zero, this phase of the insertion can be skipped.
         */
        if (tolerance > 0) {
            val matchNode: KdNode? = findBestMatchNode(p)
            if (matchNode != null) {
                // point already in index - increment counter
                matchNode.increment()
                return matchNode
            }
        }
        return insertExact(p, data)
    }

    /**
     * Finds the node in the tree which is the best match for a point
     * being inserted.
     * The match is made deterministic by returning the lowest of any nodes which
     * lie the same distance from the point.
     * There may be no match if the point is not within the distance tolerance of any
     * existing node.
     *
     * @param p the point being inserted
     * @return the best matching node
     * @return null if no match was found
     */
    private fun findBestMatchNode(p: Coordinate): KdNode? {
        val visitor = BestMatchVisitor(p, tolerance)
        query(visitor.queryEnvelope(), visitor)
        return visitor.node
    }

    private class BestMatchVisitor(private val p: Coordinate, private val tolerance: Double) : KdNodeVisitor {
        private var matchNode: KdNode? = null
        private var matchDist = 0.0
        fun queryEnvelope(): Envelope {
            val queryEnv = Envelope(p)
            queryEnv.expandBy(tolerance)
            return queryEnv
        }

        val node: KdNode?
            get() = matchNode

        override fun visit(node: KdNode?) {
            val dist = p.distance(node!!.coordinate!!)
            val isInTolerance = dist <= tolerance
            if (!isInTolerance) return
            var update = false
            if (matchNode == null || dist < matchDist || matchNode != null && dist == matchDist && node.coordinate!!
                    .compareTo(matchNode!!.coordinate) < 1
            ) update = true
            if (update) {
                matchNode = node
                matchDist = dist
            }
        }
    }

    /**
     * Inserts a point known to be beyond the distance tolerance of any existing node.
     * The point is inserted at the bottom of the exact splitting path,
     * so that tree shape is deterministic.
     *
     * @param p the point to insert
     * @param data the data for the point
     * @return the created node
     */
    private fun insertExact(p: Coordinate, data: Any?): KdNode {
        var currentNode: KdNode? = root
        var leafNode: KdNode? = root
        var isXLevel = true
        var isLessThan = true
        /**
         * Traverse the tree, first cutting the plane left-right (by X ordinate)
         * then top-bottom (by Y ordinate)
         */
        while (currentNode != null) {
            val isInTolerance = p.distance(currentNode.coordinate!!) <= tolerance

            // check if point is already in tree (up to tolerance) and if so simply
            // return existing node
            if (isInTolerance) {
                currentNode.increment()
                return currentNode
            }
            val splitValue: Double = currentNode.splitValue(isXLevel)
            isLessThan = if (isXLevel) {
                p.x < splitValue
            } else {
                p.y < splitValue
            }
            leafNode = currentNode
            currentNode = if (isLessThan) {
                //System.out.print("L");
                currentNode.getLeft()
            } else {
                //System.out.print("R");
                currentNode.getRight()
            }
            isXLevel = !isXLevel
        }
        //System.out.println("<<");
        // no node found, add new leaf node to tree
        numberOfNodes += 1
        val node: KdNode = KdNode(p, data)
        if (isLessThan) {
            leafNode!!.setLeft(node)
        } else {
            leafNode!!.setRight(node)
        }
        return node
    }

    /**
     * Performs a range search of the points in the index and visits all nodes found.
     *
     * @param queryEnv the range rectangle to query
     * @param visitor a visitor to visit all nodes found by the search
     */
    fun query(queryEnv: Envelope, visitor: KdNodeVisitor) {
        //-- Deque is faster than Stack
        val queryStack: ArrayDeque<QueryStackFrame> = ArrayDeque()
        var currentNode: KdNode? = root
        var isXLevel = true

        // search is computed via in-order traversal
        while (true) {
            if (currentNode != null) {
                queryStack.push(QueryStackFrame(currentNode, isXLevel))
                val searchLeft: Boolean = currentNode.isRangeOverLeft(isXLevel, queryEnv)
                if (searchLeft) {
                    currentNode = currentNode.getLeft()
                    if (currentNode != null) {
                        isXLevel = !isXLevel
                    }
                } else {
                    currentNode = null
                }
            } else if (!queryStack.isEmpty()) {
                // currentNode is empty, so pop stack
                val frame: QueryStackFrame = queryStack.pop()!!
                currentNode = frame.getNode()
                isXLevel = frame.isXLevel

                //-- check if search matches current node
                if (queryEnv.contains(currentNode.coordinate!!)) {
                    visitor.visit(currentNode)
                }
                val searchRight: Boolean = currentNode.isRangeOverRight(isXLevel, queryEnv)
                if (searchRight) {
                    currentNode = currentNode.getRight()
                    if (currentNode != null) {
                        isXLevel = !isXLevel
                    }
                } else {
                    currentNode = null
                }
            } else {
                //-- stack is empty and no current node
                return
            }
        }
    }

    private class QueryStackFrame(private val node: KdNode, val isXLevel: Boolean = false) {
        fun getNode(): KdNode {
            return node
        }
    }

    /**
     * Performs a range search of the points in the index.
     *
     * @param queryEnv the range rectangle to query
     * @return a list of the KdNodes found
     */
    fun query(queryEnv: Envelope): MutableList<KdNode?> {
        val result: MutableList<KdNode?> = ArrayList()
        query(queryEnv, result)
        return result
    }

    /**
     * Performs a range search of the points in the index.
     *
     * @param queryEnv
     * the range rectangle to query
     * @param result
     * a list to accumulate the result nodes into
     */
    fun query(queryEnv: Envelope, result: MutableList<KdNode?>) {
        query(queryEnv, object : KdNodeVisitor {
            override fun visit(node: KdNode?) {
                result.add(node)
            }
        })
    }

    /**
     * Searches for a given point in the index and returns its node if found.
     *
     * @param queryPt the point to query
     * @return the point node, if it is found in the index, or null if not
     */
    fun query(queryPt: Coordinate): KdNode? {
        var currentNode: KdNode? = root
        var isXLevel = true
        while (currentNode != null) {
            if (currentNode.coordinate!!.equals2D(queryPt)) return currentNode
            val searchLeft: Boolean = currentNode.isPointOnLeft(isXLevel, queryPt)
            currentNode = if (searchLeft) {
                currentNode.getLeft()
            } else {
                currentNode.getRight()
            }
            isXLevel = !isXLevel
        }
        //-- point not found
        return null
    }

    /**
     * Computes the depth of the tree.
     *
     * @return the depth of the tree
     */
    fun depth(): Int {
        return depthNode(root)
    }

    private fun depthNode(currentNode: KdNode?): Int {
        if (currentNode == null) return 0
        val dL = depthNode(currentNode.getLeft())
        val dR = depthNode(currentNode.getRight())
        return 1 + if (dL > dR) dL else dR
    }

    /**
     * Computes the size (number of items) in the tree.
     *
     * @return the size of the tree
     */
    fun size(): Int {
        return sizeNode(root)
    }

    private fun sizeNode(currentNode: KdNode?): Int {
        if (currentNode == null) return 0
        val sizeL = sizeNode(currentNode.getLeft())
        val sizeR = sizeNode(currentNode.getRight())
        return 1 + sizeL + sizeR
    }

    companion object {
        /**
         * Converts a collection of [KdNode]s
         * to an array of [Coordinate]s,
         * specifying whether repeated nodes should be represented
         * by multiple coordinates.
         *
         * @param kdnodes a collection of nodes
         * @param includeRepeated true if repeated nodes should
         * be included multiple times
         * @return an array of the coordinates represented by the nodes
         */
        /**
         * Converts a collection of [KdNode]s to an array of [Coordinate]s.
         *
         * @param kdnodes
         * a collection of nodes
         * @return an array of the coordinates represented by the nodes
         */
        @JvmOverloads
        @JvmStatic
        fun toCoordinates(kdnodes: Collection<*>, includeRepeated: Boolean = false): Array<Coordinate> {
            val coord = CoordinateList()
            val it = kdnodes.iterator()
            while (it.hasNext()) {
                val node: KdNode =
                    it.next() as KdNode
                val count = if (includeRepeated) node.count else 1
                for (i in 0 until count) {
                    coord.add(node.coordinate, true)
                }
            }
            return coord.toCoordinateArray()
        }
    }
}