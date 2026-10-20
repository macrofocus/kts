/*
 * Copyright (c) 2022 Martin Davis.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */

/**
 * Classes that operate on polygonal coverages.
 *
 * A polygonal coverage is a set of polygonal geometries which is non-overlapping and edge-matched.
 * ([Polygon]s or [MultiPolygon]s).
 * A set of polygonal geometries is a valid coverage if:
 *
 *  1. Each geometry is valid
 *  1. The interiors of all polygons are disjoint (they are non-overlapping).
 * This is the case if no polygon has a boundary which intersects the interior of another polygon.
 *  1. Where polygons are adjacent (i.e. their boundaries intersect),
 * they are **edge-matched**: the vertices
 * (and thus line segments) of the common boundary section match exactly.
 *
 * A coverage may contain holes and disjoint regions.
 *
 * Coverage algorithms (such as [CoverageUnion])
 * generally require the input coverage to be valid to produce correct results.
 * Coverages can be validated using [CoverageValidator].
 */
package org.locationtech.jts.coverage
