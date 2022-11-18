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

import org.locationtech.jts.legacy.System

/**
 * Allows an array of bytes to be used as an [InStream].
 * To optimize memory usage, instances can be reused
 * with different byte arrays.
 */
class ByteArrayInStream(buffer: ByteArray) : InStream {
    /*
	 * Implementation improvement suggested by Andrea Aime - Dec 15 2007
	 */
    private lateinit var buffer: ByteArray
    private var position = 0

    /**
     * Creates a new stream based on the given buffer.
     *
     * @param buffer the bytes to read
     */
    init {
        setBytes(buffer)
    }

    /**
     * Sets this stream to read from the given buffer
     *
     * @param buffer the bytes to read
     */
    fun setBytes(buffer: ByteArray) {
        this.buffer = buffer
        position = 0
    }

    /**
     * Reads up to <tt>buf.length</tt> bytes from the stream
     * into the given byte buffer.
     *
     * @param buf the buffer to place the read bytes into
     * @return the number of bytes read
     */
    override fun read(buf: ByteArray): Int {
        var numToRead = buf.size
        // don't try and copy past the end of the input
        if (position + numToRead > buffer.size) {
            numToRead = buffer.size - position
            System.arraycopy(buffer, position, buf, 0, numToRead)
            // zero out the unread bytes
            for (i in numToRead until buf.size) {
                buf[i] = 0
            }
        } else {
            System.arraycopy(buffer, position, buf, 0, numToRead)
        }
        position += numToRead
        return numToRead
    }
}