package org.locationtech.jts.geom

import org.locationtech.jts.geom.CoordinateSequences.copy
import org.locationtech.jts.geom.impl.CoordinateArraySequenceFactory.Companion.instance
import org.locationtech.jts.geom.util.GeometryEditor
import org.locationtech.jts.geom.util.GeometryEditor.CoordinateSequenceOperation
import org.locationtech.jts.legacy.Serializable
import org.locationtech.jts.util.Assert.shouldNeverReachHere
import kotlin.jvm.JvmStatic
import kotlin.reflect.KClass

open class GeometryFactory
/**
 * Constructs a GeometryFactory that generates Geometries having the given
 * PrecisionModel, spatial-reference ID, and CoordinateSequence implementation.
 */
    (
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
    val SRID: Int = 0,
    val coordinateSequenceFactory: CoordinateSequenceFactory = instance()
) : Serializable {
    constructor(precisionModel: PrecisionModel, SRID: Int) : this(precisionModel, SRID, instance())

    constructor(coordinateSequenceFactory: CoordinateSequenceFactory) : this(
        PrecisionModel(),
        0,
        coordinateSequenceFactory
    )

    constructor(precisionModel: PrecisionModel) : this(precisionModel, 0, instance())

    private val serialVersionUID = -6820524753094095635L

    fun createPointFromInternalCoord(coord: Coordinate?, exemplar: Geometry): Point? {
        exemplar.precisionModel.makePrecise(coord!!)
        return exemplar.factory.createPoint(coord)
    }


    private fun getDefaultCoordinateSequenceFactory(): CoordinateSequenceFactory? {
        return instance()
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
     * Constructs an empty [Point] geometry.
     *
     * @return an empty Point
     */
    fun createPoint(): Point {
        return createPoint(coordinateSequenceFactory.create(arrayOf()))
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
    fun createPoint(coordinates: CoordinateSequence?): Point {
        return Point(coordinates, this)
    }

    /**
     * Constructs an empty [MultiLineString] geometry.
     *
     * @return an empty MultiLineString
     */
    fun createMultiLineString(): MultiLineString? {
        return MultiLineString(null, this)
    }

    /**
     * Creates a MultiLineString using the given LineStrings; a null or empty
     * array will create an empty MultiLineString.
     *
     * @param lineStrings LineStrings, each of which may be empty but not null
     * @return the created MultiLineString
     */
    fun createMultiLineString(lineStrings: Array<LineString>?): MultiLineString? {
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
    fun createMultiPolygon(): MultiPolygon? {
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
     * Constructs an empty [LinearRing] geometry.
     *
     * @return an empty LinearRing
     */
    fun createLinearRing(): LinearRing {
        return createLinearRing(coordinateSequenceFactory.create(arrayOf()))
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
    fun createLinearRing(coordinates: CoordinateSequence?): LinearRing {
        return LinearRing(coordinates, this)
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
        CoordinateSequenceOperation() {
        override fun edit(coordSeq: CoordinateSequence?, geometry: Geometry?): CoordinateSequence? {
            return coordinateSequenceFactory.create(coordSeq!!)
        }
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
    fun createMultiPoint(coordinates: CoordinateSequence?): MultiPoint? {
        if (coordinates == null) {
            return createMultiPoint(arrayOf<Point>())
        }
        val points = arrayOfNulls<Point>(coordinates.size())
        for (i in 0 until coordinates.size()) {
            val ptSeq: CoordinateSequence = coordinateSequenceFactory
                .create(1, coordinates.dimension, coordinates.measures)
            copy(coordinates, i, ptSeq, 0, 1)
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
    fun createPolygon(shell: LinearRing?, holes: Array<LinearRing?>?): Polygon {
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
    fun createPolygon(shell: CoordinateSequence): Polygon? {
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
    fun createPolygon(shell: Array<Coordinate>): Polygon? {
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
    fun createPolygon(shell: LinearRing): Polygon? {
        return createPolygon(shell, null)
    }

    /**
     * Constructs an empty [Polygon] geometry.
     *
     * @return an empty polygon
     */
    fun createPolygon(): Polygon {
        return createPolygon(null, null)
    }

    fun buildGeometry(geomList: Collection<Geometry>): Geometry {
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
        // at this point we know the collection is hetereogenous.
        // Determine the type of the result from the first Geometry in the list
        // this should always return a geometry, since otherwise an empty collection would have already been returned
        val geom0 = geomList.iterator().next()
        val isCollection = geomList.size > 1
        if (isCollection) {
            when (geom0) {
                is Polygon -> {
                    return createMultiPolygon(toPolygonArray(geomList))
                }
                is LineString -> {
                    return createMultiLineString(toLineStringArray(geomList))!!
                }
                is Point -> {
                    return createMultiPoint(toPointArray(geomList))
                }
                else -> shouldNeverReachHere("Unhandled class: " + geom0::class)
            }
        }
        return geom0
    }

    /**
     * Constructs an empty [LineString] geometry.
     *
     * @return an empty LineString
     */
    fun createLineString(): LineString {
        return createLineString(coordinateSequenceFactory.create(arrayOf()))
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
    fun createLineString(coordinates: CoordinateSequence?): LineString {
        return LineString(coordinates, this)
    }

    companion object {
        @JvmStatic
        fun toGeometryArray(geometries: Collection<Geometry>): Array<Geometry> {
//            if (geometries == null) return null;
            return geometries.toTypedArray()
        }

        @JvmStatic
        fun toPolygonArray(geometries: Collection<Geometry>): Array<Polygon> {
//            if (geometries == null) return null;
            return geometries.map { it as Polygon }.toTypedArray()
        }

        @JvmStatic
        fun toLineStringArray(geometries: Collection<Geometry>): Array<LineString> {
//            if (geometries == null) return null;
            return geometries.map { it as LineString }.toTypedArray()
        }

        @JvmStatic
        fun createMultiLineString(geometries: Collection<Geometry>): Array<Geometry> {
//            if (geometries == null) return null;
            return geometries.map { it as MultiLineString }.toTypedArray()
        }

        @JvmStatic
        fun toPointArray(geometries: Collection<Geometry>): Array<Point> {
//            if (geometries == null) return null;
            return geometries.map { it as Point }.toTypedArray()
        }
    }
}
