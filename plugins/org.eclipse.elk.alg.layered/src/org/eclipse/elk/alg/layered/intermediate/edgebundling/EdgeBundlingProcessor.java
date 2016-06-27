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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.SortedSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.eclipse.elk.alg.layered.ILayoutProcessor;
import org.eclipse.elk.alg.layered.graph.LEdge;
import org.eclipse.elk.alg.layered.graph.LGraph;
import org.eclipse.elk.alg.layered.graph.LNode;
import org.eclipse.elk.alg.layered.graph.LPort;
import org.eclipse.elk.alg.layered.graph.Layer;
import org.eclipse.elk.alg.layered.intermediate.edgebundling.shortestpath.AstarAlgorithm;
import org.eclipse.elk.alg.layered.intermediate.edgebundling.shortestpath.Edge;
import org.eclipse.elk.alg.layered.intermediate.edgebundling.shortestpath.Node;
import org.eclipse.elk.alg.layered.properties.InternalProperties;
import org.eclipse.elk.alg.layered.properties.LayeredOptions;
import org.eclipse.elk.alg.layered.properties.Spacings;
import org.eclipse.elk.core.math.KVector;
import org.eclipse.elk.core.math.KVectorChain;
import org.eclipse.elk.core.util.IElkProgressMonitor;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.TreeMultimap;

/**
 * This class merges the series of big node dummy nodes introduced by either the or the back into the original node.
 * I.e., the original width is assigned to the first node of the series, all other dummies are dropped. Furthermore, the
 * EAST ports that were moved to the last dummy node, are moved back to the original node. Here, the x coordinate of the
 * moved ports have to be adapted properly.
 * 
 * <dl>
 * <dt>Precondition:</dt>
 * <dd>a graph with routed edges,</dd>
 * <dd>all dummy nodes are removed from the graph.</dd>
 * <dt>Postcondition:</dt>
 * <dd>edges are bundled with common bendpoints</dd>
 * <dt>Slots:</dt>
 * <dd>After phase 5.</dd>
 * <dt>Same-slot dependencies:</dt>
 * <dd>Before</dd>
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

    private Multimap<Integer, LEdge> edgeBundles;
    private TreeMultimap<LEdge, VerticalSegment> segmentsByEdge;
    private HashMap<Layer, TreeMultimap<Integer, VerticalSegment>> segmentsByLayerByRank;
    private HashMap<Layer, Integer> highestRankPerLayer;

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.elk.alg.layered.ILayoutProcessor#process (org.eclipse.elk.alg.layered.graph.LGraph,
     * org.eclipse.elk.core.util.IElkProgressMonitor)
     */
    @Override
    public void process(final LGraph lGraph, final IElkProgressMonitor progressMonitor) {

        edgeBundles = HashMultimap.create();
        // Sort the segments by layer (i.e. the order when traveling along the edge).
        segmentsByEdge = TreeMultimap.create(Ordering.arbitrary(), new Comparator<VerticalSegment>() {

            @Override
            public int compare(final VerticalSegment s1, final VerticalSegment s2) {
                return s1.getLayer() == s2.getLayer() ? s2.compareTo(s2)
                        : s2.getLayer().getIndex() - s1.getLayer().getIndex();
            }
        });
        segmentsByLayerByRank = Maps.newHashMap();
        highestRankPerLayer = Maps.newHashMap();

        System.out.println("grouping edges");
        switch (lGraph.getProperty(LayeredOptions.EDGE_BUNDLING_STRATEGY)) {
        case AUTOMATIC:
            groupAndSplitEdgesAutomatically(lGraph);
            break;
        case MANUAL:
            groupAndSplitEdgesManually(lGraph);
            break;
        case NONE:
        default:
            return;
        }

        // divide edges into vertical segments
        for (Layer layer : lGraph) {
            for (LNode node : layer) {
                for (LEdge edge : node.getOutgoingEdges()) {
                    for (VerticalSegment segment : edge.getProperty(InternalProperties.SEGMENTS)) {
                        addToSlot(edge, segment);
                    }
                }
            }
        }
        // Save number of ranks per layer.
        for (Layer layer : lGraph) {
            TreeMultimap<Integer, VerticalSegment> segmentsByRank = segmentsByLayerByRank.get(layer);
            if (segmentsByRank != null) {
                highestRankPerLayer.put(layer, segmentsByRank.keySet().last());
            } else {
                highestRankPerLayer.put(layer, -1);
            }
        }

        System.out.println("merging edges");
        bundleEdges(lGraph);

        System.out.println("edges merged");

        // dispose
        edgeBundles = null;
        segmentsByEdge = null;
        segmentsByLayerByRank = null;
        highestRankPerLayer = null;
    }

    /**
     * @param layeredGraph
     */
    private void bundleEdges(final LGraph layeredGraph) {
        Spacings spacings = layeredGraph.getProperty(InternalProperties.SPACINGS);
        Style style = layeredGraph.getProperty(LayeredOptions.EDGE_BUNDLING_STYLE);
        double offset = 0;
        switch (style) {
        case BUNDLE:
            offset = (double) layeredGraph.getProperty(LayeredOptions.EDGE_BUNDLING_BUNDLE_SPACING);
            break;
        case SINGLE_LINE:
            break;
        }

        for (Collection<LEdge> bundle : edgeBundles.asMap().values()) {

            // TODO add these information to the bundle
            LEdge exampleEdge = bundle.iterator().next();
            Layer sourceLayer = exampleEdge.getSource().getNode().getLayer();
            Layer targetLayer = exampleEdge.getTarget().getNode().getLayer();
            Layer preTargetLayer = targetLayer.getGraph().getLayers().get(targetLayer.getIndex() - 1);

            LEdge shortestEdge = findShortestEdge(bundle, sourceLayer, preTargetLayer, targetLayer);

            KVectorChain commonBendPoints = new KVectorChain();
            List<VerticalSegment> commonSegments = Lists.newLinkedList();
            double mergeY = shortestEdge.getSource().getAbsoluteAnchor().y;
            double splitY = shortestEdge.getTarget().getAbsoluteAnchor().y;
            for (VerticalSegment segment : segmentsByEdge.get(shortestEdge)) {
                if (segment.getLayer() == sourceLayer) {
                    mergeY = segment.getEnd();
                } else if (segment.getLayer() == preTargetLayer) {
                    splitY = segment.getStart();
                } else {
                    double x = calcX(spacings, segment.getLayer(), segment.getRank());
                    commonBendPoints.add(x, segment.getStart());
                    commonBendPoints.add(x, segment.getEnd());
                    commonSegments.add(segment);
                }
            }
            commonBendPoints.addFirst(0, mergeY);
            commonBendPoints.add(0, splitY);

            System.out.println("bundle " + exampleEdge + " split");
            int mergeRank = findFreeSlot(sourceLayer, bundle, mergeY, false);
            System.out.println("bundle " + exampleEdge + " merge");
            int splitRank = findFreeSlot(preTargetLayer, bundle, splitY, true);

            if (mergeRank != -1 && splitRank != -1) {

                double mergeX = calcX(spacings, sourceLayer, mergeRank);
                commonBendPoints.getFirst().x = mergeX;
                double splitX = calcX(spacings, preTargetLayer, splitRank);
                commonBendPoints.getLast().x = splitX;

                // Set the new bendpoints for each edge and add some port specific ones to reach the "merge" point.
                double currOffset = 0;
                for (LEdge e : bundle) {
                    KVectorChain bendPoints = e.getBendPoints();
                    bendPoints.clear();
                    bendPoints.addAllAsCopies(0, commonBendPoints);
                    bendPoints.offset(currOffset, currOffset);
                    double startY = e.getSource().getAbsoluteAnchor().y;
                    bendPoints.addFirst(new KVector(mergeX, startY));
                    double endY = e.getTarget().getAbsoluteAnchor().y;
                    bendPoints.add(new KVector(splitX, endY));

                    // Update Segments
                    SortedSet<VerticalSegment> segments = segmentsByEdge.removeAll(e);
                    // Remove the old segments.
                    for (VerticalSegment segment : segments) {
                        segmentsByLayerByRank.get(segment.getLayer()).get(segment.getRank()).remove(segment);
                    }
                    List<VerticalSegment> newSegments = Lists.newLinkedList();
                    // Create new segments
                    newSegments.add(new VerticalSegment(sourceLayer, mergeRank, e, startY, mergeY));
                    for (VerticalSegment segment : commonSegments) {
                        newSegments.add(new VerticalSegment(segment, e));
                    }
                    newSegments.add(new VerticalSegment(preTargetLayer, splitRank, e, splitY, endY));
                    // Insert the new segments to the maps.
                    segmentsByEdge.putAll(e, newSegments);
                    for (VerticalSegment segment : newSegments) {
                        segmentsByLayerByRank.get(segment.getLayer()).put(segment.getRank(), segment);
                    }

                    currOffset += offset;
                }
            } else {
                System.out.println("no free slot");
            }
        }
    }

    /**
     * @param spacings
     * @param layer
     * @param rank
     * @return
     */
    private double calcX(final Spacings spacings, final Layer layer, final int rank) {
        return layer.getPosition().x + layer.getSize().x + spacings.edgeNodeSpacing + spacings.edgeEdgeSpacing * rank;
    }

    /**
     * @param layer
     * @param bundle
     * @return
     */
    private int findFreeSlot(final Layer layer, final Collection<LEdge> bundle, final double mergeY,
            final boolean reverseSearch) {

        // build merge segment
        VerticalSegment mergeSegment = new VerticalSegment(mergeY, mergeY);
        // mergeSegment.setStart(mergeY);
        // mergeSegment.setEnd(mergeY);
        for (LEdge edge : bundle) {
            for (VerticalSegment segment : segmentsByEdge.get(edge)) {
                if (segment.getLayer() == layer) {
                    mergeSegment.include(segment);
                    LPort port = reverseSearch ? edge.getTarget() : edge.getSource();
                    mergeSegment.include(port.getAbsoluteAnchor().y);
                    break;
                }
            }
        }

        // find a free slot
        TreeMultimap<Integer, VerticalSegment> slotsInThisLayer = segmentsByLayerByRank.get(layer);
        List<Integer> ranks =
                IntStream.range(0, highestRankPerLayer.get(layer) + 1).boxed().collect(Collectors.toList());
        if (reverseSearch) {
            Collections.reverse(ranks);
        }
        System.out.println(ranks);
        for (Integer rank : ranks) {
            boolean free = true;
            for (VerticalSegment segment : slotsInThisLayer.get(rank)) {
                if ((!bundle.contains(segment.getEdge())) && mergeSegment.overlaps(segment)) {
                    free = false;
                    break;
                }
            }
            if (free) {
                System.out.println("rank " + rank);
                return rank;
            }
        }
        return -1;
    }

    private static final int X_START = 0;
    private static final int X_SOURCE_PORTS = 20;
    private static final int X_SOURCE_EDGES = 40;
    private static final int X_TARGET_EDGES = 60;
    private static final int X_TARGET_PORTS = 80;
    private static final int X_GOAL = 100;

    /**
     * Find shortest edge by building the following aux graph:
     * 
     * <pre>
     *   -o--o---o--o-
     *  /  X      X   \
     * o--o--o---o--o--o
     *  \  X      X   /
     *   -o--o---o--o-
     *                 |
     *                 -- goal node
     * 
     * 
     * </pre>
     * 
     * @param bundle
     * @param targetLayer
     * @param spacings
     * @param offset
     * @return
     */
    private LEdge findShortestEdge(final Collection<LEdge> bundle, final Layer sourceLayer, final Layer preTargetLayer,
            final Layer targetLayer) {

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
            KVector targetAnchor = lEdge.getTarget().getAbsoluteAnchor();
            startHeight += sourceAnchor.y;
            goalHeight += targetAnchor.y;

            sourcePortNodes.add(new Node(X_SOURCE_PORTS, sourceAnchor.y));
            targetPortNodes.add(new Node(X_TARGET_PORTS, targetAnchor.y));
            Edge edge = generateWeightedCompanionEdge(lEdge, sourceLayer, preTargetLayer);
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
    private Edge generateWeightedCompanionEdge(final LEdge edge, final Layer sourceLayer, final Layer preTargetLayer) {
        double weight = 0;
        double firstY = edge.getSource().getAbsoluteAnchor().y;
        double lastY = edge.getTarget().getAbsoluteAnchor().y;
        for (VerticalSegment segment : segmentsByEdge.get(edge)) {
            if (segment.getLayer() == sourceLayer) {
                firstY = segment.getEnd();
            } else if (segment.getLayer() == preTargetLayer) {
                lastY = segment.getStart();
            } else {
                weight += Math.abs(segment.getEnd() - segment.getStart());
            }
        }
        Node sourceNode = new Node(X_SOURCE_EDGES, firstY, edge);
        Node targetNode = new Node(X_TARGET_EDGES, lastY);
        return new Edge(sourceNode, targetNode, weight + (X_TARGET_EDGES - X_SOURCE_EDGES));
    }

    /**
     * Iterate through all edges and sort them into bundles according to their bundle id.
     * 
     * @param lGraph
     */
    private void groupAndSplitEdgesManually(final LGraph lGraph) {
        for (Layer layer : lGraph) {
            for (LNode node : layer.getNodes()) {
                for (LEdge edge : node.getOutgoingEdges()) {
                    // Sort marked edges into bundles.
                    Integer bundleId = edge.getProperty(LayeredOptions.EDGE_BUNDLING_BUNDLE_ID);
                    if (bundleId != null) {
                        System.out.println("found edge " + edge + " for bundle " + bundleId);
                        edgeBundles.put(bundleId, edge);
                    }
                }
            }
        }
    }

    /**
     * @param lGraph
     */
    private void groupAndSplitEdgesAutomatically(final LGraph lGraph) {
        int bundleId = 0;
        for (Layer layer : lGraph) {
            for (LNode node : layer.getNodes()) {
                HashMultimap<LNode, LEdge> possibleBundles = HashMultimap.create();
                for (LEdge edge : node.getOutgoingEdges()) {
                    possibleBundles.put(edge.getTarget().getNode(), edge);
                }
                for (Collection<LEdge> edges : possibleBundles.asMap().values()) {
                    if (edges.size() > 1) {
                        edgeBundles.putAll(bundleId++, edges);
                    }
                }
            }
        }
    }

    /**
     * @param edge
     * @param x
     * @param y1
     * @param y2
     */
    private void addToSlot(final LEdge edge, final VerticalSegment segment) {
        segmentsByEdge.put(edge, segment);
        TreeMultimap<Integer, VerticalSegment> slot = segmentsByLayerByRank.get(segment.getLayer());
        if (slot == null) {
            slot = TreeMultimap.create();
            segmentsByLayerByRank.put(segment.getLayer(), slot);
        }
        slot.put(segment.getRank(), segment);
    }

}
