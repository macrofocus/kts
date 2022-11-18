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

import org.locationtech.jts.algorithm.Centroid
import org.locationtech.jts.algorithm.ConvexHull
import org.locationtech.jts.algorithm.InteriorPoint
import org.locationtech.jts.io.WKTWriter
import org.locationtech.jts.legacy.Cloneable
import org.locationtech.jts.legacy.Serializable
import org.locationtech.jts.operation.buffer.BufferOp
import org.locationtech.jts.operation.distance.DistanceOp
import org.locationtech.jts.operation.predicate.RectangleContains
import org.locationtech.jts.operation.predicate.RectangleIntersects
import org.locationtech.jts.operation.relate.RelateOp
import org.locationtech.jts.operation.valid.IsSimpleOp
import org.locationtech.jts.operation.valid.IsValidOp
import kotlin.jvm.JvmStatic

/**
 * A representation of a planar, linear vector geometry.
 * <P>
 *
</P> * <H3>Binary Predicates</H3>
 * Because it is not clear at this time
 * what semantics for spatial
 * analysis methods involving `GeometryCollection`s would be useful,
 * `GeometryCollection`s are not supported as arguments to binary
 * predicates or the `relate`
 * method.
 *
 * <H3>Overlay Methods</H3>
 *
 * The overlay methods
 * return the most specific class possible to represent the result. If the
 * result is homogeneous, a `Point`, `LineString`, or
 * `Polygon` will be returned if the result contains a single
 * element; otherwise, a `MultiPoint`, `MultiLineString`,
 * or `MultiPolygon` will be returned. If the result is
 * heterogeneous a `GeometryCollection` will be returned. <P>
 *
 * Because it is not clear at this time what semantics for set-theoretic
 * methods involving `GeometryCollection`s would be useful,
 * `GeometryCollections`
 * are not supported as arguments to the set-theoretic methods.
 *
</P> * <H4>Representation of Computed Geometries </H4>
 *
 * The SFS states that the result
 * of a set-theoretic method is the "point-set" result of the usual
 * set-theoretic definition of the operation (SFS 3.2.21.1). However, there are
 * sometimes many ways of representing a point set as a `Geometry`.
 * <P>
 *
 * The SFS does not specify an unambiguous representation of a given point set
 * returned from a spatial analysis method. One goal of JTS is to make this
 * specification precise and unambiguous. JTS uses a canonical form for
 * `Geometry`s returned from overlay methods. The canonical
 * form is a `Geometry` which is simple and noded:
</P> * <UL>
 * <LI> Simple means that the Geometry returned will be simple according to
 * the JTS definition of `isSimple`.
</LI> * <LI> Noded applies only to overlays involving `LineString`s. It
 * means that all intersection points on `LineString`s will be
 * present as endpoints of `LineString`s in the result.
</LI></UL> *
 * This definition implies that non-simple geometries which are arguments to
 * spatial analysis methods must be subjected to a line-dissolve process to
 * ensure that the results are simple.
 *
 * <H4> Constructed Points And The Precision Model </H4>
 *
 * The results computed by the set-theoretic methods may
 * contain constructed points which are not present in the input `Geometry`
 * s. These new points arise from intersections between line segments in the
 * edges of the input `Geometry`s. In the general case it is not
 * possible to represent constructed points exactly. This is due to the fact
 * that the coordinates of an intersection point may contain twice as many bits
 * of precision as the coordinates of the input line segments. In order to
 * represent these constructed points explicitly, JTS must truncate them to fit
 * the `PrecisionModel`. <P>
 *
 * Unfortunately, truncating coordinates moves them slightly. Line segments
 * which would not be coincident in the exact result may become coincident in
 * the truncated representation. This in turn leads to "topology collapses" --
 * situations where a computed element has a lower dimension than it would in
 * the exact result. </P><P>
 *
 * When JTS detects topology collapses during the computation of spatial
 * analysis methods, it will throw an exception. If possible the exception will
 * report the location of the collapse. </P><P>
 *
</P> * <h3>Geometry Equality</h3>
 *
 * There are two ways of comparing geometries for equality:
 * **structural equality** and **topological equality**.
 *
 * <h4>Structural Equality</h4>
 *
 * Structural Equality is provided by the
 * [.equalsExact] method.
 * This implements a comparison based on exact, structural pointwise
 * equality.
 * The [.equals] is a synonym for this method,
 * to provide structural equality semantics for
 * use in Java collections.
 * It is important to note that structural pointwise equality
 * is easily affected by things like
 * ring order and component order.  In many situations
 * it will be desirable to normalize geometries before
 * comparing them (using the [.norm]
 * or [.normalize] methods).
 * [.equalsNorm] is provided
 * as a convenience method to compute equality over
 * normalized geometries, but it is expensive to use.
 * Finally, [.equalsExact]
 * allows using a tolerance value for point comparison.
 *
 * <h4>Topological Equality</h4>
 *
 * Topological Equality is provided by the
 * [.equalsTopo] method.
 * It implements the SFS definition of point-set equality
 * defined in terms of the DE-9IM matrix.
 * To support the SFS naming convention, the method
 * [.equals] is also provided as a synonym.
 * However, due to the potential for confusion with [.equals]
 * its use is discouraged.
 *
 * Since [.equals] and [.hashCode] are overridden,
 * Geometries can be used effectively in Java collections.
 *
 * @version 1.7
 */
abstract class Geometry(
    /**
     * The [GeometryFactory] used to create this Geometry
     */
    val factory: GeometryFactory
) : Cloneable, Comparable<Any?>, Serializable {
    /**
     * The bounding box of this `Geometry`.
     */
    private var envelope: Envelope? = null
    /**
     * Gets the factory which contains the context in which this geometry was created.
     *
     * @return the factory for this geometry
     */

    /**
     * The ID of the Spatial Reference System used by this `Geometry`
     */
    var SRID: Int

    /**
     * An object reference which can be used to carry ancillary data defined
     * by the client.
     */
    private var userData: Any? = null

    /**
     * Creates a new `Geometry` via the specified GeometryFactory.
     *
     * @param factory
     */
    init {
        SRID = factory.sRID
    }

    /**
     * Returns the name of this Geometry's actual class.
     *
     * @return the name of this `Geometry`s actual class
     */
    abstract val geometryType: String?

    /**
     * Gets the user data object for this geometry, if any.
     *
     * @return the user data object, or `null` if none set
     */
    fun getUserData(): Any? {
        return userData
    }

    /**
     * Returns the number of [Geometry]s in a [GeometryCollection]
     * (or 1, if the geometry is not a collection).
     *
     * @return the number of geometries contained in this geometry
     */
    open val numGeometries: Int
        get() = 1

    /**
     * Returns an element [Geometry] from a [GeometryCollection]
     * (or `this`, if the geometry is not a collection).
     *
     * @param n the index of the geometry element
     * @return the n'th geometry contained in this geometry
     */
    open fun getGeometryN(n: Int): Geometry {
        return this
    }

    /**
     * A simple scheme for applications to add their own custom data to a Geometry.
     * An example use might be to add an object representing a Coordinate Reference System.
     *
     *
     * Note that user data objects are not present in geometries created by
     * construction methods.
     *
     * @param userData an object, the semantics for which are defined by the
     * application using this Geometry
     */
    fun setUserData(userData: Any?) {
        this.userData = userData
    }

    /**
     * Returns the `PrecisionModel` used by the `Geometry`.
     *
     * @return    the specification of the grid of allowable points, for this
     * `Geometry` and all other `Geometry`s
     */
    val precisionModel: PrecisionModel
        get() = factory.precisionModel

    /**
     * Returns a vertex of this `Geometry`
     * (usually, but not necessarily, the first one).
     * The returned coordinate should not be assumed
     * to be an actual Coordinate object used in
     * the internal representation.
     *
     * @return    a [Coordinate] which is a vertex of this `Geometry`.
     * @return null if this Geometry is empty
     */
    abstract val coordinate: Coordinate?

    /**
     * Returns an array containing the values of all the vertices for
     * this geometry.
     * If the geometry is a composite, the array will contain all the vertices
     * for the components, in the order in which the components occur in the geometry.
     *
     * In general, the array cannot be assumed to be the actual internal
     * storage for the vertices.  Thus modifying the array
     * may not modify the geometry itself.
     * Use the [CoordinateSequence.setOrdinate] method
     * (possibly on the components) to modify the underlying data.
     * If the coordinates are modified,
     * [.geometryChanged] must be called afterwards.
     *
     * @return    the vertices of this `Geometry`
     * @see .geometryChanged
     * @see CoordinateSequence.setOrdinate
     */
    abstract val coordinates: Array<Coordinate>?

    /**
     * Returns the count of this `Geometry`s vertices. The `Geometry`
     * s contained by composite `Geometry`s must be
     * Geometry's; that is, they must implement `getNumPoints`
     *
     * @return    the number of vertices in this `Geometry`
     */
    abstract val numPoints: Int

    /**
     * Tests whether this [Geometry] is simple.
     * The SFS definition of simplicity
     * follows the general rule that a Geometry is simple if it has no points of
     * self-tangency, self-intersection or other anomalous points.
     *
     *
     * Simplicity is defined for each [Geometry] subclass as follows:
     *
     *  * Valid polygonal geometries are simple, since their rings
     * must not self-intersect.  `isSimple`
     * tests for this condition and reports `false` if it is not met.
     * (This is a looser test than checking for validity).
     *  * Linear rings have the same semantics.
     *  * Linear geometries are simple if they do not self-intersect at points
     * other than boundary points.
     *  * Zero-dimensional geometries (points) are simple if they have no
     * repeated points.
     *  * Empty `Geometry`s are always simple.
     *
     *
     * @return `true` if this `Geometry` is simple
     * @see .isValid
     */
    open val isSimple: Boolean
        get() {
            val op = IsSimpleOp(this)
            return op.isSimple()
        }

    /**
     * Tests whether this `Geometry`
     * is topologically valid, according to the OGC SFS specification.
     *
     *
     * For validity rules see the Javadoc for the specific Geometry subclass.
     *
     * @return `true` if this `Geometry` is valid
     *
     * @see IsValidOp
     */
    val isValid: Boolean
        get() = IsValidOp.isValid(this)

    /**
     * Tests whether the set of points covered by this `Geometry` is
     * empty.
     *
     *
     * Note this test is for topological emptiness,
     * not structural emptiness.
     * A collection containing only empty elements is reported as empty.
     * To check structural emptiness use [.getNumGeometries].
     *
     * @return `true` if this `Geometry` does not cover any points
     */
    abstract val isEmpty: Boolean

    /**
     * Returns the minimum distance between this `Geometry`
     * and another `Geometry`.
     *
     * @param  g the `Geometry` from which to compute the distance
     * @return the distance between the geometries
     * @return 0 if either input geometry is empty
     * @throws IllegalArgumentException if g is null
     */
    open fun distance(g: Geometry): Double {
        return DistanceOp.distance(this, g)
    }

    /**
     * Tests whether the distance from this `Geometry`
     * to another is less than or equal to a specified value.
     *
     * @param geom the Geometry to check the distance to
     * @param distance the distance value to compare
     * @return `true` if the geometries are less than `distance` apart.
     */
    open fun isWithinDistance(geom: Geometry, distance: Double): Boolean {
        return DistanceOp.isWithinDistance(this, geom, distance)
    }// Polygon overrides to check for actual rectangle

    /**
     * Tests whether this is a rectangular [Polygon].
     *
     * @return true if the geometry is a rectangle.
     */
    open val isRectangle: Boolean
        get() =// Polygon overrides to check for actual rectangle
            false

    /**
     * Returns the area of this `Geometry`.
     * Areal Geometries have a non-zero area.
     * They override this function to compute the area.
     * Others return 0.0
     *
     * @return the area of the Geometry
     */
    open val area: Double
        get() = 0.0

    /**
     * Returns the length of this `Geometry`.
     * Linear geometries return their length.
     * Areal geometries return their perimeter.
     * They override this function to compute the area.
     * Others return 0.0
     *
     * @return the length of the Geometry
     */
    open val length: Double
        get() = 0.0

    /**
     * Computes the centroid of this `Geometry`.
     * The centroid
     * is equal to the centroid of the set of component Geometries of highest
     * dimension (since the lower-dimension geometries contribute zero
     * "weight" to the centroid).
     *
     *
     * The centroid of an empty geometry is `POINT EMPTY`.
     *
     * @return a [Point] which is the centroid of this Geometry
     */
    open val centroid: Point
        get() {
            if (isEmpty) return factory.createPoint()
            val centPt = Centroid.getCentroid(this)
            return createPointFromInternalCoord(centPt, this)
        }

    /**
     * Computes an interior point of this `Geometry`.
     * An interior point is guaranteed to lie in the interior of the Geometry,
     * if it possible to calculate such a point exactly. Otherwise,
     * the point may lie on the boundary of the geometry.
     *
     *
     * The interior point of an empty geometry is `POINT EMPTY`.
     *
     * @return a [Point] which is in the interior of this Geometry
     */
    open val interiorPoint: Point
        get() {
            if (isEmpty) return factory.createPoint()
            val pt: Coordinate? = InteriorPoint.getInteriorPoint(this)
            return createPointFromInternalCoord(pt, this)
        }

    /**
     * Returns the dimension of this geometry.
     * The dimension of a geometry is is the topological
     * dimension of its embedding in the 2-D Euclidean plane.
     * In the JTS spatial model, dimension values are in the set {0,1,2}.
     *
     *
     * Note that this is a different concept to the dimension of
     * the vertex [Coordinate]s.
     * The geometry dimension can never be greater than the coordinate dimension.
     * For example, a 0-dimensional geometry (e.g. a Point)
     * may have a coordinate dimension of 3 (X,Y,Z).
     *
     * @return the topological dimension of this geometry.
     */
    abstract val dimension: Int

    /**
     * Returns the boundary, or an empty geometry of appropriate dimension
     * if this `Geometry`  is empty.
     * (In the case of zero-dimensional geometries, '
     * an empty GeometryCollection is returned.)
     * For a discussion of this function, see the OpenGIS Simple
     * Features Specification. As stated in SFS Section 2.1.13.1, "the boundary
     * of a Geometry is a set of Geometries of the next lower dimension."
     *
     * @return    the closure of the combinatorial boundary of this `Geometry`
     */
    abstract val boundary: Geometry?

    /**
     * Returns the dimension of this `Geometry`s inherent boundary.
     *
     * @return    the dimension of the boundary of the class implementing this
     * interface, whether or not this object is the empty geometry. Returns
     * `Dimension.FALSE` if the boundary is the empty geometry.
     */
    abstract val boundaryDimension: Int

    /**
     * Gets a Geometry representing the envelope (bounding box) of
     * this `Geometry`.
     *
     *
     * If this `Geometry` is:
     *
     *  * empty, returns an empty `Point`.
     *  * a point, returns a `Point`.
     *  * a line parallel to an axis, a two-vertex `LineString`
     *  * otherwise, returns a
     * `Polygon` whose vertices are (minx miny, minx maxy,
     * maxx maxy, maxx miny, minx miny).
     *
     *
     * @return a Geometry representing the envelope of this Geometry
     *
     * @see GeometryFactory.toGeometry
     */
    open fun getEnvelope(): Geometry {
        return factory.toGeometry(envelopeInternal)
    }

    /**
     * Gets an [Envelope] containing
     * the minimum and maximum x and y values in this `Geometry`.
     * If the geometry is empty, an empty `Envelope`
     * is returned.
     *
     * The returned object is a copy of the one maintained internally,
     * to avoid aliasing issues.
     * For best performance, clients which access this
     * envelope frequently should cache the return value.
     *
     * @return the envelope of this `Geometry`.
     * @return an empty Envelope if this Geometry is empty
     */
    open val envelopeInternal: Envelope
        get() {
            if (envelope == null) {
                envelope = computeEnvelopeInternal()
            }
            return Envelope(envelope!!)
        }

    /**
     * Notifies this geometry that its coordinates have been changed by an external
     * party (for example, via a [CoordinateFilter]).
     * When this method is called the geometry will flush
     * and/or update any derived information it has cached (such as its [Envelope] ).
     * The operation is applied to all component Geometries.
     */
    fun geometryChanged() {
        apply(geometryChangedFilter)
    }

    /**
     * Notifies this Geometry that its Coordinates have been changed by an external
     * party. When #geometryChanged is called, this method will be called for
     * this Geometry and its component Geometries.
     *
     * @see .apply
     */
    protected fun geometryChangedAction() {
        envelope = null
    }

    /**
     * Tests whether this geometry is disjoint from the argument geometry.
     *
     *
     * The `disjoint` predicate has the following equivalent definitions:
     *
     *  * The two geometries have no point in common
     *  * The DE-9IM Intersection Matrix for the two geometries matches
     * `[FF*FF****]`
     *  * `! g.intersects(this) = true`
     * <br></br>(`disjoint` is the inverse of `intersects`)
     *
     *
     * @param  g  the `Geometry` with which to compare this `Geometry`
     * @return        `true` if the two `Geometry`s are
     * disjoint
     *
     * @see Geometry.intersects
     */
    fun disjoint(g: Geometry): Boolean {
        return !intersects(g)
    }

    /**
     * Tests whether this geometry touches the
     * argument geometry.
     *
     *
     * The `touches` predicate has the following equivalent definitions:
     *
     *  * The geometries have at least one point in common,
     * but their interiors do not intersect.
     *  * The DE-9IM Intersection Matrix for the two geometries matches
     * at least one of the following patterns
     *
     *  * `[FT*******]`
     *  * `[F**T*****]`
     *  * `[F***T****]`
     *
     *
     * If both geometries have dimension 0, the predicate returns `false`,
     * since points have only interiors.
     * This predicate is symmetric.
     *
     *
     * @param  g  the `Geometry` with which to compare this `Geometry`
     * @return        `true` if the two `Geometry`s touch;
     * Returns `false` if both `Geometry`s are points
     */
    fun touches(g: Geometry): Boolean {
        // short-circuit test
        return if (!envelopeInternal.intersects(g.envelopeInternal)) false else relate(g).isTouches(
            dimension,
            g.dimension
        )
    }

    /**
     * Tests whether this geometry intersects the argument geometry.
     *
     *
     * The `intersects` predicate has the following equivalent definitions:
     *
     *  * The two geometries have at least one point in common
     *  * The DE-9IM Intersection Matrix for the two geometries matches
     * at least one of the patterns
     *
     *  * `[T********]`
     *  * `[*T*******]`
     *  * `[***T*****]`
     *  * `[****T****]`
     *
     *  * `! g.disjoint(this) = true`
     * <br></br>(`intersects` is the inverse of `disjoint`)
     *
     *
     * @param  g  the `Geometry` with which to compare this `Geometry`
     * @return        `true` if the two `Geometry`s intersect
     *
     * @see Geometry.disjoint
     */
    fun intersects(g: Geometry): Boolean {

        // short-circuit envelope test
        if (!envelopeInternal.intersects(g.envelopeInternal)) return false
        /**
         * TODO: (MD) Add optimizations:
         *
         * - for P-A case:
         * If P is in env(A), test for point-in-poly
         *
         * - for A-A case:
         * If env(A1).overlaps(env(A2))
         * test for overlaps via point-in-poly first (both ways)
         * Possibly optimize selection of point to test by finding point of A1
         * closest to centre of env(A2).
         * (Is there a test where we shouldn't bother - e.g. if env A
         * is much smaller than env B, maybe there's no point in testing
         * pt(B) in env(A)?
         */

        // optimization for rectangle arguments
        if (isRectangle) {
            return RectangleIntersects.intersects(this as Polygon, g)
        }
        if (g.isRectangle) {
            return RectangleIntersects.intersects(g as Polygon, this)
        }
        if (isGeometryCollection || g.isGeometryCollection) {
            for (i in 0 until numGeometries) {
                for (j in 0 until g.numGeometries) {
                    if (getGeometryN(i).intersects(g.getGeometryN(j))) {
                        return true
                    }
                }
            }
            return false
        }
        // general case
        return relate(g).isIntersects
    }

    /**
     * Tests whether this geometry crosses the
     * argument geometry.
     *
     *
     * The `crosses` predicate has the following equivalent definitions:
     *
     *  * The geometries have some but not all interior points in common.
     *  * The DE-9IM Intersection Matrix for the two geometries matches
     * one of the following patterns:
     *
     *  * `[T*T******]` (for P/L, P/A, and L/A situations)
     *  * `[T*****T**]` (for L/P, A/P, and A/L situations)
     *  * `[0********]` (for L/L situations)
     *
     *
     * For the A/A and P/P situations this predicate returns `false`.
     *
     *
     * The SFS defined this predicate only for P/L, P/A, L/L, and L/A situations.
     * To make the relation symmetric
     * JTS extends the definition to apply to L/P, A/P and A/L situations as well.
     *
     * @param  g  the `Geometry` with which to compare this `Geometry`
     * @return        `true` if the two `Geometry`s cross.
     */
    fun crosses(g: Geometry): Boolean {
        // short-circuit test
        return if (!envelopeInternal.intersects(g.envelopeInternal)) false else relate(g).isCrosses(
            dimension,
            g.dimension
        )
    }

    /**
     * Tests whether this geometry is within the
     * specified geometry.
     *
     *
     * The `within` predicate has the following equivalent definitions:
     *
     *  * Every point of this geometry is a point of the other geometry,
     * and the interiors of the two geometries have at least one point in common.
     *  * The DE-9IM Intersection Matrix for the two geometries matches
     * `[T*F**F***]`
     *  * `g.contains(this) = true`
     * <br></br>(`within` is the converse of [.contains])
     *
     * An implication of the definition is that
     * "The boundary of a Geometry is not within the Geometry".
     * In other words, if a geometry A is a subset of
     * the points in the boundary of a geometry B, `A.within(B) = false`
     * (As a concrete example, take A to be a LineString which lies in the boundary of a Polygon B.)
     * For a predicate with similar behaviour but avoiding
     * this subtle limitation, see [.coveredBy].
     *
     * @param  g  the `Geometry` with which to compare this `Geometry`
     * @return        `true` if this `Geometry` is within
     * `g`
     *
     * @see Geometry.contains
     *
     * @see Geometry.coveredBy
     */
    fun within(g: Geometry): Boolean {
        return g.contains(this)
    }

    /**
     * Tests whether this geometry contains the
     * argument geometry.
     *
     *
     * The `contains` predicate has the following equivalent definitions:
     *
     *  * Every point of the other geometry is a point of this geometry,
     * and the interiors of the two geometries have at least one point in common.
     *  * The DE-9IM Intersection Matrix for the two geometries matches
     * the pattern
     * `[T*****FF*]`
     *  * `g.within(this) = true`
     * <br></br>(`contains` is the converse of [.within] )
     *
     * An implication of the definition is that "Geometries do not
     * contain their boundary".  In other words, if a geometry A is a subset of
     * the points in the boundary of a geometry B, `B.contains(A) = false`.
     * (As a concrete example, take A to be a LineString which lies in the boundary of a Polygon B.)
     * For a predicate with similar behaviour but avoiding
     * this subtle limitation, see [.covers].
     *
     * @param  g  the `Geometry` with which to compare this `Geometry`
     * @return        `true` if this `Geometry` contains `g`
     *
     * @see Geometry.within
     *
     * @see Geometry.covers
     */
    operator fun contains(g: Geometry): Boolean {
        // optimization - lower dimension cannot contain areas
        if (g.dimension == 2 && dimension < 2) {
            return false
        }
        // optimization - P cannot contain a non-zero-length L
        // Note that a point can contain a zero-length lineal geometry,
        // since the line has no boundary due to Mod-2 Boundary Rule
        if (g.dimension == 1 && dimension < 1 && g.length > 0.0) {
            return false
        }
        // optimization - envelope test
        if (!envelopeInternal.contains(g.envelopeInternal)) return false
        // optimization for rectangle arguments
        return if (isRectangle) {
            RectangleContains.contains(this as Polygon, g)
        } else relate(g).isContains
        // general case
    }

    /**
     * Tests whether this geometry overlaps the
     * specified geometry.
     *
     *
     * The `overlaps` predicate has the following equivalent definitions:
     *
     *  * The geometries have at least one point each not shared by the other
     * (or equivalently neither covers the other),
     * they have the same dimension,
     * and the intersection of the interiors of the two geometries has
     * the same dimension as the geometries themselves.
     *  * The DE-9IM Intersection Matrix for the two geometries matches
     * `[T*T***T**]` (for two points or two surfaces)
     * or `[1*T***T**]` (for two curves)
     *
     * If the geometries are of different dimension this predicate returns `false`.
     * This predicate is symmetric.
     *
     * @param  g  the `Geometry` with which to compare this `Geometry`
     * @return        `true` if the two `Geometry`s overlap.
     */
    fun overlaps(g: Geometry): Boolean {
        // short-circuit test
        return if (!envelopeInternal.intersects(g.envelopeInternal)) false else relate(g).isOverlaps(
            dimension,
            g.dimension
        )
    }

    /**
     * Tests whether this geometry covers the
     * argument geometry.
     *
     *
     * The `covers` predicate has the following equivalent definitions:
     *
     *  * Every point of the other geometry is a point of this geometry.
     *  * The DE-9IM Intersection Matrix for the two geometries matches
     * at least one of the following patterns:
     *
     *  * `[T*****FF*]`
     *  * `[*T****FF*]`
     *  * `[***T**FF*]`
     *  * `[****T*FF*]`
     *
     *  * `g.coveredBy(this) = true`
     * <br></br>(`covers` is the converse of [.coveredBy])
     *
     * If either geometry is empty, the value of this predicate is `false`.
     *
     *
     * This predicate is similar to [.contains],
     * but is more inclusive (i.e. returns `true` for more cases).
     * In particular, unlike `contains` it does not distinguish between
     * points in the boundary and in the interior of geometries.
     * For most situations, `covers` should be used in preference to `contains`.
     * As an added benefit, `covers` is more amenable to optimization,
     * and hence should be more performant.
     *
     * @param  g  the `Geometry` with which to compare this `Geometry`
     * @return        `true` if this `Geometry` covers `g`
     *
     * @see Geometry.contains
     *
     * @see Geometry.coveredBy
     */
    fun covers(g: Geometry): Boolean {
        // optimization - lower dimension cannot cover areas
        if (g.dimension == 2 && dimension < 2) {
            return false
        }
        // optimization - P cannot cover a non-zero-length L
        // Note that a point can cover a zero-length lineal geometry
        if (g.dimension == 1 && dimension < 1 && g.length > 0.0) {
            return false
        }
        // optimization - envelope test
        if (!envelopeInternal.covers(g.envelopeInternal)) return false
        // optimization for rectangle arguments
        return if (isRectangle) {
            // since we have already tested that the test envelope is covered
            true
        } else relate(g).isCovers
    }

    /**
     * Tests whether this geometry is covered by the
     * argument geometry.
     *
     *
     * The `coveredBy` predicate has the following equivalent definitions:
     *
     *  * Every point of this geometry is a point of the other geometry.
     *  * The DE-9IM Intersection Matrix for the two geometries matches
     * at least one of the following patterns:
     *
     *  * `[T*F**F***]`
     *  * `[*TF**F***]`
     *  * `[**FT*F***]`
     *  * `[**F*TF***]`
     *
     *  * `g.covers(this) = true`
     * <br></br>(`coveredBy` is the converse of [.covers])
     *
     * If either geometry is empty, the value of this predicate is `false`.
     *
     *
     * This predicate is similar to [.within],
     * but is more inclusive (i.e. returns `true` for more cases).
     *
     * @param  g  the `Geometry` with which to compare this `Geometry`
     * @return        `true` if this `Geometry` is covered by `g`
     *
     * @see Geometry.within
     *
     * @see Geometry.covers
     */
    fun coveredBy(g: Geometry): Boolean {
        return g.covers(this)
    }

    /**
     * Tests whether the elements in the DE-9IM
     * [IntersectionMatrix] for the two `Geometry`s match the elements in `intersectionPattern`.
     * The pattern is a 9-character string, with symbols drawn from the following set:
     * <UL>
     * <LI> 0 (dimension 0)
    </LI> * <LI> 1 (dimension 1)
    </LI> * <LI> 2 (dimension 2)
    </LI> * <LI> T ( matches 0, 1 or 2)
    </LI> * <LI> F ( matches FALSE)
    </LI> * <LI> * ( matches any value)
    </LI></UL> *
     * For more information on the DE-9IM, see the *OpenGIS Simple Features
     * Specification*.
     *
     * @param  g                the `Geometry` with which to compare
     * this `Geometry`
     * @param  intersectionPattern  the pattern against which to check the
     * intersection matrix for the two `Geometry`s
     * @return                      `true` if the DE-9IM intersection
     * matrix for the two `Geometry`s match `intersectionPattern`
     * @see IntersectionMatrix
     */
    fun relate(g: Geometry, intersectionPattern: String): Boolean {
        return relate(g).matches(intersectionPattern)
    }

    /**
     * Returns the DE-9IM [IntersectionMatrix] for the two `Geometry`s.
     *
     * @param  g  the `Geometry` with which to compare this `Geometry`
     * @return        an [IntersectionMatrix] describing the intersections of the interiors,
     * boundaries and exteriors of the two `Geometry`s
     */
    fun relate(g: Geometry): IntersectionMatrix {
        checkNotGeometryCollection(this)
        checkNotGeometryCollection(g)
        return RelateOp.relate(this, g)
    }

    /**
     * Tests whether this geometry is
     * topologically equal to the argument geometry.
     *
     *
     * This method is included for backward compatibility reasons.
     * It has been superseded by the [.equalsTopo] method,
     * which has been named to clearly denote its functionality.
     *
     *
     * This method should NOT be confused with the method
     * [.equals], which implements
     * an exact equality comparison.
     *
     * @param  g  the `Geometry` with which to compare this `Geometry`
     * @return true if the two `Geometry`s are topologically equal
     * @see .equalsTopo
     */
    fun equals(g: Geometry?): Boolean {
        return g?.let { equalsTopo(it) } ?: false
    }

    /**
     * Tests whether this geometry is topologically equal to the argument geometry
     * as defined by the SFS `equals` predicate.
     *
     *
     * The SFS `equals` predicate has the following equivalent definitions:
     *
     *  * The two geometries have at least one point in common,
     * and no point of either geometry lies in the exterior of the other geometry.
     *  * The DE-9IM Intersection Matrix for the two geometries matches
     * the pattern `T*F**FFF*`
     * <pre>
     * T*F
     * **F
     * FF*
    </pre> *
     *
     * **Note** that this method computes **topologically equality**.
     * For structural equality, see [.equalsExact].
     *
     * @param g the `Geometry` with which to compare this `Geometry`
     * @return `true` if the two `Geometry`s are topologically equal
     * @see .equalsExact
     */
    fun equalsTopo(g: Geometry): Boolean {
        // short-circuit test
        return if (!envelopeInternal.equals(g.envelopeInternal)) false else relate(g).isEquals(
            dimension,
            g.dimension
        )
    }

    /**
     * Tests whether this geometry is structurally and numerically equal
     * to a given `Object`.
     * If the argument `Object` is not a `Geometry`,
     * the result is `false`.
     * Otherwise, the result is computed using
     * [.equalsExact].
     *
     *
     * This method is provided to fulfill the Java contract
     * for value-based object equality.
     * In conjunction with [.hashCode]
     * it provides semantics which are most useful
     * for using
     * `Geometry`s as keys and values in Java collections.
     *
     *
     * Note that to produce the expected result the input geometries
     * should be in normal form.  It is the caller's
     * responsibility to perform this where required
     * (using [Geometry.norm]
     * or [.normalize] as appropriate).
     *
     * @param o the Object to compare
     * @return true if this geometry is exactly equal to the argument
     *
     * @see .equalsExact
     * @see .hashCode
     * @see .norm
     * @see .normalize
     */
    override fun equals(o: Any?): Boolean {
        if (o !is Geometry) return false
        return equalsExact(o)
    }

    /**
     * Gets a hash code for the Geometry.
     *
     * @return an integer value suitable for use as a hashcode
     */
    override fun hashCode(): Int {
        return envelopeInternal.hashCode()
    }

    override fun toString(): String {
        return toText()
    }

    /**
     * Returns the Well-known Text representation of this `Geometry`.
     * For a definition of the Well-known Text format, see the OpenGIS Simple
     * Features Specification.
     *
     * @return    the Well-known Text representation of this `Geometry`
     */
    fun toText(): String {
        val writer = WKTWriter()
        return writer.write(this)
    }

    /**
     * Computes a buffer area around this geometry having the given width. The
     * buffer of a Geometry is the Minkowski sum or difference of the geometry
     * with a disc of radius `abs(distance)`.
     *
     *
     * Mathematically-exact buffer area boundaries can contain circular arcs.
     * To represent these arcs using linear geometry they must be approximated with line segments.
     * The buffer geometry is constructed using 8 segments per quadrant to approximate
     * the circular arcs.
     * The end cap style is `CAP_ROUND`.
     *
     *
     * The buffer operation always returns a polygonal result. The negative or
     * zero-distance buffer of lines and points is always an empty [Polygon].
     * This is also the result for the buffers of degenerate (zero-area) polygons.
     *
     * @param distance
     * the width of the buffer (may be positive, negative or 0)
     * @return a polygonal geometry representing the buffer region (which may be
     * empty)
     *
     * @throws TopologyException
     * if a robustness error occurs
     *
     * @see .buffer
     * @see .buffer
     */
    fun buffer(distance: Double): Geometry {
        return BufferOp.bufferOp(this, distance)
    }

    /**
     * Computes a buffer area around this geometry having the given width and with
     * a specified accuracy of approximation for circular arcs.
     *
     *
     * Mathematically-exact buffer area boundaries can contain circular arcs.
     * To represent these arcs
     * using linear geometry they must be approximated with line segments. The
     * `quadrantSegments` argument allows controlling the accuracy of
     * the approximation by specifying the number of line segments used to
     * represent a quadrant of a circle
     *
     *
     * The buffer operation always returns a polygonal result. The negative or
     * zero-distance buffer of lines and points is always an empty [Polygon].
     * This is also the result for the buffers of degenerate (zero-area) polygons.
     *
     * @param distance
     * the width of the buffer (may be positive, negative or 0)
     * @param quadrantSegments
     * the number of line segments used to represent a quadrant of a
     * circle
     * @return a polygonal geometry representing the buffer region (which may be
     * empty)
     *
     * @throws TopologyException
     * if a robustness error occurs
     *
     * @see .buffer
     * @see .buffer
     */
    open fun buffer(distance: Double, quadrantSegments: Int): Geometry {
        return BufferOp.bufferOp(this, distance, quadrantSegments)
    }

    /**
     * Computes a buffer area around this geometry having the given
     * width and with a specified accuracy of approximation for circular arcs,
     * and using a specified end cap style.
     *
     *
     * Mathematically-exact buffer area boundaries can contain circular arcs.
     * To represent these arcs using linear geometry they must be approximated with line segments.
     * The `quadrantSegments` argument allows controlling the
     * accuracy of the approximation
     * by specifying the number of line segments used to represent a quadrant of a circle
     *
     *
     * The end cap style specifies the buffer geometry that will be
     * created at the ends of linestrings.  The styles provided are:
     *
     *  * [BufferParameters.CAP_ROUND] - (default) a semi-circle
     *  * [BufferParameters.CAP_FLAT] - a straight line perpendicular to the end segment
     *  * [BufferParameters.CAP_SQUARE] - a half-square
     *
     *
     *
     * The buffer operation always returns a polygonal result. The negative or
     * zero-distance buffer of lines and points is always an empty [Polygon].
     * This is also the result for the buffers of degenerate (zero-area) polygons.
     *
     * @param  distance  the width of the buffer (may be positive, negative or 0)
     * @param quadrantSegments the number of line segments used to represent a quadrant of a circle
     * @param endCapStyle the end cap style to use
     * @return a polygonal geometry representing the buffer region (which may be empty)
     *
     * @throws TopologyException if a robustness error occurs
     *
     * @see .buffer
     * @see .buffer
     * @see BufferOp
     */
    open fun buffer(distance: Double, quadrantSegments: Int, endCapStyle: Int): Geometry {
        return BufferOp.bufferOp(this, distance, quadrantSegments, endCapStyle)
    }

    /**
     * Computes the smallest convex `Polygon` that contains all the
     * points in the `Geometry`. This obviously applies only to `Geometry`
     * s which contain 3 or more points; the results for degenerate cases are
     * specified as follows:
     * <TABLE>
     * <TR>
     * <TH>    Number of `Point`s in argument `Geometry`   </TH>
     * <TH>    `Geometry` class of result     </TH>
    </TR> *
     * <TR>
     * <TD>        0      </TD>
     * <TD>        empty `GeometryCollection`      </TD>
    </TR> *
     * <TR>  <TD>      1     </TD>
     * <TD>     `Point`     </TD>
    </TR> *
     * <TR>
     * <TD>      2     </TD>
     * <TD>     `LineString`     </TD>
    </TR> *
     * <TR>
     * <TD>       3 or more     </TD>
     * <TD>      `Polygon`     </TD>
    </TR> *
    </TABLE> *
     *
     * @return    the minimum-area convex polygon containing this `Geometry`'
     * s points
     */
    open fun convexHull(): Geometry? {
        return ConvexHull(this).convexHull
    }

    /**
     * Computes a new geometry which has all component coordinate sequences
     * in reverse order (opposite orientation) to this one.
     *
     * @return a reversed geometry
     */
    open fun reverse(): Geometry {
        val res = reverseInternal()
        if (envelope != null) res.envelope = envelope!!.copy()
        res.SRID = SRID
        return res
    }

    protected abstract fun reverseInternal(): Geometry

    /**
     * Computes a `Geometry` representing the point-set which is
     * common to both this `Geometry` and the `other` Geometry.
     *
     *
     * The intersection of two geometries of different dimension produces a result
     * geometry of dimension less than or equal to the minimum dimension of the input
     * geometries.
     * The result geometry may be a heterogeneous [GeometryCollection].
     * If the result is empty, it is an atomic geometry
     * with the dimension of the lowest input dimension.
     *
     *
     * Intersection of [GeometryCollection]s is supported
     * only for homogeneous collection types.
     *
     *
     * Non-empty heterogeneous [GeometryCollection] arguments are not supported.
     *
     * @param  other the `Geometry` with which to compute the intersection
     * @return a Geometry representing the point-set common to the two `Geometry`s
     * @throws TopologyException if a robustness error occurs
     * @throws IllegalArgumentException if the argument is a non-empty heterogeneous `GeometryCollection`
     */
    fun intersection(other: Geometry?): Geometry {
        return GeometryOverlay.intersection(this, other!!)
    }

    /**
     * Computes a `Geometry` representing the point-set
     * which is contained in both this
     * `Geometry` and the `other` Geometry.
     *
     *
     * The union of two geometries of different dimension produces a result
     * geometry of dimension equal to the maximum dimension of the input
     * geometries.
     * The result geometry may be a heterogeneous
     * [GeometryCollection].
     * If the result is empty, it is an atomic geometry
     * with the dimension of the highest input dimension.
     *
     *
     * Unioning [LineString]s has the effect of
     * **noding** and **dissolving** the input linework. In this context
     * "noding" means that there will be a node or endpoint in the result for
     * every endpoint or line segment crossing in the input. "Dissolving" means
     * that any duplicate (i.e. coincident) line segments or portions of line
     * segments will be reduced to a single line segment in the result.
     * If **merged** linework is required, the [LineMerger]
     * class can be used.
     *
     *
     * Non-empty [GeometryCollection] arguments are not supported.
     *
     * @param other
     * the `Geometry` with which to compute the union
     * @return a point-set combining the points of this `Geometry` and the
     * points of `other`
     * @throws TopologyException
     * if a robustness error occurs
     * @throws IllegalArgumentException
     * if either input is a non-empty GeometryCollection
     * @see LineMerger
     */
    fun union(other: Geometry?): Geometry {
        return GeometryOverlay.union(this, other!!)
    }

    /**
     * Computes a `Geometry` representing the closure of the point-set
     * of the points contained in this `Geometry` that are not contained in
     * the `other` Geometry.
     *
     *
     * If the result is empty, it is an atomic geometry
     * with the dimension of the left-hand input.
     *
     *
     * Non-empty [GeometryCollection] arguments are not supported.
     *
     * @param  other  the `Geometry` with which to compute the
     * difference
     * @return a Geometry representing the point-set difference of this `Geometry` with
     * `other`
     * @throws TopologyException if a robustness error occurs
     * @throws IllegalArgumentException if either input is a non-empty GeometryCollection
     */
    fun difference(other: Geometry?): Geometry {
        return GeometryOverlay.difference(this, other!!)
    }

    /**
     * Computes a `Geometry` representing the closure of the point-set
     * which is the union of the points in this `Geometry` which are not
     * contained in the `other` Geometry,
     * with the points in the `other` Geometry not contained in this
     * `Geometry`.
     * If the result is empty, it is an atomic geometry
     * with the dimension of the highest input dimension.
     *
     *
     * Non-empty [GeometryCollection] arguments are not supported.
     *
     * @param  other the `Geometry` with which to compute the symmetric
     * difference
     * @return a Geometry representing the point-set symmetric difference of this `Geometry`
     * with `other`
     * @throws TopologyException if a robustness error occurs
     * @throws IllegalArgumentException if either input is a non-empty GeometryCollection
     */
    fun symDifference(other: Geometry?): Geometry {
        return GeometryOverlay.symDifference(this, other!!)
    }

    /**
     * Computes the union of all the elements of this geometry.
     *
     *
     * This method supports
     * [GeometryCollection]s
     * (which the other overlay operations currently do not).
     *
     *
     * The result obeys the following contract:
     *
     *  * Unioning a set of [LineString]s has the effect of fully noding
     * and dissolving the linework.
     *  * Unioning a set of [Polygon]s always
     * returns a [Polygonal] geometry (unlike [.union],
     * which may return geometries of lower dimension if a topology collapse occurred).
     *
     *
     * @return the union geometry
     * @throws TopologyException if a robustness error occurs
     *
     * @see UnaryUnionOp
     */
    fun union(): Geometry? {
        return GeometryOverlay.union(this)
    }

    /**
     * Returns true if the two `Geometry`s are exactly equal,
     * up to a specified distance tolerance.
     * Two Geometries are exactly equal within a distance tolerance
     * if and only if:
     *
     *  * they have the same structure
     *  * they have the same values for their vertices,
     * within the given tolerance distance, in exactly the same order.
     *
     * This method does *not*
     * test the values of the `GeometryFactory`, the `SRID`,
     * or the `userData` fields.
     *
     *
     * To properly test equality between different geometries,
     * it is usually necessary to [.normalize] them first.
     *
     * @param other the `Geometry` with which to compare this `Geometry`
     * @param tolerance distance at or below which two `Coordinate`s
     * are considered equal
     * @return `true` if this and the other `Geometry`
     * have identical structure and point values, up to the distance tolerance.
     *
     * @see .equalsExact
     * @see .normalize
     * @see .norm
     */
    abstract fun equalsExact(other: Geometry?, tolerance: Double): Boolean

    /**
     * Returns true if the two `Geometry`s are exactly equal.
     * Two Geometries are exactly equal iff:
     *
     *  * they have the same structure
     *  * they have the same values for their vertices,
     * in exactly the same order.
     *
     * This provides a stricter test of equality than
     * [.equalsTopo], which is more useful
     * in certain situations
     * (such as using geometries as keys in collections).
     *
     *
     * This method does *not*
     * test the values of the `GeometryFactory`, the `SRID`,
     * or the `userData` fields.
     *
     *
     * To properly test equality between different geometries,
     * it is usually necessary to [.normalize] them first.
     *
     * @param  other  the `Geometry` with which to compare this `Geometry`
     * @return `true` if this and the other `Geometry`
     * have identical structure and point values.
     *
     * @see .equalsExact
     * @see .normalize
     * @see .norm
     */
    fun equalsExact(other: Geometry): Boolean {
        return this === other || equalsExact(other, 0.0)
    }

    /**
     * Tests whether two geometries are exactly equal
     * in their normalized forms.
     * This is a convenience method which creates normalized
     * versions of both geometries before computing
     * [.equalsExact].
     *
     *
     * This method is relatively expensive to compute.
     * For maximum performance, the client
     * should instead perform normalization on the individual geometries
     * at an appropriate point during processing.
     *
     * @param g a Geometry
     * @return true if the input geometries are exactly equal in their normalized form
     */
    fun equalsNorm(g: Geometry?): Boolean {
        return if (g == null) false else norm().equalsExact(g.norm())
    }

    /**
     * Performs an operation with or on this `Geometry`'s
     * coordinates.
     * If this method modifies any coordinate values,
     * [.geometryChanged] must be called to update the geometry state.
     * Note that you cannot use this method to
     * modify this Geometry if its underlying CoordinateSequence's #get method
     * returns a copy of the Coordinate, rather than the actual Coordinate stored
     * (if it even stores Coordinate objects at all).
     *
     * @param  filter  the filter to apply to this `Geometry`'s
     * coordinates
     */
    abstract fun apply(filter: CoordinateFilter)

    /**
     * Performs an operation on the coordinates in this `Geometry`'s
     * [CoordinateSequence]s.
     * If the filter reports that a coordinate value has been changed,
     * [.geometryChanged] will be called automatically.
     *
     * @param  filter  the filter to apply
     */
    abstract fun apply(filter: CoordinateSequenceFilter)

    /**
     * Performs an operation with or on this `Geometry` and its
     * subelement `Geometry`s (if any).
     * Only GeometryCollections and subclasses
     * have subelement Geometry's.
     *
     * @param  filter  the filter to apply to this `Geometry` (and
     * its children, if it is a `GeometryCollection`).
     */
    abstract fun apply(filter: GeometryFilter)

    /**
     * Performs an operation with or on this Geometry and its
     * component Geometry's.  Only GeometryCollections and
     * Polygons have component Geometry's; for Polygons they are the LinearRings
     * of the shell and holes.
     *
     * @param  filter  the filter to apply to this `Geometry`.
     */
    abstract fun apply(filter: GeometryComponentFilter)

    /**
     * Creates and returns a full copy of this [Geometry] object
     * (including all coordinates contained by it).
     * Subclasses are responsible for overriding this method and copying
     * their internal data.  Overrides should call this method first.
     *
     * @return a clone of this instance
     */
    @Deprecated("")
    override fun clone(): Any {
//        return try {
        val clone = factory.createGeometry(this)
        if (clone!!.envelope != null) {
            clone.envelope = Envelope(clone.envelope!!)
        }
        return clone
//        } catch (e: CloneNotSupportedException) {
//            Assert.shouldNeverReachHere()
//            null
//        }
    }

    /**
     * Creates a deep copy of this [Geometry] object.
     * Coordinate sequences contained in it are copied.
     * All instance fields are copied
     * (i.e. `envelope`, <tt>SRID</tt> and <tt>userData</tt>).
     *
     *
     * **NOTE:** the userData object reference (if present) is copied,
     * but the value itself is not copied.
     * If a deep copy is required this must be performed by the caller.
     *
     * @return a deep copy of this geometry
     */
    fun copy(): Geometry {
        val copy = copyInternal()
        copy.envelope = if (envelope == null) null else envelope!!.copy()
        copy.SRID = SRID
        copy.userData = userData
        return copy
    }

    /**
     * An internal method to copy subclass-specific geometry data.
     *
     * @return a copy of the target geometry object.
     */
    protected abstract fun copyInternal(): Geometry

    /**
     * Converts this `Geometry` to **normal form** (or **
     * canonical form** ). Normal form is a unique representation for `Geometry`
     * s. It can be used to test whether two `Geometry`s are equal
     * in a way that is independent of the ordering of the coordinates within
     * them. Normal form equality is a stronger condition than topological
     * equality, but weaker than pointwise equality. The definitions for normal
     * form use the standard lexicographical ordering for coordinates. "Sorted in
     * order of coordinates" means the obvious extension of this ordering to
     * sequences of coordinates.
     *
     *
     * NOTE that this method mutates the value of this geometry in-place.
     * If this is not safe and/or wanted, the geometry should be
     * cloned prior to normalization.
     */
    abstract fun normalize()

    /**
     * Creates a new Geometry which is a normalized
     * copy of this Geometry.
     *
     * @return a normalized copy of this geometry.
     * @see .normalize
     */
    fun norm(): Geometry {
        val copy = copy()
        copy.normalize()
        return copy
    }

    /**
     * Returns whether this `Geometry` is greater than, equal to,
     * or less than another `Geometry`. <P>
     *
     * If their classes are different, they are compared using the following
     * ordering:
    </P> * <UL>
     * <LI> Point (lowest)
    </LI> * <LI> MultiPoint
    </LI> * <LI> LineString
    </LI> * <LI> LinearRing
    </LI> * <LI> MultiLineString
    </LI> * <LI> Polygon
    </LI> * <LI> MultiPolygon
    </LI> * <LI> GeometryCollection (highest)
    </LI></UL> *
     * If the two `Geometry`s have the same class, their first
     * elements are compared. If those are the same, the second elements are
     * compared, etc.
     *
     * @param  o  a `Geometry` with which to compare this `Geometry`
     * @return    a positive number, 0, or a negative number, depending on whether
     * this object is greater than, equal to, or less than `o`, as
     * defined in "Normal Form For Geometry" in the JTS Technical
     * Specifications
     */
    open override fun compareTo(o: Any?): Int {
        val other = o as Geometry?
        if (typeCode != other!!.typeCode) {
            return typeCode - other.typeCode
        }
        if (isEmpty && other.isEmpty) {
            return 0
        }
        if (isEmpty) {
            return -1
        }
        return if (other.isEmpty) {
            1
        } else compareToSameClass(o)
    }

    /**
     * Returns whether this `Geometry` is greater than, equal to,
     * or less than another `Geometry`,
     * using the given [CoordinateSequenceComparator].
     * <P>
     *
     * If their classes are different, they are compared using the following
     * ordering:
    </P> * <UL>
     * <LI> Point (lowest)
    </LI> * <LI> MultiPoint
    </LI> * <LI> LineString
    </LI> * <LI> LinearRing
    </LI> * <LI> MultiLineString
    </LI> * <LI> Polygon
    </LI> * <LI> MultiPolygon
    </LI> * <LI> GeometryCollection (highest)
    </LI></UL> *
     * If the two `Geometry`s have the same class, their first
     * elements are compared. If those are the same, the second elements are
     * compared, etc.
     *
     * @param  o  a `Geometry` with which to compare this `Geometry`
     * @param comp a `CoordinateSequenceComparator`
     * @return    a positive number, 0, or a negative number, depending on whether
     * this object is greater than, equal to, or less than `o`, as
     * defined in "Normal Form For Geometry" in the JTS Technical
     * Specifications
     */
    open fun compareTo(o: Any, comp: CoordinateSequenceComparator): Int {
        val other = o as Geometry
        if (typeCode != other.typeCode) {
            return typeCode - other.typeCode
        }
        if (isEmpty && other.isEmpty) {
            return 0
        }
        if (isEmpty) {
            return -1
        }
        return if (other.isEmpty) {
            1
        } else compareToSameClass(o, comp)
    }

    /**
     * Returns whether the two `Geometry`s are equal, from the point
     * of view of the `equalsExact` method. Called by `equalsExact`
     * . In general, two `Geometry` classes are considered to be
     * "equivalent" only if they are the same class. An exception is `LineString`
     * , which is considered to be equivalent to its subclasses.
     *
     * @param  other  the `Geometry` with which to compare this `Geometry`
     * for equality
     * @return        `true` if the classes of the two `Geometry`
     * s are considered to be equal by the `equalsExact` method.
     */
    protected open fun isEquivalentClass(other: Geometry): Boolean {
        return this::class.simpleName == other::class.simpleName
    }

    /**
     * Tests whether this is an instance of a general [GeometryCollection],
     * rather than a homogeneous subclass.
     *
     * @return true if this is a heterogeneous GeometryCollection
     */
    val isGeometryCollection: Boolean
        get() = typeCode == TYPECODE_GEOMETRYCOLLECTION

    /**
     * Returns the minimum and maximum x and y values in this `Geometry`
     * , or a null `Envelope` if this `Geometry` is empty.
     * Unlike `getEnvelopeInternal`, this method calculates the `Envelope`
     * each time it is called; `getEnvelopeInternal` caches the result
     * of this method.
     *
     * @return    this `Geometry`s bounding box; if the `Geometry`
     * is empty, `Envelope#isNull` will return `true`
     */
    protected abstract fun computeEnvelopeInternal(): Envelope?

    /**
     * Returns whether this `Geometry` is greater than, equal to,
     * or less than another `Geometry` having the same class.
     *
     * @param  o  a `Geometry` having the same class as this `Geometry`
     * @return    a positive number, 0, or a negative number, depending on whether
     * this object is greater than, equal to, or less than `o`, as
     * defined in "Normal Form For Geometry" in the JTS Technical
     * Specifications
     */
    protected abstract fun compareToSameClass(o: Any?): Int

    /**
     * Returns whether this `Geometry` is greater than, equal to,
     * or less than another `Geometry` of the same class.
     * using the given [CoordinateSequenceComparator].
     *
     * @param  o  a `Geometry` having the same class as this `Geometry`
     * @param comp a `CoordinateSequenceComparator`
     * @return    a positive number, 0, or a negative number, depending on whether
     * this object is greater than, equal to, or less than `o`, as
     * defined in "Normal Form For Geometry" in the JTS Technical
     * Specifications
     */
    abstract fun compareToSameClass(o: Any?, comp: CoordinateSequenceComparator): Int

    /**
     * Returns the first non-zero result of `compareTo` encountered as
     * the two `Collection`s are iterated over. If, by the time one of
     * the iterations is complete, no non-zero result has been encountered,
     * returns 0 if the other iteration is also complete. If `b`
     * completes before `a`, a positive number is returned; if a
     * before b, a negative number.
     *
     * @param  a  a `Collection` of `Comparable`s
     * @param  b  a `Collection` of `Comparable`s
     * @return    the first non-zero `compareTo` result, if any;
     * otherwise, zero
     */
    protected fun compare(a: Collection<*>, b: Collection<*>): Int {
        val i = a.iterator()
        val j = b.iterator()
        while (i.hasNext() && j.hasNext()) {
            val aElement = i.next() as Comparable<Any?>
            val bElement = j.next() as Comparable<Any?>
            val comparison = aElement.compareTo(bElement)
            if (comparison != 0) {
                return comparison
            }
        }
        if (i.hasNext()) {
            return 1
        }
        return if (j.hasNext()) {
            -1
        } else 0
    }

    protected fun equal(a: Coordinate, b: Coordinate?, tolerance: Double): Boolean {
        return if (tolerance == 0.0) {
            a.equals(b)
        } else a.distance(b!!) <= tolerance
    }

    protected abstract val typeCode: Int
    private fun createPointFromInternalCoord(coord: Coordinate?, exemplar: Geometry): Point {
        // create empty point for null input
        if (coord == null) return exemplar.factory.createPoint()
        exemplar.precisionModel.makePrecise(coord)
        return exemplar.factory.createPoint(coord)
    }

    companion object {
        private const val serialVersionUID = 8763622679187376702L
        const val TYPECODE_POINT = 0
        const val TYPECODE_MULTIPOINT = 1
        const val TYPECODE_LINESTRING = 2
        const val TYPECODE_LINEARRING = 3
        const val TYPECODE_MULTILINESTRING = 4
        const val TYPECODE_POLYGON = 5
        const val TYPECODE_MULTIPOLYGON = 6
        const val TYPECODE_GEOMETRYCOLLECTION = 7
        const val TYPENAME_POINT = "Point"
        const val TYPENAME_MULTIPOINT = "MultiPoint"
        const val TYPENAME_LINESTRING = "LineString"
        const val TYPENAME_LINEARRING = "LinearRing"
        const val TYPENAME_MULTILINESTRING = "MultiLineString"
        const val TYPENAME_POLYGON = "Polygon"
        const val TYPENAME_MULTIPOLYGON = "MultiPolygon"
        const val TYPENAME_GEOMETRYCOLLECTION = "GeometryCollection"

        private val geometryChangedFilter = GeometryComponentFilter1()

        class GeometryComponentFilter1 : GeometryComponentFilter {
            override fun filter(geom: Geometry) {
                geom.geometryChangedAction()
            }
        }

        /**
         * Returns true if the array contains any non-empty `Geometry`s.
         *
         * @param  geometries  an array of `Geometry`s; no elements may be
         * `null`
         * @return             `true` if any of the `Geometry`s
         * `isEmpty` methods return `false`
         */
        @JvmStatic
        protected fun hasNonEmptyElements(geometries: Array<Geometry>): Boolean {
            for (i in geometries.indices) {
                if (!geometries[i].isEmpty) {
                    return true
                }
            }
            return false
        }

        /**
         * Returns true if the array contains any `null` elements.
         *
         * @param  array  an array to validate
         * @return        `true` if any of `array`s elements are
         * `null`
         */
        @JvmStatic
        protected fun hasNullElements(array: Array<Geometry?>): Boolean {
            for (i in array.indices) {
                if (array[i] == null) {
                    return true
                }
            }
            return false
        }

        /**
         * Throws an exception if `g`'s type is a `GeometryCollection`.
         * (Its subclasses do not trigger an exception).
         *
         * @param  g the `Geometry` to check
         * @throws  IllegalArgumentException  if `g` is a `GeometryCollection`
         * but not one of its subclasses
         */
        fun checkNotGeometryCollection(g: Geometry) {
            if (g.isGeometryCollection) {
                throw IllegalArgumentException("Operation does not support GeometryCollection arguments")
            }
        }
    }
}