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
package org.eclipse.odi.cdi.events;

import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import jakarta.enterprise.inject.spi.ObserverMethod;

/**
 * The extension of {@link ObserverMethod} with the implementation specific helper methods.
 *
 * @param <T>
 */
@Internal
public interface OdiObserverMethod<T> extends ObserverMethod<T> {

    /**
     * Returns the observed argument as the alternative to {@link #getObservedType()}.
     * @return The observed argument
     */
    Argument<T> getObservedArgument();

    /**
     * Returns the observed qualifier as the alternative to {@link #getObservedQualifiers()}.
     * @return The observed qualifier
     */
    @Nullable
    Qualifier<T> getObservedQualifier();

}
