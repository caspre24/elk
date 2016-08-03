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

import org.eclipse.elk.alg.layered.graph.LPort;
import org.eclipse.elk.alg.layered.p5edges.HyperNodeUtils.HyperNode;
import org.eclipse.elk.alg.layered.p5edges.OrthogonalRoutingGenerator.IRoutingDirectionStrategy;
import org.eclipse.elk.core.math.KVector;

/**
 * This bundle handler does nothing. It's lazy attitude comes into play when no bundles are requested.
 */
public class LazyBundleHandler implements IBundleHandler {

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.elk.alg.layered.p5edges.bundles.IBundleHandler#findBundles()
     */
    @Override
    public void findBundles() {
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.elk.alg.layered.p5edges.bundles.IBundleHandler#mergeHypernodes(java.util.List, java.util.Map,
     * org.eclipse.elk.alg.layered.p5edges.OrthogonalRoutingGenerator.IRoutingDirectionStrategy, int)
     */
    @Override
    public void mergeHypernodes(final List<HyperNode> hyperNodes, final Map<LPort, HyperNode> portToHyperNodeMap,
            final IRoutingDirectionStrategy routingStrategy, final int sourceLayerIndex) {
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.elk.alg.layered.p5edges.bundles.IBundleHandler#calcShortestEdges()
     */
    @Override
    public void calcShortestEdges() {
    }

    /* (non-Javadoc)
     * @see org.eclipse.elk.alg.layered.p5edges.bundles.IBundleHandler#getBundledAnchor(org.eclipse.elk.alg.layered.graph.LPort)
     */
    @Override
    public KVector getBundledAnchor(LPort port) {
        return port.getAbsoluteAnchor();
    }

}
