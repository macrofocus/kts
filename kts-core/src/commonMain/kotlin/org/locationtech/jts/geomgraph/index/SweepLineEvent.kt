/*
 * Copyright (c) 2016 Vivid Solutions.
 * Copyright (c) 2020 Macrofocus GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.geomgraph.index

/**
 * @version 1.7
 */
class SweepLineEvent : Comparable<Any?> {
    private var label // used for red-blue intersection detection
            : Any? = null
    private var xValue: Double
    private var eventType: Int
    var insertEvent: SweepLineEvent? = null // null if this is an INSERT event
        private set
    var deleteEventIndex = 0
    var `object`: Any? = null
        private set

    /**
     * Creates an INSERT event.
     *
     * @param label the edge set label for this object
     * @param x the event location
     * @param obj the object being inserted
     */
    constructor(label: Any?, x: Double, obj: Any?) {
        eventType = INSERT
        this.label = label
        xValue = x
        `object` = obj
    }

    /**
     * Creates a DELETE event.
     *
     * @param x the event location
     * @param insertEvent the corresponding INSERT event
     */
    constructor(x: Double, insertEvent: SweepLineEvent?) {
        eventType = DELETE
        xValue = x
        this.insertEvent = insertEvent
    }

    val isInsert: Boolean
        get() = eventType == INSERT
    val isDelete: Boolean
        get() = eventType == DELETE

    fun isSameLabel(ev: SweepLineEvent): Boolean {
        // no label set indicates single group
        return if (label == null) false else label === ev.label
    }

    /**
     * Events are ordered first by their x-value, and then by their eventType.
     * Insert events are sorted before Delete events, so that
     * items whose Insert and Delete events occur at the same x-value will be
     * correctly handled.
     */
    override fun compareTo(o: Any?): Int {
        val pe = o as SweepLineEvent?
        if (xValue < pe!!.xValue) return -1
        if (xValue > pe.xValue) return 1
        if (eventType < pe.eventType) return -1
        return if (eventType > pe.eventType) 1 else 0
    }

    companion object {
        private const val INSERT = 1
        private const val DELETE = 2
    }
}