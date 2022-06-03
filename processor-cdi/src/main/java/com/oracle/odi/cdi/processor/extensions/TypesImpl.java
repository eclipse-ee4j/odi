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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.declarations.DeclarationInfo;
import jakarta.enterprise.lang.model.types.ArrayType;
import jakarta.enterprise.lang.model.types.ClassType;
import jakarta.enterprise.lang.model.types.ParameterizedType;
import jakarta.enterprise.lang.model.types.PrimitiveType;
import jakarta.enterprise.lang.model.types.Type;
import jakarta.enterprise.lang.model.types.VoidType;
import jakarta.enterprise.lang.model.types.WildcardType;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

final class TypesImpl implements Types {
    private final VisitorContext visitorContext;
    private final VoidTypeImpl voidType;

    private final TypesWildcardUnbounded wildcardUnbounded = new TypesWildcardUnbounded();

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
        if (componentType instanceof ClassTypeImpl) {
            ClassTypeImpl cit = (ClassTypeImpl) componentType;
            ClassElement classElement = cit.getClassElement();
            for (int i = 0; i < dimensions; i++) {
                classElement = classElement.toArray();
            }
            return new ArrayTypeImpl(
                    classElement,
                    this,
                    visitorContext
            );
        } else {
            ArrayType arrayType = null;
            for (int i = 0; i < dimensions; i++) {
                if (arrayType == null) {
                    arrayType = new TypesArrayType(componentType);
                } else {
                    arrayType = new TypesArrayType(arrayType);
                }
            }
            return arrayType;
        }
    }

    @Override
    public ParameterizedType parameterized(Class<?> parameterizedType, Class<?>... typeArguments) {
        ClassElement classElement = visitorContext.getClassElement(parameterizedType)
                .orElse(ClassElement.of(parameterizedType));
        ClassElement[] generics = new ClassElement[typeArguments.length];
        for (int i = 0; i < typeArguments.length; i++) {
            Class<?> typeArgument = typeArguments[i];
            generics[i] = visitorContext.getClassElement(typeArgument).orElse(ClassElement.of(Object.class));
        }
        classElement.withBoundGenericTypes(Arrays.asList(generics));
        return new ParameterizedTypeImpl(
                classElement,
                this,
                visitorContext
        );
    }

    @Override
    public ParameterizedType parameterized(Class<?> parameterizedType, Type... typeArguments) {
        ClassElement classElement = visitorContext.getClassElement(parameterizedType)
                .orElse(ClassElement.of(parameterizedType));
        return new ParameterizedTypeImpl(classElement, this, visitorContext) {
            @Override
            public List<Type> typeArguments() {
                return Arrays.asList(typeArguments);
            }
        };
    }

    @Override
    public ParameterizedType parameterized(ClassType genericType, Type... typeArguments) {
        ClassTypeImpl impl = (ClassTypeImpl) genericType;
        ClassElement classElement = impl.getClassElement();
        return new ParameterizedTypeImpl(classElement, this, visitorContext) {
            @Override
            public List<Type> typeArguments() {
                return Arrays.asList(typeArguments);
            }
        };
    }

    @Override
    public WildcardType wildcardWithUpperBound(Type upperBound) {
        return new WildcardWithUpperBound(
                Objects.requireNonNull(upperBound, "Lower bound cannot be null")
        );
    }

    @Override
    public WildcardType wildcardWithLowerBound(Type lowerBound) {
        return new WildcardWithLowerBound(
                Objects.requireNonNull(lowerBound, "Lower bound cannot be null")
        );
    }

    @Override
    public WildcardType wildcardUnbounded() {
        return wildcardUnbounded;
    }

    private final class WildcardWithLowerBound extends TypesWildcardUnbounded implements WildcardType {
        private final Type lowerBound;

        private WildcardWithLowerBound(Type lowerBound) {
            this.lowerBound = lowerBound;
        }

        @Override
        public Type lowerBound() {
            return lowerBound;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof WildcardWithLowerBound && ((WildcardWithLowerBound) o).lowerBound.equals(lowerBound);
        }

        @Override
        public int hashCode() {
            return Objects.hash("? super", lowerBound);
        }
    }

    private final class WildcardWithUpperBound extends TypesWildcardUnbounded implements WildcardType {
        private final Type upperBound;

        private WildcardWithUpperBound(Type lowerBound) {
            this.upperBound = lowerBound;
        }

        @Override
        public Type upperBound() {
            return upperBound;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof WildcardWithUpperBound && ((WildcardWithUpperBound) o).upperBound.equals(upperBound);
        }

        @Override
        public int hashCode() {
            return Objects.hash("? extends", upperBound);
        }
    }

    private class TypesWildcardUnbounded extends AnnotationTargetImpl implements WildcardType {
        private final int hashCode = "?".hashCode();
        TypesWildcardUnbounded() {
            super(AnnotationMetadata.EMPTY_METADATA, TypesImpl.this, TypesImpl.this.visitorContext);
        }

        @Override
        public DeclarationInfo asDeclaration() {
            throw new IllegalStateException("Not a declaration");
        }

        @Override
        public Type asType() {
            throw new IllegalStateException("Not a type");
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof TypesWildcardUnbounded;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public Type upperBound() {
            return null;
        }

        @Override
        public Type lowerBound() {
            return null;
        }

    }

    private final class TypesArrayType extends AnnotationTargetImpl implements ArrayType {
        private final Type componentType;
        TypesArrayType(Type componentType) {
            super(AnnotationMetadata.EMPTY_METADATA, TypesImpl.this, TypesImpl.this.visitorContext);
            this.componentType = componentType;
        }

        @Override
        public Type componentType() {
            return componentType;
        }
    }
}
