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
package org.locationtech.jts.geom

/**
 * Iterates over all [Geometry]s in a [Geometry],
 * (which may be either a collection or an atomic geometry).
 * The iteration sequence follows a pre-order, depth-first traversal of the
 * structure of the `GeometryCollection`
 * (which may be nested). The original `Geometry` object is
 * returned as well (as the first object), as are all sub-collections and atomic elements.
 * It is  simple to ignore the intermediate `GeometryCollection` objects if they are not
 * needed.
 *
 * @version 1.7
 */
class GeometryCollectionIterator(parent: Geometry) : MutableIterator<Any?> {
    /**
     * The `Geometry` being iterated over.
     */
    private val parent: Geometry

    /**
     * Indicates whether or not the first element
     * (the root `GeometryCollection`) has been returned.
     */
    private var atStart: Boolean

    /**
     * The number of `Geometry`s in the the `GeometryCollection`.
     */
    private val max: Int

    /**
     * The index of the `Geometry` that will be returned when `next`
     * is called.
     */
    private var index: Int

    /**
     * The iterator over a nested `Geometry`, or `null`
     * if this `GeometryCollectionIterator` is not currently iterating
     * over a nested `GeometryCollection`.
     */
    private var subcollectionIterator: GeometryCollectionIterator? = null

    /**
     * Constructs an iterator over the given `Geometry`.
     *
     * @param  parent  the geometry over which to iterate; also, the first
     * element returned by the iterator.
     */
    init {
        this.parent = parent
        atStart = true
        index = 0
        max = parent.numGeometries
    }

    /**
     * Tests whether any geometry elements remain to be returned.
     *
     * @return true if more geometry elements remain
     */
    override fun hasNext(): Boolean {
        if (atStart) {
            return true
        }
        if (subcollectionIterator != null) {
            if (subcollectionIterator!!.hasNext()) {
                return true
            }
            subcollectionIterator = null
        }
        return index < max
    }

    /**
     * Gets the next geometry in the iteration sequence.
     *
     * @return the next geometry in the iteration
     */
    override fun next(): Any? {
        // the parent GeometryCollection is the first object returned
        if (atStart) {
            atStart = false
            if (isAtomic(parent)) index++
            return parent
        }
        if (subcollectionIterator != null) {
            subcollectionIterator = if (subcollectionIterator!!.hasNext()) {
                return subcollectionIterator!!.next()
            } else {
                null
            }
        }
        if (index >= max) {
            throw NoSuchElementException()
        }
        val obj: Geometry = parent.getGeometryN(index++)
        if (obj is GeometryCollection) {
            subcollectionIterator = GeometryCollectionIterator(obj)
            // there will always be at least one element in the sub-collection
            return subcollectionIterator!!.next()
        }
        return obj
    }

    /**
     * Removal is not supported.
     *
     * @throws  UnsupportedOperationException  This method is not implemented.
     */
    override fun remove() {
        throw UnsupportedOperationException(this::class.simpleName)
    }

    companion object {
        private fun isAtomic(geom: Geometry): Boolean {
            return geom !is GeometryCollection
        }
    }
}