/*
 * Copyright (c) 2020 Macrofocus GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.legacy

import org.locationtech.jts.geom.Coordinate

object System {
    /** Copies [size] elements of [src] starting at [srcPos] into [dst] at [dstPos]  */
    inline fun arraycopy(src: Array<Coordinate>, srcPos: Int, dst: Array<Coordinate?>, dstPos: Int, size: Int): Unit =
        run { src.copyInto(dst, dstPos, srcPos, srcPos + size) }

    /** Copies [size] elements of [src] starting at [srcPos] into [dst] at [dstPos]  */
    inline fun <T> arraycopy(src: Array<T>, srcPos: Int, dst: Array<T>, dstPos: Int, size: Int): Unit =
        run { src.copyInto(dst, dstPos, srcPos, srcPos + size) }

    /** Copies [size] elements of [src] starting at [srcPos] into [dst] at [dstPos]  */
    inline fun arraycopy(src: BooleanArray, srcPos: Int, dst: BooleanArray, dstPos: Int, size: Int): Unit =
        run { src.copyInto(dst, dstPos, srcPos, srcPos + size) }

    /** Copies [size] elements of [src] starting at [srcPos] into [dst] at [dstPos]  */
    inline fun arraycopy(src: LongArray, srcPos: Int, dst: LongArray, dstPos: Int, size: Int): Unit =
        run { src.copyInto(dst, dstPos, srcPos, srcPos + size) }

    /** Copies [size] elements of [src] starting at [srcPos] into [dst] at [dstPos]  */
    inline fun arraycopy(src: ByteArray, srcPos: Int, dst: ByteArray, dstPos: Int, size: Int): Unit =
        run { src.copyInto(dst, dstPos, srcPos, srcPos + size) }

    /** Copies [size] elements of [src] starting at [srcPos] into [dst] at [dstPos]  */
    inline fun arraycopy(src: ShortArray, srcPos: Int, dst: ShortArray, dstPos: Int, size: Int): Unit =
        run { src.copyInto(dst, dstPos, srcPos, srcPos + size) }

    /** Copies [size] elements of [src] starting at [srcPos] into [dst] at [dstPos]  */
    inline fun arraycopy(src: CharArray, srcPos: Int, dst: CharArray, dstPos: Int, size: Int): Unit =
        run { src.copyInto(dst, dstPos, srcPos, srcPos + size) }

    /** Copies [size] elements of [src] starting at [srcPos] into [dst] at [dstPos]  */
    inline fun arraycopy(src: IntArray, srcPos: Int, dst: IntArray, dstPos: Int, size: Int): Unit =
        run { src.copyInto(dst, dstPos, srcPos, srcPos + size) }

    /** Copies [size] elements of [src] starting at [srcPos] into [dst] at [dstPos]  */
    inline fun arraycopy(src: FloatArray, srcPos: Int, dst: FloatArray, dstPos: Int, size: Int): Unit =
        run { src.copyInto(dst, dstPos, srcPos, srcPos + size) }

    /** Copies [size] elements of [src] starting at [srcPos] into [dst] at [dstPos]  */
    inline fun arraycopy(src: DoubleArray, srcPos: Int, dst: DoubleArray, dstPos: Int, size: Int): Unit =
        run { src.copyInto(dst, dstPos, srcPos, srcPos + size) }

//    /** Copies [size] elements of [src] starting at [srcPos] into [dst] at [dstPos]  */
//    inline fun <R> arraycopy(src: Array<R>, srcPos: Int, dst: Array<R>, dstPos: Int, size: Int): Unit =
//        run { src.copyInto(dst, dstPos, srcPos, srcPos + size) }
}