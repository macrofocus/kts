/*
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 * Copyright (c) 2020 Macrofocus GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.geom

import kotlin.jvm.JvmOverloads

/**
 * Useful utility functions for handling Coordinate objects.
 */
object Coordinates {
    /**
     * Factory method providing access to common Coordinate implementations.
     *
     * @param dimension
     * @param measures
     * @return created coordinate
     */
    /**
     * Factory method providing access to common Coordinate implementations.
     *
     * @param dimension
     * @return created coordinate
     */
    @JvmOverloads
    fun create(dimension: Int, measures: Int = 0): Coordinate {
        if (dimension == 2) {
            return CoordinateXY()
        } else if (dimension == 3 && measures == 0) {
            return Coordinate()
        } else if (dimension == 3 && measures == 1) {
            return CoordinateXYM()
        } else if (dimension == 4 && measures == 1) {
            return CoordinateXYZM()
        }
        return Coordinate()
    }

    /**
     * Determine dimension based on subclass of [Coordinate].
     *
     * @param coordinate supplied coordinate
     * @return number of ordinates recorded
     */
    fun dimension(coordinate: Coordinate?): Int {
        return when (coordinate) {
            is CoordinateXY -> {
                2
            }
            is CoordinateXYM -> {
                3
            }
            is CoordinateXYZM -> {
                4
            }
            is Coordinate -> {
                3
            }
            else -> 3
        }
    }

    /**
     * Determine number of measures based on subclass of [Coordinate].
     *
     * @param coordinate supplied coordinate
     * @return number of measures recorded
     */
    fun measures(coordinate: Coordinate?): Int {
        when (coordinate) {
            is CoordinateXY -> {
                return 0
            }
            is CoordinateXYM -> {
                return 1
            }
            is CoordinateXYZM -> {
                return 1
            }
            is Coordinate -> {
                return 0
            }
            else -> return 0
        }
    }
}