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
package org.locationtech.jts.operation.distance

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.index.strtree.ItemBoundable
import org.locationtech.jts.index.strtree.ItemDistance
import org.locationtech.jts.index.strtree.STRtree
import kotlin.jvm.JvmStatic

/**
 * Computes the distance between the facets (segments and vertices)
 * of two [Geometry]s
 * using a Branch-and-Bound algorithm.
 * The Branch-and-Bound algorithm operates over a
 * traversal of R-trees built
 * on the target and the query geometries.
 *
 *
 * This approach provides the following benefits:
 *
 *  * Performance is dramatically improved due to the use of the
 * R-tree index
 * and the pruning due to the Branch-and-Bound approach
 *  * The spatial index on the target geometry is cached
 * which allow reuse in an repeated query situation.
 *
 * Using this technique is usually much more performant
 * than using the brute-force [Geometry.distance]
 * when one or both input geometries are large,
 * or when evaluating many distance computations against
 * a single geometry.
 *
 *
 * This class is thread-safe.
 *
 * @author Martin Davis
 */
class IndexedFacetDistance(private val baseGeometry: Geometry) {
    private val cachedTree: STRtree = FacetSequenceTreeBuilder.build(baseGeometry)

    /**
     * Computes the distance from the base geometry to
     * the given geometry.
     *
     * @param g the geometry to compute the distance to
     *
     * @return the computed distance
     */
    fun distance(g: Geometry?): Double {
        val tree2: STRtree = FacetSequenceTreeBuilder.build(g!!)
        val obj = cachedTree.nearestNeighbour(
            tree2,
            FACET_SEQ_DIST
        )
        val fs1: FacetSequence = obj!![0] as FacetSequence
        val fs2: FacetSequence = obj[1] as FacetSequence
        return fs1.distance(fs2)
    }

    /**
     * Computes the nearest locations on the base geometry
     * and the given geometry.
     *
     * @param g the geometry to compute the nearest location to
     * @return the nearest locations
     */
    fun nearestLocations(g: Geometry?): Array<GeometryLocation> {
        val tree2: STRtree = FacetSequenceTreeBuilder.build(g!!)
        val obj = cachedTree.nearestNeighbour(
            tree2,
            FACET_SEQ_DIST
        )
        val fs1: FacetSequence = obj!![0] as FacetSequence
        val fs2: FacetSequence = obj[1] as FacetSequence
        return fs1.nearestLocations(fs2)
    }

    /**
     * Compute the nearest locations on the target geometry
     * and the given geometry.
     *
     * @param g the geometry to compute the nearest point to
     * @return the nearest points
     */
    fun nearestPoints(g: Geometry?): Array<Coordinate>? {
        val minDistanceLocation =
            nearestLocations(g)
        return toPoints(minDistanceLocation)
    }

    /**
     * Tests whether the base geometry lies within
     * a specified distance of the given geometry.
     *
     * @param g the geometry to test
     * @param maxDistance the maximum distance to test
     * @return true if the geometry lies with the specified distance
     */
    fun isWithinDistance(g: Geometry, maxDistance: Double): Boolean {
        // short-ciruit check
        val envDist = baseGeometry.envelopeInternal.distance(g.envelopeInternal)
        if (envDist > maxDistance) return false
        val tree2: STRtree = FacetSequenceTreeBuilder.build(g)
        return cachedTree.isWithinDistance(
            tree2,
            FACET_SEQ_DIST, maxDistance
        )
    }

    private class FacetSequenceDistance : ItemDistance {
        override fun distance(item1: ItemBoundable, item2: ItemBoundable): Double {
            val fs1: FacetSequence = item1.item as FacetSequence
            val fs2: FacetSequence = item2.item as FacetSequence
            return fs1.distance(fs2)
        }
    }

    companion object {
        private val FACET_SEQ_DIST = FacetSequenceDistance()

        /**
         * Computes the distance between facets of two geometries.
         *
         *
         * For geometries with many segments or points,
         * this can be faster than using a simple distance
         * algorithm.
         *
         * @param g1 a geometry
         * @param g2 a geometry
         * @return the distance between facets of the geometries
         */
        @JvmStatic
        fun distance(g1: Geometry, g2: Geometry?): Double {
            val dist = IndexedFacetDistance(g1)
            return dist.distance(g2)
        }

        /**
         * Tests whether the facets of two geometries lie within a given distance.
         *
         * @param g1 a geometry
         * @param g2 a geometry
         * @param distance the distance limit
         * @return true if two facets lie with the given distance
         */
        @JvmStatic
        fun isWithinDistance(g1: Geometry, g2: Geometry, distance: Double): Boolean {
            val dist = IndexedFacetDistance(g1)
            return dist.isWithinDistance(g2, distance)
        }

        /**
         * Computes the nearest points of the facets of two geometries.
         *
         * @param g1 a geometry
         * @param g2 a geometry
         * @return the nearest points on the facets of the geometries
         */
        @JvmStatic
        fun nearestPoints(g1: Geometry, g2: Geometry?): Array<Coordinate>? {
            val dist = IndexedFacetDistance(g1)
            return dist.nearestPoints(g2)
        }

        private fun toPoints(locations: Array<GeometryLocation>?): Array<Coordinate>? {
            return if (locations == null) null else arrayOf(
                locations[0].coordinate!!,
                locations[1].coordinate!!
            )
        }
    }
}