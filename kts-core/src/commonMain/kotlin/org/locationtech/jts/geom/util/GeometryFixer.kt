/*
 * Copyright (c) 2021 Martin Davis.
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
package org.locationtech.jts.geom.util

import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.prep.PreparedGeometry
import org.locationtech.jts.geom.prep.PreparedGeometryFactory
import org.locationtech.jts.operation.buffer.BufferOp
import org.locationtech.jts.operation.overlayng.OverlayNG
import org.locationtech.jts.operation.overlayng.OverlayNGRobust
import kotlin.jvm.JvmOverloads

/**
 * Fixes a geometry to be a valid geometry, while preserving as much as
 * possible of the shape and location of the input.
 * Validity is determined according to [Geometry.isValid].
 *
 * Input geometries are always processed, so even valid inputs may
 * have some minor alterations.  The output is always a new geometry object.
 *
 * <h2>Semantic Rules</h2>
 *
 *  1. Vertices with non-finite X or Y ordinates are removed
 * (as per [Coordinate.isValid].
 *  1. Repeated points are reduced to a single point
 *  1. Empty atomic geometries are valid and are returned unchanged
 *  1. Empty elements are removed from collections
 *  1. `Point`: keep valid coordinate, or EMPTY
 *  1. `LineString`: coordinates are fixed
 *  1. `LinearRing`: coordinates are fixed.  Keep valid ring, or else convert into `LineString`
 *  1. `Polygon`: transform into a valid polygon,
 * preserving as much of the extent and vertices as possible.
 *
 *  * Rings are fixed to ensure they are valid
 *  * Holes intersecting the shell are subtracted from the shell
 *  * Holes outside the shell are converted into polygons
 *
 *  1. `MultiPolygon`: each polygon is fixed,
 * then result made non-overlapping (via union)
 *  1. `GeometryCollection`: each element is fixed
 *  1. Collapsed lines and polygons are handled as follows,
 * depending on the `keepCollapsed` setting:
 *
 *  * `false`: (default) collapses are converted to empty geometries
 * (and removed if they are elements of collections)
 *  * `true`: collapses are converted to a valid geometry of lower dimension
 *
 *
 * @author Martin Davis
 *
 * @see Geometry.isValid
 */
class GeometryFixer(private val geom: Geometry) {
    private val factory: GeometryFactory
    private var isKeepCollapsed = false
    private var isKeepMulti = DEFAULT_KEEP_MULTI

    /**
     * Creates a new instance to fix a given geometry.
     *
     * @param geom the geometry to be fixed
     */
    init {
        factory = geom.factory
    }

    /**
     * Sets whether collapsed geometries are converted to empty,
     * (which will be removed from collections),
     * or to a valid geometry of lower dimension.
     * The default is to convert collapses to empty geometries.
     *
     * @param isKeepCollapsed whether collapses should be converted to a lower dimension geometry
     */
    fun setKeepCollapsed(isKeepCollapsed: Boolean) {
        this.isKeepCollapsed = isKeepCollapsed
    }

    /**
     * Sets whether fixed `MULTI` geometries that consist of
     * only one item should still be returned as `MULTI` geometries.
     *
     * The default is to keep `MULTI` geometries.
     *
     * @param isKeepMulti flag whether to keep `MULTI` geometries.
     */
    fun setKeepMulti(isKeepMulti: Boolean) {
        this.isKeepMulti = isKeepMulti
    }/*
     *  Truly empty geometries are simply copied.
     *  Geometry collections with elements are evaluated on a per-element basis.
     */
    //  LinearRing must come before LineString
    /**
     * Gets the fixed geometry.
     *
     * @return the fixed geometry
     */
    val result: Geometry?
        get() {
            /*
              *  Truly empty geometries are simply copied.
              *  Geometry collections with elements are evaluated on a per-element basis.
              */
            if (geom.numGeometries == 0) {
                return geom.copy()
            }
            if (geom is Point) return fixPoint(geom)
            //  LinearRing must come before LineString
            if (geom is LinearRing) return fixLinearRing(geom)
            if (geom is LineString) return fixLineString(geom)
            if (geom is Polygon) return fixPolygon(geom)
            if (geom is MultiPoint) return fixMultiPoint(geom)
            if (geom is MultiLineString) return fixMultiLineString(geom)
            if (geom is MultiPolygon) return fixMultiPolygon(geom)
            if (geom is GeometryCollection) return fixCollection(geom)
            throw UnsupportedOperationException(geom::class.simpleName)
        }

    private fun fixPoint(geom: Point): Point {
        val pt = fixPointElement(geom) ?: return factory.createPoint()
        return pt
    }

    private fun fixPointElement(geom: Point): Point? {
        return if (geom.isEmpty || !isValidPoint(geom)) {
            null
        } else geom.copy() as Point
    }

    private fun fixMultiPoint(geom: MultiPoint): Geometry? {
        val pts: MutableList<Point> = ArrayList<Point>()
        for (i in 0 until geom.numGeometries) {
            val pt = geom.getGeometryN(i) as Point
            if (pt.isEmpty) continue
            val fixPt = fixPointElement(pt)
            if (fixPt != null) {
                pts.add(fixPt)
            }
        }
        return if (!isKeepMulti && pts.size == 1) pts[0] else factory.createMultiPoint(
            GeometryFactory.toPointArray(
                pts
            )
        )
    }

    private fun fixLinearRing(geom: LinearRing): Geometry {
        return fixLinearRingElement(geom) ?: return factory.createLinearRing()
    }

    private fun fixLinearRingElement(geom: LinearRing): Geometry? {
        if (geom.isEmpty) return null
        val pts: Array<Coordinate> = geom.coordinates
        val ptsFix = fixCoordinates(pts)
        if (isKeepCollapsed) {
            if (ptsFix.size == 1) {
                return factory.createPoint(ptsFix[0])
            }
            if (ptsFix.size > 1 && ptsFix.size <= 3) {
                return factory!!.createLineString(ptsFix)
            }
        }
        //--- too short to be a valid ring
        if (ptsFix.size <= 3) {
            return null
        }
        val ring: LinearRing = factory!!.createLinearRing(ptsFix)
        //--- convert invalid ring to LineString
        return if (!ring.isValid) {
            factory.createLineString(ptsFix)
        } else ring
    }

    private fun fixLineString(geom: LineString?): Geometry {
        return fixLineStringElement(geom) ?: return factory.createLineString()
    }

    private fun fixLineStringElement(geom: LineString?): Geometry? {
        if (geom!!.isEmpty) return null
        val pts = geom.coordinates
        val ptsFix = fixCoordinates(pts)
        if (isKeepCollapsed && ptsFix.size == 1) {
            return factory.createPoint(ptsFix[0])
        }
        return if (ptsFix.size <= 1) {
            null
        } else factory.createLineString(ptsFix)
    }

    private fun fixMultiLineString(geom: MultiLineString): Geometry? {
        val fixed: MutableList<Geometry> = ArrayList<Geometry>()
        var isMixed = false
        for (i in 0 until geom.numGeometries) {
            val line = geom.getGeometryN(i) as LineString
            if (line.isEmpty) continue
            val fix = fixLineStringElement(line) ?: continue
            if (fix !is LineString) {
                isMixed = true
            }
            fixed.add(fix)
        }
        if (fixed.size == 1) {
            if (!isKeepMulti || fixed[0] !is LineString) return fixed[0]
        }
        return if (isMixed) {
            factory.createGeometryCollection(GeometryFactory.toGeometryArray(fixed))
        } else factory.createMultiLineString(GeometryFactory.toLineStringArray(fixed))
    }

    private fun fixPolygon(geom: Polygon): Geometry {
        return fixPolygonElement(geom) ?: return factory.createPolygon()
    }

    private fun fixPolygonElement(geom: Polygon): Geometry? {
        val shell = geom.exteriorRing
        val fixShell = fixRing(shell)
        if (fixShell.isEmpty) {
            return if (isKeepCollapsed) {
                fixLineString(shell)
            } else null
            //--- if not allowing collapses then return empty polygon
        }
        //--- if no holes then done
        if (geom.getNumInteriorRing() == 0) {
            return fixShell
        }

        //--- fix holes, classify, and construct shell-true holes
        val holesFixed = fixHoles(geom)
        val holes: MutableList<Geometry> =
            ArrayList<Geometry>()
        val shells: MutableList<Geometry> =
            ArrayList<Geometry>()
        classifyHoles(fixShell, holesFixed, holes, shells)
        val polyWithHoles = difference(fixShell, holes)
        if (shells.size == 0) {
            return polyWithHoles
        }

        //--- if some holes converted to shells, union all shells
        shells.add(polyWithHoles)
        return union(shells)
    }

    private fun fixHoles(geom: Polygon): List<Geometry> {
        val holes: MutableList<Geometry> = ArrayList<Geometry>()
        for (i in 0 until geom.getNumInteriorRing()) {
            val holeRep = fixRing(geom.getInteriorRingN(i))
            if (holeRep != null) {
                holes.add(holeRep)
            }
        }
        return holes
    }

    private fun classifyHoles(
        shell: Geometry,
        holesFixed: List<Geometry>,
        holes: MutableList<Geometry>,
        shells: MutableList<Geometry>
    ) {
        val shellPrep: PreparedGeometry = PreparedGeometryFactory.prepare(shell)
        for (hole in holesFixed) {
            if (shellPrep.intersects(hole)) {
                holes.add(hole)
            } else {
                shells.add(hole)
            }
        }
    }

    /**
     * Subtracts a list of polygonal geometries from a polygonal geometry.
     *
     * @param shell polygonal geometry for shell
     * @param holes polygonal geometries to subtract
     * @return the result geometry
     */
    private fun difference(shell: Geometry, holes: List<Geometry>?): Geometry {
        if (holes == null || holes.size == 0) return shell
        val holesUnion = union(holes)
        return OverlayNGRobust.overlay(shell, holesUnion, OverlayNG.DIFFERENCE)!!
    }

    /**
     * Unions a list of polygonal geometries.
     * Optimizes case of zero or one input geometries.
     * Requires that the inputs are net new objects.
     *
     * @param polys the polygonal geometries to union
     * @return the union of the inputs
     */
    private fun union(polys: List<Geometry>): Geometry? {
        if (polys.size == 0) return factory.createPolygon()
        return if (polys.size == 1) {
            polys[0]
        } else OverlayNGRobust.union(polys)
        // TODO: replace with holes.union() once OverlayNG is the default
    }

    private fun fixRing(ring: LinearRing?): Geometry {
        //-- always execute fix, since it may remove repeated/invalid coords etc
        // TODO: would it be faster to check ring validity first?
        val poly: Geometry = factory.createPolygon(ring)
        return BufferOp.bufferByZero(poly, true)!!
    }

    private fun fixMultiPolygon(geom: MultiPolygon): Geometry {
        val polys: MutableList<Geometry> = ArrayList<Geometry>()
        for (i in 0 until geom.numGeometries) {
            val poly = geom.getGeometryN(i) as Polygon
            val polyFix = fixPolygonElement(poly)
            if (polyFix != null && !polyFix.isEmpty) {
                polys.add(polyFix)
            }
        }
        if (polys.size == 0) {
            return factory.createMultiPolygon()
        }
        // TODO: replace with polys.union() once OverlayNG is the default
        var result = union(polys)
        if (isKeepMulti && result is Polygon) result = factory.createMultiPolygon(
            arrayOf(
                result
            )
        )
        return result!!
    }

    private fun fixCollection(geom: GeometryCollection): Geometry {
        val geomRep = arrayOfNulls<Geometry>(geom.numGeometries)
        for (i in 0 until geom.numGeometries) {
            geomRep[i] = fix(geom.getGeometryN(i), isKeepCollapsed, isKeepMulti)
        }
        return factory.createGeometryCollection(geomRep.requireNoNulls())
    }

    companion object {
        private const val DEFAULT_KEEP_MULTI = true
        /**
         * Fixes a geometry to be valid, allowing to set a flag controlling how
         * single item results from fixed `MULTI` geometries should be
         * returned.
         *
         * @param geom the geometry to be fixed
         * @param isKeepMulti a flag indicating if `MULTI` geometries should not be converted to single instance types
         * if they consist of only one item.
         * @return the valid fixed geometry
         */
        /**
         * Fixes a geometry to be valid.
         *
         * @param geom the geometry to be fixed
         * @return the valid fixed geometry
         */
        @JvmOverloads
        fun fix(geom: Geometry, isKeepMulti: Boolean = DEFAULT_KEEP_MULTI): Geometry? {
            val fix = GeometryFixer(geom)
            fix.setKeepMulti(isKeepMulti)
            return fix.result
        }

        private fun isValidPoint(pt: Point): Boolean {
            val p = pt.coordinate
            return p!!.isValid
        }

        /**
         * Returns a clean copy of the input coordinate array.
         *
         * @param pts coordinates to clean
         * @return an array of clean coordinates
         */
        private fun fixCoordinates(pts: Array<Coordinate>): Array<Coordinate> {
            val ptsClean = CoordinateArrays.removeRepeatedOrInvalidPoints(pts)
            return CoordinateArrays.copyDeep(ptsClean)
        }

        private fun fix(geom: Geometry, isKeepCollapsed: Boolean, isKeepMulti: Boolean): Geometry? {
            val fix = GeometryFixer(geom)
            fix.setKeepCollapsed(isKeepCollapsed)
            fix.setKeepMulti(isKeepMulti)
            return fix.result
        }
    }
}