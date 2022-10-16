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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import org.eclipse.odi.cdi.processor.CdiUtil;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

abstract class ParameterAnnotationInjectableMethodVisitor<T extends Annotation> implements TypeElementVisitor<Object, Object> {

    protected ClassElement currentClass;

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        this.currentClass = element;
    }

    protected abstract Class<T> getParameterAnnotation();

    protected void disqualifyMethod(MethodElement element) {
        element.removeAnnotation(getParameterAnnotation());
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (currentClass == null) {
            return;
        }
        if (!element.getDeclaringType().getName().equals(currentClass.getName())) {
            // Methods aren't inherited
            disqualifyMethod(element);
            return;
        }
        final List<ParameterElement> parameters = Arrays
                .stream(element.getParameters())
                .filter(p -> p.hasDeclaredAnnotation(getParameterAnnotation()))
                .collect(Collectors.toList());
        if (parameters.isEmpty()) {
            return;
        }
        if (parameters.size() > 1) {
            context.fail("Only a single " + getParameterAnnotation().getSimpleName() + " parameter is permitted, "
                    + parameters.size()
                    + " are defined: "
                    + parameters.stream().map(Element::getName)
                    .collect(Collectors.joining(" and ")), element);
        }
        if (CdiUtil.validateNoInterceptor(context, getParameterAnnotation(), element)) {
            return;
        }
        for (ParameterElement parameter : element.getParameters()) {
            if (CdiUtil.validateParameterExtraAnnotations(context, getParameterAnnotation(), element, parameter)) {
                return;
            }
            if (CdiUtil.validateInjectedType(context, parameter.getGenericType(), parameter)) {
                return;
            }
        }
        ParameterElement parameter = parameters.get(0);
        if (element.isAbstract()) {
            context.fail("Methods with parameters annotated with " + getParameterAnnotation() + " cannot be abstract.", element);
            return;
        }
        if (element.isStatic()) {
            element.annotate(Executable.class);
        }
        if (element.isPrivate()) {
            element.annotate(ReflectiveAccess.class);
        }
        handleMatch(element, parameter, context);
    }

    public abstract void handleMatch(MethodElement methodElement, ParameterElement parameterElement, VisitorContext context);

    @Override
    @NonNull
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
