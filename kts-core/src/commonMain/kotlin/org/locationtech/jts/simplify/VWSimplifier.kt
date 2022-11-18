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
package org.locationtech.jts.simplify

import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.util.GeometryTransformer
import kotlin.jvm.JvmStatic

/**
 * Simplifies a [Geometry] using the Visvalingam-Whyatt area-based algorithm.
 * Ensures that any polygonal geometries returned are valid. Simple lines are not
 * guaranteed to remain simple after simplification. All geometry types are
 * handled. Empty and point geometries are returned unchanged. Empty geometry
 * components are deleted.
 *
 * The simplification tolerance is specified as a distance.
 * This is converted to an area tolerance by squaring it.
 *
 * Note that in general this algorithm does not preserve topology - e.g. polygons can be split,
 * collapse to lines or disappear holes can be created or disappear, and lines
 * can cross.
 *
 * <h3>Known Bugs</h3>
 *
 *  * Not yet optimized for performance
 *  * Does not simplify the endpoint of rings
 *
 * <h3>To Do</h3>
 *
 *  * Allow specifying desired number of vertices in the output
 *
 * @version 1.7
 */
class VWSimplifier
/**
 * Creates a simplifier for a given geometry.
 *
 * @param inputGeom the geometry to simplify
 */(private val inputGeom: Geometry) {
    private var distanceTolerance = 0.0
    private var isEnsureValidTopology = true

    /**
     * Sets the distance tolerance for the simplification. All vertices in the
     * simplified geometry will be within this distance of the original geometry.
     * The tolerance value must be non-negative.
     *
     * @param distanceTolerance
     * the approximation tolerance to use
     */
    fun setDistanceTolerance(distanceTolerance: Double) {
        if (distanceTolerance < 0.0) throw IllegalArgumentException("Tolerance must be non-negative")
        this.distanceTolerance = distanceTolerance
    }

    /**
     * Controls whether simplified polygons will be "fixed" to have valid
     * topology. The caller may choose to disable this because:
     *
     *  * valid topology is not required
     *  * fixing topology is a relative expensive operation
     *  * in some pathological cases the topology fixing operation may either
     * fail or run for too long
     *
     * The default is to fix polygon topology.
     *
     * @param isEnsureValidTopology
     */
    fun setEnsureValid(isEnsureValidTopology: Boolean) {
        this.isEnsureValidTopology = isEnsureValidTopology
    }// empty input produces an empty result

    /**
     * Gets the simplified geometry.
     *
     * @return the simplified geometry
     */
    val resultGeometry: Geometry
        get() =// empty input produces an empty result
            if (inputGeom.isEmpty) inputGeom.copy() else VWTransformer(isEnsureValidTopology, distanceTolerance)
                .transform(
                    inputGeom
                )

    internal class VWTransformer(private val isEnsureValidTopology: Boolean = true, distanceTolerance: Double) : GeometryTransformer() {
        private val distanceTolerance: Double

        init {
            this.distanceTolerance = distanceTolerance
        }

        override fun transformCoordinates(coords: CoordinateSequence?, parent: Geometry?): CoordinateSequence? {
            val inputPts = coords!!.toCoordinateArray()
            var newPts: Array<Coordinate>? = null
            newPts = if (inputPts!!.size == 0) {
                emptyArray()
            } else {
                VWLineSimplifier.simplify(inputPts, distanceTolerance)
            }
            return factory!!.coordinateSequenceFactory.create(newPts)
        }

        /**
         * Simplifies a polygon, fixing it if required.
         */
        override fun transformPolygon(geom: Polygon, parent: Geometry?): Geometry? {
            // empty geometries are simply removed
            if (geom.isEmpty) return null
            val rawGeom = super.transformPolygon(geom, parent)
            // don't try and correct if the parent is going to do this
            return if (parent is MultiPolygon) {
                rawGeom
            } else createValidArea(rawGeom!!)
        }

        /**
         * Simplifies a LinearRing. If the simplification results in a degenerate
         * ring, remove the component.
         *
         * @return null if the simplification results in a degenerate ring
         */
        override fun transformLinearRing(geom: LinearRing?, parent: Geometry?): Geometry? {
            val removeDegenerateRings = parent is Polygon
            val simpResult = super.transformLinearRing(geom, parent)
            return if (removeDegenerateRings && simpResult !is LinearRing) null else simpResult
        }

        /**
         * Simplifies a MultiPolygon, fixing it if required.
         */
        override fun transformMultiPolygon(geom: MultiPolygon, parent: Geometry?): Geometry {
            val rawGeom = super.transformMultiPolygon(geom, parent)
            return createValidArea(rawGeom)
        }

        /**
         * Creates a valid area geometry from one that possibly has bad topology
         * (i.e. self-intersections). Since buffer can handle invalid topology, but
         * always returns valid geometry, constructing a 0-width buffer "corrects"
         * the topology. Note this only works for area geometries, since buffer
         * always returns areas. This also may return empty geometries, if the input
         * has no actual area.
         *
         * @param rawAreaGeom
         * an area geometry possibly containing self-intersections
         * @return a valid area geometry
         */
        private fun createValidArea(rawAreaGeom: Geometry): Geometry {
            // if geometry is invalid then make it valid
            return if (isEnsureValidTopology && !rawAreaGeom.isValid) rawAreaGeom.buffer(0.0) else rawAreaGeom
        }
    }

    companion object {
        /**
         * Simplifies a geometry using a given tolerance.
         *
         * @param geom geometry to simplify
         * @param distanceTolerance the tolerance to use
         * @return a simplified version of the geometry
         */
        @JvmStatic
        fun simplify(geom: Geometry, distanceTolerance: Double): Geometry {
            val simp = VWSimplifier(geom)
            simp.setDistanceTolerance(distanceTolerance)
            return simp.resultGeometry
        }
    }
}