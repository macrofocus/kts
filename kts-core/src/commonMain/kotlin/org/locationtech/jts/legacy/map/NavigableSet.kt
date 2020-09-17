/*
 * Copyright (c) 2020 Macrofocus GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Some of the code has been derived from GWT 2.9.0, which came with the following license:
 *
 * Copyright 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.locationtech.jts.legacy.map

import org.locationtech.jts.legacy.SortedSet

/**
 * A `SortedSet` with more flexible queries.
 *
 * @param <E> element type.
</E> */
interface NavigableSet<E> : SortedSet<E> {
    fun ceiling(e: E): E?
    fun descendingIterator(): Iterator<E>?
    fun descendingSet(): NavigableSet<E>?
    fun floor(e: E): E?
    fun headSet(toElement: E, inclusive: Boolean): NavigableSet<E>?
    fun higher(e: E): E?
    fun lower(e: E): E?
    fun pollFirst(): E?
    fun pollLast(): E?
    fun subSet(fromElement: E, fromInclusive: Boolean, toElement: E, toInclusive: Boolean): NavigableSet<E>?
    fun tailSet(fromElement: E, inclusive: Boolean): NavigableSet<E>?
}