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

/**
 * @version 1.7
 */
/**
 * Provides routines to simplify and localize debugging output.
 * Debugging is controlled via a Java system property value.
 * If the system property with the name given in
 * DEBUG_PROPERTY_NAME (currently "jts.debug") has the value
 * "on" or "true" debugging is enabled.
 * Otherwise, debugging is disabled.
 * The system property can be set by specifying the following JVM option:
 * <pre>
 * -Djts.debug=on
</pre> *
 *
 * @version 1.7
 */
expect class Debug {
    fun println()

    companion object {
        var isDebugging: Boolean

        fun println()

        fun println(obj: Any)

        fun print(str: String)
    }
}