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
import java.util.Map.Entry;

import org.eclipse.elk.alg.layered.graph.LEdge;
import org.eclipse.elk.alg.layered.graph.LGraph;
import org.eclipse.elk.alg.layered.graph.LNode;
import org.eclipse.elk.alg.layered.graph.LNode.NodeType;
import org.eclipse.elk.alg.layered.graph.LPort;
import org.eclipse.elk.alg.layered.graph.Layer;
import org.eclipse.elk.alg.layered.p5edges.HyperNodeUtils.HyperNode;
import org.eclipse.elk.alg.layered.p5edges.OrthogonalRoutingGenerator.IRoutingDirectionStrategy;
import org.eclipse.elk.alg.layered.p5edges.bundles.shortestpath.AstarAlgorithm;
import org.eclipse.elk.alg.layered.p5edges.bundles.shortestpath.Edge;
import org.eclipse.elk.alg.layered.p5edges.bundles.shortestpath.Node;
import org.eclipse.elk.alg.layered.properties.InternalProperties;
import org.eclipse.elk.alg.layered.properties.LayeredOptions;
import org.eclipse.elk.core.math.KVector;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;

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
    private HashMap<Layer, Multimap<Integer, LEdge>> bundlesPerLayer;
    private Multimap<Integer, LEdge> firstEdgesPerBundle;
    private Table<Integer, Layer, LEdge> shortestEdges;
    private int maxBundleId = -1;

    /**
     * @param layeredGraph
     */
    public ActiveBundleHandler(final LGraph lGraph) {
        this.lGraph = lGraph;
        bundlesPerLayer = Maps.newHashMap();
        firstEdgesPerBundle = HashMultimap.create();
        shortestEdges = HashBasedTable.create();
    }

    /**
     * @param layer
     * @param bundleId
     * @param edge
     */
    public void putEdge(final Layer layer, final int bundleId, final LEdge edge) {
        Multimap<Integer, LEdge> bundles = bundlesPerLayer.get(layer);
        if (bundles == null) {
            bundles = HashMultimap.create();
            bundlesPerLayer.put(layer, bundles);
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
        Multimap<Integer, LEdge> bundles = bundlesPerLayer.get(layer);
        if (bundles == null) {
            bundles = HashMultimap.create();
            bundlesPerLayer.put(layer, bundles);
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
        Multimap<Integer, LEdge> bundles = bundlesPerLayer.get(layer);
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

    /* (non-Javadoc)
     * @see org.eclipse.elk.alg.layered.p5edges.bundles.IBundleHandler#calcShortestEdges()
     */
    @Override
    public void calcShortestEdges() {
        for (Entry<Integer, Collection<LEdge>> bundle : firstEdgesPerBundle.asMap().entrySet()) {
            Integer bundleId = bundle.getKey();
            Collection<LEdge> edges = bundle.getValue();
            LEdge shortestEdge = findShortestEdge(edges, bundleId);
            shortestEdges.put(bundleId, shortestEdge.getSource().getNode().getLayer(), shortestEdge);
            while (shortestEdge.getTarget().getNode().getType() != NodeType.NORMAL) {
                shortestEdge = getSuccessorEdge(shortestEdge);
                shortestEdges.put(bundleId, shortestEdge.getSource().getNode().getLayer(), shortestEdge);
            }
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
        Multimap<Integer, LEdge> bundles = bundlesPerLayer.get(layer);
        if (bundles != null) {
            for (Integer bundleId : bundles.keySet()) {
                HyperNode bundleHyperNode = portToHyperNodeMap.get(shortestEdges.get(bundleId, layer).getSource());
//                        retrieveHypernodeForBundle(bundles.get(bundleId), portToHyperNodeMap);
                for (LEdge edge : bundles.get(bundleId)) {
                    HyperNode oldHyperNode = portToHyperNodeMap.get(edge.getSource());
                    if (oldHyperNode != bundleHyperNode) {
                        if (oldHyperNode != null) {
                            removeByIdentity(hyperNodes, oldHyperNode);
                            for (LPort port : oldHyperNode.ports) {
                                portToHyperNodeMap.remove(port);
                            }
                        }
                        // bundleHyperNode.addPortPositions(edge.getSource(), portToHyperNodeMap, routingStrategy);
                        bundleHyperNode.addBundledPortPositions(edge, shortestEdges.get(bundleId, layer),
                                portToHyperNodeMap, routingStrategy);
                    }
                }
            }
        }
    }
    
    @Override
    public KVector getBundledAnchor(final LPort port) {
        LPort bundledPort = port.getProperty(InternalProperties.BUNDLED_PORT);
        if (bundledPort != null) {
            return bundledPort.getAbsoluteAnchor();
        }
        return port.getAbsoluteAnchor();
    }

    /**
     * @param hyperNodes
     * @param oldHyperNode
     */
    private void removeByIdentity(final List<HyperNode> hyperNodes, final HyperNode hyperNodeToRemove) {
        Iterator<HyperNode> iter = hyperNodes.iterator();
        while (iter.hasNext()) {
            HyperNode hyperNode = iter.next();
            if (hyperNode == hyperNodeToRemove) {
                iter.remove();
                return;
            }
        }
    }

    /**
     * Find shortest edge by building the following auxiliary graph.
     * 
     * <pre>
     *        +-------- 1-to-1 connections weighted by the total traveled y difference
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
    private LEdge findShortestEdge(final Collection<LEdge> bundle, final int bundleId) {

        int bundleSize = bundle.size();
        ArrayList<Node> sourcePortNodes = Lists.newArrayListWithCapacity(bundleSize);
        ArrayList<Node> sourceEdgeNodes = Lists.newArrayListWithCapacity(bundleSize);
        ArrayList<Node> targetEdgeNodes = Lists.newArrayListWithCapacity(bundleSize);
        ArrayList<Node> targetPortNodes = Lists.newArrayListWithCapacity(bundleSize);

        double startMinY = 0;
        double startMaxY = 0;
        double goalMinY = 0;
        double goalMaxY = 0;
        for (LEdge lEdge : bundle) {
            // start and goal stuff
            double sourceY = lEdge.getSource().getAbsoluteAnchor().y;
            double targetY = getRealTarget(lEdge).getAbsoluteAnchor().y;
            startMinY = Math.min(startMinY, sourceY);
            startMaxY = Math.max(startMaxY, sourceY);
            goalMinY = Math.min(goalMinY, targetY);
            goalMaxY = Math.max(goalMaxY, targetY);

            sourcePortNodes.add(new Node(X_SOURCE_PORTS, sourceY));
            targetPortNodes.add(new Node(X_TARGET_PORTS, targetY));
            Edge edge = generateWeightedCompanionEdge(lEdge);
            sourceEdgeNodes.add(edge.getSource());
            targetEdgeNodes.add(edge.getTarget());
        }

        Node startNode = new Node(X_START, (startMaxY - startMinY) / 2);
        Node goalNode = new Node(X_GOAL, (goalMaxY - goalMinY) / 2);
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
        DebugWriter.dump(startNode, "graph_" + bundleId);
        DebugWriter.dump(shortestPath, "path_ " + bundleId);
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
            lastY = curr.getAbsoluteAnchor().y;
            LPort next = getSuccessorTarget(curr);
            while (next.getNode().getType() != NodeType.NORMAL) {
                weight += Math.abs(curr.getAbsoluteAnchor().y - next.getAbsoluteAnchor().y);
                lastY = next.getAbsoluteAnchor().y;
                curr = next;
                next = getSuccessorTarget(curr);
            }
        }
        Node sourceNode = new Node(X_SOURCE_EDGES, firstY, edge);
        Node targetNode = new Node(X_TARGET_EDGES, lastY);
        return new Edge(sourceNode, targetNode, weight + (X_TARGET_EDGES - X_SOURCE_EDGES));
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
                        if (edge.getSource().getNode().getType() == NodeType.NORMAL) {
                            firstEdgesPerBundle.put(bundleId, edge);
                        }
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
                    for (Entry<LNode, Collection<LEdge>> bundle : possibleBundles.asMap().entrySet()) {
                        Collection<LEdge> edges = bundle.getValue();
                        if (edges.size() > 1) {
                            System.out.println("  bundle found: " + node + " --> " + bundle.getKey() + " id: " + bundleId);
                            firstEdgesPerBundle.putAll(bundleId, edges);
                            putAllEdgesRecursive(bundleId++, edges);
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
    private void putAllEdgesRecursive(final int bundleId, final Collection<LEdge> edges) {
        assert edges.size() > 0;
        LEdge edge = edges.iterator().next();
        putAllEdges(edge.getSource().getNode().getLayer(), bundleId, edges);
        if (edge.getTarget().getNode().getType() != NodeType.NORMAL) {
            LinkedList<LEdge> successorEdges = Lists.newLinkedList();
            for (LEdge e : edges) {
                Iterables.addAll(successorEdges, e.getTarget().getNode().getOutgoingEdges());
            }
            putAllEdgesRecursive(bundleId, successorEdges);
        }
    }

    private int retrieveBundleId(final LEdge edge) {
        LEdge curEdge = edge;
        while (curEdge.getSource().getNode().getType() != NodeType.NORMAL) {
            curEdge = (LEdge) curEdge.getProperty(InternalProperties.ORIGIN);
        }
        return curEdge.getProperty(LayeredOptions.EDGE_BUNDLING_BUNDLE_ID);
    }

    /**
     * @param edge
     * @return
     */
    private LEdge getSuccessorEdge(LEdge edge) {
        return edge.getTarget().getNode().getOutgoingEdges().iterator().next();
    }

    /**
     * @param port
     * @return
     */
    private LPort getSuccessorTarget(LPort port) {
        return port.getNode().getOutgoingEdges().iterator().next().getTarget();
    }

    /**
     * @param edge
     * @return
     */
    private LPort getRealTarget(final LEdge edge) {
        LPort target = edge.getTarget();
        if (target.getNode().getType() != NodeType.NORMAL) {
            LPort possibleTarget = target.getProperty(InternalProperties.LONG_EDGE_TARGET);
            if (possibleTarget != null) {
                target = possibleTarget;
            } else {
                while (target.getNode().getType() != NodeType.NORMAL) {
                    target = getSuccessorTarget(target);
                }
            }
        }
        return target;
    }

}
