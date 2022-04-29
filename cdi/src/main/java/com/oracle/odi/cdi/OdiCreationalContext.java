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

import io.micronaut.context.scope.CreatedBean;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;

final class OdiCreationalContext<T> implements CreationalContext<T> {

    private final Contextual<T> contextual;
    private CreatedBean<T> createdBean;
    private T instance;

    OdiCreationalContext(Contextual<T> contextual) {
        this.contextual = contextual;
    }

    @Override
    public void push(T incompleteInstance) {
        instance = incompleteInstance;
    }

    @Override
    public void release() {
        if (contextual instanceof OdiBean) {
            if (createdBean != null) {
                createdBean.close();
                this.createdBean = null;
            }
        } else {
            contextual.destroy(instance, this);
            instance = null;
        }
    }

    CreatedBean<T> getCreatedBean() {
        return createdBean;
    }

    void setCreatedBean(CreatedBean<T> createdBean) {
        this.createdBean = createdBean;
    }
}
