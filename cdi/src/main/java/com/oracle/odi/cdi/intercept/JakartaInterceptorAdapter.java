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
public final class JakartaInterceptorAdapter<B> extends OdiBeanImpl<B> implements ConstructorInterceptor<Object>,
        MethodInterceptor<Object, Object>,
        Interceptor<B> {

    private final BeanDefinition<B> beanDefinition;
    private final BeanContext beanContext;
    private final int priority;
    private ExecutableMethod<B, Object> aroundConstruct;
    private ExecutableMethod<B, Object> aroundInvoke;
    private ExecutableMethod<B, Object> preDestroy;
    private ExecutableMethod<B, Object> postConstruct;
    private Set<Annotation> interceptorBindings;

    /**
     * Default constructor.
     *
     * @param beanDefinition The bean definition
     * @param beanContext The bean context
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
    public void setAroundConstruct(String aroundConstructMethod) {
        this.aroundConstruct = beanDefinition.getRequiredMethod(
                aroundConstructMethod,
                jakarta.interceptor.InvocationContext.class
        );
    }

    /**
     * Sets the name of the method that defines {@link jakarta.interceptor.AroundInvoke}.
     *
     * @param aroundInvokeMethod The name of the method.
     */
    public void setAroundInvoke(String aroundInvokeMethod) {
        this.aroundInvoke = beanDefinition.getRequiredMethod(
                aroundInvokeMethod,
                jakarta.interceptor.InvocationContext.class
        );
    }

    /**
     * Sets the name of the method that defines {@link jakarta.annotation.PreDestroy} interception.
     *
     * @param preDestroyMethod The name of the method.
     */
    public void setPreDestroy(String preDestroyMethod) {
        this.preDestroy = beanDefinition.getRequiredMethod(
                preDestroyMethod,
                jakarta.interceptor.InvocationContext.class
        );
    }

    /**
     * Sets the name of the method that defines {@link jakarta.annotation.PostConstruct} interception.
     *
     * @param postConstructMethod The name of the method
     */
    public void setPostConstruct(String postConstructMethod) {
        this.postConstruct = beanDefinition.getRequiredMethod(
                postConstructMethod,
                jakarta.interceptor.InvocationContext.class
        );
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
            ConstructorInvocationContextAdapter constructorInvocationContextAdapter =
                    new ConstructorInvocationContextAdapter(context);
            aroundConstruct.invoke(
                    resolveTarget(),
                    constructorInvocationContextAdapter
            );
            return constructorInvocationContextAdapter.getConstructorTarget();
        } else {
            return context.proceed();
        }
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        ExecutableMethod<B, Object> executableMethod = selectMethod(context.getKind());
        if (executableMethod == null) {
            return context.proceed();
        }
        B target = resolveTarget();
        InvocationContextAdapter ctx = new InvocationContextAdapter(context);
        if (executableMethod.getReturnType().isVoid()) {
            executableMethod.invoke(target, ctx);
            return target;
        }
        return executableMethod.invoke(target, ctx);
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
        ExecutableMethod<B, Object> executableMethod = selectMethod(type);
        if (executableMethod != null) {
            return executableMethod.invoke(instance, ctx);
        }
        throw new IllegalStateException("Not supported intercept type: " + type);
    }

    @Nullable
    private ExecutableMethod<B, Object> selectMethod(@NonNull InterceptionType type) {
        try {
            final InterceptorKind kind = InterceptorKind.valueOf(type.name());
            return selectMethod(kind);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Nullable
    private ExecutableMethod<B, Object> selectMethod(@NonNull InterceptorKind type) {
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

    private B resolveTarget() {
        return beanContext.getBean(beanDefinition);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

}
