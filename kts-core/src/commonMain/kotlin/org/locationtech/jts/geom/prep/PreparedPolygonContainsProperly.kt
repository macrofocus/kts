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
package org.locationtech.jts.geom.prep

import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.Polygonal
import org.locationtech.jts.noding.SegmentString
import org.locationtech.jts.noding.SegmentStringUtil.extractSegmentStrings

/**
 * Computes the <tt>containsProperly</tt> spatial relationship predicate
 * for [PreparedPolygon]s relative to all other [Geometry] classes.
 * Uses short-circuit tests and indexing to improve performance.
 *
 *
 * A Geometry A <tt>containsProperly</tt> another Geometry B iff
 * all points of B are contained in the Interior of A.
 * Equivalently, B is contained in A AND B does not intersect
 * the Boundary of A.
 *
 *
 * The advantage to using this predicate is that it can be computed
 * efficiently, with no need to compute topology at individual points.
 * In a situation with many geometries intersecting the boundary
 * of the target geometry, this can make a performance difference.
 *
 * @author Martin Davis
 */
internal class PreparedPolygonContainsProperly
/**
 * Creates an instance of this operation.
 *
 * @param prepPoly the PreparedPolygon to evaluate
 */
    (prepPoly: org.locationtech.jts.geom.prep.PreparedPolygon) :
    org.locationtech.jts.geom.prep.PreparedPolygonPredicate(prepPoly) {
    /**
     * Tests whether this PreparedPolygon containsProperly a given geometry.
     *
     * @param geom the test geometry
     * @return true if the test geometry is contained properly
     */
    fun containsProperly(geom: Geometry?): Boolean {
        /**
         * Do point-in-poly tests first, since they are cheaper and may result
         * in a quick negative result.
         *
         * If a point of any test components does not lie in the target interior, result is false
         */
        val isAllInPrepGeomAreaInterior: Boolean = isAllTestComponentsInTargetInterior(geom)
        if (!isAllInPrepGeomAreaInterior) return false
        /**
         * If any segments intersect, result is false.
         */
        val lineSegStr: MutableList<SegmentString> = extractSegmentStrings(geom!!)
        val segsIntersect: Boolean = prepPoly.intersectionFinder!!.intersects(lineSegStr)
        if (segsIntersect) return false
        /**
         * Given that no segments intersect, if any vertex of the target
         * is contained in some test component.
         * the test is NOT properly contained.
         */
        if (geom is Polygonal) {
            // TODO: generalize this to handle GeometryCollections
            val isTargetGeomInTestArea: Boolean =
                isAnyTargetComponentInAreaTest(geom, prepPoly.representativePoints)
            if (isTargetGeomInTestArea) return false
        }
        return true
    }

    companion object {
        /**
         * Computes the containsProperly predicate between a [PreparedPolygon]
         * and a [Geometry].
         *
         * @param prep the prepared polygon
         * @param geom a test geometry
         * @return true if the polygon properly contains the geometry
         */
        fun containsProperly(prep: org.locationtech.jts.geom.prep.PreparedPolygon, geom: Geometry?): Boolean {
            val polyInt = PreparedPolygonContainsProperly(prep)
            return polyInt.containsProperly(geom)
        }
    }
}