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
package com.oracle.odi.cdi.processor.visitors;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.Alternative;

import java.util.Set;

/**
 * Processes {@link jakarta.enterprise.inject.Alternative} elements and adds required Micronaut annotations.
 */
public class AlternativeVisitor implements TypeElementVisitor<Alternative, Object> {
    private static final AnnotationClassValue<Object> SELECTED_ALTERNATIVE_CONDITION =
            new AnnotationClassValue<>("com.oracle.odi.cdi.condition.SelectedAlternativeCondition");

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return CollectionUtils.setOf(jakarta.enterprise.inject.Alternative.class.getName());
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (!element.hasAnnotation(Bean.class)) {
            element.annotate(Bean.class);
        }
        if (!element.hasAnnotation(Order.class)) {
            // no priority specified so disable by default
            element.annotate(Order.class, (builder) ->
                    builder.value(Ordered.HIGHEST_PRECEDENCE)
            );
            element.annotate(Requires.class, (builder) ->
                    builder.member("condition", SELECTED_ALTERNATIVE_CONDITION)
            );
        }
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
