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

/**
 * Provides versions of Geometry spatial functions which use
 * enhanced precision techniques to reduce the likelihood of robustness problems.
 *
 * @version 1.7
 */
object EnhancedPrecisionOp {
    /**
     * Computes the set-theoretic intersection of two [Geometry]s, using enhanced precision.
     * @param geom0 the first Geometry
     * @param geom1 the second Geometry
     * @return the Geometry representing the set-theoretic intersection of the input Geometries.
     */
    fun intersection(geom0: Geometry, geom1: Geometry): Geometry {
        val originalEx: RuntimeException
        originalEx = try {
            return geom0.intersection(geom1)
        } catch (ex: RuntimeException) {
            ex
        }
        /*
     * If we are here, the original op encountered a precision problem
     * (or some other problem).  Retry the operation with
     * enhanced precision to see if it succeeds
     */return try {
            val cbo: CommonBitsOp = CommonBitsOp(true)
            val resultEP: Geometry = cbo.intersection(geom0, geom1)
            // check that result is a valid geometry after the reshift to original precision
            if (!resultEP.isValid) throw originalEx
            resultEP
        } catch (ex2: RuntimeException) {
            throw originalEx
        }
    }

    /**
     * Computes the set-theoretic union of two [Geometry]s, using enhanced precision.
     * @param geom0 the first Geometry
     * @param geom1 the second Geometry
     * @return the Geometry representing the set-theoretic union of the input Geometries.
     */
    fun union(geom0: Geometry, geom1: Geometry): Geometry {
        val originalEx: RuntimeException
        originalEx = try {
            return geom0.union(geom1)
        } catch (ex: RuntimeException) {
            ex
        }
        /*
     * If we are here, the original op encountered a precision problem
     * (or some other problem).  Retry the operation with
     * enhanced precision to see if it succeeds
     */return try {
            val cbo: CommonBitsOp = CommonBitsOp(true)
            val resultEP: Geometry = cbo.union(geom0, geom1)
            // check that result is a valid geometry after the reshift to original precision
            if (!resultEP.isValid) throw originalEx
            resultEP
        } catch (ex2: RuntimeException) {
            throw originalEx
        }
    }

    /**
     * Computes the set-theoretic difference of two [Geometry]s, using enhanced precision.
     * @param geom0 the first Geometry
     * @param geom1 the second Geometry
     * @return the Geometry representing the set-theoretic difference of the input Geometries.
     */
    fun difference(geom0: Geometry, geom1: Geometry): Geometry {
        val originalEx: RuntimeException
        originalEx = try {
            return geom0.difference(geom1)
        } catch (ex: RuntimeException) {
            ex
        }
        /*
     * If we are here, the original op encountered a precision problem
     * (or some other problem).  Retry the operation with
     * enhanced precision to see if it succeeds
     */return try {
            val cbo: CommonBitsOp = CommonBitsOp(true)
            val resultEP: Geometry = cbo.difference(geom0, geom1)
            // check that result is a valid geometry after the reshift to original precision
            if (!resultEP.isValid) throw originalEx
            resultEP
        } catch (ex2: RuntimeException) {
            throw originalEx
        }
    }

    /**
     * Computes the set-theoretic symmetric difference of two [Geometry]s, using enhanced precision.
     * @param geom0 the first Geometry
     * @param geom1 the second Geometry
     * @return the Geometry representing the set-theoretic symmetric difference of the input Geometries.
     */
    fun symDifference(geom0: Geometry, geom1: Geometry): Geometry {
        val originalEx: RuntimeException
        originalEx = try {
            return geom0.symDifference(geom1)
        } catch (ex: RuntimeException) {
            ex
        }
        /*
     * If we are here, the original op encountered a precision problem
     * (or some other problem).  Retry the operation with
     * enhanced precision to see if it succeeds
     */return try {
            val cbo: CommonBitsOp = CommonBitsOp(true)
            val resultEP: Geometry = cbo.symDifference(geom0, geom1)
            // check that result is a valid geometry after the reshift to original precision
            if (!resultEP.isValid) throw originalEx
            resultEP
        } catch (ex2: RuntimeException) {
            throw originalEx
        }
    }

    /**
     * Computes the buffer of a [Geometry], using enhanced precision.
     * This method should no longer be necessary, since the buffer algorithm
     * now is highly robust.
     *
     * @param geom the first Geometry
     * @param distance the buffer distance
     * @return the Geometry representing the buffer of the input Geometry.
     */
    fun buffer(geom: Geometry, distance: Double): Geometry {
        val originalEx: RuntimeException
        originalEx = try {
            return geom.buffer(distance)
        } catch (ex: RuntimeException) {
            ex
        }
        /*
     * If we are here, the original op encountered a precision problem
     * (or some other problem).  Retry the operation with
     * enhanced precision to see if it succeeds
     */return try {
            val cbo: CommonBitsOp = CommonBitsOp(true)
            val resultEP: Geometry = cbo.buffer(geom, distance)
            // check that result is a valid geometry after the reshift to original precision
            if (!resultEP.isValid) throw originalEx
            resultEP
        } catch (ex2: RuntimeException) {
            throw originalEx
        }
    }
}