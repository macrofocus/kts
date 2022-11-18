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
import org.locationtech.jts.geom.util.GeometryEditor.CoordinateOperation
import org.locationtech.jts.geom.util.GeometryEditor.CoordinateSequenceOperation
import org.locationtech.jts.geom.util.GeometryEditor.GeometryEditorOperation
import org.locationtech.jts.util.Assert

/**
 * A class which supports creating new [Geometry]s
 * which are modifications of existing ones,
 * maintaining the same type structure.
 * Geometry objects are intended to be treated as immutable.
 * This class "modifies" Geometrys
 * by traversing them, applying a user-defined
 * [GeometryEditorOperation], [CoordinateSequenceOperation] or [CoordinateOperation]
 * and creating new Geometrys with the same structure but
 * (possibly) modified components.
 *
 * Examples of the kinds of modifications which can be made are:
 *
 *  * the values of the coordinates may be changed.
 * The editor does not check whether changing coordinate values makes the result Geometry invalid
 *  * the coordinate lists may be changed
 * (e.g. by adding, deleting or modifying coordinates).
 * The modified coordinate lists must be consistent with their original parent component
 * (e.g. a <tt>LinearRing</tt> must always have at least 4 coordinates, and the first and last
 * coordinate must be equal)
 *  * components of the original geometry may be deleted
 * (e.g. holes may be removed from a Polygon, or LineStrings removed from a MultiLineString).
 * Deletions will be propagated up the component tree appropriately.
 *
 * All changes must be consistent with the original Geometry's structure
 * (e.g. a <tt>Polygon</tt> cannot be collapsed into a <tt>LineString</tt>).
 * If changing the structure is required, use a [GeometryTransformer].
 *
 * This class supports creating an edited Geometry
 * using a different `GeometryFactory` via the [.GeometryEditor]
 * constructor.
 * Examples of situations where this is required is if the geometry is
 * transformed to a new SRID and/or a new PrecisionModel.
 *
 * **Usage Notes**
 *
 *  * The resulting Geometry is not checked for validity.
 * If validity needs to be enforced, the new Geometry's
 * [Geometry.isValid] method should be called.
 *  * By default the UserData of the input geometry is not copied to the result.
 *
 * @see GeometryTransformer
 *
 * @see Geometry.isValid
 *
 * @version 1.7
 */
class GeometryEditor {
    /**
     * The factory used to create the modified Geometry.
     * If <tt>null</tt> the GeometryFactory of the input is used.
     */
    public var factory: GeometryFactory? = null
    private var isUserDataCopied = false

    /**
     * Creates a new GeometryEditor object which will create
     * edited [Geometry]s with the same [GeometryFactory] as the input Geometry.
     */
    constructor()

    /**
     * Creates a new GeometryEditor object which will create
     * edited [Geometry]s with the given [GeometryFactory].
     *
     * @param factory the GeometryFactory to create  edited Geometrys with
     */
    constructor(factory: GeometryFactory?) {
        this.factory = factory
    }

    /**
     * Sets whether the User Data is copied to the edit result.
     * Only the object reference is copied.
     *
     * @param isUserDataCopied true if the input user data should be copied.
     */
    fun setCopyUserData(isUserDataCopied: Boolean) {
        this.isUserDataCopied = isUserDataCopied
    }

    /**
     * Edit the input [Geometry] with the given edit operation.
     * Clients can create subclasses of [GeometryEditorOperation] or
     * [CoordinateOperation] to perform required modifications.
     *
     * @param geometry the Geometry to edit
     * @param operation the edit operation to carry out
     * @return a new [Geometry] which is the result of the editing (which may be empty)
     */
    fun edit(geometry: Geometry?, operation: GeometryEditorOperation): Geometry? {
        // nothing to do
        if (geometry == null) return null
        val result = editInternal(geometry, operation)
        if (isUserDataCopied) {
            result!!.setUserData(geometry.getUserData())
        }
        return result
    }

    private fun editInternal(geometry: Geometry, operation: GeometryEditorOperation): Geometry? {
        // if client did not supply a GeometryFactory, use the one from the input Geometry
        if (factory == null) {
            factory = geometry.factory
        }
        if (geometry is GeometryCollection) {
            return editGeometryCollection(
                geometry,
                operation
            )
        }
        if (geometry is Polygon) {
            return editPolygon(geometry, operation)
        }
        if (geometry is Point) {
            return operation.edit(geometry, factory)
        }
        if (geometry is LineString) {
            return operation.edit(geometry, factory)
        }
        Assert.shouldNeverReachHere("Unsupported Geometry class: " + geometry::class)
        return null
    }

    private fun editPolygon(
        polygon: Polygon,
        operation: GeometryEditorOperation
    ): Polygon {
        var newPolygon = operation.edit(polygon, factory) as Polygon
        // create one if needed
        if (newPolygon == null) newPolygon = factory!!.createPolygon()
        if (newPolygon.isEmpty) {
            //RemoveSelectedPlugIn relies on this behaviour. [Jon Aquino]
            return newPolygon
        }
        val shell = edit(newPolygon.exteriorRing, operation) as LinearRing?
        if (shell == null || shell.isEmpty) {
            //RemoveSelectedPlugIn relies on this behaviour. [Jon Aquino]
            return factory!!.createPolygon()
        }
        val holes: ArrayList<LinearRing> = ArrayList()
        for (i in 0 until newPolygon.getNumInteriorRing()) {
            val hole = edit(newPolygon.getInteriorRingN(i), operation) as LinearRing?
            if (hole == null || hole.isEmpty) {
                continue
            }
            holes.add(hole)
        }
        return factory!!.createPolygon(
            shell,
            holes.toTypedArray()
        )
    }

    private fun editGeometryCollection(
        collection: GeometryCollection, operation: GeometryEditorOperation
    ): GeometryCollection {
        // first edit the entire collection
        // MD - not sure why this is done - could just check original collection?
        val collectionForType = operation.edit(
            collection,
            factory
        ) as GeometryCollection

        // edit the component geometries
        val geometries: ArrayList<Any?> = ArrayList()
        for (i in 0 until collectionForType.numGeometries) {
            val geometry = edit(collectionForType.getGeometryN(i), operation)
            if (geometry == null || geometry.isEmpty) {
                continue
            }
            geometries.add(geometry)
        }
        if (collectionForType::class == MultiPoint::class) {
            return factory!!.createMultiPoint(geometries.map { it as Point }.toTypedArray())
        }
        if (collectionForType::class == MultiLineString::class) {
            return factory!!.createMultiLineString(geometries.map { it as LineString }.toTypedArray())
        }
        return if (collectionForType::class == MultiPolygon::class) {
            factory!!.createMultiPolygon(geometries.map { it as Polygon }.toTypedArray())
        } else factory!!.createGeometryCollection(geometries.map { it as Geometry }.toTypedArray())
    }

    /**
     * A interface which specifies an edit operation for Geometries.
     *
     * @version 1.7
     */
    interface GeometryEditorOperation {
        /**
         * Edits a Geometry by returning a new Geometry with a modification.
         * The returned geometry may be:
         *
         *  * the input geometry itself.
         * The returned Geometry might be the same as the Geometry passed in.
         *  * `null` if the geometry is to be deleted.
         *
         *
         * @param geometry the Geometry to modify
         * @param factory the factory with which to construct the modified Geometry
         * (may be different to the factory of the input geometry)
         * @return a new Geometry which is a modification of the input Geometry
         * @return null if the Geometry is to be deleted completely
         */
        fun edit(geometry: Geometry, factory: GeometryFactory?): Geometry
    }

    /**
     * A GeometryEditorOperation which does not modify
     * the input geometry.
     * This can be used for simple changes of
     * GeometryFactory (including PrecisionModel and SRID).
     *
     * @author mbdavis
     */
    class NoOpGeometryOperation : GeometryEditorOperation {
        override fun edit(geometry: Geometry, factory: GeometryFactory?): Geometry {
            return geometry
        }
    }

    /**
     * A [GeometryEditorOperation] which edits the coordinate list of a [Geometry].
     * Operates on Geometry subclasses which contains a single coordinate list.
     */
    abstract class CoordinateOperation : GeometryEditorOperation {
        override fun edit(geometry: Geometry, factory: GeometryFactory?): Geometry {
            if (geometry is LinearRing) {
                return factory!!.createLinearRing(
                    edit(
                        geometry.coordinates,
                        geometry
                    )
                )
            }
            if (geometry is LineString) {
                return factory!!.createLineString(
                    edit(
                        geometry.coordinates,
                        geometry
                    )
                )
            }
            if (geometry is Point) {
                val newCoordinates = edit(
                    geometry.coordinates,
                    geometry
                )
                return factory!!.createPoint(if (newCoordinates!!.size > 0) newCoordinates[0] else null)
            }
            return geometry
        }

        /**
         * Edits the array of [Coordinate]s from a [Geometry].
         *
         *
         * If it is desired to preserve the immutability of Geometrys,
         * if the coordinates are changed a new array should be created
         * and returned.
         *
         * @param coordinates the coordinate array to operate on
         * @param geometry the geometry containing the coordinate list
         * @return an edited coordinate array (which may be the same as the input)
         */
        abstract fun edit(
            coordinates: Array<Coordinate>?,
            geometry: Geometry
        ): Array<Coordinate>?
    }

    /**
     * A [GeometryEditorOperation] which edits the [CoordinateSequence]
     * of a [Geometry].
     * Operates on Geometry subclasses which contains a single coordinate list.
     */
    abstract class CoordinateSequenceOperation : GeometryEditorOperation {
        override fun edit(geometry: Geometry, factory: GeometryFactory?): Geometry {
            if (geometry is LinearRing) {
                return factory!!.createLinearRing(
                    edit(
                        geometry.coordinateSequence,
                        geometry
                    )
                )
            }
            if (geometry is LineString) {
                return factory!!.createLineString(
                    edit(
                        geometry.coordinateSequence,
                        geometry
                    )
                )
            }
            return if (geometry is Point) {
                factory!!.createPoint(
                    edit(
                        geometry.coordinateSequence,
                        geometry
                    )
                )
            } else geometry
        }

        /**
         * Edits a [CoordinateSequence] from a [Geometry].
         *
         * @param coordSeq the coordinate array to operate on
         * @param geometry the geometry containing the coordinate list
         * @return an edited coordinate sequence (which may be the same as the input)
         */
        abstract fun edit(
            coordSeq: CoordinateSequence?,
            geometry: Geometry?
        ): CoordinateSequence?
    }
}