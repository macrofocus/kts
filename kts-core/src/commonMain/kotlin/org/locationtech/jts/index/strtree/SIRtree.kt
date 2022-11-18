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

import org.locationtech.jts.legacy.Math.max
import org.locationtech.jts.legacy.Math.min
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
    override val comparator: Comparator<Any?> = object : Comparator<Any?> {
        override fun compare(o1: Any?, o2: Any?): Int {
            return compareDoubles(
                ((o1 as Boundable).bounds as Interval?)!!.centre,
                ((o2 as Boundable).bounds as Interval?)!!.centre
            )
        }
    }
    override val intersectsOp: IntersectsOp = object : IntersectsOp {
        override fun intersects(aBounds: Any?, bBounds: Any?): Boolean {
            return (aBounds as Interval?)!!.intersects((bBounds as Interval?)!!)
        }
    }

    override fun createNode(level: Int): AbstractNode {
        return object : AbstractNode(level) {
            override fun computeBounds(): Any? {
                var bounds: Interval? = null
                val i: Iterator<*> = getChildBoundables().iterator()
                while (i.hasNext()) {
                    val childBoundable = i.next() as Boundable
                    if(bounds == null) {
                        bounds = Interval((childBoundable.bounds as Interval?)!!)
                    } else {
                        bounds.expandToInclude((childBoundable.bounds as Interval?)!!)
                    }
                }
                return bounds
            }
        }
    }

    /**
     * Inserts an item having the given bounds into the tree.
     */
    fun insert(x1: Double, x2: Double, item: Any?) {
        super.insert(Interval(min(x1, x2), max(x1, x2)), item!!)
    }

    /**
     * Returns items whose bounds intersect the given value.
     */
    fun query(x: Double): MutableList<Any?> {
        return query(x, x)
    }

    /**
     * Returns items whose bounds intersect the given bounds.
     * @param x1 possibly equal to x2
     */
    fun query(x1: Double, x2: Double): MutableList<Any?> {
        return super.query(Interval(min(x1, x2), max(x1, x2)))
    }
}