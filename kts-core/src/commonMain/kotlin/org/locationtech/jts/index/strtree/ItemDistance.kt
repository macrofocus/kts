/*
 * Copyright (c) 2016 Martin Davis.
 * Copyright (c) 2022 Macrofocus GmbH and Luc Girardin.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.index.strtree

/**
 * A function method which computes the distance
 * between two [ItemBoundable]s in an [STRtree].
 * Used for Nearest Neighbour searches.
 *
 * To make a distance function suitable for
 * querying a single index tree
 * via [STRtree.nearestNeighbour] ,
 * the function should have a non-zero *reflexive distance*.
 * That is, if the two arguments are the same object,
 * the distance returned should be non-zero.
 * If it is required that only pairs of **distinct** items be returned,
 * the distance function must be *anti-reflexive*,
 * and must return [Double.MAX_VALUE] for identical arguments.
 *
 * @author Martin Davis
 * @author Luc Girardin
 */
interface ItemDistance {
    /**
     * Computes the distance between two items.
     *
     * @param item1
     * @param item2
     * @return the distance between the items
     *
     * @throws IllegalArgumentException if the metric is not applicable to the arguments
     */
    fun distance(item1: ItemBoundable, item2: ItemBoundable): Double
}