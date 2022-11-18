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
package org.locationtech.jts.geom

import org.locationtech.jts.algorithm.Area
import org.locationtech.jts.algorithm.Orientation

/**
 * Represents a polygon with linear edges, which may include holes.
 * The outer boundary (shell)
 * and inner boundaries (holes) of the polygon are represented by [LinearRing]s.
 * The boundary rings of the polygon may have any orientation.
 * Polygons are closed, simple geometries by definition.
 *
 * The polygon model conforms to the assertions specified in the
 * <A HREF="http://www.opengis.org/techno/specs.htm">OpenGIS Simple Features
 * Specification for SQL</A>.
 *
 * A `Polygon` is topologically valid if and only if:
 *
 *  * the coordinates which define it are valid coordinates
 *  * the linear rings for the shell and holes are valid
 * (i.e. are closed and do not self-intersect)
 *  * holes touch the shell or another hole at at most one point
 * (which implies that the rings of the shell and holes must not cross)
 *  * the interior of the polygon is connected,
 * or equivalently no sequence of touching holes
 * makes the interior of the polygon disconnected
 * (i.e. effectively split the polygon into two pieces).
 *
 * @version 1.7
 */
open class Polygon(shell: LinearRing?, holes: Array<LinearRing>?, factory: GeometryFactory) :
    Geometry(factory), Polygonal {
    /**
     * The exterior boundary,
     * or `null` if this `Polygon`
     * is empty.
     */
    protected var shell: LinearRing? = null

    /**
     * The interior boundaries, if any.
     * This instance var is never null.
     * If there are no holes, the array is of zero length.
     */
    protected var holes: Array<LinearRing>

    /**
     * Constructs a `Polygon` with the given exterior boundary.
     *
     * @param  shell           the outer boundary of the new `Polygon`,
     * or `null` or an empty `LinearRing` if the empty
     * geometry is to be created.
     * @param  precisionModel  the specification of the grid of allowable points
     * for this `Polygon`
     * @param  SRID            the ID of the Spatial Reference System used by this
     * `Polygon`
     */
    @Deprecated("Use GeometryFactory instead")
    constructor(shell: LinearRing?, precisionModel: PrecisionModel, SRID: Int) : this(
        shell,
        arrayOf<LinearRing>(),
        GeometryFactory(precisionModel, SRID)
    ) {
    }

    /**
     * Constructs a `Polygon` with the given exterior boundary and
     * interior boundaries.
     *
     * @param  shell           the outer boundary of the new `Polygon`,
     * or `null` or an empty `LinearRing` if the empty
     * geometry is to be created.
     * @param  holes           the inner boundaries of the new `Polygon`
     * , or `null` or empty `LinearRing`s if the empty
     * geometry is to be created.
     * @param  precisionModel  the specification of the grid of allowable points
     * for this `Polygon`
     * @param  SRID            the ID of the Spatial Reference System used by this
     * `Polygon`
     */
    @Deprecated("Use GeometryFactory instead")
    constructor(
        shell: LinearRing?,
        holes: Array<LinearRing>?,
        precisionModel: PrecisionModel,
        SRID: Int
    ) : this(shell, holes, GeometryFactory(precisionModel, SRID)) {
    }

    /**
     * Constructs a `Polygon` with the given exterior boundary and
     * interior boundaries.
     *
     * @param  shell           the outer boundary of the new `Polygon`,
     * or `null` or an empty `LinearRing` if the empty
     * geometry is to be created.
     * @param  holes           the inner boundaries of the new `Polygon`
     * , or `null` or empty `LinearRing`s if the empty
     * geometry is to be created.
     */
    init {
        var shell: LinearRing? = shell
        var holes: Array<LinearRing>? = holes
        if (shell == null) {
            shell = factory.createLinearRing()
        }
        if (holes == null) {
            holes = arrayOf<LinearRing>()
        }
        // Not necessary due to null safety
//        if (hasNullElements(holes)) {
//            throw IllegalArgumentException("holes must not contain null elements")
//        }
        if (shell.isEmpty && hasNonEmptyElements(holes as Array<Geometry>)) {
            throw IllegalArgumentException("shell is empty but holes are not")
        }
        this.shell = shell
        this.holes = holes
    }

    override val coordinate: Coordinate
        get() = shell!!.coordinate!!
    override val coordinates: Array<Coordinate>
        get() {
            if (isEmpty) {
                return arrayOf()
            }
            val coordinates = arrayOfNulls<Coordinate>(
                numPoints
            )
            var k = -1
            val shellCoordinates: Array<Coordinate> = shell!!.coordinates
            for (x in shellCoordinates.indices) {
                k++
                coordinates[k] = shellCoordinates[x]
            }
            for (i in holes.indices) {
                val childCoordinates: Array<Coordinate> = holes[i].coordinates
                for (j in childCoordinates.indices) {
                    k++
                    coordinates[k] = childCoordinates[j]
                }
            }
            return coordinates.requireNoNulls()
        }
    override val numPoints: Int
        get() {
            var numPoints: Int = shell!!.numPoints
            for (i in holes.indices) {
                numPoints += holes[i].numPoints
            }
            return numPoints
        }
    override val dimension: Int
        get() = 2
    override val boundaryDimension: Int
        get() = 1
    override val isEmpty: Boolean
        get() = shell!!.isEmpty

    // check vertices have correct values
    override val isRectangle: Boolean
        // check vertices are in right order
        get() {
            if (getNumInteriorRing() != 0) return false
            if (shell == null) return false
            if (shell!!.numPoints !== 5) return false
            val seq: CoordinateSequence = shell!!.coordinateSequence!!

            // check vertices have correct values
            val env: Envelope = envelopeInternal
            for (i in 0..4) {
                val x = seq.getX(i)
                if (!(x == env.minX || x == env.maxX)) return false
                val y = seq.getY(i)
                if (!(y == env.minY || y == env.maxY)) return false
            }

            // check vertices are in right order
            var prevX = seq.getX(0)
            var prevY = seq.getY(0)
            for (i in 1..4) {
                val x = seq.getX(i)
                val y = seq.getY(i)
                val xChanged = x != prevX
                val yChanged = y != prevY
                if (xChanged == yChanged) return false
                prevX = x
                prevY = y
            }
            return true
        }
    val exteriorRing: LinearRing?
        get() = shell

    fun getNumInteriorRing(): Int {
        return holes.size
    }

    fun getInteriorRingN(n: Int): LinearRing {
        return holes[n]
    }

    override val geometryType: String?
        get() = TYPENAME_POLYGON

    /**
     * Returns the area of this `Polygon`
     *
     * @return the area of the polygon
     */
    override val area: Double
        get() {
            var area = 0.0
            area += Area.ofRing(shell!!.coordinateSequence!!)
            for (i in holes.indices) {
                area -= Area.ofRing(holes[i].coordinateSequence!!)
            }
            return area
        }

    /**
     * Returns the perimeter of this `Polygon`
     *
     * @return the perimeter of the polygon
     */
    override val length: Double
        get() {
            var len = 0.0
            len += shell!!.length
            for (i in holes.indices) {
                len += holes[i].length
            }
            return len
        }

    /**
     * Computes the boundary of this geometry
     *
     * @return a lineal geometry (which may be empty)
     * @see Geometry.getBoundary
     */
    override val boundary: Geometry?
        get() = getBoundaryFn()

    private fun getBoundaryFn(): Geometry {
        if (isEmpty) {
            return factory.createMultiLineString()
        }
        val rings: Array<LinearRing?> = arrayOfNulls<LinearRing>(holes.size + 1)
        rings[0] = shell
        for (i in holes.indices) {
            rings[i + 1] = holes[i]
        }
        // create LineString or MultiLineString as appropriate
        return if (rings.size <= 1) factory.createLinearRing(rings[0]!!.coordinateSequence) else factory.createMultiLineString(
            rings.requireNoNulls() as Array<LineString>
        )
    }

    override fun computeEnvelopeInternal(): Envelope {
        return shell!!.envelopeInternal
    }

    override fun equalsExact(other: Geometry?, tolerance: Double): Boolean {
        if (!isEquivalentClass(other!!)) {
            return false
        }
        val otherPolygon = other as Polygon
        val thisShell: Geometry? = shell
        val otherPolygonShell: Geometry? = otherPolygon.shell
        if (!thisShell!!.equalsExact(otherPolygonShell, tolerance)) {
            return false
        }
        if (holes.size != otherPolygon.holes.size) {
            return false
        }
        for (i in holes.indices) {
            if (!(holes[i] as Geometry).equalsExact(otherPolygon.holes[i], tolerance)) {
                return false
            }
        }
        return true
    }

    override fun apply(filter: CoordinateFilter) {
        shell!!.apply(filter)
        for (i in holes.indices) {
            holes[i].apply(filter)
        }
    }

    override fun apply(filter: CoordinateSequenceFilter) {
        shell!!.apply(filter)
        if (!filter.isDone) {
            for (i in holes.indices) {
                holes[i].apply(filter)
                if (filter.isDone) break
            }
        }
        if (filter.isGeometryChanged) geometryChanged()
    }

    override fun apply(filter: GeometryFilter) {
        filter.filter(this)
    }

    override fun apply(filter: GeometryComponentFilter) {
        filter.filter(this)
        shell!!.apply(filter)
        for (i in holes.indices) {
            holes[i].apply(filter)
        }
    }

    /**
     * Creates and returns a full copy of this [Polygon] object.
     * (including all coordinates contained by it).
     *
     * @return a clone of this instance
     */
    @Deprecated("")
    override fun clone(): Any {
        return copy()
    }

    override fun copyInternal(): Polygon {
        val shellCopy: LinearRing = shell!!.copy() as LinearRing
        val holeCopies: Array<LinearRing?> = arrayOfNulls<LinearRing>(holes.size)
        for (i in holes.indices) {
            holeCopies[i] = holes[i].copy() as LinearRing
        }
        return Polygon(shellCopy, holeCopies.requireNoNulls(), factory)
    }

    override fun convexHull(): Geometry {
        return exteriorRing!!.convexHull()!!
    }

    override fun normalize() {
        shell = normalized(shell!!, true)
        for (i in holes.indices) {
            holes[i] = normalized(holes[i], false)
        }
        holes.sort()
    }

    override fun compareToSameClass(o: Any?): Int {
        val poly = o as Polygon
        val thisShell: LinearRing? = shell
        val otherShell: LinearRing? = poly.shell
        val shellComp: Int = thisShell!!.compareToSameClass(otherShell)
        if (shellComp != 0) return shellComp
        val nHole1 = getNumInteriorRing()
        val nHole2 = o.getNumInteriorRing()
        var i = 0
        while (i < nHole1 && i < nHole2) {
            val holeComp: Int = getInteriorRingN(i).compareToSameClass(poly.getInteriorRingN(i))
            if (holeComp != 0) return holeComp
            i++
        }
        if (i < nHole1) return 1
        return if (i < nHole2) -1 else 0
    }

    override fun compareToSameClass(o: Any?, comp: CoordinateSequenceComparator): Int {
        val poly = o as Polygon
        val thisShell: LinearRing? = shell
        val otherShell: LinearRing? = poly.shell
        val shellComp: Int = thisShell!!.compareToSameClass(otherShell, comp)
        if (shellComp != 0) return shellComp
        val nHole1 = getNumInteriorRing()
        val nHole2 = poly.getNumInteriorRing()
        var i = 0
        while (i < nHole1 && i < nHole2) {
            val holeComp: Int = getInteriorRingN(i).compareToSameClass(poly.getInteriorRingN(i), comp)
            if (holeComp != 0) return holeComp
            i++
        }
        if (i < nHole1) return 1
        return if (i < nHole2) -1 else 0
    }

    override val typeCode: Int
        get() = TYPECODE_POLYGON

    private fun normalized(ring: LinearRing, clockwise: Boolean): LinearRing {
        val res: LinearRing = ring.copy() as LinearRing
        normalize(res, clockwise)
        return res
    }

    private fun normalize(ring: LinearRing, clockwise: Boolean) {
        if (ring.isEmpty) {
            return
        }
        val seq: CoordinateSequence = ring.coordinateSequence!!
        val minCoordinateIndex = CoordinateSequences.minCoordinateIndex(seq, 0, seq.size() - 2)
        CoordinateSequences.scroll(seq, minCoordinateIndex, true)
        if (Orientation.isCCW(seq) === clockwise) CoordinateSequences.reverse(seq)
    }

    override fun reverse(): Polygon {
        return super.reverse() as Polygon
    }

    override fun reverseInternal(): Polygon {
        val holes: Array<LinearRing?> = arrayOfNulls<LinearRing>(getNumInteriorRing())
        for (i in holes.indices) {
            holes[i] = getInteriorRingN(i).reverse()
        }
        return factory.createPolygon(exteriorRing!!.reverse(), holes.requireNoNulls())
    }

    companion object {
        private const val serialVersionUID = -3494792200821764533L
    }
}