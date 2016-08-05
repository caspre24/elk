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
package org.eclipse.elk.alg.layered.p5edges;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.eclipse.elk.alg.layered.graph.LEdge;
import org.eclipse.elk.alg.layered.graph.LNode;
import org.eclipse.elk.alg.layered.graph.LPort;
import org.eclipse.elk.alg.layered.p5edges.OrthogonalRoutingGenerator.IRoutingDirectionStrategy;
import org.eclipse.elk.alg.layered.properties.InternalProperties;
import org.eclipse.elk.alg.layered.properties.PortType;
import org.eclipse.elk.core.options.PortSide;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * @author carsten
 *
 */
public final class HyperNodeUtils {

    private HyperNodeUtils() {
    }

    ///////////////////////////////////////////////////////////////////////////////
    // Hyper Node Graph Structures

    /**
     * A hypernode used for routing a hyperedge.
     */
    public static class HyperNode implements Comparable<HyperNode> {
        /** ports represented by this hypernode. */
        public List<LPort> ports = Lists.newArrayList();
        /** mark value used for cycle breaking. */
        private int mark;
        /** the rank determines the horizontal distance to the preceding layer. */
        public int rank;
        /** the x position. */
        public double x;
        /** the width of the associated bundle. */
        public double width;
        /** vertical starting position of this hypernode. */
        public double start = Double.NaN;
        /** vertical ending position of this hypernode. */
        public double end = Double.NaN;
        /** positions of line segments going to the preceding layer. */
        public LinkedList<Double> sourcePosis = Lists.newLinkedList();
        /** positions of line segments going to the next layer. */
        public LinkedList<Double> targetPosis = Lists.newLinkedList();
        /** list of outgoing dependencies. */
        public List<Dependency> outgoing = Lists.newArrayList();
        /** sum of the weights of outgoing dependencies. */
        private int outweight;
        /** list of incoming dependencies. */
        public List<Dependency> incoming = Lists.newArrayList();
        /** sum of the weights of incoming depencencies. */
        private int inweight;

        /**
         * Adds the positions of the given port and all connected ports.
         * 
         * @param port
         *            a port
         * @param hyperNodeMap
         *            map of ports to existing hypernodes
         * @param routingStrategy
         */
        public void addPortPositions(final LPort port, final Map<LPort, HyperNode> hyperNodeMap,
                final IRoutingDirectionStrategy routingStrategy) {

            hyperNodeMap.put(port, this);
            ports.add(port);
            double pos = routingStrategy.getPortPositionOnHyperNode(port);

            addPosition(pos, port.getSide() == routingStrategy.getSourcePortSide());

            // add connected ports
            for (LPort otherPort : port.getConnectedPorts()) {
                if (!hyperNodeMap.containsKey(otherPort)) {
                    addPortPositions(otherPort, hyperNodeMap, routingStrategy);
                }
            }
        }

        /**
         * @param edgeToAdd
         * @param shortestEdge
         * @param hyperNodeMap
         * @param routingStrategy
         */
        public void addBundledPortPositions(final LEdge edgeToAdd, final LEdge shortestEdge,
                final Map<LPort, HyperNode> hyperNodeMap, final IRoutingDirectionStrategy routingStrategy) {

            LPort origSource = edgeToAdd.getSource();
            LPort origTarget = edgeToAdd.getTarget();
            LPort shortSource = shortestEdge.getSource();
            LPort shortTarget = shortestEdge.getTarget();
            hyperNodeMap.put(origSource, this);
            hyperNodeMap.put(origTarget, this);
            ports.add(origSource);
            ports.add(origTarget);

            double sourcePos;
            double targetPos;
            if (origSource.getNode() == shortSource.getNode() && origTarget.getNode() == shortTarget.getNode()) {
                sourcePos = routingStrategy.getPortPositionOnHyperNode(origSource);
                targetPos = routingStrategy.getPortPositionOnHyperNode(origTarget);
            } else if (origSource.getNode() == shortSource.getNode()) {
                sourcePos = routingStrategy.getPortPositionOnHyperNode(origSource);
                targetPos = routingStrategy.getPortPositionOnHyperNode(shortTarget);
                origTarget.setProperty(InternalProperties.BUNDLED_PORT, shortTarget);
            } else if (origTarget.getNode() == shortTarget.getNode()) {
                sourcePos = routingStrategy.getPortPositionOnHyperNode(shortSource);
                targetPos = routingStrategy.getPortPositionOnHyperNode(origTarget);
                origSource.setProperty(InternalProperties.BUNDLED_PORT, shortSource);
            } else {
                sourcePos = routingStrategy.getPortPositionOnHyperNode(shortSource);
                targetPos = routingStrategy.getPortPositionOnHyperNode(shortTarget);
                origTarget.setProperty(InternalProperties.BUNDLED_PORT, shortTarget);
                origSource.setProperty(InternalProperties.BUNDLED_PORT, shortSource);
            }
            addPosition(sourcePos, true);
            addPosition(targetPos, false);
        }
        
        /**
         * @param pos
         * @param sourceSide
         */
        private void addPosition(final double pos, final boolean sourceSide) {

            // set new start position
            if (Double.isNaN(start)) {
                start = pos;
            } else {
                start = Math.min(start, pos);
            }

            // set new end position
            if (Double.isNaN(end)) {
                end = pos;
            } else {
                end = Math.max(end, pos);
            }

            // add the new port position to the respective list
            if (sourceSide) {
                insertSorted(sourcePosis, pos);
            } else {
                insertSorted(targetPosis, pos);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder("{");
            Iterator<LPort> portIter = ports.iterator();
            while (portIter.hasNext()) {
                LPort port = portIter.next();
                String name = port.getNode() + "|" + port;
                // if (name == null) {
                // name = "n" + port.getNode().getIndex();
                // }
                builder.append(name);
                if (portIter.hasNext()) {
                    builder.append(',');
                }
            }
            builder.append('}');
            return builder.toString();
        }

        /**
         * {@inheritDoc}
         */
        public int compareTo(final HyperNode other) {
            return this.mark - other.mark;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(final Object object) {
            if (object instanceof HyperNode) {
                HyperNode other = (HyperNode) object;
                return this.mark == other.mark;
            }
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return mark;
        }

        /**
         * Return the outgoing dependencies.
         * 
         * @return the outgoing dependencies
         */
        public List<Dependency> getOutgoing() {
            return outgoing;
        }

    }

    ///////////////////////////////////////////////////////////////////////////////
    // Hyper Node Graph Structures

    /**
     * A dependency between two hypernodes.
     */
    public static final class Dependency {

        /** the source hypernode of this dependency. */
        private HyperNode source;
        /** the target hypernode of this dependency. */
        private HyperNode target;
        /** the weight of this dependency. */
        private int weight;

        /**
         * Creates a dependency from the given source to the given target.
         * 
         * @param thesource
         *            the dependency source
         * @param thetarget
         *            the dependency target
         * @param theweight
         *            weight of the dependency
         */
        private Dependency(final HyperNode thesource, final HyperNode thetarget, final int theweight) {

            this.target = thetarget;
            this.source = thesource;
            this.weight = theweight;
            source.outgoing.add(this);
            target.incoming.add(this);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return source + "->" + target;
        }

        /**
         * Return the source node.
         * 
         * @return the source
         */
        public HyperNode getSource() {
            return source;
        }

        /**
         * Return the target node.
         * 
         * @return the target
         */
        public HyperNode getTarget() {
            return target;
        }

        /**
         * Returns the weight of the hypernode dependency.
         * 
         * @return the weight
         */
        public int getWeight() {
            return weight;
        }

    }

    /** weight penalty for conflicts of horizontal line segments. */
    private static final int CONFLICT_PENALTY = 16;

    ///////////////////////////////////////////////////////////////////////////////
    // Hyper Node Graph Creation

    /**
     * Creates hypernodes for the given layer.
     * 
     * @param nodes
     *            the layer. May be {@code null}, in which case nothing happens.
     * @param portSide
     *            side of the output ports for whose outgoing edges hypernodes should be created.
     * @param hyperNodes
     *            list the created hypernodes should be added to.
     * @param portToHyperNodeMap
     *            map from ports to hypernodes that should be filled.
     * @param layerIndex
     */
    public static void createHyperNodes(final Iterable<LNode> nodes, final PortSide portSide,
            final List<HyperNode> hyperNodes, final Map<LPort, HyperNode> portToHyperNodeMap,
            final IRoutingDirectionStrategy routingStrategy) {

        if (nodes != null) {
            for (LNode node : nodes) {
                for (LPort port : node.getPorts(PortType.OUTPUT, portSide)) {
                    HyperNode hyperNode = portToHyperNodeMap.get(port);
                    if (hyperNode == null) {
                        hyperNode = new HyperNode();
                        hyperNodes.add(hyperNode);
                        hyperNode.addPortPositions(port, portToHyperNodeMap, routingStrategy);
                    }
                }
            }
        }
    }

    /**
     * Create a dependencies between each of the given hypernodes, if one is needed.
     * 
     * @param hyperNodes
     *            the hypernodes to create dependecies between
     * @param minDiff
     *            the minimal difference between horizontal line segments to avoid a conflict
     */
    public static void createDependencies(final List<HyperNode> hyperNodes, final double minDiff) {
        ListIterator<HyperNode> iter1 = hyperNodes.listIterator();
        while (iter1.hasNext()) {
            HyperNode hyperNode1 = iter1.next();
            ListIterator<HyperNode> iter2 = hyperNodes.listIterator(iter1.nextIndex());
            while (iter2.hasNext()) {
                HyperNode hyperNode2 = iter2.next();
                createDependency(hyperNode1, hyperNode2, minDiff);
            }
        }
    }

    /**
     * Clear all existing dependencies to make the hyper node graph reusable.
     * 
     * @param hyperNodes
     *            the hypernodes
     */
    public static void clearDependencies(final List<HyperNode> hyperNodes) {
        for (HyperNode node : hyperNodes) {
            node.incoming.clear();
            node.outgoing.clear();
            node.inweight = 0;
            node.outweight = 0;
            node.mark = 0;
            node.rank = 0;
        }
    }

    /**
     * Create a dependency between the two given hypernodes, if one is needed.
     * 
     * @param hn1
     *            first hypernode
     * @param hn2
     *            second hypernode
     * @param minDiff
     *            the minimal difference between horizontal line segments to avoid a conflict
     */
    private static void createDependency(final HyperNode hn1, final HyperNode hn2, final double minDiff) {

        // check if at least one of the two nodes is just a straight line; those don't
        // create dependencies since they don't take up a slot
        if (Math.abs(hn1.start - hn1.end) < OrthogonalRoutingGenerator.TOLERANCE
                || Math.abs(hn2.start - hn2.end) < OrthogonalRoutingGenerator.TOLERANCE) {
            return;
        }

        // compare number of conflicts for both variants
        int conflicts1 = countConflicts(hn1.targetPosis, hn2.sourcePosis, minDiff);
        int conflicts2 = countConflicts(hn2.targetPosis, hn1.sourcePosis, minDiff);

        // compare number of crossings for both variants
        int crossings1 = countCrossings(hn1.targetPosis, hn2.start, hn2.end)
                + countCrossings(hn2.sourcePosis, hn1.start, hn1.end);
        int crossings2 = countCrossings(hn2.targetPosis, hn1.start, hn1.end)
                + countCrossings(hn1.sourcePosis, hn2.start, hn2.end);

        int depValue1 = CONFLICT_PENALTY * conflicts1 + crossings1;
        int depValue2 = CONFLICT_PENALTY * conflicts2 + crossings2;

        if (depValue1 < depValue2) {
            // create dependency from first hypernode to second one
            new Dependency(hn1, hn2, depValue2 - depValue1);
        } else if (depValue1 > depValue2) {
            // create dependency from second hypernode to first one
            new Dependency(hn2, hn1, depValue1 - depValue2);
        } else if (depValue1 > 0 && depValue2 > 0) {
            // create two dependencies with zero weight
            new Dependency(hn1, hn2, 0);
            new Dependency(hn2, hn1, 0);
        }
    }

    /**
     * Counts the number of conflicts for the given lists of positions.
     * 
     * @param posis1
     *            sorted list of positions
     * @param posis2
     *            sorted list of positions
     * @param minDiff
     *            minimal difference between two positions
     * @return number of positions that overlap
     */
    private static int countConflicts(final List<Double> posis1, final List<Double> posis2, final double minDiff) {

        int conflicts = 0;

        if (!posis1.isEmpty() && !posis2.isEmpty()) {
            Iterator<Double> iter1 = posis1.iterator();
            Iterator<Double> iter2 = posis2.iterator();
            double pos1 = iter1.next();
            double pos2 = iter2.next();
            boolean hasMore = true;

            do {
                if (pos1 > pos2 - minDiff && pos1 < pos2 + minDiff) {
                    conflicts++;
                }

                if (pos1 <= pos2 && iter1.hasNext()) {
                    pos1 = iter1.next();
                } else if (pos2 <= pos1 && iter2.hasNext()) {
                    pos2 = iter2.next();
                } else {
                    hasMore = false;
                }
            } while (hasMore);
        }

        return conflicts;
    }

    /**
     * Counts the number of crossings for a given list of positions.
     * 
     * @param posis
     *            sorted list of positions
     * @param start
     *            start of the critical area
     * @param end
     *            end of the critical area
     * @return number of positions in the critical area
     */
    private static int countCrossings(final List<Double> posis, final double start, final double end) {
        int crossings = 0;
        for (double pos : posis) {
            if (pos > end) {
                break;
            } else if (pos >= start) {
                crossings++;
            }
        }
        return crossings;
    }

    ///////////////////////////////////////////////////////////////////////////////
    // Cycle Breaking

    /**
     * Breaks all cycles in the given hypernode structure by reversing or removing some dependencies. This
     * implementation assumes that the dependencies of zero weight are exactly the two-cycles of the hypernode
     * structure.
     * 
     * @param nodes
     *            list of hypernodes
     * @param random
     *            random number generator
     */
    public static void breakCycles(final List<HyperNode> nodes, final Random random) {
        LinkedList<HyperNode> sources = Lists.newLinkedList();
        LinkedList<HyperNode> sinks = Lists.newLinkedList();

        // initialize values for the algorithm
        int nextMark = -1;
        for (HyperNode node : nodes) {
            node.mark = nextMark--;
            int inweight = 0, outweight = 0;

            for (Dependency dependency : node.outgoing) {
                outweight += dependency.weight;
            }

            for (Dependency dependency : node.incoming) {
                inweight += dependency.weight;
            }

            node.inweight = inweight;
            node.outweight = outweight;

            if (outweight == 0) {
                sinks.add(node);
            } else if (inweight == 0) {
                sources.add(node);
            }
        }

        // assign marks to all nodes, ignore dependencies of weight zero
        Set<HyperNode> unprocessed = Sets.newTreeSet(nodes);
        int markBase = nodes.size();
        int nextRight = markBase - 1, nextLeft = markBase + 1;
        List<HyperNode> maxNodes = new ArrayList<HyperNode>();

        while (!unprocessed.isEmpty()) {
            while (!sinks.isEmpty()) {
                HyperNode sink = sinks.removeFirst();
                unprocessed.remove(sink);
                sink.mark = nextRight--;
                updateNeighbors(sink, sources, sinks);
            }

            while (!sources.isEmpty()) {
                HyperNode source = sources.removeFirst();
                unprocessed.remove(source);
                source.mark = nextLeft++;
                updateNeighbors(source, sources, sinks);
            }

            int maxOutflow = Integer.MIN_VALUE;
            for (HyperNode node : unprocessed) {
                int outflow = node.outweight - node.inweight;
                if (outflow >= maxOutflow) {
                    if (outflow > maxOutflow) {
                        maxNodes.clear();
                        maxOutflow = outflow;
                    }
                    maxNodes.add(node);
                }
            }

            if (!maxNodes.isEmpty()) {
                // if there are multiple hypernodes with maximal outflow, select one randomly
                HyperNode maxNode = maxNodes.get(random.nextInt(maxNodes.size()));
                unprocessed.remove(maxNode);
                maxNode.mark = nextLeft++;
                updateNeighbors(maxNode, sources, sinks);
                maxNodes.clear();
            }
        }

        // shift ranks that are left of the mark base
        int shiftBase = nodes.size() + 1;
        for (HyperNode node : nodes) {
            if (node.mark < markBase) {
                node.mark += shiftBase;
            }
        }

        // process edges that point left: remove those of zero weight, reverse the others
        for (HyperNode source : nodes) {
            ListIterator<Dependency> depIter = source.outgoing.listIterator();
            while (depIter.hasNext()) {
                Dependency dependency = depIter.next();
                HyperNode target = dependency.target;

                if (source.mark > target.mark) {
                    depIter.remove();
                    target.incoming.remove(dependency);

                    if (dependency.weight > 0) {
                        dependency.source = target;
                        target.outgoing.add(dependency);
                        dependency.target = source;
                        source.incoming.add(dependency);
                    }
                }
            }
        }
    }

    /**
     * Updates in-weight and out-weight values of the neighbors of the given node, simulating its removal from the
     * graph. The sources and sinks lists are also updated.
     * 
     * @param node
     *            node for which neighbors are updated
     * @param sources
     *            list of sources
     * @param sinks
     *            list of sinks
     */
    private static void updateNeighbors(final HyperNode node, final List<HyperNode> sources,
            final List<HyperNode> sinks) {

        // process following nodes
        for (Dependency dep : node.outgoing) {
            if (dep.target.mark < 0 && dep.weight > 0) {
                dep.target.inweight -= dep.weight;
                if (dep.target.inweight <= 0 && dep.target.outweight > 0) {
                    sources.add(dep.target);
                }
            }
        }

        // process preceding nodes
        for (Dependency dep : node.incoming) {
            if (dep.source.mark < 0 && dep.weight > 0) {
                dep.source.outweight -= dep.weight;
                if (dep.source.outweight <= 0 && dep.source.inweight > 0) {
                    sinks.add(dep.source);
                }
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////////
    // Topological Ordering

    /**
     * Perform a topological numbering of the given hypernodes.
     * 
     * @param nodes
     *            list of hypernodes
     */
    public static void topologicalNumbering(final List<HyperNode> nodes) {
        // determine sources, targets, incoming count and outgoing count; targets are only
        // added to the list if they only connect westward ports (that is, if all their
        // horizontal segments point to the right)
        List<HyperNode> sources = Lists.newArrayList();
        List<HyperNode> rightwardTargets = Lists.newArrayList();
        for (HyperNode node : nodes) {
            node.inweight = node.incoming.size();
            node.outweight = node.outgoing.size();

            if (node.inweight == 0) {
                sources.add(node);
            }

            if (node.outweight == 0 && node.sourcePosis.size() == 0) {
                rightwardTargets.add(node);
            }
        }

        int maxRank = -1;

        // assign ranks using topological numbering
        while (!sources.isEmpty()) {
            HyperNode node = sources.remove(0);
            for (Dependency dep : node.outgoing) {
                HyperNode target = dep.target;
                target.rank = Math.max(target.rank, node.rank + 1);
                maxRank = Math.max(maxRank, target.rank);

                target.inweight--;
                if (target.inweight == 0) {
                    sources.add(target);
                }
            }
        }

        /*
         * If we stopped here, hyper nodes that don't have any horizontal segments pointing leftward would be ranked
         * just like every other hyper node. This would move back edges too far away from their target node. To remedy
         * that, we move all hyper nodes with horizontal segments only pointing rightwards as far right as possible.
         */
        if (maxRank > -1) {
            // assign all target nodes with horzizontal segments pointing to the right the
            // rightmost rank
            for (HyperNode node : rightwardTargets) {
                node.rank = maxRank;
            }

            // let all other segments with horizontal segments pointing rightwards move as
            // far right as possible
            while (!rightwardTargets.isEmpty()) {
                HyperNode node = rightwardTargets.remove(0);

                // The node only has connections to western ports
                for (Dependency dep : node.incoming) {
                    HyperNode source = dep.source;
                    if (source.sourcePosis.size() > 0) {
                        continue;
                    }

                    source.rank = Math.min(source.rank, node.rank - 1);

                    source.outweight--;
                    if (source.outweight == 0) {
                        rightwardTargets.add(source);
                    }
                }
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////////
    // Utilities

    /**
     * Inserts a given value into a sorted list.
     * 
     * @param list
     *            sorted list
     * @param value
     *            value to insert
     */
    private static void insertSorted(final List<Double> list, final double value) {
        ListIterator<Double> listIter = list.listIterator();
        while (listIter.hasNext()) {
            double next = listIter.next().floatValue();
            if (Math.abs(next - value) < OrthogonalRoutingGenerator.TOLERANCE) {
                // an equal value is already present in the list
                return;
            } else if (next > value) {
                listIter.previous();
                break;
            }
        }
        listIter.add(Double.valueOf(value));
    }
}
