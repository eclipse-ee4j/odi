package com.oracle.odi.cdi;

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
