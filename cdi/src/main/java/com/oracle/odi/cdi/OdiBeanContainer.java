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
package com.oracle.odi.cdi;

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.inject.spi.BeanContainer;

import java.util.Collection;
import java.util.function.Function;

/**
 * ODI specific {@link BeanContainer} implementation.
 */
@Internal
public interface OdiBeanContainer extends BeanContainer {

    /**
     * Get bean definitions by CDI rules.
     *
     * @param argument  The argument
     * @param qualifier The qualifier
     * @param <T>       The type
     * @return The beans definitions
     */
    @NonNull
    <T> Collection<BeanDefinition<T>> getBeanDefinitions(@NonNull Argument<T> argument,
                                                         @Nullable io.micronaut.context.Qualifier<T> qualifier);

    /**
     * Gets a bean resolved by {@link BeanDefinition}.
     *
     * @param beanDefinition The bean definition
     * @param <T>            The bean type
     * @return The bean
     */
    @NonNull
    <T> OdiBean<T> getBean(@NonNull BeanDefinition<T> beanDefinition);

    /**
     * Gets a bean resolved by an argument and a qualifier.
     *
     * @param argument  The argument
     * @param qualifier The qualifier
     * @param <T>       The bean type
     * @return The bean
     */
    @NonNull
    <T> OdiBean<T> getBean(@NonNull Argument<T> argument,
                           @Nullable io.micronaut.context.Qualifier<T> qualifier);

    /**
     * Get beans resolved by an argument and a qualifier.
     *
     * @param argument  The argument
     * @param qualifier The qualifier
     * @param <T>       The bean type
     * @return Resolved beans
     */
    @NonNull
    <T> Collection<OdiBean<T>> getBeans(@NonNull Argument<T> argument,
                                        @Nullable io.micronaut.context.Qualifier<T> qualifier);

    /**
     * @return Basic instance
     */
    @NonNull
    OdiInstance<Object> createInstance();

    /**
     * Basic instance with an associated context.
     *
     * @param context The context
     * @return Basic instance
     */
    @NonNull
    OdiInstance<Object> createInstance(@NonNull Context context);

    /**
     * Gets {@link BeanContext}.
     *
     * @return The bean context
     */
    @NonNull
    BeanContext getBeanContext();

    /**
     * Fulfill and execute method. (event handler, disposer)
     *
     * @param beanDefinition   The bean definition
     * @param executableMethod The method to be fulfilled and executed
     * @param valueSupplier    The argument value supplier
     * @param <B>              The bean type
     * @param <R>              The result type
     * @return return value
     */
    <B, R> Object fulfillAndExecuteMethod(BeanDefinition<B> beanDefinition,
                                          ExecutableMethod<B, R> executableMethod,
                                          Function<Argument<?>, Object> valueSupplier);
}
