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
package org.locationtech.jts.index.strtree

import org.locationtech.jts.legacy.Serializable

/**
 * A node of an [AbstractSTRtree]. A node is one of:
 *
 *  * empty
 *  * an *interior node* containing child [AbstractNode]s
 *  * a *leaf node* containing data items ([ItemBoundable]s).
 *
 * A node stores the bounds of its children, and its level within the index tree.
 *
 * @version 1.7
 */
abstract class AbstractNode : Boundable, Serializable {
    private var childBoundables: ArrayList<Boundable> = ArrayList()

    override var bounds: Any? = null
        /**
         * Gets the bounds of this node
         *
         * @return the object representing bounds in this index
         */
        get() {
            if (field == null) {
                field = computeBounds()
            }
            return field
        }

    /**
     * Returns 0 if this node is a leaf, 1 if a parent of a leaf, and so on; the
     * root node will have the highest level
     *
     * @return the node level
     */
    var level = 0
        private set

    /**
     * Default constructor required for serialization.
     */
    constructor()

    /**
     * Constructs an AbstractNode at the given level in the tree
     * @param level 0 if this node is a leaf, 1 if a parent of a leaf, and so on; the
     * root node will have the highest level
     */
    constructor(level: Int) {
        this.level = level
    }

    /**
     * Returns either child [AbstractNode]s, or if this is a leaf node, real data (wrapped
     * in [ItemBoundable]s).
     *
     * @return a list of the children
     */
    fun getChildBoundables(): MutableList<Boundable> {
        return childBoundables
    }

    /**
     * Returns a representation of space that encloses this Boundable,
     * preferably not much bigger than this Boundable's boundary yet fast to
     * test for intersection with the bounds of other Boundables. The class of
     * object returned depends on the subclass of AbstractSTRtree.
     *
     * @return an Envelope (for STRtrees), an Interval (for SIRtrees), or other
     * object (for other subclasses of AbstractSTRtree)
     * @see AbstractSTRtree.IntersectsOp
     */
    protected abstract fun computeBounds(): Any?

    /**
     * Gets the count of the [Boundable]s at this node.
     *
     * @return the count of boundables at this node
     */
    fun size(): Int {
        return childBoundables.size
    }

    /**
     * Tests whether there are any [Boundable]s at this node.
     *
     * @return true if there are boundables at this node
     */
    val isEmpty: Boolean
        get() = childBoundables.isEmpty()

    /**
     * Adds either an AbstractNode, or if this is a leaf node, a data object
     * (wrapped in an ItemBoundable)
     *
     * @param childBoundable the child to add
     */
    fun addChildBoundable(childBoundable: Boundable) {
        // ToDo: this check always fail because accessing bounds automatically computes in Kotlin
//        isTrue(bounds == null)
        childBoundables.add(childBoundable)
    }

    fun setChildBoundables(childBoundables: ArrayList<Boundable>) {
        this.childBoundables = childBoundables
    }

    companion object {
        /**
         *
         */
        private const val serialVersionUID = 6493722185909573708L
    }
}