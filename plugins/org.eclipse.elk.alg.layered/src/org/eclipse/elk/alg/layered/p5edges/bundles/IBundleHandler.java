/*******************************************************************************
 * Copyright (c) 2016 Kiel University and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Kiel University - initial API and implementation
 *******************************************************************************/
package org.eclipse.elk.alg.layered.p5edges.bundles;

import java.util.List;
import java.util.Map;

import org.eclipse.elk.alg.layered.graph.LEdge;
import org.eclipse.elk.alg.layered.graph.LPort;
import org.eclipse.elk.alg.layered.p5edges.HyperNodeUtils.HyperNode;
import org.eclipse.elk.alg.layered.p5edges.OrthogonalRoutingGenerator;
import org.eclipse.elk.core.math.KVector;

/**
 * @author Kiel University
 *
 */
/**
 * @author Kiel University
 *
 */
public interface IBundleHandler {

    /**
     * 
     */
    void findBundles();

    /**
     * 
     */
    void calcShortestEdges();

    /**
     * @param port
     * @return
     */
    KVector getBundledAnchor(final LPort port);

    /**
     * @param hyperNodes
     * @param portToHyperNodeMap
     * @param sourceLayerIndex
     */
    void saveHyperNodes(final List<HyperNode> hyperNodes, final Map<LPort, HyperNode> portToHyperNodeMap,
            final int sourceLayerIndex);

    /**
     * @param node
     * @param startPos
     * @param routingStrategy
     */
    void calculateOriginalBendpoints(final HyperNode node, final double startPos);

    /**
     * @param routingGenerator
     */
    void mergeHypernodes(final OrthogonalRoutingGenerator routingGenerator);

    /**
     * 
     */
    void shiftHypernodes();

    /**
     * @param hyperNode
     * @param startPos
     * @param edgeSpacing
     * @return
     */
    double getHypernodePosition(final HyperNode hyperNode, final double startPos, final double edgeSpacing);

    /**
     * @param edge
     * @param hNode
     * @param point
     * @param rightwardBend
     * @param startOfSegment
     * @return 
     */
    boolean offsetBendpoint(final LEdge edge, final HyperNode hNode, final KVector point, final boolean rightwardBend,
            final boolean startOfSegment);

}
