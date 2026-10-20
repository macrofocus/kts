/*
 * Copyright (c) 2024 Martin Davis.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.operation.relateng

/**
 * String constants for DE-9IM matrix patterns for topological relationships.
 * These can be used with [RelateNG.evaluate]
 * and [RelateNG.relate].
 *
 * <h3>DE-9IM Pattern Matching</h3>
 * Matrix patterns are specified as a 9-character string
 * containing the pattern symbols for the DE-9IM 3x3 matrix entries,
 * listed row-wise.
 * The pattern symbols are:
 *
 *  * `0` - topological interaction has dimension 0
 *  * `1` - topological interaction has dimension 1
 *  * `2` - topological interaction has dimension 2
 *  * `F` - no topological interaction
 *  * `T` - topological interaction of any dimension
 *  * `*` - any topological interaction is allowed, including none
 *
 * @author Martin Davis
 */
class IntersectionMatrixPattern
/**
 * Cannot be instantiated.
 */
private constructor() {
    companion object {
        /**
         * A DE-9IM pattern to detect whether two polygonal geometries are adjacent along
         * an edge, but do not overlap.
         */
        const val ADJACENT = "F***1****"

        /**
         * A DE-9IM pattern to detect a geometry which properly contains another
         * geometry (i.e. which lies entirely in the interior of the first geometry).
         */
        const val CONTAINS_PROPERLY = "T**FF*FF*"

        /**
         * A DE-9IM pattern to detect if two geometries intersect in their interiors.
         * This can be used to determine if a polygonal coverage contains any overlaps
         * (although not whether they are correctly noded).
         */
        const val INTERIOR_INTERSECTS = "T********"
    }
}
