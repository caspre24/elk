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
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableSet;

import org.eclipse.elk.alg.layered.ILayoutProcessor;
import org.eclipse.elk.alg.layered.graph.LEdge;
import org.eclipse.elk.alg.layered.graph.LGraph;
import org.eclipse.elk.alg.layered.graph.LNode;
import org.eclipse.elk.alg.layered.graph.Layer;
import org.eclipse.elk.alg.layered.intermediate.edgebundling.shortestpath.AstarAlgorithm;
import org.eclipse.elk.alg.layered.intermediate.edgebundling.shortestpath.Edge;
import org.eclipse.elk.alg.layered.intermediate.edgebundling.shortestpath.Graph;
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
 * <dd>a graph with routed edges.</dd>
 * <dt>Postcondition:</dt>
 * <dd>all big node dummy nodes are removed from the graph.</dd>
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
    private HashMap<Layer, TreeMultimap<Integer, VerticalSegment>> segmentsBySlot;

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.elk.alg.layered.ILayoutProcessor#process (org.eclipse.elk.alg.layered.graph.LGraph,
     * org.eclipse.elk.core.util.IElkProgressMonitor)
     */
    @Override
    public void process(final LGraph layeredGraph, final IElkProgressMonitor progressMonitor) {

        edgeBundles = HashMultimap.create();
        // Sort the values by layer (i.e. by  when traveling along the edge).
        segmentsByEdge = TreeMultimap.create(Ordering.arbitrary(), new Comparator<VerticalSegment>() {

            @Override
            public int compare(final VerticalSegment s1, final VerticalSegment s2) {
                return s1.layer == s2.layer ? s2.compareTo(s2) : s2.layer.getIndex() - s1.layer.getIndex();
            }
        });
        segmentsBySlot = Maps.newHashMap();

        System.out.println("grouping edges");
        switch (layeredGraph.getProperty(LayeredOptions.EDGE_BUNDLING_STRATEGY)) {
        case AUTOMATIC:
            System.out.println("edgebundling strategy AUTOMATIC currently not supported");
            return;
        case MANUAL:
            groupAndSplitEdges(layeredGraph);
            break;
        case NONE:
        default:
            return;
        }

        System.out.println("merging edges");
        bundleEdges(layeredGraph);

        System.out.println("edges merged");

        // dispose
        edgeBundles = null;
        segmentsByEdge = null;
        segmentsBySlot = null;
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

            KVectorChain commonBendPoints = findShortestEdge(bundle, sourceLayer, preTargetLayer, targetLayer);

            System.out.println("bundle " + exampleEdge + " split");
            int mergeRank = findFreeSlot(sourceLayer, bundle, false);
            System.out.println("bundle " + exampleEdge + " merge");
            int splitRank = findFreeSlot(preTargetLayer, bundle, true);

            if (mergeRank != -1 && splitRank != -1) {

                double mergeX = sourceLayer.getPosition().x + sourceLayer.getSize().x + spacings.edgeNodeSpacing
                        + spacings.edgeEdgeSpacing * mergeRank;
                double splitX =
                        targetLayer.getPosition().x - spacings.edgeNodeSpacing - spacings.edgeEdgeSpacing * splitRank;

                // Set the new bendpoints for each edge and add some port specific ones to reach the "merge" point.
                Iterator<LEdge> iterator = bundle.iterator();
                double currOffset = 0;
                while (iterator.hasNext()) {
                    LEdge e = iterator.next();
                    KVectorChain bendPoints = e.getBendPoints();
                    bendPoints.clear();
                    bendPoints.addAllAsCopies(0, commonBendPoints);
                    bendPoints.offset(currOffset, currOffset);
                    bendPoints.addFirst(new KVector(mergeX, e.getSource().getAbsoluteAnchor().y));
                    bendPoints.add(new KVector(splitX, e.getTarget().getAbsoluteAnchor().y));
                    currOffset += offset;
                }
            } else {
                System.out.println("no free slot");
            }
        }
    }

    /**
     * @param layer
     * @param bundle
     * @return
     */
    private int findFreeSlot(final Layer layer, final Collection<LEdge> bundle, final boolean reverseSlots) {
        // build merge segment
        VerticalSegment mergeSegment = new VerticalSegment();
        for (LEdge lEdge : bundle) {
            for (VerticalSegment segment : segmentsByEdge.get(lEdge)) {
                if (segment.layer == layer) {
                    mergeSegment.yStart = Math.min(mergeSegment.yStart, segment.yStart);
                    mergeSegment.yEnd = Math.max(mergeSegment.yEnd, segment.yEnd);
                    break;
                }
            }
        }
        // find a free slot
        TreeMultimap<Integer, VerticalSegment> slotsInThisLayer = segmentsBySlot.get(layer);
        NavigableSet<Integer> ranks = slotsInThisLayer.keySet();
        if (reverseSlots) {
            ranks = ranks.descendingSet();
        }
        for (Integer rank : ranks) {
            boolean free = true;
            for (VerticalSegment segment : slotsInThisLayer.get(rank)) {
                if ((!bundle.contains(segment.edge)) && mergeSegment.overlaps(segment)) {
                    free = false;
                    break;
                }
            }
            if (free) {
                System.out.println("rank " + rank);
                return reverseSlots ? ranks.size() - 1 - rank : rank;
            }
        }
        return -1;
    }

    /**
     * Find shortest edge by build the following aux graph:
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
     * @param bundle
     * @param targetLayer 
     * @param spacings
     * @param offset
     * @return
     */
    private KVectorChain findShortestEdge(final Collection<LEdge> bundle, final Layer sourceLayer,
            final Layer preTargetLayer, final Layer targetLayer) {

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

            sourcePortNodes.add(new Node(Graph.X_SOURCE_PORTS, sourceAnchor.y));
            targetPortNodes.add(new Node(Graph.X_TARGET_PORTS, targetAnchor.y));
            Edge edge = generateWeightedCompanionEdge(lEdge, sourceLayer, preTargetLayer);
            sourceEdgeNodes.add(edge.getSource());
            targetEdgeNodes.add(edge.getTarget());
        }
        
        Graph graph = new Graph();
        Node startNode = new Node(Graph.X_START, startHeight / bundleSize);
        graph.addNode(startNode);
        Node goalNode = new Node(Graph.X_GOAL, goalHeight / bundleSize);
        graph.addNode(goalNode);
        for (int i = 0; i < bundleSize; i++) {
            // Add the nodes to the graph.
            graph.addNode(sourcePortNodes.get(i));
            graph.addNode(sourceEdgeNodes.get(i));
            graph.addNode(targetEdgeNodes.get(i));
            graph.addNode(targetPortNodes.get(i));
            // Create edges from start to all source-port-nodes and 
            // from all target-port-nodes to goal.
            new Edge(startNode, sourcePortNodes.get(i));
            new Edge(targetPortNodes.get(i), goalNode);
            // Create bicliques between port- and edge-nodes for both source and target side.
            for (int j = 0; j < bundleSize; j++) {
                new Edge(sourcePortNodes.get(i), sourceEdgeNodes.get(j));
                new Edge(targetEdgeNodes.get(i), targetPortNodes.get(j));
            }
        }
        List<Node> shortestPath = AstarAlgorithm.findShortestPath(graph, startNode, goalNode);
        return null;
    }

    /**
     * @param lEdge
     * @param sourceLayer
     * @param preTargetLayer
     * @return
     */
    private Edge generateWeightedCompanionEdge(final LEdge lEdge, final Layer sourceLayer, final Layer preTargetLayer) {
        double weight = 0;
        //TODO find ys
        double firstY = Double.NaN;
        double lastY = Double.NaN;
        Iterable<VerticalSegment> segments = segmentsByEdge.get(lEdge);
        for (VerticalSegment segment : segments) {
            if (segment.layer != sourceLayer && segment.layer != preTargetLayer) {
                weight += segment.yEnd - segment.yStart;
            }
        }
        Node sourceNode = new Node(Graph.X_SOURCE_EDGES, firstY, lEdge);
        Node targetNode = new Node(Graph.X_TARGET_EDGES, lastY);
        return new Edge(sourceNode, targetNode, weight);
    }

    /**
     * Iterate through all edges and sort them into bundles according to their bundle id.
     * 
     * @param layeredGraph
     * @param edgeBundles
     * @return
     */
    private void groupAndSplitEdges(final LGraph layeredGraph) {
        for (Layer layer : layeredGraph) {
            for (LNode node : layer.getNodes()) {
                for (LEdge edge : node.getOutgoingEdges()) {
                    // Sort marked edges into bundles.
                    Integer bundleId = edge.getProperty(LayeredOptions.EDGE_BUNDLING_BUNDLE_ID);
                    if (bundleId != null) {
                        System.out.println("found edge " + edge + " for bundle " + bundleId);
                        edgeBundles.put(bundleId, edge);
                    }

                    // divide edge into vertical segments
                    Iterator<VerticalSegment> iterSegments = edge.getProperty(InternalProperties.SEGMENTS).iterator();
                    while (iterSegments.hasNext()) {
                        addToSlot(edge, iterSegments.next());
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
        TreeMultimap<Integer, VerticalSegment> slot = segmentsBySlot.get(segment.layer);
        if (slot == null) {
            slot = TreeMultimap.create();
            segmentsBySlot.put(segment.layer, slot);
        }
        slot.put(segment.rank, segment);
    }

}
