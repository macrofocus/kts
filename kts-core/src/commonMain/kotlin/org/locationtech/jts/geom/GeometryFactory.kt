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

import org.locationtech.jts.geom.impl.CoordinateArraySequenceFactory
import org.locationtech.jts.geom.util.GeometryEditor
import org.locationtech.jts.legacy.Serializable
import org.locationtech.jts.util.Assert
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass

/**
 * Supplies a set of utility methods for building Geometry objects from lists
 * of Coordinates.
 *
 * Note that the factory constructor methods do **not** change the input coordinates in any way.
 * In particular, they are not rounded to the supplied <tt>PrecisionModel</tt>.
 * It is assumed that input Coordinates meet the given precision.
 *
 * Instances of this class are thread-safe.
 *
 * @version 1.7
 */
class GeometryFactory
/**
 * Constructs a GeometryFactory that generates Geometries having a floating
 * PrecisionModel and a spatial-reference ID of 0.
 */ @JvmOverloads constructor(
    /**
     * Returns the PrecisionModel that Geometries created by this factory
     * will be associated with.
     *
     * @return the PrecisionModel for this factory
     */
    val precisionModel: PrecisionModel = PrecisionModel(),
    /**
     * Gets the SRID value defined for this factory.
     *
     * @return the factory SRID value
     */
    val sRID: Int = 0,
    val coordinateSequenceFactory: CoordinateSequenceFactory = defaultCoordinateSequenceFactory
) : Serializable {

    /**
     * Constructs a GeometryFactory that generates Geometries having the given
     * CoordinateSequence implementation, a double-precision floating PrecisionModel and a
     * spatial-reference ID of 0.
     */
    constructor(coordinateSequenceFactory: CoordinateSequenceFactory) : this(
        PrecisionModel(),
        0,
        coordinateSequenceFactory
    ) {
    }

    /**
     * Creates a [Geometry] with the same extent as the given envelope.
     * The Geometry returned is guaranteed to be valid.
     * To provide this behaviour, the following cases occur:
     *
     * If the `Envelope` is:
     *
     *  * null : returns an empty [Point]
     *  * a point : returns a non-empty [Point]
     *  * a line : returns a two-point [LineString]
     *  * a rectangle : returns a [Polygon] whose points are (minx, miny),
     * (minx, maxy), (maxx, maxy), (maxx, miny), (minx, miny).
     *
     * @param  envelope the `Envelope` to convert
     * @return an empty `Point` (for null `Envelope`s),
     * a `Point` (when min x = max x and min y = max y) or a
     * `Polygon` (in all other cases)
     */
    fun toGeometry(envelope: Envelope): Geometry {
        // null envelope - return empty point geometry
        if (envelope.isNull) {
            return createPoint()
        }

        // point?
        if (envelope.minX == envelope.maxX && envelope.minY == envelope.maxY) {
            return createPoint(Coordinate(envelope.minX, envelope.minY))
        }

        // vertical or horizontal line?
        return if (envelope.minX == envelope.maxX
            || envelope.minY == envelope.maxY
        ) {
            createLineString(
                arrayOf(
                    Coordinate(envelope.minX, envelope.minY),
                    Coordinate(envelope.maxX, envelope.maxY)
                )
            )
        } else createPolygon(
            createLinearRing(
                arrayOf(
                    Coordinate(envelope.minX, envelope.minY),
                    Coordinate(envelope.minX, envelope.maxY),
                    Coordinate(envelope.maxX, envelope.maxY),
                    Coordinate(envelope.maxX, envelope.minY),
                    Coordinate(envelope.minX, envelope.minY)
                )
            ), null
        )

        // create a CW ring for the polygon 
    }

    /**
     * Creates a Point using the given Coordinate.
     * A null Coordinate creates an empty Geometry.
     *
     * @param coordinate a Coordinate, or null
     * @return the created Point
     */
    fun createPoint(coordinate: Coordinate?): Point {
        return createPoint(if (coordinate != null) coordinateSequenceFactory.create(arrayOf(coordinate)) else null)
    }
    /**
     * Creates a Point using the given CoordinateSequence; a null or empty
     * CoordinateSequence will create an empty Point.
     *
     * @param coordinates a CoordinateSequence (possibly empty), or null
     * @return the created Point
     */
    /**
     * Constructs an empty [Point] geometry.
     *
     * @return an empty Point
     */
    @JvmOverloads
    fun createPoint(coordinates: CoordinateSequence? = coordinateSequenceFactory.create(arrayOf())): Point {
        return Point(coordinates, this)
    }

    /**
     * Constructs an empty [MultiLineString] geometry.
     *
     * @return an empty MultiLineString
     */
    fun createMultiLineString(): MultiLineString {
        return MultiLineString(null, this)
    }

    /**
     * Creates a MultiLineString using the given LineStrings; a null or empty
     * array will create an empty MultiLineString.
     *
     * @param lineStrings LineStrings, each of which may be empty but not null
     * @return the created MultiLineString
     */
    fun createMultiLineString(lineStrings: Array<LineString>?): MultiLineString {
        return MultiLineString(lineStrings, this)
    }

    /**
     * Constructs an empty [GeometryCollection] geometry.
     *
     * @return an empty GeometryCollection
     */
    fun createGeometryCollection(): GeometryCollection {
        return GeometryCollection(null, this)
    }

    /**
     * Creates a GeometryCollection using the given Geometries; a null or empty
     * array will create an empty GeometryCollection.
     *
     * @param geometries an array of Geometries, each of which may be empty but not null, or null
     * @return the created GeometryCollection
     */
    fun createGeometryCollection(geometries: Array<Geometry>?): GeometryCollection {
        return GeometryCollection(geometries, this)
    }

    /**
     * Constructs an empty [MultiPolygon] geometry.
     *
     * @return an empty MultiPolygon
     */
    fun createMultiPolygon(): MultiPolygon {
        return MultiPolygon(null, this)
    }

    /**
     * Creates a MultiPolygon using the given Polygons; a null or empty array
     * will create an empty Polygon. The polygons must conform to the
     * assertions specified in the <A HREF="http://www.opengis.org/techno/specs.htm">OpenGIS Simple Features
     * Specification for SQL</A>.
     *
     * @param polygons
     * Polygons, each of which may be empty but not null
     * @return the created MultiPolygon
     */
    fun createMultiPolygon(polygons: Array<Polygon>?): MultiPolygon {
        return MultiPolygon(polygons, this)
    }

    /**
     * Creates a [LinearRing] using the given [Coordinate]s.
     * A null or empty array creates an empty LinearRing.
     * The points must form a closed and simple linestring.
     * @param coordinates an array without null elements, or an empty array, or null
     * @return the created LinearRing
     * @throws IllegalArgumentException if the ring is not closed, or has too few points
     */
    fun createLinearRing(coordinates: Array<Coordinate>?): LinearRing {
        return createLinearRing(if (coordinates != null) coordinateSequenceFactory.create(coordinates) else null)
    }
    /**
     * Creates a [LinearRing] using the given [CoordinateSequence].
     * A null or empty array creates an empty LinearRing.
     * The points must form a closed and simple linestring.
     *
     * @param coordinates a CoordinateSequence (possibly empty), or null
     * @return the created LinearRing
     * @throws IllegalArgumentException if the ring is not closed, or has too few points
     */
    /**
     * Constructs an empty [LinearRing] geometry.
     *
     * @return an empty LinearRing
     */
    @JvmOverloads
    fun createLinearRing(coordinates: CoordinateSequence? = coordinateSequenceFactory.create(arrayOf())): LinearRing {
        return LinearRing(coordinates, this)
    }

    /**
     * Constructs an empty [MultiPoint] geometry.
     *
     * @return an empty MultiPoint
     */
    fun createMultiPoint(): MultiPoint {
        return MultiPoint(null, this)
    }

    /**
     * Creates a [MultiPoint] using the given [Point]s.
     * A null or empty array will create an empty MultiPoint.
     *
     * @param point an array of Points (without null elements), or an empty array, or `null`
     * @return a MultiPoint object
     */
    fun createMultiPoint(point: Array<Point>?): MultiPoint {
        return MultiPoint(point, this)
    }

    /**
     * Creates a [MultiPoint] using the given [Coordinate]s.
     * A null or empty array will create an empty MultiPoint.
     *
     * @param coordinates an array (without null elements), or an empty array, or `null`
     * @return a MultiPoint object
     */
    @Deprecated("Use {@link GeometryFactory#createMultiPointFromCoords} instead")
    fun createMultiPoint(coordinates: Array<Coordinate>?): MultiPoint? {
        return createMultiPoint(if (coordinates != null) coordinateSequenceFactory.create(coordinates) else null)
    }

    /**
     * Creates a [MultiPoint] using the given [Coordinate]s.
     * A null or empty array will create an empty MultiPoint.
     *
     * @param coordinates an array (without null elements), or an empty array, or `null`
     * @return a MultiPoint object
     */
    fun createMultiPointFromCoords(coordinates: Array<Coordinate>?): MultiPoint? {
        return createMultiPoint(if (coordinates != null) coordinateSequenceFactory.create(coordinates) else null)
    }

    /**
     * Creates a [MultiPoint] using the
     * points in the given [CoordinateSequence].
     * A `null` or empty CoordinateSequence creates an empty MultiPoint.
     *
     * @param coordinates a CoordinateSequence (possibly empty), or `null`
     * @return a MultiPoint geometry
     */
    fun createMultiPoint(coordinates: CoordinateSequence?): MultiPoint {
        if (coordinates == null) {
            return createMultiPoint(emptyArray<Point>())
        }
        val points = arrayOfNulls<Point>(coordinates.size())
        for (i in 0 until coordinates.size()) {
            val ptSeq = coordinateSequenceFactory
                .create(1, coordinates.dimension, coordinates.measures)
            CoordinateSequences.copy(coordinates, i, ptSeq, 0, 1)
            points[i] = createPoint(ptSeq)
        }
        return createMultiPoint(points.requireNoNulls())
    }
    /**
     * Constructs a `Polygon` with the given exterior boundary and
     * interior boundaries.
     *
     * @param shell
     * the outer boundary of the new `Polygon`, or
     * `null` or an empty `LinearRing` if
     * the empty geometry is to be created.
     * @param holes
     * the inner boundaries of the new `Polygon`, or
     * `null` or empty `LinearRing` s if
     * the empty geometry is to be created.
     * @throws IllegalArgumentException if a ring is invalid
     */
    /**
     * Constructs an empty [Polygon] geometry.
     *
     * @return an empty polygon
     */
    /**
     * Constructs a `Polygon` with the given exterior boundary.
     *
     * @param shell
     * the outer boundary of the new `Polygon`, or
     * `null` or an empty `LinearRing` if
     * the empty geometry is to be created.
     * @throws IllegalArgumentException if the boundary ring is invalid
     */
    @JvmOverloads
    fun createPolygon(shell: LinearRing? = null, holes: Array<LinearRing>? = null): Polygon {
        return Polygon(shell, holes, this)
    }

    /**
     * Constructs a `Polygon` with the given exterior boundary.
     *
     * @param shell
     * the outer boundary of the new `Polygon`, or
     * `null` or an empty `LinearRing` if
     * the empty geometry is to be created.
     * @throws IllegalArgumentException if the boundary ring is invalid
     */
    fun createPolygon(shell: CoordinateSequence?): Polygon {
        return createPolygon(createLinearRing(shell))
    }

    /**
     * Constructs a `Polygon` with the given exterior boundary.
     *
     * @param shell
     * the outer boundary of the new `Polygon`, or
     * `null` or an empty `LinearRing` if
     * the empty geometry is to be created.
     * @throws IllegalArgumentException if the boundary ring is invalid
     */
    fun createPolygon(shell: Array<Coordinate>?): Polygon {
        return createPolygon(createLinearRing(shell))
    }

    /**
     * Build an appropriate `Geometry`, `MultiGeometry`, or
     * `GeometryCollection` to contain the `Geometry`s in
     * it.
     * For example:<br></br>
     *
     *  *  If `geomList` contains a single `Polygon`,
     * the `Polygon` is returned.
     *  *  If `geomList` contains several `Polygon`s, a
     * `MultiPolygon` is returned.
     *  *  If `geomList` contains some `Polygon`s and
     * some `LineString`s, a `GeometryCollection` is
     * returned.
     *  *  If `geomList` is empty, an empty `GeometryCollection`
     * is returned
     *
     * Note that this method does not "flatten" Geometries in the input, and hence if
     * any MultiGeometries are contained in the input a GeometryCollection containing
     * them will be returned.
     *
     * @param  geomList  the `Geometry`s to combine
     * @return           a `Geometry` of the "smallest", "most
     * type-specific" class that can contain the elements of `geomList`
     * .
     */
    fun buildGeometry(geomList: Collection<Geometry>): Geometry {
        /**
         * Determine some facts about the geometries in the list
         */
        var geomClass: KClass<out Geometry>? = null
        var isHeterogeneous = false
        var hasGeometryCollection = false
        val i = geomList.iterator()
        while (i.hasNext()) {
            val geom = i.next()
            val partClass: KClass<out Geometry> = geom::class
            if (geomClass == null) {
                geomClass = partClass
            }
            if (partClass != geomClass) {
                isHeterogeneous = true
            }
            if (geom is GeometryCollection) hasGeometryCollection = true
        }
        /**
         * Now construct an appropriate geometry to return
         */
        // for the empty geometry, return an empty GeometryCollection
        if (geomClass == null) {
            return createGeometryCollection()
        }
        if (isHeterogeneous || hasGeometryCollection) {
            return createGeometryCollection(toGeometryArray(geomList))
        }
        // at this point we know the collection is hetereogenous.
        // Determine the type of the result from the first Geometry in the list
        // this should always return a geometry, since otherwise an empty collection would have already been returned
        val geom0: Geometry = geomList.iterator().next() as Geometry
        val isCollection = geomList.size > 1
        if (isCollection) {
            if (geom0 is Polygon) {
                return createMultiPolygon(toPolygonArray(geomList))
            } else if (geom0 is LineString) {
                return createMultiLineString(toLineStringArray(geomList))
            } else if (geom0 is Point) {
                return createMultiPoint(toPointArray(geomList))
            }
            Assert.shouldNeverReachHere("Unhandled class: " + geom0::class)
        }
        return geom0
    }

    /**
     * Creates a LineString using the given Coordinates.
     * A null or empty array creates an empty LineString.
     *
     * @param coordinates an array without null elements, or an empty array, or null
     */
    fun createLineString(coordinates: Array<Coordinate>?): LineString {
        return createLineString(if (coordinates != null) coordinateSequenceFactory.create(coordinates) else null)
    }
    /**
     * Creates a LineString using the given CoordinateSequence.
     * A null or empty CoordinateSequence creates an empty LineString.
     *
     * @param coordinates a CoordinateSequence (possibly empty), or null
     */
    /**
     * Constructs an empty [LineString] geometry.
     *
     * @return an empty LineString
     */
    @JvmOverloads
    fun createLineString(coordinates: CoordinateSequence? = coordinateSequenceFactory.create(arrayOf())): LineString {
        return LineString(coordinates, this)
    }

    /**
     * Creates an empty atomic geometry of the given dimension.
     * If passed a dimension of -1 will create an empty [GeometryCollection].
     *
     * @param dimension the required dimension (-1, 0, 1 or 2)
     * @return an empty atomic geometry of given dimension
     */
    fun createEmpty(dimension: Int): Geometry {
        return when (dimension) {
            -1 -> createGeometryCollection()
            0 -> createPoint()
            1 -> createLineString()
            2 -> createPolygon()
            else -> throw IllegalArgumentException("Invalid dimension: $dimension")
        }
    }

    /**
     * Creates a deep copy of the input [Geometry].
     * The [CoordinateSequenceFactory] defined for this factory
     * is used to copy the [CoordinateSequence]s
     * of the input geometry.
     *
     * This is a convenient way to change the <tt>CoordinateSequence</tt>
     * used to represent a geometry, or to change the
     * factory used for a geometry.
     *
     * [Geometry.copy] can also be used to make a deep copy,
     * but it does not allow changing the CoordinateSequence type.
     *
     * @return a deep copy of the input geometry, using the CoordinateSequence type of this factory
     *
     * @see Geometry.copy
     */
    fun createGeometry(g: Geometry?): Geometry? {
        val editor = GeometryEditor(this)
        return editor.edit(g, CoordSeqCloneOp(coordinateSequenceFactory))
    }

    private class CoordSeqCloneOp(var coordinateSequenceFactory: CoordinateSequenceFactory) :
        GeometryEditor.CoordinateSequenceOperation() {
        override fun edit(coordSeq: CoordinateSequence?, geometry: Geometry?): CoordinateSequence {
            return coordinateSequenceFactory.create(coordSeq!!)
        }
    }
    /**
     * Constructs a GeometryFactory that generates Geometries having the given
     * PrecisionModel, spatial-reference ID, and CoordinateSequence implementation.
     */
    /**
     * Constructs a GeometryFactory that generates Geometries having the given
     * [PrecisionModel] and the default CoordinateSequence
     * implementation.
     *
     * @param precisionModel the PrecisionModel to use
     */
    /**
     * Constructs a GeometryFactory that generates Geometries having the given
     * [PrecisionModel] and spatial-reference ID, and the default CoordinateSequence
     * implementation.
     *
     * @param precisionModel the PrecisionModel to use
     * @param sRID the SRID to use
     */
    companion object {
        private const val serialVersionUID = -6820524753094095635L
        fun createPointFromInternalCoord(coord: Coordinate, exemplar: Geometry): Point {
            exemplar.precisionModel.makePrecise(coord)
            return exemplar.factory.createPoint(coord)
        }

        private val defaultCoordinateSequenceFactory: CoordinateSequenceFactory
            private get() = CoordinateArraySequenceFactory.instance()

        /**
         * Converts the `List` to an array.
         *
         * @param  points  the `List` of Points to convert
         * @return         the `List` in array format
         */
        @JvmStatic
        fun toPointArray(points: Collection<Geometry>): Array<Point> {
            return points.map { it as Point }.toTypedArray()
        }

        /**
         * Converts the `List` to an array.
         *
         * @param  geometries  the list of `Geometry's` to convert
         * @return            the `List` in array format
         */
        @JvmStatic
        fun toGeometryArray(geometries: Collection<Geometry>?): Array<Geometry>? {
            if (geometries == null) return null
            val geometryArray: Array<Geometry?> =
                arrayOfNulls<Geometry>(geometries.size)
            return geometries.map { it as Geometry }.toTypedArray()
        }

        /**
         * Converts the `List` to an array.
         *
         * @param  linearRings  the `List` of LinearRings to convert
         * @return              the `List` in array format
         */
        @JvmStatic
        fun toLinearRingArray(linearRings: Collection<Geometry>): Array<LinearRing> {
            return linearRings.map { it as LinearRing }.toTypedArray()
        }

        /**
         * Converts the `List` to an array.
         *
         * @param  lineStrings  the `List` of LineStrings to convert
         * @return              the `List` in array format
         */
        @JvmStatic
        fun toLineStringArray(lineStrings: Collection<Geometry>): Array<LineString> {
            return lineStrings.map { it as LineString }.toTypedArray()
        }

        /**
         * Converts the `List` to an array.
         *
         * @param  polygons  the `List` of Polygons to convert
         * @return           the `List` in array format
         */
        @JvmStatic
        fun toPolygonArray(polygons: Collection<Geometry>): Array<Polygon> {
            return polygons.map { it as Polygon }.toTypedArray()
        }

        /**
         * Converts the `List` to an array.
         *
         * @param  multiPolygons  the `List` of MultiPolygons to convert
         * @return                the `List` in array format
         */
        @JvmStatic
        fun toMultiPolygonArray(multiPolygons: Collection<Geometry>): Array<MultiPolygon> {
            return multiPolygons.map { it as MultiPolygon }.toTypedArray()
        }

        /**
         * Converts the `List` to an array.
         *
         * @param  multiLineStrings  the `List` of MultiLineStrings to convert
         * @return                   the `List` in array format
         */
        @JvmStatic
        fun toMultiLineStringArray(multiLineStrings: Collection<Geometry>): Array<MultiLineString> {
            return multiLineStrings.map { it as MultiLineString }.toTypedArray()
        }

        /**
         * Converts the `List` to an array.
         *
         * @param  multiPoints  the `List` of MultiPoints to convert
         * @return              the `List` in array format
         */
        @JvmStatic
        fun toMultiPointArray(multiPoints: Collection<Geometry>): Array<MultiPoint> {
            return multiPoints.map { it as MultiPoint }.toTypedArray()
        }
    }
}