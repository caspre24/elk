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
     * @param hyperNodes
     * @param portToHyperNodeMap
     * @param routingStrategy
     * @param sourceLayerIndex 
     */
    void mergeHypernodes(List<HyperNode> hyperNodes, Map<LPort, HyperNode> portToHyperNodeMap,
            IRoutingDirectionStrategy routingStrategy, int sourceLayerIndex);

}
