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

import com.google.common.collect.Lists;

/**
 * @author Kiel University
 *
 */
public class Graph {

    public static int X_START = 0;
    public static int X_SOURCE_PORTS = 1;
    public static int X_SOURCE_EDGES = 2;
    public static int X_TARGET_EDGES = 3;
    public static int X_TARGET_PORTS = 4;
    public static int X_GOAL = 5;
    
    private List<Node> nodes;
    private List<Edge> edges;
    /**
     * 
     */
    public Graph() {
        nodes = Lists.newLinkedList();
//        edges = Lists.newLinkedList();
    }
    /**
     * @param nodes
     * @param edges
     */
    public Graph(final List<Node> nodes, final List<Edge> edges) {
        this.nodes = nodes;
        this.edges = edges;
    }
    /**
     * @return the nodes
     */
    public List<Node> getNodes() {
        return nodes;
    }
    /**
     * @return the edges
     */
//    public List<Edge> getEdges() {
//        return edges;
//    }
    
    /**
     * @param n
     */
    public void addNode(final Node n) {
        nodes.add(n);
    }
    
    /**
     * @param e
     */
//    public void addEdge(final Edge e) {
//        edges.add(e);
//    }
}