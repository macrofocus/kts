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
package org.locationtech.jts.io

/**
 * Thrown by a `WKTReader` when a parsing problem occurs.
 *
 * @version 1.7
 */
expect class ParseException : Exception {
    /**
     * Creates a `ParseException` with the given detail message.
     *
     * @param  message  a description of this `ParseException`
     */
    constructor(message: String)

    /**
     * Creates a `ParseException` with `e`s detail message.
     *
     * @param  e  an exception that occurred while a `WKTReader` was
     * parsing a Well-known Text string
     */
    constructor(e: Exception)

    /**
     * Creates a `ParseException` with `e`s detail message.
     *
     * @param  message  a description of this `ParseException`
     * @param  e  a throwable that occurred while a com.vividsolutions.jts.io reader was
     * parsing a string representation
     */
    constructor(message: String?, e: Throwable?)
}