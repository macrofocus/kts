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
package org.locationtech.jts.operation

import org.locationtech.jts.algorithm.BoundaryNodeRule
import org.locationtech.jts.algorithm.LineIntersector
import org.locationtech.jts.algorithm.RobustLineIntersector
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.PrecisionModel
import org.locationtech.jts.geomgraph.GeometryGraph
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads

/**
 * The base class for operations that require [GeometryGraph]s.
 *
 * @version 1.7
 */
open class GeometryGraphOperation {
    protected val li: LineIntersector = RobustLineIntersector()
    protected var resultPrecisionModel: PrecisionModel? = null

    /**
     * The operation args into an array so they can be accessed by index
     */
    @JvmField
    protected var arg // the arg(s) of the operation
            : Array<GeometryGraph>

    @JvmOverloads
    constructor(
        g0: Geometry, g1: Geometry, boundaryNodeRule: BoundaryNodeRule? =
            BoundaryNodeRule.OGC_SFS_BOUNDARY_RULE //         BoundaryNodeRule.ENDPOINT_BOUNDARY_RULE
    ) {
        // use the most precise model for the result
        if (g0.precisionModel >= g1.precisionModel) setComputationPrecision(g0.precisionModel) else setComputationPrecision(
            g1.precisionModel
        )
        arg = arrayOf(GeometryGraph(0, g0, boundaryNodeRule), GeometryGraph(1, g1, boundaryNodeRule))
    }

    constructor(g0: Geometry) {
        setComputationPrecision(g0.precisionModel)
        arg = arrayOf(GeometryGraph(0, g0))
    }

    fun getArgGeometry(i: Int): Geometry {
        return arg[i].geometry
    }

    protected fun setComputationPrecision(pm: PrecisionModel?) {
        resultPrecisionModel = pm
        li.precisionModel = resultPrecisionModel
    }
}