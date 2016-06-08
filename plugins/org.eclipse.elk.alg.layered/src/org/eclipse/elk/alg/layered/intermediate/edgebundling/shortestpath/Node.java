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
package org.eclipse.elk.alg.layered.intermediate.edgebundling.shortestpath;

import java.util.List;

import org.eclipse.elk.core.math.KVector;

import com.google.common.collect.Lists;

/**
 * @author Kiel University
 *
 */
public class Node implements Comparable<Node> {
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
    public void setgScore(double gScore) {
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
    public void setPredecessor(Node predecessor) {
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
    public int compareTo(Node o) {
        return (int) (o.fScore - fScore);
    }
}