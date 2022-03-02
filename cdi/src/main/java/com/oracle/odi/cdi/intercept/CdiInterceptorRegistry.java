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
