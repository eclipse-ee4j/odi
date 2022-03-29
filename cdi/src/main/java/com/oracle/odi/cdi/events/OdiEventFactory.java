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
package com.oracle.odi.cdi.events;

import com.oracle.odi.cdi.AnnotationUtils;
import com.oracle.odi.cdi.OdiBeanContainer;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Any;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.inject.ArgumentInjectionPoint;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.util.TypeLiteral;

import java.lang.annotation.Annotation;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;

/**
 * The factory of {@link jakarta.enterprise.event.Event}.
 */
@SuppressWarnings("CdiManagedBeanInconsistencyInspection")
@Internal
@Factory
final class OdiEventFactory {

    private final OdiBeanContainer beanContainer;
    private final OdiObserverMethodRegistry observerMethodRegistry;
    private final Supplier<Executor> executorSupplier = SupplierUtil.memoized(new Supplier<>() {
        @Override
        public Executor get() {
            return beanContainer.getBeanContext().findBean(Executor.class, Qualifiers.byName("scheduled")).orElseGet(ForkJoinPool::commonPool);
        }
    });

    OdiEventFactory(OdiBeanContainer beanContainer, OdiObserverMethodRegistry observerMethodRegistry) {
        this.beanContainer = beanContainer;
        this.observerMethodRegistry = observerMethodRegistry;
    }

    @SuppressWarnings("unused")
    @Any
    @Dependent
    public <T> Event<T> build(@Nullable ArgumentInjectionPoint<T, T> argumentInjectionPoint) {
        @SuppressWarnings("unchecked")
        Argument<T> eventType = (Argument<T>) Argument.OBJECT_ARGUMENT;
        Qualifier<T> eventQualifier = null;
        AnnotationMetadata annotationMetadata = AnnotationMetadata.EMPTY_METADATA;
        if (argumentInjectionPoint != null) {
            Argument<T> argument = argumentInjectionPoint.asArgument();
            Argument<?>[] typeParameters = argument.getTypeParameters();
            if (typeParameters.length != 0) {
                //noinspection unchecked
                eventType = (Argument<T>) typeParameters[0];
            }
            eventQualifier = Qualifiers.forArgument(argument);
            annotationMetadata = argumentInjectionPoint.getAnnotationMetadata();
        }
        if (eventType.getType().equals(Object.class)) {
            //noinspection unchecked
            return (Event<T>) createObjectEvent(
                    annotationMetadata,
                    eventQualifier,
                    argumentInjectionPoint
            );
        }
        return getTypedEvent(annotationMetadata, eventType, eventQualifier, argumentInjectionPoint);
    }

    private Event<Object> createObjectEvent(
            AnnotationMetadata annotationMetadata,
            @Nullable Qualifier qualifier,
            @Nullable InjectionPoint<?> injectionPoint) {
        return new Event<>() {
            @SuppressWarnings("unchecked")
            @Override
            public void fire(Object event) {
                getTypedEvent(
                        annotationMetadata,
                        Argument.of(event.getClass()),
                        qualifier,
                        injectionPoint)
                .fire(event);
            }

            @SuppressWarnings("unchecked")
            @Override
            public <U> CompletionStage<U> fireAsync(U event) {
                return getTypedEvent(
                        annotationMetadata,
                        Argument.of(event.getClass()),
                        qualifier,
                        injectionPoint)
                .fireAsync(event);
            }

            @SuppressWarnings("unchecked")
            @Override
            public <U> CompletionStage<U> fireAsync(U event, NotificationOptions options) {
                return getTypedEvent(
                        annotationMetadata,
                        Argument.of(event.getClass()),
                        qualifier,
                        injectionPoint)
                .fireAsync(event, options);
            }

            @Override
            public Event<Object> select(Annotation... qualifiers) {
                AnnotationMetadata annotationMetadata = AnnotationUtils.annotationMetadataFromQualifierAnnotations(qualifiers);
                Qualifier<Object> qualifier = AnnotationUtils.qualifierFromQualifierAnnotations(annotationMetadata, qualifiers);
                return createObjectEvent(annotationMetadata, qualifier, injectionPoint);
            }

            @Override
            public <U> Event<U> select(Class<U> subtype, Annotation... qualifiers) {
                return getTypedEvent(Argument.of(subtype), qualifiers, injectionPoint);
            }

            @SuppressWarnings("unchecked")
            @Override
            public <U> Event<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
                return (Event<U>) getTypedEvent(
                        Argument.of(subtype.getType()),
                        qualifiers,
                        injectionPoint
                );
            }
        };
    }

    private <K> Event<K> getTypedEvent(
            AnnotationMetadata annotationMetadata,
            Argument<K> eventType,
            Qualifier<K> eventQualifier,
            InjectionPoint<?> injectionPoint) {
        return new OdiEvent<>(
                beanContainer,
                annotationMetadata,
                eventType,
                eventQualifier,
                injectionPoint,
                observerMethodRegistry,
                executorSupplier
        );
    }

    private <K> Event<K> getTypedEvent(Argument<K> eventType, Annotation[] qualifiers, InjectionPoint<?> injectionPoint) {
        AnnotationMetadata annotationMetadata = AnnotationUtils.annotationMetadataFromQualifierAnnotations(qualifiers);
        Qualifier<K> qualifier = AnnotationUtils.qualifierFromQualifierAnnotations(annotationMetadata, qualifiers);
        return new OdiEvent<>(
                beanContainer,
                annotationMetadata,
                eventType,
                qualifier,
                injectionPoint,
                observerMethodRegistry,
                executorSupplier
        );
    }

}
