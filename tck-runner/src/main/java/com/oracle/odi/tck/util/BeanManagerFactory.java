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
package com.oracle.odi.tck.util;

import io.micronaut.context.annotation.Factory;
import jakarta.el.ELResolver;
import jakarta.el.ExpressionFactory;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.AnnotatedField;
import jakarta.enterprise.inject.spi.AnnotatedMember;
import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.AnnotatedParameter;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanAttributes;
import jakarta.enterprise.inject.spi.BeanContainer;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Decorator;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.InjectionTargetFactory;
import jakarta.enterprise.inject.spi.InterceptionFactory;
import jakarta.enterprise.inject.spi.InterceptionType;
import jakarta.enterprise.inject.spi.Interceptor;
import jakarta.enterprise.inject.spi.ObserverMethod;
import jakarta.enterprise.inject.spi.ProducerFactory;
import jakarta.inject.Singleton;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;

/**
 * Default {@link BeanManager} factory for TCK.
 * (CDI-lite doesn't require BeanManager to be implemented)
 * Should be removed after TCK doesn't require any tests to have bean manager.
 */
@Factory
public class BeanManagerFactory {
    @Default
    @Singleton
    @io.micronaut.context.annotation.Bean(typed = BeanManager.class)
    final BeanManager createBeanManager(BeanContainer beanContainer) {
        return new BeanManager() {
            @Override
            public Bean<?> getPassivationCapableBean(String id) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void validate(InjectionPoint injectionPoint) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> Set<ObserverMethod<? super T>> resolveObserverMethods(T event, Annotation... qualifiers) {
                return beanContainer.resolveObserverMethods(event, qualifiers);
            }

            @Override
            public List<Decorator<?>> resolveDecorators(Set<Type> types, Annotation... qualifiers) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isPassivatingScope(Class<? extends Annotation> annotationType) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Set<Annotation> getInterceptorBindingDefinition(Class<? extends Annotation> bindingType) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Set<Annotation> getStereotypeDefinition(Class<? extends Annotation> stereotype) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean areQualifiersEquivalent(Annotation qualifier1, Annotation qualifier2) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean areInterceptorBindingsEquivalent(Annotation interceptorBinding1, Annotation interceptorBinding2) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getQualifierHashCode(Annotation qualifier) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getInterceptorBindingHashCode(Annotation interceptorBinding) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ELResolver getELResolver() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ExpressionFactory wrapExpressionFactory(ExpressionFactory expressionFactory) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> AnnotatedType<T> createAnnotatedType(Class<T> type) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> InjectionTargetFactory<T> getInjectionTargetFactory(AnnotatedType<T> annotatedType) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <X> ProducerFactory<X> getProducerFactory(AnnotatedField<? super X> field, Bean<X> declaringBean) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <X> ProducerFactory<X> getProducerFactory(AnnotatedMethod<? super X> method, Bean<X> declaringBean) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> BeanAttributes<T> createBeanAttributes(AnnotatedType<T> type) {
                throw new UnsupportedOperationException();
            }

            @Override
            public BeanAttributes<?> createBeanAttributes(AnnotatedMember<?> type) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> Bean<T> createBean(BeanAttributes<T> attributes, Class<T> beanClass, InjectionTargetFactory<T> injectionTargetFactory) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T, X> Bean<T> createBean(BeanAttributes<T> attributes, Class<X> beanClass, ProducerFactory<X> producerFactory) {
                throw new UnsupportedOperationException();
            }

            @Override
            public InjectionPoint createInjectionPoint(AnnotatedField<?> field) {
                throw new UnsupportedOperationException();
            }

            @Override
            public InjectionPoint createInjectionPoint(AnnotatedParameter<?> parameter) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T extends Extension> T getExtension(Class<T> extensionClass) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> InterceptionFactory<T> createInterceptionFactory(CreationalContext<T> ctx, Class<T> clazz) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Object getReference(Bean<?> bean, Type beanType, CreationalContext<?> ctx) {
                return beanContainer.getReference(bean, beanType, ctx);
            }

            @Override
            public Object getInjectableReference(InjectionPoint ij, CreationalContext<?> ctx) {
                return null;
            }

            @Override
            public <T> CreationalContext<T> createCreationalContext(Contextual<T> contextual) {
                return beanContainer.createCreationalContext(contextual);
            }

            @Override
            public Set<Bean<?>> getBeans(Type beanType, Annotation... qualifiers) {
                return beanContainer.getBeans(beanType, qualifiers);
            }

            @Override
            public Set<Bean<?>> getBeans(String name) {
                return beanContainer.getBeans(name);
            }

            @Override
            public <X> Bean<? extends X> resolve(Set<Bean<? extends X>> beans) {
                return beanContainer.resolve(beans);
            }

            @Override
            public List<Interceptor<?>> resolveInterceptors(InterceptionType type, Annotation... interceptorBindings) {
                return beanContainer.resolveInterceptors(type, interceptorBindings);
            }

            @Override
            public boolean isScope(Class<? extends Annotation> annotationType) {
                return beanContainer.isScope(annotationType);
            }

            @Override
            public boolean isNormalScope(Class<? extends Annotation> annotationType) {
                return beanContainer.isNormalScope(annotationType);
            }

            @Override
            public boolean isQualifier(Class<? extends Annotation> annotationType) {
                return beanContainer.isQualifier(annotationType);
            }

            @Override
            public boolean isStereotype(Class<? extends Annotation> annotationType) {
                return beanContainer.isStereotype(annotationType);
            }

            @Override
            public boolean isInterceptorBinding(Class<? extends Annotation> annotationType) {
                return beanContainer.isInterceptorBinding(annotationType);
            }

            @Override
            public Context getContext(Class<? extends Annotation> scopeType) {
                return beanContainer.getContext(scopeType);
            }

            @Override
            public Event<Object> getEvent() {
                return beanContainer.getEvent();
            }

            @Override
            public Instance<Object> createInstance() {
                return beanContainer.createInstance();
            }
        };
    }
}
