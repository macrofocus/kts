/*
 * Copyright (c) 2016 Vivid Solutions.
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.operation.union

import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.util.GeometryCombiner

/**
 * Experimental code to union MultiPolygons
 * with processing limited to the elements which actually interact.
 *
 * Not currently used, since it doesn't seem to offer much of a performance advantage.
 *
 * @author mbdavis
 */
class UnionInteracting(private val g0: Geometry, private val g1: Geometry) {
    private val geomFactory: GeometryFactory = g0.factory
    private val interacts0: BooleanArray
    private val interacts1: BooleanArray

    init {
        interacts0 = BooleanArray(g0.numGeometries)
        interacts1 = BooleanArray(g1.numGeometries)
    }

    fun union(): Geometry? {
        computeInteracting()

        // check for all interacting or none interacting!
        val int0 = extractElements(g0, interacts0, true)
        val int1 = extractElements(g1, interacts1, true)

//		System.out.println(int0);
//		System.out.println(int1);
        /*
		if (int0.isEmpty() || int1.isEmpty()) {
			System.out.println("found empty!");
//			computeInteracting();
		}
		*/
//		if (! int0.isValid()) {
        //System.out.println(int0);
        //throw new RuntimeException("invalid geom!");
//		}
        val union = int0.union(int1)
        val disjoint0 = extractElements(g0, interacts0, false)
        val disjoint1 = extractElements(g1, interacts1, false)
        return GeometryCombiner.combine(union, disjoint0, disjoint1)
    }

    private fun computeInteracting() {
        for (i in 0 until g0.numGeometries) {
            val elem = g0.getGeometryN(i)
            interacts0[i] = computeInteracting(elem)
        }
    }

    private fun computeInteracting(elem0: Geometry): Boolean {
        var interactsWithAny = false
        for (i in 0 until g1.numGeometries) {
            val elem1 = g1.getGeometryN(i)
            val interacts = elem1.envelopeInternal.intersects(elem0.envelopeInternal)
            if (interacts) interacts1[i] = true
            if (interacts) interactsWithAny = true
        }
        return interactsWithAny
    }

    private fun extractElements(
        geom: Geometry,
        interacts: BooleanArray, isInteracting: Boolean
    ): Geometry {
        val extractedGeoms: MutableList<Geometry> = ArrayList()
        for (i in 0 until geom.numGeometries) {
            val elem = geom.getGeometryN(i)
            if (interacts[i] == isInteracting) extractedGeoms.add(elem)
        }
        return geomFactory.buildGeometry(extractedGeoms)
    }

    companion object {
        fun union(g0: Geometry, g1: Geometry): Geometry? {
            val uue = UnionInteracting(g0, g1)
            return uue.union()
        }
    }
}