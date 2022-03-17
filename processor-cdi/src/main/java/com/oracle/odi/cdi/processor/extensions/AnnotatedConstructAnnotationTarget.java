/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import io.micronaut.annotation.processing.visitor.JavaVisitorContext;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.AnnotationMember;
import jakarta.enterprise.lang.model.AnnotationTarget;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.declarations.DeclarationInfo;
import jakarta.enterprise.lang.model.types.Type;

import javax.lang.model.AnnotatedConstruct;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The implementation of {@link AnnotationTarget} that is using direct annotation processor API which differs with the Micronaut one.
 */
final class AnnotatedConstructAnnotationTarget implements AnnotationTarget {

    @SuppressWarnings("checkstyle:VisibilityModifier")
    private final AnnotatedConstruct annotatedConstruct;
    private final JavaVisitorContext visitorContext;
    @SuppressWarnings("checkstyle:VisibilityModifier")
    private final Types types;

    private List<AnnotationInfo> annotations;

    AnnotatedConstructAnnotationTarget(Element element, Types types, JavaVisitorContext visitorContext) {
        Object nativeType = element.getNativeType();
        if (nativeType instanceof TypeVariable) {
            // TypeVariable getAnnotationMirrors doesn't return anything
            this.annotatedConstruct = ((TypeVariable) nativeType).asElement();
        } else {
            this.annotatedConstruct = (AnnotatedConstruct) nativeType;
        }
        this.visitorContext = visitorContext;
        this.types = types;
    }

    @Override
    public boolean isDeclaration() {
        throw new IllegalStateException("Not supported!");
    }

    @Override
    public boolean isType() {
        throw new IllegalStateException("Not supported!");
    }

    @Override
    public DeclarationInfo asDeclaration() {
        throw new IllegalStateException("Not supported!");
    }

    @Override
    public Type asType() {
        throw new IllegalStateException("Not supported!");
    }

    @Override
    public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
        Annotation annotation = annotatedConstruct.getAnnotation(annotationType);
        if (annotation == null) {
            return false;
        }
        Retention retention = annotation.annotationType().getAnnotation(Retention.class);
        if (retention.value() == RetentionPolicy.CLASS) {
            return false;
        }
        return true;
    }

    @Override
    public boolean hasAnnotation(Predicate<AnnotationInfo> predicate) {
        if (predicate == null) {
            throw new IllegalArgumentException("Argument predicate cannot be null");
        }
        return annotations().stream().anyMatch(predicate);
    }

    @Override
    public <T extends Annotation> AnnotationInfo annotation(Class<T> annotationType) {
        if (annotationType == null) {
            throw new IllegalArgumentException("Argument annotationType cannot be null");
        }
        return annotations().stream().filter(a -> a.name().equals(annotationType.getName())).findAny().orElse(null);
    }

    @Override
    public <T extends Annotation> Collection<AnnotationInfo> repeatableAnnotation(Class<T> annotationType) {
        if (annotationType == null) {
            throw new IllegalArgumentException("Argument annotationType cannot be null");
        }
        Repeatable repeatable = annotationType.getAnnotation(Repeatable.class);
        Class<? extends Annotation> container;
        if (repeatable == null) {
            container = null;
        } else {
            container = repeatable.value();
        }
        for (AnnotationInfo annotationInfo : annotations()) {
            if (annotationInfo.name().equals(annotationType.getName())) {
                if (container != null) {
                    AnnotationInfo containerAnnotation = annotations().stream()
                            .filter(ai -> ai.name().equals(container.getName()))
                            .filter(ai -> ((AnnotationMirrorAnnotationInfoImpl) ai).getAnnotatedConstruct().equals(((AnnotationMirrorAnnotationInfoImpl) annotationInfo).getAnnotatedConstruct()))
                            .findAny()
                            .orElse(null);
                    // Add annotations from the container if annotated on the same class
                    if (containerAnnotation != null) {
                        List<AnnotationInfo> result = new ArrayList<>();
                        result.add(annotationInfo);
                        result.addAll(containerAnnotation.value().asArray().stream().map(AnnotationMember::asNestedAnnotation).collect(Collectors.toUnmodifiableList()));
                        return result;
                    }
                }
                return Collections.singletonList(annotationInfo);
            }
            if (container != null && annotationInfo.name().equals(container.getName())) {
                List<AnnotationMember> annotationMembers = annotationInfo.value().asArray();
                return annotationMembers.stream().map(AnnotationMember::asNestedAnnotation).collect(Collectors.toUnmodifiableList());
            }
        }
        return Collections.emptyList();
    }

    @Override
    public Collection<AnnotationInfo> annotations(Predicate<AnnotationInfo> predicate) {
        if (predicate == null) {
            throw new IllegalArgumentException("Argument predicate cannot be null");
        }
        return annotations().stream().filter(predicate).collect(Collectors.toUnmodifiableList());
    }

    @Override
    public Collection<AnnotationInfo> annotations() {
        if (annotatedConstruct == null) {
            return Collections.emptyList();
        }
        if (annotations == null) {
            Set<String> included = new HashSet<>();
            List<Map.Entry<AnnotatedConstruct, AnnotationMirror>> annotationMirrors = new ArrayList<>();
            for (AnnotationMirror annotationMirror : annotatedConstruct.getAnnotationMirrors()) {
                included.add(annotationMirror.getAnnotationType().toString());
                annotationMirrors.add(new AbstractMap.SimpleEntry<>(annotatedConstruct, annotationMirror));
            }
            if (annotatedConstruct instanceof TypeElement) {
                TypeMirror superclass = ((TypeElement) annotatedConstruct).getSuperclass();
                while (superclass instanceof DeclaredType) {
                    DeclaredType dt = (DeclaredType) superclass;
                    TypeElement element = (TypeElement) dt.asElement();
                    element.getAnnotationMirrors().stream().filter(am -> {
                        javax.lang.model.element.Element el = am.getAnnotationType().asElement();
                        return !included.contains(el.toString()) && el.getAnnotation(Inherited.class) != null;
                    }).forEach(am -> annotationMirrors.add(new AbstractMap.SimpleEntry<>(element, am)));
                    for (Map.Entry<AnnotatedConstruct, AnnotationMirror> am : annotationMirrors) {
                        included.add(am.getValue().getAnnotationType().toString());
                    }
                    superclass = element.getSuperclass();
                }
            }

            annotations = annotationMirrors.stream()
                    .map(e -> new AnnotationMirrorAnnotationInfoImpl(e.getKey(), e.getValue()))
                    .collect(Collectors.toUnmodifiableList());
        }
        return annotations;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AnnotatedConstructAnnotationTarget other = (AnnotatedConstructAnnotationTarget) o;
        return annotatedConstruct.equals(other.annotatedConstruct);
    }

    @Override
    public int hashCode() {
        return annotatedConstruct.hashCode();
    }

    private ClassElement asClassElement(DeclaredType declaredType) {
        javax.lang.model.element.Element annotationElement = declaredType.asElement();
        JavaVisitorContext javaVisitorContext = visitorContext;
        ClassElement classElement = javaVisitorContext.getElementFactory().newClassElement((TypeElement) declaredType.asElement(), visitorContext.getAnnotationUtils().getAnnotationMetadata(annotationElement));
        return classElement;
    }

    private final class AnnotationMirrorAnnotationInfoImpl implements AnnotationInfo {

        private final AnnotatedConstruct annotatedConstruct;
        private final AnnotationMirror annotation;
        private Map<String, AnnotationMember> members;

        private AnnotationMirrorAnnotationInfoImpl(AnnotatedConstruct annotatedConstruct, AnnotationMirror annotation) {
            this.annotatedConstruct = annotatedConstruct;
            this.annotation = annotation;
        }

        public AnnotatedConstruct getAnnotatedConstruct() {
            return annotatedConstruct;
        }

        @Override
        public ClassInfo declaration() {
            DeclaredType annotationType = annotation.getAnnotationType();
            ClassElement classElement = asClassElement(annotationType);
            return new ClassInfoImpl(classElement, types, visitorContext);
        }

        @Override
        public boolean hasMember(String name) {
            return members().containsKey(name);
        }

        @Override
        public AnnotationMember member(String name) {
            return members().get(name);
        }

        @Override
        public Map<String, AnnotationMember> members() {
            if (members == null) {
                members = Stream.concat(readAnnotationDefaultValues().entrySet().stream(), annotation.getElementValues().entrySet().stream()).map(e -> new AnnotationMemberImpl(e.getKey().getSimpleName().toString(), e.getKey().getReturnType(), e.getValue())).collect(Collectors.toUnmodifiableMap(AnnotationMemberImpl::getName, it -> it, (t1, t2) -> t2));
            }
            return members;
        }

        private Map<? extends ExecutableElement, ? extends AnnotationValue> readAnnotationDefaultValues() {
            Map<ExecutableElement, AnnotationValue> defaultValues = new LinkedHashMap<>();
            javax.lang.model.element.Element element = annotation.getAnnotationType().asElement();
            if (element instanceof TypeElement) {
                TypeElement annotationElement = (TypeElement) element;
                visitorContext.getElements().getAllMembers(annotationElement).stream().filter(member -> member.getEnclosingElement().equals(annotationElement)).filter(ExecutableElement.class::isInstance).map(ExecutableElement.class::cast).filter(this::isValidDefaultValue).forEach(executableElement -> {
                    final AnnotationValue defaultValue = executableElement.getDefaultValue();
                    defaultValues.put(executableElement, defaultValue);
                });
            }
            return defaultValues;
        }

        private boolean isValidDefaultValue(ExecutableElement executableElement) {
            AnnotationValue defaultValue = executableElement.getDefaultValue();
            if (defaultValue != null) {
                Object v = defaultValue.getValue();
                if (v != null) {
                    if (v instanceof String) {
                        return StringUtils.isNotEmpty((CharSequence) v);
                    } else {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    final class AnnotationMemberImpl implements AnnotationMember {
        private final String name;
        private final TypeMirror type;
        private final javax.lang.model.element.AnnotationValue value;
        private final Object val;

        AnnotationMemberImpl(String name, TypeMirror type, javax.lang.model.element.AnnotationValue value) {
            this.name = name;
            this.type = type;
            this.value = value;
            this.val = value.getValue();
        }

        String getName() {
            return name;
        }

        @Override
        public Kind kind() {
            switch (type.getKind()) {
                case BOOLEAN:
                    return Kind.BOOLEAN;
                case BYTE:
                    return Kind.BYTE;
                case SHORT:
                    return Kind.SHORT;
                case INT:
                    return Kind.INT;
                case LONG:
                    return Kind.LONG;
                case CHAR:
                    return Kind.CHAR;
                case FLOAT:
                    return Kind.FLOAT;
                case DOUBLE:
                    return Kind.DOUBLE;
                case ARRAY:
                    return Kind.ARRAY;
                case DECLARED:
                    DeclaredType declaredType = (DeclaredType) type;
                    ElementKind kind = declaredType.asElement().getKind();
                    if (kind == ElementKind.ENUM) {
                        return Kind.ENUM;
                    }
                    if (kind == ElementKind.ANNOTATION_TYPE) {
                        return Kind.NESTED_ANNOTATION;
                    }
                    switch (declaredType.toString()) {
                        case "java.lang.String":
                            return Kind.STRING;
                        case "java.lang.Class<?>":
                            return Kind.CLASS;
                        default:
                            throw new IllegalStateException("Unknown declared type: " + declaredType);
                    }
                default:
                    throw new IllegalStateException("Unknown kind " + type.getKind());
            }
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
            if (type instanceof DeclaredType) {
                DeclaredType declaredType = (DeclaredType) type;
                if (declaredType.asElement().getKind() == ElementKind.ENUM) {
                    ClassElement classElement = asClassElement(declaredType);
                    return new ClassInfoImpl(classElement, types, visitorContext);
                }
            }
            throw new IllegalStateException("Not an enum!");
        }

        @Override
        public String asEnumConstant() {
            return val.toString();
        }

        @Override
        public Type asType() {
            if (val instanceof DeclaredType) {
                ClassElement classElement = asClassElement((DeclaredType) val);
                return TypeFactory.createType(classElement, types, visitorContext);
            }
            throw new IllegalStateException("Not a type!");
        }

        @Override
        public AnnotationInfo asNestedAnnotation() {
            if (type instanceof DeclaredType) {
                DeclaredType declaredType = (DeclaredType) type;
                javax.lang.model.element.Element element = declaredType.asElement();
                if (element.getKind() == ElementKind.ANNOTATION_TYPE) {
                    return new AnnotationMirrorAnnotationInfoImpl(annotatedConstruct, (AnnotationMirror) value.getValue());
                }
            }
            throw new IllegalStateException("Not a nested annotation!");
        }

        @Override
        public List<AnnotationMember> asArray() {
            if (type.getKind() == TypeKind.ARRAY) {
                ArrayType arrayType = (ArrayType) type;
                List<AnnotationValue> values = (List<AnnotationValue>) value.getValue();
                TypeMirror componentType = arrayType.getComponentType();
                return values.stream().map(av -> new AnnotationMemberImpl(name, componentType, av)).collect(Collectors.toUnmodifiableList());
            }
            throw new IllegalStateException("Not an array!");
        }

    }

}
