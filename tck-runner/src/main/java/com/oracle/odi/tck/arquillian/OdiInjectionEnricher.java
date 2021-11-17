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
package com.oracle.odi.tck.arquillian;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.test.spi.TestEnricher;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * ODI test enricher.
 */
public class OdiInjectionEnricher implements TestEnricher {

    static Map<String, Class> classes = new HashMap<>();

    @Inject
    private Instance<ApplicationContext> runningApplicationContext;

    static void enrich(Object testCase, ApplicationContext applicationContext) {
        Class<?> testClass = testCase.getClass();
        while (!Object.class.equals(testClass)) {
            for (Field field : testClass.getDeclaredFields()) {
                if (hasInjectAnnotation(field)) {
                    // TODO qualifiers?
                    Object value = applicationContext.getBean(field.getType());

                    try {
                        field.set(testCase, value);
                    } catch (IllegalAccessException e) {
                        field.setAccessible(true);
                        try {
                            field.set(testCase, value);
                        } catch (IllegalAccessException ex) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
            classes.put(testClass.getName(), testClass);
            testClass = testClass.getSuperclass();
        }
    }

    private static boolean hasInjectAnnotation(Field field) {
        for (Annotation annotation : field.getAnnotations()) {
            if ("jakarta.inject.Inject".equals(annotation.annotationType().getName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void enrich(Object testCase) {
        // not needed, we do that manually in OdiDeployableContainer.deploy,
        // and Arquillian won't invoke this anyway (because we don't use the "local" protocol)
    }

    @Override
    public Object[] resolve(Method method) {
        Class<?> aClass = classes.get(method.getDeclaringClass().getName());
        try {
            method = aClass.getMethod(method.getName(), Arrays.stream(method.getParameterTypes())
                            .map(clazz -> ClassUtils.forName(clazz.getName(), aClass.getClassLoader()).get())
                            .toArray(Class[]::new));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        Class<?>[] parameterTypes = method.getParameterTypes();
        Object[] result = new Object[parameterTypes.length];

        boolean hasNonArquillianDataProvider = false;
        for (Annotation annotation : method.getAnnotations()) {
            if (annotation.annotationType().getName().equals("org.testng.annotations.Test")) {
                try {
                    Method dataProviderMember = annotation.annotationType().getDeclaredMethod("dataProvider");
                    String value = dataProviderMember.invoke(annotation).toString();
                    hasNonArquillianDataProvider = !value.equals("") && !value.equals("ARQUILLIAN_DATA_PROVIDER");
                    break;
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }
        if (hasNonArquillianDataProvider) {
            return result;
        }

        Type[] genericParameterTypes = method.getGenericParameterTypes();
        ApplicationContext applicationContext = runningApplicationContext.get();
        for (int i = 0; i < parameterTypes.length; i++) {
            // TODO qualifiers?
            result[i] = applicationContext.getBean(Argument.of(genericParameterTypes[i]));
        }
        return result;
    }
}
