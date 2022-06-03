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
package com.oracle.odi.cdi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.InjectionPoint;
import jakarta.enterprise.inject.build.compatible.spi.Parameters;

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
        return new OdiInjectionPoint(bean, injectionPoint, argument);
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
        return new Parameters() {
            @Override
            public <T> T get(String key, Class<T> type) {
                final AnnotationValue<Property> av = map.get(key);
                if (av != null) {
                    return av.getValue(type).orElse(null);
                }
                return null;
            }

            @Override
            public <T> T get(String key, Class<T> type, T defaultValue) {
                final AnnotationValue<Property> av = map.get(key);
                if (av != null) {
                    return av.getValue(type).orElse(defaultValue);
                }
                return defaultValue;
            }
        };
    }
}
