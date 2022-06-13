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
package org.eclipse.odi.cdi.events;

import org.eclipse.odi.cdi.OdiBeanContainer;
import org.eclipse.odi.cdi.annotation.ObservesMethod;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.AdvisedBeanType;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticObserver;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collection;

/**
 * Implementation of {@link ExecutableMethodProcessor} that collects {@link ObservesMethod} and register them.
 */
@Singleton
@Internal
final class ObservesMethodProcessor implements ExecutableMethodProcessor<ObservesMethod> {

    private final OdiBeanContainer beanContainer;
    private final OdiObserverMethodRegistry observerMethodRegistry;

    /**
     * Default constructor.
     * @param beanContainer The bean container
     * @param observerMethodRegistry The observer registry
     */
    @Inject
    ObservesMethodProcessor(OdiBeanContainer beanContainer, OdiObserverMethodRegistry observerMethodRegistry) {
        this.beanContainer = beanContainer;
        this.observerMethodRegistry = observerMethodRegistry;
    }

    @Override
    public void process(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        // Find possibly proxied BeanDefinition
        BeanDefinition<?> targetBeanDefinition = findTargetBeanDefinitions(beanDefinition);
        if (targetBeanDefinition == null) {
            return;
        }
        if (SyntheticObserver.class.isAssignableFrom(targetBeanDefinition.getBeanType())) {
            observerMethodRegistry.register(new SyntheticObserverMethod(beanContainer, targetBeanDefinition, method));
        } else {
            observerMethodRegistry.register(new ExecutableObserverMethod(beanContainer, beanDefinition, targetBeanDefinition, method));
        }
    }

    public BeanDefinition<?> findTargetBeanDefinitions(BeanDefinition<?> originalBeanDefinition) {
        // We need to get all bean definitions and filter them for cases when bean inherit each other
        if (SyntheticObserver.class.isAssignableFrom(originalBeanDefinition.getBeanType())) {
            return originalBeanDefinition;
        }
        Collection<BeanDefinition<?>> beanDefinitions =
                beanContainer.getBeanContext().getBeanDefinitions((Argument) originalBeanDefinition.asArgument());
        for (BeanDefinition<?> beanDefinition : beanDefinitions) {
            if (beanDefinition instanceof AdvisedBeanType) {
                if (((AdvisedBeanType<?>) beanDefinition).getInterceptedType().equals(originalBeanDefinition.getBeanType())) {
                    return beanDefinition;
                }
            } else if (beanDefinition.getBeanType().equals(originalBeanDefinition.getBeanType())) {
                return beanDefinition;
            }
        }
        // Instance is replaced by something else
        return null;
    }

}
