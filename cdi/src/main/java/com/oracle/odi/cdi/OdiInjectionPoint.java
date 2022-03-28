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
package com.oracle.odi.cdi;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.InjectionPoint;

import java.lang.annotation.Annotation;
import java.lang.reflect.Member;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Internal
final class OdiInjectionPoint implements InjectionPoint {
    private final OdiBean<?> bean;
    private final Argument<?> argument;
    private final AnnotationMetadata annotationMetadata;

    OdiInjectionPoint(
            OdiBean<?> bean,
            io.micronaut.inject.InjectionPoint<?> injectionPoint,
            Argument<?> argument) {
        this.bean = Objects.requireNonNull(bean);
        Objects.requireNonNull(injectionPoint);
        this.argument = Objects.requireNonNull(argument);
        this.annotationMetadata = new AnnotationMetadataHierarchy(
                injectionPoint.getAnnotationMetadata(),
                argument.getAnnotationMetadata()
        );
    }

    @Override
    public Type getType() {
        if (argument.getTypeParameters().length == 0) {
            return argument.getType();
        }
        return argument.asParameterizedType();
    }

    @Override
    public Set<Annotation> getQualifiers() {
        return annotationMetadata.getAnnotationTypesByStereotype(MetaAnnotationSupport.META_ANNOTATION_QUALIFIER)
                .stream().map(annotationMetadata::synthesize)
                .collect(Collectors.toSet());
    }

    @Override
    public Bean<?> getBean() {
        return bean;
    }

    @Override
    public Member getMember() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Annotated getAnnotated() {
        return new OdiAnnotated(
                getType(),
                Set.of(getType()),
                annotationMetadata
        );
    }

    @Override
    public boolean isDelegate() {
        return false;
    }

    @Override
    public boolean isTransient() {
        return false;
    }
}
