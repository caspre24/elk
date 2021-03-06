/*******************************************************************************
 * Copyright (c) 2016 TypeFox GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     spoenemann - initial API and implementation
 *******************************************************************************/
package org.eclipse.elk.core.meta.scoping

import com.google.inject.Inject
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.EReference
import org.eclipse.xtext.scoping.impl.ImportedNamespaceAwareLocalScopeProvider

import static org.eclipse.elk.core.meta.metaData.MetaDataPackage.Literals.*

/**
 * Cross-references that do not target JVM elements must be scoped with the default Xtext means
 * instead of the Xbase scoping.
 */
class MetaDataScopeProvider extends AbstractMetaDataScopeProvider {
    
    @Inject ImportedNamespaceAwareLocalScopeProvider delegate
    
    override getScope(EObject context, EReference reference) {
        switch reference {
            case MD_OPTION_DEPENDENCY__TARGET,
            case MD_OPTION_SUPPORT__OPTION:
                delegate.getScope(context, reference)
            default:
                super.getScope(context, reference)
        }
    }
    
}