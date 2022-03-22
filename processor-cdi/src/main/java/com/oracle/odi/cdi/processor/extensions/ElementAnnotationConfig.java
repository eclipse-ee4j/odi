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
package com.oracle.odi.cdi.processor.extensions;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.lang.model.element.TypeElement;

import com.oracle.odi.cdi.annotation.reflect.AnnotationReflection;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.processing.JavaModelUtils;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.build.compatible.spi.DeclarationConfig;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.AnnotationTarget;

/**
 * Internal interface for configured elements.
 */
interface ElementAnnotationConfig extends DeclarationConfig, AnnotationTarget {
    Element getElement();

    VisitorContext getVisitorContext();

    @Override
    default boolean hasAnnotation(Class<? extends Annotation> annotationType) {
        if (annotationType == null) {
            return false;
        }
        return getElement().hasAnnotation(annotationType);
    }

    @Override
    default boolean hasAnnotation(Predicate<AnnotationInfo> predicate) {
        if (predicate == null) {
            return false;
        }
        return annotations().stream().anyMatch(predicate);
    }

    @Override
    default <T extends Annotation> AnnotationInfo annotation(Class<T> annotationType) {
        if (annotationType != null) {
            final AnnotationValue<T> av = getElement().getAnnotation(annotationType);
            if (av != null) {
                return new AnnotationInfoImpl(av);
            }
        }
        return null;
    }

    @Override
    default <T extends Annotation> Collection<AnnotationInfo> repeatableAnnotation(Class<T> annotationType) {
        if (annotationType != null) {
            return getElement().getAnnotationValuesByType(annotationType)
                    .stream().map(AnnotationInfoImpl::new)
                    .collect(Collectors.toUnmodifiableList());

        }
        return Collections.emptyList();
    }

    @Override
    default Collection<AnnotationInfo> annotations(Predicate<AnnotationInfo> predicate) {
        if (predicate == null) {
            return Collections.emptyList();
        }
        return annotations().stream().filter(predicate).collect(Collectors.toUnmodifiableList());
    }

    @Override
    default Collection<AnnotationInfo> annotations() {
        final Set<String> annotationNames = getElement().getAnnotationNames();
        Collection<AnnotationInfo> annotations = new LinkedHashSet<>(annotationNames.size());
        for (String annotationName : annotationNames) {
            final AnnotationValue<Annotation> av = getElement().getAnnotation(annotationName);
            annotations.add(new AnnotationInfoImpl(av));
        }
        return Collections.unmodifiableCollection(annotations);
    }

    @Override
    default DeclarationConfig addAnnotation(Class<? extends Annotation> annotationType) {
        if (annotationType != null) {
            getElement().annotate(annotationType);
        }
        return this;
    }

    @Override
    default DeclarationConfig addAnnotation(AnnotationInfo annotation) {
        if (annotation != null) {
            if (annotation instanceof AnnotationInfoImpl) {
                final AnnotationValue<?> av = ((AnnotationInfoImpl) annotation).getAnnotationValue();
                AnnotationConfigSupport.annotate(getElement(), av);
            }
        }
        return this;
    }

    @Override
    default DeclarationConfig addAnnotation(Annotation annotation) {
        if (annotation != null) {
            final AnnotationValue<Annotation> v = AnnotationReflection.toAnnotationValue(annotation);
            AnnotationConfigSupport.annotate(getElement(), v);
        }
        return this;
    }

    @Override
    default DeclarationConfig removeAnnotation(Predicate<AnnotationInfo> predicate) {
        final Element element = getElement();
        element.removeAnnotationIf(annotationValue ->
                predicate.test(new AnnotationInfoImpl(annotationValue))
        );
        final Object nativeType = element.getNativeType();
        // workaround to Micronaut bug, remove once fixed
        if (nativeType instanceof javax.lang.model.element.Element) {
            javax.lang.model.element.Element javaElement = (javax.lang.model.element.Element) nativeType;
            while (javaElement != null && !(JavaModelUtils.isClassOrInterface(javaElement) || JavaModelUtils.isRecord(javaElement) || JavaModelUtils.isEnum(javaElement))) {
                javaElement = javaElement.getEnclosingElement();
            }
            if (javaElement instanceof TypeElement) {
                final String declaring = ((TypeElement) javaElement).getQualifiedName().toString();
                AbstractAnnotationMetadataBuilder.addMutatedMetadata(declaring, nativeType, element.getAnnotationMetadata());
            }
        }
        return this;
    }

    @Override
    default DeclarationConfig removeAllAnnotations() {
        removeAnnotation(annotationValue -> true);
        return this;
    }
}
