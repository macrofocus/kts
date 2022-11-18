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
package org.locationtech.jts.shape.fractal

import org.locationtech.jts.geom.*
import org.locationtech.jts.legacy.Math.log
import org.locationtech.jts.shape.GeometricShapeBuilder

class SierpinskiCarpetBuilder(geomFactory: GeometryFactory) : GeometricShapeBuilder(geomFactory) {
    override val geometry: Geometry
        get() {
            val level = recursionLevelForSize(numPts)
            val baseLine: LineSegment = squareBaseLine
            val origin = baseLine.getCoordinate(0)
            val holes = getHoles(level, origin.x, origin.y, diameter)
            val shell = (geomFactory.toGeometry(squareExtent) as Polygon).exteriorRing
            return geomFactory.createPolygon(
                shell, holes
            )
        }

    private fun getHoles(n: Int, originX: Double, originY: Double, width: Double): Array<LinearRing> {
        val holeList: MutableList<LinearRing> = ArrayList()
        addHoles(n, originX, originY, width, holeList)
        return GeometryFactory.toLinearRingArray(holeList)
    }

    private fun addHoles(n: Int, originX: Double, originY: Double, width: Double, holeList: MutableList<LinearRing>) {
        if (n < 0) return
        val n2 = n - 1
        val widthThird = width / 3.0
        addHoles(n2, originX, originY, widthThird, holeList)
        addHoles(n2, originX + widthThird, originY, widthThird, holeList)
        addHoles(n2, originX + 2 * widthThird, originY, widthThird, holeList)
        addHoles(n2, originX, originY + widthThird, widthThird, holeList)
        addHoles(n2, originX + 2 * widthThird, originY + widthThird, widthThird, holeList)
        addHoles(n2, originX, originY + 2 * widthThird, widthThird, holeList)
        addHoles(n2, originX + widthThird, originY + 2 * widthThird, widthThird, holeList)
        addHoles(n2, originX + 2 * widthThird, originY + 2 * widthThird, widthThird, holeList)

        // add the centre hole
        holeList.add(createSquareHole(originX + widthThird, originY + widthThird, widthThird))
    }

    private fun createSquareHole(x: Double, y: Double, width: Double): LinearRing {
        val pts = arrayOf(
            Coordinate(x, y),
            Coordinate(x + width, y),
            Coordinate(x + width, y + width),
            Coordinate(x, y + width),
            Coordinate(x, y)
        )
        return geomFactory.createLinearRing(pts)
    }

    companion object {
        fun recursionLevelForSize(numPts: Int): Int {
            val pow4 = (numPts / 3).toDouble()
            val exp: Double = log(pow4) / log(4.0)
            return exp.toInt()
        }
    }
}