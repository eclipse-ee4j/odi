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
import io.micronaut.core.type.Argument;
import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.AnnotatedParameter;
import jakarta.enterprise.inject.spi.AnnotatedType;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The implementation of {@link AnnotatedMethod}.
 *
 * @param <T> The declaring class type
 */
@Internal
class OdiAnnotatedMethod<T> extends OdiAnnotated implements AnnotatedMethod<T> {

    private final String methodName;
    private final Class[] parameterTypes;
    private final Argument<?>[] arguments;

    OdiAnnotatedMethod(ClassLoader classLoader, Type type, Set<Type> exposedTypes, AnnotationMetadata annotationMetadata,
                              String methodName, Class<?>[] parameterTypes, Argument<?>[] arguments) {
        super(classLoader, type, exposedTypes, annotationMetadata);
        this.methodName = methodName;
        this.parameterTypes = parameterTypes;
        this.arguments = arguments;
    }

    @Override
    public Method getJavaMember() {
        return ReflectionUtils.getRequiredMethod((Class) type, methodName, parameterTypes);
    }

    @Override
    public boolean isStatic() {
        throw new IllegalStateException("Not implemented!");
    }

    @Override
    public AnnotatedType<T> getDeclaringType() {
        throw new IllegalStateException("Not implemented!");
    }

    @Override
    public List<AnnotatedParameter<T>> getParameters() {
        List<AnnotatedParameter<T>> parameters = new ArrayList<>(arguments.length);
        for (int i = 0; i < arguments.length; i++) {
            Argument<?> argument = arguments[i];
            parameters.add(new OdiAnnotatedParameter<>(classLoader, argument.getType(), Set.of(argument.getType()), argument.getAnnotationMetadata(), i, this));
        }
        return parameters;
    }
}
