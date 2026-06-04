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
package org.eclipse.odi.cdi;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.DependentBeanProvider;
import io.micronaut.context.scope.CreatedBean;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.proxy.InterceptedMethodProvider;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import org.eclipse.odi.cdi.context.DependentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of {@link CreationalContext}.
 * @param <T> The bean type
 */
@Internal
public final class OdiCreationalContext<T> implements CreationalContext<T> {
    private static final Logger LOG = LoggerFactory.getLogger(OdiCreationalContext.class);

    private final BeanContext beanContext;
    private final Contextual<T> contextual;
    private CreatedBean<T> createdBean;
    private T instance;
    private List<DependentContext> dependentContexts;

    OdiCreationalContext(BeanContext beanContext, Contextual<T> contextual) {
        this.beanContext = beanContext;
        this.contextual = contextual;
    }

    @Override
    public void push(T incompleteInstance) {
        instance = incompleteInstance;
    }

    @Override
    public void release() {
        try {
            if (contextual instanceof OdiBean) {
                if (createdBean instanceof BeanRegistration) {
                    BeanRegistration<T> beanRegistration = (BeanRegistration<T>) createdBean;
                    List<BeanRegistration<?>> dependentBeans = dependentBeans(beanRegistration);
                    closeAutoCloseBean(beanRegistration);
                    beanContext.destroyBean(beanRegistration);
                    closeAutoCloseDependents(dependentBeans);
                } else if (createdBean != null) {
                    createdBean.close();
                    this.createdBean = null;
                } else if (instance != null) {
                    beanContext.destroyBean(instance);
                    instance = null;
                }
            } else {
                contextual.destroy(instance, this);
                instance = null;
            }
        } finally {
            if (dependentContexts != null) {
                dependentContexts.forEach(DependentContext::destroy);
                dependentContexts.clear();
            }
        }
    }

    private void closeAutoCloseBean(BeanRegistration<T> beanRegistration) {
        if (!(contextual instanceof OdiBean<?>) || !isAutoClose(beanRegistration.getBeanDefinition())) {
            return;
        }
        closeAutoCloseRegistration(beanRegistration);
    }

    private List<BeanRegistration<?>> dependentBeans(BeanRegistration<?> beanRegistration) {
        return beanRegistration instanceof DependentBeanProvider dependentBeanProvider
                ? dependentBeanProvider.dependentBeans()
                : List.of();
    }

    private void closeAutoCloseDependents(List<BeanRegistration<?>> dependentBeans) {
        for (int i = dependentBeans.size() - 1; i >= 0; i--) {
            BeanRegistration<?> dependentBean = dependentBeans.get(i);
            if (isAutoClose(dependentBean.getBeanDefinition())) {
                closeAutoCloseRegistration(dependentBean);
            }
            closeAutoCloseDependents(dependentBeans(dependentBean));
        }
    }

    private <B> void closeAutoCloseRegistration(BeanRegistration<B> beanRegistration) {
        B bean = beanRegistration.getBean();
        if (isAutoCloseHandledByPreDestroy(beanRegistration.getBeanDefinition(), bean)) {
            return;
        }
        findAutoCloseMethod(beanRegistration.getBeanDefinition(), bean)
                .ifPresent(closeMethod -> invokeClose(closeMethod, bean));
    }

    private boolean isAutoClose(BeanDefinition<?> beanDefinition) {
        return beanDefinition.hasAnnotation(jakarta.enterprise.context.AutoClose.class)
                || beanDefinition.hasStereotype(jakarta.enterprise.context.AutoClose.class);
    }

    private <B> boolean isAutoCloseHandledByPreDestroy(BeanDefinition<B> beanDefinition, B bean) {
        if (beanDefinition.hasAnnotation(jakarta.enterprise.inject.Produces.class)) {
            return true;
        }
        return findAutoCloseMethod(beanDefinition, bean)
                .map(method -> method.hasAnnotation(jakarta.annotation.PreDestroy.class))
                .orElse(false);
    }

    private <B> Optional<ExecutableMethod<B, ?>> findAutoCloseMethod(BeanDefinition<B> beanDefinition, B bean) {
        if (bean instanceof InterceptedMethodProvider<?> interceptedMethodProvider) {
            for (ExecutableMethod<?, ?> method : interceptedMethodProvider.interceptedMethods()) {
                if (method.getMethodName().equals("close") && method.getArguments().length == 0) {
                    return Optional.of((ExecutableMethod<B, ?>) method);
                }
            }
        }
        BeanDefinition<B> closeDefinition = findAutoCloseDefinition(beanDefinition, bean);
        return closeDefinition.findMethod("close")
                .map(method -> (ExecutableMethod<B, ?>) method);
    }

    private <B> BeanDefinition<B> findAutoCloseDefinition(BeanDefinition<B> beanDefinition, B bean) {
        if (beanDefinition.isProxy()) {
            return beanDefinition;
        }
        Class<B> beanClass = (Class<B>) bean.getClass();
        return (BeanDefinition<B>) beanContext.findBeanDefinition(beanClass, beanDefinition.getDeclaredQualifier())
                .or(() -> beanContext.findBeanDefinition(beanClass, null))
                .orElse(beanDefinition);
    }

    private <B> void invokeClose(ExecutableMethod<B, ?> closeMethod, B bean) {
        try {
            closeMethod.invoke(bean);
        } catch (Throwable e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Exception thrown by @AutoClose close() method", e);
            }
        }
    }

    public CreatedBean<T> getCreatedBean() {
        return createdBean;
    }

    void setCreatedBean(CreatedBean<T> createdBean) {
        this.createdBean = createdBean;
    }

    void addDependentContext(DependentContext dependentContext) {
        if (dependentContexts == null) {
            dependentContexts = new ArrayList<>(1);
        }
        dependentContexts.add(dependentContext);
    }
}
