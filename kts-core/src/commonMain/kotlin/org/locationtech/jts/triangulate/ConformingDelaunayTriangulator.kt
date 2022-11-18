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
package org.locationtech.jts.triangulate

import org.locationtech.jts.algorithm.ConvexHull
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.index.kdtree.KdNode
import org.locationtech.jts.index.kdtree.KdTree
import org.locationtech.jts.legacy.Math.max
import org.locationtech.jts.triangulate.quadedge.LastFoundQuadEdgeLocator
import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision
import org.locationtech.jts.triangulate.quadedge.Vertex
import org.locationtech.jts.util.Debug

/**
 * Computes a Conforming Delaunay Triangulation over a set of sites and a set of
 * linear constraints.
 *
 *
 * A conforming Delaunay triangulation is a true Delaunay triangulation. In it
 * each constraint segment is present as a union of one or more triangulation
 * edges. Constraint segments may be subdivided into two or more triangulation
 * edges by the insertion of additional sites. The additional sites are called
 * Steiner points, and are necessary to allow the segments to be faithfully
 * reflected in the triangulation while maintaining the Delaunay property.
 * Another way of stating this is that in a conforming Delaunay triangulation
 * every constraint segment will be the union of a subset of the triangulation
 * edges (up to tolerance).
 *
 *
 * A Conforming Delaunay triangulation is distinct from a Constrained Delaunay triangulation.
 * A Constrained Delaunay triangulation is not necessarily fully Delaunay,
 * and it contains the constraint segments exactly as edges of the triangulation.
 *
 *
 * A typical usage pattern for the triangulator is:
 * <pre>
 * ConformingDelaunayTriangulator cdt = new ConformingDelaunayTriangulator(sites, tolerance);
 *
 * // optional
 * cdt.setSplitPointFinder(splitPointFinder);
 * cdt.setVertexFactory(vertexFactory);
 *
 * cdt.setConstraints(segments, new ArrayList(vertexMap.values()));
 * cdt.formInitialDelaunay();
 * cdt.enforceConstraints();
 * subdiv = cdt.getSubdivision();
</pre> *
 *
 * @author David Skea
 * @author Martin Davis
 */
class ConformingDelaunayTriangulator(
    initialVertices: Collection<Vertex>,
    tolerance: Double
) {
    private val initialVertices // List<Vertex>
            : MutableList<Vertex>
    private var segVertices // List<Vertex>
            : MutableList<Vertex>? = null

    // MD - using a Set doesn't seem to be much faster
    // private Set segments = new HashSet();
    private var segments: MutableList<Segment> = ArrayList() // List<Segment>

    /**
     * Gets the [QuadEdgeSubdivision] which represents the triangulation.
     *
     * @return a subdivision
     */
    var subdivision: QuadEdgeSubdivision? = null
        private set
    private var incDel: IncrementalDelaunayTriangulator? = null

    /**
     * Gets the convex hull of all the sites in the triangulation,
     * including constraint vertices.
     * Only valid after the constraints have been enforced.
     *
     * @return the convex hull of the sites
     */
    var convexHull: Geometry? = null
        private set
    private var splitFinder: ConstraintSplitPointFinder = NonEncroachingSplitPointFinder()

    /**
     * Gets the [KdTree] which contains the vertices of the triangulation.
     *
     * @return a KdTree
     */
    var kDT: KdTree? = null
    private var vertexFactory: ConstraintVertexFactory? = null

    // allPointsEnv expanded by a small buffer
    private var computeAreaEnv: Envelope? = null

    // records the last split point computed, for error reporting
    private var splitPt: Coordinate? = null

    /**
     * Gets the tolerance value used to construct the triangulation.
     *
     * @return a tolerance value
     */
    val tolerance // defines if two sites are the same.
            : Double

    /**
     * Sets the constraints to be conformed to by the computed triangulation.
     * The constraints must not contain duplicate segments (up to orientation).
     * The unique set of vertices (as [ConstraintVertex]es)
     * forming the constraints must also be supplied.
     * Supplying it explicitly allows the ConstraintVertexes to be initialized
     * appropriately (e.g. with external data), and avoids re-computing the unique set
     * if it is already available.
     *
     * @param segments a list of the constraint [Segment]s
     * @param segVertices the set of unique [ConstraintVertex]es referenced by the segments
     */
    fun setConstraints(segments: MutableList<Segment>, segVertices: MutableList<Vertex>?) {
        this.segments = segments
        this.segVertices = segVertices
    }

    /**
     * Sets the [ConstraintSplitPointFinder] to be
     * used during constraint enforcement.
     * Different splitting strategies may be appropriate
     * for special situations.
     *
     * @param splitFinder the ConstraintSplitPointFinder to be used
     */
    fun setSplitPointFinder(splitFinder: ConstraintSplitPointFinder) {
        this.splitFinder = splitFinder
    }

    /**
     * Gets the <tt>ConstraintVertexFactory</tt> used to create new constraint vertices at split points.
     *
     * @return a new constraint vertex
     */
    fun getVertexFactory(): ConstraintVertexFactory? {
        return vertexFactory
    }

    /**
     * Sets a custom [ConstraintVertexFactory] to be used
     * to allow vertices carrying extra information to be created.
     *
     * @param vertexFactory the ConstraintVertexFactory to be used
     */
    fun setVertexFactory(vertexFactory: ConstraintVertexFactory?) {
        this.vertexFactory = vertexFactory
    }

    /**
     * Gets the sites (vertices) used to initialize the triangulation.
     *
     * @return a List of Vertex
     */
    fun getInitialVertices(): MutableList<Vertex> {
        return initialVertices
    }

    /**
     * Gets the [Segment]s which represent the constraints.
     *
     * @return a collection of Segments
     */
    val constraintSegments: Collection<*>
        get() = segments

    // ==================================================================
    private fun computeBoundingBox() {
        val vertexEnv = computeVertexEnvelope(initialVertices)
        val segEnv = computeVertexEnvelope(segVertices)
        val allPointsEnv = Envelope(vertexEnv)
        allPointsEnv.expandToInclude(segEnv)
        val deltaX = allPointsEnv.width * 0.2
        val deltaY = allPointsEnv.height * 0.2
        val delta: Double = max(deltaX, deltaY)
        computeAreaEnv = Envelope(allPointsEnv)
        computeAreaEnv!!.expandBy(delta)
    }

    private fun computeConvexHull() {
        val fact = GeometryFactory()
        val coords = pointArray
        val hull = ConvexHull(coords, fact)
        convexHull = hull.convexHull
    }

    // /**
    // * Adds the segments in the Convex Hull of all sites in the input data as
    // linear constraints.
    // * This is required if TIN Refinement is performed. The hull segments are
    // flagged with a
    // unique
    // * data object to allow distinguishing them.
    // *
    // * @param convexHullSegmentData the data object to attach to each convex
    // hull segment
    // */
    // private void addConvexHullToConstraints(Object convexHullSegmentData) {
    // Coordinate[] coords = convexHull.getCoordinates();
    // for (int i = 1; i < coords.length; i++) {
    // Segment s = new Segment(coords[i - 1], coords[i], convexHullSegmentData);
    // addConstraintIfUnique(s);
    // }
    // }
    // private void addConstraintIfUnique(Segment r) {
    // boolean exists = false;
    // Iterator it = segments.iterator();
    // Segment s = null;
    // while (it.hasNext()) {
    // s = (Segment) it.next();
    // if (r.equalsTopo(s)) {
    // exists = true;
    // }
    // }
    // if (!exists) {
    // segments.add((Object) r);
    // }
    // }
    val pointArray: Array<Coordinate>
        get() {
            val pts = arrayOfNulls<Coordinate>(
                initialVertices.size
                        + segVertices!!.size
            )
            var index = 0
            val i: Iterator<*> = initialVertices.iterator()
            while (i.hasNext()) {
                val v = i.next() as Vertex
                pts[index++] = v.coordinate
            }
            val i2: Iterator<*> = segVertices!!.iterator()
            while (i2.hasNext()) {
                val v = i2.next() as Vertex
                pts[index++] = v.coordinate
            }
            return pts.requireNoNulls()
        }

    private fun createVertex(p: Coordinate): ConstraintVertex {
        var v: ConstraintVertex? = null
        if (vertexFactory != null) v = vertexFactory!!.createVertex(p, null) else v =
            ConstraintVertex(p)
        return v!!
    }

    /**
     * Creates a vertex on a constraint segment
     *
     * @param p the location of the vertex to create
     * @param seg the constraint segment it lies on
     * @return the new constraint vertex
     */
    private fun createVertex(
        p: Coordinate?,
        seg: Segment
    ): ConstraintVertex {
        var v: ConstraintVertex? = null
        if (vertexFactory != null) v = vertexFactory!!.createVertex(p, seg) else v =
            ConstraintVertex(p)
        v!!.isOnConstraint = true
        return v
    }

    /**
     * Inserts all sites in a collection
     *
     * @param vertices a collection of ConstraintVertex
     */
    private fun insertSites(vertices: Collection<*>?) {
//        Debug.println("Adding sites: " + vertices!!.size)
        val i = vertices!!.iterator()
        while (i.hasNext()) {
            val v: ConstraintVertex =
                i.next() as ConstraintVertex
            insertSite(v)
        }
    }

    private fun insertSite(v: ConstraintVertex): ConstraintVertex {
        val kdnode = kDT!!.insert(v.coordinate, v)
        if (!kdnode.isRepeated) {
            incDel!!.insertSite(v)
        } else {
            val snappedV: ConstraintVertex? =
                kdnode.data as ConstraintVertex?
            snappedV!!.merge(v)
            return snappedV
            // testing
            // if ( v.isOnConstraint() && ! currV.isOnConstraint()) {
            // System.out.println(v);
            // }
        }
        return v
    }

    /**
     * Inserts a site into the triangulation, maintaining the conformal Delaunay property.
     * This can be used to further refine the triangulation if required
     * (e.g. to approximate the medial axis of the constraints,
     * or to improve the grading of the triangulation).
     *
     * @param p the location of the site to insert
     */
    fun insertSite(p: Coordinate) {
        insertSite(createVertex(p))
    }
    // ==================================================================
    /**
     * Computes the Delaunay triangulation of the initial sites.
     */
    fun formInitialDelaunay() {
        computeBoundingBox()
        subdivision = QuadEdgeSubdivision(computeAreaEnv!!, tolerance)
        subdivision!!.setLocator(LastFoundQuadEdgeLocator(subdivision!!))
        incDel = IncrementalDelaunayTriangulator(subdivision!!)
        insertSites(initialVertices)
    }

    /**
     * Creates a Conforming Delaunay Triangulation based on the given
     * unconstrained initial vertices. The initial vertex set should not contain
     * any vertices which appear in the constraint set.
     *
     * @param initialVertices
     * a collection of [ConstraintVertex]
     * @param tolerance
     * the distance tolerance below which points are considered identical
     */
    init {
        this.initialVertices = ArrayList(initialVertices)
        this.tolerance = tolerance
        kDT = KdTree(tolerance)
    }

    /**
     * Enforces the supplied constraints into the triangulation.
     *
     * @throws ConstraintEnforcementException
     * if the constraints cannot be enforced
     */
    fun enforceConstraints() {
        addConstraintVertices()
        // if (true) return;
        var count = 0
        var splits = 0
        do {
            splits = enforceGabriel(segments)
            count++
//            Debug.println(
//                "Iter: " + count + "   Splits: " + splits
//                        + "   Current # segments = " + segments.size
//            )
        } while (splits > 0 && count < MAX_SPLIT_ITER)
        if (count == MAX_SPLIT_ITER) {
//            Debug.println("ABORTED! Too many iterations while enforcing constraints")
//            if (!Debug.isDebugging) throw ConstraintEnforcementException(
//                "Too many splitting iterations while enforcing constraints.  Last split point was at: ",
//                splitPt
//            )
        }
    }

    private fun addConstraintVertices() {
        computeConvexHull()
        // insert constraint vertices as sites
        insertSites(segVertices)
    }

    /*
	 * private List findMissingConstraints() { List missingSegs = new ArrayList();
	 * for (int i = 0; i < segments.size(); i++) { Segment s = (Segment)
	 * segments.get(i); QuadEdge q = subdiv.locate(s.getStart(), s.getEnd()); if
	 * (q == null) missingSegs.add(s); } return missingSegs; }
	 */
    private fun enforceGabriel(segsToInsert: MutableCollection<Segment>): Int {
        val newSegments: MutableList<Segment> = ArrayList()
        var splits = 0
        val segsToRemove: MutableList<Segment> = ArrayList()

        /**
         * On each iteration must always scan all constraint (sub)segments, since
         * some constraints may be rebroken by Delaunay triangle flipping caused by
         * insertion of another constraint. However, this process must converge
         * eventually, with no splits remaining to find.
         */
        val i: Iterator<*> = segsToInsert.iterator()
        while (i.hasNext()) {
            val seg: Segment = i.next() as Segment
            // System.out.println(seg);
            val encroachPt = findNonGabrielPoint(seg) ?: continue
            // no encroachment found - segment must already be in subdivision

            // compute split point
            splitPt = splitFinder.findSplitPoint(seg, encroachPt)
            val splitVertex: ConstraintVertex = createVertex(splitPt, seg)

            // DebugFeature.addLineSegment(DEBUG_SEG_SPLIT, encroachPt, splitPt, "");
            // Debug.println(WKTWriter.toLineString(encroachPt, splitPt));
            /**
             * Check whether the inserted point still equals the split pt. This will
             * not be the case if the split pt was too close to an existing site. If
             * the point was snapped, the triangulation will not respect the inserted
             * constraint - this is a failure. This can be caused by:
             *
             *  * An initial site that lies very close to a constraint segment The
             * cure for this is to remove any initial sites which are close to
             * constraint segments in a preprocessing phase.
             *  * A narrow constraint angle which causing repeated splitting until
             * the split segments are too small. The cure for this is to either choose
             * better split points or "guard" narrow angles by cracking the segments
             * equidistant from the corner.
             *
             */
            val insertedVertex: ConstraintVertex = insertSite(splitVertex)
            if (!insertedVertex.coordinate.equals2D(splitPt!!)) {
                Debug.println("Split pt snapped to: $insertedVertex")
                // throw new ConstraintEnforcementException("Split point snapped to
                // existing point
                // (tolerance too large or constraint interior narrow angle?)",
                // splitPt);
            }

            // split segment and record the new halves
            val s1: Segment = Segment(
                seg.getStartX(), seg.getStartY(), seg
                    .getStartZ(), splitVertex.x, splitVertex.y, splitVertex
                    .z, seg.getData()
            )
            val s2: Segment = Segment(
                splitVertex.x, splitVertex.y,
                splitVertex.z, seg.getEndX(), seg.getEndY(), seg.getEndZ(), seg
                    .getData()
            )
            newSegments.add(s1)
            newSegments.add(s2)
            segsToRemove.add(seg)
            splits += 1
        }
        segsToInsert.removeAll(segsToRemove)
        segsToInsert.addAll(newSegments)
        return splits
    }
    //	public static final String DEBUG_SEG_SPLIT = "C:\\proj\\CWB\\test\\segSplit.jml";
    /**
     * Given a set of points stored in the kd-tree and a line segment defined by
     * two points in this set, finds a [Coordinate] in the circumcircle of
     * the line segment, if one exists. This is called the Gabriel point - if none
     * exists then the segment is said to have the Gabriel condition. Uses the
     * heuristic of finding the non-Gabriel point closest to the midpoint of the
     * segment.
     *
     * @param p
     * start of the line segment
     * @param q
     * end of the line segment
     * @return a point which is non-Gabriel
     * or null if no point is non-Gabriel
     */
    private fun findNonGabrielPoint(seg: Segment): Coordinate? {
        val p: Coordinate = seg.getStart()
        val q: Coordinate = seg.getEnd()
        // Find the mid point on the line and compute the radius of enclosing circle
        val midPt = Coordinate((p.x + q.x) / 2.0, (p.y + q.y) / 2.0)
        val segRadius = p.distance(midPt)

        // compute envelope of circumcircle
        val env = Envelope(midPt)
        env.expandBy(segRadius)
        // Find all points in envelope
        val result: MutableList<KdNode?> = kDT!!.query(env)

        // For each point found, test if it falls strictly in the circle
        // find closest point
        var closestNonGabriel: Coordinate? = null
        var minDist = Double.MAX_VALUE
        val i: Iterator<*> = result.iterator()
        while (i.hasNext()) {
            val nextNode = i.next() as KdNode
            val testPt = nextNode.coordinate
            // ignore segment endpoints
            if (testPt!!.equals2D(p) || testPt.equals2D(q)) {
                continue
            }
            val testRadius = midPt.distance(testPt)
            if (testRadius < segRadius) {
                // double testDist = seg.distance(testPt);
                if (closestNonGabriel == null || testRadius < minDist) {
                    closestNonGabriel = testPt
                    minDist = testRadius
                }
            }
        }
        return closestNonGabriel
    }

    companion object {
        private fun computeVertexEnvelope(vertices: Collection<*>?): Envelope {
            val env = Envelope()
            val i = vertices!!.iterator()
            while (i.hasNext()) {
                val v = i.next() as Vertex
                env.expandToInclude(v.coordinate)
            }
            return env
        }

        // ==================================================================
        private const val MAX_SPLIT_ITER = 99
    }
}