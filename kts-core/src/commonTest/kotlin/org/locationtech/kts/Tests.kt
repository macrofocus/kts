package org.locationtech.kts

import kotlin.math.abs
import kotlin.test.asserter

internal fun assertEquals(expected: Double, actual: Double?, epsilon: Double, message: String? = null) {
    asserter.assertNotNull(null, actual)
    asserter.assertTrue({ messagePrefix(message) + "Expected <$expected>, actual <$actual>, should differ no more than <$epsilon>." },
        abs(expected - actual!!) <= epsilon)
}

internal fun messagePrefix(message: String?) = if (message == null) "" else "$message. "
