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
package org.eclipse.odi.cdi;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.exceptions.BeanCreationException;
import io.micronaut.context.scope.BeanCreationContext;
import io.micronaut.context.scope.CreatedBean;
import io.micronaut.context.scope.CustomScope;
import io.micronaut.context.scope.CustomScopeRegistry;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.inject.BeanType;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.spi.AlterableContext;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.CreationException;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Overrides the default custom scope registry to support CDI {@link jakarta.enterprise.context.spi.Context} concept.
 */
final class OdiCustomScopeRegistry implements CustomScopeRegistry {
    private final BeanContext beanContext;
    private volatile Map<String, Context> contextMap = null;
    private volatile Map<Context, CustomScope<?>> scopesMap = new ConcurrentHashMap<>();
    private OdiBeanContainer beanContainer;

    OdiCustomScopeRegistry(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    private OdiBeanContainer getBeanContainer() {
        if (beanContainer == null) {
            beanContainer = beanContext.getBean(OdiBeanContainer.class);
        }
        return beanContainer;
    }

    @Override
    public Optional<CustomScope<?>> findDeclaredScope(@NonNull BeanType<?> beanType) {
        final Class<? extends Annotation> scope = MetaAnnotationSupport.resolveDeclaredScope(beanType.getAnnotationMetadata());
        if (scope != Dependent.class) {
            return findScope(scope.getName());
        }
        return Optional.empty();
    }

    @Override
    public Optional<CustomScope<?>> findScope(String scopeAnnotation) {
        if (this.contextMap == null) {
            synchronized (this) {
                if (this.contextMap == null) {
                    this.contextMap = beanContext.streamOfType(Context.class).collect(Collectors.toMap(
                            (context -> context.getScope().getName()),
                            (context -> context)
                    ));
                }
            }
        }
        return Optional.ofNullable(contextMap.get(scopeAnnotation))
                .map(context -> scopesMap.computeIfAbsent(context, OdiCustomScope::new));
    }

    @Override
    public Optional<CustomScope<?>> findScope(Class<? extends Annotation> scopeAnnotation) {
        return findScope(Objects.requireNonNull(scopeAnnotation, "Scope annotation cannot be null").getName());
    }

    private <T> Contextual<T> createContextual(BeanContext beanContext, BeanCreationContext<T> creationContext) {
        return new OdiBeanImpl<>(beanContext, creationContext.definition()) {
            @Override
            public T create(CreationalContext<T> creationalContext) {
                if (creationalContext instanceof OdiCreationalContext) {
                    try {
                        final CreatedBean<T> createdBean = creationContext.create();
                        ((OdiCreationalContext<T>) creationalContext).setCreatedBean(createdBean);
                        return createdBean.bean();
                    } catch (BeanCreationException e) {
                        throw new CreationException(e.getMessage(), e);
                    }
                } else {
                    throw new CreationException("Not an ODI creational context");
                }
            }

            @Override
            public void destroy(T instance, CreationalContext<T> creationalContext) {
                if (creationalContext instanceof OdiCreationalContext) {
                    try {
                        CreatedBean<T> cb =
                                ((OdiCreationalContext<T>) creationalContext).getCreatedBean();
                        if (cb != null) {
                            cb.close();
                        }
                    } catch (BeanCreationException e) {
                        throw new CreationException(e.getMessage(), e);
                    }
                } else {
                    throw new CreationException("Not an ODI creational context");
                }
            }
        };
    }

    final class OdiCustomScope<A extends Annotation> implements CustomScope<A> {
        private final Context context;
        private final Map<BeanIdentifier, Contextual<?>> createdContextuals = new ConcurrentHashMap<>();

        OdiCustomScope(Context context) {
            this.context = context;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Class<A> annotationType() {
            return (Class<A>) context.getScope();
        }

        @Override
        public <T> T getOrCreate(BeanCreationContext<T> creationContext) {
            final OdiBean<T> contextual = getBeanContainer().getBean(creationContext.definition());
            final T bean = context.get(contextual);
            if (bean != null) {
                return bean;
            } else {
                createdContextuals.put(creationContext.id(), contextual);
                return context.get(
                        createContextual(beanContext, creationContext),
                        new OdiCreationalContext<>(beanContext, contextual)
                );

            }
        }

        @Override
        public <T> Optional<T> remove(BeanIdentifier identifier) {
            if (context instanceof AlterableContext) {
                final Contextual<?> contextual = createdContextuals.remove(identifier);
                if (contextual != null) {
                    @SuppressWarnings("unchecked") final T bean = (T) context.get(contextual);
                    if (bean != null) {
                        ((AlterableContext) context).destroy(contextual);
                        return Optional.of(bean);
                    }
                }
            }
            return Optional.empty();
        }

        @Override
        public <T> Optional<BeanRegistration<T>> findBeanRegistration(BeanDefinition<T> beanDefinition) {
            for (Map.Entry<BeanIdentifier, Contextual<?>> e : createdContextuals.entrySet()) {
                BeanIdentifier identifier = e.getKey();
                Contextual<?> contextual = e.getValue();
                if (contextual instanceof OdiBean) {
                    OdiBean<T> odiBean = (OdiBean) contextual;
                    BeanDefinition<T> odiBeanDefinition = odiBean.getBeanDefinition();
                    if (odiBeanDefinition.equals(beanDefinition)) {
                        final T bean = (T) context.get(contextual);
                        return Optional.of(
                                BeanRegistration.of(beanContext, identifier, odiBeanDefinition, bean)
                        );
                    }
                }
            }
            return Optional.empty();
        }
    }

}
