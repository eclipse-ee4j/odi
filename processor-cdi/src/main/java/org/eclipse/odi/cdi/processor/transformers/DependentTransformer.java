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
package org.eclipse.odi.cdi.processor.transformers;

import java.util.List;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.TypedAnnotationTransformer;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.context.Dependent;

/**
 * Transforms {@link jakarta.enterprise.context.Dependent} to {@link io.micronaut.context.annotation.Bean}.
 */
public class DependentTransformer implements TypedAnnotationTransformer<Dependent> {
    @Override
    public Class<Dependent> annotationType() {
        return Dependent.class;
    }

    @Override
    public List<AnnotationValue<?>> transform(AnnotationValue<Dependent> annotation, VisitorContext visitorContext) {
        return List.of(
                AnnotationValue.builder(Prototype.class).build()
        );
    }
}
