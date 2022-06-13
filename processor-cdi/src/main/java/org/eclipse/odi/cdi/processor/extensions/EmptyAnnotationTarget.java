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
package org.eclipse.odi.cdi.processor.extensions;

import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.AnnotationTarget;
import jakarta.enterprise.lang.model.declarations.DeclarationInfo;
import jakarta.enterprise.lang.model.types.Type;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Predicate;

/**
 * Empty implementation of {@link AnnotationTarget}.
 */
final class EmptyAnnotationTarget implements AnnotationTarget {

    public static final AnnotationTarget INSTANCE = new EmptyAnnotationTarget();

    @Override
    public boolean isDeclaration() {
        throw new IllegalStateException("Not supported!");
    }

    @Override
    public boolean isType() {
        throw new IllegalStateException("Not supported!");
    }

    @Override
    public DeclarationInfo asDeclaration() {
        throw new IllegalStateException("Not supported!");
    }

    @Override
    public Type asType() {
        throw new IllegalStateException("Not supported!");
    }

    @Override
    public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
        return false;
    }

    @Override
    public boolean hasAnnotation(Predicate<AnnotationInfo> predicate) {
        return false;
    }

    @Override
    public <T extends Annotation> AnnotationInfo annotation(Class<T> annotationType) {
        return null;
    }

    @Override
    public <T extends Annotation> Collection<AnnotationInfo> repeatableAnnotation(Class<T> annotationType) {
        return Collections.emptyList();
    }

    @Override
    public Collection<AnnotationInfo> annotations(Predicate<AnnotationInfo> predicate) {
        return Collections.emptyList();
    }

    @Override
    public Collection<AnnotationInfo> annotations() {
        return Collections.emptyList();
    }
}
