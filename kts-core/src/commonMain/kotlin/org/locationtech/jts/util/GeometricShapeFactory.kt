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
package org.locationtech.jts.util

import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.util.AffineTransformation.Companion.rotationInstance
import org.locationtech.jts.legacy.Math.cos
import org.locationtech.jts.legacy.Math.min
import org.locationtech.jts.legacy.Math.pow
import org.locationtech.jts.legacy.Math.sin
import kotlin.jvm.JvmOverloads
import kotlin.math.PI

/**
 * Computes various kinds of common geometric shapes.
 * Provides various ways of specifying the location and extent
 * and rotations of the generated shapes,
 * as well as number of line segments used to form them.
 *
 *
 * **Example of usage:**
 * <pre>
 * GeometricShapeFactory gsf = new GeometricShapeFactory();
 * gsf.setSize(100);
 * gsf.setNumPoints(100);
 * gsf.setBase(new Coordinate(100, 100));
 * gsf.setRotation(0.5);
 * Polygon rect = gsf.createRectangle();
</pre> *
 *
 * @version 1.7
 */
open class GeometricShapeFactory @JvmOverloads constructor(protected var geomFact: GeometryFactory = GeometryFactory()) {
    protected var precModel: PrecisionModel? = null
    protected var dim = Dimensions()
    protected var nPts = 100

    /**
     * Default is no rotation.
     */
    protected var rotationAngle = 0.0
    /**
     * Create a shape factory which will create shapes using the given
     * [GeometryFactory].
     *
     * @param geomFact the factory to use
     */
    /**
     * Create a shape factory which will create shapes using the default
     * [GeometryFactory].
     */
    init {
        precModel = geomFact.precisionModel
    }

    fun setEnvelope(env: Envelope) {
        dim.envelope = env
    }

    /**
     * Sets the location of the shape by specifying the base coordinate
     * (which in most cases is the
     * lower left point of the envelope containing the shape).
     *
     * @param base the base coordinate of the shape
     */
    fun setBase(base: Coordinate?) {
        dim.base = base
    }

    /**
     * Sets the location of the shape by specifying the centre of
     * the shape's bounding box
     *
     * @param centre the centre coordinate of the shape
     */
    fun setCentre(centre: Coordinate?) {
        dim._centre = centre
    }

    /**
     * Sets the total number of points in the created [Geometry].
     * The created geometry will have no more than this number of points,
     * unless more are needed to create a valid geometry.
     */
    fun setNumPoints(nPts: Int) {
        this.nPts = nPts
    }

    /**
     * Sets the size of the extent of the shape in both x and y directions.
     *
     * @param size the size of the shape's extent
     */
    fun setSize(size: Double) {
        dim.setSize(size)
    }

    /**
     * Sets the width of the shape.
     *
     * @param width the width of the shape
     */
    fun setWidth(width: Double) {
        dim.width = width
    }

    /**
     * Sets the height of the shape.
     *
     * @param height the height of the shape
     */
    fun setHeight(height: Double) {
        dim.height = height
    }

    /**
     * Sets the rotation angle to use for the shape.
     * The rotation is applied relative to the centre of the shape.
     *
     * @param radians the rotation angle in radians.
     */
    fun setRotation(radians: Double) {
        rotationAngle = radians
    }

    protected fun rotate(geom: Geometry): Geometry {
        if (rotationAngle != 0.0) {
            val trans = rotationInstance(
                rotationAngle,
                dim.centre!!.x, dim.centre!!.y
            )
            geom.apply(trans)
        }
        return geom
    }

    /**
     * Creates a rectangular [Polygon].
     *
     * @return a rectangular Polygon
     */
    fun createRectangle(): Polygon {
        var ipt = 0
        var nSide = nPts / 4
        if (nSide < 1) nSide = 1
        val XsegLen = dim.envelope.width / nSide
        val YsegLen = dim.envelope.height / nSide
        val pts = arrayOfNulls<Coordinate>(4 * nSide + 1)
        val env = dim.envelope

        //double maxx = env.getMinX() + nSide * XsegLen;
        //double maxy = env.getMinY() + nSide * XsegLen;
        var i: Int = 0
        while (i < nSide) {
            val x = env.minX + i * XsegLen
            val y = env.minY
            pts[ipt++] = coord(x, y)
            i++
        }
        i = 0
        while (i < nSide) {
            val x = env.maxX
            val y = env.minY + i * YsegLen
            pts[ipt++] = coord(x, y)
            i++
        }
        i = 0
        while (i < nSide) {
            val x = env.maxX - i * XsegLen
            val y = env.maxY
            pts[ipt++] = coord(x, y)
            i++
        }
        i = 0
        while (i < nSide) {
            val x = env.minX
            val y = env.maxY - i * YsegLen
            pts[ipt++] = coord(x, y)
            i++
        }
        pts[ipt++] = Coordinate(pts[0]!!)
        val ring: LinearRing = geomFact.createLinearRing(pts.requireNoNulls())
        val poly = geomFact.createPolygon(ring)
        return rotate(poly) as Polygon
    }
    //* @deprecated use {@link createEllipse} instead
    /**
     * Creates a circular or elliptical [Polygon].
     *
     * @return a circle or ellipse
     */
    fun createCircle(): Polygon {
        return createEllipse()
    }

    /**
     * Creates an elliptical [Polygon].
     * If the supplied envelope is square the
     * result will be a circle.
     *
     * @return an ellipse or circle
     */
    fun createEllipse(): Polygon {
        val env = dim.envelope
        val xRadius = env.width / 2.0
        val yRadius = env.height / 2.0
        val centreX = env.minX + xRadius
        val centreY = env.minY + yRadius
        val pts = arrayOfNulls<Coordinate>(nPts + 1)
        var iPt = 0
        for (i in 0 until nPts) {
            val ang: Double = i * (2 * PI / nPts)
            val x: Double = xRadius * cos(ang) + centreX
            val y: Double = yRadius * sin(ang) + centreY
            pts[iPt++] = coord(x, y)
        }
        pts[iPt] = Coordinate(pts[0]!!)
        val ring: LinearRing = geomFact.createLinearRing(pts.requireNoNulls())
        val poly = geomFact.createPolygon(ring)
        return rotate(poly) as Polygon
    }

    /**
     * Creates a squircular [Polygon].
     *
     * @return a squircle
     */
    fun createSquircle(): Polygon
            /**
             * Creates a squircular [Polygon].
             *
             * @return a squircle
             */
    {
        return createSupercircle(4.0)
    }

    /**
     * Creates a supercircular [Polygon]
     * of a given positive power.
     *
     * @return a supercircle
     */
    fun createSupercircle(power: Double): Polygon {
        val recipPow = 1.0 / power
        val radius = dim.minSize / 2
        val centre = dim.centre!!
        val r4: Double = pow(radius, power)
        val xyInt: Double = pow(r4 / 2, recipPow)
        val nSegsInOct = nPts / 8
        val totPts = nSegsInOct * 8 + 1
        val pts = arrayOfNulls<Coordinate>(totPts)
        val xInc = xyInt / nSegsInOct
        for (i in 0..nSegsInOct) {
            var x = 0.0
            var y = radius
            if (i != 0) {
                x = xInc * i
                val x4: Double = pow(x, power)
                y = pow(r4 - x4, recipPow)
            }
            pts[i] = coordTrans(x, y, centre)
            pts[2 * nSegsInOct - i] = coordTrans(y, x, centre)
            pts[2 * nSegsInOct + i] = coordTrans(y, -x, centre)
            pts[4 * nSegsInOct - i] = coordTrans(x, -y, centre)
            pts[4 * nSegsInOct + i] = coordTrans(-x, -y, centre)
            pts[6 * nSegsInOct - i] = coordTrans(-y, -x, centre)
            pts[6 * nSegsInOct + i] = coordTrans(-y, x, centre)
            pts[8 * nSegsInOct - i] = coordTrans(-x, y, centre)
        }
        pts[pts.size - 1] = Coordinate(pts[0]!!)
        val ring: LinearRing = geomFact.createLinearRing(pts.requireNoNulls())
        val poly = geomFact.createPolygon(ring)
        return rotate(poly) as Polygon
    }

    /**
     * Creates an elliptical arc, as a [LineString].
     * The arc is always created in a counter-clockwise direction.
     * This can easily be reversed if required by using
     * {#link LineString.reverse()}
     *
     * @param startAng start angle in radians
     * @param angExtent size of angle in radians
     * @return an elliptical arc
     */
    fun createArc(
        startAng: Double,
        angExtent: Double
    ): LineString {
        val env = dim.envelope
        val xRadius = env.width / 2.0
        val yRadius = env.height / 2.0
        val centreX = env.minX + xRadius
        val centreY = env.minY + yRadius
        var angSize = angExtent
        if (angSize <= 0.0 || angSize > 2 * PI) angSize = 2 * PI
        val angInc = angSize / (nPts - 1)
        val pts = arrayOfNulls<Coordinate>(nPts)
        var iPt = 0
        for (i in 0 until nPts) {
            val ang = startAng + i * angInc
            val x: Double = xRadius * cos(ang) + centreX
            val y: Double = yRadius * sin(ang) + centreY
            pts[iPt++] = coord(x, y)
        }
        val line: LineString = geomFact.createLineString(pts.requireNoNulls())
        return rotate(line) as LineString
    }

    /**
     * Creates an elliptical arc polygon.
     * The polygon is formed from the specified arc of an ellipse
     * and the two radii connecting the endpoints to the centre of the ellipse.
     *
     * @param startAng start angle in radians
     * @param angExtent size of angle in radians
     * @return an elliptical arc polygon
     */
    fun createArcPolygon(startAng: Double, angExtent: Double): Polygon {
        val env = dim.envelope
        val xRadius = env.width / 2.0
        val yRadius = env.height / 2.0
        val centreX = env.minX + xRadius
        val centreY = env.minY + yRadius
        var angSize = angExtent
        if (angSize <= 0.0 || angSize > 2 * PI) angSize = 2 * PI
        val angInc = angSize / (nPts - 1)
        // double check = angInc * nPts;
        // double checkEndAng = startAng + check;
        val pts = arrayOfNulls<Coordinate>(nPts + 2)
        var iPt = 0
        pts[iPt++] = coord(centreX, centreY)
        for (i in 0 until nPts) {
            val ang = startAng + angInc * i
            val x: Double = xRadius * cos(ang) + centreX
            val y: Double = yRadius * sin(ang) + centreY
            pts[iPt++] = coord(x, y)
        }
        pts[iPt++] = coord(centreX, centreY)
        val ring: LinearRing = geomFact.createLinearRing(pts.requireNoNulls())
        val poly = geomFact.createPolygon(ring)
        return rotate(poly) as Polygon
    }

    protected fun coord(x: Double, y: Double): Coordinate {
        val pt = Coordinate(x, y)
        precModel!!.makePrecise(pt)
        return pt
    }

    protected fun coordTrans(x: Double, y: Double, trans: Coordinate): Coordinate {
        return coord(x + trans.x, y + trans.y)
    }

    protected class Dimensions {
        var base: Coordinate? = null
        var _centre: Coordinate? = null
        val centre: Coordinate?
            get() {
                if (_centre == null) {
                    _centre = Coordinate(base!!.x + width / 2, base!!.y + height / 2)
                }
                return _centre!!
            }
        var width = 0.0
        var height = 0.0


        fun setSize(size: Double) {
            height = size
            width = size
        }

        val minSize: Double
            get() = min(width, height)
        var envelope: Envelope
            get() {
                if (base != null) {
                    return Envelope(base!!.x, base!!.x + width, base!!.y, base!!.y + height)
                }
                return if (_centre != null) {
                    Envelope(
                        _centre!!.x - width / 2, _centre!!.x + width / 2,
                        _centre!!.y - height / 2, _centre!!.y + height / 2
                    )
                } else Envelope(0.0, width, 0.0, height)
            }
            set(env) {
                width = env.width
                height = env.height
                base = Coordinate(env.minX, env.minY)
                _centre = Coordinate(env.centre()!!)
            }
    }
}