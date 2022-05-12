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

import com.oracle.odi.cdi.context.DependentContext;
import com.oracle.odi.cdi.context.SingletonContext;
import com.oracle.odi.cdi.events.OdiObserverMethodRegistry;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.DefaultBeanResolutionContext;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.NormalScope;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.AmbiguousResolutionException;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Stereotype;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.InterceptionType;
import jakarta.enterprise.inject.spi.Interceptor;
import jakarta.enterprise.inject.spi.ObserverMethod;
import jakarta.inject.Qualifier;
import jakarta.inject.Scope;
import jakarta.inject.Singleton;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

final class OdiBeanContainerImpl implements OdiBeanContainer {

    private final ApplicationContext applicationContext;
    private final OdiSeContainer container;
    private OdiObserverMethodRegistry observerMethodRegistry;
    private Event<Object> objectEvent;

    OdiBeanContainerImpl(OdiSeContainer container, ApplicationContext applicationContext) {
        this.container = container;
        this.applicationContext = applicationContext;
    }

    @Override
    public <B, R> Object fulfillAndExecuteMethod(BeanDefinition<B> beanDefinition,
                                                 ExecutableMethod<B, R> executableMethod,
                                                 Function<Argument<?>, Object> valueSupplier) {
        Argument<?>[] arguments = executableMethod.getArguments();
        Object[] values = new Object[arguments.length];
        try (BeanResolutionContext resolutionContext = new DefaultBeanResolutionContext(getBeanContext(), beanDefinition)) {
            DependentContext dependentContext = new DependentContext(resolutionContext);
            for (int i = 0; i < arguments.length; i++) {
                Argument<?> argument = arguments[i];
                Object value = valueSupplier.apply(argument);
                if (value != null) {
                    values[i] = value;
                } else {
                    try (BeanResolutionContext.Path ignore = resolutionContext.getPath().pushMethodArgumentResolve(
                            beanDefinition,
                            executableMethod.getMethodName(),
                            argument,
                            arguments,
                            false
                    )) {
                        if (argument.getType() == Instance.class) {
                            Instance<?> instance = createInstance(dependentContext).select(argument.getFirstTypeVariable()
                                    .orElseThrow(() -> new IllegalArgumentException("Expected the type of Instance!")));
                            values[i] = instance;
                        } else {
                            Instance<?> instance = createInstance(dependentContext).select(argument);
                            values[i] = instance.get();
                        }
                    }
                }
            }
            OdiBean<B> bean = getBean(beanDefinition);
            CreationalContext<B> creationalContext = createCreationalContext(bean);
            Context beanContext = isDependent(bean.getScope()) ? dependentContext : getContext(bean.getScope());
            B beanInstance = beanContext.get(bean, creationalContext);
            Object result = executableMethod.invoke(beanInstance, values);
            dependentContext.destroy();
            return result;
        }
    }

    @Override
    public <T> OdiBeanImpl<T> getBean(BeanDefinition<T> beanDefinition) {
        return new OdiBeanImpl<>(applicationContext, beanDefinition);
    }

    @Override
    public <T> OdiBeanImpl<T> getBean(Argument<T> argument, io.micronaut.context.Qualifier<T> qualifier) {
        Collection<BeanDefinition<T>> beanDefinitions = getBeanDefinitions(argument, qualifier);
        if (beanDefinitions.isEmpty()) {
            throw new UnsatisfiedResolutionException("No bean found for argument: " + argument + " and qualifier: " + qualifier);
        }
        if (beanDefinitions.size() > 1) {
            throw new AmbiguousResolutionException("Multiple beans found for argument: " + argument + " and qualifier: " + qualifier);
        }
        return new OdiBeanImpl<>(applicationContext, beanDefinitions.iterator().next());
    }

    @Override
    public <T> Collection<OdiBean<T>> getBeans(Argument<T> argument, io.micronaut.context.Qualifier<T> qualifier) {
        return getBeanDefinitions(argument, qualifier).stream()
                .map(bd -> new OdiBeanImpl<>(applicationContext, bd))
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public <T> Collection<BeanDefinition<T>> getBeanDefinitions(Argument<T> argument, io.micronaut.context.Qualifier<T> qualifier) {
        if (qualifier == null) {
            qualifier = DefaultQualifier.instance();
        }
        Collection<BeanDefinition<T>> beanDefinitions = applicationContext.getBeanDefinitions(argument, qualifier);
        if (beanDefinitions.isEmpty() || beanDefinitions.size() == 1) {
            return beanDefinitions;
        }

        List<BeanDefinition<T>> alternatives = beanDefinitions
                .stream()
                .filter(bd -> bd.hasAnnotation(Alternative.class))
                .collect(Collectors.toList());
        if (!alternatives.isEmpty()) {
            return alternatives.stream()
                    .sorted(Comparator.<BeanDefinition<T>>comparingInt(bd -> {
                        AnnotationValue<Priority> annotation = bd.getAnnotation(Priority.class);
                        if (annotation == null) {
                            return 0;
                        }
                        return annotation.intValue().orElse(0);
                    }).reversed())
                    .limit(1)
                    .collect(Collectors.toList());
        }
        return beanDefinitions;
    }

    @Override
    public Object getReference(Bean<?> bean, Type beanType, CreationalContext<?> ctx) {
        if (bean instanceof OdiBean) {
            if (!(beanType instanceof Class)) {
                throw new IllegalStateException("Not implemented");
            }
            OdiBean<Object> odiBean = (OdiBean<Object>) bean;
            CreationalContext creationalContext = ctx;
            Object instance = odiBean.create(creationalContext);
            if (!((Class<?>) beanType).isInstance(instance)) {
                throw new IllegalArgumentException("Invalid instance!");
            }
            return instance;
        } else {
            throw new IllegalArgumentException("Unsupported by bean type: " + bean.getClass());
        }
    }

    @Override
    public <T> CreationalContext<T> createCreationalContext(Contextual<T> contextual) {
        return new OdiCreationalContext<>(getBeanContext(), contextual);
    }

    @Override
    public Set<Bean<?>> getBeans(Type beanType, Annotation... qualifiers) {
        return getBeans(Argument.of(beanType), OdiInstanceImpl.resolveQualifier(qualifiers)).stream()
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Set<Bean<?>> getBeans(String name) {
        return getBeans(Argument.OBJECT_ARGUMENT, Qualifiers.byName(
                Objects.requireNonNull(name, "Name cannot be null")
        )).stream().collect(Collectors.toUnmodifiableSet());
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
        return observerMethodRegistry
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

    private boolean isDependent(Class<? extends Annotation> annotationType) {
        return annotationType == Dependent.class;
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
        if (scopeType == Dependent.class || scopeType == null) {
            // TODO: make contextual
            return new DependentContext(null);
        }
        if (scopeType == Singleton.class) {
            return SingletonContext.INSTANCE;
        }
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
    public OdiInstance<Object> createInstance() {
        return container;
    }

    @Override
    public OdiInstance<Object> createInstance(Context context) {
        return container.select(context);
    }

    @Override
    public BeanContext getBeanContext() {
        return applicationContext;
    }
}
