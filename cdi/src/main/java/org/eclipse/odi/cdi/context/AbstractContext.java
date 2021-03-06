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
package org.eclipse.odi.cdi.context;

import org.eclipse.odi.cdi.OdiBean;
import io.micronaut.core.annotation.Internal;
import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.context.spi.AlterableContext;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple {@link AlterableContext} context.
 */
@Internal
public abstract class AbstractContext implements AlterableContext {

    private final Map<Contextual<?>, Entry> storage = new ConcurrentHashMap<>();
    private boolean active = true;

    @Override
    public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
        chechIfActive();
        contextual = unwrapProxy(contextual);
        T instance = get(contextual);
        if (instance == null) {
            instance = contextual.create(creationalContext);
            storage.put(contextual, new Entry<>(creationalContext, instance));
        }
        return instance;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Contextual<T> contextual) {
        chechIfActive();
        contextual = unwrapProxy(contextual);
        Entry<T> entry = storage.get(contextual);
        if (entry != null) {
            return entry.instance;
        }
        return null;
    }

    private <T> Contextual<T> unwrapProxy(Contextual<T> contextual) {
        if (contextual instanceof OdiBean) {
            OdiBean<T> bean = (OdiBean<T>) contextual;
            if (bean.isProxy()) {
                contextual = bean.getProxyTargetBean();
            }
        }
        return contextual;
    }

    private void chechIfActive() {
        if (!active) {
            throw new ContextNotActiveException("Context not active!");
        }
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void destroy(Contextual<?> contextual) {
        Entry entry = storage.remove(contextual);
        if (entry != null) {
            @SuppressWarnings("rawtypes")
            Contextual rawContextual = contextual;
            rawContextual.destroy(entry.instance, entry.creationalContext);
        }
    }

    /**
     * Destroy the context.
     */
    public void destroy() {
        storage.values().forEach(e -> e.creationalContext.release());
        storage.clear();
        active = false;
    }

    /**
     * Deactivate the context.
     */
    public void deactivate() {
        active = false;
    }

    /**
     * Activate the context.
     */
    public void activate() {
        active = true;
    }

    private static final class Entry<T> {
        private final CreationalContext<T> creationalContext;
        private final T instance;

        private Entry(CreationalContext<T> creationalContext, T instance) {
            this.creationalContext = creationalContext;
            this.instance = instance;
        }
    }
}
