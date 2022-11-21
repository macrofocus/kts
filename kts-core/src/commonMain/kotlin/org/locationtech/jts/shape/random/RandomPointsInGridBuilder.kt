/*
 * Copyright (c) 2016 Martin Davis.
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
package org.locationtech.jts.shape.random

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.legacy.Math.cos
import org.locationtech.jts.legacy.Math.sin
import org.locationtech.jts.legacy.Math.sqrt
import org.locationtech.jts.math.MathUtil.clamp
import org.locationtech.jts.shape.GeometricShapeBuilder
import kotlin.math.PI
import kotlin.random.Random

/**
 * Creates random point sets
 * where the points are constrained to lie in the cells of a grid.
 *
 * @author mbdavis
 */
class RandomPointsInGridBuilder : GeometricShapeBuilder {
    private var isConstrainedToCircle = false
    private var gutterFraction = 0.0

    /**
     * Create a builder which will create shapes using the default
     * [GeometryFactory].
     */
    constructor() : super(GeometryFactory()) {}

    /**
     * Create a builder which will create shapes using the given
     * [GeometryFactory].
     *
     * @param geomFact the factory to use
     */
    constructor(geomFact: GeometryFactory?) : super(geomFact!!) {}

    /**
     * Sets whether generated points are constrained to lie
     * within a circle contained within each grid cell.
     * This provides greater separation between points
     * in adjacent cells.
     *
     *
     * The default is to not be constrained to a circle.
     * @param isConstrainedToCircle
     */
    fun setConstrainedToCircle(isConstrainedToCircle: Boolean) {
        this.isConstrainedToCircle = isConstrainedToCircle
    }

    /**
     * Sets the fraction of the grid cell side which will be treated as
     * a gutter, in which no points will be created.
     * The provided value is clamped to the range [0.0, 1.0].
     *
     * @param gutterFraction
     */
    fun setGutterFraction(gutterFraction: Double) {
        this.gutterFraction = gutterFraction
    }// ensure that at least numPts points are generated

    /**
     * Gets the [MultiPoint] containing the generated point
     *
     * @return a MultiPoint
     */
    override val geometry: Geometry
        get() {
            var nCells: Int = sqrt(numPts.toDouble()).toInt()
            // ensure that at least numPts points are generated
            if (nCells * nCells < numPts) nCells += 1
            val gridDX = extent.width / nCells
            val gridDY = extent.height / nCells
            val gutterFrac = clamp(gutterFraction, 0.0, 1.0)
            val gutterOffsetX = gridDX * gutterFrac / 2
            val gutterOffsetY = gridDY * gutterFrac / 2
            val cellFrac = 1.0 - gutterFrac
            val cellDX = cellFrac * gridDX
            val cellDY = cellFrac * gridDY
            val pts = arrayOfNulls<Coordinate>(nCells * nCells)
            var index = 0
            for (i in 0 until nCells) {
                for (j in 0 until nCells) {
                    val orgX = extent.minX + i * gridDX + gutterOffsetX
                    val orgY = extent.minY + j * gridDY + gutterOffsetY
                    pts[index++] = randomPointInCell(orgX, orgY, cellDX, cellDY)
                }
            }
            return geomFactory.createMultiPointFromCoords(pts.requireNoNulls())
        }

    private fun randomPointInCell(orgX: Double, orgY: Double, xLen: Double, yLen: Double): Coordinate {
        return if (isConstrainedToCircle) {
            randomPointInCircle(
                orgX,
                orgY,
                xLen, yLen
            )
        } else randomPointInGridCell(orgX, orgY, xLen, yLen)
    }

    private fun randomPointInGridCell(orgX: Double, orgY: Double, xLen: Double, yLen: Double): Coordinate {
        val x: Double = orgX + xLen * Random.nextDouble()
        val y: Double = orgY + yLen * Random.nextDouble()
        return createCoord(x, y)
    }

    companion object {
        private fun randomPointInCircle(orgX: Double, orgY: Double, width: Double, height: Double): Coordinate {
            val centreX = orgX + width / 2
            val centreY = orgY + height / 2
            val rndAng: Double = 2 * PI * Random.nextDouble()
            val rndRadius: Double = Random.nextDouble()
            // use square root of radius, since area is proportional to square of radius
            val rndRadius2: Double = sqrt(rndRadius)
            val rndX: Double = width / 2 * rndRadius2 * cos(rndAng)
            val rndY: Double = height / 2 * rndRadius2 * sin(rndAng)
            val x0 = centreX + rndX
            val y0 = centreY + rndY
            return Coordinate(x0, y0)
        }
    }
}