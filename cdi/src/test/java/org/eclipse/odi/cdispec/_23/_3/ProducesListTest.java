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

package org.eclipse.odi.cdispec._23._3;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.odi.cdispec.annotations.All;
import org.eclipse.odi.cdispec.annotations.WishList;
import org.eclipse.odi.test.junit5.OdiTest;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@OdiTest
public class ProducesListTest {
    @Inject @All
    List<Product> all;
    @Inject @WishList
    List<Product> wishlist;

    @Test
    @DisplayName("2.3.3. Declaring the qualifiers of a bean https://jakarta.ee/specifications/cdi/3.0/jakarta-cdi-spec-3.0.html#method_constructor_parameter_qualifiers")
    void testProducesList() {
        Assertions.assertEquals(
                3,
                all.size()
        );
        Assertions.assertEquals(
                1,
                wishlist.size()
        );
    }
}

@Singleton
class Shop {

    @Produces @All
    public List<Product> getAllProducts() {
        return Arrays.asList(
                new Product("Apple"),
                new Product("Orange"),
                new Product("Strawberry")
        );
    }

    @Produces
    @WishList
    public List<Product> getWishList() {
        return Collections.singletonList(
                new Product("Strawberry")
        );
    }

}

class Product {
    final String name;

    Product(String name) {
        this.name = name;
    }
}