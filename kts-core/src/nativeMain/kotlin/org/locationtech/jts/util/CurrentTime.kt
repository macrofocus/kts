package org.locationtech.jts.util

import kotlin.time.DurationUnit
import kotlin.time.TimeSource

/** Current UNIX time in millis */
actual val systemTimeMillis: Long
    get() = TimeSource.Monotonic.markNow().elapsedNow().toLong(DurationUnit.MILLISECONDS)