/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.oracle.odi.cdispec._27;

import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DeclaringAlternativeTest {
    @Test
    @DisplayName("2.7.1. Declaring an alternative - disabled")
    void testAlternativeDisabled() {
        try (SeContainer container = SeContainerInitializer.newInstance().initialize()) {
            final Order order = container.select(Order.class).get();
            assertFalse(order instanceof MockOrder);
        }
    }

    @Test
    @DisplayName("2.7.1. Declaring an alternative - enabled")
    void testAlternativeEnabled() {
        try (SeContainer container = SeContainerInitializer
                .newInstance()
                .addProperty("odi.selected-alternatives", "com.oracle.odi.cdispec._27.MockOrder")
                .initialize()) {
            final Order order = container.select(Order.class).get();
            assertTrue(order instanceof MockOrder);
        }
    }
}

@Singleton
class Order {

}

@Alternative
class MockOrder extends Order {}