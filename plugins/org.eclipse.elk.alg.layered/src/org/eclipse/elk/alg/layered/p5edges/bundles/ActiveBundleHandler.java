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
import java.util.Collections;
import java.util.Comparator;
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
import org.eclipse.elk.alg.layered.networksimplex.NEdge;
import org.eclipse.elk.alg.layered.networksimplex.NGraph;
import org.eclipse.elk.alg.layered.networksimplex.NNode;
import org.eclipse.elk.alg.layered.networksimplex.NetworkSimplex;
import org.eclipse.elk.alg.layered.p5edges.HyperNodeUtils;
import org.eclipse.elk.alg.layered.p5edges.HyperNodeUtils.HyperNode;
import org.eclipse.elk.alg.layered.p5edges.OrthogonalRoutingGenerator;
import org.eclipse.elk.alg.layered.p5edges.OrthogonalRoutingGenerator.IRoutingDirectionStrategy;
import org.eclipse.elk.alg.layered.p5edges.bundles.scanline.ScanlineUtils;
import org.eclipse.elk.alg.layered.p5edges.bundles.shortestpath.AstarAlgorithm;
import org.eclipse.elk.alg.layered.p5edges.bundles.shortestpath.Edge;
import org.eclipse.elk.alg.layered.p5edges.bundles.shortestpath.Node;
import org.eclipse.elk.alg.layered.properties.InternalProperties;
import org.eclipse.elk.alg.layered.properties.LayeredOptions;
import org.eclipse.elk.alg.layered.properties.Spacings;
import org.eclipse.elk.core.comments.AbstractNormalizedHeuristic.NormalizationFunction;
import org.eclipse.elk.core.math.KVector;
import org.eclipse.elk.core.util.BasicProgressMonitor;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
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

    private final LGraph lGraph;
    private final Spacings spacings;
    private final IRoutingDirectionStrategy routingStrategy;
    private final Float bundleSpacing;

    private final Map<Layer, Multimap<Integer, LEdge>> bundlesPerLayer;
    private final Multimap<Integer, LEdge> firstEdgesPerBundle;
    private final Table<Integer, Layer, LEdge> shortestEdgePerBundlePerLayer;
    private final Multimap<Layer, HyperNode> hyperNodesPerLayer;
    private final Map<Layer, Map<LPort, HyperNode>> portToHypernodesPerLayer;
    private final Map<HyperNode, List<LEdge>> hyperNodeToBundle;
    
    
    private static final Comparator<LEdge> EDGE_BY_SRC_PORT_ID_COMPARATOR = new Comparator<LEdge>() {

        @Override
        public int compare(final LEdge e1, final LEdge e2) {
            return Integer.compare(getOriginEdge(e1).getSource().getIndex(), getOriginEdge(e2).getSource().getIndex());
        }
    };

    /**
     * @param layeredGraph
     */
    public ActiveBundleHandler(final LGraph lGraph, final IRoutingDirectionStrategy routingStrategy) {
        this.lGraph = lGraph;
        spacings = lGraph.getProperty(InternalProperties.SPACINGS);
        this.routingStrategy = routingStrategy;
        bundleSpacing = lGraph.getProperty(LayeredOptions.EDGE_BUNDLING_BUNDLE_SPACING);

        bundlesPerLayer = Maps.newHashMap();
        firstEdgesPerBundle = LinkedListMultimap.create();
        shortestEdgePerBundlePerLayer = HashBasedTable.create();
        hyperNodesPerLayer = LinkedListMultimap.create();
        portToHypernodesPerLayer = Maps.newHashMap();
        hyperNodeToBundle = Maps.newIdentityHashMap();
    }

    /**
     * @param e2
     * @return
     */
    private static LEdge getOriginEdge(final LEdge edge) {
        LEdge current = edge;
        LNode source = current.getSource().getNode();
        while (source.getType() != NodeType.NORMAL) {
            current = source.getIncomingEdges().iterator().next();
            source = current.getSource().getNode();
        }
        return current;
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
                        possibleBundles.put(target, edge);
                    }
                    for (Entry<LNode, Collection<LEdge>> bundle : possibleBundles.asMap().entrySet()) {
                        Collection<LEdge> edges = bundle.getValue();
                        if (edges.size() > 1) {
                            System.out.println(
                                    "  bundle found: " + node + " --> " + bundle.getKey() + " id: " + bundleId);
                            firstEdgesPerBundle.putAll(bundleId, edges);
                            putAllEdgesRecursive(bundleId++, edges);
                        }
                    }
                }
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.elk.alg.layered.p5edges.bundles.IBundleHandler#calcShortestEdges()
     */
    @Override
    public void calcShortestEdges() {
        for (Entry<Integer, Collection<LEdge>> bundle : firstEdgesPerBundle.asMap().entrySet()) {
            Integer bundleId = bundle.getKey();
            Collection<LEdge> edges = bundle.getValue();
            LEdge shortestEdge = findShortestEdge(edges, bundleId);
            shortestEdgePerBundlePerLayer.put(bundleId, shortestEdge.getSource().getNode().getLayer(), shortestEdge);
            while (shortestEdge.getTarget().getNode().getType() != NodeType.NORMAL) {
                shortestEdge = getSuccessorEdge(shortestEdge);
                shortestEdgePerBundlePerLayer.put(bundleId, shortestEdge.getSource().getNode().getLayer(),
                        shortestEdge);
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

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.elk.alg.layered.p5edges.bundles.IBundleHandler#mergeHypernodes()
     */
    @Override
    public void mergeHypernodes(final OrthogonalRoutingGenerator routingGenerator) {
        for (Layer layer : lGraph) {
            List<HyperNode> hyperNodes = Lists.newLinkedList(hyperNodesPerLayer.removeAll(layer));
            mergeSingleLayerHypernodes(hyperNodes, portToHypernodesPerLayer.get(layer), layer);
            HyperNodeUtils.clearDependencies(hyperNodes);
            HyperNodeUtils.createDependencies(hyperNodes, routingGenerator.getConflictThreshold());
            HyperNodeUtils.breakCycles(hyperNodes, lGraph.getProperty(InternalProperties.RANDOM));
            HyperNodeUtils.topologicalNumbering(hyperNodes);
            hyperNodesPerLayer.putAll(layer, hyperNodes);
        }
    }

    private void mergeSingleLayerHypernodes(final Collection<HyperNode> hyperNodes,
            final Map<LPort, HyperNode> portToHyperNodeMap, final Layer layer) {

        Multimap<Integer, LEdge> bundles = bundlesPerLayer.get(layer);
        if (bundles != null) {
            for (Integer bundleId : bundles.keySet()) {
                HyperNode bundleHyperNode =
                        portToHyperNodeMap.get(shortestEdgePerBundlePerLayer.get(bundleId, layer).getSource());
                Collection<LEdge> bundle = bundles.get(bundleId);
                bundleHyperNode.width = bundle.size() * bundleSpacing;
                List<LEdge> sortedBundle = Lists.newLinkedList(bundle);
                Collections.sort(sortedBundle, EDGE_BY_SRC_PORT_ID_COMPARATOR);
                hyperNodeToBundle.put(bundleHyperNode, sortedBundle);
                for (LEdge edge : bundle) {
                    HyperNode oldHyperNode = portToHyperNodeMap.get(edge.getSource());
                    if (oldHyperNode != bundleHyperNode) {
                        if (oldHyperNode != null) {
                            removeByIdentity(hyperNodes, oldHyperNode);
                            for (LPort port : oldHyperNode.ports) {
                                portToHyperNodeMap.remove(port);
                            }
                        }
                        bundleHyperNode.addBundledPortPositions(edge,
                                shortestEdgePerBundlePerLayer.get(bundleId, layer), portToHyperNodeMap,
                                routingStrategy);
                    }
                }
            }
        }
    }

    /**
     * @param hyperNodes
     * @param oldHyperNode
     */
    private void removeByIdentity(final Collection<HyperNode> hyperNodes, final HyperNode hyperNodeToRemove) {
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
     * 
     */
    @Override
    public void shiftHypernodes() {
        NGraph nGraph = buildNGraph();
        applyHypernodePositions(nGraph);
    }

    /**
     * @return
     * 
     */
    private NGraph buildNGraph() {
        NGraph nGraph = new NGraph();

        int counter = 0;
        // add a virtual root node
        NNode root = NNode.of().id(counter++).origin(null).create(nGraph);

        // transform nodes
        final Map<LNode, NNode> nodeMap = Maps.newHashMap();
        for (Layer layer : lGraph) {
            for (LNode lNode : layer) {
                if (lNode.getType() == NodeType.NORMAL) {
                    NNode nNode = NNode.of()
                            .id(counter++)
                            .origin(lNode)
                            .create(nGraph);
                    nodeMap.put(lNode, nNode);
                    // add auxiliary edges to keep LNodes in position.
                    NEdge.of()
                            .weight(1000)
                            .delta((int) lNode.getPosition().x)
                            .source(root)
                            .target(nNode)
                            .create();
                }
            }
        }
        for (Layer layer : lGraph) {
            for (HyperNode hNode : hyperNodesPerLayer.get(layer)) {
                hNode.x = layer.getPosition().x + layer.getSize().x + hNode.rank * spacings.edgeEdgeSpacing;
                NNode nNode = NNode.of()
                        .id(counter++)
                        .origin(hNode)
                        .create(nGraph);
                List<LEdge> bundle = hyperNodeToBundle.get(hNode);
                if (bundle != null) {
                    LNode sourceNode = bundle.get(0).getSource().getNode();
                    LNode targetNode = bundle.get(0).getTarget().getNode();
                    if (sourceNode.getType() == NodeType.NORMAL) {
                        NEdge.of()
                                .weight(1)
                                .delta((int) (sourceNode.getSize().x + sourceNode.getMargin().right))
                                .source(nodeMap.get(sourceNode))
                                .target(nNode)
                                .create();
                    } else if (targetNode.getType() == NodeType.NORMAL) {
                        NEdge.of()
                                .weight(1)
                                .delta((int) (hNode.width + targetNode.getMargin().left))
                                .source(nNode)
                                .target(nodeMap.get(targetNode))
                                .create();
                    }
                }
            }
        }

        // calculate dependencies
        new ScanlineUtils(spacings).sweep(nGraph.nodes);

        NetworkSimplex.forGraph(nGraph).execute(new BasicProgressMonitor());
//        nGraph.writeDebugGraph("/home/carsten/tmp/simplex.kgx");

        return nGraph;
    }

    /**
     * @param nGraph
     * 
     */
    private void applyHypernodePositions(final NGraph nGraph) {
        for (NNode nNode : nGraph.nodes) {
            if (nNode.origin instanceof HyperNode) {
                ((HyperNode) nNode.origin).x = nNode.layer;
            }
        }
        for (HyperNode hNode : hyperNodesPerLayer.values()) {
            routingStrategy.calculateBendPoints(hNode, 0, this);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.elk.alg.layered.p5edges.bundles.IBundleHandler#getHypernodePosition(org.eclipse.elk.alg.layered.
     * p5edges.HyperNodeUtils.HyperNode, double, double)
     */
    @Override
    public double getHypernodePosition(final HyperNode hyperNode, final double startPos, final double edgeSpacing) {
        return hyperNode.x;
    }
    
    private Map<HyperNode, Integer> offset = Maps.newIdentityHashMap();
    
    /**
     * @param edge
     * @param hNode
     * @param point
     * @param rightwardBend
     */
    @Override
    public boolean offsetBendpoint(final LEdge edge, final HyperNode hNode, final KVector point,
            final boolean rightwardBend, final boolean startOfSegment) {
        List<LEdge> sortedBundle = hyperNodeToBundle.get(hNode);
        if (sortedBundle != null) {
            int i = sortedBundle.indexOf(edge);
            // Integer i = offset.get(hNode) == null ? 0 : offset.get(hNode);
            double yOffset = (startOfSegment ? edge.getSource().getNode().getType()
                    : edge.getTarget().getNode().getType()) == NodeType.NORMAL ? 0 : i * bundleSpacing;
            if (rightwardBend) {
                point.add(hNode.width - i * bundleSpacing, yOffset);
            } else {
                point.add(i * bundleSpacing, yOffset);
            }
            i++;
            // offset.put(hNode, i);
        }
        return false;
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
        LPort bundledPort = port.getProperty(InternalProperties.BUNDLED_PORT);
        if (bundledPort != null) {
            return bundledPort.getAbsoluteAnchor();
        }
        return port.getAbsoluteAnchor();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.elk.alg.layered.p5edges.bundles.IBundleHandler#addHyperNodes(int, java.util.List)
     */
    @Override
    public void saveHyperNodes(final List<HyperNode> hyperNodes, final Map<LPort, HyperNode> portToHyperNodeMap,
            final int sourceLayerIndex) {
        if (sourceLayerIndex >= 0) {
            Layer layer = lGraph.getLayers().get(sourceLayerIndex);
            hyperNodesPerLayer.putAll(layer, hyperNodes);
            portToHypernodesPerLayer.put(layer, portToHyperNodeMap);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.elk.alg.layered.p5edges.bundles.IBundleHandler#calculateOriginalBendpoints(org.eclipse.elk.alg.
     * layered.p5edges.HyperNodeUtils.HyperNode, double,
     * org.eclipse.elk.alg.layered.p5edges.OrthogonalRoutingGenerator.IRoutingDirectionStrategy)
     */
    @Override
    public void calculateOriginalBendpoints(final HyperNode node, final double startPos) {
        // Do nothing, bundled bendpoints are calculated later.
    }

    /**
     * @param layer
     * @param bundleId
     * @param edge
     */
    private void putEdge(final Layer layer, final int bundleId, final LEdge edge) {
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
    private void putAllEdges(final Layer layer, final int bundleId, final Iterable<? extends LEdge> edges) {
        Multimap<Integer, LEdge> bundles = bundlesPerLayer.get(layer);
        if (bundles == null) {
            bundles = HashMultimap.create();
            bundlesPerLayer.put(layer, bundles);
        }
        bundles.putAll(bundleId, edges);
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
    private LEdge getSuccessorEdge(final LEdge edge) {
        return edge.getTarget().getNode().getOutgoingEdges().iterator().next();
    }

    /**
     * @param port
     * @return
     */
    private LPort getSuccessorTarget(final LPort port) {
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
