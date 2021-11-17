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

package com.oracle.odi.cdispec._22._2;

import com.oracle.odi.test.junit5.OdiTest;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Typed;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@OdiTest
public class RestrictingBeanTypesTest {

    @Test
    @DisplayName("2.2.2. Restricting the bean types of a bean - https://jakarta.ee/specifications/cdi/3.0/jakarta-cdi-spec-3.0.html#bean_types")
    void testInjectLegalTypes(Instance<Object> instance, Shop<Book> shopBook) {
        assertNotNull(shopBook);

        assertFalse(instance.select(BookShop.class).isResolvable());
        assertFalse(instance.select(Business.class).isResolvable());

        TypeLiteral<Shop<String>> tl = new TypeLiteral<>() {};
        final Instance<Shop<String>> genericInstance = instance.select(tl);
        assertTrue(genericInstance.isUnsatisfied());
        assertFalse(genericInstance.isResolvable());

        TypeLiteral<Shop<Book>> tl2 = new TypeLiteral<>() {};
        final Instance<Shop<Book>> genericBookShop = instance.select(tl2);
        assertFalse(genericBookShop.isUnsatisfied());
        assertFalse(genericBookShop.isAmbiguous());
        assertTrue(genericBookShop.isResolvable());

        assertSame(
                shopBook,
                genericBookShop.get()
        );

    }
}

@Singleton
@Typed(Shop.class)
class BookShop extends Business
        implements Shop<Book> {
}

class Book {
    String title;
}

class Business {
}

interface Shop<T> {
}