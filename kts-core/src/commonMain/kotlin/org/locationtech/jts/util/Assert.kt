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

import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * A utility for making programming assertions.
 *
 * @version 1.7
 */
object Assert {
    /**
     * Throws an `AssertionFailedException` if the given assertion is
     * not true.
     *
     * @param  assertion                  a condition that is supposed to be true
     * @throws  AssertionFailedException  if the condition is false
     */
    @JvmStatic
    fun isTrue(assertion: Boolean) {
        isTrue(assertion, null)
    }

    /**
     * Throws an `AssertionFailedException` with the given message if
     * the given assertion is not true.
     *
     * @param  assertion                  a condition that is supposed to be true
     * @param  message                    a description of the assertion
     * @throws  AssertionFailedException  if the condition is false
     */
    fun isTrue(assertion: Boolean, message: String?) {
        if (!assertion) {
            if (message == null) {
                throw AssertionFailedException()
            } else {
                throw AssertionFailedException(message)
            }
        }
    }
    /**
     * Throws an `AssertionFailedException` with the given message if
     * the given objects are not equal, according to the `equals`
     * method.
     *
     * @param  expectedValue              the correct value
     * @param  actualValue                the value being checked
     * @param  message                    a description of the assertion
     * @throws  AssertionFailedException  if the two objects are not equal
     */
    /**
     * Throws an `AssertionFailedException` if the given objects are
     * not equal, according to the `equals` method.
     *
     * @param  expectedValue              the correct value
     * @param  actualValue                the value being checked
     * @throws  AssertionFailedException  if the two objects are not equal
     */
    @JvmOverloads
    fun equals(expectedValue: Any, actualValue: Any, message: String? = null) {
        if (actualValue != expectedValue) {
            throw AssertionFailedException(
                "Expected " + expectedValue + " but encountered "
                        + actualValue + if (message != null) ": $message" else ""
            )
        }
    }
    /**
     * Always throws an `AssertionFailedException` with the given
     * message.
     *
     * @param  message                    a description of the assertion
     * @throws  AssertionFailedException  thrown always
     */
    /**
     * Always throws an `AssertionFailedException`.
     *
     * @throws  AssertionFailedException  thrown always
     */
    @JvmOverloads
    @JvmStatic
    fun shouldNeverReachHere(message: String? = null) {
        throw AssertionFailedException(
            "Should never reach here"
                    + if (message != null) ": $message" else ""
        )
    }
}