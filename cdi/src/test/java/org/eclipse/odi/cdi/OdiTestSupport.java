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

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

import org.eclipse.odi.cdi.annotation.NotYetImplemented;
import io.micronaut.core.reflect.ReflectionUtils;
import org.junit.jupiter.api.Assertions;

public class OdiTestSupport {

    static <T> void testUnsupportedAPI(T instance, Class<? extends T> implementation) {
        Arrays.stream(implementation.getDeclaredMethods())
                .filter((m) -> m.isAnnotationPresent(NotYetImplemented.class))
                .forEach((method) ->
                                 Assertions.assertThrows(UnsupportedOperationException.class, () -> {
                                     method.setAccessible(true);
                                     Object[] args = new Object[method.getParameterTypes().length];
                                     Arrays.fill(args, null);
                                     try {
                                         ReflectionUtils.invokeMethod(instance, method, args);
                                     } catch (Exception e) {
                                         if (e.getCause() instanceof InvocationTargetException) {
                                             throw e.getCause().getCause();
                                         }
                                         throw e;
                                     }
                                 }, "Should have thrown UnsupportedOperation for method: " + method)
                );
    }
}
