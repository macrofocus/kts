/*
 * Copyright (c) 2016 Vivid Solutions.
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
package org.locationtech.jts.simplify

import org.locationtech.jts.geom.CoordinateSequence
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryComponentFilter
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.util.GeometryTransformer
import kotlin.jvm.JvmStatic

/**
 * Simplifies a geometry and ensures that
 * the result is a valid geometry having the
 * same dimension and number of components as the input,
 * and with the components having the same topological
 * relationship.
 *
 *
 * If the input is a polygonal geometry
 * ( [Polygon] or [MultiPolygon] ):
 *
 *  * The result has the same number of shells and holes as the input,
 * with the same topological structure
 *  * The result rings touch at **no more** than the number of touching points in the input
 * (although they may touch at fewer points).
 * The key implication of this statement is that if the
 * input is topologically valid, so is the simplified output.
 *
 * For linear geometries, if the input does not contain
 * any intersecting line segments, this property
 * will be preserved in the output.
 *
 *
 * For all geometry types, the result will contain
 * enough vertices to ensure validity.  For polygons
 * and closed linear geometries, the result will have at
 * least 4 vertices; for open linestrings the result
 * will have at least 2 vertices.
 *
 *
 * All geometry types are handled.
 * Empty and point geometries are returned unchanged.
 * Empty geometry components are deleted.
 *
 *
 * The simplification uses a maximum-distance difference algorithm
 * similar to the Douglas-Peucker algorithm.
 *
 * <h3>KNOWN BUGS</h3>
 *
 *  * May create invalid topology if there are components which are
 * small relative to the tolerance value.
 * In particular, if a small hole is very near an edge, it is possible for the edge to be moved by
 * a relatively large tolerance value and end up with the hole outside the result shell
 * (or inside another hole).
 * Similarly, it is possible for a small polygon component to end up inside
 * a nearby larger polygon.
 * A workaround is to test for this situation in post-processing and remove
 * any invalid holes or polygons.
 *
 *
 * @author Martin Davis
 * @see DouglasPeuckerSimplifier
 */
class TopologyPreservingSimplifier(private val inputGeom: Geometry) {
    private val lineSimplifier: TaggedLinesSimplifier = TaggedLinesSimplifier()
    private var linestringMap: MutableMap<LineString, TaggedLineString>? = null

    /**
     * Sets the distance tolerance for the simplification.
     * All vertices in the simplified geometry will be within this
     * distance of the original geometry.
     * The tolerance value must be non-negative.  A tolerance value
     * of zero is effectively a no-op.
     *
     * @param distanceTolerance the approximation tolerance to use
     */
    fun setDistanceTolerance(distanceTolerance: Double) {
        if (distanceTolerance < 0.0) throw IllegalArgumentException("Tolerance must be non-negative")
        lineSimplifier.setDistanceTolerance(distanceTolerance)
    }

    // empty input produces an empty result
    val resultGeometry: Geometry
        get() {
            // empty input produces an empty result
            if (inputGeom.isEmpty) return inputGeom.copy()
            linestringMap = HashMap()
            inputGeom.apply(LineStringMapBuilderFilter(this))
            lineSimplifier.simplify(linestringMap!!.values)
            return LineStringTransformer(linestringMap).transform(
                inputGeom
            )
        }

    internal class LineStringTransformer(private val linestringMap: Map<*, *>?) : GeometryTransformer() {
        override fun transformCoordinates(coords: CoordinateSequence?, parent: Geometry?): CoordinateSequence? {
            if (coords!!.size() == 0) return null
            // for linear components (including rings), simplify the linestring
            if (parent is LineString) {
                val taggedLine: TaggedLineString? = linestringMap!![parent] as TaggedLineString?
                return createCoordinateSequence(taggedLine!!.resultCoordinates)
            }
            // for anything else (e.g. points) just copy the coordinates
            return super.transformCoordinates(coords, parent)
        }
    }

    /**
     * A filter to add linear geometries to the linestring map
     * with the appropriate minimum size constraint.
     * Closed [LineString]s (including [LinearRing]s
     * have a minimum output size constraint of 4,
     * to ensure the output is valid.
     * For all other linestrings, the minimum size is 2 points.
     *
     * @author Martin Davis
     */
    internal class LineStringMapBuilderFilter(var tps: TopologyPreservingSimplifier) : GeometryComponentFilter {
        /**
         * Filters linear geometries.
         *
         * geom a geometry of any type
         */
        override fun filter(geom: Geometry) {
            if (geom is LineString) {
                val line = geom as LineString
                // skip empty geometries
                if (line.isEmpty) return
                val minSize = if (line.isClosed) 4 else 2
                val taggedLine = TaggedLineString(line, minSize)
                tps.linestringMap!![line] = taggedLine
            }
        }
    }

    companion object {
        @JvmStatic
        fun simplify(geom: Geometry, distanceTolerance: Double): Geometry {
            val tss = TopologyPreservingSimplifier(geom)
            tss.setDistanceTolerance(distanceTolerance)
            return tss.resultGeometry
        }
    }
}