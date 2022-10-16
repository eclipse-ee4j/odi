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
package org.eclipse.odi.cdi;

import io.micronaut.context.AbstractBeanResolutionContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.annotation.Any;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.ArgumentInjectionPoint;
import io.micronaut.inject.InjectionPoint;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Creates instances of {@link jakarta.enterprise.inject.spi.InjectionPoint}.
 */
@Factory
public class OdiInjectPointFactory {

    private static final String INTRODUCTION_TYPE = "io.micronaut.aop.Introduction";

    /**
     * Builds an instance.
     *
     * @param resolutionContext      The resolution context
     * @param beanContainer          The bean container
     * @param <T>                    The generic type
     * @return The instance
     */
    @Any
    @Dependent
    public <T> jakarta.enterprise.inject.spi.InjectionPoint build(BeanResolutionContext resolutionContext,
                                                                  OdiBeanContainer beanContainer) {
        ArgumentInjectionPoint<T, ?> injectionPoint = (ArgumentInjectionPoint<T, ?>) provideInjectionPoint(resolutionContext);
        if (injectionPoint == null) {
            throw new UnsatisfiedResolutionException("Unable to resolve injection point for path: " + resolutionContext.getPath());
        }
        OdiBean<T> bean = beanContainer.getBean(injectionPoint.getDeclaringBean());

        return new OdiInjectionPoint(resolutionContext.getContext().getClassLoader(), bean, injectionPoint, injectionPoint.asArgument());
    }

    public static <T> InjectionPoint<T> provideInjectionPoint(BeanResolutionContext resolutionContext) {
        ArrayList<BeanResolutionContext.Segment<?>> paths = new ArrayList<>(resolutionContext.getPath());
        if (!paths.isEmpty()) {
            BeanResolutionContext.Segment<?> removed = paths.remove(0);
            if (removed instanceof AbstractBeanResolutionContext.MethodArgumentSegment && !paths.isEmpty()) {
                paths.remove(0);
            }
        }
        if (paths.isEmpty()) {
            return null;
        }
        if (CollectionUtils.isNotEmpty(paths)) {
            final Iterator<BeanResolutionContext.Segment<?>> i = paths.iterator();
            BeanResolutionContext.Segment<?> segment = null;
            if (i.hasNext()) {
                segment = i.next();
                if (segment.getDeclaringType().hasStereotype(INTRODUCTION_TYPE)) {
                    segment = i.hasNext() ? i.next() : null;
                }
            }
            if (segment != null) {
                InjectionPoint<T> ip = (InjectionPoint<T>) segment.getInjectionPoint();
                if (ip != null) {
                    return ip;
                }
            }
        }
        return null;
    }
}
