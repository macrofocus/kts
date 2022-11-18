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
 * Contains classes that perform a topological overlay to compute boolean spatial functions.
 * <P>
 * The Overlay Algorithm is used in spatial analysis methods for computing set-theoretic
 * operations (boolean combinations) of input [org.locationtech.jts.geom.Geometry]s. The algorithm for
 * computing the overlay uses the intersection operations supported by topology graphs.
 * To compute an overlay it is necessary to explicitly compute the resultant graph formed
 * by the computed intersections.
</P> * <P>
 * The algorithm to compute a set-theoretic spatial analysis method has the following steps:
</P> * <UL>
 * <LI>Build topology graphs of the two input geometries.  For each geometry all
 * self-intersection nodes are computed and added to the graph.
</LI> * <LI>Compute nodes for all intersections between edges and nodes of the graphs.
</LI> * <LI>Compute the labeling for the computed nodes by merging the labels from the input graphs.
</LI> * <LI>Compute new edges between the compute intersection nodes.  Label the edges appropriately.
</LI> * <LI>Build the resultant graph from the new nodes and edges.
</LI> * <LI>Compute the labeling for isolated components of the graph.  Add the
 * isolated components to the resultant graph.
</LI> * <LI>Compute the result of the boolean combination by selecting the node and edges
 * with the appropriate labels. Polygonize areas and sew linear geometries together.
</LI></UL> *
 * <h2>Package Specification</h2>
 *
 *  * Java Topology Suite Technical Specifications
 *  * <A HREF="http://www.opengis.org/techno/specs.htm">
 * OpenGIS Simple Features Specification for SQL</A>
 *
 */
package org.locationtech.jts.operation.overlay

