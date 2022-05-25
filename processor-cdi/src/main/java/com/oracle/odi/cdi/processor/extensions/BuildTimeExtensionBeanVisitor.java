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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.oracle.odi.cdi.annotation.meta.RuntimeMetaAnnotation;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.beans.BeanElement;
import io.micronaut.inject.visitor.BeanElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator;
import jakarta.enterprise.util.Nonbinding;
import jakarta.inject.Singleton;

/**
 * A {@link io.micronaut.inject.visitor.BeanElementVisitor} that runs the {@link jakarta.enterprise.inject.build.compatible.spi.Synthesis} phase.
 */
public final class BuildTimeExtensionBeanVisitor implements BeanElementVisitor<Annotation> {
    private BeanElement firstBean;

    @Override
    public BeanElement visitBeanElement(BeanElement beanElement, VisitorContext visitorContext) {
        final BuildTimeExtensionRegistry registry = BuildTimeExtensionRegistry.getInstance();

        registry.runRegistration(beanElement, visitorContext);
        if (firstBean == null) {
            firstBean = beanElement;
        }
        return beanElement;
    }

    @Override
    public void finish(VisitorContext visitorContext) {
        ActiveVisitorContext.setVisitorContext(null);
        final BuildTimeExtensionRegistry registry = BuildTimeExtensionRegistry.getInstance();
        try {
            if (firstBean != null) {
                final SyntheticComponentsImpl syntheticComponents =
                        registry.runSynthesis(firstBean, visitorContext);
                final DiscoveryImpl discovery = registry.getDiscovery();
                if (discovery != null) {
                    final MetaAnnotationsImpl metaAnnotations = discovery.getMetaAnnotations();
                    final Set<MetaAnnotationImpl> qualifiers = metaAnnotations.getQualifiers();
                    for (MetaAnnotationImpl qualifier : qualifiers) {
                        Set<String> nonBinding = new HashSet<>();
                        qualifier.getElement().getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance().onlyDeclared())
                                .forEach(m -> {
                                    if (m.hasAnnotation(Nonbinding.class)) {
                                        nonBinding.add(m.getName());
                                    }
                                });
                        syntheticComponents.addBean(RuntimeMetaAnnotation.class)
                                .type(RuntimeMetaAnnotation.class)
                                .scope(Singleton.class)
                                .withParam("annotationType", qualifier.getClassConfig().info())
                                .withParam("nonBinding", nonBinding.toArray(String[]::new))
                                .withParam("kind", RuntimeMetaAnnotation.MetaAnnotationKind.QUALIFIER)
                                .createWith(RuntimeMetaAnnotation.Creator.class);

                    }
                }

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
                                    .named(name -> name.equals("create"))
                                    .filter(method -> method.getParameters().length == 2);
                            firstBean.addAssociatedBean(creatorElement, visitorContext).inject()
                                    .produceBeans(
                                            creatorMethods, builder -> {
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
            registry.runValidation(visitorContext);
        } finally {
            firstBean = null;
            registry.stop();
        }
    }
}
