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

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.AnnotationMember;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.types.Type;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

final class AnnotationMemberImpl implements AnnotationMember {
    @Nullable
    private final Types types;
    @Nullable
    private final VisitorContext visitorContext;
    private final String annotationName;
    private final String name;
    private final Object val;
    private ClassElement enumType;

    AnnotationMemberImpl(Types types, VisitorContext visitorContext, String annotationName, String name, Object val) {
        this.types = types;
        this.visitorContext = visitorContext;
        this.annotationName = annotationName;
        this.name = name;
        this.val = val;
    }

    private ClassElement getEnumType() {
        if (enumType == null) {
            VisitorContext visitorContext = this.visitorContext == null ? ActiveVisitorContext.currentVisitorContext() : this.visitorContext;
            ClassElement classElement = visitorContext.getClassElement(annotationName).orElse(null);
            ClassElement returnType = classElement.getEnclosedElement(ElementQuery.ALL_METHODS.named(n -> n.equals(name))).get().getReturnType();
            if (returnType.isEnum()) {
                enumType = returnType;
            }

        }
        return enumType;
    }

    @Override
    public Kind kind() {
        if (val.getClass().isArray()) {
            return Kind.ARRAY;
        }
        if (val instanceof Number) {
            if (val instanceof Long) {
                return Kind.LONG;
            } else if (val instanceof Integer) {
                return Kind.INT;
            } else if (val instanceof Float) {
                return Kind.FLOAT;
            } else if (val instanceof Double) {
                return Kind.DOUBLE;
            } else if (val instanceof Short) {
                return Kind.SHORT;
            } else if (val instanceof Byte) {
                return Kind.BYTE;
            }
        }
        if (val instanceof Boolean) {
            return Kind.BOOLEAN;
        }
        if (val instanceof AnnotationValue) {
            return Kind.NESTED_ANNOTATION;
        }
        if (val instanceof Class || val instanceof AnnotationClassValue) {
            return Kind.CLASS;
        }
        if (val instanceof Character) {
            return Kind.CHAR;
        }
        if (val instanceof Enum || visitorContext != null && getEnumType() != null) {
            return Kind.ENUM;
        }
        if (val instanceof String) {
            return Kind.STRING;
        }
        throw new IllegalStateException("Unknown kind " + val.getClass());
    }

    @Override
    public boolean asBoolean() {
        if (val instanceof Boolean) {
            return (Boolean) val;
        }
        return Boolean.parseBoolean(val.toString());
    }

    @Override
    public byte asByte() {
        if (val instanceof Number) {
            return ((Number) val).byteValue();
        }
        return Byte.parseByte(val.toString());
    }

    @Override
    public short asShort() {
        if (val instanceof Number) {
            return ((Number) val).shortValue();
        }
        return Short.parseShort(val.toString());
    }

    @Override
    public int asInt() {
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        return Integer.parseInt(val.toString());
    }

    @Override
    public long asLong() {
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        return Long.parseLong(val.toString());
    }

    @Override
    public float asFloat() {
        if (val instanceof Number) {
            return ((Number) val).floatValue();
        }
        return Float.parseFloat(val.toString());
    }

    @Override
    public double asDouble() {
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        return Double.parseDouble(val.toString());
    }

    @Override
    public char asChar() {
        if (val instanceof Character) {
            return ((Character) val);
        }
        return 0;
    }

    @Override
    public String asString() {
        return val.toString();
    }

    @Override
    public <E extends Enum<E>> E asEnum(Class<E> aClass) {
        return Enum.valueOf(aClass, val.toString());
    }

    @Override
    public ClassInfo asEnumClass() {
        ClassElement enumType = getEnumType();
        if (enumType == null) {
            throw new IllegalStateException("Not an enum!");
        }
        return new ClassInfoImpl(enumType, types, visitorContext);
    }

    @Override
    public String asEnumConstant() {
        return val.toString();
    }

    @Override
    public Type asType() {
        if (val instanceof AnnotationClassValue) {
            VisitorContext visitorContext = this.visitorContext == null ? ActiveVisitorContext.currentVisitorContext() : this.visitorContext;
            String name = ((AnnotationClassValue<?>) val).getName();
            ClassElement classElement = visitorContext.getClassElement(name).orElse(null);
            if (classElement != null) {
                return TypeFactory.createType(
                        classElement,
                        new TypesImpl(visitorContext),
                        visitorContext
                );
            } else {
                final String message = "Cannot create type for [" + name
                        + "]. The specified class is not on the user classpath";
                throw new IllegalStateException(message);
            }
        }
        throw new IllegalStateException("Not a type");
    }

    @Override
    public AnnotationInfo asNestedAnnotation() {
        if (val instanceof AnnotationValue) {
            return new AnnotationInfoImpl(types, visitorContext, (AnnotationValue<? extends Annotation>) val);
        }
        throw new IllegalStateException("Not a nested annotation");
    }

    @Override
    public List<AnnotationMember> asArray() {
        Class<?> t = val.getClass();
        if (t.isArray()) {
            if (t.getComponentType().isPrimitive()) {
                return Arrays.stream(ArrayUtils.toWrapperArray(val))
                        .map(val1 -> new AnnotationMemberImpl(types, visitorContext, annotationName, name, val1))
                        .collect(Collectors.toUnmodifiableList());
            } else {
                return Arrays.stream(((Object[]) val))
                        .map(val1 -> new AnnotationMemberImpl(types, visitorContext, annotationName, name, val1))
                        .collect(Collectors.toUnmodifiableList());
            }
        }
        return Collections.emptyList();
    }

}
