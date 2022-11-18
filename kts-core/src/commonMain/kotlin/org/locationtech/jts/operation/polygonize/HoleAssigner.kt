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
package org.locationtech.jts.operation.polygonize

import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.index.SpatialIndex
import org.locationtech.jts.index.strtree.STRtree

/**
 * Assigns hole rings to shell rings
 * during polygonization.
 * Uses spatial indexing to improve performance
 * of shell lookup.
 *
 * @author mdavis
 */
class HoleAssigner(shells: List<EdgeRing>?) {
    private val shells: List<EdgeRing>?
    private var shellIndex: SpatialIndex? = null

    /**
     * Creates a new hole assigner.
     *
     * @param shells the shells to be assigned to
     */
    init {
        this.shells = shells
        buildIndex()
    }

    private fun buildIndex() {
        shellIndex = STRtree()
        for (shell in shells!!) {
            shellIndex!!.insert(shell.getRing()!!.envelopeInternal, shell)
        }
    }

    /**
     * Assigns holes to the shells.
     *
     * @param holeList list of hole rings to assign
     */
    fun assignHolesToShells(holeList: List<EdgeRing?>?) {
        val i: Iterator<*> = holeList!!.iterator()
        while (i.hasNext()) {
            val holeER: EdgeRing =
                i.next() as EdgeRing
            assignHoleToShell(holeER)
        }
    }

    private fun assignHoleToShell(holeER: EdgeRing) {
        val shell: EdgeRing? = findShellContaining(holeER)
        if (shell != null) {
            shell.addHole(holeER)
        }
    }

    private fun queryOverlappingShells(ringEnv: Envelope): List<EdgeRing>? {
        return shellIndex!!.query(ringEnv) as List<EdgeRing>?
    }

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
     * @return containing shell EdgeRing, if there is one
     * or null if no containing EdgeRing is found
     */
    private fun findShellContaining(testEr: EdgeRing): EdgeRing? {
        val testEnv: Envelope = testEr.getRing()!!.envelopeInternal
        val candidateShells: List<EdgeRing>? = queryOverlappingShells(testEnv)
        return EdgeRing.findEdgeRingContaining(
            testEr,
            candidateShells!!
        )
    }

    companion object {
        /**
         * Assigns hole rings to shell rings.
         *
         * @param holes list of hole rings to assign
         * @param shells list of shell rings
         */
        fun assignHolesToShells(holes: MutableList<EdgeRing>?, shells: MutableList<EdgeRing>?) {
            val assigner = HoleAssigner(shells)
            assigner.assignHolesToShells(holes)
        }
    }
}