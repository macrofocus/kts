/*
 * Copyright (c) 2016 Martin Davis.
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.operation.distance

import org.locationtech.jts.geom.*
import org.locationtech.jts.index.strtree.STRtree

object FacetSequenceTreeBuilder {
    // 6 seems to be a good facet sequence size
    private const val FACET_SEQUENCE_SIZE = 6

    // Seems to be better to use a minimum node capacity
    private const val STR_TREE_NODE_CAPACITY = 4
    fun build(g: Geometry): STRtree {
        val tree = STRtree(STR_TREE_NODE_CAPACITY)
        val sections: MutableList<FacetSequence> = computeFacetSequences(g)
        val i: Iterator<*> = sections.iterator()
        while (i.hasNext()) {
            val section: FacetSequence = i.next() as FacetSequence
            tree.insert(section.envelope, section)
        }
        tree.build()
        return tree
    }

    /**
     * Creates facet sequences
     *
     * @param g
     * @return List<GeometryFacetSequence>
    </GeometryFacetSequence> */
    private fun computeFacetSequences(g: Geometry): MutableList<FacetSequence> {
        val sections: MutableList<FacetSequence> = ArrayList()
        g.apply(object : GeometryComponentFilter {
            override fun filter(geom: Geometry) {
                var seq: CoordinateSequence? = null
                if (geom is LineString) {
                    seq = geom.coordinateSequence
                    addFacetSequences(geom, seq, sections)
                } else if (geom is Point) {
                    seq = geom.coordinateSequence
                    addFacetSequences(geom, seq, sections)
                }
            }
        })
        return sections
    }

    private fun addFacetSequences(geom: Geometry, pts: CoordinateSequence?, sections: MutableList<FacetSequence>) {
        var i = 0
        val size = pts!!.size()
        while (i <= size - 1) {
            var end = i + FACET_SEQUENCE_SIZE + 1
            // if only one point remains after this section, include it in this
            // section
            if (end >= size - 1) end = size
            val sect = FacetSequence(geom, pts, i, end)
            sections.add(sect)
            i = i + FACET_SEQUENCE_SIZE
        }
    }
}