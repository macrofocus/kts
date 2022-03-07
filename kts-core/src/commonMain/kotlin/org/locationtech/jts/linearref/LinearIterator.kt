/*
 * Copyright (c) 2016 Vivid Solutions.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.linearref

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.Lineal

/**
 * An iterator over the components and coordinates of a linear geometry
 * ([LineString]s and [MultiLineString]s.
 *
 * The standard usage pattern for a [LinearIterator] is:
 *
 * <pre>
 * for (LinearIterator it = new LinearIterator(...); it.hasNext(); it.next()) {
 * ...
 * int ci = it.getComponentIndex();   // for example
 * int vi = it.getVertexIndex();      // for example
 * ...
 * }
</pre> *
 *
 * @version 1.7
 */
class LinearIterator(linearGeom: Geometry, componentIndex: Int, vertexIndex: Int) {
    private val linearGeom: Geometry
    private val numLines: Int
    /**
     * Gets the [LineString] component the iterator is current at.
     * @return a linestring
     */
    /**
     * Invariant: currentLine <> null if the iterator is pointing at a valid coordinate
     */
    var line: LineString? = null
        private set

    /**
     * The component index of the vertex the iterator is currently at.
     * @return the current component index
     */
    var componentIndex = 0
        private set

    /**
     * The vertex index of the vertex the iterator is currently at.
     * @return the current vertex index
     */
    var vertexIndex = 0
        private set

    /**
     * Creates an iterator initialized to the start of a linear [Geometry]
     *
     * @param linear the linear geometry to iterate over
     * @throws IllegalArgumentException if linearGeom is not lineal
     */
    constructor(linear: Geometry) : this(linear, 0, 0) {}

    /**
     * Creates an iterator starting at
     * a [LinearLocation] on a linear [Geometry]
     *
     * @param linear the linear geometry to iterate over
     * @param start the location to start at
     * @throws IllegalArgumentException if linearGeom is not lineal
     */
    constructor(linear: Geometry, start: LinearLocation) : this(
        linear,
        start.componentIndex,
        segmentEndVertexIndex(start)
    ) {
    }

    /**
     * Creates an iterator starting at
     * a specified component and vertex in a linear [Geometry]
     *
     * @param linearGeom the linear geometry to iterate over
     * @param componentIndex the component to start at
     * @param vertexIndex the vertex to start at
     * @throws IllegalArgumentException if linearGeom is not lineal
     */
    init {
        if (linearGeom !is Lineal) throw IllegalArgumentException("Lineal geometry is required")
        this.linearGeom = linearGeom
        numLines = linearGeom.numGeometries
        this.componentIndex = componentIndex
        this.vertexIndex = vertexIndex
        loadCurrentLine()
    }

    private fun loadCurrentLine() {
        if (componentIndex >= numLines) {
            line = null
            return
        }
        line = linearGeom.getGeometryN(componentIndex) as LineString
    }

    /**
     * Tests whether there are any vertices left to iterator over.
     * Specifically, hasNext() return <tt>true</tt> if the
     * current state of the iterator represents a valid location
     * on the linear geometry.
     *
     * @return `true` if there are more vertices to scan
     */
    operator fun hasNext(): Boolean {
        if (componentIndex >= numLines) return false
        return if (componentIndex == numLines - 1
            && vertexIndex >= line!!.numPoints
        ) false else true
    }

    /**
     * Moves the iterator ahead to the next vertex and (possibly) linear component.
     */
    operator fun next() {
        if (!hasNext()) return
        vertexIndex++
        if (vertexIndex >= line!!.numPoints) {
            componentIndex++
            loadCurrentLine()
            vertexIndex = 0
        }
    }//LineString currentLine = (LineString) linear.getGeometryN(componentIndex);

    /**
     * Checks whether the iterator cursor is pointing to the
     * endpoint of a component [LineString].
     *
     * @return `true` if the iterator is at an endpoint
     */
    val isEndOfLine: Boolean
        get() {
            if (componentIndex >= numLines) return false
            //LineString currentLine = (LineString) linear.getGeometryN(componentIndex);
            return if (vertexIndex < line!!.numPoints - 1) false else true
        }

    /**
     * Gets the first [Coordinate] of the current segment.
     * (the coordinate of the current vertex).
     * @return a [Coordinate]
     */
    val segmentStart: Coordinate
        get() = line!!.getCoordinateN(vertexIndex)

    /**
     * Gets the second [Coordinate] of the current segment.
     * (the coordinate of the next vertex).
     * If the iterator is at the end of a line, `null` is returned.
     *
     * @return a [Coordinate] or `null`
     */
    val segmentEnd: Coordinate?
        get() = if (vertexIndex < line!!.numPoints - 1) line!!.getCoordinateN(vertexIndex + 1) else null

    companion object {
        private fun segmentEndVertexIndex(loc: LinearLocation): Int {
            return if (loc.segmentFraction > 0.0) loc.segmentIndex + 1 else loc.segmentIndex
        }
    }
}