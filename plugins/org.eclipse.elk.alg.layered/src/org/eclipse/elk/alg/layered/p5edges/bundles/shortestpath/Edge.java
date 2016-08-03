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

/**
 * @author Kiel University
 *
 */
public class Edge {
    private Node source;
    private Node target;
    private double weight;

    /**
     * @param source
     * @param target
     * @param weight
     */
    public Edge(final Node source, final Node target, final double weight) {
        this.source = source;
        this.target = target;
        this.weight = weight;
        source.getOutgoingEdges().add(this);
    }
    
    /**
     * @param source
     * @param target
     * @param weight
     */
    public Edge(final Node source, final Node target) {
        this(source, target, source.getPos().distance(target.getPos()));
    }
    
    /**
     * @return the source
     */
    public Node getSource() {
        return source;
    }
    
    /**
     * @return the target
     */
    public Node getTarget() {
        return target;
    }
    
    /**
     * @return the weight
     */
    public double getWeight() {
        return weight;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "Edge [source=" + source.getPos() + ", target=" + target.getPos() + ", weight=" + weight + "]";
    }
}