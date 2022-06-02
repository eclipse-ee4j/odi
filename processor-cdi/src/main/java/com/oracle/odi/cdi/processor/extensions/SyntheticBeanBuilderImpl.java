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
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import com.oracle.odi.cdi.annotation.reflect.AnnotationReflection;
import io.micronaut.annotation.processing.visitor.JavaVisitorContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Order;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.build.compatible.spi.AnnotationBuilder;
import jakarta.enterprise.inject.build.compatible.spi.DeclarationConfig;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanBuilder;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanCreator;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanDisposer;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.AnnotationTarget;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.declarations.DeclarationInfo;
import jakarta.enterprise.lang.model.types.ClassType;
import jakarta.enterprise.lang.model.types.Type;

final class SyntheticBeanBuilderImpl<T> extends AnnotationTargetImpl implements SyntheticBeanBuilder<T>, DeclarationConfig, AnnotationTarget {
    private final ClassElement beanType;
    private final VisitorContext visitorContext;
    private final Set<String> exposedTypes = new HashSet<>();
    private final Map<String, Object> params = new LinkedHashMap<>();
    private Class<? extends SyntheticBeanDisposer<T>> disposerClass;
    private Class<? extends SyntheticBeanCreator<T>> creatorClass;

    SyntheticBeanBuilderImpl(ClassElement element,
                            Types types,
                            VisitorContext visitorContext) {
        super(cloneMetadata(element), types, visitorContext);
        this.beanType = element;
        this.visitorContext = visitorContext;
    }

    @Override
    public MutableAnnotationMetadata getAnnotationMetadata() {
        return (MutableAnnotationMetadata) super.getAnnotationMetadata();
    }

    private static AnnotationMetadata cloneMetadata(ClassElement element) {
        AnnotationMetadata annotationMetadata = element.getAnnotationMetadata();
        if (annotationMetadata instanceof MutableAnnotationMetadata) {
            return ((MutableAnnotationMetadata) annotationMetadata).clone();
        } else {
            return new MutableAnnotationMetadata();
        }
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
        addAnnotation(annotationType);
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
        addAnnotation(scopeAnnotation);
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> alternative(boolean isAlternative) {
        addAnnotation(Alternative.class);
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> priority(int priority) {
        addAnnotation(AnnotationBuilder.of(Order.class).value(-priority).build());
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> name(String name) {
        getAnnotationMetadata().addAnnotation(AnnotationUtil.NAMED, Collections.singletonMap(AnnotationMetadata.VALUE_MEMBER, name));
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> stereotype(Class<? extends Annotation> stereotypeAnnotation) {
        addAnnotation(stereotypeAnnotation);
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> stereotype(ClassInfo stereotypeAnnotation) {
        Objects.requireNonNull(stereotypeAnnotation, "Stereotype annotation cannot be null");
        getAnnotationMetadata().addAnnotation(stereotypeAnnotation.name(), Collections.emptyMap(), RetentionPolicy.RUNTIME);
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String key, boolean value) {
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String key, boolean[] value) {
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String key, int value) {
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String key, int[] value) {
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String key, long value) {
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String key, long[] value) {
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String key, double value) {
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String key, double[] value) {
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String key, String value) {
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String key, String[] value) {
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String key, Enum<?> value) {
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String key, Enum<?>[] value) {
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String key, Class<?> value) {
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String key, ClassInfo value) {
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String key, Class<?>[] value) {
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String key, ClassInfo[] value) {
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String key, AnnotationInfo value) {
        Objects.requireNonNull(value, "Annotation info cannot be null");
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String key, Annotation value) {
        Objects.requireNonNull(value, "Annotation cannot be null");
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String key, AnnotationInfo[] value) {
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    @Override
    public SyntheticBeanBuilder<T> withParam(String key, Annotation[] value) {
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
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

    @Override
    public DeclarationConfig addAnnotation(Class<? extends Annotation> annotationType) {
        addAnnotation(AnnotationValue.builder(annotationType).build());
        return this;
    }

    @Override
    public DeclarationConfig addAnnotation(AnnotationInfo annotation) {
        final AnnotationValue<?> av = ((AnnotationInfoImpl) annotation).getAnnotationValue();
        addAnnotation(av);
        return this;
    }

    @Override
    public DeclarationConfig addAnnotation(Annotation annotation) {
        final AnnotationValue<Annotation> av = AnnotationReflection.toAnnotationValue(annotation);
        addAnnotation(av);
        return this;
    }

    private void addAnnotation(AnnotationValue<?> av) {
        // TODO: add public API to avoid this cast
        ((JavaVisitorContext) visitorContext).getAnnotationUtils()
                        .newAnnotationBuilder()
                                .annotate(
                                        getAnnotationMetadata(),
                                        av
                                );
    }

    @Override
    public DeclarationConfig removeAnnotation(Predicate<AnnotationInfo> predicate) {
        getAnnotationMetadata().removeAnnotationIf(annotationValue -> predicate.test(new AnnotationInfoImpl(annotationValue)));
        return this;
    }

    @Override
    public DeclarationConfig removeAllAnnotations() {
        getAnnotationMetadata().removeAnnotationIf(annotationValue -> true);
        return this;
    }
}
