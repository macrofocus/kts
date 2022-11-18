/*
 * Copyright (c) 2016 Martin Davis.
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.shape

import org.locationtech.jts.geom.*
import org.locationtech.jts.legacy.Math.min

abstract class GeometricShapeBuilder(protected var geomFactory: GeometryFactory) {
    var extent = Envelope(0.0, 1.0, 0.0, 1.0)
    protected var numPts = 0
    val centre: Coordinate?
        get() = extent.centre()
    val diameter: Double
        get() = min(extent.height, extent.width)
    val radius: Double
        get() = diameter / 2
    val squareBaseLine: LineSegment
        get() {
            val radius = radius
            val centre = centre
            val p0 = Coordinate(centre!!.x - radius, centre.y - radius)
            val p1 = Coordinate(centre.x + radius, centre.y - radius)
            return LineSegment(p0, p1)
        }
    val squareExtent: Envelope
        get() {
            val radius = radius
            val centre = centre
            return Envelope(
                centre!!.x - radius, centre.x + radius,
                centre.y - radius, centre.y + radius
            )
        }

    /**
     * Sets the total number of points in the created [Geometry].
     * The created geometry will have no more than this number of points,
     * unless more are needed to create a valid geometry.
     */
    fun setNumPoints(numPts: Int) {
        this.numPts = numPts
    }

    abstract val geometry: Geometry?
    protected fun createCoord(x: Double, y: Double): Coordinate {
        val pt = Coordinate(x, y)
        geomFactory.precisionModel.makePrecise(pt)
        return pt
    }
}