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

import org.eclipse.elk.alg.layered.graph.LGraph;
import org.eclipse.elk.alg.layered.intermediate.edgebundling.EdgeBundlingProcessor.Strategy;
import org.eclipse.elk.alg.layered.properties.LayeredOptions;

/**
 * @author Kiel University
 *
 */
public final class BundleHandling {
    
    private BundleHandling() { }
    
    /**
     * @param lGraph
     * @return
     */
    public static IBundleHandler createHandler(final LGraph lGraph) {
        if (lGraph.getProperty(LayeredOptions.EDGE_BUNDLING_STRATEGY) == Strategy.NONE) {
            return new LazyBundleHandler();
        } else {
            return new ActiveBundleHandler(lGraph);
        }
    }
}
