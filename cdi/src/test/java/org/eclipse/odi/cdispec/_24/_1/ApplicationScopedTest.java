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

package org.eclipse.odi.cdispec._24._1;

import org.eclipse.odi.test.junit5.OdiTest;
import io.micronaut.aop.InterceptedProxy;
import jakarta.enterprise.context.ApplicationScoped;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.annotation.PostConstruct;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@OdiTest
public class ApplicationScopedTest {
    @Test
    @DisplayName("2.4.1. Built-in scope types - https://jakarta.ee/specifications/cdi/3.0/jakarta-cdi-spec-3.0.html#builtin_scopes")
    void testApplicationScoped(Shop shop) {
        assertTrue(shop instanceof InterceptedProxy);
        // proxy got initialized
        assertEquals(1, Shop.initCount);
        assertEquals(0, Shop.postConstructCount);
        assertEquals("test", shop.name());
        // proxy target got initialized
        assertEquals(2, Shop.initCount);
        assertEquals(1, Shop.postConstructCount);
    }
}

@ApplicationScoped
class Shop {
    static int initCount = 0;
    static int postConstructCount = 0;

    public Shop() {
        initCount++;
    }

    String name() {
        return "test";
    }

    @PostConstruct
    void created() {
        postConstructCount++;
    }
}
