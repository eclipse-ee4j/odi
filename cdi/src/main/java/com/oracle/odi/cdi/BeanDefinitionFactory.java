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

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Any;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.ArgumentInjectionPoint;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.InjectionPoint;

/**
 * This should be implemented in core.
 */
@Factory
public class BeanDefinitionFactory {

    @Any
    @Prototype
    final <T> BeanDefinition<T> beanDefinition(InjectionPoint<T> injectionPoint, BeanContext beanContext) {
        if (injectionPoint instanceof ArgumentInjectionPoint) {
            Argument<?> argument = ((ArgumentInjectionPoint<?, ?>) injectionPoint).getArgument();
            return beanContext.getBeanDefinition(argument.getTypeParameters()[0]);
        }
        throw new IllegalStateException();
    }

}
