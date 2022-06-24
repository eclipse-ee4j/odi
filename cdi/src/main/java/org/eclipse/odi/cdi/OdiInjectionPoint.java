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
package org.eclipse.odi.cdi;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.AnnotatedMember;
import jakarta.enterprise.inject.spi.AnnotatedParameter;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.InjectionPoint;
import org.eclipse.odi.cdi.annotated.OdiAnnotatedUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.Set;

@Internal
final class OdiInjectionPoint implements InjectionPoint {
    private final OdiBean<?> bean;
    private final Argument<?> argument;
    private final AnnotationMetadata annotationMetadata;
    private final io.micronaut.inject.InjectionPoint<?> injectionPoint;

    OdiInjectionPoint(OdiBean<?> bean,
                      io.micronaut.inject.InjectionPoint<?> injectionPoint,
                      Argument<?> argument) {
        this.bean = Objects.requireNonNull(bean);
        Objects.requireNonNull(injectionPoint);
        this.argument = Objects.requireNonNull(argument);
        this.annotationMetadata = new AnnotationMetadataHierarchy(
                injectionPoint.getAnnotationMetadata(),
                argument.getAnnotationMetadata()
        );
        this.injectionPoint = injectionPoint;
    }

    @Override
    public Type getType() {
        return argument.asType();
    }

    @Override
    public Set<Annotation> getQualifiers() {
        return AnnotationUtils.synthesizeQualifierAnnotations(annotationMetadata);
    }

    @Override
    public Bean<?> getBean() {
        return bean;
    }

    @Override
    public Member getMember() {
        Annotated annotated = getAnnotated();
        if (annotated instanceof AnnotatedMember) {
            return ((AnnotatedMember<?>) annotated).getJavaMember();
        }
        if (annotated instanceof AnnotatedParameter) {
            return ((AnnotatedParameter<?>) annotated).getDeclaringCallable().getJavaMember();
        }
        throw new IllegalStateException("Not supported!");
    }

    @Override
    public Annotated getAnnotated() {
        return OdiAnnotatedUtils.asAnnotated(injectionPoint, getType());
    }

    @Override
    public boolean isDelegate() {
        return false;
    }

    @Override
    public boolean isTransient() {
        return Modifier.isTransient(getMember().getModifiers());
    }
}
