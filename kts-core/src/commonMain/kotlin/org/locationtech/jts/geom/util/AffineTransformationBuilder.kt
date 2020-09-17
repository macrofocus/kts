/*
 * Copyright (c) 2016 Vivid Solutions.
 * Copyright (c) 2020 Macrofocus GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.geom.util

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.math.Matrix

/**
 * Builds an [AffineTransformation] defined by a set of control vectors.
 * A control vector consists of a source point and a destination point,
 * which is the image of the source point under the desired transformation.
 *
 * A transformation is well-defined
 * by a set of three control vectors
 * if and only if the source points are not collinear.
 * (In particular, the degenerate situation
 * where two or more source points are identical will not produce a well-defined transformation).
 * A well-defined transformation exists and is unique.
 * If the control vectors are not well-defined, the system of equations
 * defining the transformation matrix entries is not solvable,
 * and no transformation can be determined.
 *
 * No such restriction applies to the destination points.
 * However, if the destination points are collinear or non-unique,
 * a non-invertible transformations will be generated.
 *
 * This technique of recovering a transformation
 * from its effect on known points is used in the Bilinear Interpolated Triangulation
 * algorithm for warping planar surfaces.
 *
 * @author Martin Davis
 * @author Luc Girardin
 */
class AffineTransformationBuilder
/**
 * Constructs a new builder for
 * the transformation defined by the given
 * set of control point mappings.
 *
 * @param src0 a control point
 * @param src1 a control point
 * @param src2 a control point
 * @param dest0 the image of control point 0 under the required transformation
 * @param dest1 the image of control point 1 under the required transformation
 * @param dest2 the image of control point 2 under the required transformation
 */(
    private val src0: Coordinate,
    private val src1: Coordinate,
    private val src2: Coordinate,
    private val dest0: Coordinate,
    private val dest1: Coordinate,
    private val dest2: Coordinate
) {
    // the matrix entries for the transformation
    private var m00 = 0.0
    private var m01 = 0.0
    private var m02 = 0.0
    private var m10 = 0.0
    private var m11 = 0.0
    private var m12 = 0.0// compute full 3-point transformation

    /**
     * Computes the [AffineTransformation]
     * determined by the control point mappings,
     * or `null` if the control vectors do not determine a well-defined transformation.
     *
     * @return an affine transformation, or null if the control vectors do not determine a well-defined transformation
     */
    val transformation: AffineTransformation?
        get() {
            // compute full 3-point transformation
            val isSolvable = compute()
            return if (isSolvable) AffineTransformation(m00, m01, m02, m10, m11, m12) else null
        }

    /**
     * Computes the transformation matrix by
     * solving the two systems of linear equations
     * defined by the control point mappings,
     * if this is possible.
     *
     * @return true if the transformation matrix is solvable
     */
    private fun compute(): Boolean {
        val bx = doubleArrayOf(dest0.x, dest1.x, dest2.x)
        val row0 = solve(bx) ?: return false
        m00 = row0[0]
        m01 = row0[1]
        m02 = row0[2]
        val by = doubleArrayOf(dest0.y, dest1.y, dest2.y)
        val row1 = solve(by) ?: return false
        m10 = row1[0]
        m11 = row1[1]
        m12 = row1[2]
        return true
    }

    /**
     * Solves the transformation matrix system of linear equations
     * for the given right-hand side vector.
     *
     * @param b the vector for the right-hand side of the system
     * @return the solution vector, or `null` if no solution could be determined
     */
    private fun solve(b: DoubleArray): DoubleArray? {
        val a = arrayOf(
            doubleArrayOf(src0.x, src0.y, 1.0), doubleArrayOf(
                src1.x, src1.y, 1.0
            ), doubleArrayOf(src2.x, src2.y, 1.0)
        )
        return Matrix.solve(a, b)
    }
}