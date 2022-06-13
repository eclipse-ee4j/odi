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
package org.eclipse.odi.cdi;

import java.util.Collection;

import io.micronaut.context.Qualifier;
import io.micronaut.context.event.BeanPreDestroyEvent;
import io.micronaut.context.event.BeanPreDestroyEventListener;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.BeanDefinition;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.build.compatible.spi.Parameters;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanDisposer;
import jakarta.inject.Singleton;

@SuppressWarnings("CdiManagedBeanInconsistencyInspection")
@Singleton
final class SyntheticDisposer implements BeanPreDestroyEventListener<Object> {
    private final OdiBeanContainer beanContainer;

    SyntheticDisposer(OdiBeanContainer beanContainer) {
        this.beanContainer = beanContainer;
    }

    @SuppressWarnings({"rawtypes", "java:S1854", "unchecked"})
    @Override
    public Object onPreDestroy(BeanPreDestroyEvent<Object> event) {
        BeanDefinition<Object> beanDefinition = event.getBeanDefinition();
        Object bean = event.getBean();
        Argument argument = Argument.of(SyntheticBeanDisposer.class, bean.getClass());
        Qualifier declaredQualifier = beanDefinition.getDeclaredQualifier();
        Collection beanDefinitions = event.getSource()
                .getBeanDefinitions(
                        argument,
                        declaredQualifier
                );
        if (CollectionUtils.isNotEmpty(beanDefinitions)) {
            for (Object o : beanDefinitions) {
                if (o instanceof BeanDefinition) {
                    BeanDefinition<SyntheticBeanDisposer<Object>> definition = (BeanDefinition<SyntheticBeanDisposer<Object>>) o;

                    definition.findMethod("dispose", bean.getClass(), Instance.class, Parameters.class)
                            .ifPresent(disposalMethod -> beanContainer.fulfillAndExecuteMethod(
                                    definition,
                                    disposalMethod,
                                    argument1 -> {
                                        if (argument1.isInstance(bean)) {
                                            return bean;
                                        }
                                        return null;
                                    }
                            ));
                }
            }
        }
        return bean;
    }
}
