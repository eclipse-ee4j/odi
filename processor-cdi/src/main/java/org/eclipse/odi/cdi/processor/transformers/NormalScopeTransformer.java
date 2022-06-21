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

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.TypedAnnotationTransformer;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.runtime.context.scope.ScopedProxy;
import jakarta.enterprise.context.NormalScope;

import java.util.Arrays;
import java.util.List;

/**
 * Transforms {@link jakarta.enterprise.context.NormalScope} to
 * {@link io.micronaut.runtime.context.scope.ScopedProxy and {@link jakarta.inject.Scope}}.
 */
public class NormalScopeTransformer implements TypedAnnotationTransformer<NormalScope> {
    @Override
    public List<AnnotationValue<?>> transform(
            AnnotationValue<NormalScope> annotation,
            VisitorContext visitorContext) {
        // NormalScope beans are proxied, lazy and scoped by default
        // which translates to the scoped proxy scope of Micronaut
        return Arrays.asList(
                annotation,
                AnnotationValue.builder(ScopedProxy.class).build(),
                AnnotationValue.builder(io.micronaut.core.annotation.AnnotationUtil.SCOPE).build()
        );
    }

    @Override
    public Class<NormalScope> annotationType() {
        return NormalScope.class;
    }
}
