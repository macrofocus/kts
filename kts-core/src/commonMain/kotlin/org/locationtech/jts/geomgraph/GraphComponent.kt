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
package org.locationtech.jts.geomgraph

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.IntersectionMatrix
import org.locationtech.jts.util.Assert

/**
 * A GraphComponent is the parent class for the objects'
 * that form a graph.  Each GraphComponent can carry a
 * Label.
 * @version 1.7
 */
abstract class GraphComponent {
    open var label: Label? = null

    /**
     * isInResult indicates if this component has already been included in the result
     */
    var isInResult = false
    var isCovered = false
        set(value) {
            field = value
            isCoveredSet = true
        }
    var isCoveredSet = false
        private set
    var isVisited = false

    constructor()
    constructor(label: Label?) {
        this.label = label
    }

    /**
     * @return a coordinate in this component (or null, if there are none)
     */
    abstract fun getCoordinate(): Coordinate?

    /**
     * Compute the contribution to an IM for this component.
     *
     * @param im Intersection matrix
     */
    protected abstract fun computeIM(im: IntersectionMatrix)

    /**
     * An isolated component is one that does not intersect or touch any other
     * component.  This is the case if the label has valid locations for
     * only a single Geometry.
     *
     * @return true if this component is isolated
     */
    abstract val isIsolated: Boolean

    /**
     * Update the IM with the contribution for this component.
     * A component only contributes if it has a labelling for both parent geometries
     * @param im Intersection matrix
     */
    fun updateIM(im: IntersectionMatrix) {
        Assert.isTrue(label!!.getGeometryCount() >= 2, "found partial label")
        computeIM(im)
    }
}