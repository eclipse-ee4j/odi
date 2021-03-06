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
package org.eclipse.odi.cdi.events;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.enterprise.event.ObserverException;
import jakarta.enterprise.event.Reception;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.EventContext;
import jakarta.enterprise.inject.spi.EventMetadata;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Qualifier;
import org.eclipse.odi.cdi.OdiBeanContainer;
import org.eclipse.odi.cdi.annotation.ObservesMethod;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of {@link OdiObserverMethod} that is using {@link ExecutableMethod} to trigger the method.
 *
 * @param <B> The bean type
 * @param <E> The event type
 */
@Internal
final class ExecutableObserverMethod<B, E> extends AbstractOdiObserverMethod<E> {

    private final OdiBeanContainer beanContainer;
    private final BeanDefinition<B> originalBeanDefinition;
    private final BeanDefinition<B> beanDefinition;
    private final ExecutableMethod<B, Object> executableMethod;
    private final Argument<E> eventArgument;
    private final io.micronaut.context.Qualifier<E> eventQualifier;
    private Set<Annotation> observedQualifiers;

    ExecutableObserverMethod(OdiBeanContainer beanContainer,
                             BeanDefinition<B> originalBeanDefinition,
                             BeanDefinition<B> beanDefinition,
                             ExecutableMethod<B, Object> executableMethod) {
        super(executableMethod.getAnnotation(ObservesMethod.class));
        this.beanContainer = beanContainer;
        this.originalBeanDefinition = originalBeanDefinition;
        this.beanDefinition = beanDefinition;
        this.executableMethod = executableMethod;
        int eventArgumentsIndex = observesMethodAnnotationValue.intValue("eventArgumentIndex").getAsInt();
        this.eventArgument = Objects.requireNonNull((Argument<E>) executableMethod.getArguments()[eventArgumentsIndex]);
        this.eventQualifier = Qualifiers.forArgument(eventArgument);
    }

    @Override
    public Class<?> getBeanClass() {
        return executableMethod.getDeclaringType();
    }

    @Override
    public Bean<?> getDeclaringBean() {
        return beanContainer.getBean(originalBeanDefinition);
    }

    @Override
    public Set<Annotation> getObservedQualifiers() {
        if (observedQualifiers == null) {
            observedQualifiers = Arrays.stream(eventArgument.getAnnotationMetadata().synthesizeAnnotationsByType(Qualifier.class))
                    .collect(Collectors.toSet());
        }
        return observedQualifiers;
    }

    @Override
    public Argument<E> getObservedArgument() {
        return eventArgument;
    }

    @Override
    public io.micronaut.context.Qualifier<E> getObservedQualifier() {
        return eventQualifier;
    }

    @Override
    public void notify(E event) {
        notify(event, null);
    }

    @Override
    public void notify(EventContext<E> eventContext) {
        notify(eventContext.getEvent(), eventContext);
    }

    private void notify(E event, EventContext eventContext) {
        if (getReception() == Reception.IF_EXISTS && !beanContainer.getBeanContext().containsBean(beanDefinition.asArgument())) {
            return;
        }
        try {
            beanContainer.fulfillAndExecuteMethod(beanDefinition, executableMethod, argument -> {
                if (Objects.equals(argument, eventArgument)) {
                    return event;
                } else if (argument.getType() == EventMetadata.class) {
                    if (eventContext == null) {
                        return new EventMetadata() {
                            @Override
                            public Set<Annotation> getQualifiers() {
                                return Collections.emptySet();
                            }

                            @Override
                            public InjectionPoint getInjectionPoint() {
                                return null;
                            }

                            @Override
                            public Type getType() {
                                return getObservedType();
                            }
                        };
                    }
                    return eventContext.getMetadata();
                }
                return null;
            });
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ObserverException(e);
        }
    }

    @Override
    public String toString() {
        return "ExecutableObserverMethod: "
                + executableMethod.getDeclaringType().getName()
                + " " + executableMethod.getDescription();
    }
}
