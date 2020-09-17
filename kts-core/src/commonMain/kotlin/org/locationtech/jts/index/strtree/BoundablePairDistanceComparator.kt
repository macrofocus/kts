/*
 * Copyright (c) 2017 Jia Yu.
 * Copyright (c) 2020 Macrofocus GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.locationtech.jts.index.strtree

import org.locationtech.jts.legacy.Serializable

/**
 * The Class BoundablePairDistanceComparator. It implements Java comparator and is used
 * as a parameter to sort the BoundablePair list.
 */
class BoundablePairDistanceComparator
/**
 * Instantiates a new boundable pair distance comparator.
 *
 * @param normalOrder true puts the lowest record at the head of this queue.
 * This is the natural order. PriorityQueue peek() will get the least element.
 */(
    /** The normal order.  */
    var normalOrder: Boolean
) : Comparator<BoundablePair>, Serializable {
    /* (non-Javadoc)
	 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
	 */
    override fun compare(p1: BoundablePair, p2: BoundablePair): Int {
        val distance1 = p1.distance
        val distance2 = p2.distance
        return if (normalOrder) {
            if (distance1 > distance2) {
                return 1
            } else if (distance1 == distance2) {
                return 0
            }
            -1
        } else {
            if (distance1 > distance2) {
                return -1
            } else if (distance1 == distance2) {
                return 0
            }
            1
        }
    }
}