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

/**
 * A set known to be in ascending order. [[Sun
 * docs]](http://java.sun.com/j2se/1.5.0/docs/api/java/util/SortedSet.html)
 *
 * @param <E> element type.
</E> */
interface SortedSet<E> : MutableSet<E> {
    fun comparator(): Comparator<in E>?
    fun first(): E
    fun headSet(toElement: E): SortedSet<E>?
    fun last(): E
    fun subSet(fromElement: E, toElement: E): SortedSet<E>?
    fun tailSet(fromElement: E): SortedSet<E>?
}