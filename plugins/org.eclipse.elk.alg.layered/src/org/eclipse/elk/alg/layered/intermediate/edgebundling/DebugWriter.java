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

import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.List;

import org.eclipse.elk.alg.layered.intermediate.edgebundling.shortestpath.Edge;
import org.eclipse.elk.alg.layered.intermediate.edgebundling.shortestpath.Node;
import org.eclipse.elk.core.math.KVector;

/**
 * @author Kiel University
 *
 */
public final class DebugWriter {
    private static final double FACTOR = 5.0;
    private static final String PATH = "/home/carsten/tmp/";
    private static final String EXT = ".json";
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.00");

    private DebugWriter() {
    }

    /**
     * @param node
     */
    public static void dump(final Node node, final String fileName, HashSet<Node> nodesToPrint) {

        StringBuilder json = new StringBuilder();

        HashSet<Node> nodes = new HashSet<Node>();
        nodes.add(node);
        collectNodes(node, nodes);
        if (nodesToPrint == null) {
            nodesToPrint = new HashSet<Node>();
            nodesToPrint.addAll(nodes);
        }
        json.append("{id: \"root\", properties:{algorithm : \"elk.alg.fixed\"},\nchildren: [\n");
        for (Node n : nodes) {
            if (nodesToPrint.contains(n)) {
                json.append("{id: \"" + n.hashCode() + "\", width: 50, height: 15, properties:{position:\""
                        + n.getPos().x * FACTOR + ", " + n.getPos().y * FACTOR + "\"}, labels: [ { text: \""
                        + DECIMAL_FORMAT.format(n.getgScore()) + "\"} ]},\n");
            }
        }
        json.append("],\nedges: [\n");
        for (Node n : nodes) {
            if (nodesToPrint.contains(n)) {
                for (Edge e : n.getOutgoingEdges()) {
                    if (nodesToPrint.contains(e.getTarget())) {
                        KVector label =
                                KVector.sum(e.getSource().getPos(), e.getTarget().getPos()).scale(0.5).scale(FACTOR);
                        json.append("{id: \"" + e.hashCode() + "\", source: \"" + e.getSource().hashCode()
                                + "\", target: \"" + e.getTarget().hashCode() + "\", properties: {bendPoints: \""
                                + e.getSource().getPos().x * FACTOR + "," + e.getSource().getPos().y * FACTOR + ","
                                + e.getTarget().getPos().x * FACTOR + "," + e.getTarget().getPos().y * FACTOR
                                + "\"}, labels: [ { text: \"" + DECIMAL_FORMAT.format(e.getWeight()) + "\", x: "
                                + label.x + ", y: " + label.y + "} ]},\n");
                    }
                }
            }
        }
        json.append("]}\n");
        try {
            FileWriter fileWriter = new FileWriter(PATH + fileName + EXT);
            fileWriter.write(json.toString());
            fileWriter.flush();
            fileWriter.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * @param nodes
     */
    public static void dump(final List<Node> nodes, final String fileName) {
        HashSet<Node> nodesToPrint = new HashSet<Node>();
        nodesToPrint.addAll(nodes);
        dump(nodes.get(0), fileName, nodesToPrint);
    }

    /**
     * @param node
     * @param fileName
     */
    public static void dump(final Node node, final String fileName) {
        dump(node, fileName, null);
    }

    /**
     * @param node
     * @param nodes
     */
    private static void collectNodes(final Node node, final HashSet<Node> nodes) {
        for (Edge edge : node.getOutgoingEdges()) {
            nodes.add(edge.getTarget());
            collectNodes(edge.getTarget(), nodes);
        }
    }

}
