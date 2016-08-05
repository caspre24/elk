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
package org.eclipse.elk.alg.layered.p5edges.bundles.scanline;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

import org.eclipse.elk.alg.layered.compaction.recthull.Scanline;
import org.eclipse.elk.alg.layered.compaction.recthull.Scanline.EventHandler;
import org.eclipse.elk.alg.layered.graph.LInsets;
import org.eclipse.elk.alg.layered.graph.LNode;
import org.eclipse.elk.alg.layered.graph.LPort;
import org.eclipse.elk.alg.layered.networksimplex.NEdge;
import org.eclipse.elk.alg.layered.networksimplex.NNode;
import org.eclipse.elk.alg.layered.p5edges.HyperNodeUtils.HyperNode;
import org.eclipse.elk.alg.layered.properties.Spacings;
import org.eclipse.elk.core.math.KVector;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * @author Kiel University
 *
 */
public final class ScanlineUtils {
    
    private Spacings spacings;


    /**
     * @param spacings
     */
    public ScanlineUtils(final Spacings spacings) {
        this.spacings = spacings;
    }

    /**
     * @param nodes
     */
    public void sweep(final Collection<NNode> nodes) {

        // add all nodes twice (once for the lower, once for the upper border)
        List<Timestamp> points = Lists.newArrayList();
        List<CNode> cNodes = Lists.newLinkedList();
        for (NNode n : nodes) {
            if (n.origin == null) {
                continue;
            }
            CNode cNode = new CNode(n);
            cNodes.add(cNode);
            points.add(new Timestamp(cNode, false));
            points.add(new Timestamp(cNode, true));
        }

        ConstraintsScanlineHandler constraintsScanlineHandler = new ConstraintsScanlineHandler(cNodes);

        // execute the scanline
        Scanline.execute(points, CONSTRAIN_SCANLINE_COMPARATOR, constraintsScanlineHandler);
    }

    /**
     * @author Kiel University
     *
     */
    private static enum Type {
        LNODE, HYPERNODE;
    }

    /**
     * @author Kiel University
     *
     */
    private class CNode {

        private final NNode origin;
        private final Type type;
        private final List<LPort> ports;

        private final double x, y, width, height;
        private final LInsets margin;
        private int id;

        /**
         * @param origin
         */
        CNode(final NNode origin) {
            this.origin = origin;
            if (origin.origin instanceof HyperNode) {
                HyperNode hNode = (HyperNode) origin.origin;
                x = hNode.x;
                y = hNode.start;
                width = hNode.width;
                height = hNode.end - hNode.start;
                type = Type.HYPERNODE;
                ports = hNode.ports;
                margin = new LInsets();
            } else if (origin.origin instanceof LNode) {
                LNode lNode = (LNode) origin.origin;
                KVector position = lNode.getPosition();
                LInsets origMargin = lNode.getMargin();
                KVector size = lNode.getSize();
                x = position.x;
                y = position.y;
                width = size.x;
                height = size.y;
                type = Type.LNODE;
                ports = lNode.getPorts();
                margin = origMargin;
            } else {
                throw new IllegalArgumentException("Type " + origin.origin.getClass() + " not supported as origin!");
            }
        }

        /**
         * @param right
         */
        public void addConstraint(final CNode other) {
            if (type == Type.HYPERNODE && other.type == Type.HYPERNODE) {
                NEdge.of()
                        .weight(0)
                        .delta((int) (width + spacings.edgeEdgeSpacing))
                        .source(origin)
                        .target(other.origin)
                        .create();
            } else if (type != other.type) {
                boolean disjoint = Collections.disjoint(ports, other.ports);
                NEdge.of()
                        .weight(disjoint ? 0 : 5)
                        .delta((int) (width + margin.right + spacings.edgeNodeSpacing + other.margin.left))
                        .source(origin)
                        .target(other.origin)
                        .create();
            }
        }
        
        /* (non-Javadoc)
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            return origin.origin.toString();
        }
    }

    /**
     * A timestamp in the sense of the scanline algorithm to determine the constraints of the compaction graph. The
     * scanline traverses the plane from top (negative infinity) to bottom (positive infinity). Every timestamp (or
     * point) it encounters either represents the upper (y) or lower (y+height) border of a rectangle. Note that y is
     * also denoted as "low" and y+height as "high".
     */
    private static class Timestamp {
        private boolean low;
        private CNode node;

        Timestamp(final CNode node, final boolean low) {
            this.node = node;
            this.low = low;
        }
        
        /* (non-Javadoc)
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            if (low) {
                return node.y + ":" + node.origin.origin;
            } else {
                return (node.y + node.height) + ":" + node.origin.origin;
            }
        }
    }

    /**
     * Can be used to sort the passed {@link Timestamp}s either based on their {@link CNode}'s y-coordinate (low) or
     * y-coordinate plus height (high). If two positions are equal, the "high" value is sorted before the "low" value.
     * This implicates that <strong>no</strong> constraint is generated for element that barely share a common y-value.
     */
    private static final Comparator<Timestamp> CONSTRAIN_SCANLINE_COMPARATOR = (p1, p2) -> {

        // chose the right coordinate
        double y1 = p1.node.y;
        if (!p1.low) {
            y1 += p1.node.height;
        }
        double y2 = p2.node.y;
        if (!p2.low) {
            y2 += p2.node.height;
        }

        // compare them ...
        int cmp = Double.compare(y1, y2);

        // ... and if they are equal, sort high->low
        if (cmp == 0) {
            if (!p1.low && p2.low) {
                return 1;
            } else if (!p2.low && p1.low) {
                return -1;
            }
        }
        return cmp;
    };

    /**
     * Implements the scanline procedure as discussed by Lengauer.
     */
    private static class ConstraintsScanlineHandler implements EventHandler<Timestamp> {

        /**
         * Sorted set of intervals that allows to query for the left and right neighbor of an interval. Sorted by the x
         * coordinate of a {@link CNode}'s
         * 
         * Note: the methods to query for neighbor elements (e.g., {@link TreeSet#lower(Object)}) are not constant time.
         */
        private TreeSet<CNode> intervals =
                Sets.newTreeSet((c1, c2) -> Double.compare(c1.x + c1.width / 2, c2.x + c2.width / 2));
        /** Candidate array with possible constraints. */
        private CNode[] cand;

        /**
         * @param cNodes
         */
        ConstraintsScanlineHandler(final List<CNode> cNodes) {
            intervals.clear();
            cand = new CNode[cNodes.size()];
            int index = 0;
            for (CNode n : cNodes) {
                n.id = index++;
            }
        }

        @Override
        public void handle(final Timestamp p) {
            // System.out.println((p.low ? "insert " : "delete ") + " " + p.node.hitbox);

            if (p.low) {
                insert(p);
            } else {
                delete(p);
            }
        }

        private void insert(final Timestamp p) {
            intervals.add(p.node);

            cand[p.node.id] = intervals.lower(p.node);

            CNode right = intervals.higher(p.node);
            if (right != null) {
                cand[right.id] = p.node;
            }
        }

        private void delete(final Timestamp p) {

            CNode left = intervals.lower(p.node);
            if (left != null && left == cand[p.node.id]) {
                left.addConstraint(p.node);
//                System.out.println("\t" + left + "->" + p.node);
            }

            CNode right = intervals.higher(p.node);
            if (right != null && cand[right.id] == p.node) {
                p.node.addConstraint(right);
//                System.out.println("\t" + p.node + "->" + right);
            }

            // we are done with you!
            intervals.remove(p.node);
        }
    }

}
