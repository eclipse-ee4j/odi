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
package com.oracle.odi.cdi.processor.extensions;

import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.lang.model.types.Type;
import jakarta.enterprise.lang.model.types.TypeVariable;

import java.util.List;
import java.util.stream.Collectors;

final class TypeVariableImpl extends AnnotationTargetImpl implements TypeVariable {
    private final String name;
    private final GenericPlaceholderElement classElement;
    private final VisitorContext visitorContext;

    TypeVariableImpl(String name, GenericPlaceholderElement element, VisitorContext visitorContext, Types types) {
        super(element, types, visitorContext);
        this.name = name;
        this.classElement = element;
        this.visitorContext = visitorContext;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public List<Type> bounds() {
        return classElement.getBounds()
                .stream()
                .map(bound -> TypeFactory.createType(bound, getTypes(), visitorContext))
                .collect(Collectors.toList());
    }
}
