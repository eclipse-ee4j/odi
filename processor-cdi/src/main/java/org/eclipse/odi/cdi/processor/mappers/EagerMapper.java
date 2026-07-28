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

import io.micronaut.context.annotation.Context;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.context.Eager;

import java.util.Collections;
import java.util.List;

/**
 * Maps {@link Eager} to Micronaut eager initialization.
 */
public class EagerMapper implements TypedAnnotationMapper<Eager> {

    @Override
    public Class<Eager> annotationType() {
        return Eager.class;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Eager> annotation, VisitorContext visitorContext) {
        return Collections.singletonList(AnnotationValue.builder(Context.class).build());
    }
}
