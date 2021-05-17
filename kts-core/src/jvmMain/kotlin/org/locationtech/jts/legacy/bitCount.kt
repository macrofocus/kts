/**
 * Copyright https://github.com/Miha-x64/Kotlin-MPP_Collection_utils
 */
package org.locationtech.jts.legacy

@PublishedApi
internal actual inline val Long.bitCount: Int
    get() = java.lang.Long.bitCount(this)