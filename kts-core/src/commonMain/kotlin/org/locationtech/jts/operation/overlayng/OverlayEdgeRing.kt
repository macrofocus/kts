/*
 * Copyright (c) 2019 Martin Davis.
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
package org.locationtech.jts.operation.overlayng

import org.locationtech.jts.algorithm.Orientation.isCCW
import org.locationtech.jts.algorithm.locate.IndexedPointInAreaLocator
import org.locationtech.jts.geom.*

internal class OverlayEdgeRing(
    start: OverlayEdge,
    geometryFactory: GeometryFactory
) {
    private val startEdge: OverlayEdge?
    var ring: LinearRing? = null
        private set

    /**
     * Tests whether this ring is a hole.
     * @return `true` if this ring is a hole
     */
    var isHole = false
        private set

    private val envelope: Envelope
        get() = ring!!.envelopeInternal

    /**
     * Computes the list of coordinates which are contained in this ring.
     * The coordinates are computed once only and cached.
     *
     * @return an array of the [Coordinate]s in this ring
     */
    private val coordinates: Array<Coordinate>
    private var locator: IndexedPointInAreaLocator? = null
        private get() {
            if (field == null) {
                field = IndexedPointInAreaLocator(ring)
            }
            return field
        }
    private var shell: OverlayEdgeRing? = null
    private val holes: MutableList<OverlayEdgeRing> =
        ArrayList() // a list of EdgeRings which are holes in this EdgeRing

    init {
        startEdge = start
        coordinates = computeRingPts(start)
        computeRing(coordinates, geometryFactory)
    }

    /**
     * Sets the containing shell ring of a ring that has been determined to be a hole.
     *
     * @param shell the shell ring
     */
    fun setShell(shell: OverlayEdgeRing?) {
        this.shell = shell
        shell?.addHole(this)
    }

    /**
     * Tests whether this ring has a shell assigned to it.
     *
     * @return true if the ring has a shell
     */
    fun hasShell(): Boolean {
        return shell != null
    }

    /**
     * Gets the shell for this ring.  The shell is the ring itself if it is not a hole, otherwise its parent shell.
     *
     * @return the shell for this ring
     */
    fun getShell(): OverlayEdgeRing? {
        return if (isHole) shell else this
    }

    fun addHole(ring: OverlayEdgeRing) {
        holes.add(ring)
    }

    private fun computeRingPts(start: OverlayEdge): Array<Coordinate> {
        var edge: OverlayEdge = start
        val pts = CoordinateList()
        do {
            if (edge.edgeRing === this) throw TopologyException(
                "Edge visited twice during ring-building at " + edge.coordinate,
                edge.coordinate
            )

            //edges.add(de);
//Debug.println(de);
//Debug.println(de.getEdge());

            // only valid for polygonal output
            //Assert.isTrue(edge.getLabel().isBoundaryEither());
            edge.addCoordinates(pts)
            edge.edgeRing = this
            if (edge.nextResult() == null) throw TopologyException("Found null edge in ring", edge.dest())
            edge = edge.nextResult()!!
        } while (edge !== start)
        pts.closeRing()
        return pts.toCoordinateArray()
    }

    private fun computeRing(ringPts: Array<Coordinate>, geometryFactory: GeometryFactory) {
        if (ring != null) return  // don't compute more than once
        ring = geometryFactory.createLinearRing(ringPts)
        isHole = isCCW(ring!!.coordinates)
    }

    /**
     * Finds the innermost enclosing shell OverlayEdgeRing
     * containing this OverlayEdgeRing, if any.
     * The innermost enclosing ring is the *smallest* enclosing ring.
     * The algorithm used depends on the fact that:
     * <br></br>
     * ring A contains ring B if envelope(ring A) contains envelope(ring B)
     * <br></br>
     * This routine is only safe to use if the chosen point of the hole
     * is known to be properly contained in a shell
     * (which is guaranteed to be the case if the hole does not touch its shell)
     *
     * To improve performance of this function the caller should
     * make the passed shellList as small as possible (e.g.
     * by using a spatial index filter beforehand).
     *
     * @return containing EdgeRing or null if no containing EdgeRing is found
     */
    fun findEdgeRingContaining(erList: List<OverlayEdgeRing>): OverlayEdgeRing? {
        var minContainingRing: OverlayEdgeRing? = null
        for (edgeRing in erList) {
            if (edgeRing.contains(this)) {
                if (minContainingRing == null
                    || minContainingRing.envelope.contains(edgeRing.envelope)
                ) {
                    minContainingRing = edgeRing
                }
            }
        }
        return minContainingRing
    }

    fun locate(pt: Coordinate): Int {
        /**
         * Use an indexed point-in-polygon for performance
         */
        return locator!!.locate(pt)
    }

    /**
     * Tests if an edgeRing is properly contained in this ring.
     * Relies on property that edgeRings never overlap (although they may
     * touch at single vertices).
     *
     * @param ring ring to test
     * @return true if ring is properly contained
     */
    private fun contains(ring: OverlayEdgeRing): Boolean {
        // the test envelope must be properly contained
        // (guards against testing rings against themselves)
        val env = envelope
        val testEnv = ring.envelope
        if (!env.containsProperly(testEnv)) return false
        return isPointInOrOut(ring)
    }

    private fun isPointInOrOut(ring: OverlayEdgeRing): Boolean {
        // in most cases only one or two points will be checked
        for (pt in ring.coordinates) {
            val loc = locate(pt)
            if (loc == Location.INTERIOR) {
                return true
            }
            if (loc == Location.EXTERIOR) {
                return false
            }
            // pt is on BOUNDARY, so keep checking for a determining location
        }
        return false
    }

    val coordinate: Coordinate
        get() = coordinates[0]

    /**
     * Computes the [Polygon] formed by this ring and any contained holes.
     *
     * @return the [Polygon] formed by this ring and its holes.
     */
    fun toPolygon(factory: GeometryFactory): Polygon {
        var holeLR: Array<LinearRing?>? = null
        if (holes != null) {
            holeLR = arrayOfNulls(holes.size)
            for (i in holes.indices) {
                holeLR[i] = holes[i].ring
            }
        }
        return factory.createPolygon(ring, holeLR?.requireNoNulls())
    }

    val edge: OverlayEdge?
        get() = startEdge
}