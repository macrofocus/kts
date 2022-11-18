/*
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
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

/**
 * Sorted map providing additional query operations and views.
 *
 * @param <K> key type.
 * @param <V> value type.
</V></K> */
interface NavigableMap<K, V> : SortedMap<K, V> {
    fun ceilingEntry(key: K): Map.Entry<K, V>?
    fun ceilingKey(key: K): K?
    fun descendingKeySet(): NavigableSet<K>?
    fun descendingMap(): NavigableMap<K, V>
    fun firstEntry(): Map.Entry<K, V>?
    fun floorEntry(key: K): Map.Entry<K, V>?
    fun floorKey(key: K): K?
    fun headMap(toKey: K, inclusive: Boolean): NavigableMap<K, V>
    fun higherEntry(key: K): Map.Entry<K, V>?
    fun higherKey(key: K): K?
    fun lastEntry(): Map.Entry<K, V>?
    fun lowerEntry(key: K): Map.Entry<K, V>?
    fun lowerKey(key: K): K?
    fun navigableKeySet(): NavigableSet<K>
    fun pollFirstEntry(): Map.Entry<K, V>?
    fun pollLastEntry(): Map.Entry<K, V>?
    fun subMap(fromKey: K, fromInclusive: Boolean, toKey: K, toInclusive: Boolean): NavigableMap<K, V>
    fun tailMap(fromKey: K, inclusive: Boolean): NavigableMap<K, V>
}
