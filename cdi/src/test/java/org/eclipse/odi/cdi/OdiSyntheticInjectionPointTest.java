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

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OdiSyntheticInjectionPointTest {

    @Test
    void explicitDefaultMatchesImplicitDefaultQualifier() {
        OdiSyntheticInjectionPoint implicitDefault = new OdiSyntheticInjectionPoint(String.class.getName(), List.of());
        OdiSyntheticInjectionPoint explicitDefault = new OdiSyntheticInjectionPoint(
                String.class.getName(),
                List.of(Default.class.getName())
        );

        assertTrue(implicitDefault.matches(String.class, new Annotation[0]));
        assertTrue(implicitDefault.matches(String.class, new Annotation[]{Default.Literal.INSTANCE}));
        assertTrue(explicitDefault.matches(String.class, new Annotation[0]));
        assertTrue(explicitDefault.matches(String.class, new Annotation[]{Default.Literal.INSTANCE}));
    }

    @Test
    void nonDefaultQualifiersStillRequireExactMatch() {
        OdiSyntheticInjectionPoint implicitDefault = new OdiSyntheticInjectionPoint(String.class.getName(), List.of());

        assertFalse(implicitDefault.matches(String.class, new Annotation[]{Any.Literal.INSTANCE}));
        assertFalse(implicitDefault.matches(Integer.class, new Annotation[]{Default.Literal.INSTANCE}));
    }
}
