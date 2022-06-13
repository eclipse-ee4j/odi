/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.odi.tck.porting;

import io.micronaut.aop.Intercepted;
import org.jboss.cdi.tck.spi.Beans;

import java.io.IOException;

/**
 * TCK's beans implementation.
 */
public class BeansImpl implements Beans {
    @Override
    public boolean isProxy(Object instance) {
        return instance instanceof Intercepted;
    }

    @Override
    public synchronized byte[] passivate(Object instance) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized Object activate(byte[] bytes) throws IOException, ClassNotFoundException {
        throw new UnsupportedOperationException();
    }
}
