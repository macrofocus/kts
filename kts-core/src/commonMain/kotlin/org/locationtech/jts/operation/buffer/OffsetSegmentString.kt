/*
 * Copyright (c) 2016 Martin Davis.
 * Copyright (c) 2020 Macrofocus GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.operation.buffer

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel

/**
 * A dynamic list of the vertices in a constructed offset curve.
 * Automatically removes adjacent vertices
 * which are closer than a given tolerance.
 *
 * @author Martin Davis
 * @author Luc Girardin
 */
internal class OffsetSegmentString {
    private val ptList: ArrayList<Any?> = ArrayList()
    private var precisionModel: PrecisionModel? = null

    /**
     * The distance below which two adjacent points on the curve
     * are considered to be coincident.
     * This is chosen to be a small fraction of the offset distance.
     */
    private var minimimVertexDistance = 0.0
    fun setPrecisionModel(precisionModel: PrecisionModel?) {
        this.precisionModel = precisionModel
    }

    fun setMinimumVertexDistance(minimimVertexDistance: Double) {
        this.minimimVertexDistance = minimimVertexDistance
    }

    fun addPt(pt: Coordinate?) {
        val bufPt = Coordinate(pt!!)
        precisionModel!!.makePrecise(bufPt)
        // don't add duplicate (or near-duplicate) points
        if (isRedundant(bufPt)) return
        ptList.add(bufPt)
        //System.out.println(bufPt);
    }

    fun addPts(pt: Array<Coordinate>, isForward: Boolean) {
        if (isForward) {
            for (i in pt.indices) {
                addPt(pt[i])
            }
        } else {
            for (i in pt.indices.reversed()) {
                addPt(pt[i])
            }
        }
    }

    /**
     * Tests whether the given point is redundant
     * relative to the previous
     * point in the list (up to tolerance).
     *
     * @param pt
     * @return true if the point is redundant
     */
    private fun isRedundant(pt: Coordinate): Boolean {
        if (ptList.size < 1) return false
        val lastPt = ptList[ptList.size - 1] as Coordinate
        val ptDist = pt.distance(lastPt)
        return ptDist < minimimVertexDistance
    }

    fun closeRing() {
        if (ptList.size < 1) return
        val startPt = Coordinate((ptList[0] as Coordinate))
        val lastPt = ptList[ptList.size - 1] as Coordinate
        if (startPt == lastPt) return
        ptList.add(startPt)
    }

    fun reverse() {}

    /*
     // check that points are a ring - add the startpoint again if they are not
   if (ptList.size() > 1) {
      Coordinate start  = (Coordinate) ptList.get(0);
      Coordinate end    = (Coordinate) ptList.get(ptList.size() - 1);
      if (! start.equals(end) ) addPt(start);
    }
    */
    val coordinates: Array<Coordinate>
        get() =/*
     // check that points are a ring - add the startpoint again if they are not
   if (ptList.size() > 1) {
      Coordinate start  = (Coordinate) ptList.get(0);
      Coordinate end    = (Coordinate) ptList.get(ptList.size() - 1);
      if (! start.equals(end) ) addPt(start);
    }
    */
            ptList.map { it as Coordinate }.toTypedArray()

    override fun toString(): String {
        val fact = GeometryFactory()
        val line = fact.createLineString(coordinates)
        return line.toString()
    }

    companion object {
        private val COORDINATE_ARRAY_TYPE = arrayOfNulls<Coordinate>(0)
    }

}