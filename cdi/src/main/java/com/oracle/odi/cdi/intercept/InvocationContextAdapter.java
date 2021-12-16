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
package com.oracle.odi.cdi.intercept;

import io.micronaut.aop.InvocationContext;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.ExecutableMethod;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Adapter for the invocation context.
 * @param <B> The bean type
 */
class InvocationContextAdapter<B> implements jakarta.interceptor.InvocationContext {
    @SuppressWarnings("checkstyle:VisibilityModifier")
    final InvocationContext<?, ?> invocationContext;
    private final ExecutableMethod<B, Object>[] methods;
    private int index;
    private B interceptor;

    InvocationContextAdapter(InvocationContext<?, ?> invocationContext,
                             ExecutableMethod<B, Object>[] methods) {
        this.invocationContext = invocationContext;
        this.methods = methods;
        this.index = methods.length;
    }

    /**
     * Invoke the adapter with the given interceptor.
     * @param interceptor the interceptor
     * @return The result
     */
    public Object invoke(@NonNull B interceptor) {
        this.interceptor = interceptor;
        return methods[--index].invoke(interceptor, this);
    }

    @Override
    public Object getTarget() {
        return invocationContext.getTarget();
    }

    @Override
    public Object getTimer() {
        return null;
    }

    @Override
    public Method getMethod() {
        return ((MethodInvocationContext<?, ?>) invocationContext).getTargetMethod();
    }

    @Override
    public Constructor<?> getConstructor() {
        return null;
    }

    @Override
    public Object[] getParameters() {
        return invocationContext.getParameterValues();
    }

    @Override
    public void setParameters(Object[] objects) {
        if (objects == null || invocationContext.getArguments().length != objects.length) {
            throw new IllegalArgumentException(
                    "Parameter must be an array equal in length to the number of parameters to the method");
        }

        System.arraycopy(
                objects,
                0,
                invocationContext.getParameterValues(),
                0,
                objects.length
        );
    }

    @Override
    public Map<String, Object> getContextData() {
        final MutableConvertibleValues<Object> attributes = invocationContext.getAttributes();
        return new ContextDataMap(attributes);
    }

    @Override
    public Object proceed() {
        if (index <= 0) {
            return invocationContextProceed();
        } else {
            if (interceptor == null) {
                throw new IllegalStateException();
            } else {
                return methods[--index].invoke(interceptor, this);
            }
        }
    }

    protected Object invocationContextProceed() {
        return invocationContext.proceed();
    }

    private static final class ContextDataMap extends AbstractMap<String, Object> {

        private final MutableConvertibleValues<Object> attributes;

        private ContextDataMap(MutableConvertibleValues<Object> attributes) {
            this.attributes = attributes;
        }

        @Override
        public boolean isEmpty() {
            return attributes.isEmpty();
        }

        @Override
        public boolean containsKey(Object key) {
            return key != null && attributes.contains(key.toString());
        }

        @Override
        public boolean containsValue(Object value) {
            return attributes.values().contains(value);
        }

        @Override
        public Object get(Object key) {
            if (key != null) {
                return attributes.getValue(key.toString());
            }
            return null;
        }

        @Override
        public Object put(String key, Object value) {
            if (key != null) {
                final Object old = attributes.getValue(key);
                attributes.put(key, value);
                return old;
            }
            return null;
        }

        @Override
        public Object remove(Object key) {
            if (key != null) {
                final String k = key.toString();
                final Object old = attributes.getValue(k);
                attributes.remove(k);
                return old;
            }
            return null;
        }

        @Override
        public void putAll(Map<? extends String, ?> m) {
            if (CollectionUtils.isNotEmpty(m)) {
                m.forEach(attributes::put);
            }
        }

        @Override
        public void clear() {
            attributes.clear();
        }

        @Override
        public Set<String> keySet() {
            return attributes.names();
        }

        @Override
        public Collection<Object> values() {
            return attributes.values();
        }

        @Override
        public Set<Entry<String, Object>> entrySet() {
            return attributes.asMap().entrySet();
        }
    }
}
