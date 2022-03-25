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
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.BeanContainer;

import java.util.Collection;

/**
 * Internal ODI {@link BeanContainer}.
 */
@Internal
public interface OdiBeanContainer extends BeanContainer {

    /**
     * Get bean definitions by CDI rules.
     * @param argument The argument
     * @param qualifier The qualifier
     * @param <T> The type
     * @return The beans definitions
     */
    @NonNull <T> Collection<BeanDefinition<T>> getBeanDefinitions(@NonNull Argument<T> argument,
                                                                  @Nullable io.micronaut.context.Qualifier<T> qualifier);

    @NonNull <T> OdiBeanImpl<T> getBean(BeanDefinition<T> beanDefinition);

    @NonNull
    OdiInstance<Object> createInstance();

    @NonNull
    OdiInstance<Object> createInstance(Context context);

    /**
     * Create a custom {@link CreationalContext} that is propagating {@link BeanResolutionContext}.
     * @param contextual The Contextual
     * @param resolutionContext THe resolution context
     * @param <T> The type
     * @return The creational context
     */
    @NonNull
    <T> CreationalContext<T> createCreationalContext(@NonNull Contextual<T> contextual,
                                                     @NonNull BeanResolutionContext resolutionContext);

    BeanContext getBeanContext();

}
