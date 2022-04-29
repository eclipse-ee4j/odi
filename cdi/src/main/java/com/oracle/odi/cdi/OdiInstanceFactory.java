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
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Any;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.ArgumentInjectionPoint;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.BeanContainer;

/**
 * Creates instances of {@link jakarta.enterprise.inject.Instance}.
 */
@Factory
public class OdiInstanceFactory {

    /**
     * Builds an instance.
     *
     * @param resolutionContext      The resolution context
     * @param argumentInjectionPoint The argument injection point
     * @param <T>                    The generic type
     * @return The instance
     */
    @Any
    @Dependent
    public <T> Instance<T> build(BeanResolutionContext resolutionContext,
                                 @Nullable ArgumentInjectionPoint<T, T> argumentInjectionPoint) {
        @SuppressWarnings("unchecked")
        Argument<T> injectArgument = (Argument<T>) Argument.OBJECT_ARGUMENT;
        Qualifier<T> qualifier = null;
        if (argumentInjectionPoint != null) {
            Argument<T> argument = argumentInjectionPoint.asArgument();
            Argument[] typeParameters = argument.getTypeParameters();
            if (typeParameters.length != 0) {
                injectArgument = typeParameters[0];
            }
            qualifier = Qualifiers.forArgument(argument);
        }
        return new OdiInstanceImpl<>(
                resolutionContext.getContext(),
                (OdiBeanContainer) resolutionContext.getContext().getBean(BeanContainer.class),
                new DependentContext(resolutionContext),
                injectArgument,
                qualifier
        );
    }
}
