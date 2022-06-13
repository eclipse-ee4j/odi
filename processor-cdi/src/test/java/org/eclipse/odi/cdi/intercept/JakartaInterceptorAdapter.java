/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import io.micronaut.aop.ConstructorInterceptor;
import io.micronaut.aop.ConstructorInvocationContext;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.InvocationContext;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.ExecutableMethod;

// Bit of a hack that this has to be copied in from the other project, but couldn't figure
// out how to set the build up correctly for Maven
@Internal
public final class JakartaInterceptorAdapter<B>
        implements ConstructorInterceptor<Object>,
                   MethodInterceptor<Object, Object> {

    private final BeanRegistration<B> beanRegistration;
    private final B targetBean;
    private ExecutableMethod<B, Object> aroundConstruct;
    private ExecutableMethod<B, Object> aroundInvoke;
    private ExecutableMethod<B, Object> preDestroy;
    private ExecutableMethod<B, Object> postConstruct;

    /**
     * Default constructor.
     * @param beanRegistration The bean registration
     */
    public JakartaInterceptorAdapter(BeanRegistration<B> beanRegistration) {
        this.beanRegistration = beanRegistration;
        this.targetBean = beanRegistration.getBean();
    }

    @Override
    public int getOrder() {
        return beanRegistration.getOrder();
    }

    /**
     * Sets the name of the method that defines {@link jakarta.interceptor.AroundConstruct}.
     * @param aroundConstructMethod The name of the method.
     */
    public void setAroundConstruct(String aroundConstructMethod) {
        this.aroundConstruct = locateMethod(aroundConstructMethod);
    }

    /**
     * Sets the name of the method that defines {@link jakarta.interceptor.AroundInvoke}.
     * @param aroundInvokeMethod The name of the method.
     */
    public void setAroundInvoke(String aroundInvokeMethod) {
        this.aroundInvoke = locateMethod(aroundInvokeMethod);
    }

    /**
     * Sets the name of the method that defines {@link jakarta.annotation.PreDestroy} interception.
     * @param preDestroyMethod The name of the method.
     */
    public void setPreDestroy(String preDestroyMethod) {
        this.preDestroy = locateMethod(preDestroyMethod);
    }

    /**
     * Sets the name of the method that defines {@link jakarta.annotation.PostConstruct} interception.
     * @param postConstructMethod The name of the method
     */
    public void setPostConstruct(String postConstructMethod) {
        this.postConstruct = locateMethod(postConstructMethod);
    }

    private ExecutableMethod<B, Object> locateMethod(String aroundInvokeMethod) {
        return beanRegistration.getBeanDefinition()
                .findMethod(
                        aroundInvokeMethod,
                        javax.interceptor.InvocationContext.class
                ).orElseGet(() ->
                                    beanRegistration.getBeanDefinition().getRequiredMethod(
                                            aroundInvokeMethod,
                                            jakarta.interceptor.InvocationContext.class
                                    )
                );
    }

    @Override
    public Object intercept(InvocationContext<Object, Object> context) {
        if (context instanceof ConstructorInvocationContext) {
            return intercept((ConstructorInvocationContext<Object>) context);
        } else {
            return intercept((MethodInvocationContext<Object, Object>) context);
        }
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public Object intercept(ConstructorInvocationContext<Object> context) {
        if (aroundConstruct != null) {
            final Object bean = aroundConstruct.invoke(
                    targetBean,
                    new InvocationContextAdapter(context)
            );
            if (bean == null) {
                throw new IllegalStateException("Constructor Interceptor should never return null. Method returned null: "
                                                        + aroundConstruct.getDescription(true)
                );
            } else {
                return bean;
            }
        } else {
            return context.proceed();
        }
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        final InterceptorKind kind = context.getKind();
        switch (kind) {
        case AROUND:
            if (aroundInvoke != null) {
                return aroundInvoke.invoke(
                        targetBean,
                        new InvocationContextAdapter(context)
                );
            } else {
                return context.proceed();
            }
        case PRE_DESTROY:
            if (preDestroy != null) {
                return preDestroy.invoke(
                        targetBean,
                        new InvocationContextAdapter(context)
                );
            } else {
                return context.proceed();
            }
        case POST_CONSTRUCT:
            if (postConstruct != null) {
                return postConstruct.invoke(
                        targetBean,
                        new InvocationContextAdapter(context)
                );
            } else {
                return context.proceed();
            }
        default:
            return context.proceed();
        }
    }

    final class InvocationContextAdapter implements javax.interceptor.InvocationContext {
        private final InvocationContext<?, ?> invocationContext;

        InvocationContextAdapter(InvocationContext<?, ?> invocationContext) {
            this.invocationContext = invocationContext;
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
            if (invocationContext instanceof MethodInvocationContext) {
                return ((MethodInvocationContext<?, ?>) invocationContext).getTargetMethod();
            }
            return null;
        }

        @Override
        public Constructor<?> getConstructor() {
            if (invocationContext instanceof ConstructorInvocationContext) {
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
            return invocationContext.proceed();
        }

        private class ContextDataMap extends AbstractMap<String, Object> {

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

}
