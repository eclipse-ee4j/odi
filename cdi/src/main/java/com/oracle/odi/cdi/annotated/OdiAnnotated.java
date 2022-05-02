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
package com.oracle.odi.cdi.annotated;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import jakarta.enterprise.inject.spi.Annotated;
import jakarta.inject.Inject;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Set;

/**
 * The implementation of {@link Annotated}.
 */
@Internal
public class OdiAnnotated implements Annotated {
    protected final Type type;
    protected final Set<Type> exposedTypes;
    protected final AnnotationMetadata annotationMetadata;

    public OdiAnnotated(Type type, Set<Type> exposedTypes, AnnotationMetadata annotationMetadata) {
        this.type = type;
        this.exposedTypes = exposedTypes;
        this.annotationMetadata = annotationMetadata;
    }

    @Override
    public Type getBaseType() {
        return type;
    }

    @Override
    public Set<Type> getTypeClosure() {
        return exposedTypes;
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
        return annotationMetadata.synthesize(annotationType);
    }

    @Override
    public <T extends Annotation> Set<T> getAnnotations(Class<T> annotationType) {
        return Set.of(annotationMetadata.synthesizeAnnotationsByType(annotationType));
    }

    @Override
    public Set<Annotation> getAnnotations() {
        return Set.of(annotationMetadata.synthesizeAll());
    }

    @Override
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
        if (annotationType == Inject.class) {
            return annotationMetadata.isAnnotationPresent(AnnotationUtil.INJECT);
        }
        return annotationMetadata.isAnnotationPresent(annotationType);
    }
}
