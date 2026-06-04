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

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Runtime descriptor for a synthetic bean injection point registered during BCE synthesis.
 *
 * @param typeName The required type name
 * @param qualifierNames The qualifier annotation type names
 */
@Internal
public record OdiSyntheticInjectionPoint(String typeName, List<String> qualifierNames) {
    public OdiSyntheticInjectionPoint {
        qualifierNames = qualifierNames == null ? List.of() : List.copyOf(qualifierNames);
    }

    public boolean matches(Class<?> type, Annotation[] qualifiers) {
        if (!type.getName().equals(typeName)) {
            return false;
        }
        if (qualifiers == null || qualifiers.length == 0) {
            return qualifierNames.isEmpty();
        }
        if (qualifiers.length != qualifierNames.size()) {
            return false;
        }
        for (Annotation qualifier : qualifiers) {
            if (!qualifierNames.contains(qualifier.annotationType().getName())) {
                return false;
            }
        }
        return true;
    }
}
