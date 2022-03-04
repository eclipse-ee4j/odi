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

import com.oracle.odi.cdi.annotation.reflect.AnnotationReflection;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.reflect.ClassUtils;
import jakarta.enterprise.inject.build.compatible.spi.AnnotationBuilder;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.AnnotationMember;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.types.PrimitiveType;
import jakarta.enterprise.lang.model.types.Type;

import java.lang.annotation.Annotation;
import java.util.Arrays;

final class AnnotationBuilderImpl implements AnnotationBuilder {
    private final AnnotationValueBuilder<?> builder;

    AnnotationBuilderImpl(AnnotationValueBuilder<?> builder) {
        this.builder = builder;
    }

    @Override
    public AnnotationBuilder member(String name, AnnotationMember value) {
        return this;
    }

    @Override
    public AnnotationBuilder member(String name, boolean value) {
        builder.member(name, value);
        return this;
    }

    @Override
    public AnnotationBuilder member(String name, boolean... values) {
        builder.member(name, values);
        return this;
    }

    @Override
    public AnnotationBuilder member(String name, byte value) {
        builder.member(name, value);
        return this;
    }

    @Override
    public AnnotationBuilder member(String name, byte... values) {
        builder.member(name, values);
        return this;
    }

    @Override
    public AnnotationBuilder member(String name, short value) {
        builder.member(name, value);
        return this;
    }

    @Override
    public AnnotationBuilder member(String name, short... values) {
        builder.member(name, values);
        return this;
    }

    @Override
    public AnnotationBuilder member(String name, int value) {
        builder.member(name, value);
        return this;
    }

    @Override
    public AnnotationBuilder member(String name, int... values) {
        builder.member(name, values);
        return this;
    }

    @Override
    public AnnotationBuilder member(String name, long value) {
        builder.member(name, value);
        return this;
    }

    @Override
    public AnnotationBuilder member(String name, long... values) {
        builder.member(name, values);
        return this;
    }

    @Override
    public AnnotationBuilder member(String name, float value) {
        builder.member(name, value);
        return this;
    }

    @Override
    public AnnotationBuilder member(String name, float... values) {
        builder.member(name, values);
        return this;
    }

    @Override
    public AnnotationBuilder member(String name, double value) {
        builder.member(name, value);
        return this;
    }

    @Override
    public AnnotationBuilder member(String name, double... values) {
        builder.member(name, values);
        return this;
    }

    @Override
    public AnnotationBuilder member(String name, char value) {
        builder.member(name, value);
        return this;
    }

    @Override
    public AnnotationBuilder member(String name, char... values) {
        builder.member(name, values);
        return this;
    }

    @Override
    public AnnotationBuilder member(String name, String value) {
        builder.member(name, value);
        return this;
    }

    @Override
    public AnnotationBuilder member(String name, String... values) {
        builder.member(name, values);
        return this;
    }

    @Override
    public AnnotationBuilder member(String name, Enum<?> value) {
        builder.member(name, value);
        return this;
    }

    @Override
    public AnnotationBuilder member(String name, Enum<?>... values) {
        builder.member(name, values);
        return this;
    }

    @Override
    public AnnotationBuilder member(String name, Class<? extends Enum<?>> enumType, String enumValue) {
        builder.member(name, enumValue);
        return this;
    }

    @Override
    public AnnotationBuilder member(String name, Class<? extends Enum<?>> enumType, String... enumValues) {
        builder.member(name, enumValues);
        return this;
    }

    @Override
    public AnnotationBuilder member(String name, ClassInfo enumType, String enumValue) {
        builder.member(name, enumValue);
        return this;
    }

    @Override
    public AnnotationBuilder member(String name, ClassInfo enumType, String... enumValues) {
        builder.member(name, enumValues);
        return this;
    }

    @Override
    public AnnotationBuilder member(String name, Class<?> value) {
        builder.member(name, value);
        return this;
    }

    @Override
    public AnnotationBuilder member(String name, Class<?>... values) {
        builder.member(name, values);
        return this;
    }

    @Override
    public AnnotationBuilder member(String name, ClassInfo value) {
        builder.member(name, new AnnotationClassValue<>(value.name()));
        return this;
    }

    @Override
    public AnnotationBuilder member(String name, ClassInfo... values) {
        builder.member(name,
                Arrays.stream(values)
                        .map(ClassInfo::name)
                        .map(AnnotationClassValue::new)
                        .toArray(AnnotationClassValue[]::new
                        )
        );
        return this;
    }

    @Override
    public AnnotationBuilder member(String name, Type value) {
        switch (value.kind()) {
            case CLASS:
                member(name, value.asClass().declaration());
                break;
            case PRIMITIVE:
                PrimitiveType pt = value.asPrimitive();
                ClassUtils.getPrimitiveType(pt.name()).ifPresent(type -> member(name, type));
                break;
            default:
                return this;
        }
        return this;
    }

    @Override
    public AnnotationBuilder member(String name, Type... values) {
        return null;
    }

    @Override
    public AnnotationBuilder member(String name, AnnotationInfo value) {
        if (value instanceof AnnotationInfoImpl) {
            AnnotationInfoImpl impl = (AnnotationInfoImpl) value;
            builder.member(name, impl.getAnnotationValue());
        }
        return this;
    }

    @Override
    public AnnotationBuilder member(String name, AnnotationInfo... values) {
        AnnotationValue<?>[] annotationValues = Arrays.stream(values).filter((v) -> v instanceof AnnotationInfoImpl)
                .map((v) -> ((AnnotationInfoImpl) v).getAnnotationValue())
                .toArray(AnnotationValue[]::new);
        builder.member(name, annotationValues);
        return this;
    }

    @Override
    public AnnotationBuilder member(String name, Annotation value) {
        builder.member(name, AnnotationReflection.toAnnotationValue(value));
        return this;
    }

    @Override
    public AnnotationBuilder member(String name, Annotation... values) {
        builder.member(name, Arrays.stream(values).map(AnnotationReflection::toAnnotationValue).toArray(AnnotationValue[]::new));
        return this;
    }

    @Override
    public AnnotationInfo build() {
        return new AnnotationInfoImpl(builder.build());
    }
}
