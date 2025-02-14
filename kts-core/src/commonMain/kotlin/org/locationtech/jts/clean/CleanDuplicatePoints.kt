package org.locationtech.jts.clean

import org.locationtech.jts.geom.*

fun removeDuplicatePoints(coord: Array<Coordinate>): Array<Coordinate> {
    val uniqueCoords: MutableList<Coordinate> = ArrayList()
    var lastPt: Coordinate? = null
    for (i in coord.indices) {
        if (lastPt == null || lastPt != coord[i]) {
            lastPt = coord[i]
            uniqueCoords.add(Coordinate(lastPt))
        }
    }
    return uniqueCoords.toTypedArray()
}

fun cleanPolygon(poly: Polygon?): Polygon? {
    if(poly != null) {
        return poly.clean()
    } else {
        return null
    }
}

private fun LinearRing.clean(): LinearRing {
    val fact = factory
    val coords = removeDuplicatePoints(coordinates)
    return fact.createLinearRing(coords)
}

private fun LineString.clean(): LineString {
    val fact = factory
    val coords = removeDuplicatePoints(coordinates)
    return fact.createLineString(coords)
}

// Extension property to get holes of a Polygon
//private val Polygon.holes: List<Geometry>
//    get() = (0 until numInteriorRing).map { getInteriorRingN(it) }
fun Polygon.clean(): Polygon {
    val fact = factory

    val shellCoords: Array<Coordinate> = removeDuplicatePoints(exteriorRing!!.coordinates)
    val shell: LinearRing = fact.createLinearRing(shellCoords)
    val holes: MutableList<LinearRing> = ArrayList()
    for (i in 0..<getNumInteriorRing()) {
        val holeCoords: Array<Coordinate> = removeDuplicatePoints(getInteriorRingN(i).coordinates)
        holes.add(fact.createLinearRing(holeCoords))
    }
    return fact.createPolygon(shell, GeometryFactory.toLinearRingArray(holes))
}

private fun MultiPolygon.clean(): MultiPolygon {
    val fact = factory

    val polys: MutableList<Polygon> = ArrayList()
    for (i in 0..<numGeometries) {
        val poly = getGeometryN(i) as Polygon
        polys.add(poly.clean())
    }
    return fact.createMultiPolygon(GeometryFactory.toPolygonArray(polys))
}

private fun MultiLineString.clean(): MultiLineString {
    val fact = factory

    val lines: MutableList<LineString> = ArrayList()
    for (i in 0..<numGeometries) {
        val line = getGeometryN(i) as LineString
        lines.add(line.clean())
    }
    return fact.createMultiLineString(GeometryFactory.toLineStringArray(lines))
}

private fun GeometryCollection.clean(): GeometryCollection {
    val fact = factory

    val geoms: MutableList<Geometry> = ArrayList()
    for (i in 0..<numGeometries) {
        val geom = getGeometryN(i)
        geoms.add(geom.clean())
    }
    return fact.createGeometryCollection(GeometryFactory.toGeometryArray(geoms))
}

fun Geometry.clean(): Geometry {
    val fact = factory
    if (isEmpty) return this
    return if (this is Point) this
    else if (this is MultiPoint) this
    // LineString also handles LinearRings
    else if (this is LinearRing) this.clean()
    else if (this is LineString) this.clean()
    else if (this is Polygon) this.clean()
    else if (this is MultiLineString) this.clean()
    else if (this is MultiPolygon) this.clean()
    else if (this is GeometryCollection) this.clean()
    else throw UnsupportedOperationException(this::class.simpleName)
}