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
package org.locationtech.jts.geom.impl

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.CoordinateSequence
import org.locationtech.jts.geom.CoordinateSequenceFactory
import org.locationtech.jts.legacy.Serializable
import kotlin.jvm.JvmStatic

/**
 * Creates [CoordinateSequence]s represented as an array of [Coordinate]s.
 *
 * @version 1.7
 */
class CoordinateArraySequenceFactory private constructor() : CoordinateSequenceFactory, Serializable {
    private fun readResolve(): Any {
        // http://www.javaworld.com/javaworld/javatips/jw-javatip122.html
        return instance()
    }

    /**
     * Returns a [CoordinateArraySequence] based on the given array (the array is
     * not copied).
     *
     * @param coordinates
     * the coordinates, which may not be null nor contain null
     * elements
     */
    override fun create(coordinates: Array<Coordinate>?): CoordinateSequence {
        return CoordinateArraySequence(coordinates)
    }

    /**
     * @see CoordinateSequenceFactory.create
     */
    override fun create(coordSeq: CoordinateSequence): CoordinateSequence {
        return CoordinateArraySequence(coordSeq)
    }

    /**
     * The created sequence dimension is clamped to be &lt;= 3.
     *
     * @see CoordinateSequenceFactory.create
     */
    override fun create(size: Int, dimension: Int): CoordinateSequence {
        var d = dimension
        if (d > 3) d = 3
        //throw new IllegalArgumentException("dimension must be <= 3");

        // handle bogus dimension
        if (d < 2) d = 2
        return CoordinateArraySequence(size, d)
    }

    override fun create(size: Int, dimension: Int, measures: Int): CoordinateSequence {
        var m = measures
        var d = dimension - m
        if (m > 1) {
            m = 1 // clip measures
            //throw new IllegalArgumentException("measures must be <= 1");
        }
        if (d > 3) {
            d = 3 // clip spatial dimension
            //throw new IllegalArgumentException("spatial dimension must be <= 3");
        }
        if (d < 2) d = 2 // handle bogus spatial dimension
        return CoordinateArraySequence(size, d + m, m)
    }

    companion object {
        private const val serialVersionUID = -4099577099607551657L
        private val instanceObject = CoordinateArraySequenceFactory()

        /**
         * Returns the singleton instance of [CoordinateArraySequenceFactory]
         */
        @JvmStatic
        fun instance(): CoordinateArraySequenceFactory {
            return instanceObject
        }
    }
}