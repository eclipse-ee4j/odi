package com.oracle.odi.cdi.processor.extensions;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.ArgumentInjectionPoint;
import io.micronaut.inject.BeanDefinition;
import jakarta.enterprise.inject.build.compatible.spi.Parameters;

// Mock impl, this will need to be implemented at runtime on the ODI module
public class MockParamCreator {

    public static Parameters create(ArgumentInjectionPoint<?, ?> argumentInjectionPoint) {
        final BeanDefinition<?> declaringBean = argumentInjectionPoint.getDeclaringBean();
        final List<AnnotationValue<Property>> values = declaringBean.getAnnotationValuesByType(Property.class);
        Map<String, AnnotationValue<Property>> map = new LinkedHashMap<>();
        if (!values.isEmpty()) {
            for (AnnotationValue<Property> value : values) {
                value.stringValue("name").ifPresent((n) ->
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
