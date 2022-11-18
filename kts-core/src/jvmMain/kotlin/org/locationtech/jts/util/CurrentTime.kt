package org.locationtech.jts.util

/** Current UNIX time in millis */
actual val systemTimeMillis: Long
    get() = System.currentTimeMillis()