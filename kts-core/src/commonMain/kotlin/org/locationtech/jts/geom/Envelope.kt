/*
 * Copyright (c) 2016 Vivid Solutions.
 * Copyright (c) 2020 Macrofocus GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.geom

import org.locationtech.jts.geom.Coordinate.Companion.hashCode
import org.locationtech.jts.legacy.Serializable
import kotlin.jvm.JvmStatic
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Defines a rectangular region of the 2D coordinate plane.
 * It is often used to represent the bounding box of a [Geometry],
 * e.g. the minimum and maximum x and y values of the [Coordinate]s.
 *
 * Envelopes support infinite or half-infinite regions, by using the values of
 * `Double.POSITIVE_INFINITY` and `Double.NEGATIVE_INFINITY`.
 * Envelope objects may have a null value.
 *
 * When Envelope objects are created or initialized,
 * the supplies extent values are automatically sorted into the correct order.
 *
 * @version 1.7
 */
class Envelope : Comparable<Any?>, Serializable {
    override fun hashCode(): Int {
        //Algorithm from Effective Java by Joshua Bloch [Jon Aquino]
        var result = 17
        result = 37 * result + hashCode(minX)
        result = 37 * result + hashCode(maxX)
        result = 37 * result + hashCode(minY)
        result = 37 * result + hashCode(maxY)
        return result
    }
    /**
     * Returns the `Envelope`s minimum x-value. min x &gt; max x
     * indicates that this is a null `Envelope`.
     *
     * @return    the minimum x-coordinate
     */
    /**
     * the minimum x-coordinate
     */
    var minX = 0.0
        private set
    /**
     * Returns the `Envelope`s maximum x-value. min x &gt; max x
     * indicates that this is a null `Envelope`.
     *
     * @return    the maximum x-coordinate
     */
    /**
     * the maximum x-coordinate
     */
    var maxX = 0.0
        private set
    /**
     * Returns the `Envelope`s minimum y-value. min y &gt; max y
     * indicates that this is a null `Envelope`.
     *
     * @return    the minimum y-coordinate
     */
    /**
     * the minimum y-coordinate
     */
    var minY = 0.0
        private set
    /**
     * Returns the `Envelope`s maximum y-value. min y &gt; max y
     * indicates that this is a null `Envelope`.
     *
     * @return    the maximum y-coordinate
     */
    /**
     * the maximum y-coordinate
     */
    var maxY = 0.0
        private set

    /**
     * Creates a null `Envelope`.
     */
    constructor() {
        init()
    }

    /**
     * Creates an `Envelope` for a region defined by maximum and minimum values.
     *
     * @param  x1  the first x-value
     * @param  x2  the second x-value
     * @param  y1  the first y-value
     * @param  y2  the second y-value
     */
    constructor(x1: Double, x2: Double, y1: Double, y2: Double) {
        init(x1, x2, y1, y2)
    }

    /**
     * Creates an `Envelope` for a region defined by two Coordinates.
     *
     * @param  p1  the first Coordinate
     * @param  p2  the second Coordinate
     */
    constructor(p1: Coordinate, p2: Coordinate) {
        init(p1.x, p2.x, p1.y, p2.y)
    }

    /**
     * Creates an `Envelope` for a region defined by a single Coordinate.
     *
     * @param  p  the Coordinate
     */
    constructor(p: Coordinate) {
        init(p.x, p.x, p.y, p.y)
    }

    /**
     * Create an `Envelope` from an existing Envelope.
     *
     * @param  env  the Envelope to initialize from
     */
    constructor(env: Envelope) {
        init(env)
    }

    /**
     * Initialize to a null `Envelope`.
     */
    fun init() {
        setToNull()
    }

    /**
     * Initialize an `Envelope` for a region defined by maximum and minimum values.
     *
     * @param  x1  the first x-value
     * @param  x2  the second x-value
     * @param  y1  the first y-value
     * @param  y2  the second y-value
     */
    fun init(x1: Double, x2: Double, y1: Double, y2: Double) {
        if (x1 < x2) {
            minX = x1
            maxX = x2
        } else {
            minX = x2
            maxX = x1
        }
        if (y1 < y2) {
            minY = y1
            maxY = y2
        } else {
            minY = y2
            maxY = y1
        }
    }

    /**
     * Creates a copy of this envelope object.
     *
     * @return a copy of this envelope
     */
    fun copy(): Envelope {
        return Envelope(this)
    }

    /**
     * Initialize an `Envelope` to a region defined by two Coordinates.
     *
     * @param  p1  the first Coordinate
     * @param  p2  the second Coordinate
     */
    fun init(p1: Coordinate, p2: Coordinate) {
        init(p1.x, p2.x, p1.y, p2.y)
    }

    /**
     * Initialize an `Envelope` to a region defined by a single Coordinate.
     *
     * @param  p  the coordinate
     */
    fun init(p: Coordinate) {
        init(p.x, p.x, p.y, p.y)
    }

    /**
     * Initialize an `Envelope` from an existing Envelope.
     *
     * @param  env  the Envelope to initialize from
     */
    fun init(env: Envelope) {
        minX = env.minX
        maxX = env.maxX
        minY = env.minY
        maxY = env.maxY
    }

    /**
     * Makes this `Envelope` a "null" envelope, that is, the envelope
     * of the empty geometry.
     */
    fun setToNull() {
        minX = 0.0
        maxX = -1.0
        minY = 0.0
        maxY = -1.0
    }

    /**
     * Returns `true` if this `Envelope` is a "null"
     * envelope.
     *
     * @return    `true` if this `Envelope` is uninitialized
     * or is the envelope of the empty geometry.
     */
    val isNull: Boolean
        get() = maxX < minX

    /**
     * Returns the difference between the maximum and minimum x values.
     *
     * @return    max x - min x, or 0 if this is a null `Envelope`
     */
    val width: Double
        get() = if (isNull) {
            0.0
        } else maxX - minX

    /**
     * Returns the difference between the maximum and minimum y values.
     *
     * @return    max y - min y, or 0 if this is a null `Envelope`
     */
    val height: Double
        get() = if (isNull) {
            0.0
        } else maxY - minY

    /**
     * Gets the length of the diameter (diagonal) of the envelope.
     *
     * @return the diameter length
     */
    val diameter: Double
        get() {
            if (isNull) {
                return 0.0
            }
            val w = width
            val h = height
            return sqrt(w * w + h * h)
        }

    /**
     * Gets the area of this envelope.
     *
     * @return the area of the envelope
     * @return 0.0 if the envelope is null
     */
    val area: Double
        get() = width * height

    /**
     * Gets the minimum extent of this envelope across both dimensions.
     *
     * @return the minimum extent of this envelope
     */
    fun minExtent(): Double {
        if (isNull) return 0.0
        val w = width
        val h = height
        return if (w < h) w else h
    }

    /**
     * Gets the maximum extent of this envelope across both dimensions.
     *
     * @return the maximum extent of this envelope
     */
    fun maxExtent(): Double {
        if (isNull) return 0.0
        val w = width
        val h = height
        return if (w > h) w else h
    }

    /**
     * Enlarges this `Envelope` so that it contains
     * the given [Coordinate].
     * Has no effect if the point is already on or within the envelope.
     *
     * @param  p  the Coordinate to expand to include
     */
    fun expandToInclude(p: Coordinate) {
        expandToInclude(p.x, p.y)
    }

    /**
     * Expands this envelope by a given distance in all directions.
     * Both positive and negative distances are supported.
     *
     * @param distance the distance to expand the envelope
     */
    fun expandBy(distance: Double) {
        expandBy(distance, distance)
    }

    /**
     * Expands this envelope by a given distance in all directions.
     * Both positive and negative distances are supported.
     *
     * @param deltaX the distance to expand the envelope along the the X axis
     * @param deltaY the distance to expand the envelope along the the Y axis
     */
    fun expandBy(deltaX: Double, deltaY: Double) {
        if (isNull) return
        minX -= deltaX
        maxX += deltaX
        minY -= deltaY
        maxY += deltaY

        // check for envelope disappearing
        if (minX > maxX || minY > maxY) setToNull()
    }

    /**
     * Enlarges this `Envelope` so that it contains
     * the given point.
     * Has no effect if the point is already on or within the envelope.
     *
     * @param  x  the value to lower the minimum x to or to raise the maximum x to
     * @param  y  the value to lower the minimum y to or to raise the maximum y to
     */
    fun expandToInclude(x: Double, y: Double) {
        if (isNull) {
            minX = x
            maxX = x
            minY = y
            maxY = y
        } else {
            if (x < minX) {
                minX = x
            }
            if (x > maxX) {
                maxX = x
            }
            if (y < minY) {
                minY = y
            }
            if (y > maxY) {
                maxY = y
            }
        }
    }

    /**
     * Enlarges this `Envelope` so that it contains
     * the `other` Envelope.
     * Has no effect if `other` is wholly on or
     * within the envelope.
     *
     * @param  other  the `Envelope` to expand to include
     */
    fun expandToInclude(other: Envelope) {
        if (other.isNull) {
            return
        }
        if (isNull) {
            minX = other.minX
            maxX = other.maxX
            minY = other.minY
            maxY = other.maxY
        } else {
            if (other.minX < minX) {
                minX = other.minX
            }
            if (other.maxX > maxX) {
                maxX = other.maxX
            }
            if (other.minY < minY) {
                minY = other.minY
            }
            if (other.maxY > maxY) {
                maxY = other.maxY
            }
        }
    }

    /**
     * Translates this envelope by given amounts in the X and Y direction.
     *
     * @param transX the amount to translate along the X axis
     * @param transY the amount to translate along the Y axis
     */
    fun translate(transX: Double, transY: Double) {
        if (isNull) {
            return
        }
        init(
            minX + transX, maxX + transX,
            minY + transY, maxY + transY
        )
    }

    /**
     * Computes the coordinate of the centre of this envelope (as long as it is non-null
     *
     * @return the centre coordinate of this envelope
     * `null` if the envelope is null
     */
    fun centre(): Coordinate? {
        return if (isNull) null else Coordinate(
            (minX + maxX) / 2.0,
            (minY + maxY) / 2.0
        )
    }

    /**
     * Computes the intersection of two [Envelope]s.
     *
     * @param env the envelope to intersect with
     * @return a new Envelope representing the intersection of the envelopes (this will be
     * the null envelope if either argument is null, or they do not intersect
     */
    fun intersection(env: Envelope): Envelope {
        if (isNull || env.isNull || !intersects(env)) return Envelope()
        val intMinX = if (minX > env.minX) minX else env.minX
        val intMinY = if (minY > env.minY) minY else env.minY
        val intMaxX = if (maxX < env.maxX) maxX else env.maxX
        val intMaxY = if (maxY < env.maxY) maxY else env.maxY
        return Envelope(intMinX, intMaxX, intMinY, intMaxY)
    }

    /**
     * Tests if the region defined by `other`
     * intersects the region of this `Envelope`.
     *
     * @param  other  the `Envelope` which this `Envelope` is
     * being checked for intersecting
     * @return        `true` if the `Envelope`s intersect
     */
    fun intersects(other: Envelope): Boolean {
        return if (isNull || other.isNull) {
            false
        } else !(other.minX > maxX || other.maxX < minX || other.minY > maxY || other.maxY < minY)
    }

    /**
     * Tests if the extent defined by two extremal points
     * intersects the extent of this `Envelope`.
     *
     * @param a a point
     * @param b another point
     * @return   `true` if the extents intersect
     */
    fun intersects(a: Coordinate, b: Coordinate): Boolean {
        if (isNull) {
            return false
        }
        val envminx = if (a.x < b.x) a.x else b.x
        if (envminx > maxX) return false
        val envmaxx = if (a.x > b.x) a.x else b.x
        if (envmaxx < minX) return false
        val envminy = if (a.y < b.y) a.y else b.y
        if (envminy > maxY) return false
        val envmaxy = if (a.y > b.y) a.y else b.y
        return envmaxy >= minY
    }

    /**
     * Tests if the region defined by `other`
     * is disjoint from the region of this `Envelope`.
     *
     * @param  other  the `Envelope` being checked for disjointness
     * @return        `true` if the `Envelope`s are disjoint
     * @see .intersects
     */
    fun disjoint(other: Envelope): Boolean {
        return if (isNull || other.isNull) {
            true
        } else other.minX > maxX || other.maxX < minX || other.minY > maxY || other.maxY < minY
    }

    @Deprecated(
        """Use #intersects instead. In the future, #overlaps may be
    changed to be a true overlap check; that is, whether the intersection is
    two-dimensional."""
    )
    fun overlaps(other: Envelope): Boolean {
        return intersects(other)
    }

    /**
     * Tests if the point `p`
     * intersects (lies inside) the region of this `Envelope`.
     *
     * @param  p  the `Coordinate` to be tested
     * @return `true` if the point intersects this `Envelope`
     */
    fun intersects(p: Coordinate): Boolean {
        return intersects(p.x, p.y)
    }

    @Deprecated("Use #intersects instead.")
    fun overlaps(p: Coordinate): Boolean {
        return intersects(p)
    }

    /**
     * Check if the point `(x, y)`
     * intersects (lies inside) the region of this `Envelope`.
     *
     * @param  x  the x-ordinate of the point
     * @param  y  the y-ordinate of the point
     * @return        `true` if the point overlaps this `Envelope`
     */
    fun intersects(x: Double, y: Double): Boolean {
        return if (isNull) false else !(x > maxX || x < minX || y > maxY || y < minY)
    }

    @Deprecated("Use #intersects instead.")
    fun overlaps(x: Double, y: Double): Boolean {
        return intersects(x, y)
    }

    /**
     * Tests if the `Envelope other`
     * lies wholely inside this `Envelope` (inclusive of the boundary).
     *
     * Note that this is **not** the same definition as the SFS <tt>contains</tt>,
     * which would exclude the envelope boundary.
     *
     * @param  other the `Envelope` to check
     * @return true if `other` is contained in this `Envelope`
     * @see .covers
     */
    operator fun contains(other: Envelope): Boolean {
        return covers(other)
    }

    /**
     * Tests if the given point lies in or on the envelope.
     *
     * Note that this is **not** the same definition as the SFS <tt>contains</tt>,
     * which would exclude the envelope boundary.
     *
     * @param  p  the point which this `Envelope` is
     * being checked for containing
     * @return    `true` if the point lies in the interior or
     * on the boundary of this `Envelope`.
     * @see .covers
     */
    operator fun contains(p: Coordinate): Boolean {
        return covers(p)
    }

    /**
     * Tests if the given point lies in or on the envelope.
     *
     * Note that this is **not** the same definition as the SFS <tt>contains</tt>,
     * which would exclude the envelope boundary.
     *
     * @param  x  the x-coordinate of the point which this `Envelope` is
     * being checked for containing
     * @param  y  the y-coordinate of the point which this `Envelope` is
     * being checked for containing
     * @return    `true` if `(x, y)` lies in the interior or
     * on the boundary of this `Envelope`.
     * @see .covers
     */
    fun contains(x: Double, y: Double): Boolean {
        return covers(x, y)
    }

    /**
     * Tests if the given point lies in or on the envelope.
     *
     * @param  x  the x-coordinate of the point which this `Envelope` is
     * being checked for containing
     * @param  y  the y-coordinate of the point which this `Envelope` is
     * being checked for containing
     * @return    `true` if `(x, y)` lies in the interior or
     * on the boundary of this `Envelope`.
     */
    fun covers(x: Double, y: Double): Boolean {
        return if (isNull) false else x in minX..maxX && y >= minY && y <= maxY
    }

    /**
     * Tests if the given point lies in or on the envelope.
     *
     * @param  p  the point which this `Envelope` is
     * being checked for containing
     * @return    `true` if the point lies in the interior or
     * on the boundary of this `Envelope`.
     */
    fun covers(p: Coordinate): Boolean {
        return covers(p.x, p.y)
    }

    /**
     * Tests if the `Envelope other`
     * lies wholely inside this `Envelope` (inclusive of the boundary).
     *
     * @param  other the `Envelope` to check
     * @return true if this `Envelope` covers the `other`
     */
    fun covers(other: Envelope): Boolean {
        return if (isNull || other.isNull) {
            false
        } else other.minX >= minX && other.maxX <= maxX && other.minY >= minY && other.maxY <= maxY
    }

    /**
     * Computes the distance between this and another
     * `Envelope`.
     * The distance between overlapping Envelopes is 0.  Otherwise, the
     * distance is the Euclidean distance between the closest points.
     */
    fun distance(env: Envelope): Double {
        if (intersects(env)) return 0.0
        var dx = 0.0
        if (maxX < env.minX) dx = env.minX - maxX else if (minX > env.maxX) dx = minX - env.maxX
        var dy = 0.0
        if (maxY < env.minY) dy = env.minY - maxY else if (minY > env.maxY) dy = minY - env.maxY

        // if either is zero, the envelopes overlap either vertically or horizontally
        if (dx == 0.0) return dy
        return if (dy == 0.0) dx else sqrt(dx * dx + dy * dy)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Envelope) {
            return false
        }
        return if (isNull) {
            other.isNull
        } else maxX == other.maxX && maxY == other.maxY && minX == other.minX && minY == other.minY
    }

    override fun toString(): String {
        return "Env[$minX : $maxX, $minY : $maxY]"
    }

    /**
     * Compares two envelopes using lexicographic ordering.
     * The ordering comparison is based on the usual numerical
     * comparison between the sequence of ordinates.
     * Null envelopes are less than all non-null envelopes.
     *
     * @param o an Envelope object
     */
    override fun compareTo(o: Any?): Int {
        val env = o as Envelope?
        // compare nulls if present
        if (isNull) {
            return if (env!!.isNull) 0 else -1
        } else {
            if (env!!.isNull) return 1
        }
        // compare based on numerical ordering of ordinates
        if (minX < env.minX) return -1
        if (minX > env.minX) return 1
        if (minY < env.minY) return -1
        if (minY > env.minY) return 1
        if (maxX < env.maxX) return -1
        if (maxX > env.maxX) return 1
        if (maxY < env.maxY) return -1
        return if (maxY > env.maxY) 1 else 0
    }

    companion object {
        private const val serialVersionUID = 5873921885273102420L

        /**
         * Test the point q to see whether it intersects the Envelope defined by p1-p2
         * @param p1 one extremal point of the envelope
         * @param p2 another extremal point of the envelope
         * @param q the point to test for intersection
         * @return `true` if q intersects the envelope p1-p2
         */
        @JvmStatic
        fun intersects(p1: Coordinate, p2: Coordinate, q: Coordinate): Boolean {
            //OptimizeIt shows that Math#min and Math#max here are a bottleneck.
            //Replace with direct comparisons. [Jon Aquino]
            return q.x >= (if (p1.x < p2.x) p1.x else p2.x) && q.x <= (if (p1.x > p2.x) p1.x else p2.x) &&
                    q.y >= (if (p1.y < p2.y) p1.y else p2.y) && q.y <= (if (p1.y > p2.y) p1.y else p2.y)
        }

        /**
         * Tests whether the envelope defined by p1-p2
         * and the envelope defined by q1-q2
         * intersect.
         *
         * @param p1 one extremal point of the envelope P
         * @param p2 another extremal point of the envelope P
         * @param q1 one extremal point of the envelope Q
         * @param q2 another extremal point of the envelope Q
         * @return `true` if Q intersects P
         */
        @JvmStatic
        fun intersects(p1: Coordinate, p2: Coordinate, q1: Coordinate, q2: Coordinate): Boolean {
            var minq = min(q1.x, q2.x)
            var maxq = max(q1.x, q2.x)
            var minp = min(p1.x, p2.x)
            var maxp = max(p1.x, p2.x)
            if (minp > maxq) return false
            if (maxp < minq) return false
            minq = min(q1.y, q2.y)
            maxq = max(q1.y, q2.y)
            minp = min(p1.y, p2.y)
            maxp = max(p1.y, p2.y)
            if (minp > maxq) return false
            return maxp >= minq
        }
    }
}