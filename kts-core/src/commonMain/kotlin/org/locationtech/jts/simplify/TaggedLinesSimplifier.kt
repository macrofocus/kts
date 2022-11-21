/*
 * Copyright (c) 2016 Vivid Solutions.
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
package org.locationtech.jts.simplify

/**
 * Simplifies a collection of TaggedLineStrings, preserving topology
 * (in the sense that no new intersections are introduced).
 * This class is essentially just a container for the common
 * indexes used by [TaggedLineStringSimplifier].
 */
internal class TaggedLinesSimplifier {
    private val inputIndex: LineSegmentIndex = LineSegmentIndex()
    private val outputIndex: LineSegmentIndex = LineSegmentIndex()
    private var distanceTolerance = 0.0

    /**
     * Sets the distance tolerance for the simplification.
     * All vertices in the simplified geometry will be within this
     * distance of the original geometry.
     *
     * @param distanceTolerance the approximation tolerance to use
     */
    fun setDistanceTolerance(distanceTolerance: Double) {
        this.distanceTolerance = distanceTolerance
    }

    /**
     * Simplify a collection of TaggedLineStrings
     *
     * @param taggedLines the collection of lines to simplify
     */
    fun simplify(taggedLines: Collection<*>) {
        run {
            val i = taggedLines.iterator()
            while (i.hasNext()) {
                inputIndex.add(i.next() as TaggedLineString)
            }
        }
        val i = taggedLines.iterator()
        while (i.hasNext()) {
            val tlss: TaggedLineStringSimplifier =
                TaggedLineStringSimplifier(inputIndex, outputIndex)
            tlss.setDistanceTolerance(distanceTolerance)
            tlss.simplify(i.next() as org.locationtech.jts.simplify.TaggedLineString)
        }
    }
}