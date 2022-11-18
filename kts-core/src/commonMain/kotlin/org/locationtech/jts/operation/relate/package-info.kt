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
/**
 * Contains classes to implement the computation of the spatial relationships of <CODE>Geometry</CODE>s.
 * <P>
 * The `relate` algorithm computes the `IntersectionMatrix` describing the
 * relationship of two `Geometry`s.  The algorithm for computing `relate`
 * uses the intersection operations supported by topology graphs.  Although the `relate`
 * result depends on the resultant graph formed by the computed intersections, there is
 * no need to explicitly compute the entire graph.
 * It is sufficient to compute the local structure of the graph
 * at each intersection node.
</P> * <P>
 * The algorithm to compute `relate` has the following steps:
</P> * <UL>
 * <LI>Build topology graphs of the two input geometries. For each geometry
 * all self-intersection nodes are computed and added to the graph.
</LI> * <LI>Compute nodes for all intersections between edges and nodes of the graphs.
</LI> * <LI>Compute the labeling for the computed nodes by merging the labels from the input graphs.
</LI> * <LI>Compute the labeling for isolated components of the graph (see below)
</LI> * <LI>Compute the `IntersectionMatrix` from the labels on the nodes and edges.
</LI></UL> *
 * <H3>Labeling isolated components</H3>
 * Isolated components are components (edges or nodes) of an input `Geometry` which
 * do not contain any intersections with the other input `Geometry`.  The
 * topological relationship of these components to the other input `Geometry`
 * must be computed in order to determine the complete labeling of the component.  This can
 * be done by testing whether the component lies in the interior or exterior of the other
 * `Geometry`.  If the other `Geometry` is 1-dimensional, the isolated
 * component must lie in the exterior (since otherwise it would have an intersection with an
 * edge of the `Geometry`).  If the other `Geometry` is 2-dimensional,
 * a Point-In-Polygon test can be used to determine whether the isolated component is in the
 * interior or exterior.
 * <h2>Package Specification</h2>
 *
 *  * Java Topology Suite Technical Specifications
 *  * <A HREF="http://www.opengis.org/techno/specs.htm">
 * OpenGIS Simple Features Specification for SQL</A>
 *
 */
package org.locationtech.jts.operation.relate

