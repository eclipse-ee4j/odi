/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.micronaut.annotation.processing.visitor;

import io.micronaut.annotation.processing.AnnotationUtils;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.VisitorContext;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import java.util.List;

/**
 * Helper to bypass package access.
 */
public class JavaGenericPlaceholderElementHelper {

    public static AnnotationMetadata getGenericAnnotationMetadata(ClassElement classElement, VisitorContext visitorContext) {
        if (classElement instanceof JavaGenericPlaceholderElement) {
            JavaVisitorContext javaVisitorContext = (JavaVisitorContext) visitorContext;
            TypeVariable realTypeVariable = ((JavaGenericPlaceholderElement) classElement).realTypeVariable;
            AnnotationUtils annotationUtils = javaVisitorContext.getAnnotationUtils();
            Element element = realTypeVariable.asElement();
            return annotationUtils.getAnnotationMetadata(element);
        }
        return AnnotationMetadata.EMPTY_METADATA;
    }

    public static Object getNativeType(io.micronaut.inject.ast.Element element) {
        if (element instanceof JavaGenericPlaceholderElement) {
            TypeVariable realTypeVariable = ((JavaGenericPlaceholderElement) element).realTypeVariable;
            return realTypeVariable.asElement();
        }
        return element.getNativeType();
    }

    public static boolean isEmptyUpperBoundOfWildcard(ClassElement classElement, int boundIndex) {
        if (classElement instanceof JavaClassElement) {
            WildcardType wildcardType = (WildcardType) ((JavaClassElement) classElement).typeArguments.get(boundIndex);
            return wildcardType.getExtendsBound() == null;
        }
        return false;
    }

    public static boolean isEmptyLowerBoundOfWildcard(ClassElement classElement, int boundIndex) {
        if (classElement instanceof JavaClassElement) {
            WildcardType wildcardType = (WildcardType) ((JavaClassElement) classElement).typeArguments.get(boundIndex);
            return wildcardType.getSuperBound() == null;
        }
        return false;
    }

    public static AnnotationMetadata getBounds(ClassElement classElement, VisitorContext visitorContext) {
        if (classElement instanceof JavaClassElement) {
            List<? extends TypeParameterElement> typeParameters = ((JavaClassElement) classElement).classElement.getTypeParameters();
            for (TypeParameterElement typeParameter : typeParameters) {
                for (TypeMirror bound : typeParameter.getBounds()) {
                    if (bound instanceof DeclaredType) {
                        DeclaredType declaredType = (DeclaredType) bound;
                    }
                }
            }
        }
        if (classElement instanceof JavaGenericPlaceholderElement) {
            TypeVariable realTypeVariable = ((JavaGenericPlaceholderElement) classElement).realTypeVariable;
        }
        return AnnotationMetadata.EMPTY_METADATA;
    }
}
