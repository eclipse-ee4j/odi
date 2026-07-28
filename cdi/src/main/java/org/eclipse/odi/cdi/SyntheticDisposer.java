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
import java.util.List;
import java.util.Map;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.Qualifier;
import io.micronaut.context.event.BeanPreDestroyEvent;
import io.micronaut.context.event.BeanPreDestroyEventListener;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.BeanDefinition;
import jakarta.enterprise.context.AutoClose;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.build.compatible.spi.Parameters;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticBeanDisposer;
import jakarta.enterprise.inject.build.compatible.spi.SyntheticInjections;
import jakarta.inject.Singleton;
import org.eclipse.odi.cdi.context.DependentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("CdiManagedBeanInconsistencyInspection")
@Singleton
final class SyntheticDisposer implements BeanPreDestroyEventListener<Object> {
    private static final Logger LOG = LoggerFactory.getLogger(SyntheticDisposer.class);

    private final BeanProvider<OdiBeanContainer> beanContainer;

    SyntheticDisposer(BeanProvider<OdiBeanContainer> beanContainer) {
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

                    definition.findMethod("dispose", bean.getClass(), SyntheticInjections.class, Parameters.class)
                            .or(() -> definition.findMethod("dispose", bean.getClass(), Instance.class, Parameters.class))
                            .ifPresent(disposalMethod -> {
                                OdiSyntheticInjections injections = syntheticInjections(beanDefinition);
                                try {
                                    beanContainer.get().fulfillAndExecuteMethod(
                                            definition,
                                            disposalMethod,
                                            argument1 -> {
                                                if (argument1.isInstance(bean)) {
                                                    return bean;
                                                }
                                                if (argument1.getType() == SyntheticInjections.class) {
                                                    return injections;
                                                }
                                                return null;
                                            }
                                    );
                                } finally {
                                    injections.destroy();
                                }
                            });
                }
            }
        }
        closeAutoCloseSyntheticBean(beanDefinition, bean);
        return bean;
    }

    private void closeAutoCloseSyntheticBean(BeanDefinition<?> beanDefinition, Object bean) {
        if (bean instanceof AutoCloseable autoCloseable
                && beanDefinition.hasAnnotation(AutoClose.class)
                && OdiUtils.getSyntheticParameters(beanDefinition).containsKey(OdiSyntheticParameters.BEAN_TYPE)) {
            try {
                autoCloseable.close();
            } catch (Exception e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Error auto-closing synthetic bean [{}]: {}", beanDefinition.getBeanType().getName(), e.getMessage(), e);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private OdiSyntheticInjections syntheticInjections(BeanDefinition<?> beanDefinition) {
        Map<String, Object> syntheticParameters = OdiUtils.getSyntheticParameters(beanDefinition);
        Object value = syntheticParameters.get(OdiSyntheticParameters.INJECTION_POINTS);
        List<OdiSyntheticInjectionPoint> injectionPoints = value instanceof List<?> list
                ? (List<OdiSyntheticInjectionPoint>) list
                : List.of();
        return new OdiSyntheticInjections(
                beanContainer.get(),
                injectionPoints,
                new DependentContext(null),
                null,
                beanDefinition,
                false
        );
    }
}
