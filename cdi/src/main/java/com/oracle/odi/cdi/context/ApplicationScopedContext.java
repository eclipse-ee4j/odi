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
package com.oracle.odi.cdi.context;

import io.micronaut.core.annotation.Internal;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;

import java.lang.annotation.Annotation;

/**
 * The implementation of {@link ApplicationScoped} context.
 */
@Internal
@Singleton
final class ApplicationScopedContext extends AbstractContext {

    @Override
    public Class<? extends Annotation> getScope() {
        return ApplicationScoped.class;
    }
}
