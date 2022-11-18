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

import org.locationtech.jts.geom.Geometry

/**
 * Reads a sequence of [Geometry]s in WKT format
 * from a text file.
 * The geometries in the file may be separated by any amount
 * of whitespace and newlines.
 *
 * @author Martin Davis
 */
class WKTFileReader {
    private var file: java.io.File? = null
    private var reader: java.io.Reader? = null

    //  private Reader fileReader = new FileReader(file);
    private var wktReader: WKTReader
    private var count = 0
    private var limit = -1
    private var offset = 0
    private var isStrictParsing = true

    /**
     * Creates a new <tt>WKTFileReader</tt> given the <tt>File</tt> to read from
     * and a <tt>WKTReader</tt> to use to parse the geometries.
     *
     * @param file the <tt>File</tt> to read from
     * @param wktReader the geometry reader to use
     */
    constructor(file: java.io.File?, wktReader: WKTReader) {
        this.file = file
        this.wktReader = wktReader
    }

    /**
     * Creates a new <tt>WKTFileReader</tt>, given the name of the file to read from.
     *
     * @param filename the name of the file to read from
     * @param wktReader the geometry reader to use
     */
    constructor(filename: String?, wktReader: WKTReader) : this(
        java.io.File(filename),
        wktReader
    ) {
    }

    /**
     * Creates a new <tt>WKTFileReader</tt>, given a [Reader] to read from.
     *
     * @param reader the reader to read from
     * @param wktReader the geometry reader to use
     */
    constructor(reader: java.io.Reader?, wktReader: WKTReader) {
        this.reader = reader
        this.wktReader = wktReader
    }

    /**
     * Sets the maximum number of geometries to read.
     *
     * @param limit the maximum number of geometries to read
     */
    fun setLimit(limit: Int) {
        this.limit = limit
    }

    /**
     * Allows ignoring WKT parse errors
     * after at least one geometry has been read,
     * to return a partial result.
     *
     * @param isStrict whether to ignore parse errors
     */
    fun setStrictParsing(isStrict: Boolean) {
        isStrictParsing = isStrict
    }

    /**
     * Sets the number of geometries to skip before storing.
     *
     * @param offset the number of geometries to skip
     */
    fun setOffset(offset: Int) {
        this.offset = offset
    }

    /**
     * Reads a sequence of geometries.
     * If an offset is specified, geometries read up to the offset count are skipped.
     * If a limit is specified, no more than <tt>limit</tt> geometries are read.
     *
     * @return the list of geometries read
     * @throws IOException if an I/O exception was encountered
     * @throws ParseException if an error occurred reading a geometry
     */
    @Throws(java.io.IOException::class, ParseException::class)
    fun read(): MutableList<Geometry> {
        // do this here so that constructors don't throw exceptions
        if (file != null) reader = java.io.FileReader(file)
        count = 0
        return try {
            val bufferedReader: java.io.BufferedReader = java.io.BufferedReader(reader)
            try {
                read(bufferedReader)
            } finally {
                bufferedReader.close()
            }
        } finally {
            reader!!.close()
        }
    }

    @Throws(java.io.IOException::class, ParseException::class)
    private fun read(bufferedReader: java.io.BufferedReader): MutableList<Geometry> {
        val geoms: MutableList<Geometry> = ArrayList()
        try {
            read(bufferedReader, geoms)
        } catch (ex: ParseException) {
            // throw if strict or error is on first geometry
            if (isStrictParsing || geoms.size == 0) throw ex
        }
        return geoms
    }

    @Throws(java.io.IOException::class, ParseException::class)
    private fun read(bufferedReader: java.io.BufferedReader, geoms: MutableList<Geometry>) {
        while (!isAtEndOfFile(bufferedReader) && !isAtLimit(geoms)) {
            val g: Geometry = wktReader.read(bufferedReader)
            if (count >= offset) geoms.add(g)
            count++
        }
    }

    private fun isAtLimit(geoms: MutableList<*>): Boolean {
        if (limit < 0) return false
        return if (geoms.size < limit) false else true
    }

    /**
     * Tests if reader is at EOF, and skips any leading whitespace
     */
    @Throws(java.io.IOException::class)
    private fun isAtEndOfFile(bufferedReader: java.io.BufferedReader): Boolean {
        // skip whitespace
        var ch: Int
        do {
            bufferedReader.mark(1)
            ch = bufferedReader.read()
            // EOF reached
            if (ch < 0) return true
        } while (java.lang.Character.isWhitespace(ch))
        bufferedReader.reset()
        return false
    }
}