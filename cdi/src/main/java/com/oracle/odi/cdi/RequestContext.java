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

import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.spi.AlterableContext;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.inject.Singleton;

import java.lang.annotation.Annotation;
import java.util.AbstractMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple {@link RequestScoped} Micronaut context.
 */
@Singleton
public class RequestContext implements AlterableContext {

    private final Map<Contextual<?>, Map.Entry<CreationalContext<?>, Object>> storage = new ConcurrentHashMap<>();

    @Override
    public Class<? extends Annotation> getScope() {
        return RequestScoped.class;
    }

    @Override
    public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
        T instance = get(contextual);
        if (instance == null) {
            instance = contextual.create(creationalContext);
            storage.put(contextual, new AbstractMap.SimpleEntry<>(creationalContext, instance));
        }
        return instance;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Contextual<T> contextual) {
        Map.Entry<CreationalContext<?>, Object> entry = storage.get(contextual);
        if (entry != null) {
            return (T) entry.getValue();
        }
        return null;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void destroy(Contextual<?> contextual) {
        Map.Entry<CreationalContext<?>, Object> entry = storage.remove(contextual);
        @SuppressWarnings("rawtypes")
        Contextual rawContextual = contextual;
        rawContextual.destroy(entry.getValue(), entry.getKey());
    }
}
