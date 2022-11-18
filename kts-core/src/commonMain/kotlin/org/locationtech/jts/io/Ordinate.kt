/*
 * Copyright (c) 2018 Felix Obermaier
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.io

import org.locationtech.jts.legacy.EnumSet
import org.locationtech.jts.legacy.clone
import org.locationtech.jts.legacy.enumSetOf
import kotlin.jvm.JvmStatic

/**
 * An enumeration of possible Well-Known-Text or Well-Known-Binary ordinates.
 *
 * Intended to be used as an `EnumSet<Ordinate>`, optimized create methods have been provided for [.createXY], [.createXYM], [.createXYZ] and [.createXYZM].
 */
enum class Ordinate {
    /**
     * X-ordinate
     */
    X,

    /**
     * Y-ordinate
     */
    Y,

    /**
     * Z-ordinate
     */
    Z,

    /**
     * Measure-ordinate
     */
    M;

    companion object {
        private val XY = enumSetOf(X, Y)
        private val XYZ = enumSetOf(X, Y, Z)
        private val XYM = enumSetOf(X, Y, M)
        private val XYZM = enumSetOf(X, Y, Z, M)

        /**
         * EnumSet of X and Y ordinates, a copy is returned as EnumSets are not immutable.
         * @return EnumSet of X and Y ordinates.
         */
        @JvmStatic
        fun createXY(): EnumSet<Ordinate> {
            return XY.clone()
        }

        /**
         * EnumSet of XYZ ordinates, a copy is returned as EnumSets are not immutable.
         * @return EnumSet of X and Y ordinates.
         */
        @JvmStatic
        fun createXYZ(): EnumSet<Ordinate> {
            return XYZ.clone()
        }

        /**
         * EnumSet of XYM ordinates, a copy is returned as EnumSets are not immutable.
         * @return EnumSet of X and Y ordinates.
         */
        @JvmStatic
        fun createXYM(): EnumSet<Ordinate> {
            return XYM.clone()
        }

        /**
         * EnumSet of XYZM ordinates, a copy is returned as EnumSets are not immutable.
         * @return EnumSet of X and Y ordinates.
         */
        @JvmStatic
        fun createXYZM(): EnumSet<Ordinate> {
            return XYZM.clone()
        }
    }
}