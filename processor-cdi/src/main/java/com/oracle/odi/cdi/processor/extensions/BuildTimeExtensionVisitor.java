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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.oracle.odi.cdi.processor.InterceptorVisitor;
import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.beans.BeanElementBuilder;
import io.micronaut.inject.ast.beans.BeanMethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;

/**
 * A {@link io.micronaut.inject.visitor.TypeElementVisitor} that implements the build time extension specification.
 */
@Internal
public final class BuildTimeExtensionVisitor implements TypeElementVisitor<Object, Object> {

    private final BuildTimeExtensionRegistry registry;
    private DiscoveryImpl discovery;
    private final Set<String> supportedAnnotationNames = new HashSet<>();
    private boolean hasErrors;

    private ClassElement rootClassElement = null;
    private boolean finished = false;
    private final Set<ClassElement> interceptorBindings = new HashSet<>();
    private final Set<ClassElement> qualifiers = new HashSet<>();
    private final Set<ClassElement> stereotypes = new HashSet<>();

    /**
     * Default constructor.
     */
    public BuildTimeExtensionVisitor() {
        this.registry = BuildTimeExtensionRegistry.INSTANCE.start();

        this.supportedAnnotationNames.add("jakarta.inject.*");
        this.supportedAnnotationNames.add("jakarta.interceptor.*");
        this.supportedAnnotationNames.add("jakarta.annotation.*");
        this.supportedAnnotationNames.add("jakarta.enterprise.*");
        this.supportedAnnotationNames.add("javax.inject.*");
        this.supportedAnnotationNames.add("javax.annotation.*");
        this.supportedAnnotationNames.add("javax.interceptor.*");
        this.supportedAnnotationNames.add("javax.enterprise.*");

    }

    @Override
    public void start(VisitorContext visitorContext) {
        this.discovery = registry.runDiscovery(visitorContext);
        final MetaAnnotationsImpl metaAnnotations = this.discovery.getMetaAnnotations();
        registeredSupportedAnnotations(metaAnnotations.getInterceptorBindings());
        registeredSupportedAnnotations(metaAnnotations.getQualifiers());
        registeredSupportedAnnotations(metaAnnotations.getStereotypes());
        this.hasErrors = this.discovery.hasErrors();

        ActiveVisitorContext.setVisitorContext(visitorContext);
        if (hasErrors) {
            for (String error : discovery.getMessages().getErrors()) {
                visitorContext.fail(error, null);
            }
        } else {
            finished = false;
            final MetaAnnotationsImpl meta = discovery
                    .getMetaAnnotations();
            buildInterceptorBindingConfig(visitorContext, meta);
            buildDiscoveryConfig(visitorContext, meta.getQualifiers(), this.qualifiers);
            buildDiscoveryConfig(visitorContext, meta.getStereotypes(), this.stereotypes);
        }
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (!hasErrors) {
            if (rootClassElement == null) {
                rootClassElement = element;
            }
            this.registry.runEnhancement(element, element, context);
        }
    }

    @Override
    public void finish(VisitorContext visitorContext) {
        if (!hasErrors && !finished) {
            finished = true;
            if (rootClassElement != null) {
                final Set<String> scannedClassNames = this.discovery
                        .getScannedClasses()
                        .getScannedClassNames();

                for (String scannedClassName : scannedClassNames) {
                    visitorContext.getClassElement(scannedClassName).ifPresent((scannedClass) -> {
                        this.registry.runEnhancement(scannedClass, scannedClass, visitorContext);
                        if (scannedClass.hasAnnotation(Interceptor.class)) {
                            final BeanElementBuilder interceptorBuilder = InterceptorVisitor.addInterceptor(
                                    rootClassElement,
                                    visitorContext,
                                    scannedClass
                            );
                            if (interceptorBuilder != null) {

                                final List<MethodElement> aroundInvokeMethods =
                                        findAroundInvokeMethods(scannedClass);
                                if (!aroundInvokeMethods.isEmpty()) {
                                    if (aroundInvokeMethods.size() == 1) {
                                        final MethodElement methodElement = aroundInvokeMethods.iterator().next();
                                        InterceptorVisitor.setAroundInvokeMethod(
                                                methodElement,
                                                interceptorBuilder
                                        );
                                    } else {
                                        visitorContext.fail("Only a single @AroundInvoke method is allowed", scannedClass);
                                        return;
                                    }
                                }
                            }
                        }

                        final ElementQuery<MethodElement> executableMethods = ElementQuery.ALL_METHODS
                                .onlyDeclared()
                                .onlyDeclared()
                                .onlyConcrete()
                                .annotated((annotationMetadata -> annotationMetadata.hasAnnotation(Executable.class)));
                        rootClassElement
                                .addAssociatedBean(scannedClass)
                                .withMethods(executableMethods, BeanMethodElement::executable)
                                .inject();
                    });
                }
            }
        }
    }

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Collections.unmodifiableSet(supportedAnnotationNames);
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    private void buildDiscoveryConfig(VisitorContext visitorContext,
                                      Set<Class<? extends Annotation>> annotationClassConfigs,
                                      Set<ClassElement> targetElements) {
        for (Class<? extends Annotation> annotationConfig : annotationClassConfigs) {
            visitorContext.getClassElement(annotationConfig)
                    .ifPresent(targetElements::add);
        }
    }

    private void buildInterceptorBindingConfig(VisitorContext visitorContext, MetaAnnotationsImpl meta) {
        final Set<Class<? extends Annotation>> interceptorBindings = meta
                .getInterceptorBindings();

        buildDiscoveryConfig(visitorContext, interceptorBindings, this.interceptorBindings);
    }

    private void registeredSupportedAnnotations(Set<Class<? extends Annotation>> interceptorBindings) {
        for (Class<? extends Annotation> interceptorBinding : interceptorBindings) {
            this.supportedAnnotationNames.add(interceptorBinding.getName());
        }
    }

    private List<MethodElement> findAroundInvokeMethods(ClassElement scannedClass) {
        return scannedClass
                .getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared()
                                             .annotated((metadata) -> metadata.hasDeclaredAnnotation(AroundInvoke.class)));
    }

}
