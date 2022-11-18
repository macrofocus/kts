/*
 * Copyright (c) 2019 Martin Davis.
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.index.hprtree

import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.legacy.Math.pow
import org.locationtech.jts.shape.fractal.HilbertCode.encode

class HilbertEncoder(private val level: Int, extent: Envelope) {
    private val minx: Double
    private val miny: Double
    private val strideX: Double
    private val strideY: Double

    init {
        val hside: Int = pow(2.0, level.toDouble()).toInt() - 1
        minx = extent.minX
        val extentX = extent.width
        strideX = extentX / hside
        miny = extent.minX
        val extentY = extent.height
        strideY = extentY / hside
    }

    fun encode(env: Envelope): Int {
        val midx = env.width / 2 + env.minX
        val x = ((midx - minx) / strideX).toInt()
        val midy = env.height / 2 + env.minY
        val y = ((midy - miny) / strideY).toInt()
        return encode(level, x, y)
    }
}