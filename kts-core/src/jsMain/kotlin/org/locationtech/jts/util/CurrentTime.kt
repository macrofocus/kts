package org.locationtech.jts.util

import kotlin.js.Date

/** Current UNIX time in millis */
actual val systemTimeMillis: Long
    get() = Date.now().toLong()