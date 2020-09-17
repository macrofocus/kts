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
package org.locationtech.jts.index.strtree

import org.locationtech.jts.legacy.Math
import org.locationtech.jts.util.Assert.isTrue

/**
 * A contiguous portion of 1D-space. Used internally by SIRtree.
 * @see SIRtree
 *
 * @version 1.7
 */
class Interval(min: Double, max: Double) {
    constructor(other: Interval) : this(other.min, other.max)

    private var min: Double
    private var max: Double
    val centre: Double
        get() = (min + max) / 2

    /**
     * @return this
     */
    fun expandToInclude(other: Interval): Interval {
        max = Math.max(max, other.max)
        min = Math.min(min, other.min)
        return this
    }

    fun intersects(other: Interval): Boolean {
        return !(other.min > max || other.max < min)
    }

    override fun equals(o: Any?): Boolean {
        if (o !is Interval) {
            return false
        }
        return min == o.min && max == o.max
    }

    init {
        isTrue(min <= max)
        this.min = min
        this.max = max
    }
}