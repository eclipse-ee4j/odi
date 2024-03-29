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

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InterceptorBinding;
import org.eclipse.odi.cdi.annotation.SelfInterceptor;

/**
 * Make beans {@link AroundInvoke} interceptors of itself.
 */
public class AroundInvokeVisitor implements TypeElementVisitor<Object, AroundInvoke> {

    private ClassElement classElement;

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (element.hasStereotype(Interceptor.class) || element.hasStereotype(InterceptorBinding.class)) {
            // We are only interested in intercepted classes
            classElement = null;
        } else {
            classElement = element;
        }
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (classElement == null || !element.isAnnotationPresent(AroundInvoke.class)) {
            return;
        }

        if (element.isPrivate()) {
            element.annotate(ReflectiveAccess.class);
            element.annotate(Executable.class);
        }

        classElement.annotate(SelfInterceptor.class, annotationValueBuilder -> {
            annotationValueBuilder.member("self", classElement.getName());
        });
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
