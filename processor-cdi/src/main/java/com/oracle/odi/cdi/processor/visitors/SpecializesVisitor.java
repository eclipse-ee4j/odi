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
import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Specializes;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Processes elmeents annotated with {@link jakarta.enterprise.inject.Specializes}.
 */
public class SpecializesVisitor implements TypeElementVisitor<Object, Specializes> {

    private ClassElement currentClass;

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (element.hasDeclaredAnnotation(Specializes.class)) {
            final ClassElement superType = element.getSuperType().orElse(null);
            if (superType == null) {
                context.fail("Specialization requires directly extending another bean. See "
                                     + CdiUtil.SPEC_LOCATION
                                     + "#specialize_managed_bean",
                             element);
            } else {
                inheritQualifiers(element, superType);
                element.annotate(Replaces.class, (builder) ->
                        builder.member(AnnotationMetadata.VALUE_MEMBER, new AnnotationClassValue<>(superType.getName()))
                );
            }
        }

        this.currentClass = element;
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (currentClass != null && element.hasDeclaredAnnotation(Specializes.class)) {
            final ClassElement superType = currentClass.getSuperType().orElse(null);
            if (superType == null) {
                context.fail("Specialization on a producer method requires directly extending another bean. See "
                                     + CdiUtil.SPEC_LOCATION
                                     + "#specialize_producer_method",
                             element);

            } else {
                final Optional<MethodElement> overriddenMethod = superType.getEnclosedElement(
                        ElementQuery.ALL_METHODS
                                .onlyInstance()
                                .onlyAccessible()
                                .onlyConcrete()
                                .named((name) -> name.equals(element.getName()))
                                .annotated((metadata) -> metadata.hasDeclaredAnnotation(Produces.class))
                                .filter((methodElement -> areParametersMatched(methodElement, element)))
                );

                if (overriddenMethod.isPresent()) {
                    final MethodElement me = overriddenMethod.get();
                    inheritQualifiers(element, me);
                    element.annotate(Replaces.class, (builder) -> {
                        builder.member(AnnotationMetadata.VALUE_MEMBER,
                                       new AnnotationClassValue<>(element.getGenericReturnType().getName()));
                        builder.member("factory", new AnnotationClassValue<>(me.getDeclaringType().getName()));
                    });
                } else {
                    context.fail(
                            "Specialization on a producer method requires directly overriding a super producer method that also"
                                    + " produces a bean. See "
                                    + CdiUtil.SPEC_LOCATION
                                    + "#specialize_producer_method",
                            element);

                }
            }
        }
    }

    private boolean areParametersMatched(MethodElement methodElement, MethodElement element) {
        final ParameterElement[] parameters = methodElement.getParameters();
        final ParameterElement[] that = element.getParameters();
        if (parameters.length == that.length) {
            for (int i = 0; i < that.length; i++) {
                ParameterElement right = that[i];
                ParameterElement left = parameters[i];
                if (!right.getType().equals(left.getType())) {
                    return false;
                }

            }
            return true;
        }
        return false;
    }

    private void inheritQualifiers(Element element, Element sourceElement) {
        final List<String> qualifiers = sourceElement.getDeclaredAnnotationNamesByStereotype(AnnotationUtil.QUALIFIER);
        for (String qualifier : qualifiers) {
            final AnnotationValue<Annotation> annotation = sourceElement.getAnnotation(qualifier);
            if (annotation != null) {
                final Map<CharSequence, Object> values = annotation.getValues();
                if (values.isEmpty()) {
                    element.annotate(qualifier);
                } else {
                    element.annotate(qualifier, (builder) -> builder.members(values));
                }
            }
        }
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
