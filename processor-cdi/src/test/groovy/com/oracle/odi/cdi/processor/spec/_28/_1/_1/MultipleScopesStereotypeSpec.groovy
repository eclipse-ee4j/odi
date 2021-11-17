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

package com.oracle.odi.cdi.processor.spec._28._1._1

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import spock.lang.Narrative

@Narrative("CDI Spec - 2.8.1.1. Declaring the default scope for a stereotype")
class MultipleScopesStereotypeSpec extends AbstractTypeElementSpec {

    void 'test stereotype with more than one scope fails to compile'() {
        when:
        buildBeanDefinition('stype.Test', '''
package stype;

import java.lang.annotation.*;
import static java.lang.annotation.RetentionPolicy.*;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Stereotype;
import jakarta.inject.Singleton;

@Action
class Test {

}

@RequestScoped
@Singleton
@Stereotype
@Retention(RUNTIME)
@interface Action {}
''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains("Inherited stereotypes [@Action] include more than one defined scope: @Singleton and @RequestScoped")
    }

    void 'test stereotype with more than one scope can declare scope'() {
        when:
        def definition = buildBeanDefinition('stype.Test', '''
package stype;

import java.lang.annotation.*;
import static java.lang.annotation.RetentionPolicy.*;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Stereotype;
import jakarta.inject.Singleton;

@Action
@RequestScoped
class Test {

}

@RequestScoped
@Singleton
@Stereotype
@Retention(RUNTIME)
@interface Action {}
''')
        then:
        definition != null
    }

}

