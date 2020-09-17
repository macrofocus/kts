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
package org.locationtech.jts.index.intervalrtree

import org.locationtech.jts.index.ItemVisitor
import org.locationtech.jts.legacy.Math

class IntervalRTreeBranchNode(private val node1: IntervalRTreeNode?, private val node2: IntervalRTreeNode?) :
    IntervalRTreeNode() {
    private fun buildExtent(n1: IntervalRTreeNode?, n2: IntervalRTreeNode?) {
        min = Math.min(n1!!.min, n2!!.min)
        max = Math.max(n1.max, n2.max)
    }

    override fun query(queryMin: Double, queryMax: Double, visitor: ItemVisitor) {
        if (!intersects(queryMin, queryMax)) {
//			System.out.println("Does NOT Overlap branch: " + this);
            return
        }
        //		System.out.println("Overlaps branch: " + this);
        node1?.query(queryMin, queryMax, visitor)
        node2?.query(queryMin, queryMax, visitor)
    }

    init {
        buildExtent(node1, node2)
    }
}