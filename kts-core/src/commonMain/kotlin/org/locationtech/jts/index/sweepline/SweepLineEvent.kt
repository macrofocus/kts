/*
 * Copyright (c) 2016 Vivid Solutions.
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.index.sweepline

/**
 * @version 1.7
 */
class SweepLineEvent(
    private val xValue: Double, // null if this is an INSERT event
    val insertEvent: SweepLineEvent?, sweepInt: SweepLineInterval
) : Comparable<Any?> {
    private var eventType: Int
    var deleteEventIndex = 0
    var sweepInt: SweepLineInterval

    init {
        eventType = INSERT
        if (insertEvent != null) eventType = DELETE
        this.sweepInt = sweepInt
    }

    val isInsert: Boolean
        get() = insertEvent == null
    val isDelete: Boolean
        get() = insertEvent != null
    val interval: SweepLineInterval
        get() = sweepInt

    /**
     * ProjectionEvents are ordered first by their x-value, and then by their eventType.
     * It is important that Insert events are sorted before Delete events, so that
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
        const val INSERT = 1
        const val DELETE = 2
    }
}