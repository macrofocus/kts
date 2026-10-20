/*
 * Copyright (c) 2020 Martin Davis.
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.algorithm.construct

import org.locationtech.jts.algorithm.InteriorPoint
import org.locationtech.jts.algorithm.locate.IndexedPointInAreaLocator
import org.locationtech.jts.geom.*
import org.locationtech.jts.legacy.Math.max
import org.locationtech.jts.legacy.queue.PriorityQueue
import org.locationtech.jts.operation.distance.IndexedFacetDistance
import kotlin.jvm.JvmStatic

/**
 * Constructs the Largest Empty Circle for a set
 * of obstacle geometries, up to a given accuracy distance tolerance.
 * The obstacles may be any combination of point, linear and polygonal geometries.
 *
 * The Largest Empty Circle (LEC) is the largest circle
 * whose interior does not intersect with any obstacle
 * and whose center lies within a polygonal boundary.
 * The circle center is the point in the interior of the boundary
 * which has the farthest distance from the obstacles
 * (up to the accuracy of the distance tolerance).
 * The circle itself is determined by the center point
 * and a point lying on an obstacle determining the circle radius.
 *
 * The polygonal boundary may be supplied explicitly.
 * If it is not specified the convex hull of the obstacles is used as the boundary.
 *
 * To compute an LEC which lies *wholly* within
 * a polygonal boundary, include the boundary of the polygon(s) as a linear obstacle.
 *
 * The implementation uses a successive-approximation technique
 * over a grid of square cells covering the obstacles and boundary.
 * The grid is refined using a branch-and-bound algorithm.
 * Point containment and distance are computed in a performant
 * way by using spatial indexes.
 *
 * @author Martin Davis
 *
 * @see MaximumInscribedCircle
 *
 * @see InteriorPoint
 *
 * @see Centroid
 */
class LargestEmptyCircle(obstacles: Geometry?, boundary: Geometry?, tolerance: Double) {
    private val obstacles: Geometry
    private val boundary: Geometry?
    private val tolerance: Double
    private val factory: GeometryFactory
    private val obstacleDistance: IndexedDistanceToPoint
    private var boundaryPtLocater: IndexedPointInAreaLocator? = null
    private var boundaryDistance: IndexedFacetDistance? = null
    private var gridEnv: Envelope? = null
    private var farthestCell: Cell? = null
    private var centerCell: Cell? = null
    private var centerPt: Coordinate? = null
    private var centerPoint: Point? = null
    private var radiusPt: Coordinate? = null
    private var radiusPoint: Point? = null
    private var bounds: Geometry? = null

    /**
     * Creates a new instance of a Largest Empty Circle construction,
     * interior-disjoint to a set of obstacle geometries
     * and having its center within a polygonal boundary.
     * The obstacles may be any collection of points, lines and polygons.
     * If the boundary is null or empty the convex hull
     * of the obstacles is used as the boundary.
     *
     * @param obstacles a non-empty geometry representing the obstacles
     * @param boundary a polygonal geometry (may be null or empty)
     * @param tolerance a distance tolerance for computing the circle center point (a positive value)
     */
    init {
        if (obstacles == null || obstacles.isEmpty) {
            throw IllegalArgumentException("Obstacles geometry is empty or null")
        }
        if (boundary != null && boundary !is Polygonal) {
            throw IllegalArgumentException("Boundary must be polygonal")
        }
        if (tolerance <= 0) {
            throw IllegalArgumentException("Accuracy tolerance is non-positive: $tolerance")
        }
        this.obstacles = obstacles
        this.boundary = boundary
        factory = obstacles.factory
        this.tolerance = tolerance
        obstacleDistance = IndexedDistanceToPoint(obstacles)
    }

    /**
     * Gets the center point of the Largest Empty Circle
     * (up to the tolerance distance).
     *
     * @return the center point of the Largest Empty Circle
     */
    val center: Point?
        get() {
            compute()
            return centerPoint
        }

    /**
     * Gets a point defining the radius of the Largest Empty Circle.
     * This is a point on the obstacles which is
     * nearest to the computed center of the Largest Empty Circle.
     * The line segment from the center to this point
     * is a radius of the constructed circle, and this point
     * lies on the boundary of the circle.
     *
     * @return a point defining the radius of the Largest Empty Circle
     */
    fun getRadiusPoint(): Point? {
        compute()
        return radiusPoint
    }

    /**
     * Gets a line representing a radius of the Largest Empty Circle.
     *
     * @return a line from the center of the circle to a point on the edge
     */
    val radiusLine: LineString
        get() {
            compute()
            return factory.createLineString(
                arrayOf(
                    centerPt!!.copy(), radiusPt!!.copy()
                )
            )
        }

    /**
     * Computes the signed distance from a point to the constraints
     * (obstacles and boundary).
     * Points outside the boundary polygon are assigned a negative distance.
     * Their containing cells will be last in the priority queue
     * (but will still end up being tested since they may be refined).
     *
     * @param p the point to compute the distance for
     * @return the signed distance to the constraints (negative indicates outside the boundary)
     */
    private fun distanceToConstraints(p: Point): Double {
        val isOutide = Location.EXTERIOR == boundaryPtLocater!!.locate(p.coordinate!!)
        if (isOutide) {
            val boundaryDist: Double = boundaryDistance!!.distance(p)
            return -boundaryDist
        }
        return obstacleDistance.distance(p)
    }

    private fun distanceToConstraints(x: Double, y: Double): Double {
        val coord = Coordinate(x, y)
        val pt = factory.createPoint(coord)
        return distanceToConstraints(pt)
    }

    private fun initBoundary() {
        var bounds = this.boundary
        if (bounds == null || bounds.isEmpty) {
            bounds = obstacles.convexHull()
        }
        this.bounds = bounds
        //-- the centre point must be in the extent of the boundary
        gridEnv = bounds!!.envelopeInternal
        // if bounds does not enclose an area cannot create a ptLocater
        if (bounds.dimension >= 2) {
            boundaryPtLocater = IndexedPointInAreaLocator(bounds)
            boundaryDistance = IndexedFacetDistance(bounds)
        }
    }

    private fun compute() {
        initBoundary()

        // check if already computed
        if (centerCell != null) return

        // if boundaryPtLocater is not present then result is degenerate (represented as zero-radius circle)
        if (boundaryPtLocater == null) {
            val pt = obstacles.coordinate
            centerPt = pt!!.copy()
            centerPoint = factory.createPoint(pt)
            radiusPt = pt.copy()
            radiusPoint = factory.createPoint(pt)
            return
        }

        // Priority queue of cells, ordered by decreasing distance from constraints
        val cellQueue: PriorityQueue<Cell> = PriorityQueue()
        //-- grid covers extent of obstacles and boundary (if any)
        createInitialGrid(gridEnv!!, cellQueue)

        // use the area centroid as the initial candidate center point
        farthestCell = createCentroidCell(obstacles)
        //int totalCells = cellQueue.size();
        /**
         * Carry out the branch-and-bound search
         * of the cell space
         */
        val maxIter = MaximumInscribedCircle.computeMaximumIterations(bounds!!, tolerance)
        var iter: Long = 0
        while (!cellQueue.isEmpty() && iter < maxIter) {
            iter++
            // pick the cell with greatest distance from the queue
            val cell: Cell? = cellQueue.remove()
            //System.out.println(iter + "] Dist: " + cell.getDistance() + " Max D: " + cell.getMaxDistance() + " size: " + cell.getHSide());

            // update the center cell if the candidate is further from the constraints
            if (cell!!.distance > farthestCell!!.distance) {
                farthestCell = cell
            }
            /**
             * If this cell may contain a better approximation to the center
             * of the empty circle, then refine it (partition into subcells
             * which are added into the queue for further processing).
             * Otherwise the cell is pruned (not investigated further),
             * since no point in it can be further than the current farthest distance.
             */
            if (mayContainCircleCenter(cell)) {
                // split the cell into four sub-cells
                val h2: Double = cell.hSide / 2
                cellQueue.add(createCell(cell.x - h2, cell.y - h2, h2))
                cellQueue.add(createCell(cell.x + h2, cell.y - h2, h2))
                cellQueue.add(createCell(cell.x - h2, cell.y + h2, h2))
                cellQueue.add(createCell(cell.x + h2, cell.y + h2, h2))
                //totalCells += 4;
            }
        }
        // the farthest cell is the best approximation to the LEC center
        centerCell = farthestCell
        // compute center point
        centerPt = Coordinate(centerCell!!.x, centerCell!!.y)
        centerPoint = factory.createPoint(centerPt)
        // compute radius point
        val nearestPts: Array<Coordinate> = obstacleDistance.nearestPoints(centerPoint!!)
        radiusPt = nearestPts[0].copy()
        radiusPoint = factory.createPoint(radiusPt)
    }

    /**
     * Tests whether a cell may contain the circle center,
     * and thus should be refined (split into subcells
     * to be investigated further.)
     *
     * @param cell the cell to test
     * @return true if the cell might contain the circle center
     */
    private fun mayContainCircleCenter(cell: Cell): Boolean {
        /**
         * Every point in the cell lies outside the boundary,
         * so they cannot be the center point
         */
        if (cell.isFullyOutside) return false
        /**
         * The cell is outside, but overlaps the boundary
         * so it may contain a point which should be checked.
         * This is only the case if the potential overlap distance
         * is larger than the tolerance.
         */
        if (cell.isOutside) {
            return cell.maxDistance > tolerance
        }
        /**
         * Cell is inside the boundary. It may contain the center
         * if the maximum possible distance is greater than the current distance
         * (up to tolerance).
         */
        val potentialIncrease: Double = cell.maxDistance - farthestCell!!.distance
        return potentialIncrease > tolerance
    }

    /**
     * Initializes the queue with a cell covering
     * the extent of the area.
     *
     * @param env the area extent to cover
     * @param cellQueue the queue to initialize
     */
    private fun createInitialGrid(env: Envelope, cellQueue: PriorityQueue<Cell>) {
        val cellSize: Double = max(env.width, env.height)
        val hSide = cellSize / 2.0

        // Check for flat collapsed input and if so short-circuit
        // Result will just be centroid
        if (cellSize == 0.0) return

        val centre = env.centre()!!
        cellQueue.add(createCell(centre.x, centre.y, hSide))
    }

    private fun createCell(x: Double, y: Double, h: Double): Cell {
        return Cell(x, y, h, distanceToConstraints(x, y))
    }

    // create a cell centered on area centroid
    private fun createCentroidCell(geom: Geometry): Cell {
        val p = geom.centroid
        return Cell(p.x, p.y, 0.0, distanceToConstraints(p))
    }

    /**
     * A square grid cell centered on a given point
     * with a given side half-length,
     * and having a given distance from the center point to the constraints.
     * The maximum possible distance from any point in the cell to the
     * constraints can be computed.
     * This is used as the ordering and upper-bound function in
     * the branch-and-bound algorithm.
     */
    private class Cell(val x: Double, val y: Double, val hSide: Double, val distance: Double) :
        Comparable<Cell?> {
        val maxDistance: Double = distance + hSide * SQRT2

        init {
            // cell center x
            // cell center y
            // half the cell size

            // the distance from cell center to constraints
            /**
             * The maximum possible distance to the constraints for points in this cell
             * is the center distance plus the radius (half the diagonal length).
             */
        }

        val isFullyOutside: Boolean
            get() = maxDistance < 0
        val isOutside: Boolean
            get() = distance < 0

        /**
         * For maximum efficieny sort the PriorityQueue with largest maxDistance at front.
         * Since Java PQ sorts least-first, need to invert the comparison
         */
        override operator fun compareTo(o: Cell?): Int {
            return -maxDistance.compareTo(o!!.maxDistance)
        }

        companion object {
            private const val SQRT2 = 1.4142135623730951
        }
    }

    companion object {
        /**
         * Computes the center point of the Largest Empty Circle
         * interior-disjoint to a set of obstacles,
         * with accuracy to a given tolerance distance.
         * The obstacles may be any collection of points, lines and polygons.
         * The center of the LEC lies within the convex hull of the obstacles.
         *
         * @param obstacles a geometry representing the obstacles
         * @param tolerance the distance tolerance for computing the center point
         * @return the center point of the Largest Empty Circle
         */
        @JvmStatic
        fun getCenter(obstacles: Geometry, tolerance: Double): Point? {
            return getCenter(obstacles, null, tolerance)
        }

        /**
         * Computes the center point of the Largest Empty Circle
         * interior-disjoint to a set of obstacles and within a polygonal boundary,
         * with accuracy to a given tolerance distance.
         * The obstacles may be any collection of points, lines and polygons.
         * The center of the LEC lies within the given boundary.
         *
         * @param obstacles a geometry representing the obstacles
         * @param boundary a polygonal geometry to contain the LEC center
         * @param tolerance the distance tolerance for computing the center point
         * @return the center point of the Largest Empty Circle
         */
        @JvmStatic
        fun getCenter(obstacles: Geometry, boundary: Geometry?, tolerance: Double): Point? {
            val lec = LargestEmptyCircle(obstacles, boundary, tolerance)
            return lec.center
        }

        /**
         * Computes a radius line of the Largest Empty Circle
         * interior-disjoint to a set of obstacles,
         * with accuracy to a given tolerance distance.
         * The obstacles may be any collection of points, lines and polygons.
         * The center of the LEC lies within the convex hull of the obstacles.
         *
         * @param obstacles a geometry representing the obstacles
         * @param tolerance the distance tolerance for computing the center point
         * @return a line from the center of the circle to a point on the edge
         */
        @JvmStatic
        fun getRadiusLine(obstacles: Geometry, tolerance: Double): LineString {
            return getRadiusLine(obstacles, null, tolerance)
        }

        /**
         * Computes a radius line of the Largest Empty Circle
         * interior-disjoint to a set of obstacles and within a polygonal boundary,
         * with accuracy to a given tolerance distance.
         * The obstacles may be any collection of points, lines and polygons.
         * The center of the LEC lies within the given boundary.
         *
         * @param obstacles a geometry representing the obstacles
         * @param boundary a polygonal geometry to contain the LEC center
         * @param tolerance the distance tolerance for computing the center point
         * @return a line from the center of the circle to a point on the edge
         */
        @JvmStatic
        fun getRadiusLine(obstacles: Geometry, boundary: Geometry?, tolerance: Double): LineString {
            val lec = LargestEmptyCircle(obstacles, boundary, tolerance)
            return lec.radiusLine
        }
    }
}
