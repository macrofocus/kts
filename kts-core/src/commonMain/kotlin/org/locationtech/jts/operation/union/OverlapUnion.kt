/*
 * Copyright (c) 2019 Martin Davis.
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.operation.union

import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.util.GeometryCombiner
import kotlin.jvm.JvmOverloads

/**
 * Unions MultiPolygons efficiently by
 * using full topological union only for polygons which may overlap,
 * and combining with the remaining polygons.
 * Polygons which may overlap are those which intersect the common extent of the inputs.
 * Polygons wholly outside this extent must be disjoint to the computed union.
 * They can thus be simply combined with the union result,
 * which is much more performant.
 * (There is one caveat to this, which is discussed below).
 *
 * This situation is likely to occur during cascaded polygon union,
 * since the partitioning of polygons is done heuristically
 * and thus may group disjoint polygons which can lie far apart.
 * It may also occur in real world data which contains many disjoint polygons
 * (e.g. polygons representing parcels on different street blocks).
 *
 * <h2>Algorithm</h2>
 * The overlap region is determined as the common envelope of intersection.
 * The input polygons are partitioned into two sets:
 *
 *  * Overlapping: Polygons which intersect the overlap region, and thus potentially overlap each other
 *  * Disjoint: Polygons which are disjoint from (lie wholly outside) the overlap region
 *
 * The Overlapping set is fully unioned, and then combined with the Disjoint set.
 * Performing a simple combine works because
 * the disjoint polygons do not interact with each
 * other (since the inputs are valid MultiPolygons).
 * They also do not interact with the Overlapping polygons,
 * since they are outside their envelope.
 *
 * <h2>Discussion</h2>
 * In general the Overlapping set of polygons will
 * extend beyond the overlap envelope.  This means that the union result
 * will extend beyond the overlap region.
 * There is a small chance that the topological
 * union of the overlap region will shift the result linework enough
 * that the result geometry intersects one of the Disjoint geometries.
 * This situation is detected and if it occurs
 * is remedied by falling back to performing a full union of the original inputs.
 * Detection is done by a fairly efficient comparison of edge segments which
 * extend beyond the overlap region.  If any segments have changed
 * then there is a risk of introduced intersections, and full union is performed.
 *
 * This situation has not been observed in JTS using floating precision,
 * but it could happen due to snapping.  It has been observed
 * in other APIs (e.g. GEOS) due to more aggressive snapping.
 * It is more likely to happen if a Snap-Rounding overlay is used.
 *
 * **NOTE: Test has shown that using this heuristic impairs performance.
 * It has been removed from use.**
 *
 * @author mbdavis
 *
 */
@Deprecated("due to impairing performance")
class OverlapUnion @JvmOverloads constructor(
    private val g0: Geometry,
    private val g1: Geometry,
    unionFun: UnionStrategy = CascadedPolygonUnion.CLASSIC_UNION
) {
    private val geomFactory: GeometryFactory

    /**
     * Allows checking whether the optimized
     * or full union was performed.
     * Used for unit testing.
     *
     * @return true if the optimized union was performed
     */
    var isUnionOptimized = false
        private set
    private val unionFun: UnionStrategy

    /**
     * Creates a new instance for unioning the given geometries.
     *
     * @param g0 a geometry to union
     * @param g1 a geometry to union
     */
    init {
        geomFactory = g0.factory
        this.unionFun = unionFun
    }

    /**
     * Unions the input geometries,
     * using the more performant overlap union algorithm if possible.
     *
     * @return the union of the inputs
     */
    fun union(): Geometry? {
        val overlapEnv = overlapEnvelope(
            g0, g1
        )
        /**
         * If no overlap, can just combine the geometries
         */
        if (overlapEnv.isNull) {
            val g0Copy = g0.copy()
            val g1Copy = g1.copy()
            return GeometryCombiner.combine(g0Copy, g1Copy)
        }
        val disjointPolys: MutableList<Geometry> = ArrayList<Geometry>()
        val g0Overlap = extractByEnvelope(overlapEnv, g0, disjointPolys)
        val g1Overlap = extractByEnvelope(overlapEnv, g1, disjointPolys)

//    System.out.println("# geoms in common: " + intersectingPolys.size());
        val unionGeom = unionFull(g0Overlap, g1Overlap)
        var result: Geometry? = null
        isUnionOptimized = isBorderSegmentsSame(unionGeom, overlapEnv)
        result = if (!isUnionOptimized) {
            // overlap union changed border segments... need to do full union
            //System.out.println("OverlapUnion: Falling back to full union");
            unionFull(g0, g1)
        } else {
            //System.out.println("OverlapUnion: fast path");
            combine(unionGeom, disjointPolys)
        }
        return result
    }

    private fun combine(
        unionGeom: Geometry,
        disjointPolys: MutableList<Geometry>
    ): Geometry? {
        if (disjointPolys.size <= 0) return unionGeom
        disjointPolys.add(unionGeom)
        return GeometryCombiner.combine(disjointPolys)
    }

    private fun extractByEnvelope(
        env: Envelope, geom: Geometry,
        disjointGeoms: MutableList<Geometry>
    ): Geometry {
        val intersectingGeoms: MutableList<Geometry> = ArrayList<Geometry>()
        for (i in 0 until geom.numGeometries) {
            val elem = geom.getGeometryN(i)
            if (elem.envelopeInternal.intersects(env)) {
                intersectingGeoms.add(elem)
            } else {
                val copy = elem.copy()
                disjointGeoms.add(copy)
            }
        }
        return geomFactory.buildGeometry(intersectingGeoms)
    }

    private fun unionFull(
        geom0: Geometry,
        geom1: Geometry
    ): Geometry {
        // if both are empty collections, just return a copy of one of them
        return if (geom0.numGeometries == 0
            && geom1.numGeometries == 0
        ) geom0.copy() else unionFun.union(geom0, geom1)!!
    }

    private fun isBorderSegmentsSame(result: Geometry?, env: Envelope): Boolean {
        val segsBefore = extractBorderSegments(g0, g1, env)
        val segsAfter: MutableList<LineSegment> = ArrayList<LineSegment>()
        extractBorderSegments(result, env, segsAfter)

        //System.out.println("# seg before: " + segsBefore.size() + " - # seg after: " + segsAfter.size());
        return isEqual(segsBefore, segsAfter)
    }

    private fun isEqual(segs0: List<LineSegment>, segs1: List<LineSegment>): Boolean {
        if (segs0.size != segs1.size) return false
        val segIndex: Set<LineSegment> = HashSet<LineSegment>(segs0)
        for (seg in segs1) {
            if (!segIndex.contains(seg)) {
                //System.out.println("Found changed border seg: " + seg);
                return false
            }
        }
        return true
    }

    private fun extractBorderSegments(geom0: Geometry, geom1: Geometry?, env: Envelope): List<LineSegment> {
        val segs: MutableList<LineSegment> = ArrayList<LineSegment>()
        extractBorderSegments(geom0, env, segs)
        if (geom1 != null) extractBorderSegments(geom1, env, segs)
        return segs
    }

    companion object {
        /**
         * Union a pair of geometries,
         * using the more performant overlap union algorithm if possible.
         *
         * @param g0 a geometry to union
         * @param g1 a geometry to union
         * @param unionFun
         * @return the union of the inputs
         */
        fun union(g0: Geometry, g1: Geometry, unionFun: UnionStrategy): Geometry? {
            val union = OverlapUnion(g0, g1, unionFun)
            return union.union()
        }

        private fun overlapEnvelope(
            g0: Geometry,
            g1: Geometry
        ): Envelope {
            val g0Env = g0.envelopeInternal
            val g1Env = g1.envelopeInternal
            return g0Env.intersection(g1Env)
        }

        private fun intersects(env: Envelope, p0: Coordinate, p1: Coordinate): Boolean {
            return env.intersects(p0) || env.intersects(p1)
        }

        private fun containsProperly(env: Envelope, p0: Coordinate, p1: Coordinate): Boolean {
            return containsProperly(env, p0) && containsProperly(env, p1)
        }

        private fun containsProperly(env: Envelope, p: Coordinate): Boolean {
            return if (env.isNull) false else p.x > env.minX && p.x < env.maxX && p.y > env.minY && p.y < env.maxY
        }

        private fun extractBorderSegments(geom: Geometry?, env: Envelope, segs: MutableList<LineSegment>) {
            geom!!.apply(object : CoordinateSequenceFilter {
                override fun filter(seq: CoordinateSequence?, i: Int) {
                    if (i <= 0) return

                    // extract LineSegment
                    val p0 = seq!!.getCoordinate(i - 1)
                    val p1 = seq.getCoordinate(i)
                    val isBorder = intersects(env, p0, p1) && !containsProperly(env, p0, p1)
                    if (isBorder) {
                        val seg = LineSegment(p0, p1)
                        segs.add(seg)
                    }
                }

                override val isDone: Boolean
                    get() = false
                override val isGeometryChanged: Boolean
                    get() = false
            })
        }
    }
}