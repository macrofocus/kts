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
import org.locationtech.jts.geom.Envelope

/**
 * A node of a [KdTree], which represents one or more points in the same location.
 *
 * @author dskea
 */
class KdNode {
    /**
     * Returns the location of this node
     *
     * @return p location of this node
     */
    var coordinate: Coordinate? = null
        private set

    /**
     * Gets the user data object associated with this node.
     * @return user data
     */
    var data: Any?
        private set
    private var left: KdNode?
    private var right: KdNode?

    /**
     * Returns the number of inserted points that are coincident at this location.
     *
     * @return number of inserted points that this node represents
     */
    var count: Int
        private set

    /**
     * Creates a new KdNode.
     *
     * @param _x coordinate of point
     * @param _y coordinate of point
     * @param data a data objects to associate with this node
     */
    constructor(_x: Double, _y: Double, data: Any?) {
        coordinate = Coordinate(_x, _y)
        left = null
        right = null
        count = 1
        this.data = data
    }

    /**
     * Creates a new KdNode.
     *
     * @param p point location of new node
     * @param data a data objects to associate with this node
     */
    constructor(p: Coordinate?, data: Any?) {
        coordinate = Coordinate(p!!)
        left = null
        right = null
        count = 1
        this.data = data
    }

    /**
     * Returns the X coordinate of the node
     *
     * @return X coordinate of the node
     */
    val x: Double
        get() = coordinate!!.x

    /**
     * Returns the Y coordinate of the node
     *
     * @return Y coordinate of the node
     */
    val y: Double
        get() = coordinate!!.y

    /**
     * Gets the split value at a node, depending on
     * whether the node splits on X or Y.
     * The X (or Y) ordinates of all points in the left subtree
     * are less than the split value, and those
     * in the right subtree are greater than or equal to the split value.
     *
     * @param isSplitOnX whether the node splits on X or Y
     * @return the splitting value
     */
    fun splitValue(isSplitOnX: Boolean): Double {
        return if (isSplitOnX) {
            coordinate!!.x
        } else coordinate!!.y
    }

    /**
     * Returns the left node of the tree
     *
     * @return left node
     */
    fun getLeft(): KdNode? {
        return left
    }

    /**
     * Returns the right node of the tree
     *
     * @return right node
     */
    fun getRight(): KdNode? {
        return right
    }

    // Increments counts of points at this location
    fun increment() {
        count += 1
    }

    /**
     * Tests whether more than one point with this value have been inserted (up to the tolerance)
     *
     * @return true if more than one point have been inserted with this value
     */
    val isRepeated: Boolean
        get() = count > 1

    // Sets left node value
    fun setLeft(_left: KdNode?) {
        left = _left
    }

    // Sets right node value
    fun setRight(_right: KdNode?) {
        right = _right
    }

    /**
     * Tests whether the node's left subtree may contain values
     * in a given range envelope.
     *
     * @param isSplitOnX whether the node splits on  X or Y
     * @param env the range envelope
     * @return true if the left subtree is in range
     */
    fun isRangeOverLeft(isSplitOnX: Boolean, env: Envelope): Boolean {
        val envMin: Double = if (isSplitOnX) {
            env.minX
        } else {
            env.minY
        }
        val splitValue = splitValue(isSplitOnX)
        return envMin < splitValue
    }

    /**
     * Tests whether the node's right subtree may contain values
     * in a given range envelope.
     *
     * @param isSplitOnX whether the node splits on  X or Y
     * @param env the range envelope
     * @return true if the right subtree is in range
     */
    fun isRangeOverRight(isSplitOnX: Boolean, env: Envelope): Boolean {
        val envMax: Double = if (isSplitOnX) {
            env.maxX
        } else {
            env.maxY
        }
        val splitValue = splitValue(isSplitOnX)
        return splitValue <= envMax
    }

    /**
     * Tests whether a point is strictly to the left
     * of the splitting plane for this node.
     * If so it may be in the left subtree of this node,
     * Otherwise, the point may be in the right subtree.
     * The point is to the left if its X (or Y) ordinate
     * is less than the split value.
     *
     * @param isSplitOnX whether the node splits on  X or Y
     * @param pt the query point
     * @return true if the point is strictly to the left.
     *
     * @see .splitValue
     */
    fun isPointOnLeft(isSplitOnX: Boolean, pt: Coordinate): Boolean {
        val ptOrdinate: Double = if (isSplitOnX) {
            pt.x
        } else {
            pt.y
        }
        val splitValue = splitValue(isSplitOnX)
        return ptOrdinate < splitValue
    }
}