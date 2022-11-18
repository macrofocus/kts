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

import org.locationtech.jts.geom.Dimension

/**
 * Records topological information about an
 * edge representing a piece of linework (lineString or polygon ring)
 * from a single source geometry.
 * This information is carried through the noding process
 * (which may result in many noded edges sharing the same information object).
 * It is then used to populate the topology info fields
 * in [Edge]s (possibly via merging).
 * That information is used to construct the topology graph [OverlayLabel]s.
 *
 * @author mdavis
 */
internal class EdgeSourceInfo {
    var index: Int
        private set
    var dimension = -999
        private set
    var isHole = false
        private set
    var depthDelta = 0
        private set

    constructor(index: Int, depthDelta: Int, isHole: Boolean) {
        this.index = index
        dimension = Dimension.A
        this.depthDelta = depthDelta
        this.isHole = isHole
    }

    constructor(index: Int) {
        this.index = index
        dimension = Dimension.L
    }

    override fun toString(): String {
        return Edge.infoString(index, dimension, isHole, depthDelta)
    }
}