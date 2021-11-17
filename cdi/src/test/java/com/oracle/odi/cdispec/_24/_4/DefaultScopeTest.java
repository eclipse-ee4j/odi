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

package com.oracle.odi.cdispec._24._4;

import com.oracle.odi.test.junit5.OdiTest;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.Bean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@OdiTest
public class DefaultScopeTest {

    @Test
    @DisplayName("2.4.4. Default scope - https://jakarta.ee/specifications/cdi/3.0/jakarta-cdi-spec-3.0.html#default_scope")
    void testDefaultScope(Bean<Order> orderBean) {
        assertEquals(
                Order.class,
                orderBean.getBeanClass()
        );
        assertEquals(
                Dependent.class,
                orderBean.getScope()
        );
    }
}

@Default
class Order {

}
