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

package com.oracle.odi.cdispec._28;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Set;

import com.oracle.odi.cdispec.annotations.BusinessProcessScoped;
import com.oracle.odi.test.junit5.OdiTest;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Stereotype;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@OdiTest
public class StereotypeTests {

    @Test
    @DisplayName("2.8.1. Defining new stereotypes")
    void testDefineSimple(Bean<SomeAction> bean) {
        final Set<Class<? extends Annotation>> stereotypes = bean.getStereotypes();
        assertEquals(1, stereotypes.size());
        assertTrue(stereotypes.contains(Action.class));
    }

    @Test
    @DisplayName("2.8.1.1. Declaring the default scope for a stereotype")
    void testDefineWithScope(
            Bean<SomeRequestAction> bean,
            Bean<SingletonRequestAction> overriddenScopeBean,
            Bean<BusinessRequestAction> customScoped) {
        final Set<Class<? extends Annotation>> stereotypes = bean.getStereotypes();
        assertEquals(1, stereotypes.size());
        assertTrue(stereotypes.contains(RequestAction.class));
        assertEquals(RequestScoped.class, bean.getScope());

        final Class<? extends Annotation> scope = overriddenScopeBean.getScope();
        assertEquals(
                Singleton.class,
                scope
        );

        assertEquals(
                BusinessProcessScoped.class,
                customScoped.getScope()
        );
    }

    @Test
    @DisplayName("2.8.1.2. Specifying interceptor bindings for a stereotype")
    void testInterceptorBindingsForStereotype(Bean<SomeRequestAction> bean) {
        final Set<Class<? extends Annotation>> stereotypes = bean.getStereotypes();
        assertEquals(1, stereotypes.size());
        assertTrue(stereotypes.contains(RequestAction.class));
        assertEquals(RequestScoped.class, bean.getScope());
    }
}

@Action
@Singleton
class SomeAction {}

@RequestAction
class SomeRequestAction {}

@RequestAction
@Singleton
class SingletonRequestAction {}

@RequestAction
@BusinessProcessScoped
class BusinessRequestAction {}

@Stereotype
@Target(TYPE)
@Retention(RUNTIME)
@interface Action {}

@RequestScoped
@Stereotype
@Target(TYPE)
@Retention(RUNTIME)
@interface RequestAction {}