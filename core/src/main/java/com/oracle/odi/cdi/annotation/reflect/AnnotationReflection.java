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
package com.oracle.odi.cdi.annotation.reflect;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.AnnotationValueProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import jakarta.enterprise.util.Nonbinding;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Queue;

/**
 * Utility methods for performing reflection on annotation values.
 */
@Internal
public final class AnnotationReflection {
    private AnnotationReflection() {
    }

    /**
     * Converts an annotation to an annotation value, using reflection if necessary.
     * @param annotation The annotation
     * @param <T> The generic type
     * @return The annotation value
     */
    public static <T extends Annotation> AnnotationValue<T> toAnnotationValue(T annotation) {
        if (annotation instanceof AnnotationValueProvider) {
            //noinspection unchecked
            return ((AnnotationValueProvider<T>) annotation).annotationValue();
        } else {
            @SuppressWarnings("unchecked")
            Class<T> annotationType = (Class<T>) findAnnotationType(annotation);
            final AnnotationValueBuilder<T> builder = AnnotationValue.builder(annotationType);

            toAnnotationValues(
                    annotationType,
                    builder,
                    annotation
            );

            return builder.build();
        }
    }

    private static Class<? extends Annotation> findAnnotationType(Annotation annotation) {
        Class<? extends Annotation> annotationType = annotation.annotationType();

        Queue<Class<?>> candidates = new ArrayDeque<>();
        candidates.add(annotation.getClass());
        while (!candidates.isEmpty()) {
            Class<?> candidate = candidates.remove();

            if (candidate.isAnnotation()) {
                annotationType = (Class<? extends Annotation>) candidate;
                break;
            }

            Collections.addAll(candidates, candidate.getInterfaces());
        }

        if (annotationType == null) {
            throw new IllegalArgumentException("Not an annotation: " + annotation);
        }

        return annotationType;
    }

    private static void toAnnotationValues(Class<? extends Annotation> type,
                                           AnnotationValueBuilder<? extends Annotation> builder,
                                           Annotation value) {
        for (Method attribute : type.getDeclaredMethods()) {
            try {
                attribute.setAccessible(true);
                if (!attribute.isAnnotationPresent(Nonbinding.class)) {
                    build(builder, attribute.getName(), attribute.invoke(value), attribute.getDefaultValue());
                }
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Cannot access: " + attribute.getName(), e);
            }
        }
    }

    private static void build(AnnotationValueBuilder<?> builder, String name, Object value, @Nullable Object defaultValue) {
        if (value.equals(defaultValue)) {
            return;
        }
        if (value instanceof Boolean) {
            builder.member(name, (Boolean) value);
        } else if (value instanceof Byte) {
            builder.member(name, (Byte) value);
        } else if (value instanceof Short) {
            builder.member(name, (Short) value);
        } else if (value instanceof Integer) {
            builder.member(name, (Integer) value);
        } else if (value instanceof Long) {
            builder.member(name, (Long) value);
        } else if (value instanceof Float) {
            builder.member(name, (Float) value);
        } else if (value instanceof Double) {
            builder.member(name, (Double) value);
        } else if (value instanceof Character) {
            builder.member(name, (Character) value);
        } else if (value instanceof String) {
            builder.member(name, (String) value);
        } else if (value instanceof Enum) {
            builder.member(name, ((Enum<?>) value).name());
        } else if (value instanceof Class) {
            builder.member(name, (Class<?>) value);
        } else if (value.getClass().isArray()) {
            if (value instanceof String[]) {
                builder.member(name, (String[]) value);
            } else if (value instanceof int[]) {
                builder.member(name, (int[]) value);
            } else if (value instanceof Class[]) {
                builder.member(name, (Class<?>[]) value);
            } else if (value instanceof Enum[]) {
                builder.member(name, (Enum<?>[]) value);
            } else if (value instanceof Annotation[]) {
                final AnnotationValue<?>[] annotationValues =
                        Arrays.stream((Annotation[]) value)
                                .map(AnnotationReflection::toAnnotationValue)
                                .toArray(AnnotationValue[]::new);
                builder.member(name, annotationValues);
            } else if (value instanceof long[]) {
                builder.member(name, (long[]) value);
            } else if (value instanceof boolean[]) {
                builder.member(name, (boolean[]) value);
            } else if (value instanceof byte[]) {
                builder.member(name, (byte[]) value);
            } else if (value instanceof double[]) {
                builder.member(name, (double[]) value);
            } else if (value instanceof float[]) {
                builder.member(name, (float[]) value);
            } else if (value instanceof short[]) {
                builder.member(name, (short[]) value);
            } else if (value instanceof char[]) {
                builder.member(name, (char[]) value);
            }
        } else if (value instanceof Annotation) {
            builder.member(name, toAnnotationValue((Annotation) value));
        } else {
            throw new IllegalArgumentException("Unknown annotation attribute value: " + value);
        }
    }
}
