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
package com.oracle.odi.cdi.processor;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.InterceptorBinding;
import io.micronaut.aop.InterceptorBindingDefinitions;
import io.micronaut.aop.InterceptorKind;
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
import jakarta.inject.Singleton;
import jakarta.interceptor.AroundConstruct;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;

/**
 * Processes {@link jakarta.interceptor.Interceptor} elements to correctly handle it using Micronaut.
 */
public class InterceptorVisitor implements TypeElementVisitor<Interceptor, Object> {

    private static final String INTERCEPTOR_ADAPTER = "com.oracle.odi.cdi.intercept.JakartaInterceptorAdapter";
    private BeanElementBuilder currentBuilder;
    private ClassElement currentClass;
    private Set<String> mappedTypes = new HashSet<>();

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        mappedTypes.clear();

        this.currentClass = element;
        this.currentBuilder = addInterceptor(element, context, element);
    }

    /**
     * Adds a new interceptor bean.
     * @param originatingElement The originating element
     * @param context The context
     * @param interceptorBean The interceptor bean
     * @return The bean builder that constructs the interceptor
     */
    public static BeanElementBuilder addInterceptor(ClassElement originatingElement,
                                              VisitorContext context,
                                              ClassElement interceptorBean) {
        Set<String> interceptorBindings =
                new HashSet<>(interceptorBean.getAnnotationNamesByStereotype(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS));
        interceptorBean.removeStereotype(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS);
        interceptorBean.removeAnnotation(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS);
        interceptorBean.annotate(Singleton.class);
        final ClassElement interceptorElement = context.getClassElement(INTERCEPTOR_ADAPTER).orElse(null);
        if (interceptorElement != null) {

            return originatingElement
                    .addAssociatedBean(interceptorElement)
                    .typeArguments(interceptorBean)
                    .withParameters(parameters -> parameters[0].typeArguments(interceptorBean))
                    .annotate(InterceptorBean.class)
                    .annotate(Indexed.class,
                              (builder) -> builder.value(io.micronaut.aop.Interceptor.class))
                    .annotate(InterceptorBindingDefinitions.class,
                              (bindingBuilder) -> {
                                  List<AnnotationValue<InterceptorBinding>> bindings = new ArrayList<>();
                                  for (String interceptorBinding : interceptorBindings) {
                                      final InterceptorKind[] kinds = InterceptorKind.values();
                                      for (InterceptorKind kind : kinds) {
                                          if (kind != InterceptorKind.INTRODUCTION) {
                                              bindings.add(
                                                      AnnotationValue.builder(
                                                              InterceptorBinding.class)
                                                              .value(interceptorBinding)
                                                              .member("kind",
                                                                      kind)
                                                              .build()
                                              );
                                          }
                                      }
                                  }
                                  bindingBuilder.values(bindings.toArray(new AnnotationValue[0]));
                              });
        }
        return null;
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (currentBuilder != null && currentClass != null && element.getDeclaringType().hasDeclaredAnnotation(Interceptor.class)) {
            visitAroundMethod(element, context, AroundInvoke.class, "setAroundInvoke");
            visitAroundMethod(element, context, AroundConstruct.class, "setAroundConstruct");
            remap(element, context, PostConstruct.class, "setPostConstruct");
            remap(element, context, javax.annotation.PostConstruct.class, "setPostConstruct");
            remap(element, context, PreDestroy.class, "setPreDestroy");
            remap(element, context, javax.annotation.PreDestroy.class, "setPreDestroy");
        }
    }

    private void remap(MethodElement element, VisitorContext context, Class<? extends Annotation> ann, String setMethodName) {
        if (element.hasDeclaredAnnotation(ann)) {
            visitAroundMethod(element, context, ann, setMethodName);
            element.removeAnnotation(ann);
        }
    }

    private void visitAroundMethod(
            MethodElement element,
            VisitorContext context,
            Class<? extends Annotation> aroundAnnotation,
            String setMethodName) {
        if (element.hasDeclaredAnnotation(aroundAnnotation)) {
            if (mappedTypes.contains(setMethodName)) {
                context.fail("Only a single " + aroundAnnotation.getSimpleName() + " method is allowed", element);
                return;
            } else {
                if (element.isStatic()) {
                    context.fail(aroundAnnotation.getSimpleName() + " method cannot be static", element);
                    return;
                }
                if (element.isAbstract()) {
                    context.fail(aroundAnnotation.getSimpleName() + " method cannot be abstract", element);
                    return;
                }

                element.annotate(Executable.class);
                mappedTypes.add(setMethodName);
                addSetMethod(element, currentBuilder, setMethodName);
            }
        }
    }

    /**
     * Sets the around invoke method.
     * @param element The element
     * @param currentBuilder The current builder
     */
    public static void setAroundInvokeMethod(MethodElement element, BeanElementBuilder currentBuilder) {
        addSetMethod(element, currentBuilder, "setAroundInvoke");
    }

    private static void addSetMethod(MethodElement element, BeanElementBuilder currentBuilder, String setMethodName) {
        currentBuilder.withMethods(
                ElementQuery.ALL_METHODS.onlyInstance().onlyDeclared().onlyAccessible()
                        .named((name) -> name.equals(setMethodName)),
                (
                        (method) -> {
                            method.inject();
                            method.getParameters()[0].injectValue(element.getName());
                        })
        );
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
