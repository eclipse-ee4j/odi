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
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.types.ArrayType;
import jakarta.enterprise.lang.model.types.ClassType;
import jakarta.enterprise.lang.model.types.ParameterizedType;
import jakarta.enterprise.lang.model.types.PrimitiveType;
import jakarta.enterprise.lang.model.types.Type;
import jakarta.enterprise.lang.model.types.VoidType;
import jakarta.enterprise.lang.model.types.WildcardType;

import java.util.Objects;

final class TypesImpl implements Types {
    private final VisitorContext visitorContext;
    private final VoidTypeImpl voidType;

    TypesImpl(VisitorContext visitorContext) {
        this.visitorContext = visitorContext;
        this.voidType = new VoidTypeImpl(PrimitiveElement.VOID, this, visitorContext);
    }

    @Override
    public Type of(Class<?> clazz) {
        Objects.requireNonNull(clazz, "Class cannot be null");
        final ClassElement classElement =
                visitorContext.getClassElement(clazz).orElse(null);
        if (classElement == null) {
            throw new IllegalStateException("Type does not exist on the application classpath: " + clazz.getName());
        }
        return new ClassTypeImpl(classElement, this, visitorContext);
    }

    @Override
    public VoidType ofVoid() {
        return voidType;
    }

    @Override
    public PrimitiveType ofPrimitive(PrimitiveType.PrimitiveKind kind) {
        switch (kind) {
            case BOOLEAN: return new PrimitiveTypeImpl(PrimitiveElement.BOOLEAN, this, visitorContext);
            case BYTE: return new PrimitiveTypeImpl(PrimitiveElement.BYTE, this, visitorContext);
            case CHAR: return new PrimitiveTypeImpl(PrimitiveElement.CHAR, this, visitorContext);
            case DOUBLE: return new PrimitiveTypeImpl(PrimitiveElement.DOUBLE, this, visitorContext);
            case FLOAT: return new PrimitiveTypeImpl(PrimitiveElement.FLOAT, this, visitorContext);
            case INT: return new PrimitiveTypeImpl(PrimitiveElement.INT, this, visitorContext);
            case LONG: return new PrimitiveTypeImpl(PrimitiveElement.LONG, this, visitorContext);
            case SHORT: return new PrimitiveTypeImpl(PrimitiveElement.SHORT, this, visitorContext);
            default:
                throw new IllegalStateException("Unsupported primitive type: " + kind);
        }

    }

    @Override
    public ClassType ofClass(String name) {
        final ClassElement classElement = visitorContext.getClassElement(name).orElse(null);
        if (classElement == null) {
            throw new IllegalStateException("Type does not exist on the application classpath: " + name);
        }
        return new ClassTypeImpl(classElement, this, visitorContext);
    }

    @Override
    public ClassType ofClass(ClassInfo clazz) {
        Objects.requireNonNull(clazz, "Class cannot be null");
        return clazz.asType().asClass();
    }

    @Override
    public ArrayType ofArray(Type componentType, int dimensions) {
        return null;
    }

    @Override
    public ParameterizedType parameterized(Class<?> parameterizedType, Class<?>... typeArguments) {
        return null;
    }

    @Override
    public ParameterizedType parameterized(Class<?> parameterizedType, Type... typeArguments) {
        return null;
    }

    @Override
    public ParameterizedType parameterized(ClassType genericType, Type... typeArguments) {
        return null;
    }

    @Override
    public WildcardType wildcardWithUpperBound(Type upperBound) {
        return null;
    }

    @Override
    public WildcardType wildcardWithLowerBound(Type lowerBound) {
        return null;
    }

    @Override
    public WildcardType wildcardUnbounded() {
        return null;
    }
}
