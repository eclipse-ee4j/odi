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

import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.InjectionPoint;
import jakarta.enterprise.inject.build.compatible.spi.InvokerInfo;
import jakarta.enterprise.inject.build.compatible.spi.Parameters;
import jakarta.enterprise.invoke.Invoker;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Internal utils class to create {@link jakarta.enterprise.inject.spi.InjectionPoint}.
 */
@Internal
public final class OdiUtils {

    private OdiUtils() {
    }

    /**
     * Creates an ODI injection point.
     * @param beanContainer The bean container
     * @param injectionPoint The injection point
     * @param argument The argument
     * @return The injection point
     */
    public static jakarta.enterprise.inject.spi.InjectionPoint createCDIInjectionPoint(
            OdiBeanContainer beanContainer,
            InjectionPoint<?> injectionPoint,
            Argument<?> argument) {
        OdiBean<?> bean = beanContainer.getBean(injectionPoint.getDeclaringBean());
        return new OdiInjectionPoint(beanContainer.getBeanContext().getClassLoader(), bean, injectionPoint, argument);
    }

    public static Parameters createParameters(BeanDefinition<?> declaringBean) {
        final List<AnnotationValue<Property>> values = declaringBean.getAnnotationValuesByType(Property.class);
        Map<String, AnnotationValue<Property>> map = new LinkedHashMap<>(values.size());
        if (!values.isEmpty()) {
            for (AnnotationValue<Property> value : values) {
                value.stringValue("name").ifPresent(n ->
                    map.put(n, value)
                );
            }
        }
        Map<String, Object> syntheticParameters = getSyntheticParameters(map);
        return new Parameters() {
            @Override
            public <T> T get(String key, Class<T> type) {
                if (syntheticParameters.containsKey(key)) {
                    return convert(syntheticParameters.get(key), type);
                }
                final AnnotationValue<Property> av = map.get(key);
                if (av != null) {
                    return av.getValue(type).orElse(null);
                }
                return null;
            }

            @Override
            public <T> T get(String key, Class<T> type, T defaultValue) {
                if (syntheticParameters.containsKey(key)) {
                    T value = convert(syntheticParameters.get(key), type);
                    return value == null ? defaultValue : value;
                }
                final AnnotationValue<Property> av = map.get(key);
                if (av != null) {
                    return av.getValue(type).orElse(defaultValue);
                }
                return defaultValue;
            }
        };
    }

    public static Map<String, Object> getSyntheticParameters(BeanDefinition<?> declaringBean) {
        final List<AnnotationValue<Property>> values = declaringBean.getAnnotationValuesByType(Property.class);
        Map<String, AnnotationValue<Property>> map = new LinkedHashMap<>(values.size());
        if (!values.isEmpty()) {
            for (AnnotationValue<Property> value : values) {
                value.stringValue("name").ifPresent(n ->
                    map.put(n, value)
                );
            }
        }
        return getSyntheticParameters(map);
    }

    private static Map<String, Object> getSyntheticParameters(Map<String, AnnotationValue<Property>> map) {
        return map.getOrDefault(
                OdiSyntheticParameters.PROPERTY,
                AnnotationValue.builder(Property.class).build()
        ).stringValue()
                .map(OdiSyntheticParameters::find)
                .orElse(Map.of());
    }

    private static <T> T convert(Object value, Class<T> type) {
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        if (type == Invoker[].class && value instanceof InvokerInfo[]) {
            InvokerInfo[] infos = (InvokerInfo[]) value;
            Invoker[] invokers = new Invoker[infos.length];
            for (int i = 0; i < infos.length; i++) {
                if (infos[i] instanceof Invoker) {
                    invokers[i] = (Invoker) infos[i];
                } else {
                    return null;
                }
            }
            return type.cast(invokers);
        }
        return null;
    }
}
