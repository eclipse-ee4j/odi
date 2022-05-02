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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ReflectionUtils;
import jakarta.enterprise.inject.spi.AnnotatedConstructor;
import jakarta.enterprise.inject.spi.AnnotatedParameter;
import jakarta.enterprise.inject.spi.AnnotatedType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;

/**
 * The implementation of {@link AnnotatedConstructor}.
 *
 * @param <T> The declaring class type
 */
@Internal
public class OdiAnnotatedConstructor<T> extends OdiAnnotated implements AnnotatedConstructor<T> {

    private final Class[] parameterTypes;

    public OdiAnnotatedConstructor(Type type, Set<Type> exposedTypes, AnnotationMetadata annotationMetadata,
                                   Class<?>[] parameterTypes) {
        super(type, exposedTypes, annotationMetadata);
        this.parameterTypes = parameterTypes;
    }

    @Override
    public Constructor<T> getJavaMember() {
        return ReflectionUtils.getRequiredInternalConstructor((Class) type, parameterTypes);
    }

    @Override
    public boolean isStatic() {
        return false;
    }

    @Override
    public AnnotatedType<T> getDeclaringType() {
        return null;
    }

    @Override
    public List<AnnotatedParameter<T>> getParameters() {
        return null;
    }

}
