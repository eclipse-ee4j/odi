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

package com.oracle.odi.cdispec._25._1;

import com.oracle.odi.test.junit5.OdiTest;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@OdiTest
public class BeanDefiningAnnotationsTest {
    @Test
    @DisplayName("2.5.1. Bean defining annotations")
    void testBeanDefiningAnnotation(Instance<Shop<Book>> present, @Any Instance<Shop<Coffee>> absent) {
        assertTrue(present.isResolvable());
        assertFalse(absent.isResolvable());
    }
}

@Dependent
class BookShop
        extends Business
        implements Shop<Book> {

}
class CoffeeShop
        extends Business
        implements Shop<Coffee> {

}

class Business {}
interface Shop<T> {}
class Book {}
class Coffee {}