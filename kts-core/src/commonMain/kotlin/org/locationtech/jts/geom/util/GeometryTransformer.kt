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
package org.locationtech.jts.geom.util

import org.locationtech.jts.geom.*

/**
 * A framework for processes which transform an input [Geometry] into
 * an output [Geometry], possibly changing its structure and type(s).
 * This class is a framework for implementing subclasses
 * which perform transformations on
 * various different Geometry subclasses.
 * It provides an easy way of applying specific transformations
 * to given geometry types, while allowing unhandled types to be simply copied.
 * Also, the framework ensures that if subcomponents change type
 * the parent geometries types change appropriately to maintain valid structure.
 * Subclasses will override whichever `transformX` methods
 * they need to to handle particular Geometry types.
 *
 * A typically usage would be a transformation class that transforms <tt>Polygons</tt> into
 * <tt>Polygons</tt>, <tt>LineStrings</tt> or <tt>Points</tt>, depending on the geometry of the input
 * (For instance, a simplification operation).
 * This class would likely need to override the [.transformMultiPolygon]
 * method to ensure that if input Polygons change type the result is a <tt>GeometryCollection</tt>,
 * not a <tt>MultiPolygon</tt>.
 *
 * The default behaviour of this class is simply to recursively transform
 * each Geometry component into an identical object by deep copying down
 * to the level of, but not including, coordinates.
 *
 * All `transformX` methods may return `null`,
 * to avoid creating empty or invalid geometry objects. This will be handled correctly
 * by the transformer.   `transform*XXX*` methods should always return valid
 * geometry - if they cannot do this they should return `null`
 * (for instance, it may not be possible for a transformLineString implementation
 * to return at least two points - in this case, it should return `null`).
 * The [.transform] method itself will always
 * return a non-null Geometry object (but this may be empty).
 *
 * @version 1.7
 *
 * @see GeometryEditor
 */
open class GeometryTransformer {
    /**
     * Utility function to make input geometry available
     *
     * @return the input geometry
     */
    /**
     * Possible extensions:
     * getParent() method to return immediate parent e.g. of LinearRings in Polygons
     */
    var inputGeometry: Geometry? = null
        private set
    protected var factory: GeometryFactory? = null
    // these could eventually be exposed to clients
    /**
     * `true` if empty geometries should not be included in the result
     */
    private val pruneEmptyGeometry = true

    /**
     * `true` if a homogenous collection result
     * from a [GeometryCollection] should still
     * be a general GeometryCollection
     */
    private val preserveGeometryCollectionType = true

    /**
     * `true` if the output from a collection argument should still be a collection
     */
    private val preserveCollections = false

    /**
     * `true` if the type of the input should be preserved
     */
    private val preserveType = false
    fun transform(inputGeom: Geometry): Geometry {
        inputGeometry = inputGeom
        factory = inputGeom.factory
        if (inputGeom is Point) return transformPoint(inputGeom, null)
        if (inputGeom is MultiPoint) return transformMultiPoint(inputGeom, null)
        if (inputGeom is LinearRing) return transformLinearRing(inputGeom, null)!!
        if (inputGeom is LineString) return transformLineString(inputGeom, null)
        if (inputGeom is MultiLineString) return transformMultiLineString(inputGeom, null)
        if (inputGeom is Polygon) return transformPolygon(inputGeom, null)!!
        if (inputGeom is MultiPolygon) return transformMultiPolygon(inputGeom, null)
        if (inputGeom is GeometryCollection) return transformGeometryCollection(inputGeom, null)
        throw IllegalArgumentException("Unknown Geometry subtype: " + inputGeom::class)
    }

    /**
     * Convenience method which provides standard way of
     * creating a [CoordinateSequence]
     *
     * @param coords the coordinate array to copy
     * @return a coordinate sequence for the array
     */
    protected fun createCoordinateSequence(coords: Array<Coordinate>?): CoordinateSequence {
        return factory!!.coordinateSequenceFactory.create(coords)
    }

    /**
     * Convenience method which provides a standard way of copying [CoordinateSequence]s
     * @param seq the sequence to copy
     * @return a deep copy of the sequence
     */
    protected fun copy(seq: CoordinateSequence?): CoordinateSequence {
        return seq!!.copy()
    }

    /**
     * Transforms a [CoordinateSequence].
     * This method should always return a valid coordinate list for
     * the desired result type.  (E.g. a coordinate list for a LineString
     * must have 0 or at least 2 points).
     * If this is not possible, return an empty sequence -
     * this will be pruned out.
     *
     * @param coords the coordinates to transform
     * @param parent the parent geometry
     * @return the transformed coordinates
     */
    protected open fun transformCoordinates(coords: CoordinateSequence?, parent: Geometry?): CoordinateSequence? {
        return copy(coords)
    }

    protected fun transformPoint(geom: Point, parent: Geometry?): Geometry {
        return factory!!.createPoint(
            transformCoordinates(geom.coordinateSequence, geom)
        )
    }

    protected fun transformMultiPoint(geom: MultiPoint, parent: Geometry?): Geometry {
        val transGeomList: MutableList<Geometry> = ArrayList()
        for (i in 0 until geom.numGeometries) {
            val transformGeom = transformPoint(geom.getGeometryN(i) as Point, geom)
                ?: continue
            if (transformGeom.isEmpty) continue
            transGeomList.add(transformGeom)
        }
        return if (transGeomList.isEmpty()) {
            factory!!.createMultiPoint()
        } else factory!!.buildGeometry(transGeomList)
    }

    /**
     * Transforms a LinearRing.
     * The transformation of a LinearRing may result in a coordinate sequence
     * which does not form a structurally valid ring (i.e. a degenerate ring of 3 or fewer points).
     * In this case a LineString is returned.
     * Subclasses may wish to override this method and check for this situation
     * (e.g. a subclass may choose to eliminate degenerate linear rings)
     *
     * @param geom the ring to simplify
     * @param parent the parent geometry
     * @return a LinearRing if the transformation resulted in a structurally valid ring
     * @return a LineString if the transformation caused the LinearRing to collapse to 3 or fewer points
     */
    protected open fun transformLinearRing(geom: LinearRing?, parent: Geometry?): Geometry? {
        val seq = transformCoordinates(geom!!.coordinateSequence, geom)
            ?: return factory!!.createLinearRing(null as CoordinateSequence?)
        val seqSize = seq.size()
        // ensure a valid LinearRing
        return if (seqSize in 1..3 && !preserveType) factory!!.createLineString(seq) else factory!!.createLinearRing(
            seq
        )
    }

    /**
     * Transforms a [LineString] geometry.
     *
     * @param geom
     * @param parent
     * @return
     */
    protected fun transformLineString(geom: LineString, parent: Geometry?): Geometry {
        // should check for 1-point sequences and downgrade them to points
        return factory!!.createLineString(
            transformCoordinates(geom.coordinateSequence, geom)
        )
    }

    protected fun transformMultiLineString(geom: MultiLineString, parent: Geometry?): Geometry {
        val transGeomList: MutableList<Geometry> = ArrayList()
        for (i in 0 until geom.numGeometries) {
            val transformGeom = transformLineString(geom.getGeometryN(i) as LineString, geom)
                ?: continue
            if (transformGeom.isEmpty) continue
            transGeomList.add(transformGeom)
        }
        return if (transGeomList.isEmpty()) {
            factory!!.createMultiLineString()
        } else factory!!.buildGeometry(transGeomList)
    }

    protected open fun transformPolygon(geom: Polygon, parent: Geometry?): Geometry? {
        var isAllValidLinearRings = true
        val shell = transformLinearRing(geom.exteriorRing, geom)

        // handle empty inputs, or inputs which are made empty
        val shellIsNullOrEmpty = shell == null || shell.isEmpty
        if (geom.isEmpty && shellIsNullOrEmpty) {
            return factory!!.createPolygon()
        }
        if (shellIsNullOrEmpty || shell !is LinearRing) isAllValidLinearRings = false
        val holes: ArrayList<Geometry> = ArrayList()
        for (i in 0 until geom.getNumInteriorRing()) {
            val hole = transformLinearRing(geom.getInteriorRingN(i), geom)
            if (hole == null || hole.isEmpty) {
                continue
            }
            if (hole !is LinearRing) isAllValidLinearRings = false
            holes.add(hole)
        }
        return if (isAllValidLinearRings) factory!!.createPolygon(
            shell as LinearRing,
            holes.map { it as LinearRing }.toTypedArray()
        ) else {
            val components: MutableList<Geometry> = ArrayList()
            if (shell != null) components.add(shell)
            components.addAll(holes)
            factory!!.buildGeometry(components)
        }
    }

    protected open fun transformMultiPolygon(geom: MultiPolygon, parent: Geometry?): Geometry {
        val transGeomList: MutableList<Geometry> = ArrayList()
        for (i in 0 until geom.numGeometries) {
            val transformGeom = transformPolygon(geom.getGeometryN(i) as Polygon, geom)
                ?: continue
            if (transformGeom.isEmpty) continue
            transGeomList.add(transformGeom)
        }
        return if (transGeomList.isEmpty()) {
            factory!!.createMultiPolygon()
        } else factory!!.buildGeometry(transGeomList)
    }

    protected fun transformGeometryCollection(geom: GeometryCollection, parent: Geometry?): Geometry {
        val transGeomList: MutableList<Geometry> = ArrayList()
        for (i in 0 until geom.numGeometries) {
            val transformGeom = transform(geom.getGeometryN(i)) ?: continue
            if (pruneEmptyGeometry && transformGeom.isEmpty) continue
            transGeomList.add(transformGeom)
        }
        return if (preserveGeometryCollectionType) factory!!.createGeometryCollection(
            GeometryFactory.toGeometryArray(
                transGeomList
            )
        ) else factory!!.buildGeometry(transGeomList)
    }
}