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

import org.locationtech.jts.legacy.Math.max
import org.locationtech.jts.util.Assert

/**
 * Models a collection of [Geometry]s of
 * arbitrary type and dimension.
 *
 * @version 1.7
 */
open class GeometryCollection(geometries: Array<Geometry>?, factory: GeometryFactory) : Geometry(
    factory
) {
    /**
     * Internal representation of this `GeometryCollection`.
     */
    protected var geometries: Array<Geometry>

    @Deprecated("Use GeometryFactory instead ")
    constructor(
        geometries: Array<Geometry>?,
        precisionModel: PrecisionModel,
        SRID: Int
    ) : this(geometries, GeometryFactory(precisionModel, SRID))

    /**
     * @param geometries
     * the `Geometry`s for this `GeometryCollection`,
     * or `null` or an empty array to create the empty
     * geometry. Elements may be empty `Geometry`s,
     * but not `null`s.
     */
    init {
        var geometries: Array<Geometry>? = geometries
        if (geometries == null) {
            geometries = arrayOf()
        }
        // Unnecessary due to null safety
//        if (hasNullElements(geometries)) {
//            throw IllegalArgumentException("geometries must not contain null elements")
//        }
        this.geometries = geometries
    }

    override val coordinate: Coordinate?
        get() = if (isEmpty) null else geometries[0].coordinate

    /**
     * Collects all coordinates of all subgeometries into an Array.
     *
     * Note that while changes to the coordinate objects themselves
     * may modify the Geometries in place, the returned Array as such
     * is only a temporary container which is not synchronized back.
     *
     * @return the collected coordinates
     */
    override val coordinates: Array<Coordinate>
        get() {
            val coordinates = arrayOfNulls<Coordinate>(numPoints)
            var k = -1
            for (i in geometries.indices) {
                val childCoordinates: Array<Coordinate> = geometries[i].coordinates!!
                for (j in childCoordinates.indices) {
                    k++
                    coordinates[k] = childCoordinates[j]
                }
            }
            return coordinates.requireNoNulls()
        }
    override val isEmpty: Boolean
        get() {
            for (i in geometries.indices) {
                if (!geometries[i].isEmpty) {
                    return false
                }
            }
            return true
        }
    override val dimension: Int
        get() {
            var dimension: Int = Dimension.FALSE
            for (i in geometries.indices) {
                dimension = max(dimension, geometries[i].dimension)
            }
            return dimension
        }
    override val boundaryDimension: Int
        get() {
            var dimension: Int = Dimension.FALSE
            for (i in geometries.indices) {
                dimension = max(
                    dimension,
                    geometries[i].boundaryDimension
                )
            }
            return dimension
        }

    override val numGeometries: Int
        get() = geometries.size

    override fun getGeometryN(n: Int): Geometry {
        return geometries[n]
    }

    override val numPoints: Int
        get() {
            var numPoints = 0
            for (i in geometries.indices) {
                numPoints += geometries[i].numPoints
            }
            return numPoints
        }

    override val geometryType: String
        get() = TYPENAME_GEOMETRYCOLLECTION

    override val boundary: Geometry?
        get() {
            checkNotGeometryCollection(this)
            Assert.shouldNeverReachHere()
            return null
        }

    /**
     * Returns the area of this `GeometryCollection`
     *
     * @return the area of the polygon
     */
    override val area: Double
        get() {
            var area = 0.0
            for (i in geometries.indices) {
                area += geometries[i].area
            }
            return area
        }

    override val length: Double
        get() {
            var sum = 0.0
            for (i in geometries.indices) {
                sum += geometries[i].length
            }
            return sum
        }

    override fun equalsExact(other: Geometry?, tolerance: Double): Boolean {
        if (!isEquivalentClass(other!!)) {
            return false
        }
        val otherCollection = other as GeometryCollection
        if (geometries.size != otherCollection.geometries.size) {
            return false
        }
        for (i in geometries.indices) {
            if (!geometries[i].equalsExact(
                    otherCollection.geometries[i],
                    tolerance
                )
            ) {
                return false
            }
        }
        return true
    }

    override fun apply(filter: CoordinateFilter) {
        for (i in geometries.indices) {
            geometries[i].apply(filter)
        }
    }

    override fun apply(filter: CoordinateSequenceFilter) {
        if (geometries.isEmpty()) return
        for (i in geometries.indices) {
            geometries[i].apply(filter)
            if (filter.isDone) {
                break
            }
        }
        if (filter.isGeometryChanged) geometryChanged()
    }

    override fun apply(filter: GeometryFilter) {
        filter.filter(this)
        for (i in geometries.indices) {
            geometries[i].apply(filter)
        }
    }

    override fun apply(filter: GeometryComponentFilter) {
        filter.filter(this)
        for (i in geometries.indices) {
            geometries[i].apply(filter)
        }
    }

    /**
     * Creates and returns a full copy of this [GeometryCollection] object.
     * (including all coordinates contained by it).
     *
     * @return a clone of this instance
     */
    @Deprecated("")
    override fun clone(): Any {
        return copy()
    }

    override fun copyInternal(): GeometryCollection {
        val geometries: Array<Geometry?> = arrayOfNulls(
            geometries.size
        )
        for (i in geometries.indices) {
            geometries[i] = this.geometries[i].copy()
        }
        return GeometryCollection(geometries.requireNoNulls(), factory)
    }

    override fun normalize() {
        for (i in geometries.indices) {
            geometries[i].normalize()
        }
        geometries.sort()
    }

    override fun computeEnvelopeInternal(): Envelope {
        val envelope = Envelope()
        for (i in geometries.indices) {
            envelope.expandToInclude(geometries[i].envelopeInternal)
        }
        return envelope
    }

    override fun compareToSameClass(o: Any?): Int {
//        val theseElements: TreeSet =
//            TreeSet(Arrays.asList<Geometry>(*geometries))
//        val otherElements: TreeSet =
//            TreeSet(Arrays.asList<Geometry>(*(o as GeometryCollection).geometries))
//        return compare(theseElements, otherElements)
        // ToDo: remimplementation not equivalent
        return compare(geometries.toList(), (o as GeometryCollection).geometries.toList())
    }

    override fun compareToSameClass(o: Any?, comp: CoordinateSequenceComparator): Int {
        val gc = o as GeometryCollection
        val n1 = numGeometries
        val n2 = gc.numGeometries
        var i = 0
        while (i < n1 && i < n2) {
            val thisGeom: Geometry = getGeometryN(i)
            val otherGeom: Geometry = gc.getGeometryN(i)
            val holeComp: Int = thisGeom.compareToSameClass(otherGeom, comp)
            if (holeComp != 0) return holeComp
            i++
        }
        if (i < n1) return 1
        return if (i < n2) -1 else 0
    }

    override val typeCode: Int
        get() = TYPECODE_GEOMETRYCOLLECTION

    /**
     * Creates a [GeometryCollection] with
     * every component reversed.
     * The order of the components in the collection are not reversed.
     *
     * @return a [GeometryCollection] in the reverse order
     */
    override fun reverse(): GeometryCollection {
        return super.reverse() as GeometryCollection
    }

    override fun reverseInternal(): GeometryCollection {
        val geometries: Array<Geometry?> = arrayOfNulls(
            geometries.size
        )
        for (i in geometries.indices) {
            geometries[i] = this.geometries[i].reverse()
        }
        return GeometryCollection(geometries.requireNoNulls(), factory)
    }

    companion object {
        //  With contributions from Markus Schaber [schabios@logi-track.com] 2004-03-26
        private const val serialVersionUID = -5694727726395021467L
    }
}