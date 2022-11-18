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

/**
 * An interface for classes which prepare [Geometry]s
 * in order to optimize the performance
 * of repeated calls to specific geometric operations.
 *
 *
 * A given implementation may provide optimized implementations
 * for only some of the specified methods,
 * and delegate the remaining methods to the original [Geometry] operations.
 * An implementation may also only optimize certain situations,
 * and delegate others.
 * See the implementing classes for documentation about which methods and situations
 * they optimize.
 *
 *
 * Subclasses are intended to be thread-safe, to allow `PreparedGeometry`
 * to be used in a multi-threaded context
 * (which allows extracting maximum benefit from the prepared state).
 *
 * @author Martin Davis
 */
interface PreparedGeometry {
    /**
     * Gets the original [Geometry] which has been prepared.
     *
     * @return the base geometry
     */
    val geometry: Geometry?

    /**
     * Tests whether the base [Geometry] contains a given geometry.
     *
     * @param geom the Geometry to test
     * @return true if this Geometry contains the given Geometry
     *
     * @see Geometry.contains
     */
    operator fun contains(geom: Geometry?): Boolean

    /**
     * Tests whether the base [Geometry] properly contains a given geometry.
     *
     * The `containsProperly` predicate has the following equivalent definitions:
     *
     *  * Every point of the other geometry is a point of this geometry's interior.
     *  * The DE-9IM Intersection Matrix for the two geometries matches
     * `[T**FF*FF*]`
     *
     * In other words, if the test geometry has any interaction with the boundary of the target
     * geometry the result of <tt>containsProperly</tt> is <tt>false</tt>.
     * This is different semantics to the [Geometry.contains] predicate,
     * in which test geometries can intersect the target's boundary and still be contained.
     *
     * The advantage of using this predicate is that it can be computed
     * efficiently, since it avoids the need to compute the full topological relationship
     * of the input boundaries in cases where they intersect.
     *
     * An example use case is computing the intersections
     * of a set of geometries with a large polygonal geometry.
     * Since <tt>intersection</tt> is a fairly slow operation, it can be more efficient
     * to use <tt>containsProperly</tt> to filter out test geometries which lie
     * wholly inside the area.  In these cases the intersection is
     * known *a priori* to be exactly the original test geometry.
     *
     * @param geom the Geometry to test
     * @return true if this Geometry properly contains the given Geometry
     *
     * @see Geometry.contains
     */
    fun containsProperly(geom: Geometry?): Boolean

    /**
     * Tests whether the base [Geometry] is covered by a given geometry.
     *
     * @param geom the Geometry to test
     * @return true if this Geometry is covered by the given Geometry
     *
     * @see Geometry.coveredBy
     */
    fun coveredBy(geom: Geometry?): Boolean

    /**
     * Tests whether the base [Geometry] covers a given geometry.
     *
     * @param geom the Geometry to test
     * @return true if this Geometry covers the given Geometry
     *
     * @see Geometry.covers
     */
    fun covers(geom: Geometry?): Boolean

    /**
     * Tests whether the base [Geometry] crosses a given geometry.
     *
     * @param geom the Geometry to test
     * @return true if this Geometry crosses the given Geometry
     *
     * @see Geometry.crosses
     */
    fun crosses(geom: Geometry?): Boolean

    /**
     * Tests whether the base [Geometry] is disjoint from a given geometry.
     * This method supports [GeometryCollection]s as input
     *
     * @param geom the Geometry to test
     * @return true if this Geometry is disjoint from the given Geometry
     *
     * @see Geometry.disjoint
     */
    fun disjoint(geom: Geometry): Boolean

    /**
     * Tests whether the base [Geometry] intersects a given geometry.
     * This method supports [GeometryCollection]s as input
     *
     * @param geom the Geometry to test
     * @return true if this Geometry intersects the given Geometry
     *
     * @see Geometry.intersects
     */
    fun intersects(geom: Geometry): Boolean

    /**
     * Tests whether the base [Geometry] overlaps a given geometry.
     *
     * @param geom the Geometry to test
     * @return true if this Geometry overlaps the given Geometry
     *
     * @see Geometry.overlaps
     */
    fun overlaps(geom: Geometry?): Boolean

    /**
     * Tests whether the base [Geometry] touches a given geometry.
     *
     * @param geom the Geometry to test
     * @return true if this Geometry touches the given Geometry
     *
     * @see Geometry.touches
     */
    fun touches(geom: Geometry?): Boolean

    /**
     * Tests whether the base [Geometry] is within a given geometry.
     *
     * @param geom the Geometry to test
     * @return true if this Geometry is within the given Geometry
     *
     * @see Geometry.within
     */
    fun within(geom: Geometry?): Boolean
}