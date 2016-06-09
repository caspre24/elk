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

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.google.common.collect.Lists;

/**
 * @author Kiel University
 *
 */
public final class AstarAlgorithm {
    
    private AstarAlgorithm() {
    }

    /**
     * @param start
     * @param goal
     * @return
     */
    public static List<Node> findShortestPath(final Node start, final Node goal) {
        TreeSet<Node> openSet = new TreeSet<Node>();
        Set<Node> closedSet = new HashSet<Node>();
        openSet.add(start);
        start.setgScore(0);
        
        while (!openSet.isEmpty()) {
            Node current = openSet.pollFirst();
            if (current == goal) {
                return reconstructPath(goal, start);
            }
            closedSet.add(current);
            for (Edge edge : current.getOutgoingEdges()) {
                Node neighbor = edge.getTarget();
                if (closedSet.contains(neighbor)) {
                    // we've already been here
                    continue;
                }
                // the distance from start to the neighbor via current node
                double tentativeGScore = current.getgScore() + edge.getWeight();
                
                if (!openSet.contains(neighbor)) {
                    // we've not seen the neighbor yet
                    openSet.add(neighbor);
                } else if (tentativeGScore >= neighbor.getgScore()) {
                    // this is not a better path
                    continue;
                }
                
                // This is the best path yet, record it!
                neighbor.setPredecessor(current);
                neighbor.setgScore(tentativeGScore);
                neighbor.setfScore(tentativeGScore + estimateScore(neighbor, goal));
            }
        }
        return null;
    }


    /**
     * @param neighbor
     * @param goal
     * @return
     */
    private static double estimateScore(final Node neighbor, final Node goal) {
        return neighbor.getPos().distance(goal.getPos());
    }


    /**
     * @param goal
     * @param start
     * @return
     */
    private static List<Node> reconstructPath(final Node goal, final Node start) {
        LinkedList<Node> path = Lists.newLinkedList();
        Node current = goal;
        while (current != start) {
            path.add(current);
            current = current.getPredecessor();
        }
        path.add(start);
        return Lists.reverse(path);
    }
}
