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
package org.eclipse.odi.cdi;

import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.enterprise.inject.Instance;

/**
 * ODI specific {@link Instance} implementation.
 *
 * @param <T> The instance type
 */
@Internal
public interface OdiInstance<T> extends Instance<T> {

    @NonNull
    default <U extends T> Instance<U> select(@NonNull Argument<U> argument) {
        return select(argument, Qualifiers.forArgument(argument));
    }

    @NonNull
    <U extends T> Instance<U> select(@NonNull Argument<U> argument, @Nullable Qualifier<U> qualifier);

}
