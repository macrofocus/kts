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
package org.locationtech.jts.operation.valid

import org.locationtech.jts.algorithm.PointLocation.isInRing
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.LinearRing
import org.locationtech.jts.geomgraph.GeometryGraph
import org.locationtech.jts.index.SpatialIndex
import org.locationtech.jts.index.strtree.STRtree
import org.locationtech.jts.operation.valid.IsValidOp.Companion.findPtNotNode

/**
 * Tests whether any of a set of [LinearRing]s are
 * nested inside another ring in the set, using a spatial
 * index to speed up the comparisons.
 *
 * @version 1.7
 */
class IndexedNestedRingTester(  // used to find non-node vertices
    private val graph: GeometryGraph
) {
    private val rings: MutableList<Any?> = ArrayList()
    private val totalEnv = Envelope()
    private var index: SpatialIndex? = null
    var nestedPoint: Coordinate? = null
        private set

    fun add(ring: LinearRing) {
        rings.add(ring)
        totalEnv.expandToInclude(ring.envelopeInternal)
    }

    /**
     * If no non-node pts can be found, this means
     * that the searchRing touches ALL of the innerRing vertices.
     * This indicates an invalid polygon, since either
     * the two holes create a disconnected interior,
     * or they touch in an infinite number of points
     * (i.e. along a line segment).
     * Both of these cases are caught by other tests,
     * so it is safe to simply skip this situation here.
     */
    //System.out.println(results.size());
    val isNonNested: Boolean
        get() {
            buildIndex()
            for (i in rings.indices) {
                val innerRing = rings[i] as LinearRing
                val innerRingPts = innerRing.coordinates
                val results = index!!.query(innerRing.envelopeInternal)
                //System.out.println(results.size());
                for (j in results.indices) {
                    val searchRing = results[j] as LinearRing
                    val searchRingPts = searchRing.coordinates
                    if (innerRing === searchRing) continue
                    if (!innerRing.envelopeInternal.intersects(searchRing.envelopeInternal)) continue
                    val innerRingPt = findPtNotNode(innerRingPts, searchRing, graph) ?:
                    /**
                     * If no non-node pts can be found, this means
                     * that the searchRing touches ALL of the innerRing vertices.
                     * This indicates an invalid polygon, since either
                     * the two holes create a disconnected interior,
                     * or they touch in an infinite number of points
                     * (i.e. along a line segment).
                     * Both of these cases are caught by other tests,
                     * so it is safe to simply skip this situation here.
                     */
                    continue

                    /**
                     * If no non-node pts can be found, this means
                     * that the searchRing touches ALL of the innerRing vertices.
                     * This indicates an invalid polygon, since either
                     * the two holes create a disconnected interior,
                     * or they touch in an infinite number of points
                     * (i.e. along a line segment).
                     * Both of these cases are caught by other tests,
                     * so it is safe to simply skip this situation here.
                     */
                    val isInside = isInRing(innerRingPt, searchRingPts)
                    if (isInside) {
                        nestedPoint = innerRingPt
                        return false
                    }
                }
            }
            return true
        }

    /**
     * An implementation of an optimization introduced in GEOS
     * https://github.com/libgeos/geos/pull/255/commits/1bf16cdf5a4827b483a1f712e0597ccb243f58cb
     *
     * Not used for now, since improvement is small and very data-dependent.
     *
     * @return
     */
    /*
  private boolean isNonNestedWithIndex()
  {
    buildIndex();

    for (int i = 0; i < rings.size(); i++) {
      LinearRing outerRing = (LinearRing) rings.get(i);
      Coordinate[] outerRingPts = outerRing.getCoordinates();

      IndexedPointInAreaLocator ptLocator = new IndexedPointInAreaLocator(outerRing);
      List results = index.query(outerRing.getEnvelopeInternal());
//System.out.println(results.size());
      for (int j = 0; j < results.size(); j++) {
        LinearRing searchRing = (LinearRing) results.get(j);
        if (outerRing == searchRing)
          continue;
        
        if (! outerRing.getEnvelopeInternal().intersects(searchRing.getEnvelopeInternal()))
          continue;

        Coordinate[] searchRingPts = searchRing.getCoordinates();
        Coordinate innerRingPt = IsValidOp.findPtNotNode(searchRingPts, outerRing, graph);
        
        if (innerRingPt == null)
          continue;

        boolean isInside = Location.EXTERIOR != ptLocator.locate(innerRingPt);
        //boolean isInside = PointLocation.isInRing(innerRingPt, outerRingPts);
        
        if (isInside) {
          nestedPt = innerRingPt;
          return false;
        }
      }
    }
    return true;
  }
  */
    private fun buildIndex() {
        index = STRtree()
        for (i in rings.indices) {
            val ring = rings[i] as LinearRing
            val env = ring.envelopeInternal
            index!!.insert(env, ring)
        }
    }
}