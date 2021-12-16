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

import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.inject.Named;

import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.Stereotype;

/**
 * Validates elements annotated with {@link javax.inject.Named}.
 */
public class NamedVisitor implements TypeElementVisitor<Object, Named> {

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Collections.singleton(Named.class.getName());
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        validateElement(element, context);
    }

    private void validateElement(Element element, VisitorContext context) {
        if (element.hasAnnotation(AnnotationUtil.ANN_NAME) || element.hasStereotype(AnnotationUtil.ANN_NAME)) {
            element.stringValue(AnnotationUtil.ANN_NAME).ifPresent((name) -> validateIdentifier(name, element, context));
            // now validate stereotypes are correct
            // stereotypes can only have an empty @Named qualifier
            // see https://jakarta.ee/specifications/cdi/3.0/jakarta-cdi-spec-3.0.html#named_stereotype
            final List<String> stereotypes = element.getAnnotationNamesByStereotype(Stereotype.class);

            // if @Named is inherited via a stereotype
            if (!element.hasAnnotation(AnnotationUtil.ANN_NAME)) {
                final List<String> namedStereotypes = element.getAnnotationNamesByStereotype(AnnotationUtil.ANN_NAME);
                if (stereotypes.containsAll(namedStereotypes)) {
                    if (element.stringValue(AnnotationUtil.ANN_NAME).isPresent()) {
                        context.fail("Stereotypes [" + CdiUtil
                                             .toAnnotationDescription(stereotypes) + "] cannot define a @Named qualifier with a"
                                             + " value. See " + CdiUtil.SPEC_LOCATION + "#named_stereotype",
                                     element);
                    }
                }
            }
        }
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        validatedNamedIfPresent(element, context);
        for (ParameterElement parameter : element.getParameters()) {
            validateElement(parameter, context);
        }
    }

    @Override
    public void visitConstructor(ConstructorElement element, VisitorContext context) {
        for (ParameterElement parameter : element.getParameters()) {
            validateElement(parameter, context);
        }
    }

    @Override
    public void visitField(FieldElement element, VisitorContext context) {
        validatedNamedIfPresent(element, context);
        validateElement(element, context);
    }

    private void validatedNamedIfPresent(Element element, VisitorContext context) {
        if (element.hasAnnotation(AnnotationUtil.ANN_NAME) || element.hasStereotype(AnnotationUtil.ANN_NAME)) {
            element.stringValue(AnnotationUtil.ANN_NAME).ifPresent((name) -> validateIdentifier(name, element, context));
        }
    }

    private static boolean isJavaIdentifier(String name) {
        int start = name.codePointAt(0);
        if (!Character.isJavaIdentifierStart(start)) {
            return false;
        }
        int charCount = Character.charCount(start);
        for (int i = charCount; i < name.length(); i += charCount) {
            int codePoint = name.codePointAt(i);
            if (!Character.isJavaIdentifierPart(codePoint)) {
                return false;
            }
        }
        return true;
    }

    private void validateIdentifier(String name, Element element, VisitorContext visitorContext) {
        if (StringUtils.isNotEmpty(name)) {

            final String[] parts = name.split("\\.");
            for (String part : parts) {
                if (!isJavaIdentifier(part)) {
                    visitorContext.fail(
                            "@Named annotation specifies an invalid name. See " + CdiUtil.SPEC_LOCATION + "#names",
                            element
                    );
                    break;
                }
            }
        }
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
