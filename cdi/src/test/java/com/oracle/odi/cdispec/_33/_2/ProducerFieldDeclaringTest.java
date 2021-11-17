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

package com.oracle.odi.cdispec._33._2;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.oracle.odi.test.junit5.OdiTest;
import io.micronaut.context.annotation.Primary;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@OdiTest
public class ProducerFieldDeclaringTest {

    @Test
    @DisplayName("3.3.2. Declaring a producer field")
    void testDeclaring(
            PaymentProcessor paymentProcessor,
            List<Product> products,
            @Named("wishlist") List<Product> wishlist) {
        assertTrue(paymentProcessor instanceof CreditCardProcessor);
        assertEquals(1, products.size());
        assertEquals("Apple", products.iterator().next().name);
        assertEquals(2, wishlist.size());
        assertEquals("Banana", wishlist.iterator().next().name);
    }
}

@Singleton
class Shop {
    @Produces
    PaymentProcessor paymentProcessor = new CreditCardProcessor();
    @Produces
    @Primary
    List<Product> products = Collections.singletonList(
            new Product("Apple")
    );
    @Produces
    @Named
    List<Product> wishlist = Arrays.asList(
            new Product("Banana"),
            new Product("Pineapple")
    );
}

interface PaymentProcessor {
}
class CreditCardProcessor implements PaymentProcessor {}
class Product {
    final String name;

    Product(String name) {
        this.name = name;
    }
}
