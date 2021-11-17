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

package com.oracle.odi.cdispec._26;

import com.oracle.odi.test.junit5.OdiTest;
import jakarta.inject.Named;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@OdiTest
public class BeanNamesTest {

    @Test
    @DisplayName("2.6. Bean names")
    void testValidBeanNames(
            @Named("orderManager") OrderManager orderManager,
            @Named("com.acme.settings") Settings settings) {
        assertNotNull(orderManager);
        assertNotNull(settings);
    }
}

@Named("orderManager")
class OrderManager {}

@Named("com.acme.settings")
class Settings {}