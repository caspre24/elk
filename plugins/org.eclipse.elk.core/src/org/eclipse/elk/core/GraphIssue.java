/*******************************************************************************
 * Copyright (c) 2016 TypeFox GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    spoenemann - initial API and implementation
 *******************************************************************************/
package org.eclipse.elk.core;

import org.eclipse.elk.graph.KGraphElement;

/**
 * Descriptor of an issue found while validating the structure or the configuration of a graph.
 */
public class GraphIssue {

    /** Enumeration of issue severities. */
    public static enum Severity {
        /** An error means the layout process is aborted and the error is reported to the user. */
        ERROR,
        /** A graph with warnings but no errors can still be processed. */
        WARNING;
        
        /**
         * Returns a user-friendly string for this severity.
         */
        public String getUserString() {
            switch (this) {
                case ERROR:
                    return "Error";
                case WARNING:
                    return "Warning";
                default:
                    throw new IllegalStateException("Missing case for " + this);
            }
        }
    }

    /** The graph element to which the issue applies. */
    private final KGraphElement element;

    /** A message to be shown to users. */
    private final String message;

    /** The severity of the issue. */
    private final Severity severity;

    /**
     * Create a graph issue.
     * 
     * @param element
     *            The graph element to which the issue applies; may be {@code null}
     * @param message
     *            A message to be shown to users
     * @param severity
     *            The severity of the issue
     */
    public GraphIssue(final KGraphElement element, final String message, final Severity severity) {
        if (message == null || severity == null) {
            throw new NullPointerException();
        }
        this.element = element;
        this.message = message;
        this.severity = severity;
    }

    /**
     * The graph element to which the issue applies. May be {@code null}.
     */
    public KGraphElement getElement() {
        return element;
    }

    /**
     * A message to be shown to users.
     */
    public String getMessage() {
        return message;
    }

    /**
     * The severity of the issue.
     */
    public Severity getSeverity() {
        return severity;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof GraphIssue) {
            GraphIssue other = (GraphIssue) obj;
            return this.element == other.element
                    && this.message.equals(other.message)
                    && this.severity == other.severity;
        }
        return false;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return element.hashCode() ^ message.hashCode() ^ severity.hashCode();
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return severity.toString() + ": " + message + " (" + element + ")";
    }

}
