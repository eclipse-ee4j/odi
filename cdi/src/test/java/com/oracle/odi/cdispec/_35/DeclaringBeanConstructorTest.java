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

package com.oracle.odi.cdispec._35;

import com.oracle.odi.test.junit5.OdiTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@OdiTest
public class DeclaringBeanConstructorTest {
    @Test
    @DisplayName("3.5.1. Declaring a bean constructor")
    void testDeclaringConstructor(ShoppingCart shoppingCart) {
        Assertions.assertTrue(shoppingCart.injectInvoked);
    }
}

@ApplicationScoped
class ShoppingCart  {

    private User customer;
    boolean injectInvoked = false;

    @Inject
    public ShoppingCart(User customer) {
        this.customer = customer;
        injectInvoked = true;
    }

    public ShoppingCart(ShoppingCart original) {
        this.customer = original.customer;
    }
}

@Singleton
class User {

}

