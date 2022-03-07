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

import org.locationtech.jts.geom.*
import org.locationtech.jts.util.Assert.shouldNeverReachHere

/**
 * Extracts the subline of a linear [Geometry] between
 * two [LinearLocation]s on the line.
 */
internal class ExtractLineByLocation(private val line: Geometry) {
    /**
     * Extracts a subline of the input.
     * If `end < start` the linear geometry computed will be reversed.
     *
     * @param start the start location
     * @param end the end location
     * @return a linear geometry
     */
    fun extract(start: LinearLocation, end: LinearLocation): Geometry? {
        return if (end.compareTo(start) < 0) {
            reverse(computeLinear(end, start))
        } else computeLinear(start, end)
    }

    private fun reverse(linear: Geometry): Geometry? {
        if (linear is Lineal) return linear.reverse()
        shouldNeverReachHere("non-linear geometry encountered")
        return null
    }

    /**
     * Assumes input is valid (e.g. start <= end)
     *
     * @param start
     * @param end
     * @return a linear geometry
     */
    private fun computeLine(start: LinearLocation, end: LinearLocation): LineString {
        val coordinates = line.coordinates
        val newCoordinates = CoordinateList()
        var startSegmentIndex = start.segmentIndex
        if (start.segmentFraction > 0.0) startSegmentIndex += 1
        var lastSegmentIndex = end.segmentIndex
        if (end.segmentFraction == 1.0) lastSegmentIndex += 1
        if (lastSegmentIndex >= coordinates!!.size) lastSegmentIndex = coordinates.size - 1
        // not needed - LinearLocation values should always be correct
        //Assert.isTrue(end.getSegmentFraction() <= 1.0, "invalid segment fraction value");
        if (!start.isVertex) newCoordinates.add(start.getCoordinate(line))
        for (i in startSegmentIndex..lastSegmentIndex) {
            newCoordinates.add(coordinates[i])
        }
        if (!end.isVertex) newCoordinates.add(end.getCoordinate(line))

        // ensure there is at least one coordinate in the result
        if (newCoordinates.size <= 0) newCoordinates.add(start.getCoordinate(line))
        var newCoordinateArray: Array<Coordinate> = newCoordinates.toCoordinateArray()
        /**
         * Ensure there is enough coordinates to build a valid line.
         * Make a 2-point line with duplicate coordinates, if necessary.
         * There will always be at least one coordinate in the coordList.
         */
        if (newCoordinateArray.size <= 1) {
            newCoordinateArray = arrayOf(newCoordinateArray[0], newCoordinateArray[0])
        }
        return line.factory.createLineString(newCoordinateArray)
    }

    /**
     * Assumes input is valid (e.g. start <= end)
     *
     * @param start
     * @param end
     * @return a linear geometry
     */
    private fun computeLinear(start: LinearLocation, end: LinearLocation): Geometry {
        val builder = LinearGeometryBuilder(line.factory)
        builder.setFixInvalidLines(true)
        if (!start.isVertex) builder.add(start.getCoordinate(line))
        val it = LinearIterator(line, start)
        while (it.hasNext()) {
            if (end.compareLocationValues(it.componentIndex, it.vertexIndex, 0.0)
                < 0
            ) break
            val pt = it.segmentStart
            builder.add(pt)
            if (it.isEndOfLine) builder.endLine()
            it.next()
        }
        if (!end.isVertex) builder.add(end.getCoordinate(line))
        return builder.geometry
    }

    /**
     * Computes a valid and normalized location
     * compatible with the values in a LinearIterator.
     * (I.e. segmentFractions of 1.0 are converted to the next highest coordinate index)
     */
    /*
  private LinearLocation normalize(LinearLocation loc)
  {
    int componentIndex = loc.getComponentIndex();
    int segmentIndex = loc.getSegmentIndex();
    double segmentFraction = loc.getSegmentFraction();
    if (segmentFraction < 0.0) {
      segmentFraction = 0.0;
    }
    if (segmentFraction > 1.0) {
      segmentFraction = 1.0;
    }
    if (componentIndex < 0) {
      componentIndex = 0;
      segmentIndex = 0;
      segmentFraction = 0.0;
    }
    if (segmentIndex < 0) {
      segmentIndex = 0;
      segmentFraction = 0.0;
    }
    if (segmentFraction == 1.0) {
      segmentFraction = 0.0;
      segmentIndex += 1;
    }
    return new LinearLocation(componentIndex, segmentIndex, segmentFraction);
  }
  */
    companion object {
        /**
         * Computes the subline of a [LineString] between
         * two [LinearLocation]s on the line.
         * If the start location is after the end location,
         * the computed linear geometry has reverse orientation to the input line.
         *
         * @param line the line to use as the baseline
         * @param start the start location
         * @param end the end location
         * @return the extracted subline
         */
        fun extract(line: Geometry, start: LinearLocation, end: LinearLocation): Geometry? {
            val ls = ExtractLineByLocation(line)
            return ls.extract(start, end)
        }
    }
}