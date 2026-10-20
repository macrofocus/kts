/*
 * Copyright (c) 2023 Martin Davis.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.operation.relateng

import org.locationtech.jts.geom.Dimension
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Location
import kotlin.jvm.JvmStatic

/**
 * Creates predicate instances for evaluating OGC-standard named topological relationships.
 * Predicates can be evaluated for geometries using [RelateNG].
 *
 * @author Martin Davis
 */
object RelatePredicate {
    /**
     * Creates a predicate to determine whether two geometries intersect.
     *
     * The `intersects` predicate has the following equivalent definitions:
     *
     *  * The two geometries have at least one point in common
     *  * The DE-9IM Intersection Matrix for the two geometries matches
     * at least one of the patterns
     *     * `[T********]`
     *     * `[*T*******]`
     *     * `[***T*****]`
     *     * `[****T****]`
     *  * `disjoint() = false`
     * <br></br>(`intersects` is the inverse of `disjoint`)
     *
     * @return the predicate instance
     *
     * @see disjoint
     */
    @JvmStatic
    fun intersects(): TopologyPredicate {
        return object : BasicPredicate() {

            override fun name(): String {
                return "intersects"
            }

            override fun requireSelfNoding(): Boolean {
                //-- self-noding is not required to check for a simple interaction
                return false
            }

            override fun requireExteriorCheck(isSourceA: Boolean): Boolean {
                //-- intersects only requires testing interaction
                return false
            }

            override fun init(envA: Envelope, envB: Envelope) {
                require(envA.intersects(envB))
            }

            override fun updateDimension(locA: Int, locB: Int, dimension: Int) {
                setValueIf(true, isIntersection(locA, locB))
            }

            override fun finish() {
                //-- if no intersecting locations were found
                setValue(false)
            }
        }
    }

    /**
     * Creates a predicate to determine whether two geometries are disjoint.
     *
     * The `disjoint` predicate has the following equivalent definitions:
     *
     *  * The two geometries have no point in common
     *  * The DE-9IM Intersection Matrix for the two geometries matches
     * `[FF*FF****]`
     *  * `intersects() = false`
     * <br></br>(`disjoint` is the inverse of `intersects`)
     *
     * @return the predicate instance
     *
     * @see intersects
     */
    @JvmStatic
    fun disjoint(): TopologyPredicate {
        return object : BasicPredicate() {

            override fun name(): String {
                return "disjoint"
            }

            override fun requireSelfNoding(): Boolean {
                //-- self-noding is not required to check for a simple interaction
                return false
            }

            override fun requireInteraction(): Boolean {
                //-- ensure entire matrix is computed
                return false
            }

            override fun requireExteriorCheck(isSourceA: Boolean): Boolean {
                //-- disjoint only requires testing interaction
                return false
            }

            override fun init(envA: Envelope, envB: Envelope) {
                setValueIf(true, envA.disjoint(envB))
            }

            override fun updateDimension(locA: Int, locB: Int, dimension: Int) {
                setValueIf(false, isIntersection(locA, locB))
            }

            override fun finish() {
                //-- if no intersecting locations were found
                setValue(true)
            }
        }
    }

    /**
     * Creates a predicate to determine whether a geometry contains another geometry.
     *
     * The `contains` predicate has the following equivalent definitions:
     *
     *  * Every point of the other geometry is a point of this geometry,
     * and the interiors of the two geometries have at least one point in common.
     *  * The DE-9IM Intersection Matrix for the two geometries matches
     * the pattern
     * `[T*****FF*]`
     *  * `within(B, A) = true`
     * <br></br>(`contains` is the converse of [within] )
     *
     * An implication of the definition is that "Geometries do not
     * contain their boundary".  In other words, if a geometry A is a subset of
     * the points in the boundary of a geometry B, `B.contains(A) = false`.
     * (As a concrete example, take A to be a LineString which lies in the boundary of a Polygon B.)
     * For a predicate with similar behavior but avoiding
     * this subtle limitation, see [covers].
     *
     * @return the predicate instance
     *
     * @see within
     */
    @JvmStatic
    fun contains(): TopologyPredicate {
        return object : IMPredicate() {

            override fun name(): String {
                return "contains"
            }

            override fun requireCovers(isSourceA: Boolean): Boolean {
                return isSourceA == RelateGeometry.GEOM_A
            }

            override fun requireExteriorCheck(isSourceA: Boolean): Boolean {
                //-- only need to check B against Exterior of A
                return isSourceA == RelateGeometry.GEOM_B
            }

            override fun init(dimA: Int, dimB: Int) {
                super.init(dimA, dimB)
                require(isDimsCompatibleWithCovers(dimA, dimB))
            }

            override fun init(envA: Envelope, envB: Envelope) {
                requireCovers(envA, envB)
            }

            override fun isDetermined(): Boolean {
                return intersectsExteriorOf(RelateGeometry.GEOM_A)
            }

            override fun valueIM(): Boolean {
                return intMatrix.isContains
            }
        }
    }

    /**
     * Creates a predicate to determine whether a geometry is within another geometry.
     *
     * The `within` predicate has the following equivalent definitions:
     *
     *  * Every point of this geometry is a point of the other geometry,
     * and the interiors of the two geometries have at least one point in common.
     *  * The DE-9IM Intersection Matrix for the two geometries matches
     * `[T*F**F***]`
     *  * `contains(B, A) = true`
     * <br></br>(`within` is the converse of [contains])
     *
     * An implication of the definition is that
     * "The boundary of a Geometry is not within the Geometry".
     * In other words, if a geometry A is a subset of
     * the points in the boundary of a geometry B, `within(B, A) = false`
     * (As a concrete example, take A to be a LineString which lies in the boundary of a Polygon B.)
     * For a predicate with similar behavior but avoiding
     * this subtle limitation, see [coveredBy].
     *
     * @return the predicate instance
     *
     * @see contains
     */
    @JvmStatic
    fun within(): TopologyPredicate {
        return object : IMPredicate() {

            override fun name(): String {
                return "within"
            }

            override fun requireCovers(isSourceA: Boolean): Boolean {
                return isSourceA == RelateGeometry.GEOM_B
            }

            override fun requireExteriorCheck(isSourceA: Boolean): Boolean {
                //-- only need to check A against Exterior of B
                return isSourceA == RelateGeometry.GEOM_A
            }

            override fun init(dimA: Int, dimB: Int) {
                super.init(dimA, dimB)
                require(isDimsCompatibleWithCovers(dimB, dimA))
            }

            override fun init(envA: Envelope, envB: Envelope) {
                requireCovers(envB, envA)
            }

            override fun isDetermined(): Boolean {
                return intersectsExteriorOf(RelateGeometry.GEOM_B)
            }

            override fun valueIM(): Boolean {
                return intMatrix.isWithin
            }
        }
    }

    /**
     * Creates a predicate to determine whether a geometry covers another geometry.
     *
     * The `covers` predicate has the following equivalent definitions:
     *
     *  * Every point of the other geometry is a point of this geometry.
     *  * The DE-9IM Intersection Matrix for the two geometries matches
     * at least one of the following patterns:
     *     * `[T*****FF*]`
     *     * `[*T****FF*]`
     *     * `[***T**FF*]`
     *     * `[****T*FF*]`
     *  * `coveredBy(b, a) = true`
     * <br></br>(`covers` is the converse of [coveredBy])
     *
     * If either geometry is empty, the value of this predicate is `false`.
     *
     * This predicate is similar to [contains],
     * but is more inclusive (i.e. returns `true` for more cases).
     * In particular, unlike `contains` it does not distinguish between
     * points in the boundary and in the interior of geometries.
     * For most cases, `covers` should be used in preference to `contains`.
     * As an added benefit, `covers` is more amenable to optimization,
     * and hence should be more performant.
     *
     * @return the predicate instance
     *
     * @see coveredBy
     */
    @JvmStatic
    fun covers(): TopologyPredicate {
        return object : IMPredicate() {

            override fun name(): String {
                return "covers"
            }

            override fun requireCovers(isSourceA: Boolean): Boolean {
                return isSourceA == RelateGeometry.GEOM_A
            }

            override fun requireExteriorCheck(isSourceA: Boolean): Boolean {
                //-- only need to check B against Exterior of A
                return isSourceA == RelateGeometry.GEOM_B
            }

            override fun init(dimA: Int, dimB: Int) {
                super.init(dimA, dimB)
                require(isDimsCompatibleWithCovers(dimA, dimB))
            }

            override fun init(envA: Envelope, envB: Envelope) {
                requireCovers(envA, envB)
            }

            override fun isDetermined(): Boolean {
                return intersectsExteriorOf(RelateGeometry.GEOM_A)
            }

            override fun valueIM(): Boolean {
                return intMatrix.isCovers
            }
        }
    }

    /**
     * Creates a predicate to determine whether a geometry is covered by another geometry.
     *
     * The `coveredBy` predicate has the following equivalent definitions:
     *
     *  * Every point of this geometry is a point of the other geometry.
     *  * The DE-9IM Intersection Matrix for the two geometries matches
     * at least one of the following patterns:
     *     * `[T*F**F***]`
     *     * `[*TF**F***]`
     *     * `[**FT*F***]`
     *     * `[**F*TF***]`
     *  * `covers(B, A) = true`
     * <br></br>(`coveredBy` is the converse of [covers])
     *
     * If either geometry is empty, the value of this predicate is `false`.
     *
     * This predicate is similar to [within],
     * but is more inclusive (i.e. returns `true` for more cases).
     *
     * @return the predicate instance
     *
     * @see covers
     */
    @JvmStatic
    fun coveredBy(): TopologyPredicate {
        return object : IMPredicate() {
            override fun name(): String {
                return "coveredBy"
            }

            override fun requireCovers(isSourceA: Boolean): Boolean {
                return isSourceA == RelateGeometry.GEOM_B
            }

            override fun requireExteriorCheck(isSourceA: Boolean): Boolean {
                //-- only need to check A against Exterior of B
                return isSourceA == RelateGeometry.GEOM_A
            }

            override fun init(dimA: Int, dimB: Int) {
                super.init(dimA, dimB)
                require(isDimsCompatibleWithCovers(dimB, dimA))
            }

            override fun init(envA: Envelope, envB: Envelope) {
                requireCovers(envB, envA)
            }

            override fun isDetermined(): Boolean {
                return intersectsExteriorOf(RelateGeometry.GEOM_B)
            }

            override fun valueIM(): Boolean {
                return intMatrix.isCoveredBy
            }
        }
    }

    /**
     * Creates a predicate to determine whether a geometry crosses another geometry.
     *
     * The `crosses` predicate has the following equivalent definitions:
     *
     *  * The geometries have some but not all interior points in common.
     *  * The DE-9IM Intersection Matrix for the two geometries matches
     * one of the following patterns:
     *     * `[T*T******]` (for P/L, P/A, and L/A cases)
     *     * `[T*****T**]` (for L/P, A/P, and A/L cases)
     *     * `[0********]` (for L/L cases)
     *
     * For the A/A and P/P cases this predicate returns `false`.
     *
     * The SFS defined this predicate only for P/L, P/A, L/L, and L/A cases.
     * To make the relation symmetric
     * JTS extends the definition to apply to L/P, A/P and A/L cases as well.
     *
     * @return the predicate instance
     */
    @JvmStatic
    fun crosses(): TopologyPredicate {
        return object : IMPredicate() {
            override fun name(): String {
                return "crosses"
            }

            override fun init(dimA: Int, dimB: Int) {
                super.init(dimA, dimB)
                val isBothPointsOrAreas = ((dimA == Dimension.P && dimB == Dimension.P)
                        || (dimA == Dimension.A && dimB == Dimension.A))
                require(!isBothPointsOrAreas)
            }

            override fun isDetermined(): Boolean {
                if (dimA == Dimension.L && dimB == Dimension.L) {
                    //-- L/L interaction can only be dim = P
                    if (getDimension(Location.INTERIOR, Location.INTERIOR) > Dimension.P)
                        return true
                } else if (dimA < dimB) {
                    if (isIntersects(Location.INTERIOR, Location.INTERIOR)
                        && isIntersects(Location.INTERIOR, Location.EXTERIOR)
                    ) {
                        return true
                    }
                } else if (dimA > dimB) {
                    if (isIntersects(Location.INTERIOR, Location.INTERIOR)
                        && isIntersects(Location.EXTERIOR, Location.INTERIOR)
                    ) {
                        return true
                    }
                }
                return false
            }

            override fun valueIM(): Boolean {
                return intMatrix.isCrosses(dimA, dimB)
            }
        }
    }

    /**
     * Creates a predicate to determine whether two geometries are topologically equal.
     *
     * The `equals` predicate has the following equivalent definitions:
     *
     *  * The two geometries have at least one point in common,
     * and no point of either geometry lies in the exterior of the other geometry.
     *  * The DE-9IM Intersection Matrix for the two geometries matches
     * the pattern `T*F**FFF*`
     *
     * @return the predicate instance
     */
    @JvmStatic
    fun equalsTopo(): TopologyPredicate {
        return object : IMPredicate() {
            override fun name(): String {
                return "equals"
            }

            override fun init(dimA: Int, dimB: Int) {
                super.init(dimA, dimB)
                require(dimA == dimB)
            }

            override fun init(envA: Envelope, envB: Envelope) {
                require(envA == envB)
            }

            override fun isDetermined(): Boolean {
                val isEitherExteriorIntersects = (isIntersects(Location.INTERIOR, Location.EXTERIOR)
                        || isIntersects(Location.BOUNDARY, Location.EXTERIOR)
                        || isIntersects(Location.EXTERIOR, Location.INTERIOR)
                        || isIntersects(Location.EXTERIOR, Location.BOUNDARY))

                return isEitherExteriorIntersects
            }

            override fun valueIM(): Boolean {
                return intMatrix.isEquals(dimA, dimB)
            }
        }
    }

    /**
     * Creates a predicate to determine whether a geometry overlaps another geometry.
     *
     * The `overlaps` predicate has the following equivalent definitions:
     *
     *  * The geometries have at least one point each not shared by the other
     * (or equivalently neither covers the other),
     * they have the same dimension,
     * and the intersection of the interiors of the two geometries has
     * the same dimension as the geometries themselves.
     *  * The DE-9IM Intersection Matrix for the two geometries matches
     * `[T*T***T**]` (for P/P and A/A cases)
     * or `[1*T***T**]` (for L/L cases)
     *
     * If the geometries are of different dimension this predicate returns `false`.
     * This predicate is symmetric.
     *
     * @return the predicate instance
     */
    @JvmStatic
    fun overlaps(): TopologyPredicate {
        return object : IMPredicate() {
            override fun name(): String {
                return "overlaps"
            }

            override fun init(dimA: Int, dimB: Int) {
                super.init(dimA, dimB)
                require(dimA == dimB)
            }

            override fun isDetermined(): Boolean {
                if (dimA == Dimension.A || dimA == Dimension.P) {
                    if (isIntersects(Location.INTERIOR, Location.INTERIOR)
                        && isIntersects(Location.INTERIOR, Location.EXTERIOR)
                        && isIntersects(Location.EXTERIOR, Location.INTERIOR)
                    )
                        return true
                }
                if (dimA == Dimension.L) {
                    if (isDimension(Location.INTERIOR, Location.INTERIOR, Dimension.L)
                        && isIntersects(Location.INTERIOR, Location.EXTERIOR)
                        && isIntersects(Location.EXTERIOR, Location.INTERIOR)
                    )
                        return true
                }
                return false
            }

            override fun valueIM(): Boolean {
                return intMatrix.isOverlaps(dimA, dimB)
            }
        }
    }

    /**
     * Creates a predicate to determine whether a geometry touches another geometry.
     *
     * The `touches` predicate has the following equivalent definitions:
     *
     *  * The geometries have at least one point in common,
     * but their interiors do not intersect.
     *  * The DE-9IM Intersection Matrix for the two geometries matches
     * at least one of the following patterns
     *     * `[FT*******]`
     *     * `[F**T*****]`
     *     * `[F***T****]`
     *
     * If both geometries have dimension 0, the predicate returns `false`,
     * since points have only interiors.
     * This predicate is symmetric.
     *
     * @return the predicate instance
     */
    @JvmStatic
    fun touches(): TopologyPredicate {
        return object : IMPredicate() {
            override fun name(): String {
                return "touches"
            }

            override fun init(dimA: Int, dimB: Int) {
                super.init(dimA, dimB)
                //-- Points have only interiors, so cannot touch
                val isBothPoints = dimA == 0 && dimB == 0
                require(!isBothPoints)
            }

            override fun isDetermined(): Boolean {
                //-- for touches interiors cannot intersect
                val isInteriorsIntersects = isIntersects(Location.INTERIOR, Location.INTERIOR)
                return isInteriorsIntersects
            }

            override fun valueIM(): Boolean {
                return intMatrix.isTouches(dimA, dimB)
            }
        }
    }

    /**
     * Creates a predicate that matches a DE-9IM matrix pattern.
     *
     * @param imPattern the pattern to match
     * @return a predicate that matches the pattern
     *
     * @see IntersectionMatrixPattern
     */
    @JvmStatic
    fun matches(imPattern: String): TopologyPredicate {
        return IMPatternMatcher(imPattern)
    }
}
