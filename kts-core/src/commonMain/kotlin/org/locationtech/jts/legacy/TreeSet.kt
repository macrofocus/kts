/*
 * Copyright (c) 2020 Macrofocus GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.legacy

import kotlin.math.abs

class TreeSet<E : Comparable<E>>(
    val comparator: Comparator<E> = naturalOrder(),
    collection: Collection<E> = emptyList()
) : AbstractMutableSet<E>() {
    private val store = ArrayList<E>().apply {
        addAll(collection.toSet())
        sortWith(comparator)
    }

    override val size: Int
        get() = store.size

    override fun add(element: E): Boolean {
        val index = store.binarySearch(element, comparator)
        return if (index >= 0)
            false
        else {
            store.add(abs(index) - 1, element)
            true
        }
    }

    override fun iterator() = store.iterator()
}
