/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package org.eclipse.odi.cdi;

import io.micronaut.aop.Intercepted;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.DefaultBeanResolutionContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.exception.InvocationException;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.AdvisedBeanType;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.AnyQualifier;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.BeanContainer;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.invoke.AsyncHandler;
import jakarta.inject.Singleton;
import org.eclipse.odi.cdi.context.DependentContext;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.ServiceLoader;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

@Internal
@Singleton
final class OdiExecutableInvokerExecutor implements OdiInvokerExecutor {
    private static final String REACTIVE_STREAMS_PUBLISHER = "org.reactivestreams.Publisher";

    @Override
    public Object invoke(OdiExecutableInvokerInfo invokerInfo, Object instance, Object[] arguments) throws Exception {
        OdiBeanContainer beanContainer = (OdiBeanContainer) CDI.current().getBeanContainer();
        boolean contextualInvocation = !invokerInfo.isStaticMethod()
                && (invokerInfo.isInstanceLookup() || instance instanceof Intercepted);
        BeanDefinition<Object> beanDefinition = findBeanDefinition(
                beanContainer.getBeanContext(),
                invokerInfo,
                contextualInvocation
        );
        ExecutableMethod<Object, Object> executableMethod = findExecutableMethod(beanDefinition, invokerInfo);
        try (BeanResolutionContext resolutionContext = new DefaultBeanResolutionContext(
                beanContainer.getBeanContext(),
                beanDefinition
        )) {
            DependentContext dependentContext = new DependentContext(resolutionContext);
            Completion completion = new Completion(dependentContext);
            try {
                Object target = null;
                if (!invokerInfo.isStaticMethod()) {
                    if (invokerInfo.isInstanceLookup()) {
                        target = resolveBean(beanContainer, beanDefinition.asArgument(), dependentContext);
                    } else {
                        target = instance;
                    }
                }
                Object[] invocationArguments = resolveArguments(beanContainer, executableMethod, invokerInfo, arguments, dependentContext);
                int asyncParameter = invokerInfo.getAsyncParameterIndex();
                AsyncHandler.ParameterType parameterTypeHandler = asyncParameter >= 0
                        ? loadHandler(
                                AsyncHandler.ParameterType.class,
                                invokerInfo.getAsyncParameterHandlerClassName(),
                                executableMethod.getArguments()[asyncParameter].getType().getClassLoader()
                        )
                        : null;
                if (asyncParameter >= 0) {
                    completion.manage();
                    invocationArguments[asyncParameter] = parameterTypeHandler.transformArgument(invocationArguments[asyncParameter], completion);
                }
                Object result = executableMethod.invoke(target, invocationArguments);
                Object transformed = transformResult(result, executableMethod.getReturnType().getType(), invokerInfo, completion, parameterTypeHandler);
                if (!completion.isManaged()) {
                    completion.complete();
                }
                completion.releaseIfCompleted();
                return transformed;
            } catch (InvocationException e) {
                completion.fail();
                throw unwrapInvocationException(e);
            } catch (Exception e) {
                completion.fail();
                throw e;
            } catch (Error e) {
                completion.fail();
                throw e;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private BeanDefinition<Object> findBeanDefinition(BeanContext beanContext,
                                                      OdiExecutableInvokerInfo invokerInfo,
                                                      boolean preferAdvised) {
        BeanDefinition<Object> fallback = null;
        for (BeanDefinition<Object> beanDefinition : beanContext.getAllBeanDefinitions()) {
            if (beanDefinition instanceof AdvisedBeanType) {
                AdvisedBeanType<?> advisedBeanType = (AdvisedBeanType<?>) beanDefinition;
                if (advisedBeanType.getInterceptedType().getName().equals(invokerInfo.getBeanClassName())) {
                    if (preferAdvised) {
                        return beanDefinition;
                    }
                    fallback = beanDefinition;
                }
            } else if (beanDefinition.getBeanType().getName().equals(invokerInfo.getBeanClassName())) {
                if (!preferAdvised) {
                    return beanDefinition;
                }
                fallback = beanDefinition;
            }
        }
        if (fallback != null) {
            return fallback;
        }
        throw new IllegalStateException("No bean definition found for invoker target: " + invokerInfo.getBeanClassName());
    }

    @SuppressWarnings("unchecked")
    private ExecutableMethod<Object, Object> findExecutableMethod(BeanDefinition<Object> beanDefinition,
                                                                  OdiExecutableInvokerInfo invokerInfo) {
        return (ExecutableMethod<Object, Object>) beanDefinition.getExecutableMethods()
                .stream()
                .filter(method -> method.getMethodName().equals(invokerInfo.getMethodName()))
                .filter(method -> matchesArguments(method, invokerInfo.getParameterTypeNames(), invokerInfo.getParameterArrayDimensions()))
                .filter(method -> method.getDeclaringType().getName().equals(invokerInfo.getMethodDeclaringClassName()))
                .findFirst()
                .or(() -> beanDefinition.getExecutableMethods()
                        .stream()
                        .filter(method -> method.getMethodName().equals(invokerInfo.getMethodName()))
                        .filter(method -> matchesArguments(method, invokerInfo.getParameterTypeNames(), invokerInfo.getParameterArrayDimensions()))
                        .findFirst())
                .orElseThrow(() -> new IllegalStateException("No executable method found for CDI invoker: "
                        + invokerInfo.getMethodDeclaringClassName() + "." + invokerInfo.getMethodName()));
    }

    private boolean matchesArguments(ExecutableMethod<?, ?> method, String[] parameterTypeNames, int[] parameterArrayDimensions) {
        Argument<?>[] arguments = method.getArguments();
        if (arguments.length != parameterTypeNames.length) {
            return false;
        }
        for (int i = 0; i < arguments.length; i++) {
            Class<?> argumentType = arguments[i].getType();
            if (arrayDimensions(argumentType) != parameterArrayDimensions[i]
                    || !baseTypeName(argumentType).equals(parameterTypeNames[i])) {
                return false;
            }
        }
        return true;
    }

    private int arrayDimensions(Class<?> type) {
        int dimensions = 0;
        Class<?> current = type;
        while (current.isArray()) {
            dimensions++;
            current = current.getComponentType();
        }
        return dimensions;
    }

    private String baseTypeName(Class<?> type) {
        Class<?> current = type;
        while (current.isArray()) {
            current = current.getComponentType();
        }
        return current.getName();
    }

    private Object[] resolveArguments(OdiBeanContainer beanContainer,
                                      ExecutableMethod<Object, Object> executableMethod,
                                      OdiExecutableInvokerInfo invokerInfo,
                                      Object[] suppliedArguments,
                                      DependentContext dependentContext) {
        Argument<?>[] methodArguments = executableMethod.getArguments();
        if (methodArguments.length == 0) {
            return new Object[0];
        }
        if (suppliedArguments == null || suppliedArguments.length < methodArguments.length) {
            throw new IllegalArgumentException("Missing invoker arguments");
        }
        Object[] invocationArguments = new Object[methodArguments.length];
        for (int i = 0; i < methodArguments.length; i++) {
            if (invokerInfo.isArgumentLookup(i)) {
                invocationArguments[i] = resolveArgument(beanContainer, methodArguments[i], dependentContext);
            } else {
                invocationArguments[i] = coerceArgument(methodArguments[i], suppliedArguments[i]);
            }
        }
        return invocationArguments;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object resolveArgument(OdiBeanContainer beanContainer,
                                   Argument<?> argument,
                                   DependentContext dependentContext) {
        Class<?> argumentType = argument.getType();
        if (argumentType == BeanContainer.class) {
            return beanContainer;
        }
        if (argumentType == BeanManager.class) {
            return beanContainer.createInstance(dependentContext).select(Argument.of(BeanManager.class)).get();
        }
        if (argumentType == Event.class) {
            Argument<?> typeArgument = argument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
            if (typeArgument.getType() == Object.class) {
                return beanContainer.getEvent();
            }
            Qualifier<?> eventQualifier = Qualifiers.forArgument(argument);
            if (AnyQualifier.INSTANCE.equals(eventQualifier)) {
                eventQualifier = DefaultQualifier.instance();
            }
            return beanContainer.getEvent((Argument) typeArgument, argument.getAnnotationMetadata(), eventQualifier);
        }
        if (argumentType == Instance.class) {
            Argument<?> typeArgument = argument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
            return beanContainer.createInstance(dependentContext).select((Argument) typeArgument);
        }
        return resolveBean(beanContainer, argument, dependentContext);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object resolveBean(OdiBeanContainer beanContainer,
                               Argument<?> argument,
                               DependentContext dependentContext) {
        Qualifier qualifier = Qualifiers.forArgument(argument);
        OdiBean<?> bean = beanContainer.getBean((Argument) argument, qualifier);
        CreationalContext creationalContext = beanContainer.createCreationalContext(bean);
        Context context = beanContainer.getOdiAnnotations().isDependent(bean.getScope())
                ? dependentContext
                : beanContainer.getContext(bean.getScope());
        return context.get(bean, creationalContext);
    }

    private Object coerceArgument(Argument<?> argument, Object value) {
        Class<?> argumentType = argument.getType();
        if (!argumentType.isPrimitive() || value == null) {
            return value;
        }
        if (argumentType == boolean.class || argumentType == char.class) {
            return value;
        }
        if (!(value instanceof Number)) {
            return value;
        }
        Number number = (Number) value;
        if (argumentType == byte.class) {
            return value instanceof Byte ? value : number;
        }
        if (argumentType == short.class) {
            return value instanceof Byte || value instanceof Short ? number.shortValue() : value;
        }
        if (argumentType == int.class) {
            return value instanceof Byte || value instanceof Short || value instanceof Integer ? number.intValue() : value;
        }
        if (argumentType == long.class) {
            return value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long ? number.longValue() : value;
        }
        if (argumentType == float.class) {
            return value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long || value instanceof Float
                    ? number.floatValue()
                    : value;
        }
        if (argumentType == double.class) {
            return value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long || value instanceof Float || value instanceof Double
                    ? number.doubleValue()
                    : value;
        }
        return value;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object transformResult(Object result,
                                   Class<?> returnType,
                                   OdiExecutableInvokerInfo invokerInfo,
                                   Completion completion,
                                   AsyncHandler.ParameterType parameterTypeHandler) {
        if (result instanceof CompletionStage) {
            completion.manage();
            ((CompletionStage<?>) result).whenComplete((value, error) -> completion.complete());
            return result;
        }
        if (result instanceof Flow.Publisher) {
            completion.manage();
            return destroyOnPublisherCompletion((Flow.Publisher<?>) result, completion);
        }
        if (result != null
                && ClassUtils.isPresent(REACTIVE_STREAMS_PUBLISHER, result.getClass().getClassLoader())
                && OdiReactiveStreamsSupport.isPublisher(result)) {
            completion.manage();
            return OdiReactiveStreamsSupport.destroyOnCompletion(result, completion);
        }
        if (invokerInfo.getAsyncReturnHandlerClassName() != null) {
            AsyncHandler.ReturnType returnTypeHandler = loadHandler(
                    AsyncHandler.ReturnType.class,
                    invokerInfo.getAsyncReturnHandlerClassName(),
                    returnType.getClassLoader()
            );
            completion.manage();
            return returnTypeHandler.transform(result, completion);
        }
        if (parameterTypeHandler != null) {
            return parameterTypeHandler.transformReturnValue(result, completion);
        }
        return result;
    }

    private Flow.Publisher<?> destroyOnPublisherCompletion(Flow.Publisher<?> publisher, Completion completion) {
        return destroyOnPublisherCompletionTyped(publisher, completion);
    }

    private <T> Flow.Publisher<T> destroyOnPublisherCompletionTyped(Flow.Publisher<T> publisher, Completion completion) {
        return subscriber -> {
            try {
                publisher.subscribe(new Flow.Subscriber<T>() {
                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        subscriber.onSubscribe(new Flow.Subscription() {
                            @Override
                            public void request(long n) {
                                subscription.request(n);
                            }

                            @Override
                            public void cancel() {
                                try {
                                    subscription.cancel();
                                } finally {
                                    completion.complete();
                                }
                            }
                        });
                    }

                    @Override
                    public void onNext(T item) {
                        subscriber.onNext(item);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        try {
                            subscriber.onError(throwable);
                        } finally {
                            completion.complete();
                        }
                    }

                    @Override
                    public void onComplete() {
                        try {
                            subscriber.onComplete();
                        } finally {
                            completion.complete();
                        }
                    }
                });
            } catch (Throwable e) {
                completion.fail();
                throw e;
            }
        };
    }

    private <T> T loadHandler(Class<T> handlerType, String handlerClassName, ClassLoader classLoader) {
        if (handlerClassName == null) {
            throw new IllegalStateException("No async handler configured for " + handlerType.getName());
        }
        for (T handler : ServiceLoader.load(handlerType, classLoader)) {
            if (handler.getClass().getName().equals(handlerClassName)) {
                return handler;
            }
        }
        throw new IllegalStateException("Async handler " + handlerClassName + " is not available for " + handlerType.getName());
    }

    private Exception unwrapInvocationException(Exception e) throws Exception {
        Throwable cause = e.getCause();
        while (cause instanceof InvocationException || cause instanceof InvocationTargetException) {
            cause = cause.getCause();
        }
        if (cause instanceof Exception exception) {
            return exception;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        return e;
    }

    static final class Completion implements Runnable {
        private final DependentContext dependentContext;
        private final AtomicBoolean destroyed = new AtomicBoolean();

        private boolean managed;
        private boolean completed;
        private boolean failed;
        private boolean releaseAllowed;

        private Completion(DependentContext dependentContext) {
            this.dependentContext = dependentContext;
        }

        @Override
        public void run() {
            complete();
        }

        private synchronized void manage() {
            managed = true;
        }

        private synchronized boolean isManaged() {
            return managed;
        }

        synchronized void complete() {
            if (!failed) {
                completed = true;
                if (releaseAllowed) {
                    destroy();
                }
            }
        }

        private synchronized void releaseIfCompleted() {
            releaseAllowed = true;
            if (completed) {
                destroy();
            }
        }

        synchronized void fail() {
            failed = true;
            destroy();
        }

        private void destroy() {
            if (destroyed.compareAndSet(false, true)) {
                dependentContext.destroy();
            }
        }
    }

}
