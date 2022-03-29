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
package com.oracle.odi.cdi;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.InjectionPoint;

/**
 * Internal utils class to create {@link jakarta.enterprise.inject.spi.InjectionPoint}.
 */
@Internal
public final class OdiUtils {

    private OdiUtils() {
    }

    /**
     * Creates an ODI injection point.
     * @param beanContainer The bean container
     * @param injectionPoint The injection point
     * @param argument The argument
     * @return The injection point
     */
    public static jakarta.enterprise.inject.spi.InjectionPoint createCDIInjectionPoint(
            OdiBeanContainer beanContainer,
            InjectionPoint<?> injectionPoint,
            Argument<?> argument) {
        OdiBean<?> bean = beanContainer.getBean(injectionPoint.getDeclaringBean());
        return new OdiInjectionPoint(bean, injectionPoint, argument);
    }

}
