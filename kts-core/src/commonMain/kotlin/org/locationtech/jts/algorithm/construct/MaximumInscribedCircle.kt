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
 * Constructs the Maximum Inscribed Circle for a
 * polygonal [Geometry], up to a specified tolerance.
 * The Maximum Inscribed Circle is determined by a point in the interior of the area
 * which has the farthest distance from the area boundary,
 * along with a boundary point at that distance.
 *
 * In the context of geography the center of the Maximum Inscribed Circle
 * is known as the **Pole of Inaccessibility**.
 * A cartographic use case is to determine a suitable point
 * to place a map label within a polygon.
 *
 * The radius length of the Maximum Inscribed Circle is a
 * measure of how "narrow" a polygon is. It is the
 * distance at which the negative buffer becomes empty.
 *
 * The class supports polygons with holes and multipolygons.
 *
 * The implementation uses a successive-approximation technique
 * over a grid of square cells covering the area geometry.
 * The grid is refined using a branch-and-bound algorithm.
 * Point containment and distance are computed in a performant
 * way by using spatial indexes.
 *
 * <h3>Future Enhancements</h3>
 *
 *  * Support a polygonal constraint on placement of center
 *
 * @author Martin Davis
 * @see LargestEmptyCircle
 *
 * @see InteriorPoint
 *
 * @see Centroid
 */
class MaximumInscribedCircle(polygonal: Geometry, tolerance: Double) {
    private val inputGeom: Geometry
    private val tolerance: Double
    private val factory: GeometryFactory
    private val ptLocater: IndexedPointInAreaLocator
    private val indexedDistance: IndexedFacetDistance
    private var centerCell: Cell? = null
    private var centerPt: Coordinate? = null
    private var radiusPt: Coordinate? = null
    private var centerPoint: Point? = null
    private var radiusPoint: Point? = null

    /**
     * Creates a new instance of a Maximum Inscribed Circle computation.
     *
     * @param polygonal an areal geometry
     * @param tolerance the distance tolerance for computing the centre point (must be positive)
     * @throws IllegalArgumentException if the tolerance is non-positive, or the input geometry is non-polygonal or empty.
     */
    init {
        if (tolerance <= 0) {
            throw IllegalArgumentException("Tolerance must be positive")
        }
        if (!(polygonal is Polygon || polygonal is MultiPolygon)) {
            throw IllegalArgumentException("Input geometry must be a Polygon or MultiPolygon")
        }
        if (polygonal.isEmpty) {
            throw IllegalArgumentException("Empty input geometry is not supported")
        }
        inputGeom = polygonal
        factory = polygonal.factory
        this.tolerance = tolerance
        ptLocater = IndexedPointInAreaLocator(polygonal)
        indexedDistance = IndexedFacetDistance(polygonal.boundary!!)
    }

    /**
     * Gets the center point of the maximum inscribed circle
     * (up to the tolerance distance).
     *
     * @return the center point of the maximum inscribed circle
     */
    val center: Point?
        get() {
            compute()
            return centerPoint
        }

    /**
     * Gets a point defining the radius of the Maximum Inscribed Circle.
     * This is a point on the boundary which is
     * nearest to the computed center of the Maximum Inscribed Circle.
     * The line segment from the center to this point
     * is a radius of the constructed circle, and this point
     * lies on the boundary of the circle.
     *
     * @return a point defining the radius of the Maximum Inscribed Circle
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
     * Computes the signed distance from a point to the area boundary.
     * Points outside the polygon are assigned a negative distance.
     * Their containing cells will be last in the priority queue
     * (but may still end up being tested since they may need to be refined).
     *
     * @param p the point to compute the distance for
     * @return the signed distance to the area boundary (negative indicates outside the area)
     */
    private fun distanceToBoundary(p: Point): Double {
        val dist: Double = indexedDistance.distance(p)
        val isOutide = Location.EXTERIOR == ptLocater.locate(p.coordinate!!)
        return if (isOutide) -dist else dist
    }

    private fun distanceToBoundary(x: Double, y: Double): Double {
        val coord = Coordinate(x, y)
        val pt = factory.createPoint(coord)
        return distanceToBoundary(pt)
    }

    private fun compute() {
        // check if already computed
        if (centerCell != null) return

        // Priority queue of cells, ordered by maximum distance from boundary
        val cellQueue: PriorityQueue<Cell> = PriorityQueue<Cell>()
        createInitialGrid(inputGeom.envelopeInternal, cellQueue)

        // use the area centroid as the initial candidate center point
        var farthestCell = createCentroidCell(inputGeom)
        //int totalCells = cellQueue.size();
        /**
         * Carry out the branch-and-bound search
         * of the cell space
         */
        while (!cellQueue.isEmpty()) {
            // pick the most promising cell from the queue
            val cell: Cell? = cellQueue.remove()
            //System.out.println(factory.toGeometry(cell.getEnvelope()));

            // update the center cell if the candidate is further from the boundary
            if (cell!!.distance > farthestCell.distance) {
                farthestCell = cell
            }
            /**
             * Refine this cell if the potential distance improvement
             * is greater than the required tolerance.
             * Otherwise the cell is pruned (not investigated further),
             * since no point in it is further than
             * the current farthest distance.
             */
            val potentialIncrease: Double = cell.maxDistance - farthestCell.distance
            if (potentialIncrease > tolerance) {
                // split the cell into four sub-cells
                val h2: Double = cell.hSide / 2
                cellQueue.add(createCell(cell.x - h2, cell.y - h2, h2))
                cellQueue.add(createCell(cell.x + h2, cell.y - h2, h2))
                cellQueue.add(createCell(cell.x - h2, cell.y + h2, h2))
                cellQueue.add(createCell(cell.x + h2, cell.y + h2, h2))
                //totalCells += 4;
            }
        }
        // the farthest cell is the best approximation to the MIC center
        centerCell = farthestCell
        centerPt = Coordinate(centerCell!!.x, centerCell!!.y)
        centerPoint = factory.createPoint(centerPt)
        // compute radius point
        val nearestPts: Array<Coordinate>? = indexedDistance.nearestPoints(centerPoint)
        radiusPt = nearestPts!![0].copy()
        radiusPoint = factory.createPoint(radiusPt)
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

        // Check for flat collapsed input and if so short-circuit
        // Result will just be centroid
        if (cellSize == 0.0) return
        val hSide = cellSize / 2.0

        // compute initial grid of cells to cover area
        var x = minX
        while (x < maxX) {
            var y = minY
            while (y < maxY) {
                cellQueue.add(createCell(x + hSide, y + hSide, hSide))
                y += cellSize
            }
            x += cellSize
        }
    }

    private fun createCell(x: Double, y: Double, hSide: Double): Cell {
        return Cell(x, y, hSide, distanceToBoundary(x, y))
    }

    // create a cell centered on area centroid
    private fun createCentroidCell(geom: Geometry): Cell {
        val p = geom.centroid
        return Cell(p.x, p.y, 0.0, distanceToBoundary(p))
    }

    /**
     * A square grid cell centered on a given point,
     * with a given half-side size, and having a given distance
     * to the area boundary.
     * The maximum possible distance from any point in the cell to the
     * boundary can be computed, and is used
     * as the ordering and upper-bound function in
     * the branch-and-bound algorithm.
     *
     */
    private class Cell internal constructor(val x: Double, val y: Double, val hSide: Double, val distance: Double) :
        Comparable<Cell?> {
        val maxDistance: Double

        init {
            // cell center x
            // cell center y
            // half the cell size

            // the distance from cell center to area boundary

            // the maximum possible distance to area boundary for points in this cell
            maxDistance = distance + hSide * SQRT2
        }

        val envelope: Envelope
            get() = Envelope(x - hSide, x + hSide, y - hSide, y + hSide)

        /**
         * A cell is greater if its maximum possible distance is larger.
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
         * Computes the center point of the Maximum Inscribed Circle
         * of a polygonal geometry, up to a given tolerance distance.
         *
         * @param polygonal a polygonal geometry
         * @param tolerance the distance tolerance for computing the center point
         * @return the center point of the maximum inscribed circle
         */
        fun getCenter(polygonal: Geometry, tolerance: Double): Point? {
            val mic = MaximumInscribedCircle(polygonal, tolerance)
            return mic.center
        }

        /**
         * Computes a radius line of the Maximum Inscribed Circle
         * of a polygonal geometry, up to a given tolerance distance.
         *
         * @param polygonal a polygonal geometry
         * @param tolerance the distance tolerance for computing the center point
         * @return a line from the center to a point on the circle
         */
        fun getRadiusLine(polygonal: Geometry, tolerance: Double): LineString {
            val mic = MaximumInscribedCircle(polygonal, tolerance)
            return mic.radiusLine
        }
    }
}