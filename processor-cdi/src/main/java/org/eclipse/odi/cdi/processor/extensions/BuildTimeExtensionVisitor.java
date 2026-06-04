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

import io.micronaut.aop.Around;
import io.micronaut.aop.InterceptorBindingDefinitions;
import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementModifier;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.beans.BeanElementBuilder;
import io.micronaut.inject.ast.beans.BeanMethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.runtime.context.scope.ScopedProxy;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.NormalScope;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Qualifier;
import jakarta.inject.Scope;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import org.eclipse.odi.cdi.processor.CdiUtil;
import org.eclipse.odi.cdi.processor.transformers.InterceptorBindingTransformer;
import org.eclipse.odi.cdi.processor.visitors.DisposesMethodVisitor;
import org.eclipse.odi.cdi.processor.visitors.InjectVisitor;
import org.eclipse.odi.cdi.processor.visitors.InterceptorVisitor;
import org.eclipse.odi.cdi.processor.visitors.ObservesAsyncMethodVisitor;
import org.eclipse.odi.cdi.processor.visitors.ObservesMethodVisitor;

import java.lang.annotation.Annotation;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A {@link io.micronaut.inject.visitor.TypeElementVisitor} that implements the build time extension specification.
 */
@Internal
public final class BuildTimeExtensionVisitor implements TypeElementVisitor<Object, Object> {

    private final BuildTimeExtensionRegistry registry;
    private DiscoveryImpl discovery;
    private final Set<String> supportedAnnotationNames = new HashSet<>();
    private boolean hasErrors;

    private ClassElement applicationClassElement = null;
    private boolean finished = false;
    private final Set<ClassElement> interceptorBindings = new HashSet<>();
    private final Set<ClassElement> qualifiers = new HashSet<>();
    private final Set<ClassElement> stereotypes = new HashSet<>();
    private final Set<ClassElement> contexts = new HashSet<>();
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
            this.contexts.addAll(meta.getContexts());
        }
    }

    @Override
    public Set<String> getSupportedOptions() {
        return Set.of("micronaut.cdi.bean.packages", CdiUtil.BEAN_CLASSES_OPTION, CdiUtil.BUILD_COMPATIBLE_EXTENSIONS_OPTION);
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
            boolean isApplicationClass = element.hasDeclaredAnnotation("org.eclipse.odi.cdi.annotation.OdiApplication");
            if (applicationClassElement == null || isApplicationClass) {
                applicationClassElement = element;
            }
            if (!isApplicationClass) {
                this.registry.runEnhancement(element, element, context);
            }
        }
    }

    @Override
    public void finish(VisitorContext visitorContext) {
        if (!hasErrors && !finished) {
            finished = true;
            registry.validateAsyncHandlers(visitorContext);
            if (applicationClassElement != null) {
                final Set<String> scannedClassNames = this.discovery
                        .getScannedClasses()
                        .getScannedClassNames();
                final Set<String> handledClassNames = new HashSet<>();
                if (CollectionUtils.isNotEmpty(beanPackages)) {
                    for (String beanPackage : beanPackages) {
                        for (ClassElement interceptorBinding : visitorContext.getClassElements(beanPackage, InterceptorBinding.class.getName())) {
                            configureInterceptorBinding(interceptorBinding);
                        }
                        final ClassElement[] classElements = visitorContext.getClassElements(beanPackage,
                                                                                             Interceptor.class.getName(),
                                                                                             Scope.class.getName(),
                                                                                             Qualifier.class.getName(),
                                                                                             NormalScope.class.getName(),
                                                                                             AnnotationUtil.SCOPE,
                                                                                             AnnotationUtil.QUALIFIER);
                        for (ClassElement classElement : classElements) {
                            if (!scannedClassNames.contains(classElement.getName()) && handledClassNames.add(classElement.getName())) {
                                handleScannedClass(visitorContext, classElement);
                            }
                        }
                    }
                }
                for (ClassElement context : contexts) {
                    if (!scannedClassNames.contains(context.getName()) && handledClassNames.add(context.getName())) {
                        handleScannedClass(visitorContext, context);
                    }
                }

                for (String scannedClassName : scannedClassNames) {
                    if (handledClassNames.add(scannedClassName)) {
                        visitorContext.getClassElement(scannedClassName).ifPresent(scannedClass -> {
                            handleScannedClass(visitorContext, scannedClass);
                        });
                    }
                }
            }
        }
    }

    private void configureInterceptorBinding(ClassElement interceptorBinding) {
        if (!interceptorBinding.hasDeclaredAnnotation(InterceptorBinding.class)) {
            return;
        }
        for (AnnotationValue<?> av : InterceptorBindingTransformer.INTERCEPTOR_BINDING_VALUES) {
            interceptorBinding.annotate(AnnotationValue.builder(av, RetentionPolicy.RUNTIME)
                    .member("value", new AnnotationClassValue<>(interceptorBinding.getName()))
                    .build());
        }
        this.interceptorBindings.add(interceptorBinding);
        this.supportedAnnotationNames.add(interceptorBinding.getName());
    }

    private void handleScannedClass(VisitorContext visitorContext, ClassElement scannedClass) {
        if (isVetoed(scannedClass)) {
            return;
        }
        configureInterceptorBindings(visitorContext, scannedClass);
        this.registry.runEnhancement(scannedClass, scannedClass, visitorContext);
        boolean isInterceptor = scannedClass.hasAnnotation(Interceptor.class);
        if (isInterceptor) {
            InterceptorVisitor.addInterceptor(
                    applicationClassElement,
                    visitorContext,
                    scannedClass
            );
        }

        ElementQuery<FieldElement> instanceFields = ElementQuery.ALL_FIELDS
                .onlyInstance();
        ElementQuery<MethodElement> instanceMethods = ElementQuery.ALL_METHODS
                .onlyInstance();
        ElementQuery<MethodElement> invokableMethods = ElementQuery.ALL_METHODS
                .onlyDeclared()
                .onlyConcrete()
                .modifiers(modifiers -> !modifiers.contains(ElementModifier.PRIVATE));
        ElementQuery<MethodElement> staticInvokableMethods = ElementQuery.ALL_METHODS
                .onlyStatic()
                .onlyDeclared()
                .onlyConcrete()
                .modifiers(modifiers -> !modifiers.contains(ElementModifier.PRIVATE));


        final ElementQuery<MethodElement> executableMethods = instanceMethods
                .onlyDeclared()
                .onlyConcrete()
                .annotated((annotationMetadata -> annotationMetadata.hasAnnotation(Executable.class)));
        final ElementQuery<MethodElement> interceptedMethods = instanceMethods
                .onlyDeclared()
                .onlyConcrete()
                .modifiers(m -> m.contains(ElementModifier.PUBLIC) && !m.contains(ElementModifier.FINAL))
                .filter(method -> hasInterceptorBinding(visitorContext, method));
        final Predicate<MethodElement> isObservesMethod = m ->
                m.hasParameters() &&
                        Arrays.stream(m.getParameters())
                                .anyMatch(p -> p.hasDeclaredAnnotation(Observes.class) || p.hasDeclaredAnnotation(ObservesAsync.class)
                                );
        final Predicate<MethodElement> isDisposesMethod = m ->
                m.hasParameters() &&
                        Arrays.stream(m.getParameters())
                                .anyMatch(p -> p.hasDeclaredAnnotation(Disposes.class));
        final ElementQuery<MethodElement> observesMethods = instanceMethods
                .filter(isObservesMethod);
        final ElementQuery<MethodElement> disposesMethods = instanceMethods
                .onlyDeclared()
                .onlyConcrete()
                .filter(isDisposesMethod);
        final ElementQuery<MethodElement> staticDisposesMethods = ElementQuery.ALL_METHODS
                .onlyDeclared()
                .modifiers(modifiers -> modifiers.contains(ElementModifier.STATIC))
                .filter(isDisposesMethod);
        final ElementQuery<MethodElement> producesMethods = ElementQuery.ALL_METHODS
                .annotated(ann -> ann.hasDeclaredAnnotation(Produces.class));
        final ElementQuery<FieldElement> producesFields = ElementQuery.ALL_FIELDS
                .annotated(ann -> ann.hasDeclaredAnnotation(Produces.class));
        final Consumer<BeanElementBuilder> producesConfigurer = builder -> {
            final Element producingElement = builder.getProducingElement();
            final Set<String> annotationNames = producingElement.getDeclaredAnnotationNames();
            for (String annotationName : annotationNames) {
                final AnnotationValue<Annotation> av = producingElement.getAnnotation(annotationName);
                if (av != null
                        && !av.getAnnotationName().equals(Produces.class.getName())
                        && !av.getAnnotationName().equals(Executable.class.getName())) {
                    builder.annotate(av);
                }
            }
            if (producingElement instanceof MemberElement) {
                propagateAlternativeMetadata(((MemberElement) producingElement).getDeclaringType(), builder);
            }
        };
        BeanElementBuilder beanElementBuilder = applicationClassElement
                .addAssociatedBean(scannedClass);
        copyRegisteredQualifiers(scannedClass, beanElementBuilder);
        registry.runDiscoveryEnhancements(beanElementBuilder);
        CdiUtil.visitBeanDefinition(visitorContext, beanElementBuilder);
        registry.runDiscoveryEnhancements(beanElementBuilder);
        if (isNormalScopedBean(visitorContext, scannedClass)) {
            beanElementBuilder.intercept(AnnotationValue.builder(Around.class)
                    .member("proxyTarget", true)
                    .member("lazy", true)
                    .build());
            beanElementBuilder.annotate(ScopedProxy.class);
            beanElementBuilder.annotate(AnnotationUtil.SCOPE);
        }
        if (!isInterceptor && !scannedClass.isFinal()) {
            if (hasInterceptorBinding(visitorContext, scannedClass)) {
                beanElementBuilder.intercept();
            }
            beanElementBuilder = beanElementBuilder.withMethods(interceptedMethods, BeanMethodElement::intercept);
        }
        InjectVisitor injectVisitor = new InjectVisitor();
        scannedClass.getEnclosedElements(instanceMethods.onlyInjected())
                        .forEach(m -> injectVisitor.visitMethod(m, visitorContext));
        scannedClass.getEnclosedElements(instanceFields.onlyInjected())
                        .forEach(m -> injectVisitor.visitField(m, visitorContext));
        DisposesMethodVisitor disposesVisitor = new DisposesMethodVisitor();
        disposesVisitor.visitClass(scannedClass, visitorContext);
        scannedClass.getEnclosedElements(disposesMethods)
                .forEach(m -> disposesVisitor.visitMethod(m, visitorContext));
        scannedClass.getEnclosedElements(staticDisposesMethods)
                .forEach(m -> disposesVisitor.visitMethod(m, visitorContext));
        beanElementBuilder
                .withMethods(observesMethods, observesMethod -> {
                    if (isObservesMethod.test(observesMethod)) {
                        observesMethod.executable(true);
                        handleObservesMethod(
                                scannedClass,
                                observesMethod,
                                visitorContext
                        );
                    }
                })
                .withMethods(invokableMethods, BeanMethodElement::executable)
                .withMethods(staticInvokableMethods, BeanMethodElement::executable)
                .withMethods(executableMethods, BeanMethodElement::executable)
                .produceBeans(producesMethods, producesConfigurer)
                .produceBeans(producesFields, producesConfigurer)
                .inject();
    }

    private boolean isVetoed(ClassElement scannedClass) {
        return scannedClass.hasAnnotation(io.micronaut.core.annotation.Vetoed.class)
                || scannedClass.hasAnnotation(jakarta.enterprise.inject.Vetoed.class);
    }

    @SuppressWarnings("unchecked")
    private void propagateAlternativeMetadata(ClassElement declaringType, BeanElementBuilder builder) {
        if (!declaringType.hasAnnotation(Alternative.class) && !declaringType.hasStereotype(Alternative.class)) {
            return;
        }
        builder.annotate(Alternative.class);
        OptionalInt priority = declaringType.intValue(Priority.class);
        if (priority.isPresent()) {
            int value = priority.getAsInt();
            builder.annotate(Priority.class, annotation -> annotation.value(value));
            builder.annotate(Order.class, annotation -> annotation.value(-value));
        } else {
            copyAnnotation(declaringType, builder, Order.class);
        }
    }

    private <A extends Annotation> void copyAnnotation(ClassElement source,
                                                       BeanElementBuilder target,
                                                       Class<A> annotationType) {
        AnnotationValue<A> annotation = source.getAnnotation(annotationType);
        if (annotation != null) {
            target.annotate(annotation);
        }
    }

    private void copyRegisteredQualifiers(ClassElement source, BeanElementBuilder target) {
        for (ClassElement qualifier : qualifiers) {
            AnnotationValue<Annotation> annotationValue = source.getAnnotation(qualifier.getName());
            if (annotationValue != null) {
                target.annotate(annotationValue);
            }
        }
    }

    private void configureInterceptorBindings(VisitorContext visitorContext, ClassElement scannedClass) {
        scannedClass.getDeclaredAnnotationNames()
                .forEach(annotationName -> configurePotentialInterceptorBinding(visitorContext, annotationName));
        scannedClass.getEnclosedElements(ElementQuery.ALL_METHODS.includeHiddenElements())
                .forEach(method -> method.getDeclaredAnnotationNames()
                        .forEach(annotationName -> configurePotentialInterceptorBinding(visitorContext, annotationName)));
        scannedClass.getEnclosedElements(ElementQuery.ALL_FIELDS.includeHiddenElements())
                .forEach(field -> field.getDeclaredAnnotationNames()
                        .forEach(annotationName -> configurePotentialInterceptorBinding(visitorContext, annotationName)));
    }

    private void configurePotentialInterceptorBinding(VisitorContext visitorContext, String annotationName) {
        visitorContext.getClassElement(annotationName)
                .filter(annotation -> annotation.hasDeclaredAnnotation(InterceptorBinding.class))
                .ifPresent(this::configureInterceptorBinding);
    }

    private boolean hasInterceptorBinding(VisitorContext visitorContext, Element element) {
        if (element.getAnnotationMetadata().hasAnnotation(InterceptorBindingDefinitions.class)) {
            return true;
        }
        return element.getDeclaredAnnotationNames()
                .stream()
                .anyMatch(annotationName -> interceptorBindings.stream().anyMatch(binding -> binding.getName().equals(annotationName))
                        || visitorContext.getClassElement(annotationName)
                        .map(annotation -> annotation.hasDeclaredAnnotation(InterceptorBinding.class))
                        .orElse(false));
    }

    private boolean isNormalScopedBean(VisitorContext visitorContext, ClassElement beanElement) {
        if (beanElement.hasStereotype(NormalScope.class)) {
            return true;
        }
        return beanElement.getAnnotationNames()
                .stream()
                .map(visitorContext::getClassElement)
                .flatMap(Optional::stream)
                .anyMatch(annotationType -> annotationType.hasAnnotation(NormalScope.class) || annotationType.hasStereotype(NormalScope.class));
    }

    private static void handleObservesMethod(ClassElement classElement, MethodElement methodElement, VisitorContext visitorContext) {
        ObservesMethodVisitor observesVisitor = new ObservesMethodVisitor();
        observesVisitor.visitClass(classElement, visitorContext);
        observesVisitor.visitMethod(methodElement, visitorContext);
        ObservesAsyncMethodVisitor observesAsyncVisitor = new ObservesAsyncMethodVisitor();
        observesAsyncVisitor.visitClass(classElement, visitorContext);
        observesAsyncVisitor.visitMethod(methodElement, visitorContext);
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
