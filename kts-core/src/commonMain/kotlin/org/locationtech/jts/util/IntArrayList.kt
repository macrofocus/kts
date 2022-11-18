/*
 * Copyright (c) 2019 Martin Davis.
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.util

import org.locationtech.jts.legacy.Math.max
import org.locationtech.jts.legacy.System
import kotlin.jvm.JvmOverloads

/**
 * An extendable array of primitive `int` values.
 *
 * @author Martin Davis
 */
class IntArrayList @JvmOverloads constructor(initialCapacity: Int = 10) {
    private var data: IntArray
    private var size = 0
    /**
     * Constructs an empty list with the specified initial capacity
     *
     * @param initialCapacity the initial capacity of the list
     */
    /**
     * Constructs an empty list.
     */
    init {
        data = IntArray(initialCapacity)
    }

    /**
     * Returns the number of values in this list.
     *
     * @return the number of values in the list
     */
    fun size(): Int {
        return size
    }

    /**
     * Increases the capacity of this list instance, if necessary,
     * to ensure that it can hold at least the number of elements
     * specified by the capacity argument.
     *
     * @param capacity the desired capacity
     */
    fun ensureCapacity(capacity: Int) {
        if (capacity <= data.size) return
        val newLength: Int = max(capacity, data.size * 2)
        //System.out.println("IntArrayList: copying " + size + " ints to new array of length " + capacity);
        data = data.copyOf(newLength)
    }

    /**
     * Adds a value to the end of this list.
     *
     * @param value the value to add
     */
    fun add(value: Int) {
        ensureCapacity(size + 1)
        data[size] = value
        ++size
    }

    /**
     * Adds all values in an array to the end of this list.
     *
     * @param values an array of values
     */
    fun addAll(values: IntArray?) {
        if (values == null) return
        if (values.isEmpty()) return
        ensureCapacity(size + values.size)
        System.arraycopy(values, 0, data, size, values.size)
        size += values.size
    }

    /**
     * Returns a int array containing a copy of
     * the values in this list.
     *
     * @return an array containing the values in this list
     */
    fun toArray(): IntArray {
        val array = IntArray(size)
        System.arraycopy(data, 0, array, 0, size)
        return array
    }
}