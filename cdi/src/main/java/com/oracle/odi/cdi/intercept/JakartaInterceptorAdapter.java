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

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.oracle.odi.cdi.OdiBeanImpl;
import io.micronaut.aop.ConstructorInterceptor;
import io.micronaut.aop.ConstructorInvocationContext;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.InvocationContext;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.spi.InterceptionType;
import jakarta.enterprise.inject.spi.Interceptor;
import jakarta.interceptor.InterceptorBinding;

/**
 * Adapter type for delegating interception to a Jakarta Interceptor implementation.
 *
 * @param <B> The bean type. The target bean type
 */
@Internal
public final class JakartaInterceptorAdapter<B> extends OdiBeanImpl<B> implements
        ConstructorInterceptor<Object>,
        MethodInterceptor<Object, Object>,
        Interceptor<B> {

    private final BeanDefinition<B> beanDefinition;
    private final BeanContext beanContext;
    private final int priority;
    private ExecutableMethod<B, Object>[] aroundConstruct;
    private ExecutableMethod<B, Object>[] aroundInvoke;
    private ExecutableMethod<B, Object>[] preDestroy;
    private ExecutableMethod<B, Object>[] postConstruct;
    private Set<Annotation> interceptorBindings;

    /**
     * Default constructor.
     *
     * @param beanDefinition The bean definition
     * @param beanContext    The bean context
     */
    public JakartaInterceptorAdapter(
            BeanDefinition<B> beanDefinition,
            BeanContext beanContext) {
        super(beanContext, beanDefinition);
        this.beanContext = beanContext;
        this.beanDefinition = beanContext.getBeanDefinition(beanDefinition.asArgument());
        this.priority = this.beanDefinition.intValue(Priority.class).orElse(0);
    }

    /**
     * Sets the name of the method that defines {@link jakarta.interceptor.AroundConstruct}.
     *
     * @param aroundConstructMethod The name of the method.
     */
    @SuppressWarnings("unchecked")
    public void setAroundConstruct(List<String> aroundConstructMethod) {
        this.aroundConstruct = toMethodArray(aroundConstructMethod);
    }

    /**
     * Sets the name of the method that defines {@link jakarta.interceptor.AroundInvoke}.
     *
     * @param aroundInvokeMethod The name of the method.
     */
    public void setAroundInvoke(List<String> aroundInvokeMethod) {
        this.aroundInvoke = toMethodArray(aroundInvokeMethod);
    }

    /**
     * Sets the name of the method that defines {@link jakarta.annotation.PreDestroy} interception.
     *
     * @param preDestroyMethod The name of the method.
     */
    public void setPreDestroy(List<String> preDestroyMethod) {
        this.preDestroy = toMethodArray(preDestroyMethod);
    }

    /**
     * Sets the name of the method that defines {@link jakarta.annotation.PostConstruct} interception.
     *
     * @param postConstructMethod The name of the method
     */
    public void setPostConstruct(List<String> postConstructMethod) {
        this.postConstruct = toMethodArray(postConstructMethod);
    }

    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        if (context instanceof ConstructorInvocationContext) {
            return intercept((ConstructorInvocationContext<Object>) context);
        } else if (context instanceof MethodInvocationContext) {
            Object result = intercept((MethodInvocationContext<Object, Object>) context);
            if (result == null) {
                throw new IllegalStateException("Result cannot be null");
            }
        }
        throw new IllegalStateException("Unknown context type: " + context);
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public Object intercept(ConstructorInvocationContext<Object> context) {
        if (aroundConstruct != null) {
            ConstructorInvocationContextAdapter<B> constructorInvocationContextAdapter =
                    new ConstructorInvocationContextAdapter<>(context, aroundConstruct);
            return constructorInvocationContextAdapter.invoke(
                    resolveInterceptorBean()
            );
        } else {
            return context.proceed();
        }
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        final ExecutableMethod<B, Object>[] executableMethods = selectMethod(context.getKind());

        if (executableMethods == null) {
            return context.proceed();
        }
        ExecutableMethod<B, Object> executableMethod = executableMethods[0];
        B target = resolveInterceptorBean();
        InvocationContextAdapter<B> ctx = new InvocationContextAdapter<>(
                context,
                executableMethods,
                context.getKind()
        );

        if (executableMethod.getReturnType().isVoid()) {
            ctx.invoke(target);
            return target;
        }
        return ctx.invoke(target);
    }

    @Override
    public int getOrder() {
        return priority;
    }

    @Override
    public Set<Annotation> getInterceptorBindings() {
        if (interceptorBindings == null) {
            AnnotationMetadata annotationMetadata = beanDefinition.getAnnotationMetadata();
            interceptorBindings = annotationMetadata.getAnnotationTypesByStereotype(InterceptorBinding.class)
                    .stream().map(annotationMetadata::synthesize)
                    .collect(Collectors.toSet());
        }
        return interceptorBindings;
    }

    @Override
    public boolean intercepts(InterceptionType type) {
        return selectMethod(type) != null;
    }

    @Override
    public Object intercept(InterceptionType type, B instance, jakarta.interceptor.InvocationContext ctx) {
        final ExecutableMethod<B, Object>[] executableMethods = selectMethod(type);
        if (executableMethods != null) {
            return executableMethods[0].invoke(instance, ctx);
        }
        throw new IllegalStateException("Not supported intercept type: " + type);
    }

    @Nullable
    private ExecutableMethod<B, Object>[] selectMethod(@NonNull InterceptionType type) {
        try {
            if (type == InterceptionType.AROUND_INVOKE) {
                return selectMethod(InterceptorKind.AROUND);
            } else {
                final InterceptorKind kind = InterceptorKind.valueOf(type.name());
                return selectMethod(kind);
            }
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Nullable
    private ExecutableMethod<B, Object>[] selectMethod(@NonNull InterceptorKind type) {
        switch (type) {
            case AROUND_CONSTRUCT:
                return aroundConstruct;
            case PRE_DESTROY:
                return preDestroy;
            case AROUND:
                return aroundInvoke;
            case POST_CONSTRUCT:
                return postConstruct;
            default:
                return null;
        }
    }

    private B resolveInterceptorBean() {
        return beanContext.getBean(beanDefinition);
    }

    @SuppressWarnings("rawtypes")
    private ExecutableMethod[] toMethodArray(List<String> methods) {
        return methods.stream().map(n -> beanDefinition.getRequiredMethod(
                n,
                jakarta.interceptor.InvocationContext.class
        )).toArray(ExecutableMethod[]::new);
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public String toString() {
        return beanDefinition.toString();
    }
}
