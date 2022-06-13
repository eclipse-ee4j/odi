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
package org.eclipse.odi.cdi.events;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.odi.cdi.DefaultQualifier;
import org.eclipse.odi.cdi.OdiBeanContainer;
import org.eclipse.odi.cdi.OdiBeanImpl;
import org.eclipse.odi.cdi.OdiUtils;
import org.eclipse.odi.cdi.annotation.ObservesMethod;
import io.micronaut.context.BeanContext;
import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import jakarta.enterprise.event.Reception;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.build.compatible.spi.Parameters;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticObserver;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.EventContext;
import jakarta.enterprise.inject.spi.EventMetadata;
import jakarta.enterprise.inject.spi.InjectionPoint;

@Internal
final class SyntheticObserverMethod<B, E> extends AbstractOdiObserverMethod<E> {
    private final Argument<E> observerArgument;
    private final io.micronaut.context.Qualifier<E> eventQualifier;
    private final BeanDefinition<B> syntheticObserverBeanDefinition;
    private final BeanContext beanContext;
    private final Parameters parameters;
    private final OdiBeanContainer beanContainer;
    private final ExecutableMethod<B, Object> executableMethod;

    private Set<Annotation> observedQualifiers;

    SyntheticObserverMethod(
            OdiBeanContainer beanContainer,
            BeanDefinition<B> syntheticObserverBeanDefinition,
            ExecutableMethod<B, Object> executableMethod) {
        super(syntheticObserverBeanDefinition.findAnnotation(ObservesMethod.class).orElse(AnnotationValue.builder(ObservesMethod.class).build()));
        List<Argument<?>> typeArguments = syntheticObserverBeanDefinition.getTypeArguments(SyntheticObserver.class);
        if (CollectionUtils.isNotEmpty(typeArguments)) {
            //noinspection unchecked
            observerArgument = (Argument<E>) typeArguments.get(0);
        } else {
            throw new IllegalStateException("Synthetic observer lacks type argument: " + syntheticObserverBeanDefinition.getBeanType());
        }
        //noinspection unchecked
        if (syntheticObserverBeanDefinition.hasDeclaredAnnotation(Default.class)) {
            this.eventQualifier = DefaultQualifier.instance();
        } else {
            this.eventQualifier = (Qualifier<E>) syntheticObserverBeanDefinition.getDeclaredQualifier();
        }
        this.syntheticObserverBeanDefinition = syntheticObserverBeanDefinition;
        this.beanContext = beanContainer.getBeanContext();
        this.parameters = OdiUtils.createParameters(syntheticObserverBeanDefinition);
        this.beanContainer = beanContainer;
        this.executableMethod = executableMethod;
    }

    @Override
    public Argument<E> getObservedArgument() {
        return observerArgument;
    }

    @Override
    public Qualifier<E> getObservedQualifier() {
        return eventQualifier;
    }

    @Override
    public Class<?> getBeanClass() {
        return syntheticObserverBeanDefinition.getBeanType();
    }

    @Override
    public Set<Annotation> getObservedQualifiers() {
        if (observedQualifiers == null) {
            observedQualifiers = Arrays.stream(syntheticObserverBeanDefinition.getAnnotationMetadata().synthesizeAnnotationsByType(jakarta.inject.Qualifier.class))
                    .collect(Collectors.toSet());
        }
        return observedQualifiers;
    }

    @Override
    public Bean<?> getDeclaringBean() {
        // undefined for synthetic observers
        return new OdiBeanImpl<>(beanContext, syntheticObserverBeanDefinition);
    }

    @Override
    public void notify(E event) {
        notify(event, new EventContext<E>() {
            @Override
            public E getEvent() {
                return event;
            }

            @Override
            public EventMetadata getMetadata() {
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
        });
    }

    @Override
    public void notify(EventContext<E> eventContext) {
        notify(eventContext.getEvent(), eventContext);
    }

    private void notify(E event, @Nullable EventContext<E> eventContext) {
        if (getReception() == Reception.IF_EXISTS && !beanContext.containsBean(syntheticObserverBeanDefinition.asArgument())) {
            return;
        }

        beanContainer.fulfillAndExecuteMethod(syntheticObserverBeanDefinition, executableMethod, argument -> {
            if (argument.getType() == EventContext.class) {
                return eventContext;
            } else if (argument.getType() == Parameters.class) {
                return parameters;
            }
            return null;
        });
    }
}
