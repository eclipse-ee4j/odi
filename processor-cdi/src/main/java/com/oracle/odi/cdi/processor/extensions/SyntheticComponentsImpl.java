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
package com.oracle.odi.cdi.processor.extensions;

import java.util.ArrayList;
import java.util.List;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticComponents;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticObserverBuilder;
import jakarta.enterprise.lang.model.types.Type;

final class SyntheticComponentsImpl implements SyntheticComponents {
    private final VisitorContext visitorContext;
    private final List<SyntheticBeanBuilderImpl<?>> syntheticBeanBuilders = new ArrayList<>();

    SyntheticComponentsImpl(VisitorContext visitorContext) {
        this.visitorContext = visitorContext;
    }

    public List<SyntheticBeanBuilderImpl<?>> getSyntheticBeanBuilders() {
        return syntheticBeanBuilders;
    }

    @Override
    public <T> SyntheticBeanBuilder<T> addBean(Class<T> implementationClass) {
        final ClassElement classElement = visitorContext.getClassElement(implementationClass)
                .orElseThrow(() -> new IllegalStateException(
                        "Synthetic bean implementation class must be on the classpath of the application: " + implementationClass
                                .getName()));
        final SyntheticBeanBuilderImpl<T> builder = new SyntheticBeanBuilderImpl<>(classElement,
                                                                                   new TypesImpl(visitorContext),
                                                                                 visitorContext);
        syntheticBeanBuilders.add(builder);
        return builder;
    }

    @Override
    public <T> SyntheticObserverBuilder<T> addObserver(Class<T> eventType) {
        // TODO
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> SyntheticObserverBuilder<T> addObserver(Type eventType) {
        // TODO
        throw new UnsupportedOperationException();
    }
}
