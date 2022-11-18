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
package org.locationtech.jts.index.sweepline

import kotlin.jvm.JvmOverloads

/**
 * @version 1.7
 */
class SweepLineInterval @JvmOverloads constructor(min: Double, max: Double, item: Any? = null) {
    val min: Double
    val max: Double
    val item: Any?

    init {
        this.min = if (min < max) min else max
        this.max = if (max > min) max else min
        this.item = item
    }
}