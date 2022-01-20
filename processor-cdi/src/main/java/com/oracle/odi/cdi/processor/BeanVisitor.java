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
package com.oracle.odi.cdi.processor;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.inject.Scope;

import io.micronaut.aop.Around;
import io.micronaut.aop.InterceptorBinding;
import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

/**
 * Customizations for declared beans.
 */
public class BeanVisitor implements TypeElementVisitor<Scope, Object> {
    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Collections.singleton(Scope.class.getName());
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        final MethodElement constructor = element.getPrimaryConstructor().orElse(null);
        if (constructor != null) {
            final List<AnnotationValue<InterceptorBinding>> interceptorBindings
                    = constructor.getAnnotationValuesByType(InterceptorBinding.class);
            if (CollectionUtils.isNotEmpty(interceptorBindings)) {
                // declare binding on type level for constructor binding as well
                for (AnnotationValue<?> value : interceptorBindings) {
                    value.stringValue().ifPresent(n -> {
                        element.annotate(value);
                        if (!element.hasStereotype(Around.class)) {
                            element.annotate(Around.class, (builder) -> {
                                builder.member("proxyTarget", true);
                                builder.member("cacheableLazyTarget", true);
                            });
                        }
                    });
                }
            }
        }
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (element.hasDeclaredAnnotation(Executable.class) && element.isPrivate()) {
            element.removeAnnotation(Executable.class);
        }
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
