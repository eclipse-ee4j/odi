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

package com.oracle.odi.cdispec._24._3;

import com.oracle.odi.cdispec.annotations.All;
import com.oracle.odi.cdispec.annotations.WishList;
import com.oracle.odi.test.junit5.OdiTest;
import io.micronaut.aop.InterceptedProxy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@OdiTest
public class DefiningScopeTest {

    @Test
    @DisplayName("2.4.2. Defining new scope types")
    void testDefiningScopes(@All Product one, @WishList Product two) {
        assertTrue(one instanceof InterceptedProxy);
        assertEquals("Apple", one.name());
        assertEquals("Orange", two.name());
    }
}

@Singleton
class Shop {

    @Produces
    @ApplicationScoped
    @All
    public Product getProductOne() {
        final Product product = new Product();
        product.name = "Apple";
        return product;
    }

    @Produces
    @Singleton
    @WishList
    public Product getProductTwo() {
        final Product product = new Product();
        product.name = "Orange";
        return product;
    }

}

class Product {
    String name;

    public String name() {
        return this.name;
    }
}