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

import org.locationtech.jts.algorithm.Angle.angleBetweenOriented
import org.locationtech.jts.geom.Coordinate
import kotlin.jvm.JvmStatic

/**
 * Supports creating [AffineTransformation]s defined by various kinds of
 * inputs and transformation mapping rules.
 *
 * @author Martin Davis
 * @author Luc Girardin
 */
object AffineTransformationFactory {
    /**
     * Creates a transformation from a set of three control vectors. A control
     * vector consists of a source point and a destination point, which is the
     * image of the source point under the desired transformation. Three control
     * vectors allows defining a fully general affine transformation.
     *
     * @param src0
     * @param src1
     * @param src2
     * @param dest0
     * @param dest1
     * @param dest2
     * @return the computed transformation
     */
    fun createFromControlVectors(
        src0: Coordinate,
        src1: Coordinate, src2: Coordinate, dest0: Coordinate, dest1: Coordinate,
        dest2: Coordinate
    ): AffineTransformation? {
        val builder = AffineTransformationBuilder(
            src0,
            src1, src2, dest0, dest1, dest2
        )
        return builder.transformation
    }

    /**
     * Creates an AffineTransformation defined by a pair of control vectors. A
     * control vector consists of a source point and a destination point, which is
     * the image of the source point under the desired transformation. The
     * computed transformation is a combination of one or more of a uniform scale,
     * a rotation, and a translation (i.e. there is no shear component and no
     * reflection)
     *
     * @param src0
     * @param src1
     * @param dest0
     * @param dest1
     * @return the computed transformation, or null if the control vectors do not determine a well-defined transformation
     */
    @JvmStatic
    fun createFromControlVectors(
        src0: Coordinate,
        src1: Coordinate, dest0: Coordinate, dest1: Coordinate
    ): AffineTransformation? {
        val rotPt = Coordinate(dest1.x - dest0.x, dest1.y - dest0.y)
        val ang = angleBetweenOriented(src1, src0, rotPt)
        val srcDist = src1.distance(src0)
        val destDist = dest1.distance(dest0)
        if (srcDist == 0.0) return null
        val scale = destDist / srcDist
        val trans: AffineTransformation = AffineTransformation.translationInstance(
            -src0.x, -src0.y
        )
        trans.rotate(ang)
        trans.scale(scale, scale)
        trans.translate(dest0.x, dest0.y)
        return trans
    }

    /**
     * Creates an AffineTransformation defined by a single control vector. A
     * control vector consists of a source point and a destination point, which is
     * the image of the source point under the desired transformation. This
     * produces a translation.
     *
     * @param src0
     * the start point of the control vector
     * @param dest0
     * the end point of the control vector
     * @return the computed transformation
     */
    @JvmStatic
    fun createFromControlVectors(
        src0: Coordinate,
        dest0: Coordinate
    ): AffineTransformation {
        val dx = dest0.x - src0.x
        val dy = dest0.y - src0.y
        return AffineTransformation.translationInstance(dx, dy)
    }

    /**
     * Creates an AffineTransformation defined by a set of control vectors.
     * Between one and three vectors must be supplied.
     *
     * @param src
     * the source points of the vectors
     * @param dest
     * the destination points of the vectors
     * @return the computed transformation
     * @throws IllegalArgumentException
     * if the control vector arrays are too short, long or of different
     * lengths
     */
    fun createFromControlVectors(
        src: Array<Coordinate>,
        dest: Array<Coordinate>
    ): AffineTransformation? {
        require(src.size == dest.size) { "Src and Dest arrays are not the same length" }
        require(src.isNotEmpty()) { "Too few control points" }
        require(src.size <= 3) { "Too many control points" }
        if (src.size == 1) return createFromControlVectors(src[0], dest[0])
        return if (src.size == 2) createFromControlVectors(
            src[0],
            src[1],
            dest[0],
            dest[1]
        ) else createFromControlVectors(
            src[0], src[1], src[2], dest[0], dest[1],
            dest[2]
        )
    }

    /**
     * Creates an AffineTransformation defined by a mapping between two baselines.
     * The computed transformation consists of:
     *
     *  * a translation
     * from the start point of the source baseline to the start point of the destination baseline,
     *  * a rotation through the angle between the baselines about the destination start point,
     *  * and a scaling equal to the ratio of the baseline lengths.
     *
     * If the source baseline has zero length, an identity transformation is returned.
     *
     * @param src0 the start point of the source baseline
     * @param src1 the end point of the source baseline
     * @param dest0 the start point of the destination baseline
     * @param dest1 the end point of the destination baseline
     * @return the computed transformation
     */
    fun createFromBaseLines(
        src0: Coordinate, src1: Coordinate,
        dest0: Coordinate, dest1: Coordinate
    ): AffineTransformation {
        val rotPt = Coordinate(src0.x + dest1.x - dest0.x, src0.y + dest1.y - dest0.y)
        val ang = angleBetweenOriented(src1, src0, rotPt)
        val srcDist = src1.distance(src0)
        val destDist = dest1.distance(dest0)

        // return identity if transformation would be degenerate
        if (srcDist == 0.0) return AffineTransformation()
        val scale = destDist / srcDist
        val trans: AffineTransformation = AffineTransformation.translationInstance(
            -src0.x, -src0.y
        )
        trans.rotate(ang)
        trans.scale(scale, scale)
        trans.translate(dest0.x, dest0.y)
        return trans
    }
}