/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.build.compatible.spi.InvokerValidation;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.function.Supplier;

final class InvokerValidationImpl implements InvokerValidation {
    private static final String REACTIVE_STREAMS_PUBLISHER = "org.reactivestreams.Publisher";

    private final VisitorContext visitorContext;
    private final BuildTimeExtensionRegistry registry;

    InvokerValidationImpl(VisitorContext visitorContext, BuildTimeExtensionRegistry registry) {
        this.visitorContext = visitorContext;
        this.registry = registry;
    }

    @Override
    public void ensureAsyncHandlerExists(Class<?> returnType, Supplier<String> errorMessage) {
        if (returnType == CompletionStage.class
                || returnType == CompletableFuture.class
                || returnType == Flow.Publisher.class
                || returnType.getName().equals(REACTIVE_STREAMS_PUBLISHER)) {
            return;
        }
        if (registry.hasAsyncHandler(visitorContext, returnType.getName())) {
            return;
        }
        visitorContext.fail(errorMessage.get(), null);
    }
}
