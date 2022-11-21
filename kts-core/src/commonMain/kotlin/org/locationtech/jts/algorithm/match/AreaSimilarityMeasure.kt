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

import org.locationtech.jts.geom.Geometry

/**
 * Measures the degree of similarity between two [Geometry]s
 * using the area of intersection between the geometries.
 * The measure is normalized to lie in the range [0, 1].
 * Higher measures indicate a great degree of similarity.
 *
 *
 * NOTE: Currently experimental and incomplete.
 *
 * @author mbdavis
 */
class AreaSimilarityMeasure  /*
	public static double measure(Geometry a, Geometry b)
	{
		AreaSimilarityMeasure gv = new AreaSimilarityMeasure(a, b);
		return gv.measure();
	}
	*/
/**
 * Creates a new instance.
 */
    : SimilarityMeasure {
    override fun measure(g1: Geometry, g2: Geometry): Double {
        val areaInt = g1.intersection(g2).area
        val areaUnion = g1.union(g2).area
        return areaInt / areaUnion
    }
}