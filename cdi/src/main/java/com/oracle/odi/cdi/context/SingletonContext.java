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
package com.oracle.odi.cdi.context;

import io.micronaut.core.annotation.Internal;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.inject.Singleton;

import java.lang.annotation.Annotation;

/**
 * Simple no-op Micronaut context.
 */
@Internal
public class SingletonContext implements Context {

    public static final Context INSTANCE = new SingletonContext();

    @Override
    public Class<? extends Annotation> getScope() {
        return Singleton.class;
    }

    @Override
    public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
        return contextual.create(creationalContext);
    }

    @Override
    public <T> T get(Contextual<T> contextual) {
        throw new IllegalStateException();
    }

    @Override
    public boolean isActive() {
        return true;
    }
}
