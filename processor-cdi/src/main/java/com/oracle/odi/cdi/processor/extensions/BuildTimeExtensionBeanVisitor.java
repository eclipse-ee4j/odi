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

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.beans.BeanElement;
import io.micronaut.inject.visitor.BeanElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator;

/**
 * A {@link io.micronaut.inject.visitor.BeanElementVisitor} that runs the {@link jakarta.enterprise.inject.build.compatible.spi.Synthesis} phase.
 */
public final class BuildTimeExtensionBeanVisitor implements BeanElementVisitor<Annotation> {
    private BeanElement firstBean;

    @Override
    public BeanElement visitBeanElement(BeanElement beanElement, VisitorContext visitorContext) {
        if (firstBean == null) {
            firstBean = beanElement;
        }
        final BuildTimeExtensionRegistry registry = BuildTimeExtensionRegistry.INSTANCE;
        registry.runRegistration(beanElement, visitorContext);
        return beanElement;
    }

    @Override
    public void finish(VisitorContext visitorContext) {
        ActiveVisitorContext.setVisitorContext(null);
        final BuildTimeExtensionRegistry registry = BuildTimeExtensionRegistry.INSTANCE;
        try {
            if (firstBean != null) {
                final BeanElement beanElement = this.firstBean;
                firstBean = null;
                final SyntheticComponentsImpl syntheticComponents = registry.runSynthesis(
                        beanElement,
                        visitorContext
                );
                if (syntheticComponents != null) {
                    final List<SyntheticBeanBuilderImpl<?>> syntheticBeanBuilders =
                            syntheticComponents.getSyntheticBeanBuilders();
                    for (SyntheticBeanBuilderImpl<?> syntheticBeanBuilder : syntheticBeanBuilders) {
                        final ClassElement beanType = syntheticBeanBuilder.getBeanType();
                        final Class<? extends SyntheticBeanCreator<?>> creatorClass = syntheticBeanBuilder.getCreatorClass();
                        if (creatorClass != null) {
                            final ClassElement creatorElement = visitorContext.getClassElement(creatorClass).orElse(null);
                            if (creatorElement == null) {
                                final String message = "Synthetic bean creator class of type ["
                                        + creatorClass.getName()
                                        + "] must be present on the classpath of the application";
                                visitorContext.fail(message, null);
                            } else {
                                final ElementQuery<MethodElement> creatorMethods = ElementQuery.ALL_METHODS
                                        .named((name) -> name.equals("create"))
                                        .filter((method) -> method.getParameters().length == 2);
                                beanElement.addAssociatedBean(creatorElement, visitorContext).inject()
                                        .produceBeans(
                                                creatorMethods, (builder) -> {
                                                    builder.typed(beanType);
                                                    final Set<String> exposedTypes = syntheticBeanBuilder
                                                            .getExposedTypes();
                                                    if (exposedTypes.isEmpty()) {
                                                        builder.typed(beanType);
                                                    } else {
                                                        for (String exposedType : exposedTypes) {
                                                            visitorContext.getClassElement(exposedType)
                                                                    .ifPresent(builder::typed);
                                                        }
                                                    }
                                                }
                                        );
                            }
                        }
                    }
                }
            }
        } finally {
            firstBean = null;
            registry.stop();
        }
    }
}
