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

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import com.oracle.odi.cdi.AnnotationUtils;
import com.oracle.odi.cdi.OdiUtils;
import io.micronaut.context.BeanContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.inject.spi.EventContext;
import jakarta.enterprise.inject.spi.EventMetadata;
import jakarta.enterprise.util.TypeLiteral;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The implementation of {@link Event}.
 *
 * @param <T> The event type
 */
@Internal
final class OdiEvent<T> implements Event<T>, OdiEventMetadata {

    private static final Logger EVENT_LOGGER = LoggerFactory.getLogger(ApplicationEventPublisher.class);

    private final BeanContext beanContext;
    private final AnnotationMetadata annotationMetadata;
    private final Argument<T> eventType;
    @Nullable
    private final Qualifier<T> qualifier;
    @Nullable
    private final InjectionPoint<?> injectionPoint;
    private final Supplier<Collection<OdiObserverMethod<T>>> observerMethodsSupplier;
    private final OdiObserverMethodRegistry observerMethodRegistry;
    private final Supplier<Executor> executorSupplier;

    @Nullable
    private jakarta.enterprise.inject.spi.InjectionPoint cdiInjectionPoint;
    @Nullable
    private Set<Annotation> qualifierAnnotations;

    OdiEvent(BeanContext beanContext,
                    AnnotationMetadata annotationMetadata,
                    Argument<T> eventType,
                    @Nullable Qualifier<T> qualifier,
                    @Nullable InjectionPoint<?> injectionPoint,
                    OdiObserverMethodRegistry observerMethodRegistry,
                    Supplier<Executor> executorSupplier) {
        this.beanContext = beanContext;
        this.annotationMetadata = annotationMetadata;
        this.eventType = eventType;
        this.qualifier = qualifier;
        this.injectionPoint = injectionPoint;
        this.observerMethodsSupplier = SupplierUtil.memoizedNonEmpty(() ->
                observerMethodRegistry.findListOfObserverMethods(eventType, qualifier));
        this.executorSupplier = executorSupplier;
        this.observerMethodRegistry = observerMethodRegistry;
    }

    @Override
    public void fire(T event) {
        if (event != null) {
            if (EVENT_LOGGER.isDebugEnabled()) {
                EVENT_LOGGER.debug("Firing event: {}", event);
            }
            notifyObserverMethods(event, observerMethodsSupplier.get());
        }
    }

    public <U extends T> CompletionStage<U> fireAsync(U event) {
        return fireAsync(event, executorSupplier.get());
    }

    @Override
    public <U extends T> CompletionStage<U> fireAsync(U event, NotificationOptions options) {
        return fireAsync(event, options.getExecutor());
    }

    @Override
    public Event<T> select(Annotation... qualifiers) {
        return select(eventType, qualifiers);
    }

    @Override
    public <U extends T> Event<U> select(Class<U> subtype, Annotation... qualifiers) {
        return select(Argument.of(subtype), qualifiers);
    }

    @Override
    public <U extends T> Event<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
        Argument<U> argument = (Argument<U>) Argument.of(subtype.getType());
        return select(argument, qualifiers);
    }

    private <U extends T> OdiEvent<U> select(Argument<U> argument, Annotation[] annotations) {
        AnnotationMetadata annotationMetadata = this.annotationMetadata;
        Qualifier<U> qualifier = (Qualifier<U>) this.qualifier;
        if (annotations != null && annotations.length > 0) {
            annotationMetadata = new AnnotationMetadataHierarchy(
                    AnnotationUtils.annotationMetadataFromQualifierAnnotations(annotations), this.annotationMetadata
            );
            final Qualifier<U> resolvedQualifiers = AnnotationUtils
                    .qualifierFromQualifierAnnotations(annotationMetadata, annotations);
            qualifier = Qualifiers.byQualifiers(
                    resolvedQualifiers, (Qualifier<U>) this.qualifier
            );
        }
        return new OdiEvent<>(
                beanContext,
                annotationMetadata,
                argument,
                qualifier,
                injectionPoint,
                observerMethodRegistry,
                executorSupplier
        );
    }

    private <U extends T> CompletableFuture<U> fireAsync(U event, Executor executor) {
        Objects.requireNonNull(event, "Event cannot be null");
        CompletableFuture<U> future = new CompletableFuture<>();
        Collection<OdiObserverMethod<T>> observerMethods = observerMethodsSupplier.get();
        executor.execute(() -> {
            try {
                notifyObserverMethods(event, observerMethods);
                future.complete(event);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private void notifyObserverMethods(@NonNull T event, Collection<OdiObserverMethod<T>> observerMethods) {
        if (!observerMethods.isEmpty()) {
            for (OdiObserverMethod<T> observerMethod : observerMethods) {
                try {
                    if (EVENT_LOGGER.isTraceEnabled()) {
                        EVENT_LOGGER.trace("Invoking observer method [{}] for event: {}", observerMethod, event);
                    }
                    observerMethod.notify(createContext(event));
                } catch (ClassCastException ex) {
                    String msg = ex.getMessage();
                    if (msg == null || msg.startsWith(event.getClass().getName())) {
                        if (EVENT_LOGGER.isDebugEnabled()) {
                            EVENT_LOGGER.debug("Incompatible observerMethod for event: " + observerMethod, ex);
                        }
                    } else {
                        throw ex;
                    }
                }
            }
        }
    }

    private EventContext<T> createContext(T event) {
        return new EventContext<T>() {
            @Override
            public T getEvent() {
                return event;
            }

            @Override
            public EventMetadata getMetadata() {
                return OdiEvent.this;
            }
        };
    }

    @Override
    public Qualifier<?> getQualifier() {
        return qualifier;
    }

    @Override
    public Set<Annotation> getQualifiers() {
        if (qualifier == null) {
            return Collections.emptySet();
        }
        if (qualifierAnnotations == null) {
            qualifierAnnotations = AnnotationUtils.synthesizeQualifierAnnotations(annotationMetadata);
        }
        return qualifierAnnotations;
    }

    @Override
    public jakarta.enterprise.inject.spi.InjectionPoint getInjectionPoint() {
        if (injectionPoint == null) {
            return null;
        }
        if (cdiInjectionPoint == null) {
            cdiInjectionPoint = OdiUtils.createCDIInjectionPoint(beanContext, injectionPoint, eventType);
        }
        return cdiInjectionPoint;
    }

    @Override
    public Type getType() {
        if (eventType.getTypeParameters().length == 0) {
            return eventType.getType();
        }
        return eventType.asParameterizedType();
    }
}
