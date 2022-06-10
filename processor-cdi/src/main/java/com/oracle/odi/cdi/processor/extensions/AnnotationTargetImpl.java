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

import io.micronaut.annotation.processing.visitor.JavaVisitorContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.AnnotationTarget;

import javax.lang.model.element.PackageElement;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.function.Predicate;

abstract class AnnotationTargetImpl implements AnnotationTarget, AnnotationMetadataProvider {

    @SuppressWarnings("checkstyle:VisibilityModifier")
    final AnnotationMetadata annotationMetadata;
    final VisitorContext visitorContext;
    @SuppressWarnings("checkstyle:VisibilityModifier")
    final Types types;
    private final AnnotationTarget annotationTargetDelegate;

    protected AnnotationTargetImpl(AnnotationMetadata annotationMetadata, Types types, VisitorContext visitorContext) {
        this.annotationMetadata = annotationMetadata;
        this.visitorContext = visitorContext;
        this.types = types;
        // TODO: select impl based on some option
        if (annotationMetadata instanceof Element && visitorContext instanceof JavaVisitorContext) {
            if (annotationMetadata instanceof PrimitiveElement) {
                this.annotationTargetDelegate = EmptyAnnotationTarget.INSTANCE;
            } else {
                this.annotationTargetDelegate = new AnnotatedConstructAnnotationTarget((Element) annotationMetadata, types, (JavaVisitorContext) visitorContext);
            }
        } else {
            this.annotationTargetDelegate = new MicronautAnnotationTarget(annotationMetadata, types);
        }
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    @Override
    public boolean isDeclaration() {
        return annotationMetadata instanceof PackageElement
                || annotationMetadata instanceof ClassElement
                || annotationMetadata instanceof FieldElement
                || annotationMetadata instanceof MethodElement
                || annotationMetadata instanceof ParameterElement;
    }

    @Override
    public boolean isType() {
        return annotationMetadata instanceof TypedElement;
    }

    @Override
    public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
        return annotationTargetDelegate.hasAnnotation(annotationType);
    }

    @Override
    public boolean hasAnnotation(Predicate<AnnotationInfo> predicate) {
        return annotationTargetDelegate.hasAnnotation(predicate);
    }

    @Override
    public <T extends Annotation> AnnotationInfo annotation(Class<T> annotationType) {
        return annotationTargetDelegate.annotation(annotationType);
    }

    @Override
    public <T extends Annotation> Collection<AnnotationInfo> repeatableAnnotation(Class<T> annotationType) {
        return annotationTargetDelegate.repeatableAnnotation(annotationType);
    }

    @Override
    public Collection<AnnotationInfo> annotations(Predicate<AnnotationInfo> predicate) {
        return annotationTargetDelegate.annotations(predicate);
    }

    @Override
    public Collection<AnnotationInfo> annotations() {
        return annotationTargetDelegate.annotations();
    }

    public Types getTypes() {
        return types;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AnnotationTargetImpl other = (AnnotationTargetImpl) o;
        return annotationTargetDelegate.equals(other.annotationTargetDelegate);
    }

    @Override
    public int hashCode() {
        return annotationTargetDelegate.hashCode();
    }
}
