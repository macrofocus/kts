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
package org.locationtech.jts.geomgraph

import org.locationtech.jts.geom.Location
import org.locationtech.jts.geom.Location.toLocationSymbol

/**
 * A TopologyLocation is the labelling of a
 * GraphComponent's topological relationship to a single Geometry.
 *
 * If the parent component is an area edge, each side and the edge itself
 * have a topological location.  These locations are named
 *
 *  *  ON: on the edge
 *  *  LEFT: left-hand side of the edge
 *  *  RIGHT: right-hand side
 *
 * If the parent component is a line edge or node, there is a single
 * topological relationship attribute, ON.
 *
 * The possible values of a topological location are
 * {Location.NONE, Location.EXTERIOR, Location.BOUNDARY, Location.INTERIOR}
 *
 * The labelling is stored in an array location[j] where
 * where j has the values ON, LEFT, RIGHT
 * @version 1.7
 */
class TopologyLocation {
    lateinit var locations: IntArray

    constructor(location: IntArray) {
        init(location.size)
    }

    /**
     * Constructs a TopologyLocation specifying how points on, to the left of, and to the
     * right of some GraphComponent relate to some Geometry. Possible values for the
     * parameters are Location.NULL, Location.EXTERIOR, Location.BOUNDARY,
     * and Location.INTERIOR.
     * @see Location
     */
    constructor(on: Int, left: Int, right: Int) {
        init(3)
        locations[Position.ON] = on
        locations[Position.LEFT] = left
        locations[Position.RIGHT] = right
    }

    constructor(on: Int) {
        init(1)
        locations[Position.ON] = on
    }

    constructor(gl: TopologyLocation?) {
        init(gl!!.locations.size)
        if (gl != null) {
            for (i in locations.indices) {
                locations[i] = gl.locations[i]
            }
        }
    }

    private fun init(size: Int) {
        locations = IntArray(size)
        setAllLocations(Location.NONE)
    }

    operator fun get(posIndex: Int): Int {
        return if (posIndex < locations.size) locations[posIndex] else Location.NONE
    }

    /**
     * @return true if all locations are NULL
     */
    val isNull: Boolean
        get() {
            for (i in locations.indices) {
                if (locations[i] != Location.NONE) return false
            }
            return true
        }

    /**
     * @return true if any locations are NULL
     */
    val isAnyNull: Boolean
        get() {
            for (i in locations.indices) {
                if (locations[i] == Location.NONE) return true
            }
            return false
        }

    fun isEqualOnSide(le: TopologyLocation, locIndex: Int): Boolean {
        return locations[locIndex] == le.locations[locIndex]
    }

    val isArea: Boolean
        get() = locations.size > 1
    val isLine: Boolean
        get() = locations.size == 1

    fun flip() {
        if (locations.size <= 1) return
        val temp = locations[Position.LEFT]
        locations[Position.LEFT] = locations[Position.RIGHT]
        locations[Position.RIGHT] = temp
    }

    fun setAllLocations(locValue: Int) {
        for (i in locations.indices) {
            locations[i] = locValue
        }
    }

    fun setAllLocationsIfNull(locValue: Int) {
        for (i in locations.indices) {
            if (locations[i] == Location.NONE) locations[i] = locValue
        }
    }

    fun setLocation(locIndex: Int, locValue: Int) {
        locations[locIndex] = locValue
    }

    fun setLocation(locValue: Int) {
        setLocation(Position.ON, locValue)
    }

    fun setLocations(on: Int, left: Int, right: Int) {
        locations[Position.ON] = on
        locations[Position.LEFT] = left
        locations[Position.RIGHT] = right
    }

    fun allPositionsEqual(loc: Int): Boolean {
        for (i in locations.indices) {
            if (locations[i] != loc) return false
        }
        return true
    }

    /**
     * merge updates only the NULL attributes of this object
     * with the attributes of another.
     */
    fun merge(gl: TopologyLocation) {
        // if the src is an Area label & and the dest is not, increase the dest to be an Area
        if (gl.locations.size > locations.size) {
            val newLoc = IntArray(3)
            newLoc[Position.ON] = locations[Position.ON]
            newLoc[Position.LEFT] = Location.NONE
            newLoc[Position.RIGHT] = Location.NONE
            locations = newLoc
        }
        for (i in locations.indices) {
            if (locations[i] == Location.NONE && i < gl.locations.size) locations[i] = gl.locations[i]
        }
    }

    override fun toString(): String {
        val buf = StringBuilder()
        if (locations.size > 1) buf.append(toLocationSymbol(locations[Position.LEFT]))
        buf.append(toLocationSymbol(locations[Position.ON]))
        if (locations.size > 1) buf.append(toLocationSymbol(locations[Position.RIGHT]))
        return buf.toString()
    }
}