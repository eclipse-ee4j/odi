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

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.scope.CreatedBean;
import io.micronaut.inject.BeanDefinition;
import jakarta.enterprise.context.spi.CreationalContext;

final class OdiCreationalContext<T> implements CreationalContext<T> {

    private final BeanContext beanContext;
    private CreatedBean<T> createdBean;

    OdiCreationalContext(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    public void push(T incompleteInstance) {
        // no-op, not needed for Micronaut
    }

    @Override
    public void release() {
        if (createdBean != null) {
            if (createdBean.getClass() == BeanRegistration.class) {
                // TODO in Core: BeanRegistration#close is no-op
                BeanDefinition<T> definition = createdBean.definition();
                Object bean = beanContext.destroyBean(definition.asArgument(), definition.getDeclaredQualifier());
                if (bean == null) {
                    beanContext.destroyBean(createdBean.bean());
                }
            } else {
                createdBean.close();
            }
            this.createdBean = null;
        }
    }

    CreatedBean<T> getCreatedBean() {
        return createdBean;
    }

    void setCreatedBean(CreatedBean<T> createdBean) {
        this.createdBean = createdBean;
    }
}
