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
package org.locationtech.jts.index.strtree

import org.locationtech.jts.legacy.Math
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads

/**
 * One-dimensional version of an STR-packed R-tree. SIR stands for
 * "Sort-Interval-Recursive". STR-packed R-trees are described in:
 * P. Rigaux, Michel Scholl and Agnes Voisard. Spatial Databases With
 * Application To GIS. Morgan Kaufmann, San Francisco, 2002.
 *
 * This class is thread-safe.  Building the tree is synchronized,
 * and querying is stateless.
 *
 * @see STRtree
 *
 * @version 1.7
 */
class SIRtree
/**
 * Constructs an SIRtree with the given maximum number of child nodes that
 * a node may have
 */
/**
 * Constructs an SIRtree with the default node capacity.
 */
@JvmOverloads constructor(nodeCapacity: Int = 10) : AbstractSTRtree(nodeCapacity) {
    @JvmField
    protected val comparator: Comparator<Any?> = Comparator { o1, o2 ->
        compareDoubles(
            ((o1 as Boundable).bounds as Interval?)!!.centre,
            ((o2 as Boundable).bounds as Interval?)!!.centre
        )
    }

    override fun getComparator(): Comparator<Any?>? {
        return comparator
    }

    override val intersectsOp = object : IntersectsOp {
        override fun intersects(aBounds: Any?, bBounds: Any?): Boolean {
            return (aBounds as Interval).intersects(
                (bBounds as Interval)
            )
        }
    }

    override fun createNode(level: Int): AbstractNode {
        return object : AbstractNode(level) {
            override fun computeBounds(): Any? {
                var bounds: Interval? = null
                val i = childBoundables.iterator()
                while (i.hasNext()) {
                    val childBoundable = i.next() as Boundable
                    if (bounds == null) {
                        bounds = Interval(childBoundable.bounds as Interval)
                    } else {
                        bounds.expandToInclude(childBoundable.bounds as Interval)
                    }
                }

                return bounds
            }
        }
    }

    /**
     * Inserts an item having the given bounds into the tree.
     */
    fun insert(x1: Double, x2: Double, item: Any) {
        super.insert(Interval(Math.min(x1, x2), Math.max(x1, x2)), item)
    }

    /**
     * Returns items whose bounds intersect the given value.
     */
    fun query(x: Double): List<*>? {
        return query(x, x)
    }

    /**
     * Returns items whose bounds intersect the given bounds.
     * @param x1 possibly equal to x2
     */
    fun query(x1: Double, x2: Double): List<*>? {
        return super.query(Interval(Math.min(x1, x2), Math.max(x1, x2)))
    }
}