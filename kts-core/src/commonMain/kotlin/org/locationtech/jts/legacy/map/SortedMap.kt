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
 * A map with ordering. [[Sun
 * docs]](http://java.sun.com/j2se/1.5.0/docs/api/java/util/SortedMap.html)
 *
 * @param <K> key type.
 * @param <V> value type.
</V></K> */
interface SortedMap<K, V> : MutableMap<K, V> {
    fun comparator(): Comparator<in K>
    fun firstKey(): K
    fun headMap(toKey: K): SortedMap<K, V>?
    fun lastKey(): K
    fun subMap(fromKey: K, toKey: K): SortedMap<K, V>?
    fun tailMap(fromKey: K): SortedMap<K, V>?
}
