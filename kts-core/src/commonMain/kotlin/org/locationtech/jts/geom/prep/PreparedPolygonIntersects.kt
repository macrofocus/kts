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
import org.locationtech.jts.noding.SegmentString
import org.locationtech.jts.noding.SegmentStringUtil.extractSegmentStrings

/**
 * Computes the <tt>intersects</tt> spatial relationship predicate for
 * [PreparedPolygon]s relative to all other [Geometry] classes. Uses
 * short-circuit tests and indexing to improve performance.
 *
 * @author Martin Davis
 */
internal class PreparedPolygonIntersects
/**
 * Creates an instance of this operation.
 *
 * @param prepPoly
 * the PreparedPolygon to evaluate
 */
    (prepPoly: PreparedPolygon) :
    PreparedPolygonPredicate(prepPoly) {
    /**
     * Tests whether this PreparedPolygon intersects a given geometry.
     *
     * @param geom
     * the test geometry
     * @return true if the test geometry intersects
     */
    fun intersects(geom: Geometry): Boolean {
        /**
         * Do point-in-poly tests first, since they are cheaper and may result in a
         * quick positive result.
         *
         * If a point of any test components lie in target, result is true
         */
        val isInPrepGeomArea: Boolean = isAnyTestComponentInTarget(geom)
        if (isInPrepGeomArea) return true
        /**
         * If input contains only points, then at
         * this point it is known that none of them are contained in the target
         */
        if (geom.dimension == 0) return false
        /**
         * If any segments intersect, result is true
         */
        val lineSegStr: MutableList<SegmentString> = extractSegmentStrings(geom)
        // only request intersection finder if there are segments 
        // (i.e. NOT for point inputs)
        if (lineSegStr.size > 0) {
            val segsIntersect: Boolean = prepPoly.intersectionFinder!!.intersects(
                lineSegStr
            )
            if (segsIntersect) return true
        }
        /**
         * If the test has dimension = 2 as well, it is necessary to test for proper
         * inclusion of the target. Since no segments intersect, it is sufficient to
         * test representative points.
         */
        if (geom.dimension == 2) {
            // TODO: generalize this to handle GeometryCollections
            val isPrepGeomInArea: Boolean = isAnyTargetComponentInAreaTest(
                geom,
                prepPoly.representativePoints
            )
            if (isPrepGeomInArea) return true
        }
        return false
    }

    companion object {
        /**
         * Computes the intersects predicate between a [PreparedPolygon] and a
         * [Geometry].
         *
         * @param prep
         * the prepared polygon
         * @param geom
         * a test geometry
         * @return true if the polygon intersects the geometry
         */
        fun intersects(prep: org.locationtech.jts.geom.prep.PreparedPolygon, geom: Geometry): Boolean {
            val polyInt = PreparedPolygonIntersects(prep)
            return polyInt.intersects(geom)
        }
    }
}