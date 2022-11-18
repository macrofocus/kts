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
package org.locationtech.jts.io.gml2

import org.locationtech.jts.geom.*
import org.locationtech.jts.legacy.Math.isNaN
import org.locationtech.jts.util.Assert

/**
 * Writes [Geometry]s as XML fragments in GML2 format.
 * Allows specifying the XML prefix, namespace and srsName
 * of the emitted GML.
 * Also allows adding custom root elements
 * to support GML extensions such as KML.
 * With appropriate settings for prefix (none) and custom root elements
 * this class can be used to write out geometry in KML format.
 *
 * An example of the output that can be generated is:
 *
 * <pre>
 * &lt;gml:LineString xmlns:gml='http://www.opengis.net/gml' srsName='foo'&gt;
 * &lt;gml:coordinates&gt;
 * 6.03,8.17 7.697,6.959 8.333,5.0 7.697,3.041 6.03,1.83 3.97,1.83 2.303,3.041 1.667,5.0 2.303,6.959 3.97,8.17
 * &lt;/gml:coordinates&gt;
 * &lt;/gml:LineString&gt;
</pre> *
 *
 * This class does not rely on any external XML libraries.
 *
 * @author David Zwiers, Vivid Solutions
 * @author Martin Davis
 */
class GMLWriter {
    private var startingIndentIndex = 0
    private var maxCoordinatesPerLine = 10
    private var emitNamespace = false
    private var isRootTag = false
    private var prefix: String? = GMLConstants.GML_PREFIX
    private val namespace: String = GMLConstants.GML_NAMESPACE
    private var srsName: String? = null
    private var customElements: Array<String>? = null

    /**
     * Creates a writer which outputs GML with default settings.
     * The defaults are:
     *
     *  * the namespace prefix is <tt>gml:</tt>
     *  * no namespace prefix declaration is written
     *  * no <tt>srsName</tt> attribute is written
     *
     */
    constructor() {}

    /**
     * Creates a writer which may emit the GML namespace prefix
     * declaration in the geometry root element.
     *
     * @param emitNamespace true if the GML namespace prefix declaration should be written
     * in the geometry root element
     */
    constructor(emitNamespace: Boolean) {
        setNamespace(emitNamespace)
    }

    /**
     * Specifies the namespace prefix to write on each GML tag.
     * A null or blank prefix may be used to indicate no prefix.
     *
     *
     * The default is to write <tt>gml:</tt> as the namespace prefix.
     *
     * @param prefix the namespace prefix to use (<tt>null</tt> or blank if none)
     */
    fun setPrefix(prefix: String?) {
        this.prefix = prefix
    }

    /**
     * Sets the value of the <tt>srsName</tt> attribute
     * to be written into the root geometry tag.
     * If the value is <tt>null</tt> or blank no srsName attribute will be written.
     * The provided value must be a valid XML attribute value
     * - it will not be XML-escaped.
     *
     *
     * The default is not to write the <tt>srsName</tt> attribute.
     *
     * @param srsName the srsName attribute value
     */
    fun setSrsName(srsName: String?) {
        this.srsName = srsName
    }

    /**
     * Determines whether a GML namespace declaration will be written in the
     * opening tag of geometries.  Useful in XML-aware environments which
     * parse the geometries before use, such as XSLT.
     *
     * @param emitNamespace true if the GML namespace prefix declaration
     * should be written in the root geometry element
     */
    fun setNamespace(emitNamespace: Boolean) {
        this.emitNamespace = emitNamespace
    }

    /**
     * Specifies a list of custom elements
     * which are written after the opening tag
     * of the root element.
     * The text contained in the string sequence should form valid XML markup.
     * The specified strings are written one per line immediately after
     * the root geometry tag line.
     *
     *
     * For instance, this is useful for adding KML-specific geometry parameters
     * such as <tt>&lt;extrude&gt;</tt>
     *
     * @param customElements a list of the custom element strings, or null if none
     */
    fun setCustomElements(customElements: Array<String>?) {
        this.customElements = customElements
    }

    /**
     * Sets the starting column index for pretty printing
     *
     * @param indent
     */
    fun setStartingIndentIndex(indent: Int) {
        var indent = indent
        if (indent < 0) indent = 0
        startingIndentIndex = indent
    }

    /**
     * Sets the number of coordinates printed per line.
     *
     * @param num
     */
    fun setMaxCoordinatesPerLine(num: Int) {
        if (num < 1) throw java.lang.IndexOutOfBoundsException(
            "Invalid coordinate count per line, must be > 0"
        )
        maxCoordinatesPerLine = num
    }

    /**
     * Writes a [Geometry] in GML2 format to a String.
     *
     * @param geom
     * @return String GML2 Encoded Geometry
     */
    fun write(geom: Geometry): String {
        val writer: java.io.StringWriter = java.io.StringWriter()
        try {
            write(geom, writer)
        } catch (ex: java.io.IOException) {
            Assert.shouldNeverReachHere()
        }
        return writer.toString()
    }

    /**
     * Writes a [Geometry] in GML2 format into a [Writer].
     *
     * @param geom Geometry to encode
     * @param writer Stream to encode to.
     * @throws IOException
     */
    @Throws(java.io.IOException::class)
    fun write(geom: Geometry, writer: java.io.Writer) {
        write(geom, writer, startingIndentIndex)
    }

    @Throws(java.io.IOException::class)
    private fun write(geom: Geometry, writer: java.io.Writer, level: Int) {
        isRootTag = true
        if (geom is Point) {
            writePoint(geom, writer, level)
        } else if (geom is LineString) {
            writeLineString(geom, writer, level)
        } else if (geom is Polygon) {
            writePolygon(geom, writer, level)
        } else if (geom is MultiPoint) {
            writeMultiPoint(geom, writer, level)
        } else if (geom is MultiLineString) {
            writeMultiLineString(geom, writer, level)
        } else if (geom is MultiPolygon) {
            writeMultiPolygon(geom, writer, level)
        } else if (geom is GeometryCollection) {
            writeGeometryCollection(
                geom, writer,
                startingIndentIndex
            )
        } else {
            throw IllegalArgumentException(
                "Unhandled geometry type: "
                        + geom.geometryType
            )
        }
        writer.flush()
    }

    // <gml:Point><gml:coordinates>1195156.78946687,382069.533723461</gml:coordinates></gml:Point>
    @Throws(java.io.IOException::class)
    private fun writePoint(p: Point, writer: java.io.Writer, level: Int) {
        startLine(level, writer)
        startGeomTag(GMLConstants.GML_POINT, p, writer)
        write(arrayOf(p.coordinate!!), writer, level + 1)
        startLine(level, writer)
        endGeomTag(GMLConstants.GML_POINT, writer)
    }

    //<gml:LineString><gml:coordinates>1195123.37289257,381985.763974674 1195120.22369473,381964.660533343 1195118.14929823,381942.597718511</gml:coordinates></gml:LineString>
    @Throws(java.io.IOException::class)
    private fun writeLineString(ls: LineString, writer: java.io.Writer, level: Int) {
        startLine(level, writer)
        startGeomTag(GMLConstants.GML_LINESTRING, ls, writer)
        write(ls.coordinates, writer, level + 1)
        startLine(level, writer)
        endGeomTag(GMLConstants.GML_LINESTRING, writer)
    }

    //<gml:LinearRing><gml:coordinates>1226890.26761027,1466433.47430292 1226880.59239079,1466427.03208053...></coordinates></gml:LinearRing>
    @Throws(java.io.IOException::class)
    private fun writeLinearRing(lr: LinearRing, writer: java.io.Writer, level: Int) {
        startLine(level, writer)
        startGeomTag(GMLConstants.GML_LINEARRING, lr, writer)
        write(lr.coordinates, writer, level + 1)
        startLine(level, writer)
        endGeomTag(GMLConstants.GML_LINEARRING, writer)
    }

    @Throws(java.io.IOException::class)
    private fun writePolygon(p: Polygon, writer: java.io.Writer, level: Int) {
        startLine(level, writer)
        startGeomTag(GMLConstants.GML_POLYGON, p, writer)
        startLine(level + 1, writer)
        startGeomTag(GMLConstants.GML_OUTER_BOUNDARY_IS, null, writer)
        writeLinearRing(p.exteriorRing!!, writer, level + 2)
        startLine(level + 1, writer)
        endGeomTag(GMLConstants.GML_OUTER_BOUNDARY_IS, writer)
        for (t in 0 until p.getNumInteriorRing()) {
            startLine(level + 1, writer)
            startGeomTag(GMLConstants.GML_INNER_BOUNDARY_IS, null, writer)
            writeLinearRing(p.getInteriorRingN(t), writer, level + 2)
            startLine(level + 1, writer)
            endGeomTag(GMLConstants.GML_INNER_BOUNDARY_IS, writer)
        }
        startLine(level, writer)
        endGeomTag(GMLConstants.GML_POLYGON, writer)
    }

    @Throws(java.io.IOException::class)
    private fun writeMultiPoint(mp: MultiPoint, writer: java.io.Writer, level: Int) {
        startLine(level, writer)
        startGeomTag(GMLConstants.GML_MULTI_POINT, mp, writer)
        for (t in 0 until mp.numGeometries) {
            startLine(level + 1, writer)
            startGeomTag(GMLConstants.GML_POINT_MEMBER, null, writer)
            writePoint(mp.getGeometryN(t) as Point, writer, level + 2)
            startLine(level + 1, writer)
            endGeomTag(GMLConstants.GML_POINT_MEMBER, writer)
        }
        startLine(level, writer)
        endGeomTag(GMLConstants.GML_MULTI_POINT, writer)
    }

    @Throws(java.io.IOException::class)
    private fun writeMultiLineString(
        mls: MultiLineString, writer: java.io.Writer,
        level: Int
    ) {
        startLine(level, writer)
        startGeomTag(GMLConstants.GML_MULTI_LINESTRING, mls, writer)
        for (t in 0 until mls.numGeometries) {
            startLine(level + 1, writer)
            startGeomTag(GMLConstants.GML_LINESTRING_MEMBER, null, writer)
            writeLineString(mls.getGeometryN(t) as LineString, writer, level + 2)
            startLine(level + 1, writer)
            endGeomTag(GMLConstants.GML_LINESTRING_MEMBER, writer)
        }
        startLine(level, writer)
        endGeomTag(GMLConstants.GML_MULTI_LINESTRING, writer)
    }

    @Throws(java.io.IOException::class)
    private fun writeMultiPolygon(mp: MultiPolygon, writer: java.io.Writer, level: Int) {
        startLine(level, writer)
        startGeomTag(GMLConstants.GML_MULTI_POLYGON, mp, writer)
        for (t in 0 until mp.numGeometries) {
            startLine(level + 1, writer)
            startGeomTag(GMLConstants.GML_POLYGON_MEMBER, null, writer)
            writePolygon(mp.getGeometryN(t) as Polygon, writer, level + 2)
            startLine(level + 1, writer)
            endGeomTag(GMLConstants.GML_POLYGON_MEMBER, writer)
        }
        startLine(level, writer)
        endGeomTag(GMLConstants.GML_MULTI_POLYGON, writer)
    }

    @Throws(java.io.IOException::class)
    private fun writeGeometryCollection(
        gc: GeometryCollection, writer: java.io.Writer,
        level: Int
    ) {
        startLine(level, writer)
        startGeomTag(GMLConstants.GML_MULTI_GEOMETRY, gc, writer)
        for (t in 0 until gc.numGeometries) {
            startLine(level + 1, writer)
            startGeomTag(GMLConstants.GML_GEOMETRY_MEMBER, null, writer)
            write(gc.getGeometryN(t), writer, level + 2)
            startLine(level + 1, writer)
            endGeomTag(GMLConstants.GML_GEOMETRY_MEMBER, writer)
        }
        startLine(level, writer)
        endGeomTag(GMLConstants.GML_MULTI_GEOMETRY, writer)
    }

    /**
     * Takes a list of coordinates and converts it to GML.<br></br>
     * 2d and 3d aware.
     *
     * @param coords array of coordinates
     * @throws IOException
     */
    @Throws(java.io.IOException::class)
    private fun write(coords: Array<Coordinate>, writer: java.io.Writer, level: Int) {
        startLine(level, writer)
        startGeomTag(GMLConstants.GML_COORDINATES, null, writer)
        var dim = 2
        if (coords.size > 0) {
            if (!isNaN(coords[0].z)) dim = 3
        }
        var isNewLine = true
        for (i in coords.indices) {
            if (isNewLine) {
                startLine(level + 1, writer)
                isNewLine = false
            }
            if (dim == 2) {
                writer.write("" + coords[i].x)
                writer.write(coordinateSeparator)
                writer.write("" + coords[i].y)
            } else if (dim == 3) {
                writer.write("" + coords[i].x)
                writer.write(coordinateSeparator)
                writer.write("" + coords[i].y)
                writer.write(coordinateSeparator)
                writer.write("" + coords[i].z)
            }
            writer.write(tupleSeparator)

            // break output lines to prevent them from getting too long
            if ((i + 1) % maxCoordinatesPerLine == 0 && i < coords.size - 1) {
                writer.write("\n")
                isNewLine = true
            }
        }
        if (!isNewLine) writer.write("\n")
        startLine(level, writer)
        endGeomTag(GMLConstants.GML_COORDINATES, writer)
    }

    @Throws(java.io.IOException::class)
    private fun startLine(level: Int, writer: java.io.Writer) {
        for (i in 0 until level) writer.write(INDENT)
    }

    @Throws(java.io.IOException::class)
    private fun startGeomTag(geometryName: String, g: Geometry?, writer: java.io.Writer) {
        writer.write(
            "<"
                    + if (prefix == null || "" == prefix) "" else "$prefix:"
        )
        writer.write(geometryName)
        writeAttributes(g, writer)
        writer.write(">\n")
        writeCustomElements(g, writer)
        isRootTag = false
    }

    @Throws(java.io.IOException::class)
    private fun writeAttributes(geom: Geometry?, writer: java.io.Writer) {
        if (geom == null) return
        if (!isRootTag) return
        if (emitNamespace) {
            writer.write(
                " xmlns"
                        + (if (prefix == null || "" == prefix) "" else ":$prefix")
                        + "='" + namespace + "'"
            )
        }
        if (srsName != null && srsName!!.length > 0) {
            writer.write(" " + GMLConstants.GML_ATTR_SRSNAME + "='" + srsName + "'")
            // MD - obsoleted
//			writer.write(geom.getSRID() + "");
        }
    }

    @Throws(java.io.IOException::class)
    private fun writeCustomElements(geom: Geometry?, writer: java.io.Writer) {
        if (geom == null) return
        if (!isRootTag) return
        if (customElements == null) return
        for (i in customElements!!.indices) {
            writer.write(customElements!![i])
            writer.write("\n")
        }
    }

    @Throws(java.io.IOException::class)
    private fun endGeomTag(geometryName: String, writer: java.io.Writer) {
        writer.write("</" + prefix())
        writer.write(geometryName)
        writer.write(">\n")
    }

    private fun prefix(): String {
        return if (prefix == null || prefix!!.length == 0) "" else "$prefix:"
    }

    companion object {
        private const val INDENT = "  "
        private const val coordinateSeparator = ","
        private const val tupleSeparator = " "
    }
}