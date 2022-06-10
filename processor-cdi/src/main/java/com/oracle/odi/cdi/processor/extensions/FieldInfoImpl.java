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

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.declarations.FieldInfo;
import jakarta.enterprise.lang.model.types.Type;

final class FieldInfoImpl extends DeclarationInfoImpl implements FieldInfo {
    private final FieldElement fieldElement;
    private final VisitorContext visitorContext;
    private final ClassInfoImpl declaringInfo;

    FieldInfoImpl(ClassInfoImpl declaringInfo, FieldElement element, Types types, VisitorContext visitorContext) {
        super(element, types, visitorContext);
        this.fieldElement = element;
        this.visitorContext = visitorContext;
        this.declaringInfo = declaringInfo;
    }

    @Override
    public Type asType() {
        return type();
    }

    public VisitorContext getVisitorContext() {
        return visitorContext;
    }

    @Override
    public String name() {
        return fieldElement.getName();
    }

    @Override
    public Type type() {
        final ClassElement t = fieldElement.getType();
        return TypeFactory.createType(t, types, visitorContext);
    }

    public FieldElement getElement() {
        return fieldElement;
    }

    @Override
    public boolean isStatic() {
        return fieldElement.isStatic();
    }

    @Override
    public boolean isFinal() {
        return fieldElement.isFinal();
    }

    @Override
    public int modifiers() {
        return toReflectModifiers(fieldElement.getModifiers());
    }

    @Override
    public ClassInfo declaringClass() {
        return declaringInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        FieldInfoImpl other = (FieldInfoImpl) o;
        return annotationMetadata.equals(other.annotationMetadata);
    }

    @Override
    public int hashCode() {
        return annotationMetadata.hashCode();
    }
}
