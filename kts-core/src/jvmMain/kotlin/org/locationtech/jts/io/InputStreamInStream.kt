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
 * An adapter to allow an [InputStream] to be used as an [InStream]
 */
class InputStreamInStream(`is`: java.io.InputStream) : InStream {
    private val `is`: java.io.InputStream

    init {
        this.`is` = `is`
    }

    @Throws(java.io.IOException::class)
    override fun read(buf: ByteArray): Int {
        return `is`.read(buf)
    }
}