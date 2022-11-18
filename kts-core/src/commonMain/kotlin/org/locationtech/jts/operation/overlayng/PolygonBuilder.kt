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

import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.TopologyException
import org.locationtech.jts.util.Assert
import kotlin.jvm.JvmOverloads

internal class PolygonBuilder @JvmOverloads constructor(
    resultAreaEdges: List<OverlayEdge>,
    private val geometryFactory: GeometryFactory,
    val isEnforcePolygonal: Boolean = true
) {
    private val shellList: MutableList<OverlayEdgeRing> =
        ArrayList<OverlayEdgeRing>()
    private val freeHoleList: MutableList<OverlayEdgeRing> =
        ArrayList<OverlayEdgeRing>()

    init {
        buildRings(resultAreaEdges)
    }

    val polygons: List<Polygon>
        get() = computePolygons(shellList)
    val shellRings: List<OverlayEdgeRing>
        get() = shellList

    private fun computePolygons(shellList: List<OverlayEdgeRing>): List<Polygon> {
        val resultPolyList: MutableList<Polygon> = ArrayList<Polygon>()
        // add Polygons for all shells
        for (er in shellList) {
            val poly: Polygon = er.toPolygon(geometryFactory)
            resultPolyList.add(poly)
        }
        return resultPolyList
    }

    private fun buildRings(resultAreaEdges: List<OverlayEdge>) {
        linkResultAreaEdgesMax(resultAreaEdges)
        val maxRings: List<MaximalEdgeRing> =
            buildMaximalRings(resultAreaEdges)
        buildMinimalRings(maxRings)
        placeFreeHoles(shellList, freeHoleList)
        //Assert: every hole on freeHoleList has a shell assigned to it
    }

    private fun linkResultAreaEdgesMax(resultEdges: List<OverlayEdge>) {
        for (edge in resultEdges) {
            //Assert.isTrue(edge.isInResult());
            // TODO: find some way to skip nodes which are already linked
            MaximalEdgeRing.linkResultAreaMaxRingAtNode(edge)
        }
    }

    private fun buildMinimalRings(maxRings: List<MaximalEdgeRing>) {
        for (erMax in maxRings) {
            val minRings: List<OverlayEdgeRing> =
                erMax.buildMinimalRings(geometryFactory)
            assignShellsAndHoles(minRings)
        }
    }

    private fun assignShellsAndHoles(minRings: List<OverlayEdgeRing>) {
        /**
         * Two situations may occur:
         * - the rings are a shell and some holes
         * - rings are a set of holes
         * This code identifies the situation
         * and places the rings appropriately
         */
        val shell: OverlayEdgeRing? = findSingleShell(minRings)
        if (shell != null) {
            assignHoles(shell, minRings)
            shellList.add(shell)
        } else {
            // all rings are holes; their shell will be found later
            freeHoleList.addAll(minRings)
        }
    }

    /**
     * Finds the single shell, if any, out of
     * a list of minimal rings derived from a maximal ring.
     * The other possibility is that they are a set of (connected) holes,
     * in which case no shell will be found.
     *
     * @return the shell ring, if there is one
     * or null, if all rings are holes
     */
    private fun findSingleShell(edgeRings: List<OverlayEdgeRing>): OverlayEdgeRing? {
        var shellCount = 0
        var shell: OverlayEdgeRing? = null
        for (er in edgeRings) {
            if (!er.isHole) {
                shell = er
                shellCount++
            }
        }
        Assert.isTrue(shellCount <= 1, "found two shells in EdgeRing list")
        return shell
    }

    /**
     * Place holes have not yet been assigned to a shell.
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
    private fun placeFreeHoles(
        shellList: List<OverlayEdgeRing>,
        freeHoleList: List<OverlayEdgeRing>
    ) {
        // TODO: use a spatial index to improve performance
        for (hole in freeHoleList) {
            // only place this hole if it doesn't yet have a shell
            if (hole.getShell() == null) {
                val shell: OverlayEdgeRing? =
                    hole.findEdgeRingContaining(shellList)
                // only when building a polygon-valid result
                if (isEnforcePolygonal && shell == null) {
                    throw TopologyException("unable to assign free hole to a shell", hole.coordinate)
                }
                hole.setShell(shell)
            }
        }
    }

    companion object {
        /**
         * For all OverlayEdges in result, form them into MaximalEdgeRings
         */
        private fun buildMaximalRings(edges: Collection<OverlayEdge>): List<MaximalEdgeRing> {
            val edgeRings: MutableList<MaximalEdgeRing> =
                ArrayList<MaximalEdgeRing>()
            for (e in edges) {
                if (e.isInResultArea && e.getLabel().isBoundaryEither) {
                    // if this edge has not yet been processed
                    if (e.edgeRingMax == null) {
                        val er: MaximalEdgeRing =
                            MaximalEdgeRing(e)
                        edgeRings.add(er)
                    }
                }
            }
            return edgeRings
        }

        /**
         * For the set of minimal rings comprising a maximal ring,
         * assigns the holes to the shell known to contain them.
         * Assigning the holes directly to the shell serves two purposes:
         *
         *  * it is faster than using a point-in-polygon check later on.
         *  * it ensures correctness, since if the PIP test was used the point
         * chosen might lie on the shell, which might return an incorrect result from the
         * PIP test
         *
         */
        private fun assignHoles(
            shell: OverlayEdgeRing,
            edgeRings: List<OverlayEdgeRing>
        ) {
            for (er in edgeRings) {
                if (er.isHole) {
                    er.setShell(shell)
                }
            }
        }
    }
}