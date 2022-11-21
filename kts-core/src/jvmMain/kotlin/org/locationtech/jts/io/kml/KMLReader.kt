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
package org.locationtech.jts.io.kml

import org.locationtech.jts.geom.*
import org.locationtech.jts.io.ParseException
import kotlin.jvm.JvmOverloads

/**
 * Constructs a [Geometry] object from the OGC KML representation.
 * Works only with KML geometry elements and may also parse attributes within these elements
 */
class KMLReader @JvmOverloads constructor(
    private val geometryFactory: GeometryFactory = GeometryFactory(),
    attributeNames: Collection<String> = emptyList<String>()
) {
    private val inputFactory: javax.xml.stream.XMLInputFactory = javax.xml.stream.XMLInputFactory.newInstance()
    private val attributeNames: Set<String>

    /**
     * Creates a reader that creates objects using the default [GeometryFactory].
     *
     * @param attributeNames names of attributes that should be parsed (i.e. extrude, altitudeMode, tesselate, etc).
     */
    constructor(attributeNames: Collection<String>) : this(GeometryFactory(), attributeNames) {}
    /**
     * Creates a reader that creates objects using the given
     * [GeometryFactory].
     *
     * @param geometryFactory the factory used to create `Geometry`s.
     * @param attributeNames  names of attributes that should be parsed (i.e. extrude, altitudeMode, tesselate, etc).
     */
    /**
     * Creates a reader that creates objects using the default [GeometryFactory].
     */
    /**
     * Creates a reader that creates objects using the given
     * [GeometryFactory].
     *
     * @param geometryFactory the factory used to create `Geometry`s.
     */
    init {
        this.attributeNames = if (attributeNames == null) emptySet<String>() else HashSet<String>(attributeNames)
    }

    /**
     * Reads a KML representation of a [Geometry] from a [String].
     * If any attribute names were specified during [KMLReader] construction,
     * they will be stored as [Map] in [Geometry.setUserData]
     *
     * @param kmlGeometryString string that specifies kml representation of geometry
     * @return a `Geometry` specified by `kmlGeometryString`
     * @throws ParseException if a parsing problem occurs
     */
    @Throws(ParseException::class)
    fun read(kmlGeometryString: String?): Geometry? {
        try {
            java.io.StringReader(kmlGeometryString).use { sr ->
                val xmlSr: javax.xml.stream.XMLStreamReader = inputFactory.createXMLStreamReader(sr)
                return parseKML(xmlSr)
            }
        } catch (e: javax.xml.stream.XMLStreamException) {
            throw ParseException(e)
        }
    }

    @Throws(javax.xml.stream.XMLStreamException::class, ParseException::class)
    private fun parseKMLCoordinates(xmlStreamReader: javax.xml.stream.XMLStreamReader): Array<Coordinate?> {
        val coordinates: String = xmlStreamReader.getElementText()
        if (coordinates.isEmpty()) {
            raiseParseError("Empty coordinates")
        }
        val parsedOrdinates = doubleArrayOf(Double.NaN, Double.NaN, Double.NaN)
        val coordinateList: MutableList<Coordinate> = ArrayList()
        var spaceIdx = coordinates.indexOf(' ')
        var currentIdx = 0
        while (currentIdx < coordinates.length) {
            if (spaceIdx == -1) {
                spaceIdx = coordinates.length
            }
            val coordinate = coordinates.substring(currentIdx, spaceIdx)
            val yOrdinateComma = coordinate.indexOf(',')
            if (yOrdinateComma == -1 || yOrdinateComma == coordinate.length - 1 || yOrdinateComma == 0) {
                raiseParseError("Invalid coordinate format")
            }
            parsedOrdinates[0] = coordinate.substring(0, yOrdinateComma).toDouble()
            val zOrdinateComma = coordinate.indexOf(',', yOrdinateComma + 1)
            if (zOrdinateComma == -1) {
                parsedOrdinates[1] = coordinate.substring(yOrdinateComma + 1).toDouble()
            } else {
                parsedOrdinates[1] = coordinate.substring(yOrdinateComma + 1, zOrdinateComma).toDouble()
                parsedOrdinates[2] = coordinate.substring(zOrdinateComma + 1).toDouble()
            }
            val crd = Coordinate(parsedOrdinates[0], parsedOrdinates[1], parsedOrdinates[2])
            geometryFactory.precisionModel.makePrecise(crd)
            coordinateList.add(crd)
            currentIdx = spaceIdx + 1
            spaceIdx = coordinates.indexOf(' ', currentIdx)
            parsedOrdinates[2] = Double.NaN
            parsedOrdinates[1] = parsedOrdinates[2]
            parsedOrdinates[0] = parsedOrdinates[1]
        }
        return coordinateList.toTypedArray()
    }

    @Throws(javax.xml.stream.XMLStreamException::class, ParseException::class)
    private fun parseKMLCoordinatesAndAttributes(
        xmlStreamReader: javax.xml.stream.XMLStreamReader,
        objectNodeName: String
    ): KMLCoordinatesAndAttributes {
        var coordinates: Array<Coordinate?>? = null
        var attributes: MutableMap<String?, String?>? = null
        while (xmlStreamReader.hasNext() && !(xmlStreamReader.isEndElement() && xmlStreamReader.getLocalName() == objectNodeName)) {
            if (xmlStreamReader.isStartElement()) {
                val elementName: String = xmlStreamReader.getLocalName()
                if (elementName == COORDINATES) {
                    coordinates = parseKMLCoordinates(xmlStreamReader)
                } else if (attributeNames.contains(elementName)) {
                    if (attributes == null) {
                        attributes = HashMap<String?, String?>()
                    }
                    attributes[elementName] = xmlStreamReader.getElementText()
                }
            }
            xmlStreamReader.next()
        }
        if (coordinates == null) {
            raiseParseError(NO_ELEMENT_ERROR, COORDINATES, objectNodeName)
        }
        return KMLCoordinatesAndAttributes(coordinates, attributes)
    }

    @Throws(javax.xml.stream.XMLStreamException::class, ParseException::class)
    private fun parseKMLPoint(xmlStreamReader: javax.xml.stream.XMLStreamReader): Geometry {
        val kmlCoordinatesAndAttributes = parseKMLCoordinatesAndAttributes(xmlStreamReader, POINT)
        val point = geometryFactory.createPoint(kmlCoordinatesAndAttributes.coordinates!![0])
        point.setUserData(kmlCoordinatesAndAttributes.attributes)
        return point
    }

    @Throws(javax.xml.stream.XMLStreamException::class, ParseException::class)
    private fun parseKMLLineString(xmlStreamReader: javax.xml.stream.XMLStreamReader): Geometry {
        val kmlCoordinatesAndAttributes = parseKMLCoordinatesAndAttributes(xmlStreamReader, LINESTRING)
        val lineString: LineString = geometryFactory.createLineString(kmlCoordinatesAndAttributes.coordinates!!.requireNoNulls())
        lineString.setUserData(kmlCoordinatesAndAttributes.attributes)
        return lineString
    }

    @Throws(javax.xml.stream.XMLStreamException::class, ParseException::class)
    private fun parseKMLPolygon(xmlStreamReader: javax.xml.stream.XMLStreamReader): Geometry {
        var shell: LinearRing? = null
        var holes: ArrayList<LinearRing?>? = null
        var attributes: MutableMap<String?, String?>? = null
        while (xmlStreamReader.hasNext() && !(xmlStreamReader.isEndElement() && xmlStreamReader.getLocalName() == POLYGON)) {
            if (xmlStreamReader.isStartElement()) {
                val elementName: String = xmlStreamReader.getLocalName()
                if (elementName == OUTER_BOUNDARY_IS) {
                    moveToElement(xmlStreamReader, COORDINATES, OUTER_BOUNDARY_IS)
                    shell = geometryFactory.createLinearRing(parseKMLCoordinates(xmlStreamReader).requireNoNulls())
                } else if (elementName == INNER_BOUNDARY_IS) {
                    moveToElement(xmlStreamReader, COORDINATES, INNER_BOUNDARY_IS)
                    if (holes == null) {
                        holes = ArrayList<LinearRing?>()
                    }
                    holes.add(geometryFactory.createLinearRing(parseKMLCoordinates(xmlStreamReader).requireNoNulls()))
                } else if (attributeNames.contains(elementName)) {
                    if (attributes == null) {
                        attributes = HashMap<String?, String?>()
                    }
                    attributes[elementName] = xmlStreamReader.getElementText()
                }
            }
            xmlStreamReader.next()
        }
        if (shell == null) {
            raiseParseError("No outer boundary for Polygon")
        }
        val polygon = geometryFactory.createPolygon(
            shell,
            if (holes == null) null else holes.toArray<LinearRing>(arrayOf<LinearRing>())
        )
        polygon.setUserData(attributes)
        return polygon
    }

    @Throws(javax.xml.stream.XMLStreamException::class, ParseException::class)
    private fun parseKMLMultiGeometry(xmlStreamReader: javax.xml.stream.XMLStreamReader): Geometry? {
        val geometries: MutableList<Geometry> = ArrayList<Geometry>()
        var firstParsedType: String? = null
        var allTypesAreSame = true
        while (xmlStreamReader.hasNext()) {
            if (xmlStreamReader.isStartElement()) {
                val elementName: String = xmlStreamReader.getLocalName()
                when (elementName) {
                    POINT, LINESTRING, POLYGON, MULTIGEOMETRY -> {
                        val geometry = parseKML(xmlStreamReader)
                        if (firstParsedType == null) {
                            firstParsedType = geometry!!.geometryType
                        } else if (firstParsedType != geometry!!.geometryType) {
                            allTypesAreSame = false
                        }
                        geometries.add(geometry)
                    }
                }
            }
            xmlStreamReader.next()
        }
        if (geometries.isEmpty()) {
            return null
        }
        if (geometries.size == 1) {
            return geometries[0]
        }
        return if (allTypesAreSame) {
            when (firstParsedType) {
                POINT -> geometryFactory.createMultiPoint(
                    prepareTypedArray<Point>(
                        geometries,
                        Point::class.java
                    )
                )

                LINESTRING -> geometryFactory.createMultiLineString(
                    prepareTypedArray(
                        geometries,
                        LineString::class.java
                    )
                )

                POLYGON -> geometryFactory.createMultiPolygon(
                    prepareTypedArray(
                        geometries,
                        Polygon::class.java
                    )
                )

                else -> geometryFactory.createGeometryCollection(
                    geometries.toTypedArray()
                )
            }
        } else {
            geometryFactory.createGeometryCollection(geometries.toTypedArray())
        }
    }

    @Throws(javax.xml.stream.XMLStreamException::class, ParseException::class)
    private fun parseKML(xmlStreamReader: javax.xml.stream.XMLStreamReader): Geometry? {
        var hasElement = false
        while (xmlStreamReader.hasNext()) {
            if (xmlStreamReader.isStartElement()) {
                hasElement = true
                break
            }
            xmlStreamReader.next()
        }
        if (!hasElement) {
            raiseParseError("Invalid KML format")
        }
        val elementName: String = xmlStreamReader.getLocalName()
        when (elementName) {
            POINT -> return parseKMLPoint(xmlStreamReader)
            LINESTRING -> return parseKMLLineString(xmlStreamReader)
            POLYGON -> return parseKMLPolygon(xmlStreamReader)
            MULTIGEOMETRY -> {
                xmlStreamReader.next()
                return parseKMLMultiGeometry(xmlStreamReader)
            }
        }
        raiseParseError("Unknown KML geometry type %s", elementName)
        return null
    }

    @Throws(javax.xml.stream.XMLStreamException::class, ParseException::class)
    private fun moveToElement(
        xmlStreamReader: javax.xml.stream.XMLStreamReader,
        elementName: String,
        endElementName: String
    ) {
        var elementFound = false
        while (xmlStreamReader.hasNext() && !(xmlStreamReader.isEndElement() && xmlStreamReader.getLocalName() == endElementName)) {
            if (xmlStreamReader.isStartElement() && xmlStreamReader.getLocalName() == elementName) {
                elementFound = true
                break
            }
            xmlStreamReader.next()
        }
        if (!elementFound) {
            raiseParseError(NO_ELEMENT_ERROR, elementName, endElementName)
        }
    }

    @Throws(ParseException::class)
    private fun raiseParseError(template: String, vararg parameters: Any) {
        throw ParseException(String.format(template, *parameters))
    }

    private inline fun <reified T> prepareTypedArray(geometryList: List<Geometry>, geomClass: java.lang.Class<T>): Array<T> {
//        return geometryList.toArray<T>(java.lang.reflect.Array.newInstance(geomClass, geometryList.size) as Array<T>)
        return geometryList.map { it as T }.toTypedArray()
    }

    private class KMLCoordinatesAndAttributes(
        val coordinates: Array<Coordinate?>?,
        val attributes: Map<String?, String?>?
    )

    companion object {
        private const val POINT = "Point"
        private const val LINESTRING = "LineString"
        private const val POLYGON = "Polygon"
        private const val MULTIGEOMETRY = "MultiGeometry"
        private const val COORDINATES = "coordinates"
        private const val OUTER_BOUNDARY_IS = "outerBoundaryIs"
        private const val INNER_BOUNDARY_IS = "innerBoundaryIs"
        private const val NO_ELEMENT_ERROR = "No element %s found in %s"
    }
}