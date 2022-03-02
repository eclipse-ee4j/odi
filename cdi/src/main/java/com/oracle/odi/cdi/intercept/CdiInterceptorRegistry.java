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
package com.oracle.odi.cdi.intercept;

import io.micronaut.aop.Interceptor;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.InterceptorRegistry;
import io.micronaut.aop.chain.DefaultInterceptorRegistry;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.annotation.Primary;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.type.Executable;
import jakarta.inject.Singleton;

import java.util.Arrays;
import java.util.Collection;

/**
 * CDI specific interceptor registry that supports custom interceptor order.
 */
@Primary
@Singleton
public class CdiInterceptorRegistry implements InterceptorRegistry {

    private final DefaultInterceptorRegistry defaultInterceptorRegistry;

    public CdiInterceptorRegistry(BeanContext beanContext) {
        this.defaultInterceptorRegistry = new DefaultInterceptorRegistry(beanContext);
    }

    @Override
    public <T> Interceptor<T, ?>[] resolveInterceptors(Executable<T, ?> method,
                                                       Collection<BeanRegistration<Interceptor<T, ?>>> interceptors,
                                                       InterceptorKind interceptorKind) {
        Interceptor<T, ?>[] resolvedInterceptors = defaultInterceptorRegistry.resolveInterceptors(method, interceptors, interceptorKind);
        sortInterceptors(resolvedInterceptors);
        return resolvedInterceptors;
    }

    @Override
    public <T> Interceptor<T, T>[] resolveConstructorInterceptors(BeanConstructor<T> constructor, Collection<BeanRegistration<Interceptor<T, T>>> interceptors) {
        Interceptor<T, T>[] resolvedInterceptors = defaultInterceptorRegistry.resolveConstructorInterceptors(constructor, interceptors);
        sortInterceptors(resolvedInterceptors);
        return resolvedInterceptors;
    }

    private void sortInterceptors(Interceptor<?, ?>[] resolvedInterceptors) {
        Arrays.sort(resolvedInterceptors, (o1, o2) -> {
            if (o1 instanceof JakartaInterceptorAdapter && o2 instanceof JakartaInterceptorAdapter) {
                JakartaInterceptorAdapter j1 = (JakartaInterceptorAdapter) o1;
                JakartaInterceptorAdapter j2 = (JakartaInterceptorAdapter) o2;
                if (j1.getPriority() == j2.getPriority()) {
                    return j1.getBeanClass().getName().compareTo(j2.getBeanClass().getName());
                }
                return Integer.compare(j1.getPriority(), j2.getPriority());
            }
            return Integer.compare(o1.getOrder(), o2.getOrder());
        });
    }
}
