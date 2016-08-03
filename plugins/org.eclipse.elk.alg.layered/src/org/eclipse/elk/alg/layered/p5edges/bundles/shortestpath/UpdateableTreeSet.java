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

import java.util.TreeSet;

/**
 * @author Kiel University
 * @param <E>
 * @param <T>
 *
 */
public class UpdateableTreeSet<E extends Updatable<T>, T> extends TreeSet<E> {
    
    private static final long serialVersionUID = 4499716313777343916L;

    /**
     * @param e
     * @return
     */
    public boolean update(final E e, final T newValue) {
        if (remove(e)) {
            e.update(newValue);
            return add(e);
        } else {
            return false;
        }
    }
}
