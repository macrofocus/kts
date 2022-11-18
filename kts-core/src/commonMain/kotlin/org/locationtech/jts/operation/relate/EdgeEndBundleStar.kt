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
package org.locationtech.jts.operation.relate

import org.locationtech.jts.geom.IntersectionMatrix
import org.locationtech.jts.geomgraph.EdgeEnd
import org.locationtech.jts.geomgraph.EdgeEndStar

/**
 * An ordered list of [EdgeEndBundle]s around a [RelateNode].
 * They are maintained in CCW order (starting with the positive x-axis) around the node
 * for efficient lookup and topology building.
 *
 * @version 1.7
 */
class EdgeEndBundleStar
/**
 * Creates a new empty EdgeEndBundleStar
 */
    : EdgeEndStar() {
    /**
     * Insert a EdgeEnd in order in the list.
     * If there is an existing EdgeStubBundle which is parallel, the EdgeEnd is
     * added to the bundle.  Otherwise, a new EdgeEndBundle is created
     * to contain the EdgeEnd.
     * <br></br>
     */
    override fun insert(e: EdgeEnd?) {
        var eb: EdgeEndBundle? =
            edgeMap[e] as EdgeEndBundle?
        if (eb == null) {
            eb = EdgeEndBundle(e!!)
            insertEdgeEnd(e, eb)
        } else {
            eb.insert(e!!)
        }
    }

    /**
     * Update the IM with the contribution for the EdgeStubs around the node.
     */
    fun updateIM(im: IntersectionMatrix?) {
        val it = iterator()
        while (it.hasNext()) {
            val esb: EdgeEndBundle =
                it.next() as EdgeEndBundle
            esb.updateIM(im)
        }
    }
}