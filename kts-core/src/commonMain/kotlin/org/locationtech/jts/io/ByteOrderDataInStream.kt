/*
 * Copyright (c) 2016 Vivid Solutions.
 * Copyright (c) 2020 Macrofocus GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.io

/**
 * Allows reading a stream of Java primitive datatypes from an underlying
 * [InStream],
 * with the representation being in either common byte ordering.
 */
class ByteOrderDataInStream {
    private var byteOrder = ByteOrderValues.BIG_ENDIAN
    private var stream: InStream?

    // buffers to hold primitive datatypes
    private val buf1 = ByteArray(1)
    private val buf4 = ByteArray(4)
    private val buf8 = ByteArray(8)

    constructor() {
        stream = null
    }

    constructor(stream: InStream?) {
        this.stream = stream
    }

    /**
     * Allows a single ByteOrderDataInStream to be reused
     * on multiple InStreams.
     *
     * @param stream
     */
    fun setInStream(stream: InStream?) {
        this.stream = stream
    }

    fun setOrder(byteOrder: Int) {
        this.byteOrder = byteOrder
    }

    /**
     * Reads a byte value
     *
     * @return the byte read
     */
    @Throws(IOException::class)
    fun readByte(): Byte {
        stream!!.read(buf1)
        return buf1[0]
    }

    @Throws(IOException::class)
    fun readInt(): Int {
        stream!!.read(buf4)
        return ByteOrderValues.getInt(buf4, byteOrder)
    }

    @Throws(IOException::class)
    fun readLong(): Long {
        stream!!.read(buf8)
        return ByteOrderValues.getLong(buf8, byteOrder)
    }

    @Throws(IOException::class)
    fun readDouble(): Double {
        stream!!.read(buf8)
        return ByteOrderValues.getDouble(buf8, byteOrder)
    }
}