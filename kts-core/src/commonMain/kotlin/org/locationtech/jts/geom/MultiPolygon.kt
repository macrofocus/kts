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
 * Models a collection of [Polygon]s.
 *
 * As per the OGC SFS specification,
 * the Polygons in a MultiPolygon may not overlap,
 * and may only touch at single points.
 * This allows the topological point-set semantics
 * to be well-defined.
 *
 * @version 1.7
 */
class MultiPolygon
/**
 * @param polygons
 * the `Polygon`s for this `MultiPolygon`,
 * or `null` or an empty array to create the empty
 * geometry. Elements may be empty `Polygon`s, but
 * not `null`s. The polygons must conform to the
 * assertions specified in the <A HREF="http://www.opengis.org/techno/specs.htm">OpenGIS Simple
 * Features Specification for SQL</A>.
 */
    (polygons: Array<Polygon>?, factory: GeometryFactory) :
    GeometryCollection(polygons as Array<Geometry>?, factory), Polygonal {
    /**
     * Constructs a `MultiPolygon`.
     *
     * @param  polygons        the `Polygon`s for this `MultiPolygon`
     * , or `null` or an empty array to create the empty geometry.
     * Elements may be empty `Polygon`s, but not `null`
     * s. The polygons must conform to the assertions specified in the <A HREF="http://www.opengis.org/techno/specs.htm">OpenGIS Simple Features
     * Specification for SQL</A> .
     * @param  precisionModel  the specification of the grid of allowable points
     * for this `MultiPolygon`
     * @param  SRID            the ID of the Spatial Reference System used by this
     * `MultiPolygon`
     */
    @Deprecated("Use GeometryFactory instead")
    constructor(
        polygons: Array<Polygon>?,
        precisionModel: PrecisionModel,
        SRID: Int
    ) : this(polygons, GeometryFactory(precisionModel, SRID))

    override val dimension: Int
        get() = 2
    override val boundaryDimension: Int
        get() = 1
    override val geometryType: String
        get() = TYPENAME_MULTIPOLYGON
    /*
  public boolean isSimple() {
    return true;
  }
*/
    /**
     * Computes the boundary of this geometry
     *
     * @return a lineal geometry (which may be empty)
     * @see Geometry.getBoundary
     */
    override val boundary: Geometry
        get() {
            if (isEmpty) {
                return factory.createMultiLineString()
            }
            val allRings: ArrayList<Geometry> = ArrayList()
            for (element in geometries) {
                val rings: Geometry = element.boundary!!
                for (j in 0 until rings.numGeometries) {
                    allRings.add(rings.getGeometryN(j))
                }
            }
            return factory.createMultiLineString(allRings.map { it as LineString }.toTypedArray())
        }

    override fun equalsExact(other: Geometry?, tolerance: Double): Boolean {
        return if (!isEquivalentClass(other!!)) {
            false
        } else super.equalsExact(other, tolerance)
    }

    /**
     * Creates a [MultiPolygon] with
     * every component reversed.
     * The order of the components in the collection are not reversed.
     *
     * @return a MultiPolygon in the reverse order
     */
    override fun reverse(): MultiPolygon {
        return super.reverse() as MultiPolygon
    }

    override fun reverseInternal(): MultiPolygon {
        val polygons: Array<Polygon?> =
            arrayOfNulls(this.geometries.size)
        for (i in polygons.indices) {
            polygons[i] = this.geometries[i].reverse() as Polygon
        }
        return MultiPolygon(polygons.requireNoNulls(), factory)
    }

    override fun copyInternal(): MultiPolygon {
        val polygons: Array<Polygon?> =
            arrayOfNulls(this.geometries.size)
        for (i in polygons.indices) {
            polygons[i] = this.geometries[i].copy() as Polygon
        }
        return MultiPolygon(polygons.requireNoNulls(), factory)
    }

    override val typeCode: Int
        protected get() = TYPECODE_MULTIPOLYGON

    companion object {
        private const val serialVersionUID = -551033529766975875L
    }
}