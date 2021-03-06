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

import io.micronaut.context.annotation.Primary;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.beans.BeanElement;
import io.micronaut.inject.ast.beans.BeanElementBuilder;
import io.micronaut.inject.ast.beans.BeanMethodElement;
import io.micronaut.inject.visitor.BeanElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.build.compatible.spi.Parameters;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanDisposer;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticObserver;
import jakarta.enterprise.inject.spi.EventContext;
import jakarta.enterprise.util.Nonbinding;
import jakarta.inject.Singleton;
import org.eclipse.odi.cdi.annotation.meta.RuntimeMetaAnnotation;
import org.eclipse.odi.cdi.processor.CdiUtil;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A {@link io.micronaut.inject.visitor.BeanElementVisitor} that runs the {@link jakarta.enterprise.inject.build.compatible.spi.Synthesis} phase.
 */
public final class BuildTimeExtensionBeanVisitor implements BeanElementVisitor<Annotation> {
    private BeanElement applicationClassElement;

    @Override
    public BeanElement visitBeanElement(BeanElement beanElement, VisitorContext visitorContext) {
        final BuildTimeExtensionRegistry registry = BuildTimeExtensionRegistry.getInstance();
        boolean isApplicationClass = beanElement.hasDeclaredAnnotation("org.eclipse.odi.cdi.annotation.OdiApplication");
        registry.runRegistration(beanElement, visitorContext);
        if (applicationClassElement == null || isApplicationClass) {
            applicationClassElement = beanElement;
        }
        return beanElement;
    }

    @Override
    public void finish(VisitorContext visitorContext) {
        ActiveVisitorContext.setVisitorContext(null);
        final BuildTimeExtensionRegistry registry = BuildTimeExtensionRegistry.getInstance();
        try {
            if (applicationClassElement != null) {
                final SyntheticComponentsImpl syntheticComponents =
                        registry.runSynthesis(applicationClassElement, visitorContext);
                final DiscoveryImpl discovery = registry.getDiscovery();
                if (discovery != null) {
                    addSyntheticAnnotations(syntheticComponents, discovery);
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
                            defineSyntheticCreator(visitorContext, syntheticBeanBuilder, beanType, creatorElement);
                            Class<? extends SyntheticBeanDisposer<?>> disposerClass = syntheticBeanBuilder.getDisposerClass();
                            defineSyntheticDisposer(visitorContext, syntheticBeanBuilder, disposerClass, beanType);
                        }
                    }
                }

                List<SyntheticObserverBuilderImpl<?>> syntheticObserverBuilders = syntheticComponents.getSyntheticObserverBuilders();
                for (SyntheticObserverBuilderImpl<?> syntheticObserverBuilder : syntheticObserverBuilders) {
                    ClassElement eventType = syntheticObserverBuilder.getEventType();
                    ClassElement observerClass = syntheticObserverBuilder.getObserverClass();
                    if (eventType != null && observerClass != null) {
                        ClassElement syntheticObserver = visitorContext.getClassElement(SyntheticObserver.class)
                                .orElse(ClassElement.of(SyntheticObserver.class));

                        ElementQuery<MethodElement> notifyMethodFilter = ElementQuery.ALL_METHODS
                                .onlyInstance()
                                .named(n -> n.equals("observe"))
                                .filter(m -> m.getParameters().length == 2 &&
                                        m.getParameters()[0].getType().isAssignable(EventContext.class) &&
                                        m.getParameters()[1].getType().isAssignable(Parameters.class)
                                );
                        BeanElementBuilder observerBuilder = applicationClassElement
                                .addAssociatedBean(observerClass, visitorContext)
                                .typed(syntheticObserver)
                                .typeArgumentsForType(syntheticObserver, eventType)
                                .withMethods(notifyMethodFilter, m -> {
                                        m.executable(true);
                                        m.annotate("org.eclipse.odi.cdi.annotation.ObservesMethod");
                                    }
                                );
                        copySyntheticAnnotationMetadata(visitorContext, syntheticObserverBuilder.getAnnotationMetadata(), observerBuilder);
                    }
                }
            }
            registry.runValidation(visitorContext);
        } finally {
            applicationClassElement = null;
            registry.stop();
        }
    }

    private void defineSyntheticDisposer(VisitorContext visitorContext, SyntheticBeanBuilderImpl<?> syntheticBeanBuilder, Class<? extends SyntheticBeanDisposer<?>> disposerClass, ClassElement beanType) {
        if (disposerClass != null) {
            ClassElement disposerElement = visitorContext.getClassElement(disposerClass).orElse(null);
            if (disposerElement == null) {
                final String message = "Synthetic bean disposer class of type ["
                        + disposerClass.getName()
                        + "] must be present on the classpath of the application";
                visitorContext.fail(message, null);
            } else {
                if (disposerElement.isAbstract()) {
                    final String message = "Synthetic bean disposer class of type ["
                            + disposerClass.getName()
                            + "] cannot be abstract";
                    visitorContext.fail(message, null);
                } else {
                    Map<String, ClassElement> classElement = disposerElement.getTypeArguments(SyntheticBeanDisposer.class);
                    if (classElement.size() != 1 || !beanType.getName().equals(classElement.values().iterator().next().getName())) {
                        final String message = "Synthetic bean disposer class of type ["
                                + disposerClass.getName()
                                + "] does not specific a generic type argument that matches the bean type: " + beanType.getName();
                        visitorContext.fail(message, null);
                        return;
                    }

                    ClassElement disposerInterface = visitorContext.getClassElement(SyntheticBeanDisposer.class)
                            .orElse(ClassElement.of(SyntheticBeanDisposer.class));
                    BeanElementBuilder disposerBuilder = applicationClassElement.addAssociatedBean(disposerElement, visitorContext)
                            .typed(disposerInterface)
                            .typeArgumentsForType(disposerInterface, beanType);
                    copySyntheticAnnotationMetadata(
                            visitorContext,
                            syntheticBeanBuilder.getAnnotationMetadata(),
                            disposerBuilder
                    );
                    ElementQuery<MethodElement> disposeMethod =
                            ElementQuery.ALL_METHODS
                                    .onlyInstance()
                                    .named(n -> n.equals("dispose"))
                                    .filter(m -> m.getParameters().length == 3);
                    disposerBuilder.withMethods(disposeMethod, BeanMethodElement::executable);
                }
            }
        }
    }

    private void addSyntheticAnnotations(SyntheticComponentsImpl syntheticComponents, DiscoveryImpl discovery) {
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

    private void defineSyntheticCreator(VisitorContext visitorContext, SyntheticBeanBuilderImpl<?> syntheticBeanBuilder, ClassElement beanType, ClassElement creatorElement) {
        MutableAnnotationMetadata syntheticBeanMetadata = syntheticBeanBuilder.getAnnotationMetadata();
        final ElementQuery<MethodElement> creatorMethods = ElementQuery.ALL_METHODS
                .named(name -> name.equals("create"))
                .filter(method -> method.getParameters().length == 2);


        BeanElementBuilder beanFactory = applicationClassElement.addAssociatedBean(creatorElement, visitorContext)
                .inject()
                .produceBeans(
                        creatorMethods, builder -> {
                            builder.typed(beanType);

                            copySyntheticAnnotationMetadata(visitorContext, syntheticBeanMetadata, builder);
                            final Set<ClassElement> exposedTypes = syntheticBeanBuilder
                                    .getExposedTypes();
                            if (!exposedTypes.isEmpty()) {
                                builder.typed(exposedTypes.toArray(ClassElement.ZERO_CLASS_ELEMENTS));
                            }
                        }
                );

        copyQualifiersToFactory(syntheticBeanMetadata, beanFactory);
    }

    private void copyQualifiersToFactory(MutableAnnotationMetadata syntheticBeanMetadata, BeanElementBuilder beanFactory) {
        List<AnnotationValue<Annotation>> qualifiers = syntheticBeanMetadata
                .getAnnotationNamesByStereotype(AnnotationUtil.QUALIFIER)
                .stream()
                .map(syntheticBeanMetadata::getAnnotation)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (CollectionUtils.isNotEmpty(qualifiers)) {
            for (AnnotationValue<Annotation> qualifier : qualifiers) {
                beanFactory.qualifier(qualifier);
            }
        } else {
            beanFactory.qualifier(AnnotationValue.builder(Primary.class).build());
        }
    }

    private void copySyntheticAnnotationMetadata(VisitorContext visitorContext, MutableAnnotationMetadata syntheticBeanMetadata, BeanElementBuilder builder) {
        Set<String> annotationNames = syntheticBeanMetadata.getAnnotationNames();
        for (String annotationName : annotationNames) {
            AnnotationValue<Annotation> av = syntheticBeanMetadata.getAnnotation(annotationName);
            if (av != null) {
                builder.annotate(av);
            }
        }
        CdiUtil.visitBeanDefinition(visitorContext, builder);
    }
}
