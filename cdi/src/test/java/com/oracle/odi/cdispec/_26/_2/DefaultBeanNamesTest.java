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

package com.oracle.odi.cdispec._26._2;

import com.oracle.odi.test.junit5.OdiTest;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@OdiTest
public class DefaultBeanNamesTest {
    @Test
    @DisplayName("2.6.2. Default bean names")
    void testDefaultBeanNames(
            @Named("defaultOrderManager") OrderManager orderManager,
            @Named("fieldOrder") Order fromField,
            @Named("methodOrder") Order fromMethod) {
        assertNotNull(orderManager);
        assertNotNull(fromField);
        assertNotNull(fromMethod);
        assertEquals("from Field", fromField.name);
        assertEquals("from Method", fromMethod.name);
    }
}

interface OrderManager {}

@Named
class DefaultOrderManager implements OrderManager {
}

@Named("otherOM")
class OtherOrderManager implements OrderManager {
}

@Singleton
class OrderFactory {
    @Named
    @Produces
    Order fieldOrder = new Order("from Field");

    @Named
    @Produces
    Order methodOrder() {
        return new Order("from Method");
    }
}

class Order {
    final String name;

    Order(String name) {
        this.name = name;
    }
}