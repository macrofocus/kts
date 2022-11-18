/*
 * Copyright (c) 2021 Martin Davis.
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
package org.locationtech.jts.triangulate.polygon

import org.locationtech.jts.geom.*
import org.locationtech.jts.legacy.Math.abs
import org.locationtech.jts.legacy.TreeSet
import org.locationtech.jts.noding.*

/**
 * Transforms a polygon with holes into a single self-touching (invalid) ring
 * by joining holes to the exterior shell or to another hole.
 * The holes are added from the lowest upwards.
 * As the resulting shell develops, a hole might be added to what was
 * originally another hole.
 *
 * There is no attempt to optimize the quality of the join lines.
 * In particular, a hole which already touches at a vertex may be
 * joined at a different vertex.
 */
class PolygonHoleJoiner(private val inputPolygon: Polygon) {
    private var shellCoords: MutableList<Coordinate>? = null

    // a sorted copy of shellCoords
    private var shellCoordsSorted: TreeSet<Coordinate>? = null

    // Key: starting end of the cut; Value: list of the other end of the cut
    private var cutMap: HashMap<Coordinate, ArrayList<Coordinate>>? = null
    private val polygonIntersector: SegmentSetMutualIntersector = createPolygonIntersector(
        inputPolygon
    )

    /**
     * Computes the joined ring.
     *
     * @return the points in the joined ring
     */
    fun compute(): Array<Coordinate> {
        //--- copy the input polygon shell coords
        shellCoords = ringCoordinates(inputPolygon.exteriorRing!!)
        if (inputPolygon.getNumInteriorRing() != 0) {
            joinHoles()
        }
        return shellCoords!!.toTypedArray()
    }

    private fun joinHoles() {
        shellCoordsSorted = TreeSet<Coordinate>()
        shellCoordsSorted!!.addAll(shellCoords!!)
        cutMap = HashMap<Coordinate, ArrayList<Coordinate>>()
        val orderedHoles = sortHoles(
            inputPolygon
        )
        for (i in orderedHoles.indices) {
            joinHole(orderedHoles[i])
        }
    }

    /**
     * Joins a single hole to the current shellRing.
     *
     * @param hole the hole to join
     */
    private fun joinHole(hole: LinearRing) {
        /**
         * 1) Get a list of HoleVertex Index.
         * 2) Get a list of ShellVertex.
         * 3) Get the pair that has the shortest distance between them.
         * This pair is the endpoints of the cut
         * 4) The selected ShellVertex may occurs multiple times in
         * shellCoords[], so find the proper one and add the hole after it.
         */
        val holeCoords: Array<Coordinate> = hole.coordinates
        val holeLeftVerticesIndex = findLeftVertices(hole)
        val holeCoord = holeCoords[holeLeftVerticesIndex[0]]
        val shellCoordsList = findLeftShellVertices(holeCoord)
        var shellCoord = shellCoordsList[0]
        var shortestHoleVertexIndex = 0
        //--- pick the shell-hole vertex pair that gives the shortest distance
        if (abs(shellCoord.x - holeCoord.x) < EPS) {
            var shortest = Double.MAX_VALUE
            for (i in holeLeftVerticesIndex.indices) {
                for (j in shellCoordsList.indices) {
                    val currLength: Double =
                        abs(shellCoordsList[j].y - holeCoords[holeLeftVerticesIndex[i]].y)
                    if (currLength < shortest) {
                        shortest = currLength
                        shortestHoleVertexIndex = i
                        shellCoord = shellCoordsList[j]
                    }
                }
            }
        }
        val shellVertexIndex = getShellCoordIndex(
            shellCoord,
            holeCoords[holeLeftVerticesIndex[shortestHoleVertexIndex]]
        )
        addHoleToShell(shellVertexIndex, holeCoords, holeLeftVerticesIndex[shortestHoleVertexIndex])
    }

    /**
     * Get the ith shellvertex in shellCoords[] that the current should add after
     *
     * @param shellVertex Coordinate of the shell vertex
     * @param holeVertex  Coordinate of the hole vertex
     * @return the ith shellvertex
     */
    private fun getShellCoordIndex(shellVertex: Coordinate, holeVertex: Coordinate): Int {
        var numSkip = 0
        val newValueList: ArrayList<Coordinate> = ArrayList<Coordinate>()
        newValueList.add(holeVertex)
        if (cutMap!!.containsKey(shellVertex)) {
            for (coord in cutMap!![shellVertex]!!) {
                if (coord.y < holeVertex.y) {
                    numSkip++
                }
            }
            cutMap!![shellVertex]!!.add(holeVertex)
        } else {
            cutMap!!.put(shellVertex, newValueList)
        }
        if (!cutMap!!.containsKey(holeVertex)) {
            cutMap!!.put(holeVertex, ArrayList<Coordinate>(newValueList))
        }
        return getShellCoordIndexSkip(shellVertex, numSkip)
    }

    /**
     * Find the index of the coordinate in ShellCoords ArrayList,
     * skipping over some number of matches
     *
     * @param coord
     * @return
     */
    private fun getShellCoordIndexSkip(coord: Coordinate, numSkip: Int): Int {
        var numSkip = numSkip
        for (i in shellCoords!!.indices) {
            if (shellCoords!![i].equals2D(coord, EPS)) {
                if (numSkip == 0) return i
                numSkip--
            }
        }
        throw IllegalStateException("Vertex is not in shellcoords")
    }

    /**
     * Gets a list of shell vertices that could be used to join with the hole.
     * This list contains only one item if the chosen vertex does not share the same
     * x value with holeCoord
     *
     * @param holeCoord the hole coordinates
     * @return a list of candidate join vertices
     */
    private fun findLeftShellVertices(holeCoord: Coordinate): List<Coordinate> {
        val list: ArrayList<Coordinate> = ArrayList<Coordinate>()
        var closest: Coordinate = shellCoordsSorted!!.higher(holeCoord)
        while (closest.x == holeCoord.x) {
            closest = shellCoordsSorted!!.higher(closest)
        }
        do {
            closest = shellCoordsSorted!!.lower(closest)
        } while (!isJoinable(holeCoord, closest) && closest != shellCoordsSorted!!.first())
        list.add(closest)
        if (closest.x != holeCoord.x) return list
        val chosenX = closest.x
        list.clear()
        while (chosenX == closest.x) {
            list.add(closest)
            closest = shellCoordsSorted!!.lower(closest)
            if (closest == null) return list
        }
        return list
    }

    /**
     * Determine if a line segment between a hole vertex
     * and a shell vertex lies inside the input polygon.
     *
     * @param holeCoord a hole coordinate
     * @param shellCoord a shell coordinate
     * @return true if the line lies inside the polygon
     */
    private fun isJoinable(
        holeCoord: Coordinate,
        shellCoord: Coordinate
    ): Boolean {
        /*
    //--- slow code for testing only
    LineString join = geomFact.createLineString(new Coordinate[] { holeCoord, shellCoord });
    boolean isJoinableSlow = inputPolygon.covers(join)
    if (isJoinableSlow != isJoinable) {
      System.out.println(WKTWriter.toLineString(holeCoord, shellCoord));
    }
    //Assert.isTrue(isJoinableSlow == isJoinable);
    */return !crossesPolygon(holeCoord, shellCoord)
    }

    /**
     * Tests whether a line segment crosses the polygon boundary.
     *
     * @param p0 a vertex
     * @param p1 a vertex
     * @return true if the line segment crosses the polygon boundary
     */
    private fun crossesPolygon(p0: Coordinate, p1: Coordinate): Boolean {
        val segString: SegmentString = BasicSegmentString(arrayOf(p0, p1), null)
        val segStrings: MutableList<SegmentString> = ArrayList<SegmentString>()
        segStrings.add(segString)
        val segInt = SegmentIntersectionDetector()
        segInt.setFindProper(true)
        polygonIntersector.process(segStrings, segInt)
        return segInt.hasProperIntersection()
    }

    /**
     * Add hole vertices at proper position in shell vertex list.
     * For a touching/zero-length join line, avoids adding the join vertices twice.
     *
     * Also adds hole points to ordered coordinates.
     *
     * @param shellJoinIndex index of join vertex in shell
     * @param holeCoords the vertices of the hole to be inserted
     * @param holeJoinIndex index of join vertex in hole
     */
    private fun addHoleToShell(shellJoinIndex: Int, holeCoords: Array<Coordinate>, holeJoinIndex: Int) {
        val shellJoinPt = shellCoords!![shellJoinIndex]
        val holeJoinPt = holeCoords[holeJoinIndex]
        //-- check for touching (zero-length) join to avoid inserting duplicate vertices
        val isJoinTouching = shellJoinPt.equals2D(holeJoinPt)

        //-- create new section of vertices to insert in shell
        val newSection: MutableList<Coordinate> = ArrayList<Coordinate>()
        if (!isJoinTouching) {
            newSection.add(Coordinate(shellJoinPt))
        }
        val nPts = holeCoords.size - 1
        var i = holeJoinIndex
        do {
            newSection.add(Coordinate(holeCoords[i]))
            i = (i + 1) % nPts
        } while (i != holeJoinIndex)
        if (!isJoinTouching) {
            newSection.add(Coordinate(holeCoords[holeJoinIndex]))
        }
        shellCoords!!.addAll(shellJoinIndex, newSection)
        shellCoordsSorted!!.addAll(newSection)
    }

    /**
     *
     * @author mdavis
     */
    private class EnvelopeComparator : Comparator<Geometry> {
        override fun compare(o1: Geometry, o2: Geometry): Int {
            val e1: Envelope = o1.envelopeInternal
            val e2: Envelope = o2.envelopeInternal
            return e1.compareTo(e2)
        }
    }

    companion object {
        fun joinAsPolygon(inputPolygon: Polygon): Polygon {
            return inputPolygon.factory.createPolygon(join(inputPolygon))
        }

        fun join(inputPolygon: Polygon): Array<Coordinate> {
            val joiner = PolygonHoleJoiner(inputPolygon)
            return joiner.compute()
        }

        private const val EPS = 1.0E-4
        private fun ringCoordinates(ring: LinearRing): MutableList<Coordinate> {
            val coords: Array<Coordinate> = ring.coordinates
            val coordList: MutableList<Coordinate> = ArrayList<Coordinate>()
            for (p in coords) {
                coordList.add(p)
            }
            return coordList
        }

        /**
         * Sort the hole rings by minimum X, minimum Y.
         *
         * @param poly polygon that contains the holes
         * @return a list of sorted hole rings
         */
        private fun sortHoles(poly: Polygon): List<LinearRing> {
            val holes: MutableList<LinearRing> = ArrayList<LinearRing>()
            for (i in 0 until poly.getNumInteriorRing()) {
                holes.add(poly.getInteriorRingN(i))
            }
            holes.sortWith(EnvelopeComparator())
            return holes
        }

        /**
         * Gets a list of indices of the leftmost vertices in a ring.
         *
         * @param geom the hole ring
         * @return indices of the leftmost vertices
         */
        private fun findLeftVertices(ring: LinearRing): List<Int> {
            val coords: Array<Coordinate> = ring.coordinates
            val leftmostIndex: ArrayList<Int> = ArrayList<Int>()
            val leftX: Double = ring.envelopeInternal.minX
            for (i in 0 until coords.size - 1) {
                //TODO: can this be strict equality?
                if (abs(coords[i].x - leftX) < EPS) {
                    leftmostIndex.add(i)
                }
            }
            return leftmostIndex
        }

        private fun createPolygonIntersector(polygon: Polygon): SegmentSetMutualIntersector {
            val polySegStrings: List<SegmentString> = SegmentStringUtil.extractSegmentStrings(polygon)
            return MCIndexSegmentSetMutualIntersector(polySegStrings)
        }
    }
}