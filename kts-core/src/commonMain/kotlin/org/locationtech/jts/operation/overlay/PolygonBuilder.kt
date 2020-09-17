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
package org.locationtech.jts.operation.overlay

import org.locationtech.jts.algorithm.PointLocation
import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.CoordinateArrays.ptNotInList
import org.locationtech.jts.geomgraph.DirectedEdge
import org.locationtech.jts.geomgraph.EdgeRing
import org.locationtech.jts.geomgraph.PlanarGraph
import org.locationtech.jts.util.Assert.isTrue

/**
 * Forms [Polygon]s out of a graph of [DirectedEdge]s.
 * The edges to use are marked as being in the result Area.
 *
 *
 * @version 1.7
 */
class PolygonBuilder(private val geometryFactory: GeometryFactory) {
    private val shellList: MutableList<EdgeRing> = ArrayList()

    /**
     * Add a complete graph.
     * The graph is assumed to contain one or more polygons,
     * possibly with holes.
     */
    fun add(graph: PlanarGraph) {
        add(graph.edgeEnds, graph.getNodes())
    }

    /**
     * Add a set of edges and nodes, which form a graph.
     * The graph is assumed to contain one or more polygons,
     * possibly with holes.
     */
    fun add(dirEdges: Collection<Any?>, nodes: Collection<Any?>) {
        PlanarGraph.linkResultDirectedEdges(nodes)
        val maxEdgeRings = buildMaximalEdgeRings(dirEdges)
        val freeHoleList: MutableList<Any?> = ArrayList()
        val edgeRings = buildMinimalEdgeRings(maxEdgeRings, shellList, freeHoleList)
        sortShellsAndHoles(edgeRings, shellList, freeHoleList)
        placeFreeHoles(shellList, freeHoleList)
        //Assert: every hole on freeHoleList has a shell assigned to it
    }

    val polygons: List<Geometry>
        get() = computePolygons(shellList)

    /**
     * for all DirectedEdges in result, form them into MaximalEdgeRings
     */
    private fun buildMaximalEdgeRings(dirEdges: Collection<*>): List<*> {
        val maxEdgeRings: MutableList<Any?> = ArrayList()
        val it = dirEdges.iterator()
        while (it.hasNext()) {
            val de = it.next() as DirectedEdge
            if (de.isInResult && de.label!!.isArea) {
                // if this edge has not yet been processed
                if (de.edgeRing == null) {
                    val er = MaximalEdgeRing(de, geometryFactory)
                    maxEdgeRings.add(er)
                    er.setInResult()
                    //System.out.println("max node degree = " + er.getMaxDegree());
                }
            }
        }
        return maxEdgeRings
    }

    private fun buildMinimalEdgeRings(
        maxEdgeRings: List<Any?>,
        shellList: MutableList<EdgeRing>,
        freeHoleList: MutableList<Any?>
    ): List<*> {
        val edgeRings: MutableList<Any?> = ArrayList()
        val it = maxEdgeRings.iterator()
        while (it.hasNext()) {
            val er = it.next() as MaximalEdgeRing
            if (er.getMaxNodeDegree() > 2) {
                er.linkDirectedEdgesForMinimalEdgeRings()
                val minEdgeRings = er.buildMinimalRings()
                // at this point we can go ahead and attempt to place holes, if this EdgeRing is a polygon
                val shell = findShell(minEdgeRings)
                if (shell != null) {
                    placePolygonHoles(shell, minEdgeRings)
                    shellList.add(shell)
                } else {
                    freeHoleList.addAll(minEdgeRings)
                }
            } else {
                edgeRings.add(er)
            }
        }
        return edgeRings
    }

    /**
     * This method takes a list of MinimalEdgeRings derived from a MaximalEdgeRing,
     * and tests whether they form a Polygon.  This is the case if there is a single shell
     * in the list.  In this case the shell is returned.
     * The other possibility is that they are a series of connected holes, in which case
     * no shell is returned.
     *
     * @return the shell EdgeRing, if there is one
     * or null, if all the rings are holes
     */
    private fun findShell(minEdgeRings: List<*>?): EdgeRing? {
        var shellCount = 0
        var shell: EdgeRing? = null
        val it = minEdgeRings!!.iterator()
        while (it.hasNext()) {
            val er: EdgeRing = it.next() as MinimalEdgeRing
            if (!er.isHole) {
                shell = er
                shellCount++
            }
        }
        isTrue(shellCount <= 1, "found two shells in MinimalEdgeRing list")
        return shell
    }

    /**
     * This method assigns the holes for a Polygon (formed from a list of
     * MinimalEdgeRings) to its shell.
     * Determining the holes for a MinimalEdgeRing polygon serves two purposes:
     *
     *  * it is faster than using a point-in-polygon check later on.
     *  * it ensures correctness, since if the PIP test was used the point
     * chosen might lie on the shell, which might return an incorrect result from the
     * PIP test
     *
     */
    private fun placePolygonHoles(shell: EdgeRing, minEdgeRings: List<*>?) {
        val it = minEdgeRings!!.iterator()
        while (it.hasNext()) {
            val er = it.next() as MinimalEdgeRing
            if (er.isHole) {
                er.shell = shell
            }
        }
    }

    /**
     * For all rings in the input list,
     * determine whether the ring is a shell or a hole
     * and add it to the appropriate list.
     * Due to the way the DirectedEdges were linked,
     * a ring is a shell if it is oriented CW, a hole otherwise.
     */
    private fun sortShellsAndHoles(
        edgeRings: List<Any?>,
        shellList: MutableList<EdgeRing>,
        freeHoleList: MutableList<Any?>
    ) {
        val it = edgeRings.iterator()
        while (it.hasNext()) {
            val er = it.next() as EdgeRing
            //      er.setInResult();
            if (er.isHole) {
                freeHoleList.add(er)
            } else {
                shellList.add(er)
            }
        }
    }

    /**
     * This method determines finds a containing shell for all holes
     * which have not yet been assigned to a shell.
     * These "free" holes should
     * all be **properly** contained in their parent shells, so it is safe to use the
     * `findEdgeRingContaining` method.
     * (This is the case because any holes which are NOT
     * properly contained (i.e. are connected to their
     * parent shell) would have formed part of a MaximalEdgeRing
     * and been handled in a previous step).
     *
     * @throws TopologyException if a hole cannot be assigned to a shell
     */
    private fun placeFreeHoles(shellList: List<Any?>, freeHoleList: List<Any?>) {
        val it = freeHoleList.iterator()
        while (it.hasNext()) {
            val hole = it.next() as EdgeRing
            // only place this hole if it doesn't yet have a shell
            if (hole.shell == null) {
                val shell = findEdgeRingContaining(hole, shellList)
                    ?: throw TopologyException("unable to assign hole to a shell", hole.getCoordinate(0))
                //        Assert.isTrue(shell != null, "unable to assign hole to a shell");
                hole.shell = shell
            }
        }
    }

    private fun computePolygons(shellList: List<EdgeRing>): List<Geometry> {
        val resultPolyList: MutableList<Geometry> = ArrayList()
        // add Polygons for all shells
        val it = shellList.iterator()
        while (it.hasNext()) {
            val er = it.next()
            val poly = er.toPolygon(geometryFactory)
            resultPolyList.add(poly)
        }
        return resultPolyList
    }

    companion object {
        /**
         * Find the innermost enclosing shell EdgeRing containing the argument EdgeRing, if any.
         * The innermost enclosing ring is the *smallest* enclosing ring.
         * The algorithm used depends on the fact that:
         * <br></br>
         * ring A contains ring B iff envelope(ring A) contains envelope(ring B)
         * <br></br>
         * This routine is only safe to use if the chosen point of the hole
         * is known to be properly contained in a shell
         * (which is guaranteed to be the case if the hole does not touch its shell)
         *
         * @return containing EdgeRing, if there is one
         * or null if no containing EdgeRing is found
         */
        private fun findEdgeRingContaining(testEr: EdgeRing, shellList: List<*>): EdgeRing? {
            val testRing = testEr.linearRing!!
            val testEnv = testRing.envelopeInternal
            var testPt: Coordinate? = testRing.getCoordinateN(0)
            var minShell: EdgeRing? = null
            var minShellEnv: Envelope? = null
            val it = shellList.iterator()
            while (it.hasNext()) {
                val tryShell = it.next() as EdgeRing
                val tryShellRing = tryShell.linearRing!!
                val tryShellEnv = tryShellRing.envelopeInternal
                // the hole envelope cannot equal the shell envelope
                // (also guards against testing rings against themselves)
                if (tryShellEnv == testEnv) {
                    continue
                }
                // hole must be contained in shell
                if (!tryShellEnv.contains(testEnv)) {
                    continue
                }
                testPt = ptNotInList(testRing.coordinates, tryShellRing.coordinates)
                var isContained = false
                if (PointLocation.isInRing(testPt!!, tryShellRing.coordinates)) isContained = true

                // check if this new containing ring is smaller than the current minimum ring
                if (isContained) {
                    if (minShell == null
                        || minShellEnv!!.contains(tryShellEnv)
                    ) {
                        minShell = tryShell
                        minShellEnv = minShell.linearRing!!.envelopeInternal
                    }
                }
            }
            return minShell
        }
    }
}