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

import org.eclipse.elk.alg.layered.graph.LEdge;
import org.eclipse.elk.alg.layered.graph.Layer;

/**
 * @author Kiel University
 *
 */
public class VerticalSegment implements Comparable<VerticalSegment> {

    public Layer layer;
    public int rank;
    public LEdge edge;
    public double yStart;
    public double yEnd;

    /**
     * 
     */
    public VerticalSegment() {
        yStart = Double.POSITIVE_INFINITY;
        yEnd = Double.NEGATIVE_INFINITY;
    }

    /**
     * @param layer
     * @param rank
     * @param edge
     * @param yStart
     * @param yEnd
     */
    public VerticalSegment(final Layer layer, final int rank, final LEdge edge, final double yStart,
            final double yEnd) {
        super();
        this.layer = layer;
        this.rank = rank;
        this.edge = edge;
        this.yStart = yStart;
        this.yEnd = yEnd;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(final VerticalSegment o) {
        return (int) (yStart != o.yStart ? yStart - o.yStart : yEnd - o.yEnd);
    }

    /**
     * @param segment
     * @return
     */
    public boolean overlaps(final VerticalSegment o) {
        return ((yStart <= o.yStart && yEnd > o.yStart) || (yStart >= o.yStart && yStart < o.yEnd));
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "VerticalSegment [layer=" + layer + ", rank=" + rank + ", edge=" + edge + ", yStart=" + yStart
                + ", yEnd=" + yEnd + "]";
    }

}
