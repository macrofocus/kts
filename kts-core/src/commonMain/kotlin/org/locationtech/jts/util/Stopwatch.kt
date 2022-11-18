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
package org.locationtech.jts.util

import org.locationtech.jts.legacy.System

/**
 * Implements a timer function which can compute
 * elapsed time as well as split times.
 *
 * @version 1.7
 */
class Stopwatch {
    private var startTimestamp: Long = 0
    private var totalTime: Long = 0
    private var isRunning = false

    init {
        start()
    }

    fun start() {
        if (isRunning) return
        startTimestamp = System.currentTimeMillis()
        isRunning = true
    }

    fun stop(): Long {
        if (isRunning) {
            updateTotalTime()
            isRunning = false
        }
        return totalTime
    }

    fun reset() {
        totalTime = 0
        startTimestamp = System.currentTimeMillis()
    }

    fun split(): Long {
        if (isRunning) updateTotalTime()
        return totalTime
    }

    private fun updateTotalTime() {
        val endTimestamp: Long = System.currentTimeMillis()
        val elapsedTime = endTimestamp - startTimestamp
        startTimestamp = endTimestamp
        totalTime += elapsedTime
    }

    val time: Long
        get() {
            updateTotalTime()
            return totalTime
        }
    val timeString: String
        get() {
            val totalTime = time
            return getTimeString(totalTime)
        }

    companion object {
        fun getTimeString(timeMillis: Long): String {
            return if (timeMillis < 10000) "$timeMillis ms" else (timeMillis.toDouble() / 1000.0).toString() + " s"
        }
    }
}