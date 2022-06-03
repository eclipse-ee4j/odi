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

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Optional;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticObserver;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticObserverBuilder;
import jakarta.enterprise.inject.build.compatible.spi.Types;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.declarations.DeclarationInfo;

final class SyntheticObserverBuilderImpl<T> extends AbstractSyntheticBuilder implements SyntheticObserverBuilder<T> {
    private static final String OBSERVES_ANN = "com.oracle.odi.cdi.annotation.ObservesMethod";
    private final ClassElement eventType;
    private final VisitorContext visitorContext;
    @Nullable
    private ClassElement declaringClass;
    @Nullable
    private ClassElement observerClass;

    SyntheticObserverBuilderImpl(ClassElement eventType, Types types, VisitorContext visitorContext) {
        super(eventType.getAnnotationMetadata(), types, visitorContext);
        this.eventType = eventType;
        this.visitorContext = visitorContext;
    }

    public ClassElement getEventType() {
        return eventType;
    }

    @Nullable
    public ClassElement getDeclaringClass() {
        return declaringClass;
    }

    @Nullable
    public ClassElement getObserverClass() {
        return observerClass;
    }

    @Override
    public SyntheticObserverBuilder<T> declaringClass(Class<?> declaringClass) {
        this.declaringClass = Optional
                .ofNullable(declaringClass)
                .flatMap(visitorContext::getClassElement)
                .orElse(null);
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> declaringClass(ClassInfo declaringClass) {
        this.declaringClass = Optional
                .ofNullable(declaringClass)
                .flatMap(t -> visitorContext.getClassElement(t.name()))
                .orElse(null);
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> qualifier(Class<? extends Annotation> annotationType) {
        super.qualifier(annotationType);
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> qualifier(AnnotationInfo qualifierAnnotation) {
        super.qualifier(qualifierAnnotation);
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> qualifier(Annotation qualifierAnnotation) {

        super.qualifier(qualifierAnnotation);
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> priority(int priority) {
        getAnnotationMetadata().addAnnotation(
                OBSERVES_ANN,
                Collections.singletonMap("priority", priority)
        );
        super.priority(priority);
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> async(boolean isAsync) {
        getAnnotationMetadata().addAnnotation(
                "com.oracle.odi.cdi.annotation.ObservesMethod",
                Collections.singletonMap("async", isAsync)
        );
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> transactionPhase(TransactionPhase transactionPhase) {
        getAnnotationMetadata().addAnnotation(
                "com.oracle.odi.cdi.annotation.ObservesMethod",
                Collections.singletonMap("during", transactionPhase)
        );
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String key, boolean value) {
        super.withParam(key, value);
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String key, boolean[] value) {
        super.withParam(key, value);
        return this;
    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String key, int value) {
        super.withParam(key, value);
        return this;

    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String key, int[] value) {
        super.withParam(key, value);
        return this;

    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String key, long value) {
        super.withParam(key, value);
        return this;

    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String key, long[] value) {
        super.withParam(key, value);
        return this;

    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String key, double value) {
        super.withParam(key, value);
        return this;

    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String key, double[] value) {
        super.withParam(key, value);
        return this;

    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String key, String value) {
        super.withParam(key, value);
        return this;

    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String key, String[] value) {
        super.withParam(key, value);
        return this;

    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String key, Enum<?> value) {
        super.withParam(key, value);
        return this;

    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String key, Enum<?>[] value) {
        super.withParam(key, value);
        return this;

    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String key, Class<?> value) {
        super.withParam(key, value);
        return this;

    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String key, ClassInfo value) {
        super.withParam(key, value);
        return this;

    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String key, Class<?>[] value) {
        super.withParam(key, value);
        return this;

    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String key, ClassInfo[] value) {
        super.withParam(key, value);
        return this;

    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String key, AnnotationInfo value) {
        super.withParam(key, value);
        return this;

    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String key, Annotation value) {
        super.withParam(key, value);
        return this;

    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String key, AnnotationInfo[] value) {
        super.withParam(key, value);
        return this;

    }

    @Override
    public SyntheticObserverBuilder<T> withParam(String key, Annotation[] value) {
        super.withParam(key, value);
        return this;

    }

    @Override
    public SyntheticObserverBuilder<T> observeWith(Class<? extends SyntheticObserver<T>> observerClass) {
        ClassElement classElement = this.visitorContext.getClassElement(observerClass).orElse(null);
        if (classElement == null) {
            visitorContext.fail("Observer class registered via extension is not on the application classpath: " + observerClass, null);
        } else {
            this.observerClass = classElement;
        }
        return this;

    }

    @Override
    public DeclarationInfo asDeclaration() {
        return new ClassInfoImpl(
                eventType,
                types,
                visitorContext
        );
    }
}
