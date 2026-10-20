/*
 * Copyright (c) 2022 Martin Davis.
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

import org.locationtech.jts.geom.Envelope

/**
 * The API for strategy classes implementing
 * spatial predicates based on the DE-9IM topology model.
 * Predicate values for specific geometry pairs can be evaluated by [RelateNG].
 *
 * @author Martin Davis
 */
interface TopologyPredicate {
    /**
     * Gets the name of the predicate.
     *
     * @return the predicate name
     */
    fun name(): String

    /**
     * Reports whether this predicate requires self-noding for
     * geometries which contain crossing edges
     * (for example, [org.locationtech.jts.geom.LineString]s, or [org.locationtech.jts.geom.GeometryCollection]s
     * containing lines or polygons which may self-intersect).
     * Self-noding ensures that intersections are computed consistently
     * in cases which contain self-crossings and mutual crossings.
     *
     * Most predicates require this, but it can
     * be avoided for simple intersection detection
     * (such as in [RelatePredicate.intersects]
     * and [RelatePredicate.disjoint].
     * Avoiding self-noding improves performance for polygonal inputs.
     *
     * @return true if self-noding is required.
     */
    fun requireSelfNoding(): Boolean {
        return true
    }

    /**
     * Reports whether this predicate requires interaction between
     * the input geometries.
     * This is the case if
     * <pre>
     * IM[I, I] >= 0 or IM[I, B] >= 0 or IM[B, I] >= 0 or IM[B, B] >= 0
     * </pre>
     * This allows a fast result if
     * the envelopes of the geometries are disjoint.
     *
     * @return true if the geometries must interact
     */
    fun requireInteraction(): Boolean {
        return true
    }

    /**
     * Reports whether this predicate requires that the source
     * cover the target.
     * This is the case if
     * <pre>
     * IM[Ext(Src), Int(Tgt)] = F and IM[Ext(Src), Bdy(Tgt)] = F
     * </pre>
     * If true, this allows a fast result if
     * the source envelope does not cover the target envelope.
     *
     * @param isSourceA indicates the source input geometry
     * @return true if the predicate requires checking whether the source covers the target
     */
    fun requireCovers(isSourceA: Boolean): Boolean {
        return false
    }

    /**
     * Reports whether this predicate requires checking if the source input intersects
     * the Exterior of the target input.
     * This is the case if:
     * <pre>
     * IM[Int(Src), Ext(Tgt)] >= 0 or IM[Bdy(Src), Ext(Tgt)] >= 0
     * </pre>
     * If false, this may permit a faster result in some geometric situations.
     *
     * @param isSourceA indicates the source input geometry
     * @return true if the predicate requires checking whether the source intersects the target exterior
     */
    fun requireExteriorCheck(isSourceA: Boolean): Boolean {
        return true
    }

    /**
     * Initializes the predicate for a specific geometric case.
     * This may allow the predicate result to become known
     * if it can be inferred from the dimensions.
     *
     * @param dimA the dimension of geometry A
     * @param dimB the dimension of geometry B
     *
     * @see org.locationtech.jts.geom.Dimension
     */
    fun init(dimA: Int, dimB: Int) {
        //-- default if dimensions provide no information
    }

    /**
     * Initializes the predicate for a specific geometric case.
     * This may allow the predicate result to become known
     * if it can be inferred from the envelopes.
     *
     * @param envA the envelope of geometry A
     * @param envB the envelope of geometry B
     */
    fun init(envA: Envelope, envB: Envelope) {
        //-- default if envelopes provide no information
    }

    /**
     * Updates the entry in the DE-9IM intersection matrix
     * for given [org.locationtech.jts.geom.Location]s in the input geometries.
     *
     * If this method is called with a [org.locationtech.jts.geom.Dimension] value
     * which is less than the current value for the matrix entry,
     * the implementing class should avoid changing the entry
     * if this would cause information loss.
     *
     * @param locA the location on the A axis of the matrix
     * @param locB the location on the B axis of the matrix
     * @param dimension the dimension value for the entry
     *
     * @see org.locationtech.jts.geom.Dimension
     * @see org.locationtech.jts.geom.Location
     */
    fun updateDimension(locA: Int, locB: Int, dimension: Int)

    /**
     * Indicates that the value of the predicate can be finalized
     * based on its current state.
     */
    fun finish()

    /**
     * Tests if the predicate value is known.
     *
     * @return true if the result is known
     */
    fun isKnown(): Boolean

    /**
     * Gets the current value of the predicate result.
     * The value is only valid if [isKnown] is true.
     *
     * @return the predicate result value
     */
    fun value(): Boolean
}
