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
package com.oracle.odi.cdi.processor.mappers;

import java.util.Collections;
import java.util.List;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.interceptor.AroundInvoke;

/**
 * Maps {@link AroundInvoke} to be {@link Executable}.
 */
public class AroundInvokeMapper implements TypedAnnotationMapper<AroundInvoke> {
    @Override
    public Class<AroundInvoke> annotationType() {
        return AroundInvoke.class;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<AroundInvoke> annotation, VisitorContext visitorContext) {
        return Collections.singletonList(
                AnnotationValue.builder(Executable.class).build()
        );
    }
}