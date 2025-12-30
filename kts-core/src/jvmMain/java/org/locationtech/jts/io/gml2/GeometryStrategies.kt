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
import org.locationtech.jts.io.gml2.GMLHandler.Handler
import org.locationtech.jts.util.StringUtil
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Container for GML2 Geometry parsing strategies which can be represented in JTS.
 *
 * @author David Zwiers, Vivid Solutions.
 */
object GeometryStrategies {
    private val strategies: HashMap<String, ParseStrategy> = loadStrategies()
    private fun loadStrategies(): HashMap<String, ParseStrategy> {
        val strats: HashMap<String, ParseStrategy> = HashMap()

        // point
        strats.put(GMLConstants.GML_POINT.lowercase(), object : ParseStrategy {
            @Throws(org.xml.sax.SAXException::class)
            override fun parse(arg: Handler, gf: GeometryFactory): Any {
                // one child, either a coord
                // or a coordinate sequence
                if (arg.children!!.size != 1) throw org.xml.sax.SAXException("Cannot create a point without exactly one coordinate")
                val srid = getSrid(arg.attrs!!, gf.sRID)
                val c: Any = arg.children!!.get(0)
                var p: Point? = null
                p = if (c is Coordinate) {
                    gf.createPoint(c)
                } else {
                    gf.createPoint(c as CoordinateSequence)
                }
                if (p.SRID != srid) p.SRID = srid
                return p
            }
        })

        // linestring
        strats.put(GMLConstants.GML_LINESTRING.lowercase(), object : ParseStrategy {
            @Throws(org.xml.sax.SAXException::class)
            override fun parse(arg: Handler, gf: GeometryFactory): Any {
                // one child, either a coord
                // or a coordinate sequence
                if (arg.children!!.size < 1) throw org.xml.sax.SAXException("Cannot create a linestring without atleast two coordinates or one coordinate sequence")
                val srid = getSrid(arg.attrs!!, gf.sRID)
                var ls: LineString? = null
                ls = if (arg.children!!.size == 1) {
                    // coord set
                    try {
                        val cs = arg.children!!.get(0) as CoordinateSequence
                        gf.createLineString(cs)
                    } catch (e: java.lang.ClassCastException) {
                        throw org.xml.sax.SAXException(
                            "Cannot create a linestring without atleast two coordinates or one coordinate sequence",
                            e
                        )
                    }
                } else {
                    try {
                        val coords =
                            arg.children!!.toTypedArray() as Array<Coordinate>
                        gf.createLineString(coords)
                    } catch (e: java.lang.ClassCastException) {
                        throw org.xml.sax.SAXException(
                            "Cannot create a linestring without atleast two coordinates or one coordinate sequence",
                            e
                        )
                    }
                }
                if (ls.SRID != srid) ls.SRID = srid
                return ls
            }
        })

        // linearring
        strats.put(GMLConstants.GML_LINEARRING.lowercase(), object : ParseStrategy {
            @Throws(org.xml.sax.SAXException::class)
            override fun parse(arg: Handler, gf: GeometryFactory): Any {
                // one child, either a coord
                // or a coordinate sequence
                if (arg.children!!.size != 1 && arg.children!!.size < 4) throw org.xml.sax.SAXException("Cannot create a linear ring without atleast four coordinates or one coordinate sequence")
                val srid = getSrid(arg.attrs!!, gf.sRID)
                var ls: LinearRing? = null
                ls = if (arg.children!!.size == 1) {
                    // coord set
                    try {
                        val cs = arg.children!!.get(0) as CoordinateSequence
                        gf.createLinearRing(cs)
                    } catch (e: java.lang.ClassCastException) {
                        throw org.xml.sax.SAXException(
                            "Cannot create a linear ring without atleast four coordinates or one coordinate sequence",
                            e
                        )
                    }
                } else {
                    try {
                        val coords =
                            arg.children!!.toTypedArray() as Array<Coordinate>
                        gf.createLinearRing(coords)
                    } catch (e: java.lang.ClassCastException) {
                        throw org.xml.sax.SAXException(
                            "Cannot create a linear ring without atleast four coordinates or one coordinate sequence",
                            e
                        )
                    }
                }
                if (ls!!.SRID != srid) ls!!.SRID = srid
                return ls!!
            }
        })

        // polygon
        strats.put(GMLConstants.GML_POLYGON.lowercase(), object : ParseStrategy {
            @Throws(org.xml.sax.SAXException::class)
            override fun parse(arg: Handler, gf: GeometryFactory): Any {
                // one child, either a coord
                // or a coordinate sequence
                if (arg.children!!.size < 1) throw org.xml.sax.SAXException("Cannot create a polygon without atleast one linear ring")
                val srid = getSrid(arg.attrs!!, gf.sRID)
                val outer = arg.children!!.get(0) as LinearRing // will be the first
                val t: MutableList<*>? =
                    if (arg.children!!.size > 1) arg.children!!.subList(1, arg.children!!.size) else null
                val inner = if (t == null) null else t.toTypedArray()
                val p = gf.createPolygon(outer, inner as Array<LinearRing>)
                if (p.SRID != srid) p.SRID = srid
                return p
            }
        })

        // box
        strats.put(GMLConstants.GML_BOX.lowercase(), object : ParseStrategy {
            @Throws(org.xml.sax.SAXException::class)
            override fun parse(arg: Handler, gf: GeometryFactory): Any {
                // one child, either a coord
                // or a coordinate sequence
                if (arg.children!!.size < 1 || arg.children!!.size > 2) throw org.xml.sax.SAXException("Cannot create a box without either two coords or one coordinate sequence")

//				int srid = getSrid(arg.attrs,gf.SRID);
                var box: Envelope? = null
                box = if (arg.children!!.size == 1) {
                    val cs = arg.children!!.get(0) as CoordinateSequence
                    cs.expandEnvelope(Envelope())
                } else {
                    Envelope((arg.children!!.get(0) as Coordinate), (arg.children!!.get(1) as Coordinate))
                }
                return box
            }
        })

        // multi-point
        strats.put(GMLConstants.GML_MULTI_POINT.lowercase(), object : ParseStrategy {
            @Throws(org.xml.sax.SAXException::class)
            override fun parse(arg: Handler, gf: GeometryFactory): Any {
                // one child, either a coord
                // or a coordinate sequence
                if (arg.children!!.size < 1) throw org.xml.sax.SAXException("Cannot create a multi-point without atleast one point")
                val srid = getSrid(arg.attrs!!, gf.sRID)
                val pts = arg.children!!.toTypedArray() as Array<Point>
                val mp = gf.createMultiPoint(pts)
                if (mp.SRID != srid) mp.SRID = srid
                return mp
            }
        })

        // multi-linestring
        strats.put(GMLConstants.GML_MULTI_LINESTRING.lowercase(), object : ParseStrategy {
            @Throws(org.xml.sax.SAXException::class)
            override fun parse(arg: Handler, gf: GeometryFactory): Any {
                // one child, either a coord
                // or a coordinate sequence
                if (arg.children!!.size < 1) throw org.xml.sax.SAXException("Cannot create a multi-linestring without atleast one linestring")
                val srid = getSrid(arg.attrs!!, gf.sRID)
                val lns = arg.children!!.toTypedArray() as Array<LineString>
                val mp = gf.createMultiLineString(lns)
                if (mp.SRID != srid) mp.SRID = srid
                return mp
            }
        })

        // multi-poly
        strats.put(GMLConstants.GML_MULTI_POLYGON.lowercase(), object : ParseStrategy {
            @Throws(org.xml.sax.SAXException::class)
            override fun parse(arg: Handler, gf: GeometryFactory): Any {
                // one child, either a coord
                // or a coordinate sequence
                if (arg.children!!.size < 1) throw org.xml.sax.SAXException("Cannot create a multi-polygon without atleast one polygon")
                val srid = getSrid(arg.attrs!!, gf.sRID)
                val plys = arg.children!!.toTypedArray() as Array<Polygon>
                val mp = gf.createMultiPolygon(plys)
                if (mp.SRID != srid) mp.SRID = srid
                return mp
            }
        })

        // multi-geom
        strats.put(GMLConstants.GML_MULTI_GEOMETRY.lowercase(), object : ParseStrategy {
            @Throws(org.xml.sax.SAXException::class)
            override fun parse(arg: Handler, gf: GeometryFactory): Any {
                // one child, either a coord
                // or a coordinate sequence
                if (arg.children!!.size < 1) throw org.xml.sax.SAXException("Cannot create a multi-polygon without atleast one geometry")
                val geoms =
                    arg.children!!.toTypedArray() as Array<Geometry>
                return gf.createGeometryCollection(geoms)
            }
        })

        // coordinates
        strats.put(GMLConstants.GML_COORDINATES.lowercase(), object : ParseStrategy {
            private val patterns: WeakHashMap<String,Pattern> = WeakHashMap<String,Pattern>()
            @Throws(org.xml.sax.SAXException::class)
            override fun parse(arg: Handler, gf: GeometryFactory): Any {
                // one child, either a coord
                // or a coordinate sequence
                if (arg.text == null || arg.text!!.length == 0) throw org.xml.sax.SAXException("Cannot create a coordinate sequence without text to parse")
                var decimal = "."
                var coordSeperator = ","
                var toupleSeperator = " "

                // get overides from coordinates
                if (arg.attrs!!.getIndex("decimal") >= 0) decimal =
                    arg.attrs!!.getValue("decimal") else if (arg.attrs!!.getIndex(
                        GMLConstants.GML_NAMESPACE,
                        "decimal"
                    ) >= 0
                ) decimal = arg.attrs!!.getValue(GMLConstants.GML_NAMESPACE, "decimal")
                if (arg.attrs!!.getIndex("cs") >= 0) coordSeperator =
                    arg.attrs!!.getValue("cs") else if (arg.attrs!!.getIndex(
                        GMLConstants.GML_NAMESPACE,
                        "cs"
                    ) >= 0
                ) coordSeperator = arg.attrs!!.getValue(GMLConstants.GML_NAMESPACE, "cs")
                if (arg.attrs!!.getIndex("ts") >= 0) toupleSeperator =
                    arg.attrs!!.getValue("ts") else if (arg.attrs!!.getIndex(
                        GMLConstants.GML_NAMESPACE,
                        "ts"
                    ) >= 0
                ) toupleSeperator = arg.attrs!!.getValue(GMLConstants.GML_NAMESPACE, "ts")

                // now to start parse
                var t: String = arg.text.toString()
                t = t.replace("\\s".toRegex(), " ")
                /**
                 * Remove spaces after commas, for when they are used as separators (default).
                 * This prevents coordinates being split by the tuple separator
                 */
                t = t.replace("\\s*,\\s*".toRegex(), ",")
                var ptn: Pattern = patterns.get(toupleSeperator) as Pattern
                if (ptn == null) {
                    var ts = toupleSeperator.toString()
                    if (ts.indexOf('\\') > -1) {
                        // need to escape it
                        ts = ts.replace("\\\\".toRegex(), "\\\\\\\\")
                    }
                    if (ts.indexOf('.') > -1) {
                        // need to escape it
                        ts = ts.replace("\\.".toRegex(), "\\\\.")
                    }
                    ptn = Pattern.compile(ts)
                    patterns.put(toupleSeperator, ptn)
                }
                val touples: Array<String?> = ptn.split(t.trim { it <= ' ' }) //  t.trim().split(toupleSeperator);
                if (touples.size == 0) throw org.xml.sax.SAXException("Cannot create a coordinate sequence without a touple to parse")

                // we may have null touples, so calculate the num first
                var numNonNullTouples = 0
                for (i in touples.indices) {
                    if (touples[i] != null && "" != touples[i]!!.trim { it <= ' ' }) {
                        if (i != numNonNullTouples) {
                            touples[numNonNullTouples] = touples[i] // always shift left
                        }
                        numNonNullTouples++
                    }
                }
                for (i in numNonNullTouples until touples.size) touples[i] = null

                // null touples now at end of array
                if (numNonNullTouples == 0) throw org.xml.sax.SAXException("Cannot create a coordinate sequence without a non-null touple to parse")
                var dim: Int = StringUtil.split(touples[0]!!, coordSeperator).size
                val cs = gf.coordinateSequenceFactory.create(numNonNullTouples, dim)
                dim = cs.dimension // max dim
                val replaceDec = "." != decimal
                for (i in 0 until numNonNullTouples) {
                    // for each touple, split, parse, add
                    ptn = patterns.get(coordSeperator) as Pattern
                    if (ptn == null) {
                        var ts = coordSeperator.toString()
                        if (ts.indexOf('\\') > -1) {
                            // need to escape it
                            ts = ts.replace("\\\\".toRegex(), "\\\\\\\\")
                        }
                        if (ts.indexOf('.') > -1) {
                            // need to escape it
                            ts = ts.replace("\\.".toRegex(), "\\\\.")
                        }
                        ptn = Pattern.compile(ts)
                        patterns.put(coordSeperator, ptn)
                    }
                    val coords: Array<String?> = ptn.split(touples[i]) //  touples[i].split(coordSeperator);
                    var dimIndex = 0
                    var j = 0
                    while (j < coords.size && j < dim) {
                        if (coords[j] != null && "" != coords[j]!!.trim { it <= ' ' }) {
                            val ordinate = (if (replaceDec) coords[j]!!
                                .replace(decimal.toRegex(), ".") else coords[j])!!.toDouble()
                            cs.setOrdinate(i, dimIndex++, ordinate)
                        }
                        j++
                    }
                    // fill remaining dim
                    while (dimIndex < dim) cs.setOrdinate(i, dimIndex++, Double.NaN)
                }
                return cs
            }
        })

        // coord
        strats.put(GMLConstants.GML_COORD.lowercase(), object : ParseStrategy {
            @Throws(org.xml.sax.SAXException::class)
            override fun parse(arg: Handler, gf: GeometryFactory): Any {
                // one child, either a coord
                // or a coordinate sequence
                if (arg.children!!.size < 1) throw org.xml.sax.SAXException("Cannot create a coordinate without atleast one axis")
                if (arg.children!!.size > 3) throw org.xml.sax.SAXException("Cannot create a coordinate with more than 3 axis")
                val axis = arg.children!!.toTypedArray() as Array<Double>
                val c = Coordinate()
                c.x = axis[0]
                if (axis.size > 1) c.y = axis[1]
                if (axis.size > 2) c.z = axis[2]
                return c
            }
        })
        val coord_child: ParseStrategy = object : ParseStrategy {
            @Throws(org.xml.sax.SAXException::class)
            override fun parse(arg: Handler, gf: GeometryFactory): Any? {
                return if (arg.text == null) null else java.lang.Double.valueOf(arg.text.toString())
            }
        }

        // coord-x
        strats.put(GMLConstants.GML_COORD_X.lowercase(), coord_child)

        // coord-y
        strats.put(GMLConstants.GML_COORD_Y.lowercase(), coord_child)

        // coord-z
        strats.put(GMLConstants.GML_COORD_Z.lowercase(), coord_child)
        val member: ParseStrategy = object : ParseStrategy {
            @Throws(org.xml.sax.SAXException::class)
            override fun parse(arg: Handler, gf: GeometryFactory): Any {
                if (arg.children!!.size != 1) throw org.xml.sax.SAXException("Geometry Members may only contain one geometry.")

                // type checking will occur in the parent geom collection.
                // may wish to add this in the future
                return arg.children!!.get(0)
            }
        }
        // outerBoundary - linear ring member
        strats.put(GMLConstants.GML_OUTER_BOUNDARY_IS.lowercase(), member)

        // innerBoundary - linear ring member
        strats.put(GMLConstants.GML_INNER_BOUNDARY_IS.lowercase(), member)

        // point member
        strats.put(GMLConstants.GML_POINT_MEMBER.lowercase(), member)

        // line string member
        strats.put(GMLConstants.GML_LINESTRING_MEMBER.lowercase(), member)

        // polygon member
        strats.put(GMLConstants.GML_POLYGON_MEMBER.lowercase(), member)
        return strats
    }

    fun getSrid(attrs: org.xml.sax.Attributes, defaultValue: Int): Int {
        var srs: String? = null
        if (attrs.getIndex(GMLConstants.GML_ATTR_SRSNAME) >= 0) srs =
            attrs.getValue(GMLConstants.GML_ATTR_SRSNAME) else if (attrs.getIndex(
                GMLConstants.GML_NAMESPACE,
                GMLConstants.GML_ATTR_SRSNAME
            ) >= 0
        ) srs = attrs.getValue(GMLConstants.GML_NAMESPACE, GMLConstants.GML_ATTR_SRSNAME)
        if (srs != null) {
            srs = srs.trim { it <= ' ' }
            if (srs != null && "" != srs) {
                try {
                    return srs.toInt()
                } catch (e: java.lang.NumberFormatException) {
                    val srsNum = extractIntSuffix(srs)
                    if (srsNum != null) {
                        try {
                            return srsNum.toInt()
                        } catch (e2: java.lang.NumberFormatException) {
                            // ignore
                        }
                    }
                }
            }
        }
        return defaultValue
    }

    var PATT_SUFFIX_INT: Pattern = Pattern.compile("(\\d+)$")
    fun extractIntSuffix(s: String?): String? {
        val matcher: Matcher = PATT_SUFFIX_INT.matcher(s)
        return if (matcher.find()) {
            matcher.group(1)
        } else null
    }

    /**
     * The ParseStrategy which should be employed.
     *
     * @param uri Not currently used, included for future work
     * @param localName Used to look up an appropriate parse strategy
     * @return The ParseStrategy which should be employed
     */
    fun findStrategy(uri: String?, localName: String?): ParseStrategy? {
        return if (localName == null) null else strategies.get(localName.lowercase(Locale.getDefault()))
    }

    /**
     * This set of strategies is not expected to be used directly outside of this distribution.
     *
     * The implementation of this class are intended to be used as static function points in C. These strategies should be associated with an element when the element begins. The strategy is utilized at the end of the element to create an object of value to the user.
     *
     * In this case all the objects are either java.lang.* or JTS Geometry objects
     *
     * @author David Zwiers, Vivid Solutions.
     */
    interface ParseStrategy {
        /**
         * @param arg Value to interpret
         * @param gf GeometryFactory
         * @return The interpreted value
         * @throws SAXException
         */
        @Throws(org.xml.sax.SAXException::class)
        fun parse(arg: Handler, gf: GeometryFactory): Any?
    }
}