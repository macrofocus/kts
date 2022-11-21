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

import org.locationtech.jts.algorithm.locate.IndexedPointInAreaLocator
import org.locationtech.jts.algorithm.locate.PointOnGeometryLocator
import org.locationtech.jts.geom.*
import org.locationtech.jts.shape.GeometricShapeBuilder
import kotlin.random.Random

/**
 * Creates random point sets contained in a
 * region defined by either a rectangular or a polygonal extent.
 *
 * @author mbdavis
 */
class RandomPointsBuilder : GeometricShapeBuilder {
    protected var maskPoly: Geometry? = null
    private var extentLocator: PointOnGeometryLocator? = null

    /**
     * Create a shape factory which will create shapes using the default
     * [GeometryFactory].
     */
    constructor() : super(GeometryFactory()) {}

    /**
     * Create a shape factory which will create shapes using the given
     * [GeometryFactory].
     *
     * @param geomFact the factory to use
     */
    constructor(geomFact: GeometryFactory?) : super(geomFact!!) {}

    /**
     * Sets a polygonal mask.
     *
     * @param mask
     * @throws IllegalArgumentException if the mask is not polygonal
     */
    fun setExtent(mask: Geometry) {
        if (mask !is Polygonal) throw IllegalArgumentException("Only polygonal extents are supported")
        maskPoly = mask
        extent = mask.envelopeInternal
        extentLocator = IndexedPointInAreaLocator(mask)
    }

    override val geometry: Geometry
        get() {
            val pts = arrayOfNulls<Coordinate>(numPts)
            var i = 0
            while (i < numPts) {
                val p = createRandomCoord(extent)
                if (extentLocator != null && !isInExtent(p)) continue
                pts[i++] = p
            }
            return geomFactory.createMultiPointFromCoords(pts.requireNoNulls())
        }

    protected fun isInExtent(p: Coordinate): Boolean {
        return if (extentLocator != null) extentLocator!!.locate(p!!) != Location.EXTERIOR else extent.contains(p)
    }

    override fun createCoord(x: Double, y: Double): Coordinate {
        val pt = Coordinate(x, y)
        geomFactory.precisionModel.makePrecise(pt)
        return pt
    }

    protected fun createRandomCoord(env: Envelope): Coordinate {
        val x: Double = env.minX + env.width * Random.nextDouble()
        val y: Double = env.minY + env.height * Random.nextDouble()
        return createCoord(x, y)
    }
}