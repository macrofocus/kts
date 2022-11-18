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
    private val deList: MutableList<DirectedEdge> = ArrayList()
    private val lowestEdge: DirectedEdge? = null

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
            de!!.ring = this
            de = de!!.next
            Assert.isTrue(de != null, "found null DE in ring")
            Assert.isTrue(de === startDE || !de!!.isInRing, "found DE already in ring")
        } while (de !== startDE)
    }

    /**
     * Adds a [DirectedEdge] which is known to form part of this ring.
     * @param de the [DirectedEdge] to add.
     */
    private fun add(de: DirectedEdge) {
        deList.add(de)
    }

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
        if (holes == null) holes = ArrayList<LinearRing>()
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
                    holeLR[i] = holes!!.get(i) as LinearRing
                }
            }
            return factory.createPolygon(ring, holeLR!!.requireNoNulls())
        }

    /**
     * Tests if the [LinearRing] ring formed by this edge ring is topologically valid.
     *
     * @return true if the ring is valid
     */
    val isValid: Boolean
        get() {
            coordinates
            if (ringPts!!.size <= 3) return false
            getRing()
            return ring!!.isValid
        }

    fun isInRing(pt: Coordinate?): Boolean {
        /**
         * Use an indexed point-in-polygon for performance
         */
        return Location.EXTERIOR != locator!!.locate(pt!!)
        //return PointLocation.isInRing(pt, getCoordinates());
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
                val i: Iterator<DirectedEdge> = deList.iterator()
                while (i.hasNext()) {
                    val de: DirectedEdge = i.next() as DirectedEdge
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
                (deList.get(i).sym as PolygonizeDirectedEdge).ring
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
                (deList.get(i).sym as PolygonizeDirectedEdge).ring!!
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
    internal class EnvelopeComparator : Comparator<Any> {
        override fun compare(obj0: Any, obj1: Any): Int {
            val r0 = obj0 as EdgeRing
            val r1 = obj1 as EdgeRing
            return r0.getRing()!!.getEnvelope().compareTo(r1.getRing()!!.getEnvelope())
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
         * @return containing EdgeRing, if there is one
         * or null if no containing EdgeRing is found
         */
        fun findEdgeRingContaining(testEr: EdgeRing, erList: List<EdgeRing>): EdgeRing? {
            val testRing = testEr.getRing()
            val testEnv = testRing!!.envelopeInternal
            var testPt: Coordinate? = testRing.getCoordinateN(0)
            var minRing: EdgeRing? = null
            var minRingEnv: Envelope? = null
            val it: Iterator<*> = erList.iterator()
            while (it.hasNext()) {
                val tryEdgeRing = it.next() as EdgeRing
                val tryRing = tryEdgeRing.getRing()
                val tryShellEnv = tryRing!!.envelopeInternal
                // the hole envelope cannot equal the shell envelope
                // (also guards against testing rings against themselves)
                if (tryShellEnv.equals(testEnv)) {
                    continue
                }

                // hole must be contained in shell
                if (!tryShellEnv.contains(testEnv)) {
                    continue
                }
                testPt = CoordinateArrays.ptNotInList(testRing.coordinates, tryEdgeRing.coordinates)
                /**
                 * If testPt is null it indicates that the hole is exactly surrounded by the tryShell.
                 * This should not happen for fully noded/dissolved linework.
                 * For now just ignore this hole and continue - this should produce
                 * "best effort" output.
                 * In futher could flag this as an error (invalid ring).
                 */
                if (testPt == null) {
                    continue
                }
                val isContained = tryEdgeRing.isInRing(testPt)

                // check if the new containing ring is smaller than the current minimum ring
                if (isContained) {
                    if (minRing == null
                        || minRingEnv!!.contains(tryShellEnv)
                    ) {
                        minRing = tryEdgeRing
                        minRingEnv = minRing.getRing()!!.envelopeInternal
                    }
                }
            }
            return minRing
        }

        /**
         * Traverses a ring of DirectedEdges, accumulating them into a list.
         * This assumes that all dangling directed edges have been removed
         * from the graph, so that there is always a next dirEdge.
         *
         * @param startDE the DirectedEdge to start traversing at
         * @return a List of DirectedEdges that form a ring
         */
        fun findDirEdgesInRing(startDE: PolygonizeDirectedEdge): MutableList<DirectedEdge> {
            var de: PolygonizeDirectedEdge? = startDE
            val edges: MutableList<DirectedEdge> = ArrayList<DirectedEdge>()
            do {
                edges.add(de!!)
                de = de!!.next
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