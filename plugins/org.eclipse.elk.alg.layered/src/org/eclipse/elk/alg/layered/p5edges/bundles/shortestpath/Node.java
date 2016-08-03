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
package org.eclipse.elk.alg.layered.p5edges.bundles.shortestpath;

import java.util.List;

import org.eclipse.elk.core.math.KVector;

import com.google.common.collect.Lists;

/**
 * @author Kiel University
 *
 */
public class Node implements Comparable<Node>, Updatable<Double> {
    private Object origin;
    private KVector pos;
    private List<Edge> outgoingEdges = Lists.newLinkedList();
    private double fScore = Double.POSITIVE_INFINITY;
    private double gScore = Double.POSITIVE_INFINITY;
    private Node predecessor;
    
    /**
     * @param origin
     */
    public Node(final double xPos, final double yPos, final Object origin) {
        this.origin = origin;
        this.pos = new KVector(xPos, yPos);
    }
    
    /**
     * @param pos
     */
    public Node(final double xPos, final double yPos) {
        this(xPos, yPos, null);
    }

    /**
     * @return the pos
     */
    public KVector getPos() {
        return pos;
    }

    /**
     * @return the origin
     */
    public Object getOrigin() {
        return origin;
    }

    /**
     * @return the fScore
     */
    public double getfScore() {
        return fScore;
    }

    /**
     * @param fScore the fScore to set
     */
    public void setfScore(final double fScore) {
        this.fScore = fScore;
    }

    /**
     * @return the gScore
     */
    public double getgScore() {
        return gScore;
    }

    /**
     * @param gScore the gScore to set
     */
    public void setgScore(final double gScore) {
        this.gScore = gScore;
    }

    /**
     * @return the predecessor
     */
    public Node getPredecessor() {
        return predecessor;
    }

    /**
     * @param predecessor the predecessor to set
     */
    public void setPredecessor(final Node predecessor) {
        this.predecessor = predecessor;
    }

    /**
     * @return the outgoingEdges
     */
    public List<Edge> getOutgoingEdges() {
        return outgoingEdges;
    }

    /* (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(final Node o) {
        return Double.compare(fScore, o.fScore);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "Node [origin=" + origin + ", pos=" + pos + ", fScore=" + fScore + ", gScore=" + gScore + "]";
    }

    /* (non-Javadoc)
     * @see org.eclipse.elk.alg.layered.intermediate.edgebundling.shortestpath.Updatable#update(java.lang.Object)
     */
    @Override
    public void update(final Double newValue) {
        setfScore(newValue);
    }
}