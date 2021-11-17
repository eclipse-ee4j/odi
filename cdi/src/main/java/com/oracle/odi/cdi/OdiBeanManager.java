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
package com.oracle.odi.cdi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.oracle.odi.cdi.events.OdiObserverMethodRegistry;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.context.NormalScope;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Stereotype;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanContainer;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.InterceptionType;
import jakarta.enterprise.inject.spi.Interceptor;
import jakarta.enterprise.inject.spi.ObserverMethod;
import jakarta.inject.Qualifier;
import jakarta.inject.Scope;
import jakarta.interceptor.InterceptorBinding;

final class OdiBeanManager implements BeanContainer {
    private final ApplicationContext applicationContext;
    private final OdiSeContainer container;
    private OdiObserverMethodRegistry observerMethodRegistry;
    private Event<Object> objectEvent;

    OdiBeanManager(OdiSeContainer container, ApplicationContext applicationContext) {
        this.container = container;
        this.applicationContext = applicationContext;
    }

    @Override
    public Object getReference(Bean<?> bean, Type beanType, CreationalContext<?> ctx) {
        if (bean instanceof OdiBeanImpl) {
            if (!(beanType instanceof Class)) {
                throw new IllegalStateException("Not implemented");
            }
            OdiBeanImpl odiBean = (OdiBeanImpl) bean;
            return odiBean.create(ctx);
        } else {
            throw new IllegalArgumentException("Unsupported by bean type: " + bean.getClass());
        }
    }

    @Override
    public Object getInjectableReference(InjectionPoint ij, CreationalContext<?> ctx) {
        // TODO: support qualifiers
        return applicationContext.getBean(Argument.of(ij.getType()));
    }

    @Override
    public <T> CreationalContext<T> createCreationalContext(Contextual<T> contextual) {
        if (contextual instanceof OdiBeanImpl || contextual == null) {
            return new OdiCreationalContext<>();
        } else {
            throw new IllegalArgumentException("Unsupported by contextual type: " + contextual.getClass());
        }
    }

    @Override
    public Set<Bean<?>> getBeans(Type beanType, Annotation... qualifiers) {
        final Collection<? extends BeanDefinition<?>> beanDefinitions = applicationContext.getBeanDefinitions(
                Argument.of(beanType),
                OdiInstance.resolveQualifier(qualifiers)
        );
        return toBeanSet(beanDefinitions);
    }

    private Set<Bean<?>> toBeanSet(Collection<? extends BeanDefinition<?>> beanDefinitions) {
        return beanDefinitions.stream()
                .sorted(OrderUtil.COMPARATOR)
                .map((beanDefinition -> new OdiBeanImpl<>(applicationContext, beanDefinition)))
                .collect(Collectors.toSet());
    }

    @Override
    public Set<Bean<?>> getBeans(String name) {
        return toBeanSet(applicationContext.getBeanDefinitions(Qualifiers.byName(
                Objects.requireNonNull(name, "Name cannot be null")
        )));
    }

    @Override
    public <X> Bean<? extends X> resolve(Set<Bean<? extends X>> beans) {
        if (beans.size() != 1) {
            throw new UnsupportedOperationException();
        }
        return beans.iterator().next();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public <T> Set<ObserverMethod<? super T>> resolveObserverMethods(T event, Annotation... qualifiers) {
        if (observerMethodRegistry == null) {
            observerMethodRegistry = applicationContext.getBean(OdiObserverMethodRegistry.class);
        }
        Argument<?> argument = Argument.of(event.getClass());
        final io.micronaut.context.Qualifier qualifierInstances = AnnotationUtils
                .qualifierFromQualifierAnnotations(qualifiers);
        return (Set) observerMethodRegistry
                .findSetOfObserverMethods(argument, qualifierInstances);
    }

    @Override
    public List resolveInterceptors(InterceptionType type, Annotation... interceptorBindings) {
        return applicationContext.streamOfType(Interceptor.class)
                .filter(c -> {
                    // TODO: support filtering by annotations
                    return c.intercepts(type);
                })
                .collect(Collectors.toList());
    }

    @Override
    public boolean isScope(Class<? extends Annotation> annotationType) {
        return Objects.requireNonNull(annotationType, "Annotation type should not be null")
                .isAnnotationPresent(Scope.class);
    }

    @Override
    public boolean isNormalScope(Class<? extends Annotation> annotationType) {
        return Objects.requireNonNull(annotationType, "Annotation type should not be null")
                .isAnnotationPresent(NormalScope.class);
    }

    @Override
    public boolean isQualifier(Class<? extends Annotation> annotationType) {
        return Objects.requireNonNull(annotationType, "Annotation type should not be null")
                .isAnnotationPresent(Qualifier.class);
    }

    @Override
    public boolean isInterceptorBinding(Class<? extends Annotation> annotationType) {
        final Class<? extends Annotation> t = Objects.requireNonNull(annotationType, "Annotation type should not be null");
        return t.isAnnotationPresent(InterceptorBinding.class)
                || t.isAnnotationPresent(io.micronaut.aop.InterceptorBinding.class);
    }

    @Override
    public boolean isStereotype(Class<? extends Annotation> annotationType) {
        return Objects.requireNonNull(annotationType, "Annotation type should not be null")
                .isAnnotationPresent(Stereotype.class);
    }

    @Override
    public Context getContext(Class<? extends Annotation> scopeType) {
        final List<Context> contexts = applicationContext.streamOfType(Context.class)
                .filter(c -> c.getScope() == scopeType)
                .filter(Context::isActive)
                .collect(Collectors.toList());
        if (contexts.isEmpty()) {
            throw new ContextNotActiveException("No context active for scope: " + scopeType.getSimpleName());
        } else if (contexts.size() > 1) {
            throw new IllegalArgumentException("More than one active context for scope: " + scopeType.getSimpleName());
        } else {
            return contexts.iterator().next();
        }
    }

    @Override
    public Event<Object> getEvent() {
        if (objectEvent == null) {
            objectEvent = applicationContext.getBean(Event.class);
        }
        return objectEvent;
    }

    @Override
    public Instance<Object> createInstance() {
        return container;
    }
}
