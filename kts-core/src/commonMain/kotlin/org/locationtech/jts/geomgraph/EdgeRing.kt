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
package org.locationtech.jts.geomgraph

import org.locationtech.jts.algorithm.Orientation
import org.locationtech.jts.algorithm.PointLocation
import org.locationtech.jts.geom.*
import org.locationtech.jts.util.Assert

/**
 * @version 1.7
 */
abstract class EdgeRing(
    start: DirectedEdge?,
    protected var geometryFactory: GeometryFactory
) {
    protected var startDe // the directed edge which starts the list of edges for this EdgeRing
            : DirectedEdge? = null
    private var maxNodeDegree = -1
    private val edges: MutableList<Any?> = ArrayList() // the DirectedEdges making up this EdgeRing
    private val pts: MutableList<Coordinate> = ArrayList()
    private val label: Label =
        Label(Location.NONE) // label stores the locations of each geometry on the face surrounded by this ring
    private var ring // the ring created for this EdgeRing
            : LinearRing? = null
    private var isHole = false
    private var shell // if non-null, the ring is a hole and this EdgeRing is its containing shell
            : EdgeRing? = null
    private val holes: ArrayList<Any?> =
        ArrayList() // a list of EdgeRings which are holes in this EdgeRing

    init {
        computePoints(start)
        computeRing()
    }

    fun isIsolated(): Boolean {
        return label.getGeometryCount() == 1
    }

    fun isHole(): Boolean {
        //computePoints();
        return isHole
    }

    fun getCoordinate(i: Int): Coordinate {
        return pts.get(i)
    }

    fun getLinearRing(): LinearRing? {
        return ring
    }

    fun getLabel(): Label {
        return label
    }

    fun isShell(): Boolean {
        return shell == null
    }

    fun getShell(): EdgeRing? {
        return shell
    }

    fun setShell(shell: EdgeRing?) {
        this.shell = shell
        shell?.addHole(this)
    }

    fun addHole(ring: EdgeRing?) {
        holes.add(ring)
    }

    fun toPolygon(geometryFactory: GeometryFactory): Polygon {
        val holeLR =
            arrayOfNulls<LinearRing>(holes.size)
        for (i in holes.indices) {
            holeLR[i] = (holes.get(i) as EdgeRing).getLinearRing()
        }
        return geometryFactory.createPolygon(getLinearRing(), holeLR.requireNoNulls())
    }

    /**
     * Compute a LinearRing from the point list previously collected.
     * Test if the ring is a hole (i.e. if it is CCW) and set the hole flag
     * accordingly.
     */
    fun computeRing() {
        if (ring != null) return  // don't compute more than once
        val coord = arrayOfNulls<Coordinate>(pts.size)
        for (i in pts.indices) {
            coord[i] = pts.get(i) as Coordinate
        }
        ring = geometryFactory.createLinearRing(coord.requireNoNulls())
        isHole = Orientation.isCCW(ring!!.coordinates)
        //Debug.println( (isHole ? "hole - " : "shell - ") + WKTWriter.toLineString(new CoordinateArraySequence(ring.getCoordinates())));
    }

    abstract fun getNext(de: DirectedEdge): DirectedEdge?
    abstract fun setEdgeRing(de: DirectedEdge, er: EdgeRing)

    /**
     * Returns the list of DirectedEdges that make up this EdgeRing
     *
     * @return List of DirectedEdges
     */
    fun getEdges(): MutableList<*> {
        return edges
    }

    /**
     * Collect all the points from the DirectedEdges of this ring into a contiguous list
     */
    protected fun computePoints(start: DirectedEdge?) {
//System.out.println("buildRing");
        startDe = start
        var de: DirectedEdge? = start
        var isFirstEdge = true
        do {
//      Assert.isTrue(de != null, "found null Directed Edge");
            if (de == null) throw TopologyException("Found null DirectedEdge")
            if (de.edgeRing === this) throw TopologyException("Directed Edge visited twice during ring-building at " + de!!.coordinate)
            edges.add(de)
            //Debug.println(de);
//Debug.println(de.getEdge());
            val label: Label = de!!.label!!
            Assert.isTrue(label.isArea())
            mergeLabel(label)
            addPoints(de.edge, de.isForward, isFirstEdge)
            isFirstEdge = false
            setEdgeRing(de, this)
            de = getNext(de)
        } while (de !== startDe)
    }

    fun getMaxNodeDegree(): Int {
        if (maxNodeDegree < 0) computeMaxNodeDegree()
        return maxNodeDegree
    }

    private fun computeMaxNodeDegree() {
        maxNodeDegree = 0
        var de: DirectedEdge? = startDe
        do {
            val node: Node = de!!.node!!
            val degree: Int =
                (node.edges as DirectedEdgeStar)!!.getOutgoingDegree(this)
            if (degree > maxNodeDegree) maxNodeDegree = degree
            de = getNext(de)
        } while (de !== startDe)
        maxNodeDegree *= 2
    }

    fun setInResult() {
        var de: DirectedEdge? = startDe
        do {
            de!!.edge.isInResult = true
            de = de.next
        } while (de !== startDe)
    }

    protected fun mergeLabel(deLabel: Label) {
        mergeLabel(deLabel, 0)
        mergeLabel(deLabel, 1)
    }

    /**
     * Merge the RHS label from a DirectedEdge into the label for this EdgeRing.
     * The DirectedEdge label may be null.  This is acceptable - it results
     * from a node which is NOT an intersection node between the Geometries
     * (e.g. the end node of a LinearRing).  In this case the DirectedEdge label
     * does not contribute any information to the overall labelling, and is simply skipped.
     */
    protected fun mergeLabel(deLabel: Label, geomIndex: Int) {
        val loc: Int = deLabel.getLocation(geomIndex, Position.RIGHT)
        // no information to be had from this label
        if (loc == Location.NONE) return
        // if there is no current RHS value, set it
        if (label.getLocation(geomIndex) == Location.NONE) {
            label.setLocation(geomIndex, loc)
            return
        }
    }

    protected fun addPoints(edge: Edge, isForward: Boolean, isFirstEdge: Boolean) {
        val edgePts: Array<Coordinate> = edge.getCoordinates()
        if (isForward) {
            var startIndex = 1
            if (isFirstEdge) startIndex = 0
            for (i in startIndex until edgePts.size) {
                pts.add(edgePts[i])
            }
        } else { // is backward
            var startIndex = edgePts.size - 2
            if (isFirstEdge) startIndex = edgePts.size - 1
            for (i in startIndex downTo 0) {
                pts.add(edgePts[i])
            }
        }
    }

    /**
     * This method will cause the ring to be computed.
     * It will also check any holes, if they have been assigned.
     *
     * @param p point
     * @return true of ring contains point
     */
    fun containsPoint(p: Coordinate?): Boolean {
        val shell = getLinearRing()
        val env = shell!!.envelopeInternal
        if (!env.contains(p!!)) return false
        if (!PointLocation.isInRing(p, shell.coordinates)) return false
        val i: Iterator<*> = holes.iterator()
        while (i.hasNext()) {
            val hole = i.next() as EdgeRing
            if (hole.containsPoint(p)) return false
        }
        return true
    }
}