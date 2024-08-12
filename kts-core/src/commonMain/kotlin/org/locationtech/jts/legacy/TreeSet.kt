/*
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.legacy

import kotlin.math.abs

expect class TreeSet<E : Comparable<E>>(
    comparator: Comparator<E>  = naturalOrder(),
    collection: Collection<E> = emptyList()
) : AbstractMutableSet<E> {
    fun higher(holeCoord: E): E

    fun lower(holeCoord: E): E

    override val size: Int

    override fun iterator(): MutableIterator<E>

    override fun add(element: E): Boolean
}
