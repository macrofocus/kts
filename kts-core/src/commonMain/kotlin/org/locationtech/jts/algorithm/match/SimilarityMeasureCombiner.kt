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
package org.locationtech.jts.algorithm.match

import org.locationtech.jts.legacy.Math.min
import kotlin.jvm.JvmStatic

/**
 * Provides methods to mathematically combine [SimilarityMeasure] values.
 *
 * @author Martin Davis
 */
object SimilarityMeasureCombiner {
    @JvmStatic
    fun combine(measure1: Double, measure2: Double): Double {
        return min(measure1, measure2)
    }
}