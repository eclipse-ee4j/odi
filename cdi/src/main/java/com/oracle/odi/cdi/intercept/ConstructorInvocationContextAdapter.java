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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;

import io.micronaut.aop.ConstructorInvocationContext;
import io.micronaut.aop.InvocationContext;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.type.Argument;

/**
 * CDI constructor interceptor.
 *
 * According to the spec {@link #proceed()} should return `null` for the first call and after that {@link #getTarget()}
 * will return the actual instance.
 */
final class ConstructorInvocationContextAdapter extends InvocationContextAdapter {

    private Object constructorTarget;

    ConstructorInvocationContextAdapter(InvocationContext<?, ?> invocationContext) {
        super(invocationContext);
    }

    @Override
    public Object getTarget() {
        return constructorTarget;
    }

    @Override
    public Method getMethod() {
        return null;
    }

    @Override
    public Constructor<?> getConstructor() {
        ConstructorInvocationContext<?> cic = (ConstructorInvocationContext<?>) invocationContext;
        final BeanConstructor<?> constructor = cic.getConstructor();
        final Class<?>[] args = Arrays.stream(constructor.getArguments())
                .map(Argument::getType)
                .toArray(Class[]::new);
        try {
            return constructor.getDeclaringBeanType()
                    .getDeclaredConstructor(args);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Constructor advice missing reflection information for constructor: " + e
                    .getMessage(), e);
        }
    }

    @Override
    public Object proceed() {
        constructorTarget = invocationContext.proceed();
        return null;
    }

    public Object getConstructorTarget() {
        return constructorTarget;
    }
}
