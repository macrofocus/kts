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
package org.locationtech.jts.coverage

import org.locationtech.jts.algorithm.locate.IndexedPointInAreaLocator
import org.locationtech.jts.algorithm.locate.PointOnGeometryLocator
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Location
import org.locationtech.jts.geom.Polygon

class CoveragePolygon(private val polygon: Polygon) {
    private val polyEnv: Envelope = polygon.envelopeInternal
    internal var locator: IndexedPointInAreaLocator? = null

    fun intersectsEnv(env: Envelope): Boolean {
        //-- test intersection explicitly to avoid expensive null check
        return !(env.minX > polyEnv.maxX
                || env.maxX < polyEnv.minX
                || env.minY > polyEnv.maxY
                || env.maxY < polyEnv.minY)
    }

    private fun intersectsEnv(p: Coordinate): Boolean {
        //-- test intersection explicitly to avoid expensive null check
        return !(p.x > polyEnv.maxX ||
                p.x < polyEnv.minX ||
                p.y > polyEnv.maxY ||
                p.y < polyEnv.minY)
    }

    operator fun contains(p: Coordinate): Boolean {
        if (!intersectsEnv(p)) return false
        val pia = getPointLocator()
        return Location.INTERIOR == pia.locate(p)
    }

    private fun getPointLocator(): PointOnGeometryLocator {
        if (locator == null) {
            locator = IndexedPointInAreaLocator(polygon)
        }
        return locator!!
    }
}
