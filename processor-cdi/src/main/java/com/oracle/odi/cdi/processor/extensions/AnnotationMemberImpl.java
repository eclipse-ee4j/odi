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

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.AnnotationMember;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.types.Type;

final class AnnotationMemberImpl implements AnnotationMember {
    private final Object val;

    AnnotationMemberImpl(Object val) {
        this.val = val;
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
        if (val instanceof String) {
            return Kind.STRING;
        }
        if (val instanceof AnnotationValue) {
            return Kind.NESTED_ANNOTATION;
        }
        if (val instanceof Class || val instanceof AnnotationClassValue) {
            return Kind.CLASS;
        }
        if (val instanceof Enum) {
            return Kind.ENUM;
        }
        if (val instanceof Character) {
            return Kind.CHAR;
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
        throw new IllegalStateException("Not an enum type");
    }

    @Override
    public String asEnumConstant() {
        return val.toString();
    }

    @Override
    public Type asType() {
        if (val instanceof AnnotationClassValue) {
            VisitorContext visitorContext = ActiveVisitorContext.currentVisitorContext();
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
            return new AnnotationInfoImpl(
                (AnnotationValue<? extends Annotation>) val
            );
        }
        throw new IllegalStateException("Not a nested annotation");
    }

    @Override
    public List<AnnotationMember> asArray() {
        Class<?> t = val.getClass();
        if (t.isArray()) {
            if (t.getComponentType().isPrimitive()) {
                return Arrays.stream(ArrayUtils.toWrapperArray(val))
                        .map(AnnotationMemberImpl::new)
                        .collect(Collectors.toUnmodifiableList());
            } else {
                return Arrays.stream(((Object[]) val))
                        .map(AnnotationMemberImpl::new)
                        .collect(Collectors.toUnmodifiableList());
            }
        }
        return Collections.emptyList();
    }

}
