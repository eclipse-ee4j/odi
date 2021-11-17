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

import java.util.List;
import java.util.stream.Collectors;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.types.ParameterizedType;
import jakarta.enterprise.lang.model.types.Type;

final class ParameterizedTypeImpl extends AnnotationTargetImpl implements ParameterizedType {

    private final VisitorContext visitorContext;
    private final ClassElement classElement;

    ParameterizedTypeImpl(ClassElement element, Types types, VisitorContext visitorContext) {
        super(element, types);
        this.visitorContext = visitorContext;
        this.classElement = element;
    }

    @Override
    public ClassInfo declaration() {
        return new ClassInfoImpl(
                visitorContext.getClassElement(getElement().getName())
                        .orElseThrow(() -> new IllegalStateException("Class not found on classpath: " + getElement().getName())),
                getTypes(),
                visitorContext);
    }

    @Override
    public List<Type> typeArguments() {
        // TODO: nested parameter types, type variables etc.
        return classElement.getTypeArguments().values()
                .stream().map(ce -> TypeFactory.createType(classElement, getTypes(), visitorContext))
                .collect(Collectors.toUnmodifiableList());
    }
}
