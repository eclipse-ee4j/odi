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
package org.eclipse.odi.cdi.annotated;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import jakarta.enterprise.inject.spi.AnnotatedCallable;
import jakarta.enterprise.inject.spi.AnnotatedParameter;

import java.lang.reflect.Type;
import java.util.Set;

/**
 * The implementation of {@link AnnotatedParameter}.
 *
 * @param <T> The declaring class type
 */
@Internal
class OdiAnnotatedParameter<T> extends OdiAnnotated implements AnnotatedParameter<T> {

    private final int position;
    private final AnnotatedCallable<T> outer;

    OdiAnnotatedParameter(Type type, Set<Type> exposedTypes, AnnotationMetadata annotationMetadata,
                          int position, AnnotatedCallable<T> outer) {
        super(type, exposedTypes, annotationMetadata);
        this.position = position;
        this.outer = outer;
    }

    @Override
    public int getPosition() {
        return position;
    }

    @Override
    public AnnotatedCallable<T> getDeclaringCallable() {
        return outer;
    }
}
