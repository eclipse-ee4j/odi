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
package com.oracle.odi.cdi.processor.transformers;

import java.util.List;

import io.micronaut.context.annotation.Bean;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.TypedAnnotationTransformer;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.Default;

/**
 * Makes components annotated with {@link Default} beans.
 */
public class DefaultQualifierTransformer implements TypedAnnotationTransformer<Default> {
    @Override
    public Class<Default> annotationType() {
        return Default.class;
    }

    @Override
    public List<AnnotationValue<?>> transform(AnnotationValue<Default> annotation, VisitorContext visitorContext) {
        return List.of(
                AnnotationValue.builder(Bean.class).build(),
                AnnotationValue.builder(Default.class).build()
        );
    }
}