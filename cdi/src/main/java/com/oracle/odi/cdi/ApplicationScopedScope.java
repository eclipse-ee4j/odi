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

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.micronaut.core.annotation.Internal;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.spi.AlterableContext;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.inject.Singleton;

/**
 * The implementation of {@link ApplicationScoped} scope context.
 */
@Internal
@Singleton
final class ApplicationScopedScope implements AlterableContext {
    private final Map<Contextual<?>, Instance> instances = new ConcurrentHashMap<>();

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void destroy(Contextual<?> contextual) {
        final Instance o = instances.remove(contextual);
        if (o != null) {
            ((Contextual) contextual).destroy(
                    o.bean,
                    o.creationalContext
            );
            o.creationalContext.release();
        }
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return ApplicationScoped.class;
    }

    @Override
    public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
        final Instance instance = instances.get(contextual);
        if (instance != null) {
            //noinspection unchecked
            return (T) instance.bean;
        } else {
            final T bean = contextual.create(creationalContext);
            instances.put(contextual, new Instance(creationalContext, bean));
            return bean;
        }
    }

    @Override
    public <T> T get(Contextual<T> contextual) {
        final Instance instance = instances.get(contextual);
        if (instance != null) {
            //noinspection unchecked
            return (T) instance.bean;
        }
        return null;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    private static class Instance {
        @SuppressWarnings("checkstyle:VisibilityModifier")
        final CreationalContext<?> creationalContext;
        @SuppressWarnings("checkstyle:VisibilityModifier")
        final Object bean;

        Instance(CreationalContext<?> creationalContext, Object bean) {
            this.creationalContext = creationalContext;
            this.bean = bean;
        }
    }
}
