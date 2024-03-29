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
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;

import java.util.List;

/**
 * Processes {@link InterceptorBinding} elements to correctly handle it using Micronaut.
 */
public class InterceptorBindingVisitor implements TypeElementVisitor<InterceptorBinding, Object> {

    @Override
    public int getOrder() {
        return 1;
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (element.isAnnotationPresent(Interceptor.class)) {
            // We are only interested in intercepted classes
            return;
        }

        List<MethodElement> methods = element.getEnclosedElements(
                ElementQuery.ALL_METHODS
                        .onlyInstance()
                        .onlyAccessible()
                        .onlyConcrete()
                        .onlyDeclared()
        );

        if (!methods.isEmpty()) {
            element.annotate(Around.class, builder -> {
                builder
                        .member("proxyTarget", true)
                        .member("cacheableLazyTarget", true).build();
            });
        }

        List<MethodElement> selfInterceptorMethods = element.getEnclosedElements(
                ElementQuery.ALL_METHODS
                        .onlyInstance()
                        .onlyConcrete()
                        .onlyDeclared()
                        .filter(methodElement -> methodElement.hasAnnotation(AroundInvoke.class))
        );

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
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
