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

import org.locationtech.jts.algorithm.LineIntersector
import org.locationtech.jts.algorithm.RobustLineIntersector
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.MultiPolygon
import org.locationtech.jts.geomgraph.GeometryGraph
import org.locationtech.jts.operation.relate.EdgeEndBundle
import org.locationtech.jts.operation.relate.RelateNode
import org.locationtech.jts.operation.relate.RelateNodeGraph

/**
 * Checks that a [GeometryGraph] representing an area
 * (a [Polygon] or [MultiPolygon] )
 * has consistent semantics for area geometries.
 * This check is required for any reasonable polygonal model
 * (including the OGC-SFS model, as well as models which allow ring self-intersection at single points)
 *
 * Checks include:
 *
 *  * test for rings which properly intersect
 * (but not for ring self-intersection, or intersections at vertices)
 *  * test for consistent labelling at all node points
 * (this detects vertex intersections with invalid topology,
 * i.e. where the exterior side of an edge lies in the interior of the area)
 *  * test for duplicate rings
 *
 * If an inconsistency is found the location of the problem
 * is recorded and is available to the caller.
 *
 * @version 1.7
 */
class ConsistentAreaTester
/**
 * Creates a new tester for consistent areas.
 *
 * @param geomGraph the topology graph of the area geometry
 */(private val geomGraph: GeometryGraph) {
    private val li: LineIntersector = RobustLineIntersector()
    private val nodeGraph = RelateNodeGraph()

    /**
     * @return the intersection point, or `null` if none was found
     */
    // the intersection point found (if any)
    var invalidPoint: Coordinate? = null
        private set
    /**
     * To fully check validity, it is necessary to
     * compute ALL intersections, including self-intersections within a single edge.
     */
    /**
     * A proper intersection means that the area is not consistent.
     */
    /**
     * Check all nodes to see if their labels are consistent with area topology.
     *
     * @return `true` if this area has a consistent node labelling
     */
    val isNodeConsistentArea: Boolean
        get() {
            /**
             * To fully check validity, it is necessary to
             * compute ALL intersections, including self-intersections within a single edge.
             */
            val intersector = geomGraph.computeSelfNodes(li, true, true)
            /**
             * A proper intersection means that the area is not consistent.
             */
            if (intersector.hasProperIntersection()) {
                invalidPoint = intersector.properIntersectionPoint
                return false
            }
            nodeGraph.build(geomGraph)
            return isNodeEdgeAreaLabelsConsistent
        }

    /**
     * Check all nodes to see if their labels are consistent.
     * If any are not, return false
     *
     * @return `true` if the edge area labels are consistent at this node
     */
    private val isNodeEdgeAreaLabelsConsistent: Boolean
        private get() {
            val nodeIt = nodeGraph.nodeIterator
            while (nodeIt.hasNext()) {
                val node = nodeIt.next() as RelateNode
                if (!node.edges!!.isAreaLabelsConsistent(geomGraph)) {
                    invalidPoint = node.coordinate.copy()
                    return false
                }
            }
            return true
        }

    /**
     * Checks for two duplicate rings in an area.
     * Duplicate rings are rings that are topologically equal
     * (that is, which have the same sequence of points up to point order).
     * If the area is topologically consistent (determined by calling the
     * `isNodeConsistentArea`,
     * duplicate rings can be found by checking for EdgeBundles which contain
     * more than one EdgeEnd.
     * (This is because topologically consistent areas cannot have two rings sharing
     * the same line segment, unless the rings are equal).
     * The start point of one of the equal rings will be placed in
     * invalidPoint.
     *
     * @return true if this area Geometry is topologically consistent but has two duplicate rings
     */
    fun hasDuplicateRings(): Boolean {
        val nodeIt = nodeGraph.nodeIterator
        while (nodeIt.hasNext()) {
            val node = nodeIt.next() as RelateNode
            val i = node.edges!!.iterator()
            while (i.hasNext()) {
                val eeb = i.next() as EdgeEndBundle
                if (eeb.getEdgeEnds().size > 1) {
                    invalidPoint = eeb.edge.getCoordinate(0)
                    return true
                }
            }
        }
        return false
    }
}