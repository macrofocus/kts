/*
 * Copyright (c) 2016 Vivid Solutions.
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
package org.locationtech.jts.simplify

import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Envelope.Companion.intersects
import org.locationtech.jts.geom.LineSegment
import org.locationtech.jts.index.ItemVisitor
import org.locationtech.jts.index.quadtree.Quadtree

/**
 * An spatial index on a set of [LineSegment]s.
 * Supports adding and removing items.
 *
 * @author Martin Davis
 */
class LineSegmentIndex {
    private val index = Quadtree()
    fun add(line: TaggedLineString) {
        val segs = line.segments
        for (i in segs.indices) {
            val seg = segs[i]
            add(seg)
        }
    }

    fun add(seg: LineSegment?) {
        index.insert(Envelope(seg!!.p0, seg.p1), seg)
    }

    fun remove(seg: LineSegment) {
        index.remove(Envelope(seg.p0, seg.p1), seg)
    }

    fun query(querySeg: LineSegment): MutableList<Any?> {
        val env = Envelope(querySeg.p0, querySeg.p1)
        val visitor =
            LineSegmentVisitor(querySeg)
        index.query(env, visitor)

//    List listQueryItems = index.query(env);
//    System.out.println("visitor size = " + itemsFound.size()
//                       + "  query size = " + listQueryItems.size());
//    List itemsFound = index.query(env);
        return visitor.getItems()
    }
}

/**
 * ItemVisitor subclass to reduce volume of query results.
 */
internal class LineSegmentVisitor( // MD - only seems to make about a 10% difference in overall time.
    private val querySeg: LineSegment
) : ItemVisitor {
    private val items: ArrayList<Any?> = ArrayList()
    override fun visitItem(item: Any?) {
        val seg = item as LineSegment
        if (intersects(seg.p0, seg.p1, querySeg.p0, querySeg.p1)) items.add(item)
    }

    fun getItems(): ArrayList<Any?> {
        return items
    }
}