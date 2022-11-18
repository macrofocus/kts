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

import org.locationtech.jts.io.ByteOrderValues.getDouble
import org.locationtech.jts.io.ByteOrderValues.getInt
import org.locationtech.jts.io.ByteOrderValues.getLong

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

    /**
     * Gets the data item that was last read from the stream.
     *
     * @return the data last read
     */
    var data: ByteArray? = null
        private set

    /**
     * Gets the number of bytes read from the stream.
     *
     * @return the number of bytes read
     */
    var count: Long = 0
        private set

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

    /**
     * Sets the ordering on the stream using the codes in [ByteOrderValues].
     *
     * @param byteOrder the byte order code
     */
    fun setOrder(byteOrder: Int) {
        this.byteOrder = byteOrder
    }

    /**
     * Reads a byte value.
     *
     * @return the value read
     * @throws IOException if an I/O error occurred
     * @throws ParseException if not enough data could be read
     */
    @Throws(IOException::class, ParseException::class)
    fun readByte(): Byte {
        read(buf1)
        return buf1[0]
    }

    /**
     * Reads an int value.
     *
     * @return the value read
     * @throws IOException if an I/O error occurred
     * @throws ParseException if not enough data could be read
     */
    @Throws(IOException::class, ParseException::class)
    fun readInt(): Int {
        read(buf4)
        return getInt(buf4, byteOrder)
    }

    /**
     * Reads a long value.
     *
     * @return the value read
     * @throws IOException if an I/O error occurred
     * @throws ParseException if not enough data could be read
     */
    @Throws(IOException::class, ParseException::class)
    fun readLong(): Long {
        read(buf8)
        return getLong(buf8, byteOrder)
    }

    /**
     * Reads a double value.
     *
     * @return the value read
     * @throws IOException if an I/O error occurred
     * @throws ParseException if not enough data could be read
     */
    @Throws(IOException::class, ParseException::class)
    fun readDouble(): Double {
        read(buf8)
        return getDouble(buf8, byteOrder)
    }

    @Throws(IOException::class, ParseException::class)
    private fun read(buf: ByteArray) {
        val num: Int = stream!!.read(buf)
        if (num < buf.size) throw ParseException("Attempt to read past end of input")
        data = buf
        count += num.toLong()
    }
}