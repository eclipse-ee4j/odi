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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import com.oracle.odi.cdi.annotation.reflect.AnnotationReflection;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Order;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanDisposer;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.declarations.DeclarationInfo;
import jakarta.enterprise.lang.model.types.ClassType;
import jakarta.enterprise.lang.model.types.Type;

final class SyntheticBeanBuilderImpl<T> extends AnnotationTargetImpl implements SyntheticBeanBuilder<T>, ElementAnnotationConfig {
    private final ClassElement beanType;
    private final VisitorContext visitorContext;
    private final Set<String> exposedTypes = new HashSet<>();
    private final Map<String, Object> params = new LinkedHashMap<>();
    private Class<? extends SyntheticBeanDisposer<T>> disposerClass;
    private Class<? extends SyntheticBeanCreator<T>> creatorClass;

    SyntheticBeanBuilderImpl(ClassElement element,
                                    Types types,
                                    VisitorContext visitorContext) {
        super(element, types);
        this.beanType = element;
        this.visitorContext = visitorContext;
    }

    public ClassElement getBeanType() {
        return beanType;
    }

    public Set<String> getExposedTypes() {
        return exposedTypes;
    }

    public Class<? extends SyntheticBeanDisposer<T>> getDisposerClass() {
        return disposerClass;
    }

    public Class<? extends SyntheticBeanCreator<T>> getCreatorClass() {
        return creatorClass;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    @Override
    public SyntheticBeanBuilder<T> type(Class<?> type) {
        if (type != null) {
            this.exposedTypes.add(type.getName());
        }
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> type(ClassInfo type) {
        if (type != null) {
            this.exposedTypes.add(type.name());
        }
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> type(Type type) {
        if (type instanceof ClassType) {
            return type(((ClassType) type).declaration());
        }
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> qualifier(Class<? extends Annotation> annotationType) {
        getElement().annotate(annotationType);
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> qualifier(AnnotationInfo qualifierAnnotation) {
        addAnnotation(qualifierAnnotation);
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> qualifier(Annotation qualifierAnnotation) {
        addAnnotation(qualifierAnnotation);
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> scope(Class<? extends Annotation> scopeAnnotation) {
        getElement().annotate(scopeAnnotation.getName());
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> alternative(boolean isAlternative) {
        getElement().annotate(Alternative.class);
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> priority(int priority) {
        getElement().annotate(Order.class, (builder) -> builder.value(-priority));
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> name(String name) {
        getElement().annotate(AnnotationUtil.NAMED, (builder) -> builder.value(name));
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> stereotype(Class<? extends Annotation> stereotypeAnnotation) {
        getElement().annotate(stereotypeAnnotation);
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> stereotype(ClassInfo stereotypeAnnotation) {
        Objects.requireNonNull(stereotypeAnnotation, "Stereotype annotation cannot be null");
        getElement().annotate(stereotypeAnnotation.name());
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String key, boolean value) {
        getElement().annotate(Property.class, builder -> {
           builder.member("name", key);
           builder.value(value);
        });
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String key, boolean[] value) {
        getElement().annotate(Property.class, builder -> {
            builder.member("name", key);
            builder.member(AnnotationMetadata.VALUE_MEMBER, value);
        });
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String key, int value) {
        getElement().annotate(Property.class, builder -> {
            builder.member("name", key);
            builder.value(value);
        });
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String key, int[] value) {
        getElement().annotate(Property.class, builder -> {
            builder.member("name", key);
            builder.member(AnnotationMetadata.VALUE_MEMBER, value);
        });
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String key, long value) {
        getElement().annotate(Property.class, builder -> {
            builder.member("name", key);
            builder.member(AnnotationMetadata.VALUE_MEMBER, value);
        });
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String key, long[] value) {
        getElement().annotate(Property.class, builder -> {
            builder.member("name", key);
            builder.member(AnnotationMetadata.VALUE_MEMBER, value);
        });
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String key, double value) {
        getElement().annotate(Property.class, builder -> {
            builder.member("name", key);
            builder.member(AnnotationMetadata.VALUE_MEMBER, value);
        });
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String key, double[] value) {
        getElement().annotate(Property.class, builder -> {
            builder.member("name", key);
            builder.member(AnnotationMetadata.VALUE_MEMBER, value);
        });
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String key, String value) {
        getElement().annotate(Property.class, builder -> {
            builder.member("name", key);
            builder.member(AnnotationMetadata.VALUE_MEMBER, value);
        });
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String key, String[] value) {
        getElement().annotate(Property.class, builder -> {
            builder.member("name", key);
            builder.member(AnnotationMetadata.VALUE_MEMBER, value);
        });
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String key, Class<?> value) {
        getElement().annotate(Property.class, builder -> {
            builder.member("name", key);
            builder.member(AnnotationMetadata.VALUE_MEMBER, value);
        });
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String key, Class<?>[] value) {
        getElement().annotate(Property.class, builder -> {
            builder.member("name", key);
            builder.member(AnnotationMetadata.VALUE_MEMBER, value);
        });
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String key, AnnotationInfo value) {
        Objects.requireNonNull(value, "Annotation info cannot be null");
        getElement().annotate(Property.class, builder -> {
            builder.member("name", key);
            builder.member(AnnotationMetadata.VALUE_MEMBER, ((AnnotationInfoImpl) value).getAnnotationValue());
        });
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String key, Annotation value) {
        Objects.requireNonNull(value, "Annotation cannot be null");
        getElement().annotate(Property.class, builder -> {
            builder.member("name", key);
            builder.member(AnnotationMetadata.VALUE_MEMBER, AnnotationReflection.toAnnotationValue(value));
        });
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String key, AnnotationInfo[] value) {
        final AnnotationValue<?>[] annotationValues = Stream.of(value)
                .filter(ai -> ai instanceof AnnotationInfoImpl)
                .map(AnnotationInfoImpl.class::cast)
                .map(AnnotationInfoImpl::getAnnotationValue)
                .toArray(AnnotationValue[]::new);
        getElement().annotate(Property.class, builder -> {
            builder.member("name", key);
            builder.member(AnnotationMetadata.VALUE_MEMBER, annotationValues);
        });
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String key, Annotation[] value) {
        final AnnotationValue[] annotationValues = Stream.of(value)
                .filter(Objects::nonNull)
                .map(AnnotationReflection::toAnnotationValue)
                .toArray(AnnotationValue[]::new);
        getElement().annotate(Property.class, builder -> {
            builder.member("name", key);
            builder.member(AnnotationMetadata.VALUE_MEMBER, annotationValues);
        });
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> createWith(Class<? extends SyntheticBeanCreator<T>> creatorClass) {
        this.creatorClass = creatorClass;
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> disposeWith(Class<? extends SyntheticBeanDisposer<T>> disposerClass) {
        this.disposerClass = disposerClass;
        return this;
    }

    @Override
    public VisitorContext getVisitorContext() {
        return this.visitorContext;
    }

    @Override
    public DeclarationInfo asDeclaration() {
        return new ClassInfoImpl(
                this.beanType,
                getTypes(),
                this.visitorContext
        );
    }

    @Override
    public Type asType() {
        return asDeclaration().asType();
    }

    @Override
    public DeclarationInfo info() {
        throw new IllegalStateException("Not a declaration");
    }
}
