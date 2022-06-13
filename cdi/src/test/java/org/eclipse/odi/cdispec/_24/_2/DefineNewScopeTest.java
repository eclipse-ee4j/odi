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

package org.eclipse.odi.cdispec._24._2;

import org.eclipse.odi.cdispec.annotations.BusinessProcessScoped;
import org.eclipse.odi.test.junit5.OdiTest;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.spi.AlterableContext;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanContainer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.annotation.PreDestroy;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@OdiTest
public class DefineNewScopeTest {
    @Test
    @DisplayName("2.4.2. Defining new scope types - https://jakarta.ee/specifications/cdi/3.0/jakarta-cdi-spec-3.0.html#defining_new_scope_type")
    void testNewScope(BeanContainer beanContainer, Order order, BusinessProcessScope businessProcessScope) {
        assertNotNull(order);
        // not yet initialized
        assertEquals(
                0,
                businessProcessScope.instances.size()
        );
        assertEquals(
                "test",
                order.name()
        );
        // now initialized
        assertEquals(
                1,
                businessProcessScope.instances.size()
        );

        final Bean<?> bean = beanContainer.getBeans(Order.class).iterator().next();

        assertNotNull(businessProcessScope.get(bean));
        assertEquals(
                1,
                businessProcessScope.instances.size()
        );

        businessProcessScope.destroy(bean);

        assertEquals(
                0,
                businessProcessScope.instances.size()
        );

    }
}

interface Order {
    String name();
}

@BusinessProcessScoped
class DefaultOrder implements Order {
    @Inject NameProvider nameProvider;

    @Override
    public String name() {
        return nameProvider.name();
    }
}

@Dependent
class NameProvider {
    private boolean closed;

    String name() {
        return "test";
    }

    @PreDestroy
    void close() {
        this.closed = true;
    }
}

@Singleton
class BusinessProcessScope implements AlterableContext {
    final Map<Contextual<?>, Instance> instances = new HashMap<>();

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void destroy(Contextual<?> contextual) {
        final Instance o = instances.remove(contextual);
        if (o != null) {
            ((Contextual) contextual).destroy(
                    o.bean,
                    o.creationalContext
            );
        }
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return BusinessProcessScoped.class;
    }

    @Override
    public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
        final Instance instance = instances.get(contextual);
        if (instance != null) {
            //noinspection unchecked
            return (T) instance.bean;
        } else {
            final T bean = contextual.create(creationalContext);
            instances.put(contextual, new Instance(creationalContext, bean));
            return bean;
        }
    }

    @Override
    public <T> T get(Contextual<T> contextual) {
        final Instance instance = instances.get(contextual);
        if (instance != null) {
            //noinspection unchecked
            return (T) instance.bean;
        }
        return null;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    static class Instance {
        final CreationalContext<?> creationalContext;
        final Object bean;

        Instance(CreationalContext<?> creationalContext, Object bean) {
            this.creationalContext = creationalContext;
            this.bean = bean;
        }
    }
}