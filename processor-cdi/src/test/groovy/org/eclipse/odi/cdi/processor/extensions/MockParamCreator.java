package org.eclipse.odi.cdi.processor.extensions;

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
                    return av.getValue(type).map(MockParamCreator::copyArray).orElse(null);
                }
                return null;
            }

            @Override
            public <T> T get(String key, Class<T> type, T defaultValue) {
                final AnnotationValue<Property> av = map.get(key);
                if (av != null) {
                    return av.getValue(type).map(MockParamCreator::copyArray).orElse(defaultValue);
                }
                return defaultValue;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> T copyArray(T value) {
        if (value instanceof boolean[]) {
            return (T) ((boolean[]) value).clone();
        }
        if (value instanceof byte[]) {
            return (T) ((byte[]) value).clone();
        }
        if (value instanceof short[]) {
            return (T) ((short[]) value).clone();
        }
        if (value instanceof int[]) {
            return (T) ((int[]) value).clone();
        }
        if (value instanceof long[]) {
            return (T) ((long[]) value).clone();
        }
        if (value instanceof char[]) {
            return (T) ((char[]) value).clone();
        }
        if (value instanceof float[]) {
            return (T) ((float[]) value).clone();
        }
        if (value instanceof double[]) {
            return (T) ((double[]) value).clone();
        }
        if (value instanceof Object[]) {
            return (T) ((Object[]) value).clone();
        }
        return value;
    }
}
