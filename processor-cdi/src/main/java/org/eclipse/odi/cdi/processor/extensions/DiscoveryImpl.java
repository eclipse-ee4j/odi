/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.odi.cdi.processor.extensions;

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.visitor.VisitorContext;

@Internal
final class DiscoveryImpl {

    private final DiscoveryMessagesImpl messages = new DiscoveryMessagesImpl();
    private final MetaAnnotationsImpl metaAnnotations;
    private final ScannedClassesImpl scannedClasses = new ScannedClassesImpl();

    public DiscoveryImpl(VisitorContext visitorContext) {
        this.metaAnnotations = new MetaAnnotationsImpl(visitorContext);
    }

    public MetaAnnotationsImpl getMetaAnnotations() {
        return metaAnnotations;
    }

    public ScannedClassesImpl getScannedClasses() {
        return scannedClasses;
    }

    public DiscoveryMessagesImpl getMessages() {
        return messages;
    }

    public boolean hasErrors() {
        return !messages.getErrors().isEmpty();
    }
}
