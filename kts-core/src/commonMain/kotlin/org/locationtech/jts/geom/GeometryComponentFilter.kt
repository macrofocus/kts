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
package org.locationtech.jts.geom

/**
 * `Geometry` classes support the concept of applying
 * a `GeometryComponentFilter`
 * filter to the `Geometry`.
 * The filter is applied to every component of the `Geometry`
 * which is itself a `Geometry`
 * and which does not itself contain any components.
 * (For instance, all the [LinearRing]s in [Polygon]s are visited,
 * but in a [MultiPolygon] the [Polygon]s themselves are not visited.)
 * Thus the only classes of Geometry which must be
 * handled as arguments to [.filter]
 * are [LineString]s, [LinearRing]s and [Point]s.
 *
 * A `GeometryComponentFilter` filter can either
 * record information about the `Geometry`
 * or change the `Geometry` in some way.
 * `GeometryComponentFilter`
 * is an example of the Gang-of-Four Visitor pattern.
 *
 * @version 1.7
 */
interface GeometryComponentFilter {
    /**
     * Performs an operation with or on `geom`.
     *
     * @param  geom  a `Geometry` to which the filter is applied.
     */
    fun filter(geom: Geometry)
}