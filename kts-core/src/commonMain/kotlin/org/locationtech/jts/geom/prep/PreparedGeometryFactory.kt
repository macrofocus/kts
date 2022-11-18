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
package org.locationtech.jts.geom.prep

import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.Lineal
import org.locationtech.jts.geom.Polygonal
import org.locationtech.jts.geom.Puntal
import kotlin.jvm.JvmStatic

/**
 * A factory for creating [PreparedGeometry]s.
 * It chooses an appropriate implementation of PreparedGeometry
 * based on the geometric type of the input geometry.
 *
 *
 * In the future, the factory may accept hints that indicate
 * special optimizations which can be performed.
 *
 *
 * Instances of this class are thread-safe.
 *
 * @author Martin Davis
 */
class PreparedGeometryFactory {
    /**
     * Creates a new [PreparedGeometry] appropriate for the argument [Geometry].
     *
     * @param geom the geometry to prepare
     * @return the prepared geometry
     */
    fun create(geom: Geometry): org.locationtech.jts.geom.prep.PreparedGeometry {
        if (geom is Polygonal) return org.locationtech.jts.geom.prep.PreparedPolygon(geom as Polygonal)
        if (geom is Lineal) return org.locationtech.jts.geom.prep.PreparedLineString(geom as Lineal)
        return if (geom is Puntal) org.locationtech.jts.geom.prep.PreparedPoint(geom as Puntal) else org.locationtech.jts.geom.prep.BasicPreparedGeometry(
            geom
        )
        /**
         * Default representation.
         */
    }

    companion object {
        /**
         * Creates a new [PreparedGeometry] appropriate for the argument [Geometry].
         *
         * @param geom the geometry to prepare
         * @return the prepared geometry
         */
        @JvmStatic
        fun prepare(geom: Geometry): org.locationtech.jts.geom.prep.PreparedGeometry {
            return PreparedGeometryFactory().create(geom)
        }
    }
}