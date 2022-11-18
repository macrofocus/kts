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

import org.locationtech.jts.algorithm.BoundaryNodeRule
import org.locationtech.jts.algorithm.LineIntersector
import org.locationtech.jts.algorithm.Orientation
import org.locationtech.jts.algorithm.PointLocator
import org.locationtech.jts.algorithm.locate.IndexedPointInAreaLocator
import org.locationtech.jts.algorithm.locate.PointOnGeometryLocator
import org.locationtech.jts.geom.*
import org.locationtech.jts.geomgraph.index.EdgeSetIntersector
import org.locationtech.jts.geomgraph.index.SegmentIntersector
import org.locationtech.jts.geomgraph.index.SimpleMCSweepLineIntersector
import org.locationtech.jts.util.Assert
import kotlin.jvm.JvmOverloads

/**
 * A GeometryGraph is a graph that models a given Geometry
 * @version 1.7
 */
class GeometryGraph @JvmOverloads constructor(// the index of this geometry as an argument to a spatial function (used for labelling)
    private val argIndex: Int, private val parentGeom: Geometry?, val boundaryNodeRule: BoundaryNodeRule? =
        BoundaryNodeRule.OGC_SFS_BOUNDARY_RULE
) : PlanarGraph() {
    /**
     * The lineEdgeMap is a map of the linestring components of the
     * parentGeometry to the edges which are derived from them.
     * This is used to efficiently perform findEdge queries
     */
    private val lineEdgeMap: MutableMap<Any?, Any?> = HashMap()

    /**
     * If this flag is true, the Boundary Determination Rule will used when deciding
     * whether nodes are in the boundary or not
     */
    private var useBoundaryDeterminationRule = true
    private var boundaryNodes: Collection<*>? = null
    private var hasTooFewPoints = false
    private var invalidPoint: Coordinate? = null
    private var areaPtLocator: PointOnGeometryLocator? = null

    // for use if geometry is not Polygonal
    private val ptLocator = PointLocator()
    private fun createEdgeSetIntersector(): EdgeSetIntersector {
        // various options for computing intersections, from slowest to fastest

        //private EdgeSetIntersector esi = new SimpleEdgeSetIntersector();
        //private EdgeSetIntersector esi = new MonotoneChainIntersector();
        //private EdgeSetIntersector esi = new NonReversingChainIntersector();
        //private EdgeSetIntersector esi = new SimpleSweepLineIntersector();
        //private EdgeSetIntersector esi = new MCSweepLineIntersector();

        //return new SimpleEdgeSetIntersector();
        return SimpleMCSweepLineIntersector()
    }

    init {
        parentGeom?.let { add(it) }
    }

    /*
   * This constructor is used by clients that wish to add Edges explicitly,
   * rather than adding a Geometry.  (An example is BufferOp).
   */
    // no longer used
    //  public GeometryGraph(int argIndex, PrecisionModel precisionModel, int SRID) {
    //    this(argIndex, null);
    //    this.precisionModel = precisionModel;
    //    this.SRID = SRID;
    //  }
    //  public PrecisionModel getPrecisionModel()
    //  {
    //    return precisionModel;
    //  }
    //  public int getSRID() { return SRID; }
    fun hasTooFewPoints(): Boolean {
        return hasTooFewPoints
    }

    fun getInvalidPoint(): Coordinate? {
        return invalidPoint
    }

    fun getGeometry(): Geometry? {
        return parentGeom
    }

    fun getBoundaryNodes(): Collection<*> {
        if (boundaryNodes == null) boundaryNodes = nodes.getBoundaryNodes(argIndex)
        return boundaryNodes!!
    }

    fun getBoundaryPoints(): Array<Coordinate?> {
        val coll = getBoundaryNodes()
        val pts = arrayOfNulls<Coordinate>(coll.size)
        var i = 0
        val it = coll.iterator()
        while (it.hasNext()) {
            val node: Node = it.next() as Node
            pts[i++] = node.getCoordinate()!!.copy()
        }
        return pts
    }

    fun findEdge(line: LineString?): Edge? {
        return lineEdgeMap[line] as Edge?
    }

    fun computeSplitEdges(edgelist: MutableList<Edge>) {
        val i: Iterator<*> = edges.iterator()
        while (i.hasNext()) {
            val e: Edge = i.next() as Edge
            e.eiList.addSplitEdges(edgelist)
        }
    }

    private fun add(g: Geometry) {
        if (g.isEmpty) return

        // check if this Geometry should obey the Boundary Determination Rule
        // all collections except MultiPolygons obey the rule
        if (g is MultiPolygon) useBoundaryDeterminationRule = false
        if (g is Polygon) addPolygon(g) else if (g is LineString) addLineString(
            g
        ) else if (g is Point) addPoint(g) else if (g is MultiPoint) addCollection(
            g
        ) else if (g is MultiLineString) addCollection(g) else if (g is MultiPolygon) addCollection(
            g
        ) else if (g is GeometryCollection) addCollection(g) else throw UnsupportedOperationException(g::class.simpleName)
    }

    private fun addCollection(gc: GeometryCollection) {
        for (i in 0 until gc.numGeometries) {
            val g = gc.getGeometryN(i)
            add(g)
        }
    }

    /**
     * Add a Point to the graph.
     */
    private fun addPoint(p: Point) {
        val coord = p.coordinate!!
        insertPoint(argIndex, coord, Location.INTERIOR)
    }

    /**
     * Adds a polygon ring to the graph.
     * Empty rings are ignored.
     *
     * The left and right topological location arguments assume that the ring is oriented CW.
     * If the ring is in the opposite orientation,
     * the left and right locations must be interchanged.
     */
    private fun addPolygonRing(lr: LinearRing?, cwLeft: Int, cwRight: Int) {
        // don't bother adding empty holes
        if (lr!!.isEmpty) return
        val coord: Array<Coordinate> = CoordinateArrays.removeRepeatedPoints(lr.coordinates)
        if (coord.size < 4) {
            hasTooFewPoints = true
            invalidPoint = coord[0]
            return
        }
        var left = cwLeft
        var right = cwRight
        if (Orientation.isCCW(coord)) {
            left = cwRight
            right = cwLeft
        }
        val e: Edge = Edge(
            coord,
            Label(argIndex, Location.BOUNDARY, left, right)
        )
        lineEdgeMap[lr] = e
        insertEdge(e)
        // insert the endpoint as a node, to mark that it is on the boundary
        insertPoint(argIndex, coord[0], Location.BOUNDARY)
    }

    private fun addPolygon(p: Polygon) {
        addPolygonRing(
            p.exteriorRing,
            Location.EXTERIOR,
            Location.INTERIOR
        )
        for (i in 0 until p.getNumInteriorRing()) {
            val hole = p.getInteriorRingN(i)

            // Holes are topologically labelled opposite to the shell, since
            // the interior of the polygon lies on their opposite side
            // (on the left, if the hole is oriented CW)
            addPolygonRing(
                hole,
                Location.INTERIOR,
                Location.EXTERIOR
            )
        }
    }

    private fun addLineString(line: LineString) {
        val coord: Array<Coordinate> = CoordinateArrays.removeRepeatedPoints(line.coordinates)
        if (coord.size < 2) {
            hasTooFewPoints = true
            invalidPoint = coord[0]
            return
        }

        // add the edge for the LineString
        // line edges do not have locations for their left and right sides
        val e: Edge = Edge(
            coord, Label(
                argIndex, Location.INTERIOR
            )
        )
        lineEdgeMap[line] = e
        insertEdge(e)
        /*
     * Add the boundary points of the LineString, if any.
     * Even if the LineString is closed, add both points as if they were endpoints.
     * This allows for the case that the node already exists and is a boundary point.
     */Assert.isTrue(coord.size >= 2, "found LineString with single point")
        insertBoundaryPoint(argIndex, coord[0])
        insertBoundaryPoint(argIndex, coord[coord.size - 1])
    }

    /**
     * Add an Edge computed externally.  The label on the Edge is assumed
     * to be correct.
     *
     * @param e Edge
     */
    fun addEdge(e: Edge) {
        insertEdge(e)
        val coord: Array<Coordinate> = e.getCoordinates()
        // insert the endpoint as a node, to mark that it is on the boundary
        insertPoint(argIndex, coord[0], Location.BOUNDARY)
        insertPoint(argIndex, coord[coord.size - 1], Location.BOUNDARY)
    }

    /**
     * Add a point computed externally.  The point is assumed to be a
     * Point Geometry part, which has a location of INTERIOR.
     *
     * @param pt Coordinate
     */
    fun addPoint(pt: Coordinate) {
        insertPoint(argIndex, pt, Location.INTERIOR)
    }

    /**
     * Compute self-nodes, taking advantage of the Geometry type to
     * minimize the number of intersection tests.  (E.g. rings are
     * not tested for self-intersection, since they are assumed to be valid).
     *
     * @param li the LineIntersector to use
     * @param computeRingSelfNodes if `false`, intersection checks are optimized to not test rings for self-intersection
     * @return the computed SegmentIntersector containing information about the intersections found
     */
    fun computeSelfNodes(li: LineIntersector?, computeRingSelfNodes: Boolean): SegmentIntersector {
        val si = SegmentIntersector(
            li!!, true, false
        )
        val esi = createEdgeSetIntersector()
        // optimize intersection search for valid Polygons and LinearRings
        val isRings = (parentGeom is LinearRing
                || parentGeom is Polygon
                || parentGeom is MultiPolygon)
        val computeAllSegments = computeRingSelfNodes || !isRings
        esi.computeIntersections(edges, si, computeAllSegments)

        //System.out.println("SegmentIntersector # tests = " + si.numTests);
        addSelfIntersectionNodes(argIndex)
        return si
    }

    fun computeEdgeIntersections(
        g: GeometryGraph,
        li: LineIntersector?,
        includeProper: Boolean
    ): SegmentIntersector {
        val si = SegmentIntersector(
            li!!, includeProper, true
        )
        si.setBoundaryNodes(getBoundaryNodes(), g.getBoundaryNodes())
        val esi = createEdgeSetIntersector()
        esi.computeIntersections(edges, g.edges, si)
        /*
for (Iterator i = g.edges.iterator(); i.hasNext();) {
Edge e = (Edge) i.next();
Debug.print(e.getEdgeIntersectionList());
}
*/return si
    }

    private fun insertPoint(argIndex: Int, coord: Coordinate, onLocation: Int) {
        val n: Node = nodes.addNode(coord)
        val lbl: Label? = n.label
        if (lbl == null) {
            n.label = Label(argIndex, onLocation)
        } else lbl.setLocation(argIndex, onLocation)
    }

    /**
     * Adds candidate boundary points using the current [BoundaryNodeRule].
     * This is used to add the boundary
     * points of dim-1 geometries (Curves/MultiCurves).
     */
    private fun insertBoundaryPoint(argIndex: Int, coord: Coordinate) {
        val n: Node = nodes.addNode(coord)
        // nodes always have labels
        val lbl: Label = n.label!!
        // the new point to insert is on a boundary
        var boundaryCount = 1
        // determine the current location for the point (if any)
        var loc = Location.NONE
        loc = lbl.getLocation(argIndex, Position.ON)
        if (loc == Location.BOUNDARY) boundaryCount++

        // determine the boundary status of the point according to the Boundary Determination Rule
        val newLoc = determineBoundary(boundaryNodeRule, boundaryCount)
        lbl.setLocation(argIndex, newLoc)
    }

    private fun addSelfIntersectionNodes(argIndex: Int) {
        val i: Iterator<*> = edges.iterator()
        while (i.hasNext()) {
            val e: Edge = i.next() as Edge
            val eLoc: Int = e.label!!.getLocation(argIndex)
            val eiIt: Iterator<*> = e.eiList.iterator()
            while (eiIt.hasNext()) {
                val ei: EdgeIntersection =
                    eiIt.next() as EdgeIntersection
                addSelfIntersectionNode(argIndex, ei.coord, eLoc)
            }
        }
    }

    /**
     * Add a node for a self-intersection.
     * If the node is a potential boundary node (e.g. came from an edge which
     * is a boundary) then insert it as a potential boundary node.
     * Otherwise, just add it as a regular node.
     */
    private fun addSelfIntersectionNode(argIndex: Int, coord: Coordinate, loc: Int) {
        // if this node is already a boundary node, don't change it
        if (isBoundaryNode(argIndex, coord)) return
        if (loc == Location.BOUNDARY && useBoundaryDeterminationRule) insertBoundaryPoint(
            argIndex,
            coord
        ) else insertPoint(argIndex, coord, loc)
    }
    // MD - experimental for now
    /**
     * Determines the [Location] of the given [Coordinate]
     * in this geometry.
     *
     * @param pt the point to test
     * @return the location of the point in the geometry
     */
    fun locate(pt: Coordinate?): Int {
        if (parentGeom is Polygonal && parentGeom.numGeometries > 50) {
            // lazily init point locator
            if (areaPtLocator == null) {
                areaPtLocator = IndexedPointInAreaLocator(parentGeom)
            }
            return areaPtLocator!!.locate(pt!!)
        }
        return ptLocator.locate(pt!!, parentGeom!!)
    }

    companion object {
        /**
         * This method implements the Boundary Determination Rule
         * for determining whether
         * a component (node or edge) that appears multiple times in elements
         * of a MultiGeometry is in the boundary or the interior of the Geometry
         * <br></br>
         * The SFS uses the "Mod-2 Rule", which this function implements
         * <br></br>
         * An alternative (and possibly more intuitive) rule would be
         * the "At Most One Rule":
         * isInBoundary = (componentCount == 1)
         */
        /*
  public static boolean isInBoundary(int boundaryCount)
  {
    // the "Mod-2 Rule"
    return boundaryCount % 2 == 1;
  }
  public static int determineBoundary(int boundaryCount)
  {
    return isInBoundary(boundaryCount) ? Location.BOUNDARY : Location.INTERIOR;
  }
*/
        /**
         * Determine boundary
         *
         * @param boundaryNodeRule Boundary node rule
         * @param boundaryCount the number of component boundaries that this point occurs in
         * @return boundary or interior
         */
        fun determineBoundary(boundaryNodeRule: BoundaryNodeRule?, boundaryCount: Int): Int {
            return if (boundaryNodeRule!!.isInBoundary(boundaryCount)) Location.BOUNDARY else Location.INTERIOR
        }
    }
}