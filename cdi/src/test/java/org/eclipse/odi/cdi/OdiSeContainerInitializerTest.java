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

package org.eclipse.odi.cdi;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OdiSeContainerInitializerTest {

    @Test
    void testSeContainerInitializer() {
        try (SeContainer container = SeContainerInitializer.newInstance().initialize()) {
            assertTrue(container.isRunning());
            // getBeanManager not supported
            assertThrows(UnsupportedOperationException.class, container::getBeanManager);
            assertNotNull(container.getBeanContainer());
            assertFalse(container.isAmbiguous());
            assertTrue(container.isResolvable());
            assertFalse(container.isUnsatisfied());
            final Instance<Simple> instance = container.select(Simple.class);

            assertNotNull(instance);

            assertFalse(instance.isAmbiguous());
            assertFalse(instance.isUnsatisfied());
            assertTrue(instance.isResolvable());

            assertEquals(instance.get(), instance.get());
            assertEquals(instance.get(), instance.iterator().next());
            assertEquals(instance.get(), instance.stream().findFirst().orElse(null));

            final Instance<Foo> foos = container.select(Foo.class);

            assertTrue(foos.isAmbiguous());
            final List<Foo> fooList = foos.stream().collect(Collectors.toList());

            assertEquals(2, fooList.size());
            final Instance<Foo> one = foos.select(NamedLiteral.of("one"));

            assertTrue(one.isResolvable());
            assertFalse(one.isAmbiguous());
            assertTrue(one.get() instanceof Foo1);

            final Instance<Foo2> two = foos.select(Foo2.class);

            assertTrue(two.isResolvable());
            assertFalse(two.isAmbiguous());
            assertTrue(two.get() instanceof Foo2);

            TypeLiteral<Foo<String>> fooString = new TypeLiteral<>() {
            };
            final Instance<Foo<String>> fooStringInstance = container.select(fooString);

            assertFalse(fooStringInstance.isAmbiguous());
            assertTrue(fooStringInstance.isResolvable());
            assertTrue(fooStringInstance.get() instanceof Foo1);

        }
    }

    @Singleton
    static class Simple {}

    interface Foo<T extends CharSequence> {}

    @Singleton
    @Named("one")
    static class Foo1 implements Foo<String> {}

    @Singleton
    @Named("two")
    static class Foo2 implements Foo<StringBuilder> {}
}
