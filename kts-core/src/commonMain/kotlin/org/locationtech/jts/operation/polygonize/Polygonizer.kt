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
package org.locationtech.jts.operation.polygonize

import kotlin.jvm.JvmField

import org.locationtech.jts.geom.*
import kotlin.jvm.JvmOverloads

/**
 * Polygonizes a set of [Geometry]s which contain linework that
 * represents the edges of a planar graph.
 * All types of Geometry are accepted as input;
 * the constituent linework is extracted as the edges to be polygonized.
 * The processed edges must be correctly noded; that is, they must only meet
 * at their endpoints.  Polygonization will accept incorrectly noded input
 * but will not form polygons from non-noded edges,
 * and reports them as errors.
 *
 * The Polygonizer reports the follow kinds of errors:
 *
 *  * **[Dangles][.getDangles]** - edges which have one or both ends which are not incident on another edge endpoint
 *  * **[Cut Edges][.getCutEdges]** - edges which are connected at both ends but which do not form part of polygon
 *  * **[Invalid Ring Lines][.getInvalidRingLines]** - edges which form rings which are invalid
 * (e.g. the component lines contain a self-intersection)
 *
 * The [.Polygonizer] constructor allows
 * extracting only polygons which form a valid polygonal result.
 * The set of extracted polygons is guaranteed to be edge-disjoint.
 * This is useful where it is known that the input lines form a
 * valid polygonal geometry (which may include holes or nested polygons).
 *
 * @version 1.7
 */
class Polygonizer
/**
 * Creates a polygonizer that extracts all polygons.
 */ @JvmOverloads constructor(private val extractOnlyPolygonal: Boolean = false) {
    /**
     * Adds every linear element in a [Geometry] into the polygonizer graph.
     */
    private class LineStringAdder(var p: Polygonizer) : GeometryComponentFilter {
        override fun filter(g: Geometry) {
            if (g is LineString) p.add(g)
        }
    }

    // default factory
    private val lineStringAdder = LineStringAdder(this)
    protected @JvmField var graph: PolygonizeGraph? = null

    // initialize with empty collections, in case nothing is computed
    protected var dangles: Collection<LineString> = ArrayList()
        get() {
            polygonize()
            return field
        }
    protected var cutEdges: MutableList<LineString> = ArrayList()
    @JvmField
    protected var invalidRingLines: MutableList<LineString> = ArrayList()
    protected @JvmField var holeList: MutableList<EdgeRing>? = null
    protected @JvmField var shellList: MutableList<EdgeRing>? = null
    protected @JvmField var polyList: List<Geometry>? = null
    private var isCheckingRingsValid = true
    private var geomFactory: GeometryFactory? = null
    /**
     * Creates a polygonizer, specifying whether a valid polygonal geometry must be created.
     * If the argument is `true`
     * then areas may be discarded in order to
     * ensure that the extracted geometry is a valid polygonal geometry.
     *
     * @param extractOnlyPolygonal true if a valid polygonal geometry should be extracted
     */
    /**
     * Adds a collection of geometries to the edges to be polygonized.
     * May be called multiple times.
     * Any dimension of Geometry may be added;
     * the constituent linework will be extracted and used.
     *
     * @param geomList a list of [Geometry]s with linework to be polygonized
     */
    fun add(geomList: Collection<*>) {
        val i = geomList.iterator()
        while (i.hasNext()) {
            val geometry = i.next() as Geometry
            add(geometry)
        }
    }

    /**
     * Add a [Geometry] to the edges to be polygonized.
     * May be called multiple times.
     * Any dimension of Geometry may be added;
     * the constituent linework will be extracted and used
     *
     * @param g a [Geometry] with linework to be polygonized
     */
    fun add(g: Geometry) {
        g.apply(lineStringAdder)
    }

    /**
     * Adds a linestring to the graph of polygon edges.
     *
     * @param line the [LineString] to add
     */
    private fun add(line: LineString) {
        // record the geometry factory for later use
        geomFactory = line.factory
        // create a new graph using the factory from the input Geometry
        if (graph == null) graph = PolygonizeGraph(geomFactory!!)
        graph!!.addEdge(line)
    }

    /**
     * Allows disabling the valid ring checking,
     * to optimize situations where invalid rings are not expected.
     *
     * The default is `true`.
     *
     * @param isCheckingRingsValid true if generated rings should be checked for validity
     */
    fun setCheckRingsValid(isCheckingRingsValid: Boolean) {
        this.isCheckingRingsValid = isCheckingRingsValid
    }

    /**
     * Gets the list of polygons formed by the polygonization.
     * @return a collection of [Polygon]s
     */
    val polygons: Collection<*>?
        get() {
            polygonize()
            return polyList
        }// result may not be valid Polygonal, so return as a GeometryCollection

    /**
     * Gets a geometry representing the polygons formed by the polygonization.
     * If a valid polygonal geometry was extracted the result is a [org.locationtech.jts.geom.Polygonal] geometry.
     *
     * @return a geometry containing the polygons
     */
    val geometry: Geometry
        get() {
            if (geomFactory == null) geomFactory = GeometryFactory()
            polygonize()
            return if (extractOnlyPolygonal) {
                geomFactory!!.buildGeometry(polyList!!)
            } else geomFactory!!.createGeometryCollection(GeometryFactory.toGeometryArray(polyList))
            // result may not be valid Polygonal, so return as a GeometryCollection
        }

    /**
     * Gets the list of cut edges found during polygonization.
     * @return a collection of the input [LineString]s which are cut edges
     */
    fun getCutEdges(): Collection<LineString> {
        polygonize()
        return cutEdges
    }

    /**
     * Gets the list of lines forming invalid rings found during polygonization.
     * @return a collection of the input [LineString]s which form invalid rings
     */
    fun getInvalidRingLines(): Collection<LineString> {
        polygonize()
        return invalidRingLines
    }

    /**
     * Performs the polygonization, if it has not already been carried out.
     */
    private fun polygonize() {
        // check if already computed
        if (polyList != null) return
        polyList = ArrayList()

        // if no geometries were supplied it's possible that graph is null
        if (graph == null) return
        dangles = graph!!.deleteDangles()
        cutEdges = graph!!.deleteCutEdges()
        val edgeRingList: MutableList<EdgeRing> = graph!!.edgeRings

        //Debug.printTime("Build Edge Rings");
        var validEdgeRingList: MutableList<EdgeRing> = ArrayList()
        val invalidRings: MutableList<EdgeRing> = ArrayList()
        if (isCheckingRingsValid) {
            findValidRings(edgeRingList, validEdgeRingList, invalidRings)
            invalidRingLines = extractInvalidLines(invalidRings)
        } else {
            validEdgeRingList = edgeRingList
        }
        //Debug.printTime("Validate Rings");
        findShellsAndHoles(validEdgeRingList)
        HoleAssigner.assignHolesToShells(holeList, shellList)

        // order the shells to make any subsequent processing deterministic
        shellList!!.sortWith(EdgeRing.EnvelopeComparator())

        //Debug.printTime("Assign Holes");
        var includeAll = true
        if (extractOnlyPolygonal) {
            findDisjointShells(shellList!!)
            includeAll = false
        }
        polyList = extractPolygons(shellList!!, includeAll)
    }

    private fun findValidRings(
        edgeRingList: MutableList<EdgeRing>,
        validEdgeRingList: MutableList<EdgeRing>,
        invalidRingList: MutableList<EdgeRing>
    ) {
        for (er in edgeRingList) {
            er.computeValid()
            if (er.isValid) validEdgeRingList.add(er) else invalidRingList.add(er)
        }
    }

    private fun findShellsAndHoles(edgeRingList: MutableList<EdgeRing>) {
        holeList = ArrayList()
        shellList = ArrayList()
        val i: Iterator<*> = edgeRingList.iterator()
        while (i.hasNext()) {
            val er: EdgeRing =
                i.next() as EdgeRing
            er.computeHole()
            if (er.isHole) holeList!!.add(er) else shellList!!.add(er)
        }
    }

    /**
     * Extracts unique lines for invalid rings,
     * discarding rings which correspond to outer rings and hence contain
     * duplicate linework.
     *
     * @param invalidRings
     * @return
     */
    private fun extractInvalidLines(invalidRings: MutableList<EdgeRing>): MutableList<LineString> {
        /**
         * Sort rings by increasing envelope area.
         * This causes inner rings to be processed before the outer rings
         * containing them, which allows outer invalid rings to be discarded
         * since their linework is already reported in the inner rings.
         */
        invalidRings.sortWith(EdgeRing.EnvelopeAreaComparator())
        /**
         * Scan through rings.  Keep only rings which have an adjacent EdgeRing
         * which is either valid or marked as not processed.
         * This avoids including outer rings which have linework which is duplicated.
         */
        val invalidLines: MutableList<LineString> = ArrayList()
        for (er in invalidRings) {
            if (isIncludedInvalid(er)) {
                invalidLines.add(er.lineString)
            }
            er.isProcessed = true
        }
        return invalidLines
    }

    /**
     * Tests if a invalid ring should be included in
     * the list of reported invalid rings.
     *
     * Rings are included only if they contain
     * linework which is not already in a valid ring,
     * or in an already-included ring.
     *
     * Because the invalid rings list is sorted by extent area,
     * this results in outer rings being discarded,
     * since all their linework is reported in the rings they contain.
     *
     * @param invalidRing the ring to test
     * @return true if the ring should be included
     */
    private fun isIncludedInvalid(invalidRing: EdgeRing): Boolean {
        for (de in invalidRing.edges) {
            val deAdj = de.sym as PolygonizeDirectedEdge
            val erAdj = deAdj.ring!!
            val isEdgeIncluded = erAdj.isValid || erAdj.isProcessed
            if (!isEdgeIncluded) return true
        }
        return false
    }

    companion object {
        private fun findDisjointShells(shellList: MutableList<EdgeRing>) {
            findOuterShells(shellList)
            var isMoreToScan: Boolean
            do {
                isMoreToScan = false
                val i: Iterator<*> = shellList.iterator()
                while (i.hasNext()) {
                    val er: EdgeRing =
                        i.next() as EdgeRing
                    if (er.isIncludedSet) {
                        continue
                    }
                    er.updateIncluded()
                    if (!er.isIncludedSet) {
                        isMoreToScan = true
                    }
                }
            } while (isMoreToScan)
        }

        /**
         * For each outer hole finds and includes a single outer shell.
         * This seeds the traversal algorithm for finding only polygonal shells.
         *
         * @param shellList the list of shell EdgeRings
         */
        private fun findOuterShells(shellList: MutableList<EdgeRing>) {
            val i: Iterator<*> = shellList.iterator()
            while (i.hasNext()) {
                val er: EdgeRing =
                    i.next() as EdgeRing
                val outerHoleER: EdgeRing? = er.getOuterHole()
                if (outerHoleER != null && !outerHoleER.isProcessed) {
                    er.isIncluded = true
                    outerHoleER.isProcessed = true
                }
            }
        }

        private fun extractPolygons(shellList: MutableList<EdgeRing>, includeAll: Boolean): List<Geometry> {
            val polyList: MutableList<Polygon> = ArrayList()
            val i: Iterator<*> = shellList.iterator()
            while (i.hasNext()) {
                val er: EdgeRing =
                    i.next() as EdgeRing
                if (includeAll || er.isIncluded) {
                    polyList.add(er.polygon)
                }
            }
            return polyList
        }
    }
}