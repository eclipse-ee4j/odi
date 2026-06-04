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
package org.eclipse.odi.cdi.processor.visitors;

import io.micronaut.aop.Around;
import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.context.AutoClose;
import jakarta.enterprise.inject.Stereotype;
import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import org.eclipse.odi.cdi.annotation.OdiConstructorTarget;
import org.eclipse.odi.cdi.processor.CdiUtil;

import java.lang.annotation.Annotation;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Processes {@link InterceptorBinding} elements to correctly handle it using Micronaut.
 */
public class InterceptorBindingVisitor implements TypeElementVisitor<Object, InterceptorBinding> {

    private static final int ADDITIONAL_PROXY_CONSTRUCTOR_PARAMETERS = 5;

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Set.of(
                InterceptorBinding.class.getName(),
                AroundInvoke.class.getName(),
                AnnotationUtil.ANN_INTERCEPTOR_BINDING,
                AnnotationUtil.ANN_INTERCEPTOR_BINDINGS
        );
    }

    @Override
    public int getOrder() {
        return 1;
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        addNestedInterceptorBindings(element, element.getAnnotationMetadata(), context);
        if (element.isAnnotationPresent(Interceptor.class)) {
            // We are only interested in intercepted classes
            return;
        }

        List<MethodElement> allMethods = element.getEnclosedElements(
                ElementQuery.ALL_METHODS
                        .onlyInstance()
                        .onlyAccessible()
                        .onlyConcrete()
        );
        allMethods.forEach(InterceptorBindingVisitor::removeInheritedMethodInterceptorBindings);
        allMethods.forEach(methodElement -> addNestedInterceptorBindings(
                methodElement,
                methodElement.getMethodAnnotationMetadata().getAnnotationMetadata(),
                context
        ));

        List<MethodElement> selfInterceptorMethods = element.getEnclosedElements(
                ElementQuery.ALL_METHODS
                        .onlyInstance()
                        .onlyConcrete()
                        .onlyDeclared()
                        .filter(methodElement -> methodElement.hasAnnotation(AroundInvoke.class))
        );

        boolean hasClassInterceptorBinding = !element.getAnnotationNamesByStereotype(InterceptorBinding.class).isEmpty();
        List<MethodElement> businessMethods = allMethods.stream()
                .filter(this::isBusinessMethod)
                .collect(Collectors.toList());
        boolean hasSelfInterceptorMethods = !selfInterceptorMethods.isEmpty();
        List<MethodElement> interceptedBusinessMethods = businessMethods.stream()
                .filter(method -> hasClassInterceptorBinding || hasSelfInterceptorMethods || hasInterceptorBinding(method))
                .collect(Collectors.toList());
        boolean hasBoundBusinessMethod = businessMethods.stream().anyMatch(this::hasInterceptorBinding);
        boolean hasConstructorInterceptorBinding = element.getPrimaryConstructor().map(this::hasInterceptorBinding).orElse(false);
        boolean hasClassOrMethodInterceptorBinding = !interceptedBusinessMethods.isEmpty()
                || hasSelfInterceptorMethods;
        boolean hasConstructorInterception = hasClassInterceptorBinding || hasConstructorInterceptorBinding;

        if (hasClassInterceptorBinding
                || hasBoundBusinessMethod
                || hasConstructorInterceptorBinding
                || !selfInterceptorMethods.isEmpty()) {
            element.getPrimaryConstructor().ifPresent(this::annotateConstructorTarget);
        }

        if (hasClassOrMethodInterceptorBinding || hasConstructorInterception) {
            if (CdiUtil.validateInterceptedBeanProxyability(context, element, interceptedBusinessMethods)) {
                return;
            }
            if (CdiUtil.validateInterceptedBeanConstructor(context, element)) {
                return;
            }
            if (hasClassInterceptorBinding || hasSelfInterceptorMethods || hasConstructorInterception) {
                annotateAround(element, !element.hasAnnotation(AutoClose.class));
            } else {
                interceptedBusinessMethods.forEach(InterceptorBindingVisitor::annotateAround);
            }
        }

        selfInterceptorMethods.forEach(methodElement -> {
            if (methodElement.isPrivate()) {
                methodElement.annotate(Executable.class);
                methodElement.annotate(ReflectiveAccess.class);
            }
        });

        if (!selfInterceptorMethods.isEmpty()) {
            InterceptorVisitor.addInterceptor(element, context, element, true);
        }
    }

    @Override
    public void visitConstructor(ConstructorElement element, VisitorContext context) {
        addNestedInterceptorBindings(element, element.getMethodAnnotationMetadata().getAnnotationMetadata(), context);
        annotateConstructorTarget(element);
    }

    private void annotateConstructorTarget(MethodElement constructor) {
        constructor.annotate(OdiConstructorTarget.class, builder -> builder
                .member("value", new AnnotationClassValue<>(constructor.getDeclaringType().getName()))
                .member("additionalProxyParameters", ADDITIONAL_PROXY_CONSTRUCTOR_PARAMETERS));
    }

    private static void annotateAround(Element element) {
        annotateAround(element, true);
    }

    private static void annotateAround(Element element, boolean proxyTarget) {
        element.annotate(Around.class, builder -> builder
                .member("proxyTarget", proxyTarget)
                .member("cacheableLazyTarget", proxyTarget));
    }

    static void removeInheritedMethodInterceptorBindings(MethodElement methodElement) {
        List<String> methodInterceptorBindings = methodElement.getMethodAnnotationMetadata()
                .getAnnotationMetadata()
                .getAnnotationNamesByStereotype(InterceptorBinding.class);
        Set<String> declaredAnnotationNames = methodElement.getDeclaredAnnotationNames();
        for (String annotationName : methodInterceptorBindings) {
            if (!declaredAnnotationNames.contains(annotationName)) {
                methodElement.removeAnnotation(annotationName);
            }
        }
    }

    static void addNestedInterceptorBindings(Element target, AnnotationMetadata sourceMetadata, VisitorContext context) {
        Set<String> interceptorBindings = new LinkedHashSet<>(
                sourceMetadata.getAnnotationNamesByStereotype(InterceptorBinding.class)
        );
        Set<String> declaredAnnotations = sourceMetadata.getDeclaredAnnotationNames();
        Map<String, AnnotationValue<Annotation>> collectedBindings = new LinkedHashMap<>();
        for (String interceptorBinding : interceptorBindings) {
            AnnotationValue<Annotation> annotationValue = declaredAnnotations.contains(interceptorBinding)
                    ? sourceMetadata.getAnnotation(interceptorBinding)
                    : null;
            if (annotationValue != null) {
                annotationValue = withNonBindingMembers(annotationValue, context, interceptorBinding);
                target.annotate(annotationValue);
                collectedBindings.put(interceptorBinding, annotationValue);
            }
        }
        collectStereotypeInterceptorBindings(target, sourceMetadata, context, collectedBindings, declaredAnnotations);
        for (String interceptorBinding : interceptorBindings) {
            addNestedInterceptorBindings(target, context, interceptorBinding, collectedBindings, declaredAnnotations, new LinkedHashSet<>());
        }
    }

    private static void collectStereotypeInterceptorBindings(Element target,
                                                             AnnotationMetadata sourceMetadata,
                                                             VisitorContext context,
                                                             Map<String, AnnotationValue<Annotation>> collectedBindings,
                                                             Set<String> declaredAnnotations) {
        for (String stereotype : sourceMetadata.getAnnotationNamesByStereotype(Stereotype.class)) {
            if (stereotype.equals(Stereotype.class.getName())) {
                continue;
            }
            context.getClassElement(stereotype).ifPresent(stereotypeElement -> {
                Set<String> declaredStereotypeAnnotations = stereotypeElement.getDeclaredAnnotationNames();
                for (String interceptorBinding : stereotypeElement.getAnnotationNamesByStereotype(InterceptorBinding.class)) {
                    if (isNestedInterceptorBinding(stereotype, interceptorBinding)
                            && declaredStereotypeAnnotations.contains(interceptorBinding)) {
                        AnnotationValue<Annotation> annotationValue = stereotypeElement.getAnnotation(interceptorBinding);
                        if (annotationValue != null && !declaredAnnotations.contains(interceptorBinding)) {
                            annotationValue = withNonBindingMembers(annotationValue, context, interceptorBinding);
                            collectInterceptorBinding(target, context, collectedBindings, interceptorBinding, annotationValue, false);
                        }
                    }
                }
            });
        }
    }

    private static void addNestedInterceptorBindings(Element target,
                                                      VisitorContext context,
                                                      String interceptorBinding,
                                                      Map<String, AnnotationValue<Annotation>> collectedBindings,
                                                      Set<String> declaredAnnotations,
                                                      Set<String> visiting) {
        if (!visiting.add(interceptorBinding)) {
            return;
        }
        try {
            context.getClassElement(interceptorBinding).ifPresent(bindingElement -> {
                Set<String> declaredBindingAnnotations = bindingElement.getDeclaredAnnotationNames();
                List<String> nestedBindings = bindingElement.getAnnotationNamesByStereotype(InterceptorBinding.class);
                for (String nestedBinding : nestedBindings) {
                    if (isNestedInterceptorBinding(interceptorBinding, nestedBinding)
                            && declaredBindingAnnotations.contains(nestedBinding)
                            && !declaredAnnotations.contains(nestedBinding)) {
                        AnnotationValue<Annotation> nestedAnnotation = bindingElement.getAnnotation(nestedBinding);
                        if (nestedAnnotation != null) {
                            nestedAnnotation = withNonBindingMembers(nestedAnnotation, context, nestedBinding);
                            if (!collectInterceptorBinding(target, context, collectedBindings, nestedBinding, nestedAnnotation, true)) {
                                return;
                            }
                            addNestedInterceptorBindings(
                                    target,
                                    context,
                                    nestedBinding,
                                    collectedBindings,
                                    declaredAnnotations,
                                    visiting
                            );
                        }
                    }
                }
            });
        } finally {
            visiting.remove(interceptorBinding);
        }
    }

    private static boolean collectInterceptorBinding(Element target,
                                                     VisitorContext context,
                                                     Map<String, AnnotationValue<Annotation>> collectedBindings,
                                                     String interceptorBinding,
                                                     AnnotationValue<Annotation> annotationValue,
                                                     boolean annotateTarget) {
        AnnotationValue<Annotation> existingAnnotation = collectedBindings.get(interceptorBinding);
        if (existingAnnotation == null) {
            collectedBindings.put(interceptorBinding, annotationValue);
            if (annotateTarget) {
                target.annotate(annotationValue);
            }
            return true;
        }
        if (!interceptorBindingValuesMatch(existingAnnotation, annotationValue, context)) {
            context.fail("Conflicting interceptor binding values for " + interceptorBinding, target);
            return false;
        }
        return true;
    }

    private static boolean isNestedInterceptorBinding(String interceptorBinding, String nestedBinding) {
        return !nestedBinding.equals(interceptorBinding)
                && !nestedBinding.equals(InterceptorBinding.class.getName());
    }

    private static AnnotationValue<Annotation> withNonBindingMembers(AnnotationValue<Annotation> annotationValue,
                                                                     VisitorContext context,
                                                                     String annotationName) {
        Set<String> nonBindingMembers = nonBindingMembers(context, annotationName);
        if (nonBindingMembers.isEmpty()) {
            return annotationValue;
        }
        nonBindingMembers.add(AnnotationUtil.NON_BINDING_ATTRIBUTE);
        return AnnotationValue.builder(annotationValue)
                .member(AnnotationUtil.NON_BINDING_ATTRIBUTE, nonBindingMembers.toArray(String[]::new))
                .build();
    }

    private static boolean interceptorBindingValuesMatch(AnnotationValue<?> left,
                                                         AnnotationValue<?> right,
                                                         VisitorContext context) {
        if (!left.getAnnotationName().equals(right.getAnnotationName())) {
            return false;
        }
        return bindingValues(left, context).equals(bindingValues(right, context));
    }

    private static Map<CharSequence, Object> bindingValues(AnnotationValue<?> annotationValue, VisitorContext context) {
        Map<CharSequence, Object> values = new LinkedHashMap<>(annotationValue.getValues());
        for (String nonBindingMember : nonBindingMembers(context, annotationValue.getAnnotationName())) {
            values.remove(nonBindingMember);
        }
        return values;
    }

    private static Set<String> nonBindingMembers(VisitorContext context, String annotationName) {
        Set<String> nonBindingMembers = new LinkedHashSet<>();
        context.getClassElement(annotationName).ifPresent(annotation ->
                annotation.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared())
                        .stream()
                        .filter(method -> method.hasAnnotation(Nonbinding.class))
                        .map(MethodElement::getName)
                        .forEach(nonBindingMembers::add)
        );
        return nonBindingMembers;
    }

    private boolean hasInterceptorBinding(MethodElement methodElement) {
        return !methodElement.getMethodAnnotationMetadata()
                .getAnnotationMetadata()
                .getAnnotationNamesByStereotype(InterceptorBinding.class)
                .isEmpty();
    }

    private boolean isBusinessMethod(MethodElement methodElement) {
        return !isObjectMethod(methodElement)
                && !methodElement.hasAnnotation(AroundInvoke.class);
    }

    private boolean isObjectMethod(MethodElement methodElement) {
        return switch (methodElement.getName()) {
            case "toString", "hashCode", "getClass", "clone", "notify", "notifyAll" ->
                    methodElement.getParameters().length == 0;
            case "equals" -> methodElement.getParameters().length == 1
                    && Object.class.getName().equals(methodElement.getParameters()[0].getType().getName());
            case "wait" -> {
                int parameters = methodElement.getParameters().length;
                yield parameters >= 0 && parameters <= 2;
            }
            default -> Object.class.getName().equals(methodElement.getDeclaringType().getName());
        };
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
