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
package org.locationtech.jts.operation.polygonize

import org.locationtech.jts.algorithm.Orientation
import org.locationtech.jts.algorithm.locate.IndexedPointInAreaLocator
import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.impl.CoordinateArraySequence
import org.locationtech.jts.io.WKTWriter
import org.locationtech.jts.planargraph.DirectedEdge
import org.locationtech.jts.util.Assert

/**
 * Represents a ring of [PolygonizeDirectedEdge]s which form
 * a ring of a polygon.  The ring may be either an outer shell or a hole.
 *
 * @version 1.7
 */
class EdgeRing(private val factory: GeometryFactory) {
    private val deList: MutableList<PolygonizeDirectedEdge> = ArrayList()

    // cache the following data for efficiency
    private var ring: LinearRing? = null
    private var locator: IndexedPointInAreaLocator? = null
        private get() {
            if (field == null) {
                field = IndexedPointInAreaLocator(getRing())
            }
            return field
        }
    private var ringPts: Array<Coordinate>? = null
    private var holes: MutableList<LinearRing>? = null
    private var shell: EdgeRing? = null

    /**
     * Tests whether this ring is a hole.
     * @return `true` if this ring is a hole
     */
    var isHole = false
        private set

    /**
     * Tests if the [LinearRing] ring formed by this edge ring is topologically valid.
     * Must be called after [computeValid].
     *
     * @return true if the ring is valid
     */
    var isValid = false
        private set
    /**
     * @return whether the ring has been processed
     */
    /**
     * @param isProcessed whether the ring has been processed
     */
    var isProcessed = false
    var isIncludedSet = false
        private set
    var isIncluded = false
        set(isIncluded) {
            field = isIncluded
            isIncludedSet = true
        }

    fun build(startDE: PolygonizeDirectedEdge) {
        var de: PolygonizeDirectedEdge? = startDE
        do {
            add(de!!)
            de.ring = this
            de = de.next
            Assert.isTrue(de != null, "found null DE in ring")
            Assert.isTrue(de === startDE || !de!!.isInRing, "found DE already in ring")
        } while (de !== startDE)
    }

    /**
     * Adds a [DirectedEdge] which is known to form part of this ring.
     * @param de the [DirectedEdge] to add.
     */
    private fun add(de: DirectedEdge) {
        deList.add(de as PolygonizeDirectedEdge)
    }

    val edges: List<PolygonizeDirectedEdge>
        get() = deList

    /**
     * Computes whether this ring is a hole.
     * Due to the way the edges in the polygonization graph are linked,
     * a ring is a hole if it is oriented counter-clockwise.
     */
    fun computeHole() {
        val ring = getRing()
        isHole = Orientation.isCCW(ring!!.coordinates)
    }

    /**
     * Adds a hole to the polygon formed by this ring.
     * @param hole the [LinearRing] forming the hole.
     */
    fun addHole(hole: LinearRing) {
        if (holes == null) holes = ArrayList()
        holes!!.add(hole)
    }

    /**
     * Adds a hole to the polygon formed by this ring.
     * @param holeER the [LinearRing] forming the hole.
     */
    fun addHole(holeER: EdgeRing) {
        holeER.setShell(this)
        val hole = holeER.getRing()
        if (holes == null) holes = ArrayList()
        holes!!.add(hole!!)
    }

    /**
     * Computes the [Polygon] formed by this ring and any contained holes.
     *
     * @return the [Polygon] formed by this ring and its holes.
     */
    val polygon: Polygon
        get() {
            var holeLR: Array<LinearRing?>? = null
            if (holes != null) {
                holeLR = arrayOfNulls(holes!!.size)
                for (i in holes!!.indices) {
                    holeLR[i] = holes!![i]
                }
            }
            return factory.createPolygon(ring, holeLR?.requireNoNulls())
        }

    /**
     * Computes the validity of the ring.
     * Must be called prior to calling [isValid].
     */
    fun computeValid() {
        coordinates
        if (ringPts!!.size <= 3) {
            isValid = false
            return
        }
        getRing()
        isValid = ring!!.isValid
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
    private fun contains(ring: EdgeRing): Boolean {
        // the test envelope must be properly contained
        // (guards against testing rings against themselves)
        val env = envelope
        val testEnv = ring.envelope
        if (!env.containsProperly(testEnv)) return false
        return isPointInOrOut(ring)
    }

    private fun isPointInOrOut(ring: EdgeRing): Boolean {
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

    /**
     * Computes the list of coordinates which are contained in this ring.
     * The coordinates are computed once only and cached.
     *
     * @return an array of the [Coordinate]s in this ring
     */
    private val coordinates: Array<Coordinate>
        private get() {
            if (ringPts == null) {
                val coordList = CoordinateList()
                for (de in deList) {
                    val edge = de.edge as PolygonizeEdge
                    addEdge(edge.line.coordinates, de.edgeDirection, coordList)
                }
                ringPts = coordList.toCoordinateArray()
            }
            return ringPts!!
        }

    /**
     * Gets the coordinates for this ring as a [LineString].
     * Used to return the coordinates in this ring
     * as a valid geometry, when it has been detected that the ring is topologically
     * invalid.
     * @return a [LineString] containing the coordinates in this ring
     */
    val lineString: LineString
        get() {
            coordinates
            return factory.createLineString(ringPts)
        }

    /**
     * Returns this ring as a [LinearRing], or null if an Exception occurs while
     * creating it (such as a topology problem).
     */
    fun getRing(): LinearRing? {
        if (ring != null) return ring
        coordinates
        //if (ringPts.length < 3) System.out.println(ringPts);
        try {
            ring = factory.createLinearRing(ringPts!!)
        } catch (ex: Exception) {
            //System.out.println(ringPts);
        }
        return ring
    }

    private val envelope: Envelope
        get() = getRing()!!.envelopeInternal

    /**
     * Sets the containing shell ring of a ring that has been determined to be a hole.
     *
     * @param shell the shell ring
     */
    fun setShell(shell: EdgeRing?) {
        this.shell = shell
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
    fun getShell(): EdgeRing? {
        return if (isHole) shell else this
    }

    /**
     * Tests whether this ring is an outer hole.
     * A hole is an outer hole if it is not contained by a shell.
     *
     * @return true if the ring is an outer hole.
     */
    val isOuterHole: Boolean
        get() = if (!isHole) false else !hasShell()

    /**
     * Tests whether this ring is an outer shell.
     *
     * @return true if the ring is an outer shell.
     */
    val isOuterShell: Boolean
        get() = getOuterHole() != null

    /**
     * Gets the outer hole of a shell, if it has one.
     * An outer hole is one that is not contained
     * in any other shell.
     * Each disjoint connected group of shells
     * is surrounded by an outer hole.
     *
     * @return the outer hole edge ring, or null
     */
    fun getOuterHole(): EdgeRing? {
        /*
     * Only shells can have outer holes
     */
        if (isHole) return null
        /*
     * A shell is an outer shell if any edge is also in an outer hole.
     * A hole is an outer hole if it is not contained by a shell.
     */for (i in deList.indices) {
            val adjRing: EdgeRing? =
                (deList[i].sym as PolygonizeDirectedEdge).ring
            if (adjRing!!.isOuterHole) return adjRing
        }
        return null
    }

    /**
     * Updates the included status for currently non-included shells
     * based on whether they are adjacent to an included shell.
     */
    fun updateIncluded() {
        if (isHole) return
        for (i in deList.indices) {
            val adjShell: EdgeRing? =
                (deList[i].sym as PolygonizeDirectedEdge).ring!!
                    .getShell()
            if (adjShell != null && adjShell.isIncludedSet) {
                // adjacent ring has been processed, so set included to inverse of adjacent included
                isIncluded = !adjShell.isIncluded
                return
            }
        }
    }

    /**
     * Gets a string representation of this object.
     *
     * @return a string representing the object
     */
    override fun toString(): String {
        return WKTWriter.toLineString(
            CoordinateArraySequence(
                coordinates
            )
        )
    }

    /**
     * Compares EdgeRings based on their envelope,
     * using the standard lexicographic ordering.
     * This ordering is sufficient to make edge ring sorting deterministic.
     *
     * @author mbdavis
     */
    internal class EnvelopeComparator : Comparator<EdgeRing> {
        override fun compare(r0: EdgeRing, r1: EdgeRing): Int {
            return r0.getRing()!!.getEnvelope().compareTo(r1.getRing()!!.getEnvelope())
        }
    }

    /**
     * Compares EdgeRings based on the area of their envelopes.
     * Smaller envelopes sort before bigger ones.
     * This effectively sorts EdgeRings in order of containment.
     *
     * @author mdavis
     */
    internal class EnvelopeAreaComparator : Comparator<EdgeRing> {
        override fun compare(r0: EdgeRing, r1: EdgeRing): Int {
            return r0.getRing()!!.getEnvelope().area.compareTo(
                r1.getRing()!!.getEnvelope().area
            )
        }
    }

    companion object {
        /**
         * Find the innermost enclosing shell EdgeRing containing the argument EdgeRing, if any.
         * The innermost enclosing ring is the *smallest* enclosing ring.
         * The algorithm used depends on the fact that:
         * <br></br>
         * ring A contains ring B if envelope(ring A) contains envelope(ring B)
         * <br></br>
         * This routine is only safe to use if the chosen point of the hole
         * is known to be properly contained in a shell
         * (which is guaranteed to be the case if the hole does not touch its shell)
         *
         *
         * To improve performance of this function the caller should
         * make the passed shellList as small as possible (e.g.
         * by using a spatial index filter beforehand).
         *
         * @return containing EdgeRing, or null if no containing EdgeRing is found
         */
        fun findEdgeRingContaining(testEr: EdgeRing, erList: List<EdgeRing>): EdgeRing? {
            var minContainingRing: EdgeRing? = null
            for (edgeRing in erList) {
                if (edgeRing.contains(testEr)) {
                    if (minContainingRing == null
                        || minContainingRing.envelope.contains(edgeRing.envelope)
                    ) {
                        minContainingRing = edgeRing
                    }
                }
            }
            return minContainingRing
        }

        /**
         * Traverses a ring of DirectedEdges, accumulating them into a list.
         * This assumes that all dangling directed edges have been removed
         * from the graph, so that there is always a next dirEdge.
         *
         * @param startDE the DirectedEdge to start traversing at
         * @return a List of DirectedEdges that form a ring
         */
        fun findDirEdgesInRing(startDE: PolygonizeDirectedEdge): MutableList<PolygonizeDirectedEdge> {
            var de: PolygonizeDirectedEdge? = startDE
            val edges: MutableList<PolygonizeDirectedEdge> = ArrayList()
            do {
                edges.add(de!!)
                de = de.next
                Assert.isTrue(de != null, "found null DE in ring")
                Assert.isTrue(de === startDE || !de!!.isInRing, "found DE already in ring")
            } while (de !== startDE)
            return edges
        }

        private fun addEdge(coords: Array<Coordinate>, isForward: Boolean, coordList: CoordinateList) {
            if (isForward) {
                for (i in coords.indices) {
                    coordList.add(coords[i], false)
                }
            } else {
                for (i in coords.indices.reversed()) {
                    coordList.add(coords[i], false)
                }
            }
        }
    }
}