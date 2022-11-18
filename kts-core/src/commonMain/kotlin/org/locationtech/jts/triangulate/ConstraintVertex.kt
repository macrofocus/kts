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
package org.locationtech.jts.triangulate

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.triangulate.quadedge.Vertex

/**
 * A vertex in a Constrained Delaunay Triangulation.
 * The vertex may or may not lie on a constraint.
 * If it does it may carry extra information about the original constraint.
 *
 * @author Martin Davis
 */
class ConstraintVertex
/**
 * Creates a new constraint vertex
 *
 * @param p the location of the vertex
 */
    (p: Coordinate?) : Vertex(p) {
    /**
     * Tests whether this vertex lies on a constraint.
     *
     * @return true if the vertex lies on a constraint
     */
    /**
     * Sets whether this vertex lies on a constraint.
     *
     * @param isOnConstraint true if this vertex lies on a constraint
     */
    var isOnConstraint = false
    private var constraint: Any? = null

    /**
     * Sets the external constraint information
     *
     * @param constraint an object which carries information about the constraint this vertex lies on
     */
    fun setConstraint(constraint: Any?) {
        isOnConstraint = true
        this.constraint = constraint
    }

    /**
     * Gets the external constraint object
     *
     * @return the external constraint object
     */
    fun getConstraint(): Any? {
        return constraint
    }

    /**
     * Merges the constraint data in the vertex <tt>other</tt> into this vertex.
     * This method is called when an inserted vertex is
     * very close to an existing vertex in the triangulation.
     *
     * @param other the constraint vertex to merge
     */
    fun merge(other: ConstraintVertex?) {
        if (other!!.isOnConstraint) {
            isOnConstraint = true
            constraint = other.constraint
        }
    }
}