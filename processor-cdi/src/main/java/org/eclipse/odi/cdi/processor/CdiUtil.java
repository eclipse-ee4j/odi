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
package org.eclipse.odi.cdi.processor;

import io.micronaut.annotation.processing.visitor.JavaClassElement;
import io.micronaut.annotation.processing.visitor.JavaClassElementHelper;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptor;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Internal CDI utils.
 */
@Internal
public final class CdiUtil {
    public static final String SPEC_LOCATION = "https://jakarta.ee/specifications/cdi/3.0/jakarta-cdi-spec-3.0.html";

    private CdiUtil() {
    }

    public static boolean validateBeanDefinition(VisitorContext context, Class<? extends Annotation> annotationType, ClassElement classElement) {
        if (!org.eclipse.odi.cdi.processor.AnnotationUtil.hasBeanDefiningAnnotation(classElement)) {
            context.fail(
                    "Class with " + annotationType.getSimpleName() + " must specify a bean definition annotation. See "
                            + CdiUtil.SPEC_LOCATION
                            + "#bean_defining_annotations",
                    classElement
            );
            return true;
        }
        return false;
    }

    public static boolean validateNoInterceptor(VisitorContext context,
                                                Class<? extends Annotation> annotationType,
                                                MethodElement methodElement) {
        if (methodElement.getOwningType().hasDeclaredAnnotation(Interceptor.class)) {
            context.fail("Interceptors cannot have methods annotated with @" + annotationType.getSimpleName(), methodElement);
            return true;
        }
        return false;
    }

    public static boolean validateMethodExtraAnnotations(VisitorContext context,
                                                         Class<? extends Annotation> annotationType,
                                                         MethodElement methodElement) {
        String prefix = methodElement instanceof ConstructorElement ? "Constructors" : "Methods";
        for (Class<? extends Annotation> annotationToCheck : new Class[]{Inject.class, Produces.class}) {
            if (!annotationToCheck.equals(annotationType) && methodElement.hasDeclaredAnnotation(annotationToCheck)) {
                context.fail(prefix + " annotated with @" + annotationType.getSimpleName() + " cannot be annotated with @" + annotationToCheck.getSimpleName(), methodElement);
                return true;
            }
        }
        return false;
    }

    public static boolean validateParameterExtraAnnotations(VisitorContext context,
                                                            Class<? extends Annotation> annotationType,
                                                            MethodElement methodElement,
                                                            ParameterElement parameterElement) {
        String prefix = methodElement instanceof ConstructorElement ? "Constructors" : "Methods";
        for (Class<? extends Annotation> annotationToCheck : new Class[]{Disposes.class, Observes.class, ObservesAsync.class}) {
            if (!annotationToCheck.equals(annotationType) && parameterElement.hasDeclaredAnnotation(annotationToCheck)) {
                context.fail(prefix + " with parameters annotated with @" + annotationType.getSimpleName() + " cannot be annotated with @" + annotationToCheck.getSimpleName(), parameterElement);
                return true;
            }
        }
        return false;
    }

    public static boolean validateMethodNoSpecialParameters(VisitorContext context,
                                                            String annotationSimpleName,
                                                            MethodElement methodElement,
                                                            ParameterElement parameterElement) {
        String prefix = methodElement instanceof ConstructorElement ? "Constructors" : "Methods";
        for (Class<? extends Annotation> annotationToCheck : new Class[]{Disposes.class, Observes.class, ObservesAsync.class}) {
            if (parameterElement.hasDeclaredAnnotation(annotationToCheck)) {
                context.fail(prefix + " annotated with @" + annotationSimpleName + " cannot define parameters annotated with @" + annotationToCheck.getSimpleName(), parameterElement);
                return true;
            }
        }
        return false;
    }

    public static boolean validateField(VisitorContext context,
                                        Class<? extends Annotation> annotationType,
                                        FieldElement fieldElement) {
        if (!io.micronaut.core.annotation.AnnotationUtil.INJECT.equals(annotationType.getName())
                && fieldElement.hasDeclaredAnnotation(io.micronaut.core.annotation.AnnotationUtil.INJECT)) {
            context.fail("Fields annotated with @" + annotationType.getSimpleName() + " cannot be annotated with @Inject", fieldElement);
            return true;
        }
        return false;
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
