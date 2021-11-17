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

package com.oracle.odi.cdispec._22;

import com.oracle.odi.test.junit5.OdiTest;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@OdiTest
public class BeanTypesTest {
    private final BookShop bookShop;
    private final Business business;
    private final Shop<Book> shopBook;

    public BeanTypesTest(BookShop bookShop, Business business, Shop<Book> shopBook) {
        this.bookShop = bookShop;
        this.business = business;
        this.shopBook = shopBook;
    }

    @Test
    @DisplayName("2.2 Bean Types - https://jakarta.ee/specifications/cdi/3.0/jakarta-cdi-spec-3.0.html#bean_types")
    void testInjectLegalTypes() {
        assertNotNull(bookShop);
        assertNotNull(business);
        assertNotNull(shopBook);
        assertEquals(
                bookShop, business
        );
        assertEquals(
                bookShop, shopBook
        );
    }
}

@Singleton
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