/*
 * Copyright (c) 2016 Vivid Solutions.
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
package org.locationtech.jts.simplify

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.LineSegment
import kotlin.jvm.JvmOverloads

/**
 * A [LineSegment] which is tagged with its location in a parent [Geometry].
 * Used to index the segments in a geometry and recover the segment locations
 * from the index.
 */
class TaggedLineSegment @JvmOverloads constructor(
    p0: Coordinate?,
    p1: Coordinate?,
    val parent: Geometry? = null,
    val index: Int = -1
) : LineSegment(
    p0!!, p1!!
)