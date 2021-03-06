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

import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.inject.Inject;
import org.eclipse.odi.cdi.processor.CdiUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Visits all elements annotated with {@link jakarta.inject.Inject} to validate them.
 */
public class InjectVisitor implements TypeElementVisitor<Object, Object> {

    private List<ConstructorElement> injectConstructors = new ArrayList<>(2);

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Collections.singleton(AnnotationUtil.INJECT);
    }

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
        if (element.hasDeclaredAnnotation(AnnotationUtil.INJECT)) {

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
        if (element.hasDeclaredAnnotation(AnnotationUtil.INJECT)) {
            validateInjectMethod(element, context);
        }
    }

    @Override
    public void visitField(FieldElement element, VisitorContext context) {
        if (element.hasDeclaredAnnotation(AnnotationUtil.INJECT)) {
            if (element.hasDeclaredAnnotation(Property.class)) {
                element.removeAnnotation(AnnotationUtil.INJECT);
            }
            CdiUtil.validateInjectedType(context, element.getGenericField(), element);
            CdiUtil.visitInjectPoint(context, element);
        }
    }

    private void validateInjectMethod(MethodElement element, VisitorContext context) {
        if (CdiUtil.validateMethodExtraAnnotations(context, Inject.class, element)) {
            return;
        }
        for (ParameterElement parameter : element.getParameters()) {
            if (CdiUtil.validateMethodNoSpecialParameters(context, "Inject", element, parameter)) {
                return;
            }
            if (CdiUtil.validateInjectedType(context, parameter.getGenericType(), parameter)) {
                return;
            }
            if (CdiUtil.visitInjectPoint(context, parameter)) {
                return;
            }
        }
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
