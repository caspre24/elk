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
import org.eclipse.elk.alg.layered.p5edges.OrthogonalRoutingGenerator.IRoutingDirectionStrategy;
import org.eclipse.elk.core.math.KVector;

/**
 * This bundle handler does nothing. It's lazy attitude comes into play when no bundles are requested.
 */
public class LazyBundleHandler implements IBundleHandler {

    private final IRoutingDirectionStrategy routingStrategy;

    /**
     * @param routingStrategy
     */
    public LazyBundleHandler(final IRoutingDirectionStrategy routingStrategy) {
        this.routingStrategy = routingStrategy;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.elk.alg.layered.p5edges.bundles.IBundleHandler#findBundles()
     */
    @Override
    public void findBundles() {
        // Nothing to do.
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.elk.alg.layered.p5edges.bundles.IBundleHandler#calcShortestEdges()
     */
    @Override
    public void calcShortestEdges() {
        // Nothing to do.
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.eclipse.elk.alg.layered.p5edges.bundles.IBundleHandler#getBundledAnchor(org.eclipse.elk.alg.layered.graph.
     * LPort)
     */
    @Override
    public KVector getBundledAnchor(final LPort port) {
        return port.getAbsoluteAnchor();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.elk.alg.layered.p5edges.bundles.IBundleHandler#saveHyperNodes(java.util.List, java.util.Map,
     * int)
     */
    @Override
    public void saveHyperNodes(final List<HyperNode> hyperNodes, final Map<LPort, HyperNode> portToHyperNodeMap,
            final int sourceLayerIndex) {
        // Nothing to do.

    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.elk.alg.layered.p5edges.bundles.IBundleHandler#calculateOriginalBendpoints(org.eclipse.elk.alg.
     * layered.p5edges.HyperNodeUtils.HyperNode, double)
     */
    @Override
    public void calculateOriginalBendpoints(final HyperNode node, final double startPos) {
        routingStrategy.calculateBendPoints(node, startPos, this);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.eclipse.elk.alg.layered.p5edges.bundles.IBundleHandler#mergeHypernodes(org.eclipse.elk.alg.layered.p5edges.
     * OrthogonalRoutingGenerator)
     */
    @Override
    public void mergeHypernodes(final OrthogonalRoutingGenerator routingGenerator) {
        // Nothing to do.
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.elk.alg.layered.p5edges.bundles.IBundleHandler#shiftHypernodes()
     */
    @Override
    public void shiftHypernodes() {
        // Nothing to do.
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.elk.alg.layered.p5edges.bundles.IBundleHandler#getHypernodePosition(org.eclipse.elk.alg.layered.
     * p5edges.HyperNodeUtils.HyperNode, double, double)
     */
    @Override
    public double getHypernodePosition(final HyperNode hyperNode, final double startPos, final double edgeSpacing) {
        return startPos + hyperNode.rank * edgeSpacing;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.eclipse.elk.alg.layered.p5edges.bundles.IBundleHandler#offsetBendpoint(org.eclipse.elk.alg.layered.graph.
     * LEdge, org.eclipse.elk.alg.layered.p5edges.HyperNodeUtils.HyperNode, org.eclipse.elk.core.math.KVector, boolean,
     * boolean)
     */
    @Override
    public boolean offsetBendpoint(final LEdge edge, final HyperNode hNode, final KVector point,
            final boolean rightwardBend, final boolean startOfSegment) {
        
        return true;
    }

}
