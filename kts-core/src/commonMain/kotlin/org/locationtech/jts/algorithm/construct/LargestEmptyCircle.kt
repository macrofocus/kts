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
import org.locationtech.jts.legacy.Math.min
import org.locationtech.jts.legacy.queue.PriorityQueue
import org.locationtech.jts.operation.distance.IndexedFacetDistance

/**
 * Constructs the Largest Empty Circle for a set
 * of obstacle geometries, up to a specified tolerance.
 * The obstacles are point and line geometries.
 *
 * The Largest Empty Circle is the largest circle which
 * has its center in the convex hull of the obstacles (the *boundary*),
 * and whose interior does not intersect with any obstacle.
 * The circle center is the point in the interior of the boundary
 * which has the farthest distance from the obstacles (up to tolerance).
 * The circle is determined by the center point
 * and a point lying on an obstacle indicating the circle radius.
 *
 * The implementation uses a successive-approximation technique
 * over a grid of square cells covering the obstacles and boundary.
 * The grid is refined using a branch-and-bound algorithm.
 * Point containment and distance are computed in a performant
 * way by using spatial indexes.
 *
 * <h3>Future Enhancements</h3>
 *
 *  * Support polygons as obstacles
 *  * Support a client-defined boundary polygon
 *
 * @author Martin Davis
 * @see MaximumInscribedCircle
 *
 * @see InteriorPoint
 *
 * @see Centroid
 */
class LargestEmptyCircle(obstacles: Geometry, tolerance: Double) {
    private val obstacles: Geometry
    private val tolerance: Double
    private val factory: GeometryFactory
    private var boundary: Geometry? = null
    private var ptLocater: IndexedPointInAreaLocator? = null
    private val obstacleDistance: IndexedFacetDistance
    private var boundaryDistance: IndexedFacetDistance? = null
    private var farthestCell: Cell? = null
    private var centerCell: Cell? = null
    private var centerPt: Coordinate? = null
    private var centerPoint: Point? = null
    private var radiusPt: Coordinate? = null
    private var radiusPoint: Point? = null

    /**
     * Creates a new instance of a Largest Empty Circle construction.
     *
     * @param obstacles a geometry representing the obstacles (points and lines)
     * @param tolerance the distance tolerance for computing the circle center point
     */
    init {
        if (obstacles.isEmpty) {
            throw IllegalArgumentException("Empty obstacles geometry is not supported")
        }
        this.obstacles = obstacles
        factory = obstacles.factory
        this.tolerance = tolerance
        obstacleDistance = IndexedFacetDistance(obstacles)
        setBoundary(obstacles)
    }

    /**
     * Sets the area boundary as the convex hull
     * of the obstacles.
     *
     * @param obstacles
     */
    private fun setBoundary(obstacles: Geometry) {
        // TODO: allow this to be set by client as arbitrary polygon
        boundary = obstacles.convexHull()
        // if boundary does not enclose an area cannot create a ptLocater
        if (boundary!!.dimension >= 2) {
            ptLocater = IndexedPointInAreaLocator(boundary)
            boundaryDistance = IndexedFacetDistance(boundary!!)
        }
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
        val isOutide = Location.EXTERIOR == ptLocater!!.locate(p.coordinate!!)
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

    private fun compute() {
        // check if already computed
        if (centerCell != null) return

        // if ptLocater is not present then result is degenerate (represented as zero-radius circle)
        if (ptLocater == null) {
            val pt = obstacles.coordinate
            centerPt = pt!!.copy()
            centerPoint = factory.createPoint(pt)
            radiusPt = pt.copy()
            radiusPoint = factory.createPoint(pt)
            return
        }

        // Priority queue of cells, ordered by decreasing distance from constraints
        val cellQueue: PriorityQueue<Cell> = PriorityQueue<Cell>()
        createInitialGrid(obstacles.envelopeInternal, cellQueue)

        // use the area centroid as the initial candidate center point
        farthestCell = createCentroidCell(obstacles)
        //int totalCells = cellQueue.size();
        /**
         * Carry out the branch-and-bound search
         * of the cell space
         */
        while (!cellQueue.isEmpty()) {
            // pick the cell with greatest distance from the queue
            val cell: Cell? = cellQueue.remove()

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
        val nearestPts: Array<Coordinate> = obstacleDistance.nearestPoints(centerPoint)!!
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
     * Initializes the queue with a grid of cells covering
     * the extent of the area.
     *
     * @param env the area extent to cover
     * @param cellQueue the queue to initialize
     */
    private fun createInitialGrid(env: Envelope, cellQueue: PriorityQueue<Cell>) {
        val minX = env.minX
        val maxX = env.maxX
        val minY = env.minY
        val maxY = env.maxY
        val width = env.width
        val height = env.height
        val cellSize: Double = min(width, height)
        val hSize = cellSize / 2.0

        // compute initial grid of cells to cover area
        var x = minX
        while (x < maxX) {
            var y = minY
            while (y < maxY) {
                cellQueue.add(createCell(x + hSize, y + hSize, hSize))
                y += cellSize
            }
            x += cellSize
        }
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
    private class Cell internal constructor(val x: Double, val y: Double, val hSide: Double, val distance: Double) :
        Comparable<Cell?> {
        val maxDistance: Double

        init {
            // cell center x
            // cell center y
            // half the cell size

            // the distance from cell center to constraints
            /**
             * The maximum possible distance to the constraints for points in this cell
             * is the center distance plus the radius (half the diagonal length).
             */
            maxDistance = distance + hSide * SQRT2
        }

        val isFullyOutside: Boolean
            get() = maxDistance < 0
        val isOutside: Boolean
            get() = distance < 0

        /**
         * A cell is greater if its maximum distance is larger.
         */
        override operator fun compareTo(o: Cell?): Int {
            return (o!!.maxDistance - maxDistance).toInt()
        }

        companion object {
            private const val SQRT2 = 1.4142135623730951
        }
    }

    companion object {
        /**
         * Computes the center point of the Largest Empty Circle
         * within a set of obstacles, up to a given tolerance distance.
         *
         * @param obstacles a geometry representing the obstacles (points and lines)
         * @param tolerance the distance tolerance for computing the center point
         * @return the center point of the Largest Empty Circle
         */
        fun getCenter(obstacles: Geometry, tolerance: Double): Point? {
            val lec = LargestEmptyCircle(obstacles, tolerance)
            return lec.center
        }

        /**
         * Computes a radius line of the Largest Empty Circle
         * within a set of obstacles, up to a given distance tolerance.
         *
         * @param obstacles a geometry representing the obstacles (points and lines)
         * @param tolerance the distance tolerance for computing the center point
         * @return a line from the center of the circle to a point on the edge
         */
        fun getRadiusLine(obstacles: Geometry, tolerance: Double): LineString {
            val lec = LargestEmptyCircle(obstacles, tolerance)
            return lec.radiusLine
        }
    }
}