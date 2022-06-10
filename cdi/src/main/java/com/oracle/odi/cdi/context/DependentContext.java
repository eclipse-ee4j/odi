/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package com.oracle.odi.cdi.context;

import com.oracle.odi.cdi.OdiCreationalContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.scope.CreatedBean;
import io.micronaut.core.annotation.Internal;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.spi.Context;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple {@link Dependent} context implementation. {@link BeanResolutionContext} is included to be propagated for dependent beans resolution.
 */
@Internal
public final class DependentContext implements Context {

    private final BeanResolutionContext resolutionContext;
    private final List<CreationalContext> contexts = new ArrayList<>();

    public DependentContext(BeanResolutionContext resolutionContext) {
        this.resolutionContext = resolutionContext;
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return Dependent.class;
    }

    @Override
    public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
        if (creationalContext == null) {
            return null;
        }
        contexts.add(creationalContext);
        T bean = contextual.create(creationalContext);
        if (creationalContext instanceof OdiCreationalContext) {
            CreatedBean<T> createdBean = ((OdiCreationalContext<T>) creationalContext).getCreatedBean();
            if (createdBean instanceof BeanRegistration) {
                resolutionContext.addDependentBean((BeanRegistration<T>) createdBean);
            }
        }
        return bean;
    }

    @Override
    public <T> T get(Contextual<T> contextual) {
        return null;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    public BeanResolutionContext getResolutionContext() {
        return resolutionContext;
    }

    public void destroy() {
        contexts.forEach(CreationalContext::release);
    }
}
