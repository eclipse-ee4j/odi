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
import io.micronaut.core.reflect.ReflectionUtils;
import jakarta.enterprise.inject.spi.AnnotatedField;
import jakarta.enterprise.inject.spi.AnnotatedType;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Set;

/**
 * The implementation of {@link AnnotatedField}.
 *
 * @param <T> The declaring class type
 */
@Internal
public class OdiAnnotatedField<T> extends OdiAnnotated implements AnnotatedField<T> {

    private final String fieldName;

    public OdiAnnotatedField(Type type, Set<Type> exposedTypes, AnnotationMetadata annotationMetadata, String fieldName) {
        super(type, exposedTypes, annotationMetadata);
        this.fieldName = fieldName;
    }

    @Override
    public Field getJavaMember() {
        return ReflectionUtils.getRequiredField((Class) type, fieldName);
    }

    @Override
    public boolean isStatic() {
        return false;
    }

    @Override
    public AnnotatedType<T> getDeclaringType() {
        return null;
    }
}
