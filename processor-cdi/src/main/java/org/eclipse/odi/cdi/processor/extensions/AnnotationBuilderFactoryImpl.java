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
package org.eclipse.odi.cdi.processor.extensions;

import java.lang.annotation.Annotation;
import java.util.Objects;

import io.micronaut.core.annotation.AnnotationValue;
import jakarta.enterprise.inject.build.compatible.spi.AnnotationBuilder;
import jakarta.enterprise.inject.build.compatible.spi.AnnotationBuilderFactory;
import jakarta.enterprise.lang.model.declarations.ClassInfo;


final class AnnotationBuilderFactoryImpl implements AnnotationBuilderFactory {
    @Override
    public AnnotationBuilder create(Class<? extends Annotation> annotationType) {
        Objects.requireNonNull(annotationType, "Annotation cannot be null");
        return new AnnotationBuilderImpl(AnnotationValue.builder(annotationType));
    }

    @Override
    public AnnotationBuilder create(ClassInfo annotationType) {
        Objects.requireNonNull(annotationType, "Annotation cannot be null");
        return new AnnotationBuilderImpl(AnnotationValue.builder(annotationType.name()));
    }
}
