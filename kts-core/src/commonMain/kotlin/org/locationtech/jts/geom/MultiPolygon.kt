package org.locationtech.jts.geom


/**
 * Models a collection of {@link Polygon}s.
 * <p>
 * As per the OGC SFS specification,
 * the Polygons in a MultiPolygon may not overlap,
 * and may only touch at single points.
 * This allows the topological point-set semantics
 * to be well-defined.
 *
 *@version 1.7
 */
open class MultiPolygon : GeometryCollection, Polygonal {
    constructor(geometries: Array<Polygon>?, factory: GeometryFactory?) : super(geometries as Array<Geometry>?, factory)

    override val dimension: Int
        get() = 2

    override val boundaryDimension: Int
        get() = 1

    override val geometryType: String
        get() = TYPENAME_MULTIPOLYGON

    override val typeCode: Int
        get() = TYPECODE_MULTIPOLYGON

    override val boundary: Geometry?
        get() {
            if (isEmpty) {
                return factory.createMultiLineString()
            }
            val allRings = ArrayList<Geometry>()
            for (element in geometries) {
                val rings = element.boundary!!
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

    override fun copyInternal(): MultiPolygon {
        val polygons = arrayOfNulls<Polygon>(geometries.size)
        for (i in polygons.indices) {
            polygons[i] = geometries[i].copy() as Polygon
        }
        return MultiPolygon(polygons.requireNoNulls(), factory)
    }

    companion object {
        private const val serialVersionUID = -551033529766975875L
    }
}