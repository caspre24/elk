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
package org.eclipse.elk.alg.layered.intermediate.edgebundling;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map.Entry;

import org.eclipse.elk.alg.layered.ILayoutProcessor;
import org.eclipse.elk.alg.layered.graph.LEdge;
import org.eclipse.elk.alg.layered.graph.LGraph;
import org.eclipse.elk.alg.layered.graph.LNode;
import org.eclipse.elk.alg.layered.graph.Layer;
import org.eclipse.elk.alg.layered.properties.InternalProperties;
import org.eclipse.elk.alg.layered.properties.LayeredOptions;
import org.eclipse.elk.alg.layered.properties.Spacings;
import org.eclipse.elk.core.math.KVector;
import org.eclipse.elk.core.math.KVectorChain;
import org.eclipse.elk.core.util.IElkProgressMonitor;

/**
 * This class merges the series of big node dummy nodes introduced by either the
 *  or the  back into the original node. 
 * I.e., the original width is assigned to the first node of the series, all other dummies 
 * are dropped. Furthermore, the EAST ports that were moved to the last dummy node, are moved 
 * back to the original node. Here, the x coordinate of the moved ports have to be adapted properly.
 * 
 * <dl>
 *   <dt>Precondition:</dt>
 *     <dd>a graph with routed edges.</dd>
 *   <dt>Postcondition:</dt>
 *     <dd>all big node dummy nodes are removed from the graph.</dd>
 *   <dt>Slots:</dt>
 *     <dd>After phase 5.</dd>
 *   <dt>Same-slot dependencies:</dt>
 *     <dd>Before </dd>
 * </dl>
 * 
 * @author csp
 */
public class EdgeBundlingProcessor implements ILayoutProcessor {
    
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

    /* (non-Javadoc)
     * @see org.eclipse.elk.alg.layered.ILayoutProcessor#process
     * (org.eclipse.elk.alg.layered.graph.LGraph, org.eclipse.elk.core.util.IElkProgressMonitor)
     */
    @Override
    public void process(final LGraph layeredGraph, final IElkProgressMonitor progressMonitor) {
        
        System.out.println("grouping edges");
        HashMap<Integer, LinkedList<LEdge>> edgeBundles = new HashMap<Integer, LinkedList<LEdge>>();
        switch (layeredGraph.getProperty(LayeredOptions.EDGE_BUNDLING_STRATEGY)) {
        case AUTOMATIC:
            System.out.println("edgebundling strategy AUTOMATIC currently not supported");
            return;
        case MANUAL:
            // Iterate through all edges and sort them into bundles according to their bundle id.
            for (Layer layer : layeredGraph) {
                for (LNode node : layer.getNodes()) {
                    for (LEdge edge : node.getOutgoingEdges()) {
                        Integer bundleId = edge.getProperty(LayeredOptions.EDGE_BUNDLING_BUNDLE_ID);
                        if (bundleId != null) {
                            System.out.println("found edge " + edge + " for bundle " + bundleId);
                            LinkedList<LEdge> bundle = edgeBundles.get(bundleId);
                            if (bundle == null) {
                                bundle = new LinkedList<LEdge>();
                                edgeBundles.put(bundleId, bundle);
                            }
                            bundle.add(edge);
                        }
                    }
                }
            }
            break;
        case NONE:
        default:
            return;
        }
        
        System.out.println("merging edges");
        Spacings spacings = layeredGraph.getProperty(InternalProperties.SPACINGS);
        
        for (Entry<Integer, LinkedList<LEdge>> group : edgeBundles.entrySet()) {
            // Use the first edge as reference edge whose bendpoints to use.
            LEdge refEdge = group.getValue().getFirst();
            
            Style style = layeredGraph.getProperty(LayeredOptions.EDGE_BUNDLING_STYLE);
            double offset = 0;
            switch (style) {
            case BUNDLE:
                offset = (double) layeredGraph.getProperty(LayeredOptions.EDGE_BUNDLING_BUNDLE_SPACING);
                break;
            case SINGLE_LINE:
            }
            
            // Calculate first (most left) and last (most right) x coordinates where bendpoints can be placed.
            double firstX = 0;
            double lastX = 0;
            LNode srcNode = refEdge.getSource().getNode();
            firstX =
                    srcNode.getPosition().x
                    + srcNode.getSize().x
                    + srcNode.getMargin().right
                    + spacings.edgeNodeSpacing;
            LNode targetNode = refEdge.getTarget().getNode();
            lastX =
                    targetNode.getPosition().x
                    - targetNode.getMargin().left
                    - spacings.edgeNodeSpacing
                    - group.getValue().size() * offset;
            
            // Make sure the reference edge has at least two bendpoints.
            if (refEdge.getBendPoints().isEmpty()) {
                double y = refEdge.getSource().getAbsoluteAnchor().y;
                refEdge.getBendPoints().add(new KVector(firstX, y));
                refEdge.getBendPoints().add(new KVector(lastX, y));
            }
            
            // Make sure there's only one bendpoint with x less or equal to firstX (resp. greater or equal to lastX)
            // to get rid of near-node bendpoints. These will be added later for each port independently.
            KVectorChain commonBendPoints = new KVectorChain();
            commonBendPoints.addAllAsCopies(0, refEdge.getBendPoints());
            Iterator<KVector> commonBpIter = commonBendPoints.iterator();
            KVector bp = commonBpIter.next();
            while (commonBpIter.hasNext() && bp.x <= firstX) {
                commonBpIter.remove();
                bp = commonBpIter.next();
            }
            commonBendPoints.addFirst(new KVector(firstX, bp.y));
            
            commonBpIter = commonBendPoints.descendingIterator();
            bp = commonBpIter.next();
            while (commonBpIter.hasNext() && bp.x >= lastX) {
                commonBpIter.remove();
                bp = commonBpIter.next();
            }
            commonBendPoints.add(new KVector(lastX, bp.y));
            
            // Set the new bendpoints for each edge and add some port specific ones to reach the "merge" point.
            Iterator<LEdge> iterator = group.getValue().iterator();
            double currOffset = 0;
            while (iterator.hasNext()) {
                LEdge e = iterator.next();
                KVectorChain bendPoints = e.getBendPoints();
                bendPoints.clear();
                bendPoints.addAllAsCopies(0, commonBendPoints);
                bendPoints.offset(currOffset, currOffset);
                bendPoints.addFirst(new KVector(firstX + currOffset, e.getSource().getAbsoluteAnchor().y));
                bendPoints.add(new KVector(lastX + currOffset, e.getTarget().getAbsoluteAnchor().y));
                currOffset += offset;
            }
        }
        System.out.println("edges merged");
    }

}
