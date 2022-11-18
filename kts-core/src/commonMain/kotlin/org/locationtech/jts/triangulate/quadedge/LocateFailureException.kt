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
package org.locationtech.jts.triangulate.quadedge

import org.locationtech.jts.geom.LineSegment

class LocateFailureException : RuntimeException {
    var segment: LineSegment? = null
        private set

    constructor(msg: String?) : super(msg) {}
    constructor(msg: String, seg: LineSegment?) : super(msgWithSpatial(msg, seg)) {
        segment = LineSegment(seg!!)
    }

    constructor(seg: LineSegment) : super(
        "Locate failed to converge (at edge: "
                + seg
                + ").  Possible causes include invalid Subdivision topology or very close sites"
    ) {
        segment = LineSegment(seg)
    }

    companion object {
        private fun msgWithSpatial(msg: String, seg: LineSegment?): String {
            return if (seg != null) "$msg [ $seg ]" else msg
        }
    }
}