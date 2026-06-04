/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import io.micronaut.core.annotation.Internal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores synthetic component parameters that cannot be represented as annotation
 * values.
 */
@Internal
public final class OdiSyntheticParameters {
    public static final String PROPERTY = "org.eclipse.odi.synthetic.parameters";
    public static final String INJECTION_POINTS = "org.eclipse.odi.synthetic.injection.points";
    public static final String BEAN_TYPE = "org.eclipse.odi.synthetic.bean.type";

    private static final Map<String, Map<String, Object>> PARAMETERS = new ConcurrentHashMap<>();

    private OdiSyntheticParameters() {
    }

    public static String register(Map<String, Object> parameters) {
        String id = UUID.randomUUID().toString();
        Map<String, Object> copy = new LinkedHashMap<>(parameters.size());
        parameters.forEach((key, value) -> copy.put(key, copyArray(value)));
        PARAMETERS.put(id, Collections.unmodifiableMap(copy));
        return id;
    }

    public static Map<String, Object> find(String id) {
        Map<String, Object> parameters = PARAMETERS.get(id);
        return parameters == null ? Collections.emptyMap() : parameters;
    }

    private static Object copyArray(Object value) {
        if (value instanceof boolean[]) {
            return ((boolean[]) value).clone();
        }
        if (value instanceof byte[]) {
            return ((byte[]) value).clone();
        }
        if (value instanceof short[]) {
            return ((short[]) value).clone();
        }
        if (value instanceof int[]) {
            return ((int[]) value).clone();
        }
        if (value instanceof long[]) {
            return ((long[]) value).clone();
        }
        if (value instanceof char[]) {
            return ((char[]) value).clone();
        }
        if (value instanceof float[]) {
            return ((float[]) value).clone();
        }
        if (value instanceof double[]) {
            return ((double[]) value).clone();
        }
        if (value instanceof Object[]) {
            return ((Object[]) value).clone();
        }
        return value;
    }
}
