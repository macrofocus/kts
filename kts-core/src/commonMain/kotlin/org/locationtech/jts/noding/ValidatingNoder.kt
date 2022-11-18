/*
 * Copyright (c) 2020 Martin Davis, and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.noding

/**
 * A wrapper for [Noder]s which validates
 * the output arrangement is correctly noded.
 * An arrangement of line segments is fully noded if
 * there is no line segment
 * which has another segment intersecting its interior.
 * If the noding is not correct, a [org.locationtech.jts.geom.TopologyException] is thrown
 * with details of the first invalid location found.
 *
 * @author mdavis
 *
 * @see FastNodingValidator
 */
class ValidatingNoder(noder: Noder) : Noder {
    private val noder: Noder
    private var nodedSS: Collection<SegmentString>? = null

    /**
     * Creates a noding validator wrapping the given Noder
     *
     * @param noder the Noder to validate
     */
    init {
        this.noder = noder
    }

    /**
     * Checks whether the output of the wrapped noder is fully noded.
     * Throws an exception if it is not.
     *
     * @throws org.locationtech.jts.geom.TopologyException
     */
    override fun computeNodes(segStrings: Collection<SegmentString>) {
        noder.computeNodes(segStrings)
        nodedSS = noder.nodedSubstrings
        validate()
    }

    private fun validate() {
        val nv: FastNodingValidator =
            FastNodingValidator(nodedSS)
        nv.checkValid()
    }

    override val nodedSubstrings: Collection<SegmentString>
        get() = nodedSS!!
}