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
/**
 * @author Kiel University
 *
 */
public class VerticalSegment implements Comparable<VerticalSegment> {

    private Layer layer;
    private int rank;
    private LEdge edge;
    private double start;
    private double end;

    /**
     * @param layer
     * @param rank
     * @param edge
     * @param start
     * @param end
     */
    public VerticalSegment(final Layer layer, final int rank, final LEdge edge, final double start,
            final double end) {
        this.layer = layer;
        this.rank = rank;
        this.edge = edge;
        this.start = start;
        this.end = end;
    }
    
    /**
     * @param otherSegment
     * @param newEdge
     */
    public VerticalSegment(final VerticalSegment otherSegment, final LEdge newEdge) {
        this(otherSegment.layer, otherSegment.rank, newEdge, otherSegment.start, otherSegment.end);
    }

    /**
     * 
     */
    public VerticalSegment(final double start, final double end) {
        this.start = start;
        this.end = end;
    }

    /**
     * @return the start
     */
    public double getStart() {
        return start;
    }

    /**
     * @param start
     *            the start to set
     */
    public void setStart(final double start) {
        this.start = start;
    }

    /**
     * @return the End
     */
    public double getEnd() {
        return end;
    }

    /**
     * @param end
     *            the end to set
     */
    public void setEnd(final double end) {
        this.end = end;
    }

    /**
     * @return the layer
     */
    public Layer getLayer() {
        return layer;
    }

    /**
     * @return the rank
     */
    public int getRank() {
        return rank;
    }

    /**
     * @return the edge
     */
    public LEdge getEdge() {
        return edge;
    }

    /**
     * @return the minimum of start and end.
     */
    public double getTop() {
        return Math.min(start, end);
    }

    /**
     * @return the maximum of start and end.
     */
    public double getBottom() {
        return Math.max(start, end);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(final VerticalSegment o) {
        return (int) (getTop() != o.getTop() ? getTop() - o.getTop() : getBottom() - o.getBottom());
    }

    /**
     * @param segment
     * @return
     */
    public boolean overlaps(final VerticalSegment o) {
        return ((getTop() <= o.getTop() && getBottom() > o.getTop())
                || (getTop() >= o.getTop() && getTop() < o.getBottom()));
    }
    
//    public void include(final VerticalSegment o) {
//        start = Math.min(start, o.getTop());
//        end = Math.max(end, o.getBottom());
//    }
    
    /**
     * @param y
     */
    public void include(final double y) {
        if (start < end) {
            start = Math.min(start, y);
            end = Math.max(end, y);
        } else {
            start = Math.max(start, y);
            end = Math.min(end, y);
        }
    }
    
    public void enlarge(final double d) {
        if (start < end) {
            start -= d;
            end += d;
        } else {
            start += d;
            end -= d;
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "VerticalSegment [layer=" + layer + ", rank=" + rank + ", edge=" + edge + ", yStart=" + start
                + ", yEnd=" + end + "]";
    }

}
