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

package com.oracle.odi.cdispec._37;

import java.lang.annotation.Retention;

import com.oracle.odi.test.junit5.OdiTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@OdiTest
public class InitializerMethodsTest {

    @Test
    @DisplayName("3.7.1. Declaring an initializer method")
    void testDeclareInitializerMethod(Order order) {
        Assertions.assertNotNull(order.product);
        Assertions.assertNotNull(order.customer);
    }
}

@ApplicationScoped
class Order {

    Product product;
    User customer;

    @Inject
    void setProduct(@Selected Product product) {
        this.product = product;
    }

    @Inject
    public void setCustomer(User customer) {
        this.customer = customer;
    }

}

@Singleton
@Selected
class Product {}

@Singleton
class User {}

@Qualifier
@Retention(RUNTIME)
@interface Selected {
}
