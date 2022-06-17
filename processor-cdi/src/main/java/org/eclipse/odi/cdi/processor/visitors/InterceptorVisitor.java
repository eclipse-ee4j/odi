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

import io.micronaut.aop.InterceptorBean;
import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Indexed;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.beans.BeanElementBuilder;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Singleton;
import jakarta.interceptor.AroundConstruct;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Processes {@link jakarta.interceptor.Interceptor} elements to correctly handle it using Micronaut.
 */
public class InterceptorVisitor implements TypeElementVisitor<Interceptor, Object> {

    private static final String INTERCEPTOR_ADAPTER = "org.eclipse.odi.cdi.intercept.JakartaInterceptorAdapter";

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        addInterceptor(element, context, element);
    }

    /**
     * Adds a new interceptor bean.
     *
     * @param originatingElement The originating element
     * @param context            The context
     * @param interceptorBean    The interceptor bean
     * @return The bean builder that constructs the interceptor
     */
    public static BeanElementBuilder addInterceptor(ClassElement originatingElement,
                                                    VisitorContext context,
                                                    ClassElement interceptorBean) {
        return addInterceptor(originatingElement, context, interceptorBean, false);
    }

    /**
     * Adds a new interceptor bean.
     *
     * @param originatingElement The originating element
     * @param context            The context
     * @param interceptorBean    The interceptor bean
     * @param isSelfInterceptor  Is self interceptor
     * @return The bean builder that constructs the interceptor
     */
    public static BeanElementBuilder addInterceptor(ClassElement originatingElement,
                                                    VisitorContext context,
                                                    ClassElement interceptorBean,
                                                    boolean isSelfInterceptor) {
        Set<String> interceptorBindings =
                new HashSet<>(interceptorBean.getAnnotationNamesByStereotype(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS));
        if (!isSelfInterceptor) {
            interceptorBean.removeAnnotation(AnnotationUtil.ANN_AROUND);
            interceptorBean.removeStereotype(AnnotationUtil.ANN_AROUND);
            interceptorBean.removeStereotype(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS);
            interceptorBean.removeAnnotation(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS);
        }
        // Investigate why `.hasAnnotation(Scope.class)` doesn't work
        if (!originatingElement.hasAnnotation(Dependent.class)) {
            interceptorBean.annotate(Singleton.class);
        }
        final ClassElement interceptorElement = context.getClassElement(INTERCEPTOR_ADAPTER).orElse(null);
        if (interceptorElement != null) {

            final BeanElementBuilder interceptorBuilder = originatingElement
                    .addAssociatedBean(interceptorElement)
                    .typeArguments(interceptorBean)
                    .withParameters(parameters -> parameters[0].typeArguments(interceptorBean))
                    .annotate(InterceptorBean.class)
                    .annotate(Indexed.class, builder -> builder.value(io.micronaut.aop.Interceptor.class));
            for (String interceptorBinding : interceptorBindings) {
                final AnnotationValue<Annotation> av = interceptorBean.getAnnotation(interceptorBinding);
                if (av != null) {
                    interceptorBuilder.annotate(av);
                }
            }
            discoverInterceptMethods(
                    originatingElement,
                    context,
                    interceptorBean,
                    interceptorBuilder,
                    AroundInvoke.class,
                    "setAroundInvoke",
                    false,
                    isSelfInterceptor
            );
            discoverInterceptMethods(
                    originatingElement,
                    context,
                    interceptorBean,
                    interceptorBuilder,
                    AroundConstruct.class,
                    "setAroundConstruct",
                    false,
                    isSelfInterceptor
            );
            discoverInterceptMethods(
                    originatingElement,
                    context,
                    interceptorBean,
                    interceptorBuilder,
                    PostConstruct.class,
                    "setPostConstruct",
                    true,
                    isSelfInterceptor
            );
            discoverInterceptMethods(
                    originatingElement,
                    context,
                    interceptorBean,
                    interceptorBuilder,
                    PreDestroy.class,
                    "setPreDestroy",
                    true,
                    isSelfInterceptor
            );
            discoverInterceptMethods(
                    originatingElement,
                    context,
                    interceptorBean,
                    interceptorBuilder,
                    javax.annotation.PostConstruct.class,
                    "setPostConstruct",
                    true,
                    isSelfInterceptor
            );
            discoverInterceptMethods(
                    originatingElement,
                    context,
                    interceptorBean,
                    interceptorBuilder,
                    javax.annotation.PreDestroy.class,
                    "setPreDestroy",
                    true,
                    isSelfInterceptor
            );
            return interceptorBuilder;
        }
        return null;
    }

    private static void discoverInterceptMethods(ClassElement originatingElement,
                                                 VisitorContext context,
                                                 ClassElement interceptorBean,
                                                 BeanElementBuilder interceptorBuilder,
                                                 Class<? extends Annotation> aroundAnnotation,
                                                 String setMethodName,
                                                 boolean isRemoveAnn, boolean isSelfInterceptor) {
        final ElementQuery<MethodElement> baseQuery = ElementQuery.ALL_METHODS
                .onlyAccessible(originatingElement);

        Map<String, String> mappedTypes = new HashMap<>();
        List<String> aroundMethods = new ArrayList<>(5);
        interceptorBean.getEnclosedElements(
                baseQuery.annotated(ann -> ann.hasDeclaredAnnotation(aroundAnnotation) || aroundAnnotation.equals(ann.classValue(Executable.class).orElse(null)))
        ).forEach(methodElement -> {
            final String declaringType = methodElement.getDeclaringType().getName();
            final String previous = mappedTypes.put(declaringType, methodElement.getName());
            if (previous != null) {
                context.fail("Only a single " + aroundAnnotation.getSimpleName() + " method is allowed", originatingElement);
            } else {
                if (methodElement.isStatic()) {
                    context.fail(aroundAnnotation.getSimpleName() + " method cannot be static", methodElement);
                } else if (methodElement.isAbstract()) {
                    context.fail(aroundAnnotation.getSimpleName() + " method cannot be abstract", methodElement);
                } else {
                    methodElement.annotate(Executable.class, (builder) -> builder.value(aroundAnnotation));
                    final ClassElement dt = methodElement.getDeclaringType();
                    if (!dt.equals(interceptorBean)) {
                        final MethodElement parentMethod = dt.getEnclosedElement(
                                        ElementQuery.ALL_METHODS.onlyInstance().
                                                named(n -> n.equals(methodElement.getName()))
                                                .filter(m -> m.equals(methodElement)))
                                .orElse(null);
                        if (parentMethod != null) {
                            parentMethod.annotate(Executable.class, (builder) -> builder.value(aroundAnnotation));
                            if (isRemoveAnn) {
                                parentMethod.removeAnnotation(aroundAnnotation);
                            }
                        }
                    }
                    aroundMethods.add(methodElement.getName());
                    if (isRemoveAnn) {
                        methodElement.removeAnnotation(aroundAnnotation);
                    }
                }
            }
        });

        if (!aroundMethods.isEmpty()) {
            addSetMethod(aroundMethods, interceptorBuilder, setMethodName);
        }

        if (isSelfInterceptor) {
            addSetMethod("true", interceptorBuilder, "setSelfInterceptor");
        }
    }

    private static void addSetMethod(List<String> methods, BeanElementBuilder currentBuilder, String setMethodName) {
        addSetMethod(String.join(",", methods), currentBuilder, setMethodName);
    }

    private static void addSetMethod(String value, BeanElementBuilder currentBuilder, String setMethodName) {
        currentBuilder.withMethods(
                ElementQuery.ALL_METHODS.onlyInstance().onlyDeclared().onlyAccessible()
                        .named((name) -> name.equals(setMethodName)),
                (
                        (method) -> {
                            method.inject();
                            method.getParameters()[0].injectValue(value);
                        })
        );
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
