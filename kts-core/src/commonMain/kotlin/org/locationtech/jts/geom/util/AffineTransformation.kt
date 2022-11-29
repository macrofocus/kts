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
package org.locationtech.jts.geom.util

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.CoordinateSequence
import org.locationtech.jts.geom.CoordinateSequenceFilter
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.legacy.Cloneable
import org.locationtech.jts.legacy.Math.cos
import org.locationtech.jts.legacy.Math.doubleToLongBits
import org.locationtech.jts.legacy.Math.sin
import org.locationtech.jts.legacy.Math.sqrt
import kotlin.jvm.JvmStatic

/**
 * Represents an affine transformation on the 2D Cartesian plane.
 * It can be used to transform a [Coordinate] or [Geometry].
 * An affine transformation is a mapping of the 2D plane into itself
 * via a series of transformations of the following basic types:
 *
 *  * reflection (through a line)
 *  * rotation (around the origin)
 *  * scaling (relative to the origin)
 *  * shearing (in both the X and Y directions)
 *  * translation
 *
 * In general, affine transformations preserve straightness and parallel lines,
 * but do not preserve distance or shape.
 *
 * An affine transformation can be represented by a 3x3
 * matrix in the following form:
 * <blockquote><pre>
 * T = | m00 m01 m02 |
 * | m10 m11 m12 |
 * |  0   0   1  |
</pre></blockquote> *
 * A coordinate P = (x, y) can be transformed to a new coordinate P' = (x', y')
 * by representing it as a 3x1 matrix and using matrix multiplication to compute:
 * <blockquote><pre>
 * | x' |  = T x | x |
 * | y' |        | y |
 * | 1  |        | 1 |
</pre></blockquote> *
 * <h3>Transformation Composition</h3>
 * Affine transformations can be composed using the [.compose] method.
 * Composition is computed via multiplication of the
 * transformation matrices, and is defined as:
 * <blockquote>`
 * A.compose(B) = T<sub>B</sub> x T<sub>A</sub>
`</blockquote> *
 * This produces a transformation whose effect is that of A followed by B.
 * The methods [.reflect], [.rotate],
 * [.scale], [.shear], and [.translate]
 * have the effect of composing a transformation of that type with
 * the transformation they are invoked on.
 *
 * The composition of transformations is in general *not* commutative.
 *
 * <h3>Transformation Inversion</h3>
 * Affine transformations may be invertible or non-invertible.
 * If a transformation is invertible, then there exists
 * an inverse transformation which when composed produces
 * the identity transformation.
 * The [.getInverse] method
 * computes the inverse of a transformation, if one exists.
 *
 * @author Martin Davis
 */
class AffineTransformation : Cloneable, CoordinateSequenceFilter {
    // affine matrix entries
    // (bottom row is always [ 0 0 1 ])
    private var m00 = 0.0
    private var m01 = 0.0
    private var m02 = 0.0
    private var m10 = 0.0
    private var m11 = 0.0
    private var m12 = 0.0

    /**
     * Constructs a new identity transformation
     */
    constructor() {
        setToIdentity()
    }

    /**
     * Constructs a new transformation whose
     * matrix has the specified values.
     *
     * @param matrix an array containing the 6 values { m00, m01, m02, m10, m11, m12 }
     * @throws NullPointerException if matrix is null
     * @throws ArrayIndexOutOfBoundsException if matrix is too small
     */
    constructor(matrix: DoubleArray) {
        m00 = matrix[0]
        m01 = matrix[1]
        m02 = matrix[2]
        m10 = matrix[3]
        m11 = matrix[4]
        m12 = matrix[5]
    }

    /**
     * Constructs a new transformation whose
     * matrix has the specified values.
     *
     * @param m00 the entry for the [0, 0] element in the transformation matrix
     * @param m01 the entry for the [0, 1] element in the transformation matrix
     * @param m02 the entry for the [0, 2] element in the transformation matrix
     * @param m10 the entry for the [1, 0] element in the transformation matrix
     * @param m11 the entry for the [1, 1] element in the transformation matrix
     * @param m12 the entry for the [1, 2] element in the transformation matrix
     */
    constructor(
        m00: Double,
        m01: Double,
        m02: Double,
        m10: Double,
        m11: Double,
        m12: Double
    ) {
        setTransformation(m00, m01, m02, m10, m11, m12)
    }

    /**
     * Constructs a transformation which is
     * a copy of the given one.
     *
     * @param trans the transformation to copy
     */
    constructor(trans: AffineTransformation) {
        setTransformation(trans)
    }

    /**
     * Constructs a transformation
     * which maps the given source
     * points into the given destination points.
     *
     * @param src0 source point 0
     * @param src1 source point 1
     * @param src2 source point 2
     * @param dest0 the mapped point for source point 0
     * @param dest1 the mapped point for source point 1
     * @param dest2 the mapped point for source point 2
     */
    constructor(
        src0: Coordinate?,
        src1: Coordinate?,
        src2: Coordinate?,
        dest0: Coordinate?,
        dest1: Coordinate?,
        dest2: Coordinate?
    )

    /**
     * Sets this transformation to be the identity transformation.
     * The identity transformation has the matrix:
     * <blockquote><pre>
     * | 1 0 0 |
     * | 0 1 0 |
     * | 0 0 1 |
    </pre></blockquote> *
     * @return this transformation, with an updated matrix
     */
    fun setToIdentity(): AffineTransformation {
        m00 = 1.0
        m01 = 0.0
        m02 = 0.0
        m10 = 0.0
        m11 = 1.0
        m12 = 0.0
        return this
    }

    /**
     * Sets this transformation's matrix to have the given values.
     *
     * @param m00 the entry for the [0, 0] element in the transformation matrix
     * @param m01 the entry for the [0, 1] element in the transformation matrix
     * @param m02 the entry for the [0, 2] element in the transformation matrix
     * @param m10 the entry for the [1, 0] element in the transformation matrix
     * @param m11 the entry for the [1, 1] element in the transformation matrix
     * @param m12 the entry for the [1, 2] element in the transformation matrix
     * @return this transformation, with an updated matrix
     */
    fun setTransformation(
        m00: Double,
        m01: Double,
        m02: Double,
        m10: Double,
        m11: Double,
        m12: Double
    ): AffineTransformation {
        this.m00 = m00
        this.m01 = m01
        this.m02 = m02
        this.m10 = m10
        this.m11 = m11
        this.m12 = m12
        return this
    }

    /**
     * Sets this transformation to be a copy of the given one
     *
     * @param trans a transformation to copy
     * @return this transformation, with an updated matrix
     */
    fun setTransformation(trans: AffineTransformation): AffineTransformation {
        m00 = trans.m00
        m01 = trans.m01
        m02 = trans.m02
        m10 = trans.m10
        m11 = trans.m11
        m12 = trans.m12
        return this
    }

    /**
     * Gets an array containing the entries
     * of the transformation matrix.
     * Only the 6 non-trivial entries are returned,
     * in the sequence:
     * <pre>
     * m00, m01, m02, m10, m11, m12
    </pre> *
     *
     * @return an array of length 6
     */
    val matrixEntries: DoubleArray
        get() = doubleArrayOf(m00, m01, m02, m10, m11, m12)

    /**
     * Computes the determinant of the transformation matrix.
     * The determinant is computed as:
     * <blockquote><pre>
     * | m00 m01 m02 |
     * | m10 m11 m12 | = m00 * m11 - m01 * m10
     * |  0   0   1  |
    </pre></blockquote> *
     * If the determinant is zero,
     * the transform is singular (not invertible),
     * and operations which attempt to compute
     * an inverse will throw a <tt>NoninvertibleTransformException</tt>.
     *
     * @return the determinant of the transformation
     * @see .getInverse
     */
    val determinant: Double
        get() = m00 * m11 - m01 * m10

    /**
     * Computes the inverse of this transformation, if one
     * exists.
     * The inverse is the transformation which when
     * composed with this one produces the identity
     * transformation.
     * A transformation has an inverse if and only if it
     * is not singular (i.e. its
     * determinant is non-zero).
     * Geometrically, an transformation is non-invertible
     * if it maps the plane to a line or a point.
     * If no inverse exists this method
     * will throw a <tt>NoninvertibleTransformationException</tt>.
     *
     * The matrix of the inverse is equal to the
     * inverse of the matrix for the transformation.
     * It is computed as follows:
     * <blockquote><pre>
     * 1
     * inverse(A)  =  ---   x  adjoint(A)
     * det
     *
     * =   1       |  m11  -m01   m01*m12-m02*m11  |
     * ---   x  | -m10   m00  -m00*m12+m10*m02  |
     * det      |  0     0     m00*m11-m10*m01  |
     *
     *
     * = |  m11/det  -m01/det   m01*m12-m02*m11/det |
     * | -m10/det   m00/det  -m00*m12+m10*m02/det |
     * |   0           0          1               |
     *
    </pre></blockquote> *
     *
     * @return a new inverse transformation
     * @throws NoninvertibleTransformationException
     * @see .getDeterminant
     */
//    @get:Throws(NoninvertibleTransformationException::class)
    val inverse: AffineTransformation
        get() {
            val det = determinant
            if (det == 0.0) throw NoninvertibleTransformationException("Transformation is non-invertible")
            val im00 = m11 / det
            val im10 = -m10 / det
            val im01 = -m01 / det
            val im11 = m00 / det
            val im02 = (m01 * m12 - m02 * m11) / det
            val im12 = (-m00 * m12 + m10 * m02) / det
            return AffineTransformation(im00, im01, im02, im10, im11, im12)
        }

    /**
     * Explicitly computes the math for a reflection.  May not work.
     * @param x0 the X ordinate of one point on the reflection line
     * @param y0 the Y ordinate of one point on the reflection line
     * @param x1 the X ordinate of another point on the reflection line
     * @param y1 the Y ordinate of another point on the reflection line
     * @return this transformation, with an updated matrix
     */
    fun setToReflectionBasic(x0: Double, y0: Double, x1: Double, y1: Double): AffineTransformation {
        if (x0 == x1 && y0 == y1) {
            throw IllegalArgumentException("Reflection line points must be distinct")
        }
        val dx = x1 - x0
        val dy = y1 - y0
        val d: Double = sqrt(dx * dx + dy * dy)
        val sin = dy / d
        val cos = dx / d
        val cs2 = 2 * sin * cos
        val c2s2 = cos * cos - sin * sin
        m00 = c2s2
        m01 = cs2
        m02 = 0.0
        m10 = cs2
        m11 = -c2s2
        m12 = 0.0
        return this
    }

    /**
     * Sets this transformation to be a reflection
     * about the line defined by a line <tt>(x0,y0) - (x1,y1)</tt>.
     *
     * @param x0 the X ordinate of one point on the reflection line
     * @param y0 the Y ordinate of one point on the reflection line
     * @param x1 the X ordinate of another point on the reflection line
     * @param y1 the Y ordinate of another point on the reflection line
     * @return this transformation, with an updated matrix
     */
    fun setToReflection(x0: Double, y0: Double, x1: Double, y1: Double): AffineTransformation {
        if (x0 == x1 && y0 == y1) {
            throw IllegalArgumentException("Reflection line points must be distinct")
        }
        // translate line vector to origin
        setToTranslation(-x0, -y0)

        // rotate vector to positive x axis direction
        val dx = x1 - x0
        val dy = y1 - y0
        val d: Double = sqrt(dx * dx + dy * dy)
        val sin = dy / d
        val cos = dx / d
        rotate(-sin, cos)
        // reflect about the x axis
        scale(1.0, -1.0)
        // rotate back
        rotate(sin, cos)
        // translate back
        translate(x0, y0)
        return this
    }

    /**
     * Sets this transformation to be a reflection
     * about the line defined by vector (x,y).
     * The transformation for a reflection
     * is computed by:
     * <blockquote>`
     * d = sqrt(x<sup>2</sup> + y<sup>2</sup>) <br></br>
     * sin = y / d;<br></br>
     * cos = x / d;<br></br>
     * <br></br>
     * T<sub>ref</sub> = T<sub>rot(sin, cos)</sub> x T<sub>scale(1, -1)</sub> x T<sub>rot(-sin, cos)</sub><br></br>
    `</blockquote> *
     *
     * @param x the x-component of the reflection line vector
     * @param y the y-component of the reflection line vector
     * @return this transformation, with an updated matrix
     */
    fun setToReflection(x: Double, y: Double): AffineTransformation {
        if (x == 0.0 && y == 0.0) {
            throw IllegalArgumentException("Reflection vector must be non-zero")
        }
        /**
         * Handle special case - x = y.
         * This case is specified explicitly to avoid roundoff error.
         */
        if (x == y) {
            m00 = 0.0
            m01 = 1.0
            m02 = 0.0
            m10 = 1.0
            m11 = 0.0
            m12 = 0.0
            return this
        }

        // rotate vector to positive x axis direction
        val d: Double = sqrt(x * x + y * y)
        val sin = y / d
        val cos = x / d
        rotate(-sin, cos)
        // reflect about the x-axis
        scale(1.0, -1.0)
        // rotate back
        rotate(sin, cos)
        return this
    }

    /**
     * Sets this transformation to be a rotation around the origin.
     * A positive rotation angle corresponds
     * to a counter-clockwise rotation.
     * The transformation matrix for a rotation
     * by an angle <tt>theta</tt>
     * has the value:
     * <blockquote><pre>
     * |  cos(theta)  -sin(theta)   0 |
     * |  sin(theta)   cos(theta)   0 |
     * |           0            0   1 |
    </pre></blockquote> *
     *
     * @param theta the rotation angle, in radians
     * @return this transformation, with an updated matrix
     */
    fun setToRotation(theta: Double): AffineTransformation {
        setToRotation(sin(theta), cos(theta))
        return this
    }

    /**
     * Sets this transformation to be a rotation around the origin
     * by specifying the sin and cos of the rotation angle directly.
     * The transformation matrix for the rotation
     * has the value:
     * <blockquote><pre>
     * |  cosTheta  -sinTheta   0 |
     * |  sinTheta   cosTheta   0 |
     * |         0          0   1 |
    </pre></blockquote> *
     *
     * @param sinTheta the sine of the rotation angle
     * @param cosTheta the cosine of the rotation angle
     * @return this transformation, with an updated matrix
     */
    fun setToRotation(sinTheta: Double, cosTheta: Double): AffineTransformation {
        m00 = cosTheta
        m01 = -sinTheta
        m02 = 0.0
        m10 = sinTheta
        m11 = cosTheta
        m12 = 0.0
        return this
    }

    /**
     * Sets this transformation to be a rotation
     * around a given point (x,y).
     * A positive rotation angle corresponds
     * to a counter-clockwise rotation.
     * The transformation matrix for a rotation
     * by an angle <tt>theta</tt>
     * has the value:
     * <blockquote><pre>
     * |  cosTheta  -sinTheta   x-x*cos+y*sin |
     * |  sinTheta   cosTheta   y-x*sin-y*cos |
     * |           0            0   1 |
    </pre></blockquote> *
     *
     * @param theta the rotation angle, in radians
     * @param x the x-ordinate of the rotation point
     * @param y the y-ordinate of the rotation point
     * @return this transformation, with an updated matrix
     */
    fun setToRotation(theta: Double, x: Double, y: Double): AffineTransformation {
        setToRotation(sin(theta), cos(theta), x, y)
        return this
    }

    /**
     * Sets this transformation to be a rotation
     * around a given point (x,y)
     * by specifying the sin and cos of the rotation angle directly.
     * The transformation matrix for the rotation
     * has the value:
     * <blockquote><pre>
     * |  cosTheta  -sinTheta   x-x*cos+y*sin |
     * |  sinTheta   cosTheta   y-x*sin-y*cos |
     * |         0          0         1       |
    </pre></blockquote> *
     *
     * @param sinTheta the sine of the rotation angle
     * @param cosTheta the cosine of the rotation angle
     * @param x the x-ordinate of the rotation point
     * @param y the y-ordinate of the rotation point
     * @return this transformation, with an updated matrix
     */
    fun setToRotation(sinTheta: Double, cosTheta: Double, x: Double, y: Double): AffineTransformation {
        m00 = cosTheta
        m01 = -sinTheta
        m02 = x - x * cosTheta + y * sinTheta
        m10 = sinTheta
        m11 = cosTheta
        m12 = y - x * sinTheta - y * cosTheta
        return this
    }

    /**
     * Sets this transformation to be a scaling.
     * The transformation matrix for a scale
     * has the value:
     * <blockquote><pre>
     * |  xScale      0  dx |
     * |  0      yScale  dy |
     * |  0           0   1 |
    </pre></blockquote> *
     *
     * @param xScale the amount to scale x-ordinates by
     * @param yScale the amount to scale y-ordinates by
     * @return this transformation, with an updated matrix
     */
    fun setToScale(xScale: Double, yScale: Double): AffineTransformation {
        m00 = xScale
        m01 = 0.0
        m02 = 0.0
        m10 = 0.0
        m11 = yScale
        m12 = 0.0
        return this
    }

    /**
     * Sets this transformation to be a shear.
     * The transformation matrix for a shear
     * has the value:
     * <blockquote><pre>
     * |  1      xShear  0 |
     * |  yShear      1  0 |
     * |  0           0  1 |
    </pre></blockquote> *
     * Note that a shear of (1, 1) is *not*
     * equal to shear(1, 0) composed with shear(0, 1).
     * Instead, shear(1, 1) corresponds to a mapping onto the
     * line x = y.
     *
     * @param xShear the x component to shear by
     * @param yShear the y component to shear by
     * @return this transformation, with an updated matrix
     */
    fun setToShear(xShear: Double, yShear: Double): AffineTransformation {
        m00 = 1.0
        m01 = xShear
        m02 = 0.0
        m10 = yShear
        m11 = 1.0
        m12 = 0.0
        return this
    }

    /**
     * Sets this transformation to be a translation.
     * For a translation by the vector (x, y)
     * the transformation matrix has the value:
     * <blockquote><pre>
     * |  1  0  dx |
     * |  1  0  dy |
     * |  0  0   1 |
    </pre></blockquote> *
     * @param dx the x component to translate by
     * @param dy the y component to translate by
     * @return this transformation, with an updated matrix
     */
    fun setToTranslation(dx: Double, dy: Double): AffineTransformation {
        m00 = 1.0
        m01 = 0.0
        m02 = dx
        m10 = 0.0
        m11 = 1.0
        m12 = dy
        return this
    }

    /**
     * Updates the value of this transformation
     * to that of a reflection transformation composed
     * with the current value.
     *
     * @param x0 the x-ordinate of a point on the line to reflect around
     * @param y0 the y-ordinate of a point on the line to reflect around
     * @param x1 the x-ordinate of a point on the line to reflect around
     * @param y1 the y-ordinate of a point on the line to reflect around
     * @return this transformation, with an updated matrix
     */
    fun reflect(x0: Double, y0: Double, x1: Double, y1: Double): AffineTransformation {
        compose(reflectionInstance(x0, y0, x1, y1))
        return this
    }

    /**
     * Updates the value of this transformation
     * to that of a reflection transformation composed
     * with the current value.
     *
     * @param x the x-ordinate of the line to reflect around
     * @param y the y-ordinate of the line to reflect around
     * @return this transformation, with an updated matrix
     */
    fun reflect(x: Double, y: Double): AffineTransformation {
        compose(reflectionInstance(x, y))
        return this
    }

    /**
     * Updates the value of this transformation
     * to that of a rotation transformation composed
     * with the current value.
     * Positive angles correspond to a rotation
     * in the counter-clockwise direction.
     *
     * @param theta the angle to rotate by, in radians
     * @return this transformation, with an updated matrix
     */
    fun rotate(theta: Double): AffineTransformation {
        compose(rotationInstance(theta))
        return this
    }

    /**
     * Updates the value of this transformation
     * to that of a rotation around the origin composed
     * with the current value,
     * with the sin and cos of the rotation angle specified directly.
     *
     * @param sinTheta the sine of the angle to rotate by
     * @param cosTheta the cosine of the angle to rotate by
     * @return this transformation, with an updated matrix
     */
    fun rotate(sinTheta: Double, cosTheta: Double): AffineTransformation {
        compose(rotationInstance(sinTheta, cosTheta))
        return this
    }

    /**
     * Updates the value of this transformation
     * to that of a rotation around a given point composed
     * with the current value.
     * Positive angles correspond to a rotation
     * in the counter-clockwise direction.
     *
     * @param theta the angle to rotate by, in radians
     * @param x the x-ordinate of the rotation point
     * @param y the y-ordinate of the rotation point
     * @return this transformation, with an updated matrix
     */
    fun rotate(theta: Double, x: Double, y: Double): AffineTransformation {
        compose(rotationInstance(theta, x, y))
        return this
    }

    /**
     * Updates the value of this transformation
     * to that of a rotation around a given point composed
     * with the current value,
     * with the sin and cos of the rotation angle specified directly.
     *
     * @param sinTheta the sine of the angle to rotate by
     * @param cosTheta the cosine of the angle to rotate by
     * @param x the x-ordinate of the rotation point
     * @param y the y-ordinate of the rotation point
     * @return this transformation, with an updated matrix
     */
    fun rotate(sinTheta: Double, cosTheta: Double, x: Double, y: Double): AffineTransformation {
        compose(rotationInstance(sinTheta, cosTheta, x, y))
        return this
    }

    /**
     * Updates the value of this transformation
     * to that of a scale transformation composed
     * with the current value.
     *
     * @param xScale the value to scale by in the x direction
     * @param yScale the value to scale by in the y direction
     * @return this transformation, with an updated matrix
     */
    fun scale(xScale: Double, yScale: Double): AffineTransformation {
        compose(scaleInstance(xScale, yScale))
        return this
    }

    /**
     * Updates the value of this transformation
     * to that of a shear transformation composed
     * with the current value.
     *
     * @param xShear the value to shear by in the x direction
     * @param yShear the value to shear by in the y direction
     * @return this transformation, with an updated matrix
     */
    fun shear(xShear: Double, yShear: Double): AffineTransformation {
        compose(shearInstance(xShear, yShear))
        return this
    }

    /**
     * Updates the value of this transformation
     * to that of a translation transformation composed
     * with the current value.
     *
     * @param x the value to translate by in the x direction
     * @param y the value to translate by in the y direction
     * @return this transformation, with an updated matrix
     */
    fun translate(x: Double, y: Double): AffineTransformation {
        compose(translationInstance(x, y))
        return this
    }

    /**
     * Updates this transformation to be
     * the composition of this transformation with the given [AffineTransformation].
     * This produces a transformation whose effect
     * is equal to applying this transformation
     * followed by the argument transformation.
     * Mathematically,
     * <blockquote>`
     * A.compose(B) = T<sub>B</sub> x T<sub>A</sub>
    `</blockquote> *
     *
     * @param trans an affine transformation
     * @return this transformation, with an updated matrix
     */
    fun compose(trans: AffineTransformation): AffineTransformation {
        val mp00 = trans.m00 * m00 + trans.m01 * m10
        val mp01 = trans.m00 * m01 + trans.m01 * m11
        val mp02 = trans.m00 * m02 + trans.m01 * m12 + trans.m02
        val mp10 = trans.m10 * m00 + trans.m11 * m10
        val mp11 = trans.m10 * m01 + trans.m11 * m11
        val mp12 = trans.m10 * m02 + trans.m11 * m12 + trans.m12
        m00 = mp00
        m01 = mp01
        m02 = mp02
        m10 = mp10
        m11 = mp11
        m12 = mp12
        return this
    }

    /**
     * Updates this transformation to be the composition
     * of a given [AffineTransformation] with this transformation.
     * This produces a transformation whose effect
     * is equal to applying the argument transformation
     * followed by this transformation.
     * Mathematically,
     * <blockquote>`
     * A.composeBefore(B) = T<sub>A</sub> x T<sub>B</sub>
    `</blockquote> *
     *
     * @param trans an affine transformation
     * @return this transformation, with an updated matrix
     */
    fun composeBefore(trans: AffineTransformation): AffineTransformation {
        val mp00 = m00 * trans.m00 + m01 * trans.m10
        val mp01 = m00 * trans.m01 + m01 * trans.m11
        val mp02 = m00 * trans.m02 + m01 * trans.m12 + m02
        val mp10 = m10 * trans.m00 + m11 * trans.m10
        val mp11 = m10 * trans.m01 + m11 * trans.m11
        val mp12 = m10 * trans.m02 + m11 * trans.m12 + m12
        m00 = mp00
        m01 = mp01
        m02 = mp02
        m10 = mp10
        m11 = mp11
        m12 = mp12
        return this
    }

    /**
     * Applies this transformation to the <tt>src</tt> coordinate
     * and places the results in the <tt>dest</tt> coordinate
     * (which may be the same as the source).
     *
     * @param src the coordinate to transform
     * @param dest the coordinate to accept the results
     * @return the <tt>dest</tt> coordinate
     */
    fun transform(src: Coordinate, dest: Coordinate): Coordinate {
        val xp = m00 * src.x + m01 * src.y + m02
        val yp = m10 * src.x + m11 * src.y + m12
        dest.x = xp
        dest.y = yp
        return dest
    }

    /**
     * Creates a new [Geometry] which is the result
     * of this transformation applied to the input Geometry.
     *
     * @param g  a `Geometry`
     * @return a transformed Geometry
     */
    fun transform(g: Geometry): Geometry {
        val g2 = g.copy()
        g2.apply(this)
        return g2
    }

    /**
     * Applies this transformation to the i'th coordinate
     * in the given CoordinateSequence.
     *
     * @param seq  a `CoordinateSequence`
     * @param i the index of the coordinate to transform
     */
    fun transform(seq: CoordinateSequence?, i: Int) {
        val xp = m00 * seq!!.getOrdinate(i, 0) + m01 * seq.getOrdinate(i, 1) + m02
        val yp = m10 * seq.getOrdinate(i, 0) + m11 * seq.getOrdinate(i, 1) + m12
        seq.setOrdinate(i, 0, xp)
        seq.setOrdinate(i, 1, yp)
    }

    /**
     * Transforms the i'th coordinate in the input sequence
     *
     * @param seq  a `CoordinateSequence`
     * @param i the index of the coordinate to transform
     */
    override fun filter(seq: CoordinateSequence?, i: Int) {
        transform(seq, i)
    }

    override val isGeometryChanged: Boolean
        get() = true

    /**
     * Reports that this filter should continue to be executed until
     * all coordinates have been transformed.
     *
     * @return false
     */
    override val isDone: Boolean
        get() = false

    /**
     * Tests if this transformation is the identity transformation.
     *
     * @return true if this is the identity transformation
     */
    val isIdentity: Boolean
        get() = m00 == 1.0 && m01 == 0.0 && m02 == 0.0 && m10 == 0.0 && m11 == 1.0 && m12 == 0.0

    /**
     * Tests if an object is an
     * <tt>AffineTransformation</tt>
     * and has the same matrix as
     * this transformation.
     *
     * @param obj an object to test
     * @return true if the given object is equal to this object
     */
    override fun equals(obj: Any?): Boolean {
        if (obj == null) return false
        if (obj !is AffineTransformation) return false
        val trans = obj
        return m00 == trans.m00 && m01 == trans.m01 && m02 == trans.m02 && m10 == trans.m10 && m11 == trans.m11 && m12 == trans.m12
    }

    /* (non-Javadoc)
   * @see java.lang.Object#hashCode()
   */
    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        var temp: Long = doubleToLongBits(m00)
        result = prime * result + (temp xor (temp ushr 32)).toInt()
        temp = doubleToLongBits(m01)
        result = prime * result + (temp xor (temp ushr 32)).toInt()
        temp = doubleToLongBits(m02)
        result = prime * result + (temp xor (temp ushr 32)).toInt()
        temp = doubleToLongBits(m10)
        result = prime * result + (temp xor (temp ushr 32)).toInt()
        temp = doubleToLongBits(m11)
        result = prime * result + (temp xor (temp ushr 32)).toInt()
        temp = doubleToLongBits(m12)
        result = prime * result + (temp xor (temp ushr 32)).toInt()
        return result
    }

    /**
     * Gets a text representation of this transformation.
     * The string is of the form:
     * <pre>
     * AffineTransformation[[m00, m01, m02], [m10, m11, m12]]
    </pre> *
     *
     * @return a string representing this transformation
     */
    override fun toString(): String {
        return ("AffineTransformation[[" + m00 + ", " + m01 + ", " + m02
                + "], ["
                + m10 + ", " + m11 + ", " + m12 + "]]")
    }

    /**
     * Clones this transformation
     *
     * @return a copy of this transformation
     */
    override fun clone(): Any {
//        try {
//            return super<Cloneable>.clone()
        return AffineTransformation(this)
//        } catch (ex: Exception) {
//            shouldNeverReachHere()
//        }
//        return null
    }

    companion object {
        /**
         * Creates a transformation for a reflection about the
         * line (x0,y0) - (x1,y1).
         *
         * @param x0 the x-ordinate of a point on the reflection line
         * @param y0 the y-ordinate of a point on the reflection line
         * @param x1 the x-ordinate of a another point on the reflection line
         * @param y1 the y-ordinate of a another point on the reflection line
         * @return a transformation for the reflection
         */
        @JvmStatic
        fun reflectionInstance(x0: Double, y0: Double, x1: Double, y1: Double): AffineTransformation {
            val trans = AffineTransformation()
            trans.setToReflection(x0, y0, x1, y1)
            return trans
        }

        /**
         * Creates a transformation for a reflection about the
         * line (0,0) - (x,y).
         *
         * @param x the x-ordinate of a point on the reflection line
         * @param y the y-ordinate of a point on the reflection line
         * @return a transformation for the reflection
         */
        @JvmStatic
        fun reflectionInstance(x: Double, y: Double): AffineTransformation {
            val trans = AffineTransformation()
            trans.setToReflection(x, y)
            return trans
        }

        /**
         * Creates a transformation for a rotation
         * about the origin
         * by an angle *theta*.
         * Positive angles correspond to a rotation
         * in the counter-clockwise direction.
         *
         * @param theta the rotation angle, in radians
         * @return a transformation for the rotation
         */
        @JvmStatic
        fun rotationInstance(theta: Double): AffineTransformation {
            return rotationInstance(sin(theta), cos(theta))
        }

        /**
         * Creates a transformation for a rotation
         * by an angle *theta*,
         * specified by the sine and cosine of the angle.
         * This allows providing exact values for sin(theta) and cos(theta)
         * for the common case of rotations of multiples of quarter-circles.
         *
         * @param sinTheta the sine of the rotation angle
         * @param cosTheta the cosine of the rotation angle
         * @return a transformation for the rotation
         */
        @JvmStatic
        fun rotationInstance(sinTheta: Double, cosTheta: Double): AffineTransformation {
            val trans = AffineTransformation()
            trans.setToRotation(sinTheta, cosTheta)
            return trans
        }

        /**
         * Creates a transformation for a rotation
         * about the point (x,y) by an angle *theta*.
         * Positive angles correspond to a rotation
         * in the counter-clockwise direction.
         *
         * @param theta the rotation angle, in radians
         * @param x the x-ordinate of the rotation point
         * @param y the y-ordinate of the rotation point
         * @return a transformation for the rotation
         */
        @JvmStatic
        fun rotationInstance(theta: Double, x: Double, y: Double): AffineTransformation {
            return rotationInstance(sin(theta), cos(theta), x, y)
        }

        /**
         * Creates a transformation for a rotation
         * about the point (x,y) by an angle *theta*,
         * specified by the sine and cosine of the angle.
         * This allows providing exact values for sin(theta) and cos(theta)
         * for the common case of rotations of multiples of quarter-circles.
         *
         * @param sinTheta the sine of the rotation angle
         * @param cosTheta the cosine of the rotation angle
         * @param x the x-ordinate of the rotation point
         * @param y the y-ordinate of the rotation point
         * @return a transformation for the rotation
         */
        @JvmStatic
        fun rotationInstance(sinTheta: Double, cosTheta: Double, x: Double, y: Double): AffineTransformation {
            val trans = AffineTransformation()
            trans.setToRotation(sinTheta, cosTheta, x, y)
            return trans
        }

        /**
         * Creates a transformation for a scaling relative to the origin.
         *
         * @param xScale the value to scale by in the x direction
         * @param yScale the value to scale by in the y direction
         * @return a transformation for the scaling
         */
        @JvmStatic
        fun scaleInstance(xScale: Double, yScale: Double): AffineTransformation {
            val trans = AffineTransformation()
            trans.setToScale(xScale, yScale)
            return trans
        }

        /**
         * Creates a transformation for a scaling relative to the point (x,y).
         *
         * @param xScale the value to scale by in the x direction
         * @param yScale the value to scale by in the y direction
         * @param x the x-ordinate of the point to scale around
         * @param y the y-ordinate of the point to scale around
         * @return a transformation for the scaling
         */
        @JvmStatic
        fun scaleInstance(xScale: Double, yScale: Double, x: Double, y: Double): AffineTransformation {
            val trans = AffineTransformation()
            trans.translate(-x, -y)
            trans.scale(xScale, yScale)
            trans.translate(x, y)
            return trans
        }

        /**
         * Creates a transformation for a shear.
         *
         * @param xShear the value to shear by in the x direction
         * @param yShear the value to shear by in the y direction
         * @return a transformation for the shear
         */
        @JvmStatic
        fun shearInstance(xShear: Double, yShear: Double): AffineTransformation {
            val trans = AffineTransformation()
            trans.setToShear(xShear, yShear)
            return trans
        }

        /**
         * Creates a transformation for a translation.
         *
         * @param x the value to translate by in the x direction
         * @param y the value to translate by in the y direction
         * @return a transformation for the translation
         */
        @JvmStatic
        fun translationInstance(x: Double, y: Double): AffineTransformation {
            val trans = AffineTransformation()
            trans.setToTranslation(x, y)
            return trans
        }
    }
}