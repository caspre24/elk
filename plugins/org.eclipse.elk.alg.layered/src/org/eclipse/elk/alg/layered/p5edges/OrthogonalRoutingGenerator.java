/*******************************************************************************
 * Copyright (c) 2010, 2015 Kiel University and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Kiel University - initial API and implementation
 *******************************************************************************/
package org.eclipse.elk.alg.layered.p5edges;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.elk.alg.layered.DebugUtil;
import org.eclipse.elk.alg.layered.graph.LEdge;
import org.eclipse.elk.alg.layered.graph.LGraph;
import org.eclipse.elk.alg.layered.graph.LNode;
import org.eclipse.elk.alg.layered.graph.LNode.NodeType;
import org.eclipse.elk.alg.layered.graph.LPort;
import org.eclipse.elk.alg.layered.graph.Layer;
import org.eclipse.elk.alg.layered.intermediate.edgebundling.VerticalSegment;
import org.eclipse.elk.alg.layered.p5edges.HyperNodeUtils.HyperNode;
import org.eclipse.elk.alg.layered.p5edges.bundles.IBundleHandler;
import org.eclipse.elk.alg.layered.p5edges.bundles.LazyBundleHandler;
import org.eclipse.elk.alg.layered.properties.InternalProperties;
import org.eclipse.elk.alg.layered.properties.LayeredOptions;
import org.eclipse.elk.core.math.KVector;
import org.eclipse.elk.core.math.KVectorChain;
import org.eclipse.elk.core.options.PortSide;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Edge routing implementation that creates orthogonal bend points. Inspired by:
 * <ul>
 *   <li>Georg Sander. Layout of directed hypergraphs with orthogonal hyperedges. In
 *     <i>Proceedings of the 11th International Symposium on Graph Drawing (GD '03)</i>,
 *     volume 2912 of LNCS, pp. 381-386. Springer, 2004.</li>
 *   <li>Giuseppe di Battista, Peter Eades, Roberto Tamassia, Ioannis G. Tollis,
 *     <i>Graph Drawing: Algorithms for the Visualization of Graphs</i>,
 *     Prentice Hall, New Jersey, 1999 (Section 9.4, for cycle breaking in the
 *     hyperedge segment graph)
 * </ul>
 * 
 * <p>This is a generic implementation that can be applied to all four routing directions.
 * Usually, edges will be routed from west to east. However, with northern and southern
 * external ports, this changes: edges are routed from south to north and north to south,
 * respectively. To support these different requirements, the routing direction-related
 * code is factored out into {@link IRoutingDirectionStrategy routing strategies}.</p>
 * 
 * <p>When instantiating a new routing generator, the concrete directional strategy must be
 * specified. Once that is done, {@link #routeEdges(LGraph, List, int, List, double)}
 * is called repeatedly to route edges between given lists of nodes.</p>
 * 
 * @author msp
 * @author cds
 * @kieler.design proposed by msp
 * @kieler.rating proposed yellow by msp
 */
public final class OrthogonalRoutingGenerator {
    
    ///////////////////////////////////////////////////////////////////////////////
    // Routing Strategies
    
    /**
     * A routing direction strategy adapts the {@link OrthogonalRoutingGenerator} to different
     * routing directions. Usually, but not always, edges will be routes from west to east.
     * However, with northern and southern external ports, this changes. Routing strategies
     * support that.
     * 
     * @author cds
     */
    public interface IRoutingDirectionStrategy {
        /**
         * Returns the port's position on a hyper edge axis. In the west-to-east routing
         * case, this would be the port's exact y coordinate.
         * 
         * @param port the port.
         * @return the port's coordinate on the hyper edge axis.
         */
        double getPortPositionOnHyperNode(final LPort port);
        
        /**
         * Returns the side of ports that should be considered on a source layer. For a
         * west-to-east routing, this would probably be the eastern ports of each western
         * layer.
         * 
         * @return the side of ports to be considered in the source layer.
         */
        PortSide getSourcePortSide();
        
        /**
         * Returns the side of ports that should be considered on a target layer. For a
         * west-to-east routing, this would probably be the western ports of each eastern
         * layer.
         * 
         * @return the side of ports to be considered in the target layer.
         */
        PortSide getTargetPortSide();
        
        /**
         * Calculates and assigns bend points for edges incident to the ports belonging
         * to the given hyper edge.
         * 
         * @param hyperNode the hyper edge.
         * @param startPos the position of the trunk of the first hyper edge between the
         *                 layers. This position, together with the current hyper node's
         *                 rank allows the calculation of the hyper node's trunk's position.
         */
        void calculateBendPoints(final HyperNode hyperNode, final double startPos);
    }
    
    /**
     * Routing strategy for routing layers from west to east.
     * 
     * @author cds
     */
    private class WestToEastRoutingStrategy implements IRoutingDirectionStrategy {
        /**
         * {@inheritDoc}
         */
        public double getPortPositionOnHyperNode(final LPort port) {
            return port.getNode().getPosition().y + port.getPosition().y + port.getAnchor().y;
        }

        /**
         * {@inheritDoc}
         */
        public PortSide getSourcePortSide() {
            return PortSide.EAST;
        }

        /**
         * {@inheritDoc}
         */
        public PortSide getTargetPortSide() {
            return PortSide.WEST;
        }

        /**
         * {@inheritDoc}
         */
        public void calculateBendPoints(final HyperNode hyperNode, final double startPos) {
            
            // Calculate coordinates for each port's bend points
            double x = startPos + hyperNode.rank * edgeSpacing;
            
            for (LPort port : hyperNode.ports) {
                double sourcey = port.getAbsoluteAnchor().y;
                
                for (LEdge edge : port.getOutgoingEdges()) {
                    LPort target = edge.getTarget();
                    double targety = target.getAbsoluteAnchor().y;
                    if (Math.abs(sourcey - targety) > TOLERANCE) {
                        KVector point1 = new KVector(x, sourcey);
                        edge.getBendPoints().add(point1);
                        addJunctionPointIfNecessary(edge, hyperNode, point1, true);
                        
                        KVector point2 = new KVector(x, targety);
                        edge.getBendPoints().add(point2);
                        addJunctionPointIfNecessary(edge, hyperNode, point2, true);

                        saveVerticalSegment(edge, port.getNode().getLayer(), hyperNode.rank, sourcey, targety);
                    }
                }
            }
        }
    }
    
    /**
     * Routing strategy for routing layers from north to south.
     * 
     * @author cds
     */
    private class NorthToSouthRoutingStrategy implements IRoutingDirectionStrategy {
        /**
         * {@inheritDoc}
         */
        public double getPortPositionOnHyperNode(final LPort port) {
            return port.getNode().getPosition().x + port.getPosition().x + port.getAnchor().x;
        }

        /**
         * {@inheritDoc}
         */
        public PortSide getSourcePortSide() {
            return PortSide.SOUTH;
        }

        /**
         * {@inheritDoc}
         */
        public PortSide getTargetPortSide() {
            return PortSide.NORTH;
        }

        /**
         * {@inheritDoc}
         */
        public void calculateBendPoints(final HyperNode hyperNode, final double startPos) {
            
            // Calculate coordinates for each port's bend points
            double y = startPos + hyperNode.rank * edgeSpacing;
            
            for (LPort port : hyperNode.ports) {
                double sourcex = port.getAbsoluteAnchor().x;
                
                for (LEdge edge : port.getOutgoingEdges()) {
                    LPort target = edge.getTarget();
                    double targetx = target.getAbsoluteAnchor().x;
                    if (Math.abs(sourcex - targetx) > TOLERANCE) {
                        KVector point1 = new KVector(sourcex, y);
                        edge.getBendPoints().add(point1);
                        addJunctionPointIfNecessary(edge, hyperNode, point1, false);
                        
                        KVector point2 = new KVector(targetx, y);
                        edge.getBendPoints().add(point2);
                        addJunctionPointIfNecessary(edge, hyperNode, point2, false);

                        saveVerticalSegment(edge, port.getNode().getLayer(), hyperNode.rank, sourcex, targetx);
                    }
                }
            }
        }
    }
    
    /**
     * Routing strategy for routing layers from south to north.
     * 
     * @author cds
     */
    private class SouthToNorthRoutingStrategy implements IRoutingDirectionStrategy {
        /**
         * {@inheritDoc}
         */
        public double getPortPositionOnHyperNode(final LPort port) {
            return port.getNode().getPosition().x + port.getPosition().x + port.getAnchor().x;
        }

        /**
         * {@inheritDoc}
         */
        public PortSide getSourcePortSide() {
            return PortSide.NORTH;
        }

        /**
         * {@inheritDoc}
         */
        public PortSide getTargetPortSide() {
            return PortSide.SOUTH;
        }

        /**
         * {@inheritDoc}
         */
        public void calculateBendPoints(final HyperNode hyperNode, final double startPos) {
            
            // Calculate coordinates for each port's bend points
            double y = startPos - hyperNode.rank * edgeSpacing;
            
            for (LPort port : hyperNode.ports) {
                double sourcex = port.getAbsoluteAnchor().x;
                
                for (LEdge edge : port.getOutgoingEdges()) {
                    LPort target = edge.getTarget();
                    double targetx = target.getAbsoluteAnchor().x;
                    if (Math.abs(sourcex - targetx) > TOLERANCE) {
                        KVector point1 = new KVector(sourcex, y);
                        edge.getBendPoints().add(point1);
                        addJunctionPointIfNecessary(edge, hyperNode, point1, false);
                        
                        KVector point2 = new KVector(targetx, y);
                        edge.getBendPoints().add(point2);
                        addJunctionPointIfNecessary(edge, hyperNode, point2, false);

                        saveVerticalSegment(edge, port.getNode().getLayer(), hyperNode.rank, sourcex, targetx);
                    }
                }
            }
        }
    }
    
    /**
     * Enumeration of available routing directions.
     */
    public static enum RoutingDirection {
        /** west to east routing direction. */
        WEST_TO_EAST,
        /** north to south routing direction. */
        NORTH_TO_SOUTH,
        /** south to north routing direction. */
        SOUTH_TO_NORTH;
    }
    
    
    ///////////////////////////////////////////////////////////////////////////////
    // Constants and Variables

    /** differences below this tolerance value are treated as zero. */
    public static final double TOLERANCE = 1e-3;
    
    /** factor for edge spacing used to determine the conflict threshold. */
    private static final double CONFL_THRESH_FACTOR = 0.2;
    /** routing direction strategy. */
    private final IRoutingDirectionStrategy routingStrategy;
    /** spacing between edges. */
    private final double edgeSpacing;
    /** threshold at which conflicts of horizontal line segments are detected. */
    private final double conflictThreshold;
    /** set of already created junction points, to avoid multiple points at the same position. */
    private final Set<KVector> createdJunctionPoints = Sets.newHashSet();
    /** prefix of debug output files. */
    private final String debugPrefix;
    
    
    ///////////////////////////////////////////////////////////////////////////////
    // Constructor
    
    /**
     * Constructs a new instance.
     * 
     * @param direction the direction edges should point at.
     * @param edgeSpacing the space between edges.
     * @param debugPrefix prefix of debug output files, or {@code null} if no debug output should
     *                    be generated.
     */
    public OrthogonalRoutingGenerator(final RoutingDirection direction, final double edgeSpacing,
            final String debugPrefix) {
        
        switch (direction) {
        case WEST_TO_EAST:
            this.routingStrategy = new WestToEastRoutingStrategy();
            break;
        case NORTH_TO_SOUTH:
            this.routingStrategy = new NorthToSouthRoutingStrategy();
            break;
        case SOUTH_TO_NORTH:
            this.routingStrategy = new SouthToNorthRoutingStrategy();
            break;
        default:
            throw new IllegalArgumentException();
        }
        this.edgeSpacing = edgeSpacing;
        this.conflictThreshold = CONFL_THRESH_FACTOR * edgeSpacing;
        this.debugPrefix = debugPrefix;
    }


    ///////////////////////////////////////////////////////////////////////////////
    // Edge Routing

    /**
     * Route edges between the given layers.
     * 
     * @param layeredGraph
     *            the layered graph.
     * @param sourceLayerNodes
     *            the left layer. May be {@code null}.
     * @param sourceLayerIndex
     *            the source layer's index. Ignored if there is no source layer.
     * @param targetLayerNodes
     *            the right layer. May be {@code null}.
     * @param startPos
     *            horizontal position of the first routing slot
     * @return the number of routing slots for this layer
     */
    public int routeEdges(final LGraph layeredGraph, final Iterable<LNode> sourceLayerNodes, final int sourceLayerIndex,
            final Iterable<LNode> targetLayerNodes, final double startPos) {

        return routeEdges(layeredGraph, sourceLayerNodes, sourceLayerIndex, targetLayerNodes, startPos,
                new LazyBundleHandler());
    }


    /**
     * Route edges between the given layers.
     * 
     * @param layeredGraph
     *            the layered graph.
     * @param sourceLayerNodes
     *            the left layer. May be {@code null}.
     * @param sourceLayerIndex
     *            the source layer's index. Ignored if there is no source layer.
     * @param targetLayerNodes
     *            the right layer. May be {@code null}.
     * @param startPos
     *            horizontal position of the first routing slot
     * @param bundleHandler
     * @return the number of routing slots for this layer
     */
    public int routeEdges(final LGraph layeredGraph, final Iterable<LNode> sourceLayerNodes, final int sourceLayerIndex,
            final Iterable<LNode> targetLayerNodes, final double startPos, final IBundleHandler bundleHandler) {

        Map<LPort, HyperNode> portToHyperNodeMap = Maps.newHashMap();
        List<HyperNode> hyperNodes = Lists.newArrayList();
        
        // create hypernodes for eastern output ports of the left layer and for western
        // output ports of the right layer
        HyperNodeUtils.createHyperNodes(sourceLayerNodes, routingStrategy.getSourcePortSide(), hyperNodes,
                portToHyperNodeMap, routingStrategy);
        HyperNodeUtils.createHyperNodes(targetLayerNodes, routingStrategy.getTargetPortSide(), hyperNodes,
                portToHyperNodeMap, routingStrategy);
        
        // merge hypernodes for bundled edges, if applicable
        bundleHandler.mergeHypernodes(hyperNodes, portToHyperNodeMap, routingStrategy, sourceLayerIndex);
        
        // create dependencies for the hypernode ordering graph
        HyperNodeUtils.createDependencies(hyperNodes, conflictThreshold);
        
        // write the full dependency graph to an output file
        if (debugPrefix != null) {
            DebugUtil.writeDebugGraph(layeredGraph, sourceLayerNodes == null ? 0
                    : sourceLayerIndex + 1, hyperNodes, debugPrefix, "full");
        }
        
        // break cycles
        HyperNodeUtils.breakCycles(hyperNodes, layeredGraph.getProperty(InternalProperties.RANDOM));

        // write the acyclic dependency graph to an output file
        if (debugPrefix != null) {
            DebugUtil.writeDebugGraph(layeredGraph, sourceLayerNodes == null ? 0
                    : sourceLayerIndex + 1, hyperNodes, debugPrefix, "acyclic");
        }
        
        // assign ranks to the hypernodes
        HyperNodeUtils.topologicalNumbering(hyperNodes);
        
        // set bend points with appropriate coordinates
        int rankCount = -1;
        for (HyperNode node : hyperNodes) {
            // Hypernodes that are just straight lines don't take up a slot and don't need bend points
            if (Math.abs(node.start - node.end) < TOLERANCE) {
                continue;
            }
            
            rankCount = Math.max(rankCount, node.rank);
            
            routingStrategy.calculateBendPoints(node, startPos);
        }
        
        // release the created resources
        createdJunctionPoints.clear();
        return rankCount + 1;
    }
    
    
    ///////////////////////////////////////////////////////////////////////////////
    // Utilities
    
    /**
     * Add a junction point to the given edge if necessary. It is necessary to add a junction point if
     * the bend point is not at one of the two end positions of the hypernode.
     * 
     * @param edge an edge
     * @param hyperNode the corresponding hypernode
     * @param pos the bend point position
     * @param vertical {@code true} if the connecting segment is vertical, {@code false} if it
     *          is horizontal
     */
    private void addJunctionPointIfNecessary(final LEdge edge, final HyperNode hyperNode,
            final KVector pos, final boolean vertical) {
        
        double p = vertical ? pos.y : pos.x;
        
        // check if the given bend point is somewhere between the start and end position of the hypernode
        if (p > hyperNode.start && p < hyperNode.end
                || !hyperNode.sourcePosis.isEmpty() && !hyperNode.targetPosis.isEmpty()
                // the bend point is at the start and joins another edge at the same position
                && (Math.abs(p - hyperNode.sourcePosis.getFirst()) < TOLERANCE
                    && Math.abs(p - hyperNode.targetPosis.getFirst()) < TOLERANCE
                // the bend point is at the end and joins another edge at the same position
                    || Math.abs(p - hyperNode.sourcePosis.getLast()) < TOLERANCE
                    && Math.abs(p - hyperNode.targetPosis.getLast()) < TOLERANCE)) {
            
            // check whether there is already a junction point at the same position
            if (!createdJunctionPoints.contains(pos)) {
                
                // create a new junction point for the edge at the bend point's position
                KVectorChain junctionPoints = edge.getProperty(LayeredOptions.JUNCTION_POINTS);
                if (junctionPoints == null) {
                    junctionPoints = new KVectorChain();
                    edge.setProperty(LayeredOptions.JUNCTION_POINTS, junctionPoints);
                }
                
                KVector jpoint = new KVector(pos);
                junctionPoints.add(jpoint);
                createdJunctionPoints.add(jpoint);
            }
        }
    }

    
    /**
     * @param point1
     * @param layer
     * @param rank
     */
    private void saveVerticalSegment(final LEdge edge, final Layer layer,
            final int rank, final double sourcey, final double tagety) {
        
        LEdge originEdge = edge;
        while (originEdge.getSource().getNode().getType() == NodeType.LONG_EDGE) {
            originEdge = (LEdge) originEdge.getSource().getNode().getProperty(InternalProperties.ORIGIN);
        }
        originEdge.getProperty(InternalProperties.SEGMENTS)
                .add(new VerticalSegment(layer, rank, originEdge, sourcey, tagety));
    }

}
