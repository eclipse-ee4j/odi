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

package com.oracle.odi.cdispec._32._1;

import com.oracle.odi.test.junit5.OdiTest;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@OdiTest
public class ProducerMethodTest {

    @Test
    @DisplayName("3.2.2. Declaring a producer method")
    void testProducerMethod(PaymentProcessor paymentProcessor, List<Product> products) {
        assertTrue(paymentProcessor instanceof CreditCardProcessor);
        assertEquals(2, products.size());
    }
}

@Singleton
class Shop {
    @Produces
    PaymentProcessor getPaymentProcessor() {
        return new CreditCardProcessor();
    }

    @Produces
    List<Product> getProducts() {
        return Arrays.asList(
                new Product("Apple"),
                new Product("Orange")
        );
    }
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
