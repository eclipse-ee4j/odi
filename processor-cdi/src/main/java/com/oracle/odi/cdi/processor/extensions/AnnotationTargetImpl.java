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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.lang.model.element.PackageElement;

import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.TypedElement;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.AnnotationTarget;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Qualifier;
import jakarta.inject.Scope;
import jakarta.inject.Singleton;

abstract class AnnotationTargetImpl implements AnnotationTarget {
    private static final Map<String, String> META_ANNOTATIONS = new HashMap<>(10);

    static {
        META_ANNOTATIONS.put(Singleton.class.getName(), AnnotationUtil.SINGLETON);
        META_ANNOTATIONS.put(Scope.class.getName(), AnnotationUtil.SCOPE);
        META_ANNOTATIONS.put(Qualifier.class.getName(), AnnotationUtil.QUALIFIER);
        META_ANNOTATIONS.put(Named.class.getName(), AnnotationUtil.NAMED);
        META_ANNOTATIONS.put(Inject.class.getName(), AnnotationUtil.INJECT);
    }

    @SuppressWarnings("checkstyle:VisibilityModifier") final Element element;
    @SuppressWarnings("checkstyle:VisibilityModifier") final Types types;

    protected AnnotationTargetImpl(Element element, Types types) {
        this.element = element;
        this.types = types;
    }

    @Override
    public boolean isDeclaration() {
        return element instanceof PackageElement
                || element instanceof ClassElement
                || element instanceof FieldElement
                || element instanceof MethodElement
                || element instanceof ParameterElement;
    }

    @Override
    public boolean isType() {
        return element instanceof TypedElement;
    }

    @Override
    public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
        if (annotationType == null) {
            throw new IllegalArgumentException("Argument annotationType cannot be null");
        }
        final String n = META_ANNOTATIONS.get(annotationType.getName());
        if (n != null) {
            return element.hasAnnotation(n);
        }
        return element.hasAnnotation(annotationType);
    }

    @Override
    public boolean hasAnnotation(Predicate<AnnotationInfo> predicate) {
        if (predicate == null) {
            throw new IllegalArgumentException("Argument predicate cannot be null");
        }

        final Set<String> annotationNames = element.getAnnotationNames();
        for (String annotationName : annotationNames) {
            final AnnotationValue<Annotation> av = element
                    .getAnnotation(META_ANNOTATIONS.getOrDefault(annotationName, annotationName));
            if (av != null) {
                if (predicate.test(new AnnotationInfoImpl(av))) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public <T extends Annotation> AnnotationInfo annotation(Class<T> annotationType) {
        if (annotationType == null) {
            throw new IllegalArgumentException("Argument annotationType cannot be null");
        }
        String annotationName = annotationType.getName();
        final String n = META_ANNOTATIONS.getOrDefault(annotationName, annotationName);
        final AnnotationValue<T> av;
        if (n != null) {
            av = element.getAnnotation(n);
        } else {
            av = element.getAnnotation(annotationType);
        }

        if (av != null) {
            return new AnnotationInfoImpl(av);
        }
        return null;
    }

    @Override
    public <T extends Annotation> Collection<AnnotationInfo> repeatableAnnotation(Class<T> annotationType) {
        if (annotationType == null) {
            throw new IllegalArgumentException("Argument annotationType cannot be null");
        }

        return element.getAnnotationValuesByType(annotationType)
                .stream().map(AnnotationInfoImpl::new)
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public Collection<AnnotationInfo> annotations(Predicate<AnnotationInfo> predicate) {
        if (predicate == null) {
            throw new IllegalArgumentException("Argument predicate cannot be null");
        }
        return element.getAnnotationNames().stream()
                .map(name -> new AnnotationInfoImpl(
                        element.getAnnotation(META_ANNOTATIONS.getOrDefault(name, name))
                ))
                .filter(predicate)
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public Collection<AnnotationInfo> annotations() {
        return element.getAnnotationNames().stream()
                .map(name -> new AnnotationInfoImpl(element.getAnnotation(META_ANNOTATIONS.getOrDefault(name, name))))
                .collect(Collectors.toUnmodifiableList());
    }

    public Element getElement() {
        return element;
    }

    public Types getTypes() {
        return types;
    }
}
