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
package com.oracle.odi.cdi.processor.visitors;

import com.oracle.odi.cdi.processor.CdiUtil;
import io.micronaut.context.annotation.Property;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Disposes;

import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Visits all elements annotated with {@link javax.inject.Inject} to validate them.
 */
public class InjectVisitor implements TypeElementVisitor<Object, Inject> {

    private List<ConstructorElement> injectConstructors = new ArrayList<>(2);

    @Override
    public int getOrder() {
        // TODO: Should be after all visitors that can make a class injectable
        return TypeElementVisitor.super.getOrder();
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        injectConstructors.clear();
    }

    @Override
    public void visitConstructor(ConstructorElement element, VisitorContext context) {
        if (element.hasDeclaredAnnotation(Inject.class)) {

            validateInjectMethod(element, context);
            injectConstructors.add(element);
            if (injectConstructors.size() == 2) {
                final String methodDesc = injectConstructors.stream()
                        .map((me) -> me.getDescription(true))
                        .collect(Collectors.joining(" and "));
                context.fail("More than one constructor annotated with @Inject found: "
                                     + methodDesc
                                     + ". See "
                                     + CdiUtil.SPEC_LOCATION
                                     + "#declaring_bean_constructor",
                             element);
                injectConstructors.clear();
            }
        }
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (element.hasDeclaredAnnotation(Inject.class)) {
            validateInjectMethod(element, context);
        }
    }

    @Override
    public void visitField(FieldElement element, VisitorContext context) {
        if (element.hasDeclaredAnnotation(Inject.class)) {
            if (element.hasDeclaredAnnotation(Property.class)) {
                element.removeAnnotation(Inject.class);
            }
            CdiUtil.validateInjectedType(context, element.getGenericField(), element);
            CdiUtil.visitInjectPoint(context, element);
        }
    }

    private void validateInjectMethod(MethodElement element, VisitorContext context) {
        final ParameterElement[] parameters = element.getParameters();
        for (ParameterElement parameter : parameters) {
            if (CdiUtil.validateInjectedType(context, parameter.getGenericType(), parameter)) {
                return;
            }
            if (failIfAnnotationPresent(parameter, context, Disposes.class, element instanceof ConstructorElement)) {
                break;
            }
            if (failIfAnnotationPresent(parameter, context, Observes.class, element instanceof ConstructorElement)) {
                break;
            }
            if (failIfAnnotationPresent(parameter, context, ObservesAsync.class, element instanceof ConstructorElement)) {
                break;
            }
            CdiUtil.visitInjectPoint(context, parameter);
        }
    }

    private boolean failIfAnnotationPresent(ParameterElement element,
                                            VisitorContext context,
                                            Class<? extends Annotation> t,
                                            boolean isConstructor) {
        if (element.hasDeclaredAnnotation(t)) {
            context.fail((
                                 isConstructor
                                         ? "Constructors"
                                         : "Methods") + " annotated with @Inject cannot define parameters annotated with @" + t
                    .getSimpleName(), element);
            return true;
        }
        return false;
    }

    @Override
    public void finish(VisitorContext visitorContext) {
        injectConstructors.clear();
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
