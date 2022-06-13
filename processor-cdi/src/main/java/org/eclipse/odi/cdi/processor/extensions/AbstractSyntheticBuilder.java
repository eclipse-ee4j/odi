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
package org.eclipse.odi.cdi.processor.extensions;

import java.lang.annotation.Annotation;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import org.eclipse.odi.cdi.annotation.reflect.AnnotationReflection;
import io.micronaut.annotation.processing.visitor.JavaVisitorContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Order;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.build.compatible.spi.AnnotationBuilder;
import jakarta.enterprise.inject.build.compatible.spi.DeclarationConfig;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.AnnotationTarget;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.declarations.DeclarationInfo;
import jakarta.enterprise.lang.model.types.Type;

/**
 * Abstract builder impl.
 */
abstract class AbstractSyntheticBuilder extends AnnotationTargetImpl implements DeclarationConfig, AnnotationTarget {
    protected final VisitorContext thisVisitorContext;
    private final Map<String, Object> params = new LinkedHashMap<>();

    AbstractSyntheticBuilder(AnnotationMetadata annotationMetadata,
                             Types types,
                             VisitorContext visitorContext) {
        super(cloneMetadata(annotationMetadata), types, visitorContext);
        this.thisVisitorContext = visitorContext;
    }

    @Override
    public final MutableAnnotationMetadata getAnnotationMetadata() {
        return (MutableAnnotationMetadata) super.getAnnotationMetadata();
    }

    public Map<String, Object> getParams() {
        return params;
    }

    protected Object priority(int priority) {
        addAnnotation(AnnotationBuilder.of(Order.class).value(-priority).build());
        return this;
    }

    public Object qualifier(Class<? extends Annotation> annotationType) {
        addAnnotation(annotationType);
        return this;
    }

    public Object qualifier(AnnotationInfo qualifierAnnotation) {
        addAnnotation(qualifierAnnotation);
        return this;
    }

    protected Object qualifier(Annotation qualifierAnnotation) {
        addAnnotation(qualifierAnnotation);
        return this;
    }

    protected Object scope(Class<? extends Annotation> scopeAnnotation) {
        addAnnotation(scopeAnnotation);
        return this;
    }

    protected Object alternative(boolean isAlternative) {
        addAnnotation(Alternative.class);
        return this;
    }

    protected Object withParam(String key, boolean value) {
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    protected Object withParam(String key, boolean[] value) {
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    protected Object withParam(String key, int value) {
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    protected Object withParam(String key, int[] value) {
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    protected Object withParam(String key, long value) {
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    protected Object withParam(String key, long[] value) {
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    protected Object withParam(String key, double value) {
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    protected Object withParam(String key, double[] value) {
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    protected Object withParam(String key, String value) {
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    protected Object withParam(String key, String[] value) {
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    protected Object withParam(String key, Enum<?> value) {
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    protected Object withParam(String key, Enum<?>[] value) {
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    protected Object withParam(String key, Class<?> value) {
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    protected Object withParam(String key, ClassInfo value) {
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    protected Object withParam(String key, Class<?>[] value) {
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    protected Object withParam(String key, ClassInfo[] value) {
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    protected Object withParam(String key, AnnotationInfo value) {
        Objects.requireNonNull(value, "Annotation info cannot be null");
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    protected Object withParam(String key, Annotation value) {
        Objects.requireNonNull(value, "Annotation cannot be null");
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    protected Object withParam(String key, AnnotationInfo[] value) {
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    protected Object withParam(String key, Annotation[] value) {
        addAnnotation(AnnotationBuilder.of(Property.class).member("name", key).value(value).build());
        return this;
    }

    private static AnnotationMetadata cloneMetadata(AnnotationMetadata annotationMetadata) {
        if (annotationMetadata instanceof MutableAnnotationMetadata) {
            return ((MutableAnnotationMetadata) annotationMetadata).clone();
        } else {
            return new MutableAnnotationMetadata();
        }
    }

    public final Type asType() {
        return asDeclaration().asType();
    }

    @Override
    public DeclarationInfo info() {
        throw new IllegalStateException("Not a declaration");
    }

    @Override
    public final DeclarationConfig addAnnotation(Class<? extends Annotation> annotationType) {
        addAnnotation(AnnotationValue.builder(annotationType).build());
        return this;
    }

    @Override
    public final DeclarationConfig addAnnotation(AnnotationInfo annotation) {
        final AnnotationValue<?> av = ((AnnotationInfoImpl) annotation).getAnnotationValue();
        addAnnotation(av);
        return this;
    }

    @Override
    public final DeclarationConfig addAnnotation(Annotation annotation) {
        final AnnotationValue<Annotation> av = AnnotationReflection.toAnnotationValue(annotation);
        addAnnotation(av);
        return this;
    }

    private void addAnnotation(AnnotationValue<?> av) {
        // TODO: add public API to avoid this cast
        ((JavaVisitorContext) thisVisitorContext).getAnnotationUtils()
                .newAnnotationBuilder()
                .annotate(
                        getAnnotationMetadata(),
                        av
                );
    }

    @Override
    public final DeclarationConfig removeAnnotation(Predicate<AnnotationInfo> predicate) {
        getAnnotationMetadata().removeAnnotationIf(annotationValue -> predicate.test(new AnnotationInfoImpl(annotationValue)));
        return this;
    }

    @Override
    public final DeclarationConfig removeAllAnnotations() {
        getAnnotationMetadata().removeAnnotationIf(annotationValue -> true);
        return this;
    }
}
