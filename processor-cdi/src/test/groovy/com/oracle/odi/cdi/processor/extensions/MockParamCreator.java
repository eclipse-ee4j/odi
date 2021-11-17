package com.oracle.odi.cdi.processor.extensions;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.ArgumentInjectionPoint;
import io.micronaut.inject.BeanDefinition;

// Mock impl, this will need to be implemented at runtime on the ODI module
public class MockParamCreator {

    public static Map<String, Object> create(ArgumentInjectionPoint<?, ?> argumentInjectionPoint) {
        final BeanDefinition<?> declaringBean = argumentInjectionPoint.getDeclaringBean();
        final List<AnnotationValue<Property>> values = declaringBean.getAnnotationValuesByType(Property.class);
        if (!values.isEmpty()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (AnnotationValue<Property> value : values) {
                value.stringValue("name").ifPresent((n) ->
                    map.put(n, value.getValue(Object.class).orElse(null))
                );
            }
            return map;
        }
        return Collections.EMPTY_MAP;
    }
}
