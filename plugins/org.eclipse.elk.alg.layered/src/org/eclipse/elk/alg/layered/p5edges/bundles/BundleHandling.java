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
import org.eclipse.elk.alg.layered.p5edges.OrthogonalRoutingGenerator.IRoutingDirectionStrategy;
import org.eclipse.elk.alg.layered.properties.LayeredOptions;

/**
 * @author Kiel University
 *
 */
public final class BundleHandling {
    
    /**
     * The strategy to find edge bundles.
     */
    public enum Strategy {
        /** Don't bundle edges. */
        NONE,
        /** Assign the bundle id manually. */
        MANUAL,
        /** Determine edge bundles automatically. */
        AUTOMATIC;
    }

    /**
     * The style of bundled edges.
     */
    public enum Style {
        /** Bundle edges into a single line. */
        SINGLE_LINE,
        /** Draw bundled edges close together. */
        BUNDLE;
    }

    private BundleHandling() { }
    
    /**
     * @param lGraph
     * @return
     */
    public static IBundleHandler createHandler(final LGraph lGraph, final IRoutingDirectionStrategy routingStrategy) {
        if (lGraph.getProperty(LayeredOptions.EDGE_BUNDLING_STRATEGY) == BundleHandling.Strategy.NONE) {
            return new LazyBundleHandler(routingStrategy);
        } else {
            return new ActiveBundleHandler(lGraph, routingStrategy);
        }
    }
}
