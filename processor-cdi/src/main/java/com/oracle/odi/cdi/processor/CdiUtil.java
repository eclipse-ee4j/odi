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

import io.micronaut.annotation.processing.visitor.JavaClassElement;
import io.micronaut.annotation.processing.visitor.JavaClassElementHelper;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Instance;

import java.util.List;
import java.util.stream.Collectors;

@Internal
public final class CdiUtil {
    public static final String SPEC_LOCATION = "https://jakarta.ee/specifications/cdi/3.0/jakarta-cdi-spec-3.0.html";

    private CdiUtil() {
    }

    public static String toAnnotationDescription(List<String> annotations) {
        return annotations.stream().map(n -> "@" + NameUtils.getSimpleName(n)).collect(Collectors.joining(" and "));
    }

    private static boolean needsDefaultQualifier(AnnotationMetadata declaredMetadata) {
        return !declaredMetadata.hasStereotype(AnnotationUtil.QUALIFIER);
    }

    public static void visitBeanDefinition(VisitorContext context, Element beanDefinition) {
        AnnotationMetadata annotationMetadata = beanDefinition.getAnnotationMetadata();
        if (annotationMetadata instanceof AnnotationMetadataHierarchy) {
            annotationMetadata = annotationMetadata.getDeclaredMetadata();
        }
        if (needsDefaultQualifier(annotationMetadata)) {
            beanDefinition.annotate(Default.class);
        }
    }

    public static boolean visitInjectPoint(VisitorContext context, TypedElement injectPoint) {
        if (needsDefaultQualifier(injectPoint)) {
            injectPoint.annotate(Default.class);
        }
        return CdiUtil.validateInjectedType(context, injectPoint.getGenericType(), injectPoint);
    }

    public static boolean validateInjectedType(VisitorContext context, ClassElement classElement, Element owningElement) {
        if (classElement.getName().equals(Instance.class.getName()) && isNoGenericType(classElement)) {
            context.fail("jakarta.enterprise.inject.Instance must have a required type parameter specified", owningElement);
            return true;
        }
        if (classElement.getName().equals(Event.class.getName()) && isNoGenericType(classElement)) {
            context.fail("jakarta.enterprise.event.Event must have a required type parameter specified", owningElement);
            return true;
        }
        return false;
    }

    private static boolean isNoGenericType(ClassElement classElement) {
        if (classElement instanceof JavaClassElement) {
            return JavaClassElementHelper.getGenericTypeInfo((JavaClassElement) classElement).isEmpty();
        }
        return false;
    }
}
