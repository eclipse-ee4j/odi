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
package org.eclipse.odi.cdi.intercept;

import io.micronaut.aop.Interceptor;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.InterceptorRegistry;
import io.micronaut.aop.chain.DefaultInterceptorRegistry;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.annotation.Primary;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.type.Executable;
import jakarta.inject.Singleton;
import org.eclipse.odi.cdi.annotation.DisposerMethod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

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
        if (resolvedInterceptors.length == 0
                && interceptorKind == InterceptorKind.AROUND
                && method.getAnnotationMetadata().getAnnotationValuesByName(AnnotationUtil.ANN_INTERCEPTOR_BINDING).isEmpty()) {
            resolvedInterceptors = resolveClassLevelAroundInterceptors(method, interceptors);
        }
        resolvedInterceptors = selectInterceptorsForKind(method, resolvedInterceptors, interceptorKind);
        sortInterceptors(resolvedInterceptors);
        return resolvedInterceptors;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> Interceptor<T, ?>[] resolveClassLevelAroundInterceptors(Executable<T, ?> method,
                                                                        Collection<BeanRegistration<Interceptor<T, ?>>> interceptors) {
        List<Interceptor<T, ?>> selected = new ArrayList<>(interceptors.size());
        Class<?> declaringType = method.getDeclaringType();
        for (BeanRegistration<Interceptor<T, ?>> interceptor : interceptors) {
            if (hasClassLevelBinding(declaringType, interceptor)) {
                selected.add(interceptor.getBean());
            }
        }
        return selected.toArray(new Interceptor[0]);
    }

    private boolean hasClassLevelBinding(Class<?> declaringType,
                                         BeanRegistration<? extends Interceptor<?, ?>> interceptor) {
        return interceptor.getBeanDefinition()
                .getAnnotationMetadata()
                .getAnnotationValuesByName(AnnotationUtil.ANN_INTERCEPTOR_BINDING)
                .stream()
                .filter(binding -> binding.enumValue("kind", InterceptorKind.class)
                        .map(InterceptorKind.AROUND::equals)
                        .orElse(false))
                .map(binding -> binding.stringValue().orElse(null))
                .filter(Objects::nonNull)
                .anyMatch(bindingName -> classHasAnnotation(declaringType, bindingName));
    }

    private boolean classHasAnnotation(Class<?> declaringType, String annotationName) {
        for (java.lang.annotation.Annotation annotation : declaringType.getAnnotations()) {
            if (annotation.annotationType().getName().equals(annotationName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public <T> Interceptor<T, T>[] resolveConstructorInterceptors(BeanConstructor<T> constructor, Collection<BeanRegistration<Interceptor<T, T>>> interceptors) {
        Interceptor<T, T>[] resolvedInterceptors = defaultInterceptorRegistry.resolveConstructorInterceptors(constructor, interceptors);
        resolvedInterceptors = selectInterceptorsForKind(resolvedInterceptors, InterceptorKind.AROUND_CONSTRUCT);
        sortInterceptors(resolvedInterceptors);
        return resolvedInterceptors;
    }

    private <I extends Interceptor<?, ?>> I[] selectInterceptorsForKind(Executable<?, ?> method,
                                                                        I[] interceptors,
                                                                        InterceptorKind interceptorKind) {
        List<I> selected = new ArrayList<>(interceptors.length);
        for (I interceptor : interceptors) {
            if (!(interceptor instanceof JakartaInterceptorAdapter<?> jakartaInterceptorAdapter)
                    || jakartaInterceptorAdapter.intercepts(interceptorKind)
                    || (method != null && isDisposerAroundInvoke(method, interceptorKind, jakartaInterceptorAdapter))) {
                selected.add(interceptor);
            }
        }
        if (selected.size() == interceptors.length) {
            return interceptors;
        }
        return selected.toArray(Arrays.copyOf(interceptors, selected.size()));
    }

    private <I extends Interceptor<?, ?>> I[] selectInterceptorsForKind(I[] interceptors,
                                                                        InterceptorKind interceptorKind) {
        return selectInterceptorsForKind(null, interceptors, interceptorKind);
    }

    private boolean isDisposerAroundInvoke(Executable<?, ?> method,
                                           InterceptorKind interceptorKind,
                                           JakartaInterceptorAdapter<?> jakartaInterceptorAdapter) {
        return interceptorKind == InterceptorKind.PRE_DESTROY
                && method.getAnnotationMetadata().hasAnnotation(DisposerMethod.class)
                && jakartaInterceptorAdapter.intercepts(InterceptorKind.AROUND);
    }

    private void sortInterceptors(Interceptor<?, ?>[] resolvedInterceptors) {
        Arrays.sort(resolvedInterceptors, (o1, o2) -> {
            if (o1 instanceof JakartaInterceptorAdapter && o2 instanceof JakartaInterceptorAdapter) {
                JakartaInterceptorAdapter j1 = (JakartaInterceptorAdapter) o1;
                JakartaInterceptorAdapter j2 = (JakartaInterceptorAdapter) o2;
                if (j1.isSelfInterceptor() && !j2.isSelfInterceptor()) {
                    return 1;
                }
                if (!j1.isSelfInterceptor() && j2.isSelfInterceptor()) {
                    return -1;
                }
                if (j1.getPriority() == j2.getPriority()) {
                    return j1.getBeanClass().getName().compareTo(j2.getBeanClass().getName());
                }
                return Integer.compare(j1.getPriority(), j2.getPriority());
            }
            return Integer.compare(o1.getOrder(), o2.getOrder());
        });
    }
}
