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
package org.locationtech.jts.precision

import org.locationtech.jts.geom.Geometry
import kotlin.jvm.JvmOverloads

/**
 * Provides versions of Geometry spatial functions which use
 * common bit removal to reduce the likelihood of robustness problems.
 *
 * In the current implementation no rounding is performed on the
 * reshifted result geometry, which means that it is possible
 * that the returned Geometry is invalid.
 * Client classes should check the validity of the returned result themselves.
 *
 * @version 1.7
 */
class CommonBitsOp @JvmOverloads constructor(val returnToOriginalPrecision: Boolean = true) {
    private var cbr: CommonBitsRemover? = null
    /**
     * Creates a new instance of class, specifying whether
     * the result [Geometry]s should be reshifted.
     *
     * @param returnToOriginalPrecision
     */

    /**
     * Computes the set-theoretic intersection of two [Geometry]s, using enhanced precision.
     * @param geom0 the first Geometry
     * @param geom1 the second Geometry
     * @return the Geometry representing the set-theoretic intersection of the input Geometries.
     */
    fun intersection(geom0: Geometry, geom1: Geometry): Geometry {
        val geom = removeCommonBits(geom0, geom1)
        return computeResultPrecision(geom[0]!!.intersection(geom[1]))
    }

    /**
     * Computes the set-theoretic union of two [Geometry]s, using enhanced precision.
     * @param geom0 the first Geometry
     * @param geom1 the second Geometry
     * @return the Geometry representing the set-theoretic union of the input Geometries.
     */
    fun union(geom0: Geometry, geom1: Geometry): Geometry {
        val geom = removeCommonBits(geom0, geom1)
        return computeResultPrecision(geom[0]!!.union(geom[1]))
    }

    /**
     * Computes the set-theoretic difference of two [Geometry]s, using enhanced precision.
     * @param geom0 the first Geometry
     * @param geom1 the second Geometry, to be subtracted from the first
     * @return the Geometry representing the set-theoretic difference of the input Geometries.
     */
    fun difference(geom0: Geometry, geom1: Geometry): Geometry {
        val geom = removeCommonBits(geom0, geom1)
        return computeResultPrecision(geom[0]!!.difference(geom[1]))
    }

    /**
     * Computes the set-theoretic symmetric difference of two geometries,
     * using enhanced precision.
     * @param geom0 the first Geometry
     * @param geom1 the second Geometry
     * @return the Geometry representing the set-theoretic symmetric difference of the input Geometries.
     */
    fun symDifference(geom0: Geometry, geom1: Geometry): Geometry {
        val geom = removeCommonBits(geom0, geom1)
        return computeResultPrecision(geom[0]!!.symDifference(geom[1]))
    }

    /**
     * Computes the buffer a geometry,
     * using enhanced precision.
     * @param geom0 the Geometry to buffer
     * @param distance the buffer distance
     * @return the Geometry representing the buffer of the input Geometry.
     */
    fun buffer(geom0: Geometry, distance: Double): Geometry {
        val geom = removeCommonBits(geom0)
        return computeResultPrecision(geom.buffer(distance))
    }

    /**
     * If required, returning the result to the original precision if required.
     *
     * In this current implementation, no rounding is performed on the
     * reshifted result geometry, which means that it is possible
     * that the returned Geometry is invalid.
     *
     * @param result the result Geometry to modify
     * @return the result Geometry with the required precision
     */
    private fun computeResultPrecision(result: Geometry): Geometry {
        if (returnToOriginalPrecision) cbr!!.addCommonBits(result)
        return result
    }

    /**
     * Computes a copy of the input [Geometry] with the calculated common bits
     * removed from each coordinate.
     * @param geom0 the Geometry to remove common bits from
     * @return a copy of the input Geometry with common bits removed
     */
    private fun removeCommonBits(geom0: Geometry): Geometry {
        cbr = CommonBitsRemover()
        cbr!!.add(geom0)
        return cbr!!.removeCommonBits(geom0.copy())
    }

    /**
     * Computes a copy of each input [Geometry]s with the calculated common bits
     * removed from each coordinate.
     * @param geom0 a Geometry to remove common bits from
     * @param geom1 a Geometry to remove common bits from
     * @return an array containing copies
     * of the input Geometry's with common bits removed
     */
    private fun removeCommonBits(geom0: Geometry, geom1: Geometry): Array<Geometry?> {
        cbr = CommonBitsRemover()
        cbr!!.add(geom0)
        cbr!!.add(geom1)
        val geom = arrayOfNulls<Geometry>(2)
        geom[0] = cbr!!.removeCommonBits(geom0.copy())
        geom[1] = cbr!!.removeCommonBits(geom1.copy())
        return geom
    }
}