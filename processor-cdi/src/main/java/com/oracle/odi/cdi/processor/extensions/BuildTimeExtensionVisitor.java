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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.oracle.odi.cdi.processor.visitors.InterceptorVisitor;
import com.oracle.odi.cdi.processor.visitors.ObservesMethodVisitor;
import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.beans.BeanElementBuilder;
import io.micronaut.inject.ast.beans.BeanMethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.context.NormalScope;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Qualifier;
import jakarta.inject.Scope;
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
    private Set<String> beanPackages = new HashSet<>();

    /**
     * Default constructor.
     */
    public BuildTimeExtensionVisitor() {
        this.registry = BuildTimeExtensionRegistry.getInstance().start();

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
        this.beanPackages = resolveBeanPackages(visitorContext);

        ActiveVisitorContext.setVisitorContext(visitorContext);
        if (hasErrors) {
            for (String error : discovery.getMessages().getErrors()) {
                visitorContext.fail(error, null);
            }
        } else {
            finished = false;
            final MetaAnnotationsImpl meta = discovery
                    .getMetaAnnotations();
            buildInterceptorBindingConfig(meta);
            buildDiscoveryConfig(meta.getQualifiers(), this.qualifiers);
            buildDiscoveryConfig(meta.getStereotypes(), this.stereotypes);
        }
    }

    @Override
    public Set<String> getSupportedOptions() {
        return Collections.singleton("micronaut.cdi.bean.packages");
    }

    private Set<String> resolveBeanPackages(VisitorContext visitorContext) {
        final String packageNames = visitorContext.getOptions().get("micronaut.cdi.bean.packages");
        if (packageNames != null) {
            return CollectionUtils.setOf(packageNames.split(","));
        }
        return Collections.emptySet();
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
                if (CollectionUtils.isNotEmpty(beanPackages)) {
                    for (String beanPackage : beanPackages) {
                        final ClassElement[] classElements = visitorContext.getClassElements(beanPackage,
                                                                                             Interceptor.class.getName(),
                                                                                             Scope.class.getName(),
                                                                                             Qualifier.class.getName(),
                                                                                             NormalScope.class.getName(),
                                                                                             AnnotationUtil.SCOPE,
                                                                                             AnnotationUtil.QUALIFIER);
                        for (ClassElement classElement : classElements) {
                            if (!scannedClassNames.contains(classElement.getName())) {
                                handleScannedClass(visitorContext, classElement);
                            }
                        }
                    }
                }

                for (String scannedClassName : scannedClassNames) {
                    visitorContext.getClassElement(scannedClassName).ifPresent(scannedClass -> {
                        handleScannedClass(visitorContext, scannedClass);
                    });
                }
            }
        }
    }

    private void handleScannedClass(VisitorContext visitorContext, ClassElement scannedClass) {
        this.registry.runEnhancement(scannedClass, scannedClass, visitorContext);
        if (scannedClass.hasAnnotation(Interceptor.class)) {
            final BeanElementBuilder interceptorBuilder = InterceptorVisitor.addInterceptor(
                    rootClassElement,
                    visitorContext,
                    scannedClass
            );
        }

        final ElementQuery<MethodElement> executableMethods = ElementQuery.ALL_METHODS
                .onlyInstance()
                .onlyDeclared()
                .onlyConcrete()
                .annotated((annotationMetadata -> annotationMetadata.hasAnnotation(Executable.class)));
        final Predicate<MethodElement> isObservesMethod = m ->
                m.hasParameters() &&
                        Arrays.stream(m.getParameters())
                                .anyMatch(p -> p.hasDeclaredAnnotation(Observes.class) || p.hasDeclaredAnnotation(ObservesAsync.class)
                                );
        final ElementQuery<MethodElement> observesMethods = ElementQuery.ALL_METHODS
                .onlyInstance()
                .filter(isObservesMethod);
        final ElementQuery<MethodElement> producesMethods = ElementQuery.ALL_METHODS
                .onlyInstance()
                .annotated(ann -> ann.hasDeclaredAnnotation(Produces.class));
        final ElementQuery<FieldElement> producesFields = ElementQuery.ALL_FIELDS
                .onlyInstance()
                .annotated(ann -> ann.hasDeclaredAnnotation(Produces.class));
        final Consumer<BeanElementBuilder> producesConfigurer = builder -> {
            final Element producingElement = builder.getProducingElement();
            final Set<String> annotationNames = producingElement.getDeclaredAnnotationNames();
            for (String annotationName : annotationNames) {
                final AnnotationValue<Annotation> av = producingElement.getAnnotation(annotationName);
                if (av != null && !av.getAnnotationName().equals(Produces.class.getName())) {
                    builder.annotate(av);
                }
            }
        };
        final BeanElementBuilder beanElementBuilder = rootClassElement
                .addAssociatedBean(scannedClass);
        registry.runDiscoveryEnhancements(beanElementBuilder);
        beanElementBuilder.withMethods(executableMethods, BeanMethodElement::executable)
                .withMethods(observesMethods, observesMethod -> {
                    if (isObservesMethod.test(observesMethod)) {
                        observesMethod.executable();
                        observesMethod.annotate(Executable.class, builder ->
                                builder.member("processOnStartup", true)
                        );
                        ObservesMethodVisitor.handleObservesMethod(
                                scannedClass,
                                observesMethod,
                                visitorContext
                        );
                    }
                })
                .produceBeans(producesMethods, producesConfigurer)
                .produceBeans(producesFields, producesConfigurer)
                .inject();

    }

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Collections.unmodifiableSet(supportedAnnotationNames);
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    private void buildDiscoveryConfig(Set<MetaAnnotationImpl> annotationClassConfigs,
                                      Set<ClassElement> targetElements) {
        for (MetaAnnotationImpl metaAnn : annotationClassConfigs) {
            targetElements.add(metaAnn.getClassConfig().getElement());
        }
    }

    private void buildInterceptorBindingConfig(MetaAnnotationsImpl meta) {

        buildDiscoveryConfig(meta.getInterceptorBindings(), this.interceptorBindings);
    }

    private void registeredSupportedAnnotations(Set<MetaAnnotationImpl> interceptorBindings) {
        for (MetaAnnotationImpl interceptorBinding : interceptorBindings) {
            this.supportedAnnotationNames.add(interceptorBinding.getClassConfig().info().name());
        }
    }

}
