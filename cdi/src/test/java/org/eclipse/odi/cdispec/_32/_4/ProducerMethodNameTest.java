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

package org.eclipse.odi.cdispec._32._4;

import java.util.Arrays;
import java.util.List;

import org.eclipse.odi.test.junit5.OdiTest;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@OdiTest
public class ProducerMethodNameTest {
    @Test
    @DisplayName("3.2.4. Default bean name for a producer method")
    void testNamedLookup(
            @Named("creditCardProcessor") PaymentProcessor ccpp,
            @Named("bankTransferProcessor") PaymentProcessor btp,
            @Named("wishList") List<Product> products,
            @Named("all") List<Product> all) {
        assertTrue(ccpp instanceof CreditCardProcessor);
        assertTrue(btp instanceof BankTransfer);
        assertEquals(2, products.size());
        assertEquals(3, all.size());
    }
}

@Singleton
class Shop {
    @Produces
    @Named
    PaymentProcessor creditCardProcessor() {
        return new CreditCardProcessor();
    }

    @Produces
    @Named
    PaymentProcessor bankTransferProcessor() {
        return new BankTransfer();
    }

    @Produces
    @Named
    List<Product> getWishList() {
        return Arrays.asList(
                new Product("Apple"),
                new Product("Orange")
        );
    }

    @Produces
    @Named
    List<Product> getAll() {
        return Arrays.asList(
                new Product("Banana"),
                new Product("Strawberry"),
                new Product("Kiwi")
        );
    }

}

interface PaymentProcessor {
}
class CreditCardProcessor implements PaymentProcessor {}
class BankTransfer implements PaymentProcessor {}
class Product {
    final String name;

    Product(String name) {
        this.name = name;
    }
}
