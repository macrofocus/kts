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
package org.locationtech.jts.util

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.CoordinateFilter

/**
 * A [CoordinateFilter] that counts the total number of coordinates
 * in a `Geometry`.
 *
 * @version 1.7
 */
class CoordinateCountFilter : CoordinateFilter {
    /**
     * Returns the result of the filtering.
     *
     * @return    the number of points found by this `CoordinateCountFilter`
     */
    var count = 0
        private set

    override fun filter(coord: Coordinate?) {
        count++
    }
}