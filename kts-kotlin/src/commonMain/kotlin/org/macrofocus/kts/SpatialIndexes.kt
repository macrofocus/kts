/*
 * Copyright (c) 2026 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.macrofocus.kts

import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.index.ItemVisitor
import org.locationtech.jts.index.SpatialIndex
import org.locationtech.jts.index.hprtree.HPRtree
import org.locationtech.jts.index.quadtree.Quadtree
import org.locationtech.jts.index.strtree.STRtree

/**
 * A typed view of one of JTS' spatial indexes, which hold `Any?`: [strTree], [hprTree] or [quadtree]. For a static
 * set of items, [PackedRTree] is faster.
 */
class TypedSpatialIndex<T>(val index: SpatialIndex) {
    fun insert(envelope: Envelope, item: T) = index.insert(envelope, item)

    fun remove(envelope: Envelope, item: T): Boolean = index.remove(envelope, item)

    /** The items whose envelope intersects [envelope]. */
    @Suppress("UNCHECKED_CAST")
    fun query(envelope: Envelope): List<T> = index.query(envelope) as List<T>

    /** Calls [action] with the items whose envelope intersects [envelope], without collecting them in a list. */
    fun forEach(envelope: Envelope, action: (T) -> Unit) = index.query(envelope, object : ItemVisitor {
        @Suppress("UNCHECKED_CAST")
        override fun visitItem(item: Any?) = action(item as T)
    })
}

fun <G : Geometry> TypedSpatialIndex<G>.insert(geometry: G) = insert(geometry.envelopeInternal, geometry)

fun <T> strTree(nodeCapacity: Int = 10): TypedSpatialIndex<T> = TypedSpatialIndex(STRtree(nodeCapacity))

fun <T> hprTree(nodeCapacity: Int = 16): TypedSpatialIndex<T> = TypedSpatialIndex(HPRtree(nodeCapacity))

fun <T> quadtree(): TypedSpatialIndex<T> = TypedSpatialIndex(Quadtree())
