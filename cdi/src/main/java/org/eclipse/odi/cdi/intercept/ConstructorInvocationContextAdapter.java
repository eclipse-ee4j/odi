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
package org.eclipse.odi.cdi.intercept;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;

import io.micronaut.aop.ConstructorInvocationContext;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.InvocationContext;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.AdvisedBeanType;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ConstructorInjectionPoint;
import io.micronaut.inject.ExecutableMethod;

/**
 * CDI constructor interceptor.
 *
 * According to the spec {@link #proceed()} should return `null` for the first call and after that {@link #getTarget()}
 * will return the actual instance.
 * @param <B> the bean type
 */
final class ConstructorInvocationContextAdapter<B> extends InvocationContextAdapter<B> {

    private Object constructorTarget;

    ConstructorInvocationContextAdapter(
            InvocationContext<?, ?> invocationContext,
            ExecutableMethod<B, Object>[] methods) {
        super(invocationContext, methods, InterceptorKind.AROUND_CONSTRUCT);
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
        Class<?> declaringBeanType = constructor.getDeclaringBeanType();
        Class<?>[] args = Arrays.stream(constructor.getArguments())
                .map(Argument::getType)
                .toArray(Class[]::new);
        if (constructor instanceof ConstructorInjectionPoint) {
            final BeanDefinition<?> declaringBean = ((ConstructorInjectionPoint<?>) constructor).getDeclaringBean();
            if (declaringBean instanceof AdvisedBeanType) {
                declaringBeanType = ((AdvisedBeanType<?>) declaringBean).getInterceptedType();
                args = Arrays.copyOfRange(args, 0, args.length - 4);
            }
        }

        try {
            return declaringBeanType
                    .getDeclaredConstructor(args);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Constructor advice missing reflection information for constructor: " + e
                    .getMessage(), e);
        }
    }

    @Override
    public Object proceed() {
        super.proceed();
        return null;
    }

    @Override
    protected Object invocationContextProceed() {
        constructorTarget = super.invocationContextProceed();
        return constructorTarget;
    }

    public Object getConstructorTarget() {
        return constructorTarget;
    }

    @Override
    public Object invoke(B interceptor) {
        super.invoke(interceptor);
        return getConstructorTarget();
    }
}
