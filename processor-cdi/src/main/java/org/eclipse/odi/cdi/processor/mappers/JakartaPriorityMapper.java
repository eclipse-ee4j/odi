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
package org.eclipse.odi.cdi.processor.mappers;

import java.util.Collections;
import java.util.List;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Order;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.annotation.Priority;

/**
 * Transforms {@link jakarta.annotation.Priority} to {@link io.micronaut.core.annotation.Order}.
 */
public class JakartaPriorityMapper implements TypedAnnotationMapper<Priority> {
    @Override
    public Class<Priority> annotationType() {
        return Priority.class;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Priority> annotation, VisitorContext visitorContext) {
        final int priority = annotation.intValue().orElse(0);
        // priority annotation models the inverse with higher number being higher priority
        final int negated = -priority;
        return Collections.singletonList(
                AnnotationValue.builder(Order.class)
                        .value(negated)
                        .build()
        );
    }
}
