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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.eclipse.elk.alg.layered.graph.LEdge;
import org.eclipse.elk.alg.layered.graph.LGraph;
import org.eclipse.elk.alg.layered.graph.LNode;
import org.eclipse.elk.alg.layered.graph.LNode.NodeType;
import org.eclipse.elk.alg.layered.graph.LPort;
import org.eclipse.elk.alg.layered.graph.Layer;
import org.eclipse.elk.alg.layered.intermediate.edgebundling.DebugWriter;
import org.eclipse.elk.alg.layered.intermediate.edgebundling.shortestpath.AstarAlgorithm;
import org.eclipse.elk.alg.layered.intermediate.edgebundling.shortestpath.Edge;
import org.eclipse.elk.alg.layered.intermediate.edgebundling.shortestpath.Node;
import org.eclipse.elk.alg.layered.p5edges.HyperNodeUtils.HyperNode;
import org.eclipse.elk.alg.layered.p5edges.OrthogonalRoutingGenerator.IRoutingDirectionStrategy;
import org.eclipse.elk.alg.layered.properties.InternalProperties;
import org.eclipse.elk.alg.layered.properties.LayeredOptions;
import org.eclipse.elk.core.math.KVector;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

/**
 * @author Kiel University
 *
 */
public class ActiveBundleHandler implements IBundleHandler {

    private static final int X_START = 0;
    private static final int X_SOURCE_PORTS = 20;
    private static final int X_SOURCE_EDGES = 40;
    private static final int X_TARGET_EDGES = 60;
    private static final int X_TARGET_PORTS = 80;
    private static final int X_GOAL = 100;

    private LGraph lGraph;
    private HashMap<Layer, Multimap<Integer, LEdge>> edgeBundles;
    private HashMap<Layer, ArrayList<LEdge>> shortestEdges;
    private int maxBundleId = -1;

    /**
     * @param layeredGraph
     */
    public ActiveBundleHandler(final LGraph lGraph) {
        this.lGraph = lGraph;
        edgeBundles = Maps.newHashMap();
        shortestEdges = Maps.newHashMap();
    }

    /**
     * @param layer
     * @param bundleId
     * @param edge
     */
    public void putEdge(final Layer layer, final int bundleId, final LEdge edge) {
        Multimap<Integer, LEdge> bundles = edgeBundles.get(layer);
        if (bundles == null) {
            bundles = HashMultimap.create();
            edgeBundles.put(layer, bundles);
        }
        bundles.put(bundleId, edge);
    }

    /**
     * @param layer
     * @param bundleId
     * @param edges
     * @param edge
     */
    public void putAllEdges(final Layer layer, final int bundleId, final Iterable<? extends LEdge> edges) {
        Multimap<Integer, LEdge> bundles = edgeBundles.get(layer);
        if (bundles == null) {
            bundles = HashMultimap.create();
            edgeBundles.put(layer, bundles);
        }
        bundles.putAll(bundleId, edges);
    }

    /**
     * @param layer
     * @param bundleId
     * @return
     * @return
     */
    public Collection<LEdge> getEdges(final Layer layer, final int bundleId) {
        Multimap<Integer, LEdge> bundles = edgeBundles.get(layer);
        if (bundles != null) {
            return bundles.get(bundleId);
        }
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.elk.alg.layered.p5edges.bundles.IBundleHandler#findBundles()
     */
    @Override
    public void findBundles() {
        System.out.println("grouping edges");
        switch (lGraph.getProperty(LayeredOptions.EDGE_BUNDLING_STRATEGY)) {
        case AUTOMATIC:
            groupAndSplitEdgesAutomatically();
            break;
        case MANUAL:
            groupAndSplitEdgesManually();
            break;
        case NONE:
        default:
            return;
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.elk.alg.layered.p5edges.bundles.IBundleHandler#mergeHypernodes(java.util.List)
     */
    @Override
    public void mergeHypernodes(final List<HyperNode> hyperNodes, final Map<LPort, HyperNode> portToHyperNodeMap,
            final IRoutingDirectionStrategy routingStrategy, final int sourceLayerIndex) {
        if (sourceLayerIndex < 0) {
            return;
        }
        Layer layer = lGraph.getLayers().get(sourceLayerIndex);
        Multimap<Integer, LEdge> bundles = edgeBundles.get(layer);
        if (bundles != null) {
            for (Integer bundleId : bundles.keySet()) {
                HyperNode bundleHyperNode = retrieveHypernodeForBundle(getEdges(layer, bundleId), portToHyperNodeMap);
                for (LEdge edge : getEdges(layer, bundleId)) {
                    HyperNode oldHyperNode = portToHyperNodeMap.get(edge.getSource());
                    if (oldHyperNode != bundleHyperNode) {
                        hyperNodes.remove(oldHyperNode);
                        for (LPort port : oldHyperNode.ports) {
                            portToHyperNodeMap.remove(port);
                        }
                        bundleHyperNode.addPortPositions(edge.getSource(), portToHyperNodeMap, routingStrategy);
                    }
                }
            }
        }
    }

    public void calcShortestEdges() {
        for (Layer layer : lGraph) {
            Multimap<Integer, LEdge> bundles = edgeBundles.get(layer);
            if (bundles != null) {
                
            }
        }
        for (int id = 0; id < maxBundleId + 1; id++) {
            
        }
    }

    /**
     * Find shortest edge by building the following auxiliary graph:
     * 
     * <pre>
     *        +-------- 1-to-1 connections weight by the total traveled y difference
     *     +--|--+----- bicliques (fully connected bigraph)
     *  +--|--|--|--+-- 1-to-n connections
     *  |  |  |  |  |
     *   -o-o---o-o-
     *  /  X     X  \
     * o--o-o---o-o--o
     *  \  X     X  /
     *   -o-o---o-o-
     * |  | |   | |  |
     * |  | |   | |  +-- goal
     * |  | |   | +----- target ports
     * |  | |   +------- end y pos of reused edge path
     * |  | +----------- start y pos of reused edge path
     * |  +------------- source ports
     * +---------------- start
     * </pre>
     * 
     * @param bundle
     * @param targetLayer
     * @param spacings
     * @param offset
     * @return
     */
    private LEdge findShortestEdge(final Collection<LEdge> bundle) {

        int bundleSize = bundle.size();
        ArrayList<Node> sourcePortNodes = Lists.newArrayListWithCapacity(bundleSize);
        ArrayList<Node> sourceEdgeNodes = Lists.newArrayListWithCapacity(bundleSize);
        ArrayList<Node> targetEdgeNodes = Lists.newArrayListWithCapacity(bundleSize);
        ArrayList<Node> targetPortNodes = Lists.newArrayListWithCapacity(bundleSize);

        double startHeight = 0;
        double goalHeight = 0;
        for (LEdge lEdge : bundle) {
            // start and goal stuff
            KVector sourceAnchor = lEdge.getSource().getAbsoluteAnchor();
            KVector targetAnchor = getRealTarget(lEdge).getAbsoluteAnchor();
            startHeight += sourceAnchor.y;
            goalHeight += targetAnchor.y;

            sourcePortNodes.add(new Node(X_SOURCE_PORTS, sourceAnchor.y));
            targetPortNodes.add(new Node(X_TARGET_PORTS, targetAnchor.y));
            Edge edge = generateWeightedCompanionEdge(lEdge);
            sourceEdgeNodes.add(edge.getSource());
            targetEdgeNodes.add(edge.getTarget());
        }

        Node startNode = new Node(X_START, startHeight / bundleSize);
        Node goalNode = new Node(X_GOAL, goalHeight / bundleSize);
        for (int i = 0; i < bundleSize; i++) {
            // Create edges from start to all source-port-nodes and
            // from all target-port-nodes to goal.
            new Edge(startNode, sourcePortNodes.get(i), 0);
            new Edge(targetPortNodes.get(i), goalNode, 0);
            // Create bicliques between port- and edge-nodes for both source and target side.
            for (int j = 0; j < bundleSize; j++) {
                new Edge(sourcePortNodes.get(i), sourceEdgeNodes.get(j));
                new Edge(targetEdgeNodes.get(i), targetPortNodes.get(j));
            }
        }
        List<Node> shortestPath = AstarAlgorithm.findShortestPath(startNode, goalNode);
        DebugWriter.dump(startNode, "graph");
        DebugWriter.dump(shortestPath, "path");
        return (LEdge) shortestPath.get(2).getOrigin();
    }

    /**
     * @param edge
     * @param sourceLayer
     * @param preTargetLayer
     * @return
     */
    private Edge generateWeightedCompanionEdge(final LEdge edge) {
        double weight = 0;
        double firstY = edge.getSource().getAbsoluteAnchor().y;
        double lastY = getRealTarget(edge).getAbsoluteAnchor().y;
        LPort curr = edge.getTarget();
        if (curr.getNode().getType() != NodeType.NORMAL) {
            firstY = curr.getAbsoluteAnchor().y;
            LPort next = curr.getNode().getOutgoingEdges().iterator().next().getTarget();
            while (next.getNode().getType() != NodeType.NORMAL) {
                weight += Math.abs(curr.getAbsoluteAnchor().y - next.getAbsoluteAnchor().y);
                lastY = next.getAbsoluteAnchor().y;
                curr = next;
                next = curr.getNode().getOutgoingEdges().iterator().next().getTarget();
            }
        }
        Node sourceNode = new Node(X_SOURCE_EDGES, firstY, edge);
        Node targetNode = new Node(X_TARGET_EDGES, lastY);
        return new Edge(sourceNode, targetNode, weight + (X_TARGET_EDGES - X_SOURCE_EDGES));
    }

    private LPort getRealTarget(final LEdge edge) {
        LPort target = edge.getTarget();
        if (target.getNode().getType() != NodeType.NORMAL) {
            target = target.getProperty(InternalProperties.LONG_EDGE_TARGET);
        }
        return target;
    }

    /**
     * @param edges
     * @param portToHyperNodeMap
     * @return
     */
    private HyperNode retrieveHypernodeForBundle(final Collection<LEdge> edges,
            final Map<LPort, HyperNode> portToHyperNodeMap) {

        HyperNode hyperNode = null;
        Iterator<LEdge> iterEdges = edges.iterator();
        while (iterEdges.hasNext()) {
            LEdge edge = iterEdges.next();
            hyperNode = portToHyperNodeMap.get(edge.getSource());
            if (hyperNode == null) {
                hyperNode = portToHyperNodeMap.get(edge.getTarget());
            }
            if (hyperNode != null) {
                return hyperNode;
            }
        }
        throw new IllegalStateException("No hypernode found for any bundled ports.\nShould never end up here!");
    }

    /**
     * Iterate through all edges and sort them into bundles according to their bundle id.
     * 
     * @param lGraph
     */
    private void groupAndSplitEdgesManually() {
        for (Layer layer : lGraph) {
            for (LNode node : layer.getNodes()) {
                for (LEdge edge : node.getOutgoingEdges()) {
                    // Sort marked edges into bundles.
                    Integer bundleId = retrieveBundleId(edge);
                    if (bundleId != null) {
                        System.out.println("found edge " + edge + " for bundle " + bundleId);
                        putEdge(layer, bundleId, edge);
                        maxBundleId = Math.max(maxBundleId, bundleId);
                    }
                }
            }
        }
    }

    /**
     * @param lGraph
     */
    private void groupAndSplitEdgesAutomatically() {
        int bundleId = 0;
        ListIterator<Layer> iterLayers = lGraph.getLayers().listIterator();
        while (iterLayers.hasNext()) {
            Layer layer = iterLayers.next();
            for (LNode node : layer.getNodes()) {
                if (node.getType() == NodeType.NORMAL) {
                    HashMultimap<LNode, LEdge> possibleBundles = HashMultimap.create();
                    for (LEdge edge : node.getOutgoingEdges()) {
                        LNode target = getRealTarget(edge).getNode();
                        //TODO is this necessary?
//                        if (target.getType() != NodeType.NORMAL) {
//                            continue;
//                        }
                        possibleBundles.put(target, edge);
                    }
                    for (Collection<LEdge> edges : possibleBundles.asMap().values()) {
                        if (edges.size() > 1) {
                            putAllEdgesRecursive(lGraph.getLayers().listIterator(iterLayers.previousIndex()),
                                    bundleId++, edges);
                            // putAllEdges(layer, bundleId++, edges);
                        }
                    }
                }
            }
        }
        maxBundleId = bundleId - 1;
    }

    /**
     * @param listIterator
     * @param bundleId
     * @param edges
     */
    private void putAllEdgesRecursive(final ListIterator<Layer> iterLayers, final int bundleId,
            final Collection<LEdge> edges) {
        if (iterLayers.hasNext()) {
            putAllEdges(iterLayers.next(), bundleId, edges);
            LinkedList<LEdge> successorEdges = Lists.newLinkedList();
            for (LEdge edge : edges) {
                Iterables.addAll(successorEdges, edge.getTarget().getNode().getOutgoingEdges());
            }
            putAllEdgesRecursive(iterLayers, bundleId, successorEdges);
        }
    }

    private int retrieveBundleId(final LEdge edge) {
        LEdge curEdge = edge;
        while (curEdge.getSource().getNode().getType() != NodeType.NORMAL) {
            curEdge = (LEdge) curEdge.getProperty(InternalProperties.ORIGIN);
        }
        return curEdge.getProperty(LayeredOptions.EDGE_BUNDLING_BUNDLE_ID);
    }

}
