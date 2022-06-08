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

import io.micronaut.context.annotation.Bean;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Stereotype;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Validate elements annotated with {@link jakarta.enterprise.inject.Stereotype}.
 */
public class ClassStereotypeValidator implements TypeElementVisitor<Object, Stereotype> {
    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        validateBean(element, context);
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (element.hasDeclaredAnnotation(Produces.class)) {
            validateBean(element, context);
        }
    }

    @Override
    public void visitField(FieldElement element, VisitorContext context) {
        if (element.hasDeclaredAnnotation(Produces.class)) {
            validateBean(element, context);
        }
    }

    private void validateBean(Element element, VisitorContext context) {
        final List<String> stereotypes = element.getAnnotationNamesByStereotype(Stereotype.class);
        validateScopes(element, context, stereotypes);
        validateQualifiers(element, context, stereotypes);
        validateTyped(element, context, stereotypes);
    }

    private void validateTyped(Element element, VisitorContext context, List<String> stereotypes) {
        if (element.hasStereotype(Bean.class) && !element.hasAnnotation(Bean.class)) {
            if (element.stringValue(Bean.class, "typed").isPresent()) {
                context.fail("Inherited stereotypes ["
                                     + CdiUtil.toAnnotationDescription(stereotypes)
                                     + "] cannot declare @Typed. See "
                                     + CdiUtil.SPEC_LOCATION
                                     + "#named_stereotype",
                             element);
            }

        }
    }

    private void validateScopes(Element element, VisitorContext context, List<String> stereotypes) {
        final List<String> scopes = resolveDeclaredScopes(element, stereotypes);
        if (scopes.size() > 1) {
            if (scopes.size() == 2 && scopes.contains(Dependent.class.getName())) {
                element.removeAnnotation(Dependent.class);
                return;
            }
            context.fail("Inherited stereotypes [" + CdiUtil
                                 .toAnnotationDescription(stereotypes) + "] include more than one defined scope: " + CdiUtil
                                 .toAnnotationDescription(scopes) + ". To resolve this problem declare the selected scope on "
                                 + "the type.",
                         element);
        }
    }

    private void validateQualifiers(Element element, VisitorContext context, List<String> stereotypes) {

        if (!stereotypes.isEmpty()) {

            final List<String> qualifiers = new ArrayList<>(element.getAnnotationNamesByStereotype(io.micronaut.core.annotation.AnnotationUtil.QUALIFIER));
            qualifiers.removeAll(stereotypes);
            if (!qualifiers.isEmpty()) {
                for (String qualifier : qualifiers) {
                    if (!AnnotationUtil.ANN_NAME.equals(qualifier) && element.hasStereotype(qualifier) && !element
                            .hasAnnotation(qualifier)) {
                        context.fail("Inherited stereotypes ["
                                             + CdiUtil.toAnnotationDescription(stereotypes)
                                             + "] cannot declare qualifiers: "
                                             + CdiUtil.toAnnotationDescription(qualifiers)
                                             + ". See " + CdiUtil.SPEC_LOCATION
                                             + "#named_stereotype",
                                     element);

                        break;
                    }
                }
            }
        }
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    private static List<String> resolveDeclaredScopes(AnnotationMetadata annotationMetadata, List<String> stereotypes) {
        if (annotationMetadata == AnnotationMetadata.EMPTY_METADATA) {
            return Collections.emptyList();
        }
        final List<String> scopeStereotypes =
                new ArrayList<>(annotationMetadata.getDeclaredAnnotationNamesByStereotype(io.micronaut.core.annotation.AnnotationUtil.SCOPE));

        purgeInternalScopes(stereotypes, scopeStereotypes);
        String n = null;
        if (scopeStereotypes.isEmpty()) {
            scopeStereotypes.addAll(annotationMetadata.getAnnotationNamesByStereotype(io.micronaut.core.annotation.AnnotationUtil.SCOPE));
            purgeInternalScopes(stereotypes, scopeStereotypes);
        }
        return scopeStereotypes;
    }

    private static void purgeInternalScopes(List<String> stereotypes, List<String> scopeStereotypes) {
        // deal with stereotypes with scopes
        scopeStereotypes.removeAll(stereotypes);
        // filter internal annotations
        scopeStereotypes.remove("io.micronaut.runtime.context.scope.ScopedProxy");
    }

}
